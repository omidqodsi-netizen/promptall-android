package ir.promptall.app.ai

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.promptall.app.PromptAllApplication
import ir.promptall.app.data.remote.AiAppConfig
import ir.promptall.app.data.remote.AiHistoryItem
import ir.promptall.app.data.remote.AiProfile
import ir.promptall.app.data.remote.AppBootstrapRequest
import ir.promptall.app.data.remote.ConsumedPurchaseRequest
import ir.promptall.app.data.remote.PreparePurchaseRequest
import ir.promptall.app.data.remote.PreparePurchaseResponse
import ir.promptall.app.data.remote.PromptDto
import ir.promptall.app.data.remote.PromptImage
import ir.promptall.app.data.remote.PurchaseStatusResponse
import ir.promptall.app.data.remote.VerifyPurchaseRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

data class PendingAiPurchase(
    val purchaseId: Long,
    val postId: Long,
    val productId: String,
    val payload: String,
    val purchaseToken: String?,
    val orderId: String?,
    val flowStarted: Boolean = false,
    val createdAt: Long = 0L,
    val referencePath: String? = null,
    val referenceMime: String = "image/jpeg",
    val promptTitle: String = "",
    val promptText: String = "",
    val promptImageUrl: String = "",
    val promptImageWidth: Int = 1,
    val promptImageHeight: Int = 1,
    val categoryName: String? = null,
    val categorySlug: String? = null,
) {
    fun toPromptDto(): PromptDto = PromptDto(
        id = postId,
        title = promptTitle.ifBlank { "پرامپت #$postId" },
        promptText = promptText,
        image = PromptImage(
            url = promptImageUrl,
            width = promptImageWidth.coerceAtLeast(1),
            height = promptImageHeight.coerceAtLeast(1),
        ),
        categoryName = categoryName,
        categorySlug = categorySlug,
    )
}

data class AiImageUiState(
    val loading: Boolean = true,
    val profileLoading: Boolean = false,
    val config: AiAppConfig? = null,
    val profile: AiProfile? = null,
    val history: List<AiHistoryItem> = emptyList(),
    val referencePath: String? = null,
    val referenceMime: String = "image/jpeg",
    val referencePreparing: Boolean = false,
    val checkoutStage: String? = null,
    val generating: Boolean = false,
    val reconciling: Boolean = false,
    val resultUrl: String? = null,
    val activePurchaseId: Long? = null,
    val pendingPurchases: List<PendingAiPurchase> = emptyList(),
    val error: String? = null,
)

class AiImageViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PromptAllApplication
    private val api = app.aiImageApi
    private val identity = AiAppIdentity(application)

    private val _state = MutableStateFlow(
        AiImageUiState(
            referencePath = identity.pendingReferencePath()?.takeIf { File(it).exists() },
            referenceMime = identity.pendingReferenceMime(),
            pendingPurchases = identity.recoverablePendingPurchases(),
        )
    )
    val state: StateFlow<AiImageUiState> = _state.asStateFlow()

    init {
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val cfg = api.config()
                val session = api.bootstrap(
                    AppBootstrapRequest(
                        installationId = identity.installationId(),
                        installSecret = identity.installSecret(),
                    )
                )
                identity.saveToken(session.token)
                cfg to session.profile
            }.onSuccess { (cfg, profile) ->
                _state.value = _state.value.copy(
                    loading = false,
                    config = cfg,
                    profile = profile,
                    pendingPurchases = identity.recoverablePendingPurchases(),
                    error = null,
                )
                loadHistory()
                syncPendingPurchasesSilently()
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = humanError(it, "اتصال بخش ساخت تصویر برقرار نشد."),
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearResult(keepReference: Boolean = true) {
        if (!keepReference) removeReference()
        _state.value = _state.value.copy(resultUrl = null, error = null)
    }

    fun stageReference(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(referencePreparing = true, error = null, resultUrl = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val mime = (resolver.getType(uri) ?: "image/jpeg").lowercase()
                    val allowed = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
                    require(mime in allowed) { "فرمت عکس پشتیبانی نمی‌شود. JPG، PNG یا WebP انتخاب کنید." }
                    val maxMb = (_state.value.config?.maxUploadMb ?: 8).coerceAtLeast(1)
                    val ext = when (mime) {
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        else -> "jpg"
                    }
                    val dir = File(context.filesDir, "ai-reference").apply { mkdirs() }
                    val dest = File(dir, "reference-${System.currentTimeMillis()}.$ext")
                    resolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                total += read
                                require(total <= maxMb * 1024L * 1024L) {
                                    "حجم عکس بیشتر از ${maxMb}MB است. یک عکس کم‌حجم‌تر انتخاب کنید."
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: error("فایل تصویر قابل خواندن نیست.")
                    val protected = identity.recoverablePendingPurchases().mapNotNull { it.referencePath }.toSet()
                    identity.pendingReferencePath()?.let { old ->
                        if (old != dest.absolutePath && old !in protected) runCatching { File(old).delete() }
                    }
                    identity.savePendingReference(dest.absolutePath, mime)
                    dest.absolutePath to mime
                }
            }.onSuccess { (path, mime) ->
                _state.value = _state.value.copy(
                    referencePreparing = false,
                    referencePath = path,
                    referenceMime = mime,
                    error = null,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    referencePreparing = false,
                    error = humanError(it, "آماده‌سازی عکس مرجع انجام نشد."),
                )
            }
        }
    }

    fun removeReference() {
        val protected = identity.recoverablePendingPurchases().mapNotNull { it.referencePath }.toSet()
        identity.pendingReferencePath()?.let { if (it !in protected) runCatching { File(it).delete() } }
        identity.clearPendingReference()
        _state.value = _state.value.copy(referencePath = null, resultUrl = null, error = null)
    }

    fun hasReference(): Boolean {
        val path = _state.value.referencePath ?: identity.pendingReferencePath()
        return !path.isNullOrBlank() && File(path).exists()
    }

    fun pendingPurchaseFor(postId: Long): PendingAiPurchase? =
        identity.recoverablePendingPurchases()
            .filter { it.postId == postId }
            .maxByOrNull { it.createdAt }

    fun pendingPurchasesForAnotherPrompt(postId: Long): List<PendingAiPurchase> =
        identity.recoverablePendingPurchases()
            .filter { it.postId != postId }
            .sortedByDescending { it.createdAt }

    fun abandonPendingPurchase(purchaseId: Long) {
        identity.removePendingPurchase(purchaseId)
        refreshPendingState(activeId = null)
    }

    fun preparePurchase(prompt: PromptDto, onReady: (PreparePurchaseResponse) -> Unit) {
        val token = identity.token()
        if (token.isBlank()) {
            initialize()
            _state.value = _state.value.copy(error = "نشست برنامه هنوز آماده نیست؛ چند لحظه دیگر دوباره تلاش کنید.")
            return
        }
        if (!hasReference()) {
            _state.value = _state.value.copy(error = "اول یک عکس واضح از چهره خودتان انتخاب کنید.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(checkoutStage = "در حال آماده‌سازی خرید…", error = null)
            runCatching { api.preparePurchase(token, PreparePurchaseRequest(prompt.id)) }
                .onSuccess { prepared ->
                    identity.savePendingPurchase(
                        id = prepared.purchaseId,
                        post = prompt,
                        productId = prepared.productId,
                        payload = prepared.payload,
                        referencePath = _state.value.referencePath ?: identity.pendingReferencePath(),
                        referenceMime = _state.value.referenceMime.ifBlank { identity.pendingReferenceMime() },
                    )
                    refreshPendingState(activeId = null)
                    _state.value = _state.value.copy(checkoutStage = "در انتظار پرداخت امن کافه‌بازار…")
                    onReady(prepared)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        checkoutStage = null,
                        error = humanError(it, "آماده‌سازی پرداخت انجام نشد."),
                    )
                }
        }
    }

    fun purchaseFlowStarted(purchaseId: Long) {
        identity.markPendingPurchaseFlowStarted(purchaseId)
        refreshPendingState(activeId = null)
        _state.value = _state.value.copy(checkoutStage = "صفحه پرداخت کافه‌بازار باز شد…", error = null)
    }

    fun purchaseCancelled(purchaseId: Long) {
        val pending = identity.pendingPurchaseById(purchaseId)
        if (pending?.purchaseToken.isNullOrBlank()) identity.removePendingPurchase(purchaseId)
        refreshPendingState(activeId = null)
        _state.value = _state.value.copy(
            checkoutStage = null,
            error = "پرداخت لغو شد؛ مبلغی از شما کسر نشده است.",
        )
    }

    fun purchaseFailed(purchaseId: Long, message: String?) {
        val pending = identity.pendingPurchaseById(purchaseId)
        if (pending?.purchaseToken.isNullOrBlank()) identity.removePendingPurchase(purchaseId)
        refreshPendingState(activeId = null)
        _state.value = _state.value.copy(
            checkoutStage = null,
            error = message?.takeIf { it.isNotBlank() } ?: "پرداخت بازار انجام نشد.",
        )
    }

    fun showError(message: String) {
        _state.value = _state.value.copy(checkoutStage = null, error = message)
    }

    fun verifyPurchase(prepared: PreparePurchaseResponse, purchase: PurchaseInfo, onVerified: () -> Unit) {
        identity.savePendingPurchaseReceipt(prepared.purchaseId, purchase.purchaseToken, purchase.orderId)
        refreshPendingState(activeId = prepared.purchaseId)
        viewModelScope.launch {
            _state.value = _state.value.copy(checkoutStage = "در حال تأیید خرید روی سرور…", error = null)
            runCatching {
                api.verifyPurchase(
                    identity.token(),
                    VerifyPurchaseRequest(
                        purchaseId = prepared.purchaseId,
                        purchaseToken = purchase.purchaseToken,
                        orderId = purchase.orderId,
                        developerPayload = purchase.payload.ifBlank { prepared.payload },
                    ),
                )
            }.onSuccess {
                _state.value = _state.value.copy(checkoutStage = "پرداخت تأیید شد؛ آماده‌سازی ساخت…")
                onVerified()
            }.onFailure {
                _state.value = _state.value.copy(
                    checkoutStage = null,
                    error = humanError(it, "تأیید سروری خرید بازار ناموفق بود."),
                )
            }
        }
    }

    fun verifyRecoveredPurchase(pending: PendingAiPurchase, purchase: PurchaseInfo, onVerified: () -> Unit) {
        verifyPurchase(
            PreparePurchaseResponse(true, pending.purchaseId, pending.productId, pending.payload),
            purchase,
            onVerified,
        )
    }

    fun verifyStoredReceipt(pending: PendingAiPurchase, onVerified: () -> Unit) {
        val token = pending.purchaseToken
        if (token.isNullOrBlank()) {
            _state.value = _state.value.copy(error = "رسید خرید قبلی کامل نیست؛ بازیابی از کافه‌بازار را امتحان کنید.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(checkoutStage = "در حال بازیابی و تأیید خرید قبلی…", error = null)
            runCatching {
                api.verifyPurchase(
                    identity.token(),
                    VerifyPurchaseRequest(
                        purchaseId = pending.purchaseId,
                        purchaseToken = token,
                        orderId = pending.orderId.orEmpty(),
                        developerPayload = pending.payload,
                    ),
                )
            }.onSuccess {
                _state.value = _state.value.copy(checkoutStage = "خرید قبلی تأیید شد؛ ادامه ساخت…")
                onVerified()
            }.onFailure {
                _state.value = _state.value.copy(
                    checkoutStage = null,
                    error = humanError(it, "بازیابی خرید قبلی ناموفق بود."),
                )
            }
        }
    }

    fun confirmConsumed(
        pending: PendingAiPurchase,
        purchaseToken: String,
        onReadyToGenerate: () -> Unit,
        onFailure: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(checkoutStage = "در حال نهایی‌کردن خرید…", error = null)
            var last: Throwable? = null
            repeat(5) { attempt ->
                val result = runCatching {
                    api.confirmConsumed(
                        identity.token(),
                        ConsumedPurchaseRequest(pending.purchaseId, purchaseToken),
                    )
                }
                if (result.isSuccess) {
                    refreshPendingState(activeId = pending.purchaseId)
                    _state.value = _state.value.copy(checkoutStage = null)
                    onReadyToGenerate()
                    return@launch
                }
                last = result.exceptionOrNull()
                if (attempt < 4) delay(650L * (attempt + 1))
            }
            _state.value = _state.value.copy(
                checkoutStage = null,
                error = humanError(last ?: Exception(), "تأیید نهایی خرید هنوز کامل نشده است."),
            )
            onFailure?.invoke()
        }
    }

    fun checkPurchaseResult(pending: PendingAiPurchase, silent: Boolean = false, onUnfinished: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (!silent) {
                _state.value = _state.value.copy(reconciling = true, checkoutStage = "در حال بررسی نتیجه روی سرور…", error = null)
            }
            val result = runCatching { api.purchaseStatus(identity.token(), pending.purchaseId) }
            result.onSuccess { status ->
                val completed = applyPurchaseStatus(pending, status)
                if (!completed) {
                    if (!silent) {
                        _state.value = _state.value.copy(
                            reconciling = false,
                            checkoutStage = null,
                            error = when (status.status) {
                                "generating" -> "ساخت تصویر هنوز روی سرور در حال انجام است. چند لحظه دیگر «بررسی نتیجه» را بزنید."
                                "generation_failed" -> status.errorMessage.ifBlank { "ساخت قبلی ناموفق بود؛ بدون پرداخت مجدد می‌توانید دوباره تلاش کنید." }
                                else -> null
                            },
                        )
                    }
                    onUnfinished?.invoke()
                }
            }.onFailure {
                if (!silent) {
                    _state.value = _state.value.copy(
                        reconciling = false,
                        checkoutStage = null,
                        error = humanError(it, "بررسی وضعیت سفارش انجام نشد."),
                    )
                }
                onUnfinished?.invoke()
            }
        }
    }

    fun generate(postId: Long, purchaseId: Long) {
        val pending = identity.pendingPurchaseById(purchaseId)
        if (pending == null || pending.postId != postId) {
            _state.value = _state.value.copy(error = "این خرید برای پرامپت دیگری ثبت شده است و برای تصویر فعلی قابل استفاده نیست.")
            return
        }
        val path = pending.referencePath ?: _state.value.referencePath ?: identity.pendingReferencePath()
        if (path.isNullOrBlank() || !File(path).exists()) {
            _state.value = _state.value.copy(error = "عکس مرجع مربوط به این خرید پیدا نشد؛ پرداخت شما محفوظ است.")
            return
        }
        viewModelScope.launch {
            // First ask the server. If a timed-out request already completed, never generate twice.
            val preflight = runCatching { api.purchaseStatus(identity.token(), purchaseId) }.getOrNull()
            if (preflight != null && applyPurchaseStatus(pending, preflight)) return@launch
            if (preflight?.status == "generating") {
                reconcileAmbiguousGeneration(pending)
                return@launch
            }

            _state.value = _state.value.copy(
                generating = true,
                reconciling = false,
                checkoutStage = null,
                error = null,
                resultUrl = null,
                activePurchaseId = purchaseId,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = File(path)
                    val mime = pending.referenceMime.ifBlank {
                        _state.value.referenceMime.ifBlank { identity.pendingReferenceMime() }
                    }
                    api.generate(
                        token = identity.token(),
                        postId = postId.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                        purchaseId = purchaseId.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                        referenceImage = MultipartBody.Part.createFormData(
                            "reference_image",
                            file.name,
                            file.asRequestBody(mime.toMediaTypeOrNull()),
                        ),
                    )
                }
            }.onSuccess { response ->
                identity.removePendingPurchase(purchaseId)
                _state.value = _state.value.copy(
                    generating = false,
                    reconciling = false,
                    resultUrl = response.imageUrl,
                    profile = response.profile ?: _state.value.profile,
                    activePurchaseId = null,
                    pendingPurchases = identity.recoverablePendingPurchases(),
                    error = null,
                )
                loadHistory()
            }.onFailure { error ->
                if (isAmbiguousNetworkFailure(error)) {
                    reconcileAmbiguousGeneration(pending)
                } else {
                    _state.value = _state.value.copy(
                        generating = false,
                        reconciling = false,
                        activePurchaseId = purchaseId,
                        pendingPurchases = identity.recoverablePendingPurchases(),
                        error = humanError(
                            error,
                            "ساخت تصویر انجام نشد؛ پرداخت شما محفوظ است و می‌توانید دوباره تلاش کنید.",
                        ),
                    )
                }
            }
        }
    }

    fun retryGeneration(postId: Long, purchaseId: Long? = null) {
        val pending = purchaseId?.let(identity::pendingPurchaseById) ?: pendingPurchaseFor(postId)
        if (pending == null || pending.postId != postId) {
            _state.value = _state.value.copy(error = "خرید آماده‌ای برای این پرامپت پیدا نشد.")
            return
        }
        if (pending.purchaseToken.isNullOrBlank()) {
            _state.value = _state.value.copy(error = "رسید پرداخت این پرامپت هنوز تأیید نشده است؛ ابتدا خرید قبلی را بازیابی کنید.")
            return
        }
        checkPurchaseResult(pending, silent = true) {
            generate(postId, pending.purchaseId)
        }
    }

    fun loadHistory() {
        val token = identity.token()
        if (token.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(profileLoading = true)
            runCatching { api.history(token) }
                .onSuccess { response ->
                    _state.value = _state.value.copy(
                        profileLoading = false,
                        history = response.items,
                        profile = response.profile,
                    )
                }
                .onFailure { _state.value = _state.value.copy(profileLoading = false) }
        }
    }

    private suspend fun reconcileAmbiguousGeneration(pending: PendingAiPurchase) {
        _state.value = _state.value.copy(
            generating = false,
            reconciling = true,
            checkoutStage = "پاسخ ساخت دیر رسید؛ در حال بررسی نتیجه روی سرور…",
            error = null,
            activePurchaseId = pending.purchaseId,
        )
        repeat(8) { attempt ->
            val status = runCatching { api.purchaseStatus(identity.token(), pending.purchaseId) }.getOrNull()
            if (status != null) {
                if (applyPurchaseStatus(pending, status)) return
                if (status.status == "generation_failed") {
                    _state.value = _state.value.copy(
                        reconciling = false,
                        checkoutStage = null,
                        error = status.errorMessage.ifBlank { "ساخت قبلی ناموفق بود؛ بدون پرداخت مجدد دوباره تلاش کنید." },
                    )
                    return
                }
            }
            if (attempt < 7) delay(2_000L)
        }
        _state.value = _state.value.copy(
            reconciling = false,
            checkoutStage = null,
            error = "درخواست ساخت روی سرور ثبت شده اما نتیجه هنوز قطعی نیست. پرداخت شما محفوظ است؛ کمی بعد «بررسی نتیجه» را بزنید.",
        )
    }

    private fun applyPurchaseStatus(pending: PendingAiPurchase, status: PurchaseStatusResponse): Boolean {
        if (status.postId != 0L && status.postId != pending.postId) return false
        if (status.completed && status.imageUrl.isNotBlank()) {
            identity.removePendingPurchase(pending.purchaseId)
            _state.value = _state.value.copy(
                generating = false,
                reconciling = false,
                checkoutStage = null,
                resultUrl = status.imageUrl,
                profile = status.profile ?: _state.value.profile,
                activePurchaseId = null,
                pendingPurchases = identity.recoverablePendingPurchases(),
                error = null,
            )
            loadHistory()
            return true
        }
        refreshPendingState(activeId = pending.purchaseId)
        return false
    }

    private fun syncPendingPurchasesSilently() {
        val pending = identity.recoverablePendingPurchases().filter { !it.purchaseToken.isNullOrBlank() }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            pending.take(8).forEach { purchase ->
                val status = runCatching { api.purchaseStatus(identity.token(), purchase.purchaseId) }.getOrNull()
                if (status?.completed == true && status.imageUrl.isNotBlank()) {
                    identity.removePendingPurchase(purchase.purchaseId)
                }
            }
            refreshPendingState(activeId = null)
        }
    }

    private fun refreshPendingState(activeId: Long?) {
        _state.value = _state.value.copy(
            pendingPurchases = identity.recoverablePendingPurchases(),
            activePurchaseId = activeId,
        )
    }

    private fun isAmbiguousNetworkFailure(error: Throwable): Boolean {
        if (error is SocketTimeoutException) return true
        if (error is IOException) return true
        val raw = error.message.orEmpty().lowercase()
        return raw.contains("timeout") || raw.contains("timed out") || raw.contains("connection reset") || raw.contains("unexpected end of stream")
    }

    private fun humanError(error: Throwable, fallback: String): String {
        val http = error as? HttpException
        val code = http?.code()
        val raw = error.message.orEmpty()
        val serverMessage = runCatching {
            val body = http?.response()?.errorBody()?.string().orEmpty()
            if (body.isBlank()) null else JSONObject(body).optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return when {
            code == 401 || raw.contains("401") -> "نشست برنامه منقضی شده است. برنامه را دوباره باز کنید."
            code == 402 || raw.contains("402") -> serverMessage ?: "پرداخت این درخواست کامل یا معتبر نشده است."
            code == 403 -> serverMessage ?: "اجازه انجام این درخواست تأیید نشد. تنظیمات حساب یا خرید را بررسی کنید."
            code == 409 && (serverMessage.orEmpty() + raw).contains("used", ignoreCase = true) -> "این خرید قبلاً برای یک ساخت استفاده شده است. نتیجه را بررسی کنید."
            code == 409 -> serverMessage ?: "$fallback چند ثانیه دیگر دوباره تلاش کنید."
            code == 413 -> "حجم عکس بیشتر از حد مجاز است. یک تصویر کم‌حجم‌تر انتخاب کنید."
            code == 503 -> serverMessage ?: "سرویس ساخت تصویر موقتاً در دسترس نیست. کمی بعد دوباره امتحان کنید."
            raw.contains("Unable to resolve host", ignoreCase = true) -> "اتصال اینترنت برقرار نیست. اینترنت را بررسی کنید."
            !serverMessage.isNullOrBlank() -> serverMessage
            raw.isNotBlank() -> "$fallback\n$raw"
            else -> fallback
        }
    }
}

