package ir.promptall.app.ai

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.ConnectionState
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.request.PurchaseRequest
import ir.promptall.app.data.remote.AiHistoryItem
import ir.promptall.app.data.remote.PreparePurchaseResponse
import ir.promptall.app.data.remote.PromptDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private val AiPurple = Color(0xFFA85CFF)
private val AiPurpleStrong = Color(0xFF7C3AED)
private val AiPurpleSoft = Color(0xFFD0A5FF)
private val AiSurface = Color(0xFF111217)
private val AiSurfaceRaised = Color(0xFF17131F)
private val AiBorder = Color(0xFF2D2B34)
private val AiMuted = Color(0xFF9D99A7)
private val AiGreen = Color(0xFF7BDD9B)
private val AiOrange = Color(0xFFFFB86A)
private val AiRed = Color(0xFFFF8FA6)

/** Thin Poolakey adapter. No Bazaar secret is stored in the APK. */
class BazaarBillingManager(private val activity: ComponentActivity) {
    private var payment: Payment? = null
    private var connection: Connection? = null
    private var configuredKey: String? = null

    fun isBazaarInstalled(): Boolean = runCatching {
        activity.packageManager.getPackageInfo("com.farsitel.bazaar", 0)
    }.isSuccess

    fun configure(rsaPublicKey: String, onState: (Boolean, String?) -> Unit) {
        if (!isBazaarInstalled()) {
            onState(false, "کافه‌بازار روی این گوشی نصب نیست یا در دسترس نیست.")
            return
        }
        if (rsaPublicKey.isBlank()) {
            onState(false, "کلید عمومی پرداخت بازار روی سرور تنظیم نشده است.")
            return
        }
        if (configuredKey == rsaPublicKey && connection?.getState() == ConnectionState.Connected) {
            onState(true, null)
            return
        }
        disconnect()
        configuredKey = rsaPublicKey
        val p = Payment(
            context = activity,
            config = PaymentConfiguration(
                localSecurityCheck = SecurityCheck.Enable(rsaPublicKey = rsaPublicKey),
                shouldSupportSubscription = false,
            ),
        )
        payment = p
        connection = p.connect {
            connectionSucceed { onState(true, null) }
            connectionFailed { onState(false, it.message ?: "اتصال به سرویس پرداخت کافه‌بازار برقرار نشد.") }
            disconnected { onState(false, "ارتباط با کافه‌بازار قطع شد.") }
        }
    }

    fun loadPrice(productId: String, onResult: (String?, String?) -> Unit) {
        val p = payment ?: return onResult(null, "اتصال بازار آماده نیست.")
        if (connection?.getState() != ConnectionState.Connected) return onResult(null, "اتصال بازار آماده نیست.")
        p.getInAppSkuDetails(listOf(productId)) {
            getSkuDetailsSucceed { list ->
                val sku = list.firstOrNull { it.sku == productId } ?: list.firstOrNull()
                onResult(sku?.price, if (sku == null) "محصول پرداختی در بازار پیدا نشد." else null)
            }
            getSkuDetailsFailed { onResult(null, it.message ?: "دریافت قیمت از بازار انجام نشد.") }
        }
    }

    fun purchase(
        prepared: PreparePurchaseResponse,
        onStarted: () -> Unit,
        onSuccess: (PurchaseInfo) -> Unit,
        onOwnedPurchase: (PurchaseInfo) -> Unit,
        onCanceled: () -> Unit,
        onFailure: (String?) -> Unit,
    ) {
        val p = payment
        if (p == null || connection?.getState() != ConnectionState.Connected) {
            onFailure("اتصال پرداخت بازار آماده نیست.")
            return
        }
        p.purchaseProduct(
            registry = activity.activityResultRegistry,
            request = PurchaseRequest(productId = prepared.productId, payload = prepared.payload),
        ) {
            purchaseFlowBegan { onStarted() }
            failedToBeginFlow { failure ->
                recoverOwnedProduct(
                    productId = prepared.productId,
                    onFound = onOwnedPurchase,
                    onMissing = {
                        onFailure(
                            failure.message?.takeIf { message -> message.isNotBlank() }
                                ?.let { message -> "صفحه پرداخت کافه‌بازار باز نشد: $message" }
                                ?: "صفحه پرداخت کافه‌بازار باز نشد. اتصال بازار و وضعیت محصول را بررسی کنید."
                        )
                    },
                )
            }
            purchaseSucceed { onSuccess(it) }
            purchaseCanceled { onCanceled() }
            purchaseFailed { failure ->
                // A consumable that was paid but not consumed is reported by Bazaar
                // as already owned on the next purchase attempt. Query owned purchases
                // and recover that receipt instead of turning it into a new-payment error.
                recoverOwnedProduct(
                    productId = prepared.productId,
                    onFound = onOwnedPurchase,
                    onMissing = { onFailure(failure.message) },
                )
            }
        }
    }

