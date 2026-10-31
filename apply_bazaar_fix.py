#!/usr/bin/env python3
"""Apply the narrowly scoped PromptAll 3.10.9 -> 3.10.10 billing recovery fix.

Run from the root of the existing GitHub repository. Never reconstructs the app.
Fails if expected upstream anchors changed, preventing a silent damaged build.
"""
from pathlib import Path

SCREEN = Path('app/src/main/java/ir/promptall/app/ai/AiImageScreens.kt')
GRADLE = Path('app/build.gradle.kts')
VIEWMODEL = Path('app/src/main/java/ir/promptall/app/ai/AiImageViewModel.kt')

OLD_CLASS_START = 'class BazaarBillingManager(private val activity: ComponentActivity) {'
NEW_CLASS = r'''class BazaarBillingManager(private val activity: ComponentActivity) {
    private var payment: Payment? = null
    private var connection: Connection? = null
    private var configuredKey: String? = null
    private var connecting = false
    private var generation = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun isBazaarInstalled(): Boolean = runCatching {
        activity.packageManager.getPackageInfo("com.farsitel.bazaar", 0)
    }.isSuccess

    fun isConnected(): Boolean = runCatching {
        connection?.getState() == ConnectionState.Connected
    }.getOrDefault(false)

    // Never deliver callbacks from a connection we intentionally replaced/closed.
    private fun deliver(session: Int, callback: (Boolean, String?) -> Unit, ok: Boolean, error: String?) {
        mainHandler.post {
            if (session == generation) callback(ok, error)
        }
    }

    /** Safe to call again after disconnect or when returning to the app. */
    fun configure(rsaPublicKey: String, onState: (Boolean, String?) -> Unit) {
        if (!isBazaarInstalled()) {
            onState(false, "کافه‌بازار روی این گوشی نصب نیست یا در دسترس نیست.")
            return
        }
        val key = rsaPublicKey.trim()
        if (key.isBlank()) {
            onState(false, "کلید عمومی پرداخت بازار روی سرور تنظیم نشده است.")
            return
        }
        if (configuredKey == key && isConnected()) {
            onState(true, null)
            return
        }
        // Avoid creating duplicate simultaneous service bindings.
        if (configuredKey == key && connecting) return

        // Invalidates old callback generation before disconnecting the old service.
        disconnect()
        configuredKey = key
        val session = generation
        val p = Payment(
            context = activity,
            config = PaymentConfiguration(
                localSecurityCheck = SecurityCheck.Enable(rsaPublicKey = key),
                shouldSupportSubscription = false,
            ),
        )
        payment = p
        connecting = true
        try {
            connection = p.connect {
                connectionSucceed {
                    if (session == generation) {
                        connecting = false
                        deliver(session, onState, true, null)
                    }
                }
                connectionFailed { failure ->
                    if (session == generation) {
                        connecting = false
                        android.util.Log.w("PromptAllBilling", "Bazaar billing connection failed", failure)
                        deliver(session, onState, false,
                            failure.message?.takeIf { it.isNotBlank() }
                                ?: "اتصال به سرویس پرداخت کافه‌بازار برقرار نشد.")
                    }
                }
                disconnected {
                    if (session == generation) {
                        connecting = false
                        android.util.Log.w("PromptAllBilling", "Bazaar billing service disconnected")
                        deliver(session, onState, false, "ارتباط با کافه‌بازار قطع شد. دوباره متصل شوید.")
                    }
                }
            }
        } catch (failure: Throwable) {
            if (session == generation) {
                connecting = false
                android.util.Log.w("PromptAllBilling", "Could not start Bazaar billing connection", failure)
                deliver(session, onState, false,
                    failure.message?.takeIf { it.isNotBlank() }
                        ?: "شروع اتصال به سرویس پرداخت بازار ممکن نشد.")
            }
        }
    }

    fun loadPrice(productId: String, onResult: (String?, String?) -> Unit) {
        val p = payment ?: return onResult(null, "اتصال بازار آماده نیست.")
        if (!isConnected()) return onResult(null, "اتصال بازار آماده نیست.")
        p.getInAppSkuDetails(listOf(productId)) {
            getSkuDetailsSucceed { list ->
                // Never display a different SKU's price if the requested product is absent.
                val sku = list.firstOrNull { it.sku == productId }
                onResult(sku?.price, if (sku == null) "محصول پرداختی در بازار پیدا نشد؛ شناسه محصول را بررسی کنید." else null)
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
        if (p == null || !isConnected()) {
            onFailure("اتصال پرداخت بازار آماده نیست؛ اتصال مجدد را بزنید.")
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
                // An already-owned consumable may have succeeded before the UI received a result.
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
        if (!isConnected()) return onMissing()
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
        if (!isConnected()) return onResult(null, "اتصال بازار آماده نیست.")
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
        if (p == null || !isConnected()) {
            onFailure("اتصال بازار برای نهایی‌کردن خرید آماده نیست.")
            return
        }
        p.consumeProduct(purchaseToken) {
            consumeSucceed { onSuccess() }
            consumeFailed { onFailure(it.message) }
        }
    }

    fun disconnect() {
        generation += 1
        connecting = false
        runCatching { connection?.disconnect() }
        connection = null
        payment = null
        configuredKey = null
    }
}'''


