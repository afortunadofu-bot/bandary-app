package com.bandary.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.bandary.data.local.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 兩層方案的分級：PRO_PLUS 完全包含 PRO 的所有功能，只是額度（照片/影片張數）更高。 */
enum class ProTier { PRO, PRO_PLUS }

/**
 * 每場演唱會的照片張數上限，依方案分級——這是 Pro 與 Pro+ 唯一的額度差異（其餘進階功能兩者相同）。
 * AddShowScreen（實際擋額度的地方）與 ProFeaturesScreen（方案比較文案）共用同一組數字，改這裡就好。
 * 免費方案的張數刻意設得比舊版（原本不分方案一律 10 張）低，讓「多張照片」真的是解鎖後才有的價值；
 * 已經上傳超過新上限的舊資料不會被砍掉，只是在降級/到期後不能再新增，見 AddShowScreen 的額度處理。
 */
object PhotoQuota {
    const val FREE = 3
    const val PRO = 5
    const val PRO_PLUS = 10

    fun forTier(isPro: Boolean, isProPlus: Boolean): Int = when {
        isProPlus -> PRO_PLUS
        isPro -> PRO
        else -> FREE
    }
}

/**
 * Bandary Pro 的 Google Play 帳單。
 *
 * 商品 ID 一旦在 Play Console 建立就永遠不能改，所以集中在這裡當唯一真相來源；
 * Play Console 上要建立同名商品（PLUS_* 是新增的第二層方案，上架前要先在 Play Console 建立，
 * 沒建立之前 App 會查不到價格、按鈕會顯示「查不到這個商品」，不會導致舊方案跟著壞掉）：
 *   - bandary_pro_lifetime      ：應用程式內商品（一次性），NT$149
 *   - bandary_pro_yearly        ：訂閱，基本方案週期一年，NT$79
 *   - bandary_proplus_lifetime  ：應用程式內商品（一次性），NT$299
 *   - bandary_proplus_yearly    ：訂閱，基本方案週期一年，NT$159
 *
 * 免費試用是在 Play Console 的訂閱商品底下加一個「優惠方案（offer）」、階段設為免費試用，
 * 不是另外開一個商品，所以 productId 不用動。App 這邊會自動抓到帶試用的那份報價
 * （見 [toOffer]），Play Console 沒設定的話就退回原本無試用的行為，不會壞掉。
 */
enum class ProPlan(val productId: String, val productType: String, val tier: ProTier) {
    /** 一次買斷，永久解鎖。 */
    LIFETIME("bandary_pro_lifetime", BillingClient.ProductType.INAPP, ProTier.PRO),

    /** 年訂閱，到期自動續訂。 */
    YEARLY("bandary_pro_yearly", BillingClient.ProductType.SUBS, ProTier.PRO),

    /** Pro+（收藏家方案）一次買斷：更高的每場照片上限，其餘功能與 Pro 相同。 */
    PLUS_LIFETIME("bandary_proplus_lifetime", BillingClient.ProductType.INAPP, ProTier.PRO_PLUS),

    /** Pro+ 年訂閱。 */
    PLUS_YEARLY("bandary_proplus_yearly", BillingClient.ProductType.SUBS, ProTier.PRO_PLUS);

    companion object {
        fun fromProductId(id: String): ProPlan? = entries.firstOrNull { it.productId == id }
    }
}

/** 一個可購買方案的實際報價（價格字串由 Play 依使用者所在地/幣別給，不是我們寫死的）。 */
data class PlanOffer(
    val plan: ProPlan,
    /** 續訂後實際會收的金額（不是試用期的 $0）。 */
    val formattedPrice: String,
    val details: ProductDetails,
    /** 訂閱才有；一次性商品為 null。送出購買時必須帶。 */
    val offerToken: String?,
    /**
     * 這份報價含的免費試用天數；0＝沒有試用。
     * Play 只會把「這個帳號還有資格用」的試用方案回給我們，所以這個值不為 0 就代表使用者真的試得到，
     * 不需要自己記誰用過試用——那個判斷 Play 端才有完整資料。
     */
    val trialDays: Int = 0
)

/** Play 帳單服務的連線狀態。UNAVAILABLE 代表這台裝置根本不能用 Play 付費（例如側載、無 Play 服務）。 */
enum class BillingAvailability { IDLE, CONNECTING, READY, UNAVAILABLE }

