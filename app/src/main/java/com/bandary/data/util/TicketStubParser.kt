package com.bandary.data.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * 把票根 / 購票證明單的 OCR 文字，解讀成可以直接填進表單的演唱會資訊。
 *
 * 為什麼需要：原本「掃描票券自動填寫」其實沒有在自動填寫——它只是把 OCR 的每一行
 * 列出來，讓使用者自己點「這行是名稱」「這行是場地」。日期、時間、座位、票價、
 * 取票序號一個都讀不出來，等於掃完還是要手打一遍。
 *
 * 解讀策略刻意以**標籤**為主、格式為輔：
 *   - 超商購票證明單的版面各家不同、也會改版，但欄位名稱（「交易序號」「演出時間」）
 *     相當穩定，比「第幾行是什麼」可靠得多。
 *   - OCR 會把標籤與值拆成兩行（尤其表格式版面），所以同一行找不到值時會往下一行找。
 *   - 拆不出來就留空。這裡**寧可少填也不亂猜**——票根填錯日期會讓人錯過演出，
 *     比留空要人自己打嚴重得多。
 *
 * 解讀結果一律走「確認後才套用」的流程（見 AddShowScreen 的解讀卡），
 * 不直接覆蓋使用者已經填好的欄位。
 */
object TicketStubParser {

    // ── 平台辨識 ────────────────────────────────────────────────────────────
    // 值＝TicketPlatform.label / PICKUP_MACHINES 裡的字串，才能直接餵回表單。
    private val PLATFORM_KEYWORDS = listOf(
        listOf("ibon", "IBON", "ｉｂｏｎ", "統一超商", "7-ELEVEN", "7-11", "萊爾富") to "ibon 售票系統",
        listOf("拓元", "tixCraft", "TIXCRAFT") to "拓元 tixCraft",
        listOf("KKTIX", "kktix", "KKLIVE") to "KKTIX",
        listOf("TicketPlus", "Ticket Plus", "遠大") to "遠大 Ticket Plus",
        listOf("寬宏", "KHAM", "kham") to "寬宏售票",
        listOf("KLOOK", "Klook", "客路") to "KLOOK",
        listOf("FamiPort", "FAMIPORT", "全家便利商店", "全家便利", "FamilyMart") to "其他"
    )

    /** 取票機台：只有超商機台，跟購票平台是兩件事（拓元的票也可能在 7-11 取）。 */
    private val MACHINE_KEYWORDS = listOf(
        listOf("ibon", "IBON", "ｉｂｏｎ", "7-ELEVEN", "7-11", "統一超商") to "7-11 ibon",
        listOf("FamiPort", "FAMIPORT", "全家便利商店", "全家便利", "FamilyMart") to "全家 FamiPort"
    )

    // ── 欄位標籤 ────────────────────────────────────────────────────────────
    // 每組是同義詞，愈前面愈明確。比對時已做過全形轉半形與去空白。
    private val LABEL_SHOW = listOf(
        "活動名稱", "節目名稱", "演出名稱", "商品名稱", "票券名稱", "場次名稱", "代收項目", "活動"
    )
    private val LABEL_VENUE = listOf(
        "演出地點", "活動地點", "展演地點", "場地", "地點", "場館"
    )
    private val LABEL_DATETIME = listOf(
        "演出時間", "活動時間", "演出日期", "活動日期", "入場時間", "開演時間", "場次時間", "場次", "日期", "時間"
    )
    private val LABEL_SEAT = listOf(
        "座位", "座位號碼", "席次", "區域座位", "票區"
    )
    private val LABEL_PRICE = listOf(
        "票價", "單價", "金額", "應繳金額", "合計", "總計", "售價"
    )
    // 取票序號：各家叫法差很多，這組要夠寬。
    private val LABEL_SERIAL = listOf(
        "取票序號", "交易序號", "繳費序號", "取件編號", "取票編號", "認證碼", "驗證碼", "條碼號碼", "序號"
    )
    private val LABEL_ORDER = listOf(
        "訂單編號", "訂單號碼", "票號", "票券編號", "交易編號", "訂票編號", "取票號碼"
    )

    /** 「繳費期限」「列印時間」這種時間**不是**演出時間，抓到會把日期填錯。 */
    private val DATE_TRAPS = listOf(
        "繳費期限", "繳費截止", "付款期限", "列印時間", "交易時間", "列印日期",
        "購買時間", "訂購時間", "有效期限", "開賣", "售票時間"
    )

    data class Stub(
        val platform: String? = null,
        val machine: String? = null,
        val showName: String? = null,
        val venue: String? = null,
        val city: String? = null,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val seat: String? = null,
        val price: Double? = null,
        val pickupNumber: String? = null,
        val pickupTicketNo: String? = null
    ) {
        /** 讀到幾個欄位。0 代表這張圖大概不是票根，UI 要退回原本的逐行挑選。 */
        val filledCount: Int
            get() = listOf(
                showName, venue, seat, pickupNumber, pickupTicketNo,
                date?.toString(), time?.toString(), price?.toString()
            ).count { !it.isNullOrBlank() }
    }

