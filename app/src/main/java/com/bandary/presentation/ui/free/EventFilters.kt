package com.bandary.presentation.ui.free

import com.bandary.domain.model.FreeEvent

/**
 * 活動篩選用的純函式（不碰 Compose，方便單元測試）。
 */

/**
 * 台灣縣市，依北→南→東→離島排。已經是 [normalizeCity] 後的寫法（不含市／縣），
 * 所以新竹市與新竹縣、嘉義市與嘉義縣各併成一個選項。
 *
 * 面板上永遠列出全部縣市：以前只列「資料裡有的」，大部分活動沒填縣市，
 * 結果整排只剩「臺北 3」，使用者根本選不到自己要的地方（實機回報）。
 */
val TAIWAN_CITIES: List<String> = listOf(
    "基隆", "臺北", "新北", "桃園", "新竹", "苗栗",
    "臺中", "彰化", "南投", "雲林",
    "嘉義", "臺南", "高雄", "屏東",
    "宜蘭", "花蓮", "臺東",
    "澎湖", "金門", "連江"
)

/**
 * 這場活動在哪個縣市。有填縣市就用它；沒填就從場地名稱找縣市字樣
 * （「臺北流行音樂中心」「高雄駁二」這類場地名本身就帶縣市）。找不到回 null。
 */
fun eventCity(e: FreeEvent): String? {
    e.city?.trim()?.takeIf { it.isNotBlank() }?.let { return normalizeCity(it) }
    val venue = e.venue.replace("台", "臺")
    // 「馬祖」是連江縣的通稱，場地名常只寫馬祖。
    if ("馬祖" in venue) return "連江"
    // 「新北投」在臺北市，不能被下面的「新北」吃掉。
    if ("北投" in venue) return "臺北"
    return TAIWAN_CITIES.firstOrNull { it in venue }
}

/**
 * 票價篩選的區間。可複選，符合任一格就顯示。每一格是 (min, max]，
 * 例如 500 算在「500 以下」、501 算在「501–1000」。
 */
enum class PriceBucket(val label: String, val min: Double, val max: Double) {
    FREE("免費", 0.0, 0.0),
    UNDER_500("500 以下", 0.0, 500.0),
    TO_1000("501–1000", 500.0, 1000.0),
    TO_2000("1001–2000", 1000.0, 2000.0),
    TO_3000("2001–3000", 2000.0, 3000.0),
    OVER_3000("3001 以上", 3000.0, Double.MAX_VALUE);

    fun matches(e: FreeEvent): Boolean {
        // 「免費」的判斷跟卡片上的免費標籤同一套規則：ticketPrice 為 null 或 0。
        val price = e.ticketPrice ?: 0.0
        if (this == FREE) return price == 0.0
        if (price == 0.0) return false
        // 有票價區間就用區間（早鳥 400／現場 500 → 400–500），只要跟這一格有重疊就算。
        val lo = e.minPrice ?: price
        val hi = e.maxPrice ?: price
        return lo <= max && hi > min
    }
}

/**
 * 自訂票價區間（使用者自己輸入上下限，兩端都含）。只填一端就是「N 以上」或「N 以下」。
 * 免費活動當作 0 元：下限空白或 0 時會包含免費活動。
 */
fun matchesCustomPrice(e: FreeEvent, minIn: Double?, maxIn: Double?): Boolean {
    // 上下限打反了（最低 2000、最高 500）就自動對調，不要默默變成 0 筆。
    val swap = minIn != null && maxIn != null && minIn > maxIn
    val min = if (swap) maxIn else minIn
    val max = if (swap) minIn else maxIn
    val price = e.ticketPrice ?: 0.0
    val lo = if (price == 0.0) 0.0 else e.minPrice ?: price
    val hi = if (price == 0.0) 0.0 else e.maxPrice ?: price
    return lo <= (max ?: Double.MAX_VALUE) && hi >= (min ?: 0.0)
}

/** 自訂票價區間的顯示文字。兩端都空回 null。 */
fun customPriceLabel(minIn: Double?, maxIn: Double?): String? {
    val swap = minIn != null && maxIn != null && minIn > maxIn
    val min = if (swap) maxIn else minIn
    val max = if (swap) minIn else maxIn
    fun f(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    return when {
        min != null && max != null -> "NT$${f(min)}–${f(max)}"
        min != null -> "NT$${f(min)} 以上"
        max != null -> "NT$${f(max)} 以下"
        else -> null
    }
}

/** 「我的」篩選：可複選，符合任一類就顯示。 */
enum class MineFilter(val label: String) {
    INTERESTED("有興趣"),
    /** 已經按「參加」加進自己演唱會的。 */
    JOINED("我參加的"),
    /** 自己投稿的（任何審核狀態）。 */
    SUBMITTED("我投稿的")
}
