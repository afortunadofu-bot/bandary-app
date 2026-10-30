package com.bandary.domain.model

/**
 * 後台數據分析：把散落的計數變成能拿來做決策的指標。
 *
 * 這個檔案**只做計算**，不碰 Firestore、不碰 Compose，所以每一條指標都驗得了。
 * 後台最怕的就是「看起來很專業但算錯的數字」——錯的 KPI 比沒有 KPI 更糟，
 * 因為你會照著它做決定。
 *
 * ## 分母只有一個
 * 全站彙整（`globalStats`）一次最多掃 300 位使用者，而使用者總數是另外查的。
 * 這兩個數字**不能混用**：拿「全部使用者」當分母、「掃到的使用者」當分子，
 * 算出來的轉換率會莫名其妙地低，而且愈多人用就愈低。
 * 所以漏斗與所有比率一律用 [scannedUsers] 當分母，畫面上也要標明是抽樣。
 *
 * ## 現在算不出來的東西
 * `users/{uid}` 沒有「帳號何時建立」這個欄位，所以**留存率（D1/D7/D30）、
 * 世代分析（cohort）、隨時間變化的轉換率都算不出來**。
 * 不是不想做，是資料從來沒被記下來。v1.96.0 起開始寫 `firstSeenAt`，
 * 累積一段時間之後這些才有意義——在那之前硬畫一條留存曲線只會是假的。
 */

/** 漏斗的一階。[count] 是這一階的人數；[hint] 寫給人看的定義，避免事後誤讀。 */
data class FunnelStep(
    val label: String,
    val count: Int,
    val hint: String = ""
) {
    /** 相對上一階的轉換率。第一階沒有上一階，給 1。 */
    fun stepRate(previous: Int): Double =
        if (previous <= 0) 0.0 else count.toDouble() / previous

    /** 相對第一階的整體轉換率。 */
    fun overallRate(top: Int): Double =
        if (top <= 0) 0.0 else count.toDouble() / top
}

/** 一條有目標值的比率型 KPI。[target] 是自訂的參考線，null = 沒有參考值。 */
data class RatioKpi(
    val label: String,
    val value: Double,
    val caption: String,
    val target: Double? = null,
    /** true = 愈高愈好（轉換率）；false = 愈低愈好（沉睡率）。決定顏色往哪邊紅。 */
    val higherIsBetter: Boolean = true
) {
    val percentText: String get() = formatPercent(value)

    /** 跟目標比起來算好還是不好。沒有目標時一律當成中性。 */
    fun status(): KpiStatus {
        val t = target ?: return KpiStatus.NEUTRAL
        val good = if (higherIsBetter) value >= t else value <= t
        if (good) return KpiStatus.GOOD
        // 差一點（目標的 ±25% 以內）跟差很多要分開，不然整頁都是紅的就沒有訊號了。
        val ratio = if (higherIsBetter) value / t else t / value.coerceAtLeast(0.0001)
        return if (ratio >= 0.75) KpiStatus.WARN else KpiStatus.BAD
    }
}

enum class KpiStatus { GOOD, WARN, BAD, NEUTRAL }

/** 四捨五入到小數一位的百分比字串。0.1234 → "12.3%" */
fun formatPercent(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "—"
    val p = v * 100
    return if (p >= 99.95 || p == 0.0 || p >= 10) "${Math.round(p)}%"
    else "${Math.round(p * 10) / 10.0}%"
}

/**
 * 一份卡司字串裡的個別藝人（已套用後台用的長度過濾）。
 *
 * 為什麼需要：音樂節／拼盤的 `artistName` 常常是整份卡司黏成一串
 * （`"#Mooove! / THE PINK MINDS / シークレット花言葉 / …"`）。
 * 直接拿它去做排行，排出來的「熱門藝人第一名」就是那一整串卡司——
 * 實機回報的「掃描出來都是同一個藝人」就是這個。
 *
 * **拆分本身沿用 [splitArtistNamesPasted]**，不另外寫一套。
 * 這個專案已經有三種藝人拆分（打字用的保守版、貼上用的、追蹤用的），
 * 每一種的分隔符都是為了某個實機問題調過的
 * （團名自己帶空格、`AC/DC` 不能切、`7/18：` 要當日期前綴丟掉）。
 * 後台再寫第四套，只會變成四套會慢慢分岔的規則。
 *
 * 這裡**只多加一層長度上限**：沒有分隔符號、整段黏在一起的髒資料拆不開，
 * 當成一筆丟掉比讓它霸佔排行榜第一名好。
 */
fun artistNamesForStats(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return splitArtistNamesPasted(raw).filter { it.length <= MAX_ARTIST_NAME }
}

/** 單一藝人名稱的長度上限。超過這個長度的多半是沒切開的卡司或整段敘述。 */
private const val MAX_ARTIST_NAME = 40

/**
 * 依名單展開後重新排行。回傳 `名稱 to 次數`，由多到少。
 *
 * 跟原本的 `rank()` 差在這裡會先展開卡司，
 * 所以一場十團的音樂節會算成十位藝人各一次，而不是一個長度 300 的「藝人」。
 */
fun rankArtists(raws: List<String?>, limit: Int = Int.MAX_VALUE): List<Pair<String, Int>> {
    val counts = HashMap<String, Int>()
    raws.forEach { raw ->
        artistNamesForStats(raw).forEach { name -> counts.merge(name, 1, Int::plus) }
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { it.key to it.value }
}

/**
 * 集中度：前 [n] 名佔總量的比例。
 *
 * 投稿者、藝人這種清單要看的不只是「誰第一名」，而是**有多依賴少數幾個人**。
 * 前三名佔八成，代表只要那三個人不玩了，內容就斷了——那是風險，不是成績。
 */
fun concentration(ranking: List<Pair<String, Int>>, n: Int = 3): Double {
    val total = ranking.sumOf { it.second }
    if (total <= 0) return 0.0
    return ranking.take(n).sumOf { it.second }.toDouble() / total
}

/**
 * 環比成長率：最後一期相對前一期。資料不足兩期時回 null（不要用 0 冒充）。
 *
 * 回 null 而不是 0 很重要：0% 的意思是「持平」，但資料不足的意思是「不知道」。
 * 把不知道畫成持平，就是在用假訊號餵決策。
 */
fun momGrowth(trend: List<Pair<String, Int>>): Double? {
    if (trend.size < 2) return null
    val prev = trend[trend.size - 2].second
    val last = trend.last().second
    if (prev <= 0) return null
    return (last - prev).toDouble() / prev
}

/**
 * 簡單線性迴歸的斜率，用來判斷趨勢方向（每期平均增減幾筆）。
 * 只看頭尾會被單月的異常值騙，取迴歸線穩定得多。
 */
fun trendSlope(trend: List<Pair<String, Int>>): Double {
    val n = trend.size
    if (n < 2) return 0.0
    val xs = (0 until n).map { it.toDouble() }
    val ys = trend.map { it.second.toDouble() }
    val mx = xs.average()
    val my = ys.average()
    var num = 0.0
    var den = 0.0
    for (i in 0 until n) {
        num += (xs[i] - mx) * (ys[i] - my)
        den += (xs[i] - mx) * (xs[i] - mx)
    }
    return if (den == 0.0) 0.0 else num / den
}