    private fun recoverOwnedProduct(
        productId: String,
        onFound: (PurchaseInfo) -> Unit,
        onMissing: () -> Unit,
    ) {
        val p = payment ?: return onMissing()
        if (connection?.getState() != ConnectionState.Connected) return onMissing()
        p.getPurchasedProducts {
            querySucceed { purchases ->
                val owned = purchases.firstOrNull { it.productId == productId }
                if (owned != null) onFound(owned) else onMissing()
            }
            queryFailed { onMissing() }
        }
    }

    fun findPendingPurchase(productId: String, payload: String, onResult: (PurchaseInfo?, String?) -> Unit) {
        val p = payment ?: return onResult(null, "اتصال بازار آماده نیست.")
        if (connection?.getState() != ConnectionState.Connected) return onResult(null, "اتصال بازار آماده نیست.")
        p.getPurchasedProducts {
            querySucceed { purchases ->
                val exact = purchases.firstOrNull { it.productId == productId && it.payload == payload }
                onResult(exact, if (exact == null) "خرید مطابق این درخواست در حساب بازار پیدا نشد." else null)
            }
            queryFailed { onResult(null, it.message ?: "بازیابی خرید از بازار انجام نشد.") }
        }
    }

    fun consume(purchaseToken: String, onSuccess: () -> Unit, onFailure: (String?) -> Unit) {
        val p = payment
        if (p == null || connection?.getState() != ConnectionState.Connected) {
            onFailure("اتصال بازار برای نهایی‌کردن خرید آماده نیست.")
            return
        }
        p.consumeProduct(purchaseToken) {
            consumeSucceed { onSuccess() }
            consumeFailed { onFailure(it.message) }
        }
    }

    fun disconnect() {
        runCatching { connection?.disconnect() }
        connection = null
        payment = null
    }
}