def replace_one(s: str, old: str, new: str, label: str) -> str:
    count = s.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected exactly one anchor, found {count}; source may have changed')
    return s.replace(old, new, 1)


def main() -> None:
    if not SCREEN.is_file() or not GRADLE.is_file() or not VIEWMODEL.is_file():
        raise RuntimeError('Run from the repository root, not inside the patch ZIP directory.')
    source = SCREEN.read_text(encoding='utf-8')
    gradle = GRADLE.read_text(encoding='utf-8')
    viewmodel = VIEWMODEL.read_text(encoding='utf-8')
    if 'versionCode = 31010' in gradle and 'fun connectBazaar()' in source:
        print('Fix is already applied. Nothing changed.')
        return
    if 'versionCode = 31009' not in gradle or 'versionName = "3.10.9"' not in gradle:
        raise RuntimeError('Expected version 3.10.9. Do not apply to an unknown version.')

    begin = source.find(OLD_CLASS_START)
    end = source.find('\n@Composable\nfun AiGenerateScreen(', begin)
    if begin < 0 or end < 0 or source.count(OLD_CLASS_START) != 1:
        raise RuntimeError('BazaarBillingManager layout differs from the reviewed source.')
    source = source[:begin] + NEW_CLASS + source[end:]

    old = '''    LaunchedEffect(config?.rsaPublicKey, config?.productId) {
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
'''
    new = '''    fun connectBazaar() {
        val cfg = config ?: return
        price = null
        bazaarMessage = null
        billing.configure(cfg.rsaPublicKey) { connected, message ->
            bazaarConnected = connected
            bazaarMessage = message
            if (connected) {
                billing.loadPrice(cfg.productId) { loadedPrice, error ->
                    price = loadedPrice
                    if (error != null) bazaarMessage = error
                }
            } else {
                price = null
            }
        }
    }
    LaunchedEffect(config?.rsaPublicKey, config?.productId) {
        if (config != null) connectBazaar()
    }
    // A Bazaar purchase briefly moves our Activity out of the foreground.
    // Reconnect on return only when NOT inside an active checkout/verification.
    DisposableEffect(activity, billing, config?.rsaPublicKey, config?.productId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                !billing.isConnected() && state.checkoutStage == null
            ) {
                connectBazaar()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(billing) { onDispose { billing.disconnect() } }
'''
    source = replace_one(source, old, new, 'connection setup')
    source = replace_one(
        source,
        'import androidx.compose.runtime.remember\n',
        'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberUpdatedState\n',
        'Compose rememberUpdatedState import',
    )
    source = replace_one(
        source,
        '    val config = state.config\n    fun connectBazaar() {',
        '    val config = state.config\n    val currentCheckoutStage = rememberUpdatedState(state.checkoutStage)\n    fun connectBazaar() {',
        'lifecycle state freshness',
    )
    source = replace_one(
        source,
        '!billing.isConnected() && state.checkoutStage == null',
        '!billing.isConnected() && currentCheckoutStage.value == null',
        'lifecycle checkout guard',
    )

    old = '''    fun startNewPurchase() {
        vm.preparePurchase(prompt) { prepared ->
            billing.purchase(
'''
    new = '''    fun startNewPurchase() {
        if (!billing.isConnected()) {
            connectBazaar()
            vm.showError("اتصال کافه‌بازار قطع است. پس از اتصال مجدد، پرداخت را تکرار کنید.")
            return
        }
        vm.preparePurchase(prompt) { prepared ->
            // Preparing on the server is asynchronous; the Bazaar service can drop meanwhile.
            if (!billing.isConnected()) {
                connectBazaar()
                vm.purchaseFailed(prepared.purchaseId, "اتصال کافه‌بازار هنگام آماده‌سازی قطع شد؛ دوباره تلاش کنید.")
                return@preparePurchase
            }
            billing.purchase(
'''
    source = replace_one(source, old, new, 'start payment safeguard')

    old = '''                                        onPay = ::startNewPurchase,
                                        onRetryGeneration = null,
'''
    new = '''                                        onPay = ::startNewPurchase,
                                        onRetryGeneration = null,
                                        onReconnect = ::connectBazaar,
'''
    source = replace_one(source, old, new, 'pay card caller')

    old = '''                    } else {
                        when {
                            pending != null -> {
'''
    new = '''                    } else {
                        if (!bazaarConnected && (pending != null || otherPending != null)) {
                            item {
                                OutlinedButton(onClick = ::connectBazaar, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Refresh, null)
                                    Spacer(Modifier.width(7.dp))
                                    Text("اتصال مجدد به کافه‌بازار")
                                }
                            }
                        }
                        when {
                            pending != null -> {
'''
    source = replace_one(source, old, new, 'pending recovery reconnect button')

    old = '''    onPay: () -> Unit,
    onRetryGeneration: (() -> Unit)?,
) {
'''
    new = '''    onPay: () -> Unit,
    onRetryGeneration: (() -> Unit)?,
    onReconnect: () -> Unit,
) {
'''
    source = replace_one(source, old, new, 'PaymentCard signature')

    old = '''            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = AiGreen, modifier = Modifier.size(13.dp))
'''
    new = '''            if (busyText == null && (!connected || price == null)) {
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("اتصال مجدد و دریافت قیمت از بازار")
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = AiGreen, modifier = Modifier.size(13.dp))
'''
    source = replace_one(source, old, new, 'PaymentCard retry UI')

    # Keep a recoverable purchase if the Bazaar checkout had actually opened.
    # A transient binder disconnect is NOT proof that the user paid nothing.
    old = """    fun purchaseFailed(purchaseId: Long, message: String?) {
        val pending = identity.pendingPurchaseById(purchaseId)
        if (pending?.purchaseToken.isNullOrBlank()) identity.removePendingPurchase(purchaseId)
"""
    new = """    fun purchaseFailed(purchaseId: Long, message: String?) {
        val pending = identity.pendingPurchaseById(purchaseId)
        // Keep the receipt-recovery route when Bazaar's payment UI already opened.
        if (pending != null && !pending.flowStarted && pending.purchaseToken.isNullOrBlank()) {
            identity.removePendingPurchase(purchaseId)
        }
"""
    viewmodel = replace_one(viewmodel, old, new, 'preserve interrupted purchase')

    gradle = replace_one(gradle, 'versionCode = 31009', 'versionCode = 31010', 'versionCode')
    gradle = replace_one(gradle, 'versionName = "3.10.9"', 'versionName = "3.10.10"', 'versionName')

    # Update only after every anchor validated; safe even if upstream has changed.
    SCREEN.write_text(source, encoding='utf-8')
    GRADLE.write_text(gradle, encoding='utf-8')
    VIEWMODEL.write_text(viewmodel, encoding='utf-8')
    print('Applied v3.10.10 fix to billing screen, purchase recovery ViewModel and app/build.gradle.kts')


if __name__ == '__main__':
    main()