@Singleton
class BillingManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsDataStore
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        // 一次性商品可能進入「付款處理中」（如超商代碼），必須明確宣告才收得到 PENDING 狀態。
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        // 斷線自動重連，省掉自己寫 retry/backoff。
        .enableAutoServiceReconnection()
        .build()

    private val _availability = MutableStateFlow(BillingAvailability.IDLE)
    val availability: StateFlow<BillingAvailability> = _availability.asStateFlow()

    private val _offers = MutableStateFlow<Map<ProPlan, PlanOffer>>(emptyMap())
    val offers: StateFlow<Map<ProPlan, PlanOffer>> = _offers.asStateFlow()

    /** 目前帳號已擁有的方案（買斷 / 有效訂閱），用來做互斥與按鈕狀態。 */
    private val _owned = MutableStateFlow<Set<ProPlan>>(emptySet())
    val owned: StateFlow<Set<ProPlan>> = _owned.asStateFlow()

    /** 給畫面顯示的一次性訊息（成功／失敗／付款處理中）。顯示後由畫面呼叫 [clearMessage]。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun clearMessage() { _message.value = null }

    /** 進入 Pro 頁時呼叫；已連線就只重新查一次價格與擁有狀態。 */
    fun start() {
        if (client.isReady) {
            // 自動重連（enableAutoServiceReconnection）接回來時不會再呼叫 onBillingSetupFinished，
            // 狀態會卡在斷線時設的 CONNECTING——價格查得到、按鈕卻一直顯示「連線 Google Play 中」。
            _availability.value = BillingAvailability.READY
            queryOffers()
            queryOwned()
            return
        }
        if (_availability.value == BillingAvailability.CONNECTING) return
        _availability.value = BillingAvailability.CONNECTING
        runCatching { client.startConnection(this) }
            .onFailure {
                _availability.value = BillingAvailability.UNAVAILABLE
                Log.w(TAG, "startConnection failed", it)
            }
    }

    // ── BillingClientStateListener ──────────────────────────────────────────
    override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            _availability.value = BillingAvailability.READY
            queryOffers()
            queryOwned()
        } else {
            _availability.value = BillingAvailability.UNAVAILABLE
            Log.w(TAG, "billing setup failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    override fun onBillingServiceDisconnected() {
        // enableAutoServiceReconnection 會自己重連，這裡只把狀態退回 CONNECTING。
        _availability.value = BillingAvailability.CONNECTING
    }

    // ── 查價 ────────────────────────────────────────────────────────────────
    private fun queryOffers() {
        queryOffersFor(BillingClient.ProductType.INAPP, listOf(ProPlan.LIFETIME, ProPlan.PLUS_LIFETIME))
        queryOffersFor(BillingClient.ProductType.SUBS, listOf(ProPlan.YEARLY, ProPlan.PLUS_YEARLY))
    }

    private fun queryOffersFor(type: String, plans: List<ProPlan>) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                plans.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it.productId)
                        .setProductType(type)
                        .build()
                }
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails($type) failed: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            // 查得到東西就代表連線是好的（同上：自動重連不會通知 onBillingSetupFinished）。
            _availability.value = BillingAvailability.READY
            val found = details.productDetailsList.mapNotNull { pd -> toOffer(pd) }
            if (found.isNotEmpty()) {
                _offers.value = _offers.value + found.associateBy { it.plan }
            }
        }
    }

    private fun toOffer(pd: ProductDetails): PlanOffer? {
        val plan = ProPlan.fromProductId(pd.productId) ?: return null
        return when (plan.productType) {
            BillingClient.ProductType.INAPP -> {
                val one = pd.oneTimePurchaseOfferDetails ?: return null
                PlanOffer(plan, displayPrice(one.formattedPrice, one.priceAmountMicros, one.priceCurrencyCode), pd, offerToken = null)
            }
            else -> {
                // 訂閱可能有多份報價：基本方案，以及帶免費試用的優惠方案。
                // Play 只回傳這個帳號有資格取得的報價，所以有試用版就優先拿試用版；
                // 這裡要顯示的價格一律是「試用結束後實際會收的錢」，不是試用期那段的 $0。
                val offers = pd.subscriptionOfferDetails.orEmpty()
                val offer = offers.firstOrNull { freeTrialDays(it) > 0 }
                    ?: offers.firstOrNull()
                    ?: return null
                val phases = offer.pricingPhases.pricingPhaseList
                val phase = phases.lastOrNull { it.priceAmountMicros > 0L } ?: phases.firstOrNull() ?: return null
                val price = displayPrice(phase.formattedPrice, phase.priceAmountMicros, phase.priceCurrencyCode)
                PlanOffer(plan, price, pd, offerToken = offer.offerToken, trialDays = freeTrialDays(offer))
            }
        }
    }

    /** 這份報價開頭的免費試用天數；沒有試用就回 0。 */
    private fun freeTrialDays(offer: ProductDetails.SubscriptionOfferDetails): Int {
        val first = offer.pricingPhases.pricingPhaseList.firstOrNull() ?: return 0
        if (first.priceAmountMicros != 0L) return 0
        return isoPeriodToDays(first.billingPeriod)
    }

    /**
     * ISO-8601 期間轉天數，只處理 Play 訂閱週期實際會出現的形式（P7D／P1W／P1M／P1Y）。
     * 月與年用 30／365 概算即可——這個值只拿來排提醒通知，不是計費依據。
     */
    private fun isoPeriodToDays(period: String?): Int {
        val m = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""").find(period ?: return 0)
            ?: return 0
        val (y, mo, w, d) = m.destructured
        return (y.toIntOrNull() ?: 0) * 365 + (mo.toIntOrNull() ?: 0) * 30 +
            (w.toIntOrNull() ?: 0) * 7 + (d.toIntOrNull() ?: 0)
    }

    // ── 擁有狀態 / 恢復購買 ─────────────────────────────────────────────────
    /** 靜默查詢目前擁有的商品（開頁時跑）。 */
    fun queryOwned() = queryPurchases(announce = false)

    /** 使用者主動按「恢復購買」：查完會給訊息回饋。 */
    fun restorePurchases() {
        if (!client.isReady) {
            _message.value = "尚未連上 Google Play，請稍後再試。"
            start()
            return
        }
        queryPurchases(announce = true)
    }

    private fun queryPurchases(announce: Boolean) {
        if (!client.isReady) return
        val types = listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)
        val collected = mutableSetOf<ProPlan>()
        var remaining = types.size
        types.forEach { type ->
            val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
            client.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .forEach { p ->
                            acknowledgeIfNeeded(p)
                            p.products.mapNotNull { ProPlan.fromProductId(it) }.forEach(collected::add)
                        }
                }
                remaining--
                if (remaining == 0) onPurchasesQueried(collected, announce)
            }
        }
    }

    private fun onPurchasesQueried(found: Set<ProPlan>, announce: Boolean) {
        _owned.value = found
        if (found.isNotEmpty()) {
            grantEntitlements(found)
            if (announce) _message.value = "已恢復購買，Pro 功能已解鎖。"
        } else if (announce) {
            _message.value = "這個 Google 帳號沒有找到購買紀錄。請確認登入的是當初購買的帳號。"
        }
        // 注意：查不到購買時「絕對不」把 Pro 關掉——折扣碼解鎖的使用者本來就沒有 Play 購買紀錄。
    }

    // ── 購買 ────────────────────────────────────────────────────────────────
    fun purchase(activity: Activity, plan: ProPlan) {
        val ownedNow = _owned.value
        when {
            plan in ownedNow -> {
                _message.value = "你已經擁有這個方案了。"
                return
            }
            plan == ProPlan.YEARLY && ProPlan.LIFETIME in ownedNow -> {
                _message.value = "你已買斷永久版，不需要再訂閱。"
                return
            }
            plan == ProPlan.PLUS_YEARLY && ProPlan.PLUS_LIFETIME in ownedNow -> {
                _message.value = "你已買斷 Pro+ 永久版，不需要再訂閱。"
                return
            }
            plan.tier == ProTier.PRO && ProPlan.PLUS_LIFETIME in ownedNow -> {
                _message.value = "你已擁有更高階的 Pro+ 永久版，已經包含所有 Pro 功能，不需要再買 Pro。"
                return
            }
        }
        if (!client.isReady) {
            _message.value = "尚未連上 Google Play，請稍後再試。"
            start()
            return
        }
        val offer = _offers.value[plan]
        if (offer == null) {
            _message.value = "Google Play 目前查不到這個商品，請稍後再試。"
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.details)
            .apply { offer.offerToken?.let { setOfferToken(it) } }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        _busy.value = true
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _busy.value = false
            _message.value = errorText(result)
        }
    }

    // ── PurchasesUpdatedListener ────────────────────────────────────────────
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        _busy.value = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit // 使用者自己取消，不用彈訊息
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // 例如換手機重裝：Play 說已擁有，直接查一次把權限補回來。
                queryPurchases(announce = true)
            }
            else -> _message.value = errorText(result)
        }
    }

    private fun handlePurchase(p: Purchase) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                acknowledgeIfNeeded(p)
                val plans = p.products.mapNotNull { ProPlan.fromProductId(it) }.toSet()
                _owned.value = _owned.value + plans
                grantEntitlements(plans)
                scheduleTrialReminder(p, plans)
                _message.value = when {
                    trialDaysFor(plans) > 0 ->
                        "試用開始，${trialDaysFor(plans)} 天內免費。到期前我們會提醒你一次，" +
                            "不想繼續可以隨時在 Google Play 取消。"
                    ProPlan.PLUS_LIFETIME in plans && ProPlan.PLUS_YEARLY in _owned.value ->
                        "已永久解鎖 Pro+！你先前的 Pro+ 年訂閱不會自動取消，請到 Google Play 訂閱管理取消，以免明年續扣。"
                    ProPlan.PLUS_LIFETIME in plans -> "解鎖成功，感謝支持！Pro+ 功能永久可用。"
                    ProPlan.PLUS_YEARLY in plans -> "訂閱成功，感謝支持！Pro+ 功能已開啟。"
                    ProPlan.LIFETIME in plans && ProPlan.YEARLY in _owned.value ->
                        "已永久解鎖！你先前的年訂閱不會自動取消，請到 Google Play 訂閱管理取消，以免明年續扣。"
                    ProPlan.LIFETIME in plans -> "解鎖成功，感謝支持！Pro 功能永久可用。"
                    else -> "訂閱成功，感謝支持！"
                }
            }
            Purchase.PurchaseState.PENDING ->
                _message.value = "付款處理中，完成後會自動解鎖（可能需要幾分鐘）。"
            else -> Unit
        }
    }

    /** Play 規定購買後 3 天內未確認會自動退款，所以拿到就確認。 */
    private fun acknowledgeIfNeeded(p: Purchase) {
        if (p.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    /** 這批方案裡（實務上只會有一個）帶的試用天數。 */
    private fun trialDaysFor(plans: Set<ProPlan>): Int =
        plans.mapNotNull { _offers.value[it]?.trialDays }.maxOrNull() ?: 0

    /**
     * 訂閱帶試用時排一則到期前提醒；買斷則把既有提醒取消掉——買斷之後不會再有扣款，
     * 這時候還跳「試用快到期」只會讓人以為又要收一次錢。
     */
    private fun scheduleTrialReminder(p: Purchase, plans: Set<ProPlan>) {
        if (plans.any { it.productType == BillingClient.ProductType.INAPP }) {
            TrialReminderScheduler.cancel(context)
            return
        }
        val days = trialDaysFor(plans)
        if (days > 0) TrialReminderScheduler.schedule(context, p.purchaseTime, days)
    }

    /** Pro+ 一定也算 Pro（tier 是包含關係），所以買到任一 PLUS 方案時兩個旗標都要開。 */
    private fun grantEntitlements(plans: Set<ProPlan>) {
        scope.launch {
            settings.setPro(true)
            if (plans.any { it.tier == ProTier.PRO_PLUS }) settings.setProPlus(true)
        }
    }

    /** Google Play 的訂閱管理頁；已訂閱時給使用者取消/更改的入口。 */
    fun manageSubscriptionUrl(plan: ProPlan = ProPlan.YEARLY): String =
        "https://play.google.com/store/account/subscriptions" +
            "?sku=${plan.productId}&package=${context.packageName}"

    private fun errorText(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
            "目前無法連線到 Google Play 付款服務，請稍後再試。"
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "網路連線有問題，請確認網路後再試一次。"
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "這個商品在你的地區或帳號暫時無法購買。"
        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "付款設定有誤，我們會盡快處理。"
        else -> "購買未完成（${result.responseCode}），請稍後再試。"
    }

    private companion object { const val TAG = "BillingManager" }
}

/**
 * Play 回傳的價格字串依裝置語系而定，台幣常被格式化成「$160.00」，看不出幣別、還多了無意義的小數。
 * 整數金額去掉小數；台幣補上「NT」。其他情況照 Play 的字串顯示。
 */
internal fun displayPrice(formatted: String, micros: Long, currencyCode: String): String {
    var text = formatted.trim()
    if (micros > 0 && micros % 1_000_000L == 0L) {
        text = text.replace(Regex("""[.,]00(?=\D*$)"""), "")
    }
    if (currencyCode == "TWD" && text.startsWith("$")) text = "NT$text"
    return text
}