@Composable
fun AiGenerateScreen(
    prompt: PromptDto,
    vm: AiImageViewModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPrompt: (PromptDto) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val billing = remember(activity) { BazaarBillingManager(activity) }
    var bazaarConnected by remember { mutableStateOf(false) }
    var bazaarMessage by remember { mutableStateOf<String?>(null) }
    var price by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var recoveryBusy by remember { mutableStateOf(false) }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.stageReference(context, uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.stageReference(context, uri)
    }

    val config = state.config
    LaunchedEffect(config?.rsaPublicKey, config?.productId) {
        val cfg = config ?: return@LaunchedEffect
        billing.configure(cfg.rsaPublicKey) { connected, message ->
            bazaarConnected = connected
            bazaarMessage = message
            if (connected) {
                billing.loadPrice(cfg.productId) { loadedPrice, error ->
                    price = loadedPrice
                    if (error != null) bazaarMessage = error
                }
            }
        }
    }
    DisposableEffect(billing) { onDispose { billing.disconnect() } }

    val pending = state.pendingPurchases
        .filter { it.postId == prompt.id }
        .maxByOrNull { it.createdAt }
    val otherPending = state.pendingPurchases
        .filter { it.postId != prompt.id }
        .maxByOrNull { it.createdAt }
    val resultUrl = state.resultUrl?.takeIf { state.resultPostId == prompt.id }
    val currentGenerating = state.generating && state.generationPostId == prompt.id
    val currentReconciling = state.reconciling && state.generationPostId == prompt.id
    val saveImage = rememberImageSaveAction()

    LaunchedEffect(prompt.id) {
        vm.enterPrompt(prompt.id)
    }

    LaunchedEffect(pending?.purchaseId) {
        pending?.let { vm.refreshPendingPurchase(it, surfaceCompletedResult = true) }
    }
    LaunchedEffect(otherPending?.purchaseId) {
        otherPending?.let { vm.refreshPendingPurchase(it, surfaceCompletedResult = false) }
    }

    fun finishConsumeAndGenerate(p: PendingAiPurchase, token: String) {
        billing.consume(
            purchaseToken = token,
            onSuccess = {
                vm.confirmConsumed(p, token, onReadyToGenerate = {
                    recoveryBusy = false
                    vm.generate(prompt.id, p.purchaseId)
                }, onFailure = { recoveryBusy = false })
            },
            onFailure = {
                // It may already have been consumed before an app restart. The
                // server checks Bazaar's consumptionState and can still resume.
                vm.confirmConsumed(p, token, onReadyToGenerate = {
                    recoveryBusy = false
                    vm.generate(prompt.id, p.purchaseId)
                }, onFailure = { recoveryBusy = false })
            },
        )
    }

    fun recoverPurchase(p: PendingAiPurchase, onUnavailable: (() -> Unit)? = null) {
        if (!bazaarConnected || recoveryBusy) return
        recoveryBusy = true
        val token = p.purchaseToken
        if (!token.isNullOrBlank()) {
            vm.verifyStoredReceipt(p) { finishConsumeAndGenerate(p, token) }
            return
        }
        billing.findPendingPurchase(p.productId, p.payload) { purchase, error ->
            if (purchase == null) {
                recoveryBusy = false
                if (onUnavailable != null) {
                    onUnavailable()
                } else {
                    vm.showError(error ?: "خرید نیمه‌تمام در حساب بازار پیدا نشد.")
                }
            } else {
                vm.verifyRecoveredPurchase(p, purchase) {
                    finishConsumeAndGenerate(p, purchase.purchaseToken)
                }
            }
        }
    }

    fun startNewPurchase() {
        vm.preparePurchase(prompt) { prepared ->
            billing.purchase(
                prepared = prepared,
                onStarted = { vm.purchaseFlowStarted(prepared.purchaseId) },
                onSuccess = { purchase ->
                    vm.verifyPurchase(prepared, purchase) {
                        val p = vm.pendingPurchaseFor(prompt.id)
                            ?.takeIf { it.purchaseId == prepared.purchaseId }
                        if (p != null) finishConsumeAndGenerate(p, purchase.purchaseToken)
                        else vm.showError("اطلاعات خرید روی دستگاه پیدا نشد؛ پرداخت شما روی سرور قابل پیگیری است.")
                    }
                },
                onOwnedPurchase = { purchase ->
                    vm.verifyPurchase(prepared, purchase) {
                        val p = vm.pendingPurchaseFor(prompt.id)
                            ?.takeIf { it.purchaseId == prepared.purchaseId }
                        if (p != null) finishConsumeAndGenerate(p, purchase.purchaseToken)
                        else vm.showError("خرید قبلی پیدا شد اما اطلاعات ادامه ساخت روی دستگاه کامل نیست.")
                    }
                },
                onCanceled = { vm.purchaseCancelled(prepared.purchaseId) },
                onFailure = { message -> vm.purchaseFailed(prepared.purchaseId, message) },
            )
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF281437), Color(0xFF0D0C12), Color(0xFF07080B)),
                radius = 1500f,
            )
        )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AiTopBar(
                title = "ساخت با چهره من",
                subtitle = "همین پرامپت، با هویت چهره شما",
                onBack = onBack,
                onProfile = onOpenProfile,
            )

            when {
                state.loading -> LoadingCenter("در حال آماده‌سازی استودیو…")
                config == null || !config.enabled || !config.bazaarEnabled -> AiDisabledState(
                    state.error ?: config?.disabledMessage.orEmpty().ifBlank { "متأسفانه فعلاً این قابلیت در دسترس نیست." }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { PromptPreviewCard(prompt) }
                    item {
                        StepStrip(
                            hasPhoto = !state.referencePath.isNullOrBlank(),
                            paid = pending?.purchaseToken != null || currentGenerating || resultUrl != null,
                            done = resultUrl != null,
                        )
                    }
                    item {
                        FaceUploadCard(
                            state = state,
                            locked = currentGenerating || currentReconciling || state.checkoutStage != null || pending != null,
                            onGallery = { galleryPicker.launch("image/*") },
                            onCamera = {
                                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                                val file = File(dir, "face-${System.currentTimeMillis()}.jpg")
                                val captureUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                cameraUri = captureUri
                                cameraLauncher.launch(captureUri)
                            },
                            onRemove = vm::removeReference,
                        )
                    }

                    if (!state.error.isNullOrBlank()) {
                        item { ErrorBanner(state.error.orEmpty(), onDismiss = vm::clearError) }
                    }

                    if (resultUrl != null) {
                        item {
                            ResultCard(
                                url = resultUrl.orEmpty(),
                                title = prompt.title,
                                onSave = { saveImage(resultUrl.orEmpty(), prompt.id) },
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onOpenProfile, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.PhotoLibrary, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("تصاویر من")
                                }
                                Button(
                                    onClick = { vm.clearResult(keepReference = true) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AiPurpleStrong),
                                ) {
                                    Icon(Icons.Default.Refresh, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("ساخت دوباره")
                                }
                            }
                        }
                    } else if (currentGenerating || currentReconciling) {
                        item {
                            GenerationProgressCard(
                                message = if (state.reconciling) {
                                    state.checkoutStage ?: "در حال بررسی نتیجه ساخت روی سرور…"
                                } else {
                                    "در حال ساخت تصویر شما…"
                                }
                            )
                        }
                    } else {
                        when {
                            pending != null -> {
                                item {
                                    RecoveryCard(
                                        busy = recoveryBusy || state.checkoutStage != null || state.reconciling,
                                        receiptSaved = !pending.purchaseToken.isNullOrBlank(),
                                        onContinue = { recoverPurchase(pending) },
                                        onCheckResult = { vm.checkPurchaseResult(pending) },
                                        onPayAgain = ::startNewPurchase,
                                    )
                                }
                            }
                            otherPending != null -> {
                                item {
                                    OtherPendingPurchaseCard(
                                        pending = otherPending,
                                        busy = recoveryBusy || state.checkoutStage != null,
                                        onOpenPrevious = { onOpenPrompt(otherPending.toPromptDto()) },
                                        onReplace = {
                                            vm.replacePendingPurchaseForPrompt(
                                                pending = otherPending,
                                                prompt = prompt,
                                                onReady = { replaced ->
                                                    recoverPurchase(
                                                        replaced,
                                                        onUnavailable = {
                                                            vm.abandonPendingPurchase(replaced.purchaseId)
                                                            startNewPurchase()
                                                        },
                                                    )
                                                },
                                                onCannotReuse = ::startNewPurchase,
                                            )
                                        },
                                    )
                                }
                            }
                            else -> {
                                item {
                                    PaymentCard(
                                        price = price,
                                        connected = bazaarConnected,
                                        message = bazaarMessage,
                                        paymentNotice = config.paymentNotice,
                                        enabled = !state.referencePath.isNullOrBlank() && state.checkoutStage == null,
                                        busyText = state.checkoutStage,
                                        onPay = ::startNewPurchase,
                                        onRetryGeneration = null,
                                    )
                                }
                            }
                        }
                    }

                    item { PrivacyAndSafetyCard() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProfileScreen(vm: AiImageViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<AiHistoryItem?>(null) }
    val saveImage = rememberImageSaveAction()
    LaunchedEffect(Unit) { vm.loadHistory() }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1A1023), Color(0xFF0A0A0E), Color(0xFF07080B)))
        )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AiTopBar("پروفایل من", "تصاویر ساخته‌شده با PromptAll AI", onBack, null)
            ProfileSummary(
                name = state.profile?.name ?: "کاربر PromptAll",
                count = state.profile?.generatedCount ?: state.history.size,
                loading = state.profileLoading,
                onRefresh = vm::loadHistory,
            )
            when {
                state.profileLoading && state.history.isEmpty() -> LoadingCenter("در حال دریافت تصاویر شما…")
                state.history.isEmpty() -> EmptyProfile()
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp, 6.dp, 12.dp, 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.history, key = { it.id }) { item ->
                        HistoryCard(item) { preview = item }
                    }
                }
            }
        }

        if (preview != null) {
            ModalBottomSheet(
                onDismissRequest = { preview = null },
                containerColor = Color(0xFF101116),
            ) {
                val item = preview ?: return@ModalBottomSheet
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(24.dp)),
                    )
                    Spacer(Modifier.height(13.dp))
                    Text(
                        item.title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { saveImage(item.imageUrl, item.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AiPurpleStrong),
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(6.dp))
                        Text("ذخیره تصویر")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onProfile: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleAction(Icons.Default.ArrowBack, "بازگشت", onBack)
        if (onProfile != null) {
            Spacer(Modifier.width(8.dp))
            CircleAction(Icons.Default.Person, "پروفایل و تصاویر من", onProfile, accent = true)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = AiMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CircleAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = if (accent) Color(0xFF251733) else Color(0xFF17191D),
        border = BorderStroke(1.dp, if (accent) Color(0xFF68428B) else AiBorder),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = if (accent) AiPurpleSoft else Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun PromptPreviewCard(prompt: PromptDto) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        color = AiSurface,
        border = BorderStroke(1.dp, AiBorder),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = prompt.image.url,
                contentDescription = prompt.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("پرامپت انتخاب‌شده", color = AiPurpleSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    prompt.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Right,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "نیازی به کپی پرامپت نیست؛ متن همین کارت خودکار اجرا می‌شود.",
                    color = AiMuted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Right,
                )
            }
        }
    }
}

