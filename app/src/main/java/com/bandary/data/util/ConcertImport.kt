package com.bandary.data.util

import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import com.bandary.domain.model.FestivalSlot
import com.bandary.domain.model.TicketStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * 批量匯入解析：把一段 CSV / 貼上的清單文字解析成多筆 [Concert]（可預覽、可挑選後再存）。
 *
 * 欄位順序與「匯出 CSV」完全一致，可直接來回（export → 編輯 → import）：
 *   名稱, 藝人, 場地, 縣市, 國家, 開始日期時間, 結束日, 結束時間, 類型, 票務狀態, 票價, 購票平台, 座位, 評分, 備忘錄
 *
 * 只有「名稱或藝人其一」+「可解析的開始日期」為必填，其餘欄位可留空或整段省略。
 * 分隔符自動判斷（逗號或 Tab），支援雙引號包住含逗號 / 換行的欄位。第一列若是表頭（開頭為「名稱」）會自動略過。
 */
object ConcertImport {

    /** 欄位表頭（與匯出一致）。供畫面顯示範例用。 */
    val HEADER = listOf(
        "名稱", "藝人", "場地", "縣市", "國家", "開始日期時間", "結束日", "結束時間",
        "類型", "票務狀態", "票價", "購票平台", "座位", "評分", "備忘錄"
    )

    data class Row(
        val lineNumber: Int,        // 來源第幾筆（不含表頭，從 1 起算）
        val raw: String,            // 原始文字（預覽用）
        val concert: Concert?,      // 解析成功的演唱會；失敗為 null
        val error: String? = null   // 失敗原因（畫面顯示）
    )

    fun parse(text: String): List<Row> {
        val records = parseDelimited(text)
        val rows = mutableListOf<Row>()
        var n = 0
        records.forEachIndexed { idx, fields ->
            // 全空白列略過
            if (fields.all { it.isBlank() }) return@forEachIndexed
            // 表頭列（第一筆且第一格是「名稱」）略過
            if (idx == 0 && fields.firstOrNull()?.trim() == "名稱") return@forEachIndexed

            n++
            val raw = fields.joinToString(" | ") { it.trim() }.take(120)
            fun col(i: Int) = fields.getOrNull(i)?.trim().orEmpty()

            val name = col(0)
            val artist = col(1)
            if (name.isBlank() && artist.isBlank()) {
                rows += Row(n, raw, null, "缺少名稱與藝人")
                return@forEachIndexed
            }
            // 多位藝人自動拆分：藝人欄用 / 、／ ； | 分隔時，第一位當主藝人，其餘存成已選的音樂節名單，
            // 卡片 / 統計 / 分享才會把多位藝人都算進去。
            val artistParts = artist.split(Regex("[/、／;；|]"))
                .map { it.trim() }.filter { it.isNotBlank() }
            val mainArtist = artistParts.firstOrNull() ?: ""
            val extraFestivals = artistParts.drop(1).map {
                FestivalSlot(startTime = "", endTime = "", stage = "", artistName = it, isSelected = true)
            }
            val dateRaw = col(5)
            val dateTime = parseDateTime(dateRaw)
            if (dateTime == null) {
                rows += Row(n, raw, null,
                    if (dateRaw.isBlank()) "缺少開始日期" else "日期格式無法解析：$dateRaw")
                return@forEachIndexed
            }

            val concert = Concert(
                id = "",
                name = name,
                artistName = mainArtist,
                festivals = extraFestivals,
                venue = col(2),
                city = col(3).ifBlank { null },
                country = col(4).ifBlank { "台灣" },
                dateTime = dateTime,
                endDate = parseDate(col(6)),
                endTime = parseTime(col(7)),
                type = matchType(col(8)),
                ticketStatus = matchStatus(col(9)),
                ticketPrice = col(10).filter { it.isDigit() || it == '.' }.toDoubleOrNull(),
                ticketPlatform = col(11).ifBlank { null },
                seatInfo = col(12).ifBlank { null },
                rating = col(13).toIntOrNull()?.coerceIn(1, 5),
                notes = col(14).ifBlank { null }
            )
            rows += Row(n, raw, concert)
        }
        return rows
    }

    private fun matchType(s: String): ConcertType {
        if (s.isBlank()) return ConcertType.CONCERT
        return ConcertType.entries.firstOrNull { it.label == s || it.name.equals(s, true) }
            ?: ConcertType.CONCERT
    }

    private fun matchStatus(s: String): TicketStatus {
        if (s.isBlank()) return TicketStatus.NONE
        // 先比對現行四態的 label / name；再吸收舊字面（免費 / 待購票 / 抽票 / 未購票）以相容舊 CSV。
        return TicketStatus.selectable.firstOrNull { it.label == s || it.name.equals(s, true) }
            ?: when (s) {
                "免費" -> TicketStatus.BOUGHT
                "未購票", "待購票", "抽票", "WISHLIST", "LOTTERY", "NOT_BOUGHT" -> TicketStatus.NONE
                else -> TicketStatus.fromStored(s.uppercase())
            }
    }

    // ── 日期 / 時間容錯解析 ─────────────────────────────────────────
    // 接受：2026-07-01T19:30 / 2026/7/1 19:30 / 2026-07-01 19:30 / 2026-07-01（純日期→19:00）

    private fun parseDateTime(input: String): LocalDateTime? {
        val s = input.trim()
        if (s.isBlank()) return null
        // 先試標準 ISO（匯出格式）
        runCatching { return LocalDateTime.parse(s) }
        val norm = s.replace('/', '-').replace('T', ' ')
        val parts = norm.split(Regex("\\s+"), limit = 2)
        val date = parseDate(parts[0]) ?: return null
        val time = if (parts.size > 1) parseTime(parts[1]) else null
        val t = time ?: LocalTime(19, 0)
        return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, t.hour, t.minute)
    }

    private fun parseDate(input: String): LocalDate? {
        val s = input.trim()
        if (s.isBlank()) return null
        runCatching { return LocalDate.parse(s) }
        val p = s.replace('/', '-').split('-')
        if (p.size < 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        return runCatching { LocalDate(y, m, d) }.getOrNull()
    }

    private fun parseTime(input: String): LocalTime? {
        val s = input.trim()
        if (s.isBlank()) return null
        runCatching { return LocalTime.parse(s) }
        val p = s.split(':')
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val mi = p.getOrNull(1)?.toIntOrNull() ?: 0
        return runCatching { LocalTime(h.coerceIn(0, 23), mi.coerceIn(0, 59)) }.getOrNull()
    }

    // ── 分隔文字解析（支援雙引號、引號內逗號 / 換行）────────────────
    // 分隔符：整段若含 Tab 而不含逗號 → 用 Tab，否則用逗號。

    private fun parseDelimited(text: String): List<List<String>> {
        if (text.isBlank()) return emptyList()
        val delim = if (text.contains('\t') && !text.contains(',')) '\t' else ','
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        fun endField() { fields.add(field.toString()); field.setLength(0) }
        fun endRecord() { endField(); records.add(fields); fields = mutableListOf() }
        while (i < n) {
            val ch = text[i]
            when {
                inQuotes -> when {
                    ch == '"' && i + 1 < n && text[i + 1] == '"' -> { field.append('"'); i++ }
                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }
                ch == '"' -> inQuotes = true
                ch == delim -> endField()
                ch == '\r' -> { /* 略過，交給 \n 收尾 */ }
                ch == '\n' -> endRecord()
                else -> field.append(ch)
            }
            i++
        }
        // 收尾最後一筆（若有殘留）
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }
}
