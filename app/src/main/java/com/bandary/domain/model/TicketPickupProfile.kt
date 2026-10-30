package com.bandary.domain.model

/**
 * 每個售票平台的取票規則。
 *
 * 為什麼要綁在一起：取票欄位以前是「選了平台就四個欄位全開」——不管那個平台到底要不要
 * 身分證、到底能不能在全家取。使用者得自己記得「拓元的票要去 7-11、要打身分證」，
 * 而 App 明明知道這件事卻不講。
 *
 * 這份設定做三件事：預選對的機台、把用不到的欄位收起來、在表單裡直接寫出取票步驟。
 *
 * ⚠️ 各平台的規則會變（改版、個別場次另訂）。所以這裡一律當成**建議**：
 * 機台只是預選不是限制，欄位收起來也還能手動展開。寧可提示錯也不要擋住使用者。
 */
data class TicketPickupProfile(
    /** 對應 TicketPlatform.label。 */
    val platform: String,
    /** 這個平台最常用的取票機台，第一個會被預選。空＝不用去超商。 */
    val machines: List<String> = emptyList(),
    /** 機台是否需要輸入身分證字號。 */
    val needsIdNumber: Boolean = false,
    /** 「取票序號」在這個平台的叫法，直接拿去當欄位標題。 */
    val serialLabel: String = "取票序號",
    val serialHint: String = "例：A8K3M9P2",
    /** 第二組號碼的叫法；null＝這個平台沒有第二組。 */
    val ticketNoLabel: String? = "取票號碼",
    /** 電子票券：根本不用去超商，入場掃 QR code。 */
    val eTicketOnly: Boolean = false,
    /** 取票步驟，照順序寫在表單裡。 */
    val steps: List<String> = emptyList(),
    val note: String? = null
)

private val DEFAULT_PROFILE = TicketPickupProfile(
    platform = "其他",
    machines = PICKUP_MACHINES,
    steps = listOf("到超商機台選「取票 / 領取商品」", "輸入取票序號", "列印繳費單或票券")
)

val TICKET_PICKUP_PROFILES = listOf(
    TicketPickupProfile(
        platform = "拓元 tixCraft",
        machines = listOf("7-11 ibon"),
        needsIdNumber = true,
        serialLabel = "取票序號",
        serialHint = "拓元訂單確認信上的一組英數字",
        ticketNoLabel = "訂單編號",
        steps = listOf(
            "7-11 ibon 首頁選「票券中心」→「取票」",
            "選「拓元售票」",
            "輸入訂單編號與身分證字號",
            "確認場次後列印，拿到櫃台付款 / 領票"
        ),
        note = "拓元多數場次只能在 7-11 取票，全家取不到。"
    ),
    TicketPickupProfile(
        platform = "ibon 售票系統",
        machines = listOf("7-11 ibon"),
        needsIdNumber = true,
        serialLabel = "取票序號",
        serialHint = "簡訊 / Email 裡的取票序號",
        ticketNoLabel = "訂單編號",
        steps = listOf(
            "7-11 ibon 首頁選「票券中心」→「取票」",
            "輸入取票序號與身分證字號",
            "列印購票證明單，到櫃台領票"
        )
    ),
    TicketPickupProfile(
        platform = "遠大 Ticket Plus",
        machines = listOf("全家 FamiPort", "7-11 ibon"),
        needsIdNumber = false,
        serialLabel = "取票序號",
        serialHint = "訂單通知信上的取票序號",
        ticketNoLabel = "訂單編號",
        steps = listOf(
            "全家 FamiPort 選「取票」→「遠大售票」",
            "輸入取票序號",
            "列印繳費單，到櫃台領票"
        ),
        note = "Ticket Plus 以全家為主，部分場次也開放 7-11。"
    ),
    TicketPickupProfile(
        platform = "寬宏售票",
        machines = listOf("7-11 ibon", "全家 FamiPort"),
        needsIdNumber = false,
        serialLabel = "取票序號",
        serialHint = "寬宏訂單通知信上的取票序號",
        ticketNoLabel = "訂單編號",
        steps = listOf(
            "超商機台選「取票 / 領取商品」→「寬宏售票」",
            "輸入取票序號與訂單編號",
            "列印繳費單，到櫃台領票"
        ),
        note = "寬宏多數場次在 7-11 與全家都能取。"
    ),
    TicketPickupProfile(
        platform = "KKTIX",
        machines = emptyList(),
        eTicketOnly = true,
        serialLabel = "票券序號",
        serialHint = "KKTIX 票券頁上的序號（通常不用手動輸入）",
        ticketNoLabel = null,
        steps = listOf(
            "入場出示 KKTIX App / 訂單信裡的 QR code",
            "不用去超商取票"
        ),
        note = "KKTIX 多數場次是電子票券。若這場另外要超商取票，手動開啟下面的欄位即可。"
    ),
    TicketPickupProfile(
        platform = "KLOOK",
        machines = emptyList(),
        eTicketOnly = true,
        serialLabel = "憑證編號",
        serialHint = "KLOOK 訂單的電子憑證編號",
        ticketNoLabel = "訂單編號",
        steps = listOf("入場出示 KLOOK App 裡的電子憑證"),
        note = "KLOOK 是電子憑證，不用去超商。"
    )
)

/** 找這個平台的取票規則；沒收錄的（含使用者自訂平台）給通用版本，不會變成空白。 */
fun pickupProfileFor(platform: String?): TicketPickupProfile {
    val p = platform?.trim().orEmpty()
    if (p.isBlank()) return DEFAULT_PROFILE
    return TICKET_PICKUP_PROFILES.firstOrNull { it.platform.equals(p, ignoreCase = true) }
        ?: TICKET_PICKUP_PROFILES.firstOrNull { it.platform.contains(p, true) || p.contains(it.platform, true) }
        ?: DEFAULT_PROFILE.copy(platform = p)
}
