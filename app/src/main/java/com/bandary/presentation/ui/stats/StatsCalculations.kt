package com.bandary.presentation.ui.stats

/**
 * 統計頁的純計算。
 *
 * 這個檔案刻意不 import 任何 Compose 或 Android 的東西——因為不碰 framework，
 * 就能用一般的 JVM 單元測試跑（不需要模擬器），也就是唯一測得起來的一層。
 * StatsScreen.kt 只負責把這裡算出來的結果畫出來。
 *
 * 判斷要不要放進來的標準：這段邏輯錯了會「算錯數字」還是「畫錯位置」？
 * 前者放這裡並補測試，後者留在畫面檔。
 */

// ── 圖表分類 ────────────────────────────────────────────────
// 「全部」＝完整順序，其餘各留一類，讓人不用從頭捲到尾。
internal const val STAT_CAT_ALL = "全部"
internal const val STAT_CAT_TREND = "趨勢"      // 成長曲線 / 年份 / 熱力圖 / 星期 / 回訪率
internal const val STAT_CAT_ARTIST = "藝人"     // 文字雲 / 觀看時長 / 排行 / 花費 / 同行者
internal const val STAT_CAT_PLACE = "地點"      // 國家 / 場館 / 縣市
internal const val STAT_CAT_TICKET = "票務"     // 類型 / 票價 / 售票平台
internal val STAT_CATS = listOf(STAT_CAT_ALL, STAT_CAT_TREND, STAT_CAT_ARTIST, STAT_CAT_PLACE, STAT_CAT_TICKET)

/** 目前分類要不要顯示這一區。「全部」一律顯示。 */
internal fun showStat(current: String, section: String) =
    current == STAT_CAT_ALL || current == section

// ── 距離 ────────────────────────────────────────────────────
/** 各縣市到臺北的粗略直線距離（km）。只用來算「最遠抵達」與交通距離，不需要精確。 */
fun distanceFromTaipei(city: String): Int = when (city) {
    "臺北", "新北", "臺北市", "新北市" -> 10
    "基隆", "基隆市" -> 30
    "桃園", "桃園市" -> 35
    "新竹", "新竹市", "新竹縣" -> 75
    "苗栗", "苗栗縣" -> 120
    "臺中", "臺中市" -> 165
    "彰化", "彰化縣" -> 200
    "南投", "南投縣" -> 210
    "雲林", "雲林縣" -> 240
    "嘉義", "嘉義市", "嘉義縣" -> 275
    "臺南", "臺南市" -> 320
    "高雄", "高雄市" -> 360
    "屏東", "屏東縣" -> 395
    "宜蘭", "宜蘭縣" -> 65
    "花蓮", "花蓮縣" -> 185
    "臺東", "臺東縣" -> 360
    "澎湖", "澎湖縣" -> 320
    "金門", "金門縣" -> 350
    "馬祖", "連江縣" -> 220
    else -> 100
}

// ── 圖表細節 ────────────────────────────────────────────────
/**
 * 圖表裡被點到的那一項：名稱、數值，以及「這個數字在整體裡代表什麼」。
 *
 * 圖表本身只講得出「多少」，講不出「多不多」。點一個項目直接跳去清單，中間那層
 * 解讀就永遠不會出現——這個結構就是把那層補回來的地方。
 */
internal data class ChartDetail(
    val label: String,
    val valueText: String,
    val facts: List<Pair<String, String>>,
    val drillCount: Int,
    val onDrill: (() -> Unit)?
)

/**
 * 由「這一項」與「整組資料」算出可以直接顯示的解讀。
 * 佔比 / 排名 / 與第一名的差距 / 相對平均——都是從同一組數字推出來的，不需要額外查詢。
 */
internal fun chartDetailOf(
    label: String,
    data: List<Pair<String, Int>>,
    unit: String,
    onDrill: (() -> Unit)?
): ChartDetail? {
    val idx = data.indexOfFirst { it.first == label }
    if (idx < 0) return null
    val value = data[idx].second
    val total = data.sumOf { it.second }.coerceAtLeast(1)
    // 名次一定要從「數值」算，不能用在清單裡的位置。有些圖表（例如各年份場次）是照年份排序的，
    // 用索引當名次會把「第 3 個年份」講成「第 3 名」，那是錯的資訊。
    // 同分並列同名次（兩個並列第 1，下一個是第 3），與一般排行榜的認知一致。
    val sortedDesc = data.map { it.second }.sortedDescending()
    val rank = sortedDesc.indexOfFirst { it == value } + 1
    val top = sortedDesc.firstOrNull() ?: value
    val avg = total.toDouble() / data.size

    val facts = buildList {
        add("佔比" to "${value * 100 / total}%（共 $total$unit）")
        add("排名" to "第 $rank 名／共 ${data.size} 項")
        if (rank == 1) {
            // 第二名要取「第一個比自己小的值」，不是清單的第 2 筆——同分並列時第 2 筆還是第一名。
            val second = sortedDesc.firstOrNull { it < value }
            if (second != null) add("領先" to "比第二名多 ${value - second}$unit")
        } else {
            add("與第一名" to "少 ${top - value}$unit")
        }
        // 「比平均多/少」讓單一數字有基準；剛好等於平均時講「就是平均」比講 0 更好懂。
        val diff = value - avg
        add(
            "與平均" to when {
                kotlin.math.abs(diff) < 0.05 -> "剛好是平均"
                diff > 0 -> "高於平均 ${"%.1f".format(diff)}$unit"
                else -> "低於平均 ${"%.1f".format(-diff)}$unit"
            }
        )
    }
    return ChartDetail(label, "$value$unit", facts, value, onDrill)
}

/** ARGB 轉 #RRGGBB。分享圖的顏色參數要的是字串。 */
internal fun argbToHex(argb: Int): String = String.format("#%06X", 0xFFFFFF and argb)

// ── 圖表的一句話重點 ────────────────────────────────────────
/**
 * 把一張分布圖濃縮成**一句話的重點**。
 *
 * ## 為什麼需要
 * 「看不出 insight」的實際情況是：一張甜甜圈畫了五塊、一排長條標了數字，
 * 全部都是**事實**，但沒有一個是**結論**。使用者得自己在心裡比大小、算比例，
 * 而那件事圖表本來就應該幫他做完。
 *
 * 所以每張分布圖底下補一句「最多的是什麼、占多少」。這不是裝飾文案——
 * 它回答的是看完圖之後的那個問題：「所以呢？」
 *
 * ## 什麼時候回 null（不要硬寫一句）
 *  - **只有一類**：寫「最多的是 X，占 100%」等於沒說話，而且會讓人以為漏了什麼。
 *  - **總數是 0**：沒資料就不要造句。
 *  - 並列最多時不挑其中一個講（挑哪個都是任意的），改成講「並列」。
 */
fun topShareInsight(data: List<Pair<String, Int>>, unit: String = ""): String? {
    val clean = data.filter { it.second > 0 }
    if (clean.size < 2) return null
    val total = clean.sumOf { it.second }
    if (total <= 0) return null
    val max = clean.maxOf { it.second }
    val leaders = clean.filter { it.second == max }
    val pct = max * 100 / total
    return if (leaders.size > 1) {
        "「${leaders.first().first}」等 ${leaders.size} 項並列最多，各 $max$unit"
    } else {
        "最多的是「${leaders.first().first}」，$max$unit，占 $pct%"
    }
}