@Composable
private fun StepStrip(hasPhoto: Boolean, paid: Boolean, done: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        StepChip("۱", "چهره", hasPhoto, !hasPhoto)
        StepLine(hasPhoto)
        StepChip("۲", "پرداخت", paid, hasPhoto && !paid)
        StepLine(paid)
        StepChip("۳", "ساخت", done, paid && !done)
    }
}

@Composable
private fun StepLine(active: Boolean) {
    Box(Modifier.width(34.dp).height(2.dp).background(if (active) AiPurple else Color(0xFF2B2932)))
}

@Composable
private fun StepChip(number: String, label: String, done: Boolean, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(31.dp),
            shape = CircleShape,
            color = when {
                done -> Color(0xFF173021)
                active -> Color(0xFF2A1838)
                else -> Color(0xFF17181C)
            },
            border = BorderStroke(
                1.dp,
                when {
                    done -> Color(0xFF3A7650)
                    active -> Color(0xFF7348A2)
                    else -> AiBorder
                }
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (done) "✓" else number,
                    color = when {
                        done -> AiGreen
                        active -> AiPurpleSoft
                        else -> AiMuted
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            label,
            color = if (active || done) Color.White else AiMuted,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FaceUploadCard(
    state: AiImageUiState,
    locked: Boolean,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        color = AiSurface,
        border = BorderStroke(1.dp, if (state.referencePath != null) Color(0xFF58386F) else AiBorder),
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = Color(0xFF21142D)) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = AiPurpleSoft, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("مرجع هویت", color = AiPurpleSoft, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("عکس چهره شما", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("روشن، واضح، بدون فیلتر سنگین و ترجیحاً روبه‌دوربین", color = AiMuted, fontSize = 9.5.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (state.referencePath != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onGallery, enabled = !locked) { Text("تغییر") }
                        OutlinedButton(onClick = onRemove, enabled = !locked) { Text("حذف") }
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = AiGreen, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("عکس آماده است", color = AiGreen, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("همین عکس برای ساخت استفاده می‌شود", color = AiMuted, fontSize = 9.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    AsyncImage(
                        model = File(state.referencePath),
                        contentDescription = "عکس مرجع",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(21.dp)),
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(21.dp),
                    color = Color(0xFF0D0E12),
                    border = BorderStroke(1.dp, Color(0xFF43334F)),
                ) {
                    if (state.referencePreparing) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = AiPurple, modifier = Modifier.size(31.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.height(9.dp))
                            Text("در حال آماده‌سازی عکس…", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.AddAPhoto, null, tint = AiPurpleSoft, modifier = Modifier.size(31.dp))
                            Spacer(Modifier.height(7.dp))
                            Text("یک عکس واضح از چهره انتخاب کن", color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Text("JPG / PNG / WebP", color = AiMuted, fontSize = 8.5.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onCamera, enabled = !locked) {
                                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("دوربین")
                                }
                                Button(
                                    onClick = onGallery,
                                    enabled = !locked,
                                    colors = ButtonDefaults.buttonColors(containerColor = AiPurpleStrong),
                                ) {
                                    Icon(Icons.Default.Image, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("گالری")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(
    price: String?,
    connected: Boolean,
    message: String?,
    paymentNotice: String,
    enabled: Boolean,
    busyText: String?,
    onPay: () -> Unit,
    onRetryGeneration: (() -> Unit)?,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF15101C),
        border = BorderStroke(1.dp, Color(0xFF54366B)),
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    if (connected) "بازار متصل" else "در انتظار بازار",
                    if (connected) AiGreen else AiOrange,
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("ساخت همین تصویر", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text("هر خرید = یک خروجی برای همین پرامپت", color = AiMuted, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                price ?: "قیمت از کافه‌بازار دریافت می‌شود",
                color = AiPurpleSoft,
                fontSize = if (price != null) 29.sp else 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                "مبلغ نمایش‌داده‌شده دقیقاً از کافه‌بازار دریافت می‌شود.",
                color = AiMuted,
                fontSize = 8.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            )
            if (!message.isNullOrBlank()) {
                Text(
                    message,
                    color = AiOrange,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                )
            }
            Spacer(Modifier.height(14.dp))

            when {
                busyText != null -> BusyButton(busyText)
                onRetryGeneration != null -> PrimaryActionButton(
                    "تلاش دوباره؛ بدون پرداخت مجدد",
                    Icons.Default.Refresh,
                    onRetryGeneration,
                )
                else -> PrimaryActionButton(
                    "پرداخت با کافه‌بازار و ساخت تصویر",
                    Icons.Default.ShoppingBag,
                    onPay,
                    enabled && connected && price != null,
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = AiGreen, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(paymentNotice, color = AiMuted, fontSize = 8.5.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RecoveryCard(
    busy: Boolean,
    receiptSaved: Boolean,
    onContinue: () -> Unit,
    onCheckResult: () -> Unit,
    onPayAgain: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        color = Color(0xFF111A17),
        border = BorderStroke(1.dp, Color(0xFF355847)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restore, null, tint = AiGreen, modifier = Modifier.size(28.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (receiptSaved) "پرداخت این پرامپت ثبت شده" else "یک پرداخت نیمه‌تمام پیدا شد",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                    Text(
                        if (receiptSaved) "اول نتیجه را بررسی کن؛ اگر ساخته نشده باشد بدون پرداخت دوباره ادامه می‌دهیم."
                        else "ابتدا وضعیت خرید قبلی از کافه‌بازار بررسی می‌شود.",
                        color = AiMuted,
                        fontSize = 9.5.sp,
                        textAlign = TextAlign.Right,
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            if (busy) {
                BusyButton("در حال بررسی خرید و نتیجه ساخت…")
            } else {
                PrimaryActionButton("بررسی نتیجه ساخت", Icons.Default.CloudDone, onCheckResult)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (receiptSaved) "ادامه بدون پرداخت مجدد" else "بازیابی خرید قبلی", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPayAgain, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("ساخت مجدد با پرداخت جدید", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "پرداخت قبلی حذف نمی‌شود؛ می‌توانید آن را جداگانه پیگیری کنید.",
                    color = AiMuted,
                    fontSize = 8.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OtherPendingPurchaseCard(
    pending: PendingAiPurchase,
    busy: Boolean,
    onOpenPrevious: () -> Unit,
    onReplace: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        color = Color(0xFF1D1710),
        border = BorderStroke(1.dp, Color(0xFF6B5231)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (pending.promptImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = pending.promptImageUrl,
                        contentDescription = pending.promptTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(15.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    Icon(Icons.Default.WarningAmber, null, tint = AiOrange, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(2f)) {
                    Text("یک ساخت نیمه‌تمام دارید", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(
                        pending.promptTitle.ifBlank { "پرامپت #${pending.postId}" },
                        color = AiOrange,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Right,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "ساخت یک پرامپت را دارید؛ می‌خواهید به‌جای آن این پرامپت را بسازید؟ اگر جایگزین کنید، همان خرید/اعتبار قبلی برای این پرامپت استفاده می‌شود و پرداخت دوباره لازم نیست.",
                color = AiMuted,
                fontSize = 9.5.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (busy) {
                BusyButton("در حال بررسی و جایگزینی ساخت…")
            } else {
                Button(
                    onClick = onReplace,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AiPurpleStrong),
                ) {
                    Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("بله، این پرامپت را جایگزین کن", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenPrevious, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("ادامه ساخت پرامپت قبلی", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AiPurpleStrong,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2C2931),
            disabledContentColor = AiMuted,
        ),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
private fun BusyButton(text: String) {
    Surface(
        Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF241833),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = AiPurpleSoft)
            Spacer(Modifier.width(9.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GenerationProgressCard(message: String = "در حال ساخت تصویر شما…") {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = AiSurface,
        border = BorderStroke(1.dp, Color(0xFF4B3562)),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(66.dp), shape = CircleShape, color = Color(0xFF241735)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AiPurpleSoft, modifier = Modifier.size(43.dp), strokeWidth = 3.dp)
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(15.dp))
            Text(message, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(
                "چهره مرجع و تمام جزئیات پرامپت در حال پردازش است. معمولاً کمی زمان می‌برد.",
                color = AiMuted,
                fontSize = 10.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(50), color = Color(0xFF142219)) {
                Text(
                    "اگر ساخت خطا بخورد، پرداخت شما محفوظ می‌ماند",
                    color = AiGreen,
                    fontSize = 8.5.sp,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultCard(url: String, title: String, onSave: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = AiSurface,
        border = BorderStroke(1.dp, Color(0xFF365B42)),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = Color(0xFF173021)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = AiGreen, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("تصویر آماده شد", color = AiGreen, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(title, color = AiMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 640.dp)
                    .clip(RoundedCornerShape(21.dp)).background(Color.Black),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AiPurpleStrong),
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(5.dp))
                Text("ذخیره تصویر")
            }
        }
    }
}

@Composable
private fun PrivacyAndSafetyCard() {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0E1014),
        border = BorderStroke(1.dp, AiBorder),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDone, null, tint = AiPurpleSoft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("قبل از پرداخت بدان", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "عکس انتخابی برای ادامه فرایند ساخت به سرور PromptAll ارسال می‌شود. خروجی‌های موفق در بخش «تصاویر من» نگهداری می‌شوند تا بعداً دوباره به آن‌ها دسترسی داشته باشی.",
                color = AiMuted,
                fontSize = 9.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Right,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = Color(0xFF2B171C),
        border = BorderStroke(1.dp, Color(0xFF6A3542)),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = AiRed) }
            Spacer(Modifier.width(4.dp))
            Text(
                message,
                color = Color(0xFFFFD5DC),
                fontSize = 10.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AiDisabledState(message: String) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = AiPurpleSoft, modifier = Modifier.size(50.dp))
        Spacer(Modifier.height(12.dp))
        Text("استودیو تصویر در حال آماده‌سازی است", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(message, color = AiMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LoadingCenter(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AiPurpleSoft)
            Spacer(Modifier.height(10.dp))
            Text(message, color = AiMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ProfileSummary(
    name: String,
    count: Int,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        shape = RoundedCornerShape(25.dp),
        color = AiSurface,
        border = BorderStroke(1.dp, AiBorder),
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) CircularProgressIndicator(color = AiPurpleSoft, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "تازه‌سازی", tint = AiMuted)
            }
            Surface(modifier = Modifier.padding(horizontal = 4.dp), shape = RoundedCornerShape(50), color = Color(0xFF21142D)) {
                Text("$count خروجی", color = AiPurpleSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(name, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("گالری خصوصی تصاویر ساخته‌شده", color = AiMuted, fontSize = 9.5.sp)
            }
            Spacer(Modifier.width(10.dp))
            Surface(modifier = Modifier.size(54.dp), shape = CircleShape, color = Color(0xFF28183A)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = AiPurpleSoft, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyProfile() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(modifier = Modifier.size(84.dp), shape = CircleShape, color = Color(0xFF18151D)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFF756B80), modifier = Modifier.size(42.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("هنوز تصویری نساخته‌ای", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(
            "یکی از پرامپت‌های تصویری را باز کن و روی «با چهره خودت بساز» بزن.",
            color = AiMuted,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun HistoryCard(item: AiHistoryItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        color = AiSurface,
        border = BorderStroke(1.dp, AiBorder),
    ) {
        Column {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height.toFloat() else .78f)
                    .clip(RoundedCornerShape(topStart = 21.dp, topEnd = 21.dp)),
            )
            Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.End) {
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Right,
                )
                Text("PromptAll AI", color = AiPurpleSoft, fontSize = 8.5.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .12f)) {
        Text(text, color = color, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

private val imageSaveClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

@Composable
private fun rememberImageSaveAction(): (String, Long) -> Unit {
    val context = LocalContext.current
    var pendingSave by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingSave
        pendingSave = null
        if (granted && target != null) {
            saveImageToGallery(context, target.first, target.second)
        } else if (!granted) {
            Toast.makeText(context, "برای ذخیره تصویر، اجازه دسترسی به حافظه لازم است.", Toast.LENGTH_LONG).show()
        }
    }

    return { url, id ->
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = url to id
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveImageToGallery(context, url, id)
        }
    }
}

private fun saveImageToGallery(context: Context, url: String, id: Long) {
    if (url.isBlank()) {
        Toast.makeText(context, "آدرس تصویر معتبر نیست.", Toast.LENGTH_SHORT).show()
        return
    }
    Toast.makeText(context, "در حال ذخیره تصویر…", Toast.LENGTH_SHORT).show()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        val result = runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PromptAll-Android/3.10.8")
                .build()
            imageSaveClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty image response")
                val responseMime = body.contentType()?.toString()?.substringBefore(';')?.trim().orEmpty()
                val mime = responseMime.takeIf { it.startsWith("image/", ignoreCase = true) } ?: when {
                    url.contains(".webp", ignoreCase = true) -> "image/webp"
                    url.contains(".png", ignoreCase = true) -> "image/png"
                    else -> "image/jpeg"
                }
                val ext = when (mime.lowercase()) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                val fileName = "PromptAll-$id-${System.currentTimeMillis()}.$ext"
                val input = body.byteStream()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PromptAll")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("MediaStore insert failed")
                    try {
                        resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                            ?: throw IOException("MediaStore output failed")
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    } catch (e: Throwable) {
                        resolver.delete(uri, null, null)
                        throw e
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val dir = File(pictures, "PromptAll").apply { mkdirs() }
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
                }
            }
        }
        withContext(Dispatchers.Main) {
            result.onSuccess {
                Toast.makeText(context, "تصویر در گالری، پوشه PromptAll ذخیره شد.", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "ذخیره تصویر انجام نشد؛ اتصال اینترنت یا دسترسی حافظه را بررسی کنید.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
