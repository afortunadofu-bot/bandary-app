package com.bandary.data.util

/**
 * 把 Google 日曆事件的「標題 / 地點」拆成藝人、活動名、場地、縣市。
 *
 * 為什麼需要：匯入以前是 `artistName = title`、`venue = location`，
 * 也就是把整串標題當成藝人名、整串地址當成場地。
 * 從別人共編的「藝人場次日曆」匯進來時，每一筆的藝人欄都會是
 * 「五月天 @ 台北小巨蛋」這種字串，統計與追蹤藝人全部跟著壞掉。
 *
 * 共編日曆的標題慣例就那幾種，這裡只做**有把握**的拆法，拆不出來就整串當活動名、
 * 藝人留空，交給使用者在匯入前的批次編輯補——猜錯比留空更難收拾。
 */
object CalendarTitleParser {

    /** 台灣縣市（與 AddShowScreen 的 TAIWAN_CITIES 同一份，含「台／臺」兩種寫法）。 */
    private val CITIES = listOf(
        "臺北", "新北", "桃園", "新竹", "苗栗", "臺中", "彰化", "南投", "雲林",
        "嘉義", "臺南", "高雄", "屏東", "宜蘭", "花蓮", "臺東", "基隆",
        "澎湖", "金門", "馬祖"
    )

    /** 藝人與場地之間常見的分隔符號。順序有意義：先找最明確的 @。 */
    private val AT_SEPARATORS = listOf(" @ ", "@", " at ", "＠")

    /** 藝人與活動名之間常見的分隔符號。 */
    private val NAME_SEPARATORS = listOf(" - ", " – ", " — ", " ｜ ", "｜", " | ", "︱")

    data class Parsed(
        val name: String,
        val artist: String,
        val venue: String,
        val city: String
    )

    /**
     * [title] 是日曆事件標題，[location] 是地點欄（可能為空）。
     *
     * 支援的標題寫法：
     *   「五月天 @ 小巨蛋」  → 藝人＝五月天、場地＝小巨蛋
     *   「五月天 - 好好好巡迴」→ 藝人＝五月天、活動名＝好好好巡迴
     *   「【好好好巡迴】五月天」→ 活動名＝好好好巡迴、藝人＝五月天
     *   其他                  → 整串當活動名，藝人留空
     */
    fun parse(title: String, location: String?): Parsed {
        val raw = title.trim()
        var name = raw
        var artist = ""
        var venue = ""

        // 1) 先看「藝人 @ 場地」——這種寫法對場地最有把握
        val atSep = AT_SEPARATORS.firstOrNull { raw.contains(it) }
        if (atSep != null) {
            val left = raw.substringBefore(atSep).trim()
            val right = raw.substringAfter(atSep).trim()
            if (left.isNotBlank() && right.isNotBlank()) {
                artist = left
                venue = right
                name = left
            }
        }

        // 2) 【活動名】藝人
        if (artist.isBlank() && raw.startsWith("【") && raw.contains("】")) {
            val inside = raw.substringAfter("【").substringBefore("】").trim()
            val rest = raw.substringAfter("】").trim()
            if (inside.isNotBlank() && rest.isNotBlank()) {
                name = inside
                artist = rest
            }
        }

        // 3) 藝人 - 活動名
        if (artist.isBlank()) {
            val sep = NAME_SEPARATORS.firstOrNull { raw.contains(it) }
            if (sep != null) {
                val left = raw.substringBefore(sep).trim()
                val right = raw.substringAfter(sep).trim()
                if (left.isNotBlank() && right.isNotBlank()) {
                    artist = left
                    name = right
                }
            }
        }

        // 地點欄比標題可靠，有就覆蓋掉標題拆出來的場地
        val loc = location?.trim().orEmpty()
        if (loc.isNotBlank()) venue = venueOf(loc)

        return Parsed(
            name = name.trim(),
            artist = artist.trim(),
            venue = venue.trim(),
            city = cityOf(listOf(loc, raw))
        )
    }

    /**
     * 從地點欄取場地名。Google 日曆的地點常常是
     * 「Legacy Taipei, 100台北市中正區八德路一段1號」這種「場地, 完整地址」，
     * 逗號前那段才是人看得懂的場地名。
     */
    fun venueOf(location: String): String {
        val first = location.split(",", "，").firstOrNull()?.trim().orEmpty()
        // 逗號前就是一串數字開頭的郵遞區號／門牌時，整串留著比切壞好
        return if (first.length >= 2 && !first.first().isDigit()) first else location.trim()
    }

    /**
     * 從候選字串裡找台灣縣市。找不到回空字串——不亂猜。
     * 「台北」「臺北」都算同一個，回傳統一用「臺」開頭的那個寫法。
     */
    fun cityOf(candidates: List<String>): String {
        candidates.forEach { text ->
            if (text.isBlank()) return@forEach
            CITIES.forEach { city ->
                val alt = city.replace("臺", "台")
                if (text.contains(city) || text.contains(alt)) return city
            }
        }
        return ""
    }
}