    fun parse(rawLines: List<String>): Stub {
        val lines = rawLines.map { normalize(it) }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return Stub()
        val all = lines.joinToString("\n")

        val platform = PLATFORM_KEYWORDS.firstOrNull { (keys, _) -> keys.any { it in all } }?.second
        val machine = MACHINE_KEYWORDS.firstOrNull { (keys, _) -> keys.any { it in all } }?.second

        val showName = valueFor(lines, LABEL_SHOW)?.let { cleanShowName(it) }
        val venueRaw = valueFor(lines, LABEL_VENUE)
        val venue = venueRaw?.let { stripAddress(it) }

        // 日期時間：標籤命中的那一行優先；沒有標籤才全文掃，且要避開「繳費期限」那類陷阱。
        val dtLine = valueFor(lines, LABEL_DATETIME)
        val date = dtLine?.let { findDate(it) } ?: findDateAvoidingTraps(lines)
        val time = dtLine?.let { findTime(it) } ?: findTimeAvoidingTraps(lines)

        val seat = valueFor(lines, LABEL_SEAT)?.let { cleanSeat(it) } ?: findSeatFreeform(lines)
        val price = valueFor(lines, LABEL_PRICE)?.let { findPrice(it) } ?: findPriceFreeform(lines)

        val serial = valueFor(lines, LABEL_SERIAL)?.let { cleanCode(it) }?.takeIf { looksLikeCode(it) }
        val order = valueFor(lines, LABEL_ORDER)?.let { cleanCode(it) }?.takeIf { looksLikeCode(it) }

        return Stub(
            platform = platform,
            machine = machine,
            showName = showName,
            venue = venue,
            city = venueRaw?.let { cityOf(it) } ?: venue?.let { cityOf(it) },
            date = date,
            time = time,
            seat = seat,
            price = price,
            pickupNumber = serial,
            pickupTicketNo = order?.takeIf { it != serial }
        )
    }

    // ── 文字正規化 ──────────────────────────────────────────────────────────

    /** 全形轉半形、統一冒號、壓掉多餘空白。OCR 對全形數字與冒號的輸出很不一致。 */
    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val c = when {
                ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()  // 全形 ASCII
                ch == '　' -> ' '                                      // 全形空白
                ch == '：' -> ':'
                else -> ch
            }
            sb.append(c)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * 找標籤對應的值：同一行 `標籤: 值`，值是空的就取下一行。
     * 下一行本身又是標籤時放棄——表格式版面常有連續兩個標題列。
     */
    private fun valueFor(lines: List<String>, labels: List<String>): String? {
        for (label in labels) {
            for ((i, line) in lines.withIndex()) {
                val idx = line.indexOf(label)
                if (idx < 0) continue
                val after = line.substring(idx + label.length).trimStart(':', ' ', '.', '-', '|')
                if (after.isNotBlank()) return after.trim()
                val next = lines.getOrNull(i + 1)?.trim().orEmpty()
                if (next.isNotBlank() && !isLabelLine(next)) return next
            }
        }
        return null
    }

    private fun isLabelLine(line: String): Boolean {
        val everyLabel = LABEL_SHOW + LABEL_VENUE + LABEL_DATETIME + LABEL_SEAT +
            LABEL_PRICE + LABEL_SERIAL + LABEL_ORDER
        // 整行幾乎就是標籤本身才算，避免「演出地點在台北小巨蛋」被誤判成標籤列。
        return everyLabel.any { line == it || line == "$it:" }
    }

    // ── 日期與時間 ──────────────────────────────────────────────────────────

