package com.bandary.domain.model

/**
 * 公開活動的修改紀錄與「誰想改什麼」。
 *
 * 為什麼要有：活動是公開共編的，但共編走的是「提案 → 管理員審核」而不是直接改
 * （公開資料被任意改寫，一個人手滑就影響所有人）。這帶來兩個副作用：
 *   1. 核准之後沒人知道剛剛改了什麼，改錯了也沒得救 → 需要修改紀錄與回溯。
 *   2. 提案在核准前只有提案者與管理員看得到，別人會重複提一樣的修改 → 提案要公開。
 *
 * 兩件事共用同一套 diff：提案是「現況 → 提案內容」的 diff，
 * 修改紀錄是「改之前 → 改之後」的 diff。顯示的東西一模一樣。
 */
data class FieldChange(
    val label: String,
    val before: String,
    val after: String
) {
    /** 空字串在畫面上要寫成「（空白）」，不然看起來像沒改。 */
    fun beforeText(): String = before.ifBlank { "（空白）" }
    fun afterText(): String = after.ifBlank { "（空白）" }
}

/**
 * 一次修改。`before` 是改之前的完整內容（不含圖片），回溯時直接套回去。
 *
 * 圖片刻意不進快照：活動的圖是 Base64 存在文件裡的，一張就好幾十 KB，
 * 進快照會讓修改紀錄很快撞上 Firestore 單文件 1MB 上限。
 * 回溯只還原文字欄位，圖片維持現況——這點在回溯的確認對話框要講清楚。
 */
data class EventRevision(
    val id: String = "",
    val eventId: String = "",
    val changedBy: String = "",
    val changedAt: Long = 0L,
    /** 「管理員編輯」「核准了 someone 的修改提案」之類的來源說明。 */
    val note: String = "",
    val changes: List<FieldChange> = emptyList(),
    /** 改之前的內容（FreeEvent 的雲端 map，已去掉圖片欄位）。 */
    val before: Map<String, Any?> = emptyMap()
)

object EventDiff {

    /**
     * 要比對的欄位。刻意**不**比對 id / status / reviewComment / editOfId /
     * createdBy / createdAt / 圖片：那些是流程欄位不是內容，列出來只會洗版。
     *
     * 票價四個欄位（ticketPrice / min / max / priceText）合成一行「票價」：
     * 使用者心裡票價就是一件事，拆成四行看不懂哪個是哪個。
     */
    private val FIELDS: List<Pair<String, (FreeEvent) -> String>> = listOf(
        "名稱" to { e -> e.name },
        "藝人" to { e -> e.artistName },
        "嘉賓" to { e ->
            e.guestsByArtist.orEmpty().entries
                .sortedBy { it.key }
                .joinToString("；") { (k, v) -> "$k：" + v.joinToString("、") }
        },
        "場地" to { e -> e.venue },
        "縣市" to { e -> e.city.orEmpty() },
        "國家" to { e -> e.countryOrDefault() },
        "日期時間" to { e -> e.dateTime.toString().replace('T', ' ') },
        "結束日期" to { e -> e.endDate?.toString().orEmpty() },
        "結束時間" to { e -> e.endTime?.toString().orEmpty() },
        "票價" to { e -> e.priceLabel() ?: if ((e.ticketPrice ?: 0.0) <= 0.0) "免費" else "" },
        "售票連結" to { e -> e.ticketUrl.orEmpty() },
        "來源網址" to { e -> e.sourceUrl.orEmpty() },
        "備註" to { e -> e.note.orEmpty() },
        "開賣時間" to { e ->
            e.saleTimes.sortedBy { it.at }
                .joinToString("；") { s -> (if (s.label.isBlank()) "" else s.label + " ") + s.at.toString().replace('T', ' ') }
        }
    )

    /** 兩個版本之間真的變了的欄位。沒變的不列——修改紀錄要能一眼看完。 */
    fun diff(before: FreeEvent, after: FreeEvent): List<FieldChange> =
        FIELDS.mapNotNull { (label, get) ->
            val b = get(before).trim()
            val a = get(after).trim()
            if (b == a) null else FieldChange(label, b, a)
        }

    /** 一行摘要，給列表用：「改了 名稱、場地 等 3 項」。 */
    fun summarize(changes: List<FieldChange>): String = when {
        changes.isEmpty() -> "沒有變更"
        changes.size <= 2 -> "改了 " + changes.joinToString("、") { it.label }
        else -> "改了 " + changes.take(2).joinToString("、") { it.label } + " 等 ${changes.size} 項"
    }

    /** 圖片欄位不進快照，理由見 EventRevision 的註解。 */
    private val SNAPSHOT_EXCLUDED = setOf("imageUri", "imageUris")

    fun stripImages(map: Map<String, Any?>): Map<String, Any?> =
        map.filterKeys { it !in SNAPSHOT_EXCLUDED }
}
