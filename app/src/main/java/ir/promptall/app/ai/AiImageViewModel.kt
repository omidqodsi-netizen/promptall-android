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
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.util.UUID

data class PendingAiPurchase(
    val purchaseId: Long,
    val postId: Long,
    val productId: String,
    val payload: String,
    val purchaseToken: String?,
    val orderId: String?,
)

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
    val resultUrl: String? = null,
    val activePurchaseId: Long? = null,
    val pendingPurchase: PendingAiPurchase? = null,
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
            pendingPurchase = identity.pendingPurchase(),
            activePurchaseId = identity.pendingPurchase()?.purchaseId,
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
                    pendingPurchase = identity.pendingPurchase(),
                    activePurchaseId = identity.pendingPurchase()?.purchaseId,
                    error = null,
                )
                loadHistory()
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
            _state.value = _state.value.copy(
                referencePreparing = true,
                error = null,
                resultUrl = null,
            )
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

                    identity.pendingReferencePath()?.let { old ->
                        if (old != dest.absolutePath) runCatching { File(old).delete() }
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
        identity.pendingReferencePath()?.let { runCatching { File(it).delete() } }
        identity.clearPendingReference()
        _state.value = _state.value.copy(
            referencePath = null,
            resultUrl = null,
            error = null,
        )
    }

    fun hasReference(): Boolean {
        val path = _state.value.referencePath ?: identity.pendingReferencePath()
        return !path.isNullOrBlank() && File(path).exists()
    }

    fun pendingPurchaseFor(postId: Long): PendingAiPurchase? {
        val pending = identity.pendingPurchase() ?: return null
        return pending.takeIf { it.postId == postId }
    }

    fun abandonPendingPurchase() {
        identity.clearPendingPurchase()
        _state.value = _state.value.copy(
            pendingPurchase = null,
            activePurchaseId = null,
            checkoutStage = null,
            error = null,
        )
    }

    fun preparePurchase(postId: Long, onReady: (PreparePurchaseResponse) -> Unit) {
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
            runCatching { api.preparePurchase(token, PreparePurchaseRequest(postId)) }
                .onSuccess { prepared ->
                    identity.savePendingPurchase(
                        id = prepared.purchaseId,
                        postId = postId,
                        productId = prepared.productId,
                        payload = prepared.payload,
                    )
                    val pending = identity.pendingPurchase()
                    _state.value = _state.value.copy(
                        checkoutStage = "در انتظار پرداخت امن کافه‌بازار…",
                        activePurchaseId = prepared.purchaseId,
                        pendingPurchase = pending,
                    )
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

    fun purchaseFlowStarted() {
        _state.value = _state.value.copy(checkoutStage = "صفحه پرداخت کافه‌بازار باز شد…", error = null)
    }

    fun purchaseCancelled() {
        _state.value = _state.value.copy(
            checkoutStage = null,
            error = "پرداخت لغو شد؛ مبلغی از شما کسر نشده است.",
        )
    }

    fun purchaseFailed(message: String?) {
        _state.value = _state.value.copy(
            checkoutStage = null,
            error = message?.takeIf { it.isNotBlank() } ?: "پرداخت بازار انجام نشد.",
        )
    }

    /**
     * Persist the Bazaar receipt before the network verification starts. If the
     * app is killed after Bazaar returns success, the next launch can continue
     * the exact same purchase instead of charging the user again.
     */
    fun verifyPurchase(
        prepared: PreparePurchaseResponse,
        purchase: PurchaseInfo,
        onVerified: () -> Unit,
    ) {
        identity.savePendingPurchaseReceipt(
            purchaseToken = purchase.purchaseToken,
            orderId = purchase.orderId,
        )
        _state.value = _state.value.copy(pendingPurchase = identity.pendingPurchase())

        viewModelScope.launch {
            _state.value = _state.value.copy(
                checkoutStage = "در حال تأیید خرید روی سرور…",
                error = null,
            )
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

    fun verifyRecoveredPurchase(
        pending: PendingAiPurchase,
        purchase: PurchaseInfo,
        onVerified: () -> Unit,
    ) {
        val prepared = PreparePurchaseResponse(
            success = true,
            purchaseId = pending.purchaseId,
            productId = pending.productId,
            payload = pending.payload,
        )
        verifyPurchase(prepared, purchase, onVerified)
    }

    fun verifyStoredReceipt(
        pending: PendingAiPurchase,
        onVerified: () -> Unit,
    ) {
        val purchaseToken = pending.purchaseToken
        if (purchaseToken.isNullOrBlank()) {
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
                        purchaseToken = purchaseToken,
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
                        ConsumedPurchaseRequest(
                            purchaseId = pending.purchaseId,
                            purchaseToken = purchaseToken,
                        ),
                    )
                }
                if (result.isSuccess) {
                    _state.value = _state.value.copy(
                        checkoutStage = null,
                        activePurchaseId = pending.purchaseId,
                        pendingPurchase = identity.pendingPurchase(),
                    )
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

    fun generate(postId: Long, purchaseId: Long) {
        val path = _state.value.referencePath ?: identity.pendingReferencePath()
        if (path.isNullOrBlank() || !File(path).exists()) {
            _state.value = _state.value.copy(error = "عکس مرجع پیدا نشد؛ دوباره عکس را انتخاب کنید.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                generating = true,
                checkoutStage = null,
                error = null,
                resultUrl = null,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = File(path)
                    val mime = _state.value.referenceMime.ifBlank { identity.pendingReferenceMime() }
                    val part = MultipartBody.Part.createFormData(
                        "reference_image",
                        file.name,
                        file.asRequestBody(mime.toMediaTypeOrNull()),
                    )
                    api.generate(
                        token = identity.token(),
                        postId = postId.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                        purchaseId = purchaseId.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                        referenceImage = part,
                    )
                }
            }.onSuccess { response ->
                identity.clearPendingPurchase()
                _state.value = _state.value.copy(
                    generating = false,
                    resultUrl = response.imageUrl,
                    profile = response.profile ?: _state.value.profile,
                    activePurchaseId = null,
                    pendingPurchase = null,
                    error = null,
                )
                loadHistory()
            }.onFailure {
                _state.value = _state.value.copy(
                    generating = false,
                    activePurchaseId = purchaseId,
                    pendingPurchase = identity.pendingPurchase(),
                    error = humanError(
                        it,
                        "ساخت تصویر انجام نشد؛ پرداخت شما محفوظ است و می‌توانید دوباره تلاش کنید.",
                    ),
                )
            }
        }
    }

    fun retryGeneration(postId: Long) {
        val pending = pendingPurchaseFor(postId)
        val purchaseId = _state.value.activePurchaseId ?: pending?.purchaseId
        if (purchaseId != null && purchaseId > 0) {
            generate(postId, purchaseId)
        } else {
            _state.value = _state.value.copy(error = "خرید آماده‌ای برای تلاش دوباره پیدا نشد.")
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
                .onFailure {
                    _state.value = _state.value.copy(profileLoading = false)
                }
        }
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
            code == 409 && (serverMessage.orEmpty() + raw).contains("used", ignoreCase = true) -> "این خرید قبلاً برای یک ساخت استفاده شده است."
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

    fun installationId(): String {
        val current = prefs.getString("installation_id", null)
        if (!current.isNullOrBlank()) return current
        return UUID.randomUUID().toString().also {
            prefs.edit().putString("installation_id", it).apply()
        }
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
        prefs.edit()
            .putString("ai_reference_path", path)
            .putString("ai_reference_mime", mime)
            .apply()
    }

    fun pendingReferencePath(): String? = prefs.getString("ai_reference_path", null)
    fun pendingReferenceMime(): String = prefs.getString("ai_reference_mime", "image/jpeg").orEmpty().ifBlank { "image/jpeg" }
    fun clearPendingReference() = prefs.edit().remove("ai_reference_path").remove("ai_reference_mime").apply()

    fun savePendingPurchase(id: Long, postId: Long, productId: String, payload: String) {
        prefs.edit()
            .putLong("ai_purchase_id", id)
            .putLong("ai_purchase_post", postId)
            .putString("ai_purchase_product", productId)
            .putString("ai_purchase_payload", payload)
            .remove("ai_purchase_token")
            .remove("ai_purchase_order")
            .apply()
    }

    fun savePendingPurchaseReceipt(purchaseToken: String, orderId: String) {
        prefs.edit()
            .putString("ai_purchase_token", purchaseToken)
            .putString("ai_purchase_order", orderId)
            .apply()
    }

    fun pendingPurchase(): PendingAiPurchase? {
        val id = prefs.getLong("ai_purchase_id", 0L)
        val postId = prefs.getLong("ai_purchase_post", 0L)
        val productId = prefs.getString("ai_purchase_product", null).orEmpty()
        val payload = prefs.getString("ai_purchase_payload", null).orEmpty()
        if (id <= 0L || postId <= 0L || productId.isBlank() || payload.isBlank()) return null
        return PendingAiPurchase(
            purchaseId = id,
            postId = postId,
            productId = productId,
            payload = payload,
            purchaseToken = prefs.getString("ai_purchase_token", null)?.takeIf { it.isNotBlank() },
            orderId = prefs.getString("ai_purchase_order", null)?.takeIf { it.isNotBlank() },
        )
    }

    fun clearPendingPurchase() {
        prefs.edit()
            .remove("ai_purchase_id")
            .remove("ai_purchase_post")
            .remove("ai_purchase_product")
            .remove("ai_purchase_payload")
            .remove("ai_purchase_token")
            .remove("ai_purchase_order")
            .apply()
    }
}