    private val RE_YMD = Regex("""(\d{4})\s*[/\-.年]\s*(\d{1,2})\s*[/\-.月]\s*(\d{1,2})""")
    private val RE_ROC = Regex("""(?<!\d)(1\d{2})\s*[/\-.年]\s*(\d{1,2})\s*[/\-.月]\s*(\d{1,2})""")
    private val RE_MD = Regex("""(?<!\d)(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
    private val RE_TIME = Regex("""(?<!\d)(\d{1,2})\s*[:時]\s*(\d{2})""")
    private val RE_AMPM = Regex("""(上午|下午|AM|PM|am|pm)\s*(\d{1,2})\s*[:時]?\s*(\d{2})?""")

    fun findDate(text: String, thisYear: Int? = null): LocalDate? {
        RE_YMD.find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            return safeDate(y.toInt(), mo.toInt(), d.toInt())
        }
        // 民國年：115/09/23。必須排在西元之後，否則 2026 會被當成民國。
        RE_ROC.find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            return safeDate(y.toInt() + 1911, mo.toInt(), d.toInt())
        }
        RE_MD.find(text)?.let { m ->
            val (mo, d) = m.destructured
            val y = thisYear ?: return null   // 沒有年份就別猜，交給使用者選
            return safeDate(y, mo.toInt(), d.toInt())
        }
        return null
    }

    fun findTime(text: String): LocalTime? {
        RE_AMPM.find(text)?.let { m ->
            val mark = m.groupValues[1]
            val h = m.groupValues[2].toIntOrNull() ?: return@let
            val min = m.groupValues[3].toIntOrNull() ?: 0
            val pm = mark == "下午" || mark.equals("PM", true)
            val hour = when {
                pm && h < 12 -> h + 12
                !pm && h == 12 -> 0
                else -> h
            }
            return safeTime(hour, min)
        }
        RE_TIME.find(text)?.let { m ->
            val (h, min) = m.destructured
            return safeTime(h.toInt(), min.toInt())
        }
        return null
    }

    private fun findDateAvoidingTraps(lines: List<String>): LocalDate? =
        lines.filterNot { l -> DATE_TRAPS.any { it in l } }.firstNotNullOfOrNull { findDate(it) }

    private fun findTimeAvoidingTraps(lines: List<String>): LocalTime? =
        lines.filterNot { l -> DATE_TRAPS.any { it in l } }.firstNotNullOfOrNull { findTime(it) }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        runCatching { LocalDate(y, m, d) }.getOrNull()?.takeIf { y in 2000..2100 }

    private fun safeTime(h: Int, m: Int): LocalTime? =
        runCatching { LocalTime(h, m) }.getOrNull()

    // ── 場地 / 縣市 ─────────────────────────────────────────────────────────

    private val CITIES = listOf(
        "臺北", "台北", "新北", "桃園", "新竹", "苗栗", "臺中", "台中", "彰化", "南投",
        "雲林", "嘉義", "臺南", "台南", "高雄", "屏東", "宜蘭", "花蓮", "臺東", "台東",
        "基隆", "澎湖", "金門", "馬祖"
    )

    /** 「台北小巨蛋, 台北市南京東路四段2號」→「台北小巨蛋」：逗號後多半是地址。 */
    private fun stripAddress(s: String): String =
        s.split(',', '，').first().trim().ifBlank { s.trim() }

    private fun cityOf(s: String): String? {
        val hit = CITIES.firstOrNull { it in s } ?: return null
        // 統一寫成「臺」：演唱會表單的 TAIWAN_CITIES 用這一套，
        // 回「台北」的話選單對不上，縣市就白填了。
        return hit.replace("台", "臺")
    }

    /** 票根的活動名常帶票種後綴，「…演唱會 / 全票」的後半不是活動名。 */
    private fun cleanShowName(s: String): String =
        s.substringBefore(" 全票").substringBefore(" 優待票").trim().trim('-', '|', ':').trim()

    // ── 座位 / 票價 ─────────────────────────────────────────────────────────

    private val RE_SEAT = Regex("""[A-Za-z0-9]{0,4}\s*區\s*\d{1,3}\s*排\s*\d{1,4}\s*號""")

    /**
     * 「B區 12排 34號」→「B區12排34號」。OCR 在區／排／號之間插的空白多寡不固定，
     * 壓掉才能跟自由格式那條路走出同一個結果（不然同一張票兩條路徑給不同答案）。
     */
    private fun cleanSeat(s: String): String? {
        val compact = RE_SEAT.find(s)?.value?.replace(Regex("\\s+"), "")
        return compact ?: s.trim().takeIf { it.isNotBlank() }
    }

    private fun findSeatFreeform(lines: List<String>): String? =
        lines.firstNotNullOfOrNull { RE_SEAT.find(it)?.value?.replace(Regex("\\s+"), "") }

    /** 票價：抓最大的那個數字。票根上常同時有單價與合計，合計才是使用者記得的金額。 */
    private fun findPrice(text: String): Double? =
        Regex("""\d[\d,]*""").findAll(text)
            .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .filter { it in 50.0..100_000.0 }
            .maxOrNull()

    private fun findPriceFreeform(lines: List<String>): Double? =
        lines.filter { "NT" in it || "$" in it || "元" in it }
            .firstNotNullOfOrNull { findPrice(it) }

    // ── 序號 ────────────────────────────────────────────────────────────────

    private fun cleanCode(s: String): String =
        s.trim().takeWhile { it.isLetterOrDigit() || it == '-' }.trim('-')

    /**
     * 像不像取票代碼：純英數（可含 -）、6～24 碼、至少有一個數字。
     * 身分證格式（1 英文 + 9 數字）要排除——那是取票身份證欄位的東西，
     * 混進序號欄會讓使用者在超商機台打錯。
     */
    private fun looksLikeCode(s: String): Boolean {
        if (s.length !in 6..24) return false
        if (!s.all { it.isLetterOrDigit() || it == '-' }) return false
        if (s.none { it.isDigit() }) return false
        if (Regex("^[A-Za-z][0-9]{9}$").matches(s)) return false
        return true
    }
}