private class AiAppIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("promptall_image_search", Context.MODE_PRIVATE)
    private val pendingKey = "ai_pending_purchases_v2"

    fun installationId(): String {
        val current = prefs.getString("installation_id", null)
        if (!current.isNullOrBlank()) return current
        return UUID.randomUUID().toString().also { prefs.edit().putString("installation_id", it).apply() }
    }

    fun installSecret(): String {
        val current = prefs.getString("ai_install_secret", null)
        if (!current.isNullOrBlank()) return current
        return (UUID.randomUUID().toString() + UUID.randomUUID().toString()).also {
            prefs.edit().putString("ai_install_secret", it).apply()
        }
    }

    fun token(): String = prefs.getString("ai_app_token", "").orEmpty()
    fun saveToken(value: String) = prefs.edit().putString("ai_app_token", value).apply()

    fun savePendingReference(path: String, mime: String) {
        prefs.edit().putString("ai_reference_path", path).putString("ai_reference_mime", mime).apply()
    }
    fun pendingReferencePath(): String? = prefs.getString("ai_reference_path", null)
    fun pendingReferenceMime(): String = prefs.getString("ai_reference_mime", "image/jpeg").orEmpty().ifBlank { "image/jpeg" }
    fun clearPendingReference() = prefs.edit().remove("ai_reference_path").remove("ai_reference_mime").apply()

    fun savePendingPurchase(
        id: Long,
        post: PromptDto,
        productId: String,
        payload: String,
        referencePath: String?,
        referenceMime: String,
    ) {
        val list = allPendingPurchases().filterNot { it.purchaseId == id }.toMutableList()
        list += PendingAiPurchase(
            purchaseId = id,
            postId = post.id,
            productId = productId,
            payload = payload,
            purchaseToken = null,
            orderId = null,
            flowStarted = false,
            createdAt = System.currentTimeMillis(),
            referencePath = referencePath,
            referenceMime = referenceMime,
            promptTitle = post.title,
            promptText = post.promptText,
            promptImageUrl = post.image.url,
            promptImageWidth = post.image.width,
            promptImageHeight = post.image.height,
            categoryName = post.categoryName,
            categorySlug = post.categorySlug,
        )
        savePendingList(list)
    }

    fun markPendingPurchaseFlowStarted(id: Long) = updatePurchase(id) { it.copy(flowStarted = true) }
    fun savePendingPurchaseReceipt(id: Long, purchaseToken: String, orderId: String) =
        updatePurchase(id) { it.copy(purchaseToken = purchaseToken, orderId = orderId, flowStarted = true) }

    fun pendingPurchaseById(id: Long): PendingAiPurchase? = recoverablePendingPurchases().firstOrNull { it.purchaseId == id }

    fun recoverablePendingPurchases(): List<PendingAiPurchase> {
        migrateLegacyIfNeeded()
        val all = allPendingPurchases()
        val valid = all.filter { it.purchaseToken?.isNotBlank() == true || it.flowStarted }
        if (valid.size != all.size) savePendingList(valid)
        return valid.sortedByDescending { it.createdAt }
    }

    fun removePendingPurchase(id: Long) {
        savePendingList(allPendingPurchases().filterNot { it.purchaseId == id })
    }

    private fun updatePurchase(id: Long, block: (PendingAiPurchase) -> PendingAiPurchase) {
        savePendingList(allPendingPurchases().map { if (it.purchaseId == id) block(it) else it })
    }

    private fun allPendingPurchases(): List<PendingAiPurchase> {
        val raw = prefs.getString(pendingKey, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optLong("purchaseId")
                    val postId = o.optLong("postId")
                    val productId = o.optString("productId")
                    val payload = o.optString("payload")
                    if (id <= 0 || postId <= 0 || productId.isBlank() || payload.isBlank()) continue
                    add(
                        PendingAiPurchase(
                            purchaseId = id,
                            postId = postId,
                            productId = productId,
                            payload = payload,
                            purchaseToken = o.optString("purchaseToken").takeIf { it.isNotBlank() },
                            orderId = o.optString("orderId").takeIf { it.isNotBlank() },
                            flowStarted = o.optBoolean("flowStarted", false),
                            createdAt = o.optLong("createdAt", 0L),
                            referencePath = o.optString("referencePath").takeIf { it.isNotBlank() },
                            referenceMime = o.optString("referenceMime", "image/jpeg").ifBlank { "image/jpeg" },
                            promptTitle = o.optString("promptTitle"),
                            promptText = o.optString("promptText"),
                            promptImageUrl = o.optString("promptImageUrl"),
                            promptImageWidth = o.optInt("promptImageWidth", 1),
                            promptImageHeight = o.optInt("promptImageHeight", 1),
                            categoryName = o.optString("categoryName").takeIf { it.isNotBlank() },
                            categorySlug = o.optString("categorySlug").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun savePendingList(list: List<PendingAiPurchase>) {
        val array = JSONArray()
        list.sortedByDescending { it.createdAt }.take(20).forEach { p ->
            array.put(
                JSONObject().apply {
                    put("purchaseId", p.purchaseId)
                    put("postId", p.postId)
                    put("productId", p.productId)
                    put("payload", p.payload)
                    put("purchaseToken", p.purchaseToken ?: "")
                    put("orderId", p.orderId ?: "")
                    put("flowStarted", p.flowStarted)
                    put("createdAt", p.createdAt)
                    put("referencePath", p.referencePath ?: "")
                    put("referenceMime", p.referenceMime)
                    put("promptTitle", p.promptTitle)
                    put("promptText", p.promptText)
                    put("promptImageUrl", p.promptImageUrl)
                    put("promptImageWidth", p.promptImageWidth)
                    put("promptImageHeight", p.promptImageHeight)
                    put("categoryName", p.categoryName ?: "")
                    put("categorySlug", p.categorySlug ?: "")
                }
            )
        }
        prefs.edit().putString(pendingKey, array.toString()).apply()
    }

    private fun migrateLegacyIfNeeded() {
        if (!prefs.getString(pendingKey, null).isNullOrBlank()) return
        val id = prefs.getLong("ai_purchase_id", 0L)
        val postId = prefs.getLong("ai_purchase_post", 0L)
        val productId = prefs.getString("ai_purchase_product", null).orEmpty()
        val payload = prefs.getString("ai_purchase_payload", null).orEmpty()
        if (id > 0 && postId > 0 && productId.isNotBlank() && payload.isNotBlank()) {
            savePendingList(
                listOf(
                    PendingAiPurchase(
                        purchaseId = id,
                        postId = postId,
                        productId = productId,
                        payload = payload,
                        purchaseToken = prefs.getString("ai_purchase_token", null)?.takeIf { it.isNotBlank() },
                        orderId = prefs.getString("ai_purchase_order", null)?.takeIf { it.isNotBlank() },
                        flowStarted = prefs.getBoolean("ai_purchase_flow_started", false),
                        createdAt = prefs.getLong("ai_purchase_created_at", System.currentTimeMillis()),
                        referencePath = prefs.getString("ai_purchase_reference_path", null)?.takeIf { it.isNotBlank() },
                        referenceMime = prefs.getString("ai_purchase_reference_mime", "image/jpeg").orEmpty().ifBlank { "image/jpeg" },
                    )
                )
            )
        }
        prefs.edit()
            .remove("ai_purchase_id").remove("ai_purchase_post").remove("ai_purchase_product")
            .remove("ai_purchase_payload").remove("ai_purchase_token").remove("ai_purchase_order")
            .remove("ai_purchase_flow_started").remove("ai_purchase_created_at")
            .remove("ai_purchase_reference_path").remove("ai_purchase_reference_mime")
            .apply()
    }
}
