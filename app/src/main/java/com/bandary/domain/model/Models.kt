package com.bandary.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

// ── Concert (演唱會) ──────────────────────────────────────────
data class Concert(
    val id: String = "",
    val name: String = "",
    val artistName: String = "",
    val venue: String = "",
    val hall: String? = null,
    val address: String? = null,             // 地址（與場地名分開）
    val city: String? = null,
    val country: String? = "台灣",           // 國家（預設台灣）
    val dateTime: LocalDateTime,
    val endTime: LocalTime? = null,
    val endDate: LocalDate? = null,          // 多日活動結束日（連續）；null 或等於開始日視為單日
    val extraDates: List<LocalDate> = emptyList(), // 額外日期（非連續的多日活動，例如巡演 7/1、7/5、7/8）
    val type: ConcertType = ConcertType.CONCERT,
    val customType: String? = null,          // type==OTHER 時的自訂類型名稱（例如「音樂劇」「Livehouse」）
    val ticketStatus: TicketStatus = TicketStatus.NONE,
    val ticketPrice: Double? = null,
    val ticketPlatform: String? = null,
    val ticketUrl: String? = null,           // 售票主頁（購票 / 搶票連結）
    val infoUrl: String? = null,             // 演唱會說明頁（活動資訊，可與售票頁不同）
    val pickupNumber: String? = null,        // 取票序號
    val pickupIdNumber: String? = null,      // 取票身份證（部分平台需輸入身分證）
    val pickupTicketNo: String? = null,      // 取票號碼（另一組號碼，與序號分開）
    val pickupMachine: String? = null,       // 取票機台（FamiPort / ibon…或自訂）
    // 取票序號截圖（訂單信 / 售票 App 的畫面）。在超商機台前翻訂單信最慌，
    // 存一張截圖比記一串英數字實在。跟身分證欄一樣屬於敏感內容，詳情頁預設收折。
    val pickupShotUri: String? = null,
    val seatInfo: String? = null,
    val setlist: List<String> = emptyList(), // 歌單（每首一列，依演出順序）
    val companions: List<String> = emptyList(), // 同行者暱稱
    val dayCovers: List<String> = emptyList(),  // 多日活動：各日期的封面圖（對應 occurrenceDates 順序，空字串＝沿用主封面）
    val notes: String? = null,
    val rating: Int? = null,
    val rewatchIntent: RewatchIntent? = null,
    val photoUri: String? = null,            // 封面（沿用）
    val photoUris: List<String> = emptyList(), // 額外多張照片
    val seatMapUri: String? = null,          // 場位圖
    val timetableUris: List<String> = emptyList(), // 節目表 / 演出時間表（可多張，最多 5）——與封面 / 演出照片分開
    val coverColorHex: String? = null,       // 沒封面時，卡片底圖的自訂漸層基底色（#RRGGBB）
    val coverGradientLight: Boolean = false, // 有封面圖時，疊字漸層用淺色（true）或深色（false）
    val reminders: List<ReminderOffset> = listOf(ReminderOffset.ONE_WEEK, ReminderOffset.ONE_DAY),
    val customReminderMinutes: Int? = null,  // 舊：單一自訂提醒（演出前幾分鐘）——保留讀取舊資料，新資料改用下面的清單
    val extraReminderMinutes: List<Int> = emptyList(),  // 多個自訂提醒：各為演出前幾分鐘
    val festivals: List<FestivalSlot> = emptyList(),
    // 多組開賣時間（會員預購 / 一般售票 / 加場…）。每組有名稱與時間，名稱可留空。
    // 舊資料的單一 saleStartAt 會在 Room 遷移時轉成這裡的第一筆（名稱空白）。
    val saleTimes: List<SaleTime> = emptyList(),
    val saleReminderMinutes: Int? = null,    // 搶票提醒：開賣前幾分鐘，**所有開賣時間共用**（null = 預設 60 分）
    val syncToCalendar: Boolean = false,     // 是否於新增時同步到 Google 日曆（逐場自訂）
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,             // 軟刪除時間戳（在垃圾桶裡，保留 7 天）；null＝未刪除
    // 最後修改時間。雲端同步用它決定「手機與網頁誰比較新」（last-write-wins），
    // 所以任何會改動內容的路徑都必須更新它，否則那次修改同步時會被舊資料蓋掉。
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 這場活動的所有發生日期（去重排序）：主日期 + 連續 endDate 區間 + 非連續 extraDates。
 * 月曆標記 / 詳情顯示 / widget 皆以此為單一真相來源。
 */
fun Concert.occurrenceDates(): List<LocalDate> {
    val start = dateTime.date
    val set = linkedSetOf(start)
    endDate?.takeIf { it > start }?.let { end ->
        var d = start
        while (d < end) { d = d.plus(1, DateTimeUnit.DAY); set.add(d) }
    }
    set.addAll(extraDates)
    return set.sorted()
}

/** 是否為多日活動（發生日超過一天）。 */
fun Concert.isMultiDay(): Boolean = occurrenceDates().size > 1

/**
 * 這場演出**實際結束的時刻**。
 *
 * 用「最後一個發生日」而不是開始日：連續多日（`endDate`）與非連續巡演（`extraDates`）
 * 都算在內，所以演到第三天的活動不會在第一天結束時就被當成過去式。
 *
 * 沒填結束時間就算到那天的最後一刻。**刻意不猜一個預設長度**（例如「開演後三小時」）：
 * 猜短了會讓人在現場看到自己的演出被歸成歷史，猜長了又沒解決問題。
 * 算到當天結束，使用者的直覺是「今天的場次今天都還在『即將到來』」，這跟事實一致。
 */
fun Concert.endMoment(): LocalDateTime {
    val lastDay = occurrenceDates().lastOrNull() ?: dateTime.date
    return endTime?.let { LocalDateTime(lastDay, it) }
        ?: LocalDateTime(lastDay, LocalTime(23, 59, 59))
}

/**
 * 這場演出結束了嗎（相對於 [now]）。「即將到來 / 歷史紀錄」就看這個。
 *
 * ## 原本是壞的
 * 分頁原本用 `dateTime >= 本月第一天` 分流，於是**這個月已經看完的場次會繼續留在
 * 「即將到來」直到月底換月**。實機回報：「活動結束後補填結束時間，但活動仍然停留在
 * 『即將到來』分頁，不知道怎麼讓它移去『歷史紀錄』。」
 *
 * 他確實沒辦法——那個判定跟他填的任何欄位都無關，只跟日曆換不換月有關。
 * 使用者會以為是自己漏填了什麼，然後去翻每一個欄位，而答案是「等月底」。
 *
 * 當初「本月起」是刻意的，為了一眼看到近期場次。但代價是分頁的名字在說謊：
 * 已經結束的東西不叫「即將到來」。本月已結束的場次在「歷史紀錄」的最上面，
 * 一樣找得到，而且位置是對的。
 */
fun Concert.hasEnded(now: LocalDateTime): Boolean = endMoment() < now

/** 取某一天的封面圖：多日活動若該日設了專屬封面就用它，否則沿用主封面。 */
fun Concert.coverForDate(date: LocalDate): String? {
    val idx = occurrenceDates().indexOf(date)
    val day = dayCovers.getOrNull(idx)?.takeIf { it.isNotBlank() }
    return day ?: photoUri?.takeIf { it.isNotBlank() } ?: photoUris.firstOrNull()
}

// 從 Google 日曆匯入的活動，備註會以此字串開頭（單一真相：匯入端與標記端共用）。
const val CALENDAR_IMPORT_NOTE = "從 Google 日曆匯入"

/** 是否為從 Google 日曆匯入的活動（用來在卡片 / 詳情頁加標記）。 */
fun Concert.isFromCalendarImport(): Boolean = notes?.startsWith(CALENDAR_IMPORT_NOTE) == true

/**
 * 一組開賣時間。[label] 是這一輪的名稱（會員預購 / 一般售票…），可留空。
 * 提醒設定不放這裡——依產品決定「提前幾分鐘」是整場共用一個值（見 Concert.saleReminderMinutes）。
 */
data class SaleTime(
    val label: String = "",
    val at: LocalDateTime
)

/**
 * 最早的一組開賣時間。舊程式（月曆 / widget / 詳情頁）都以「這場什麼時候開賣」為單位，
 * 改成多組後仍需要一個代表值，這裡統一由 saleTimes 推導，**不另存欄位**避免兩份資料打架。
 */
val Concert.saleStartAt: LocalDateTime?
    get() = saleTimes.minByOrNull { it.at }?.at

/** 同上，活動端的代表值。 */
val FreeEvent.saleStartAt: LocalDateTime?
    get() = saleTimes.minByOrNull { it.at }?.at

/** 顯示用：「會員預購 8/1 12:00」；沒名稱就只給時間。 */
fun SaleTime.display(): String {
    val t = "${at.monthNumber}/${at.dayOfMonth} " +
        "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    return if (label.isBlank()) t else "$label $t"
}

// 搶票提醒：開賣前多久（分鐘）。UI 用。
val SALE_REMINDER_OPTIONS = listOf(
    10 to "10 分前", 30 to "30 分前", 60 to "1 小時前", 180 to "3 小時前", 1440 to "1 天前"
)

enum class ConcertType(val label: String) {
    CONCERT("演唱會"),
    FESTIVAL("音樂節"),
    FAN_MEETING("見面會"),
    ONLINE("線上直播"),
    EXHIBITION("展覽"),
    STAGE_PLAY("舞台劇"),
    MUSICAL("音樂劇"),
    TALK_SHOW("脫口秀"),
    FREE("免費活動"),
    OTHER("自訂")
}

/** 顯示用的類型名稱：OTHER 且有填自訂名稱時用自訂名稱，否則用內建 label。 */
fun Concert.typeLabel(): String =
    if (type == ConcertType.OTHER && !customType.isNullOrBlank()) customType!!.trim() else type.label

// 星期標籤（週一..週日，對應 isoDayNumber 1..7）。統計星期分布與鑽取共用同一組。
val WEEKDAY_LABELS = listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")

/** 這場演出是星期幾（週一..週日）。 */
fun Concert.weekdayLabel(): String = WEEKDAY_LABELS[(dateTime.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)]

/**
 * 這場演出落在哪個票價區間（與統計「票價分布」的 6 區間一致）；
 * 免費 = 類型免費 / 票券狀態免費 / 0 元。未填票價且非免費回 null（不歸入任何區間）。
 */
fun Concert.priceBucketLabel(): String? {
    // 免費 = 類型免費 / 0 元（票券狀態已不含「免費」，改由票價 0 表示）。
    val free = type == ConcertType.FREE || ticketPrice == 0.0
    if (free) return "免費"
    val p = ticketPrice ?: return null
    if (p <= 0) return null
    return when {
        p <= 800 -> "1–800"
        p <= 1500 -> "800–1.5k"
        p <= 2500 -> "1.5k–2.5k"
        p <= 4000 -> "2.5k–4k"
        else -> "4k+"
    }
}

/**
 * 購票狀況：無 → 已購票 → 已取票 → 已看過 的生命週期四態（編輯頁改版 spec §4.3）。
 * 「無」＝還沒買 / 未設定；免費不是狀態，改由票價 0 表示（見 [priceBucketLabel]）。
 * 歷史值（未購票 / 待購票 / 抽票）都吸收成「無」，免費吸收成「已購票」。
 */
enum class TicketStatus(val label: String) {
    NONE("無"),
    BOUGHT("已購票"),
    PICKED_UP("已取票"),
    WATCHED("已看過");

    companion object {
        /** 供 SegmentedRow 用：四態全部可選（「無」也是明確選項）。 */
        val selectable = listOf(NONE, BOUGHT, PICKED_UP, WATCHED)

        /**
         * 從本機 / 雲端存的字串還原成四態，並吸收所有歷史字面——避免舊資料「自動變成無以外的錯值」：
         * 已購票 / 已取票 / 已看過 直通；免費(FREE) → 已購票；
         * 未購票(NOT_BOUGHT) / 待購票(WISHLIST) / 抽票(LOTTERY) / 未設定 → 無。
         */
        fun fromStored(name: String?): TicketStatus = when (name) {
            "BOUGHT" -> BOUGHT
            "PICKED_UP" -> PICKED_UP
            "WATCHED" -> WATCHED
            "FREE" -> BOUGHT
            else -> NONE
        }
    }
}

// Built-in platforms; AddShow form supports custom string platforms too.
enum class TicketPlatform(val label: String) {
    TIXCRAFT("拓元 tixCraft"),
    TICKETPLUS("遠大 Ticket Plus"),
    KKTIX("KKTIX"),
    IBON("ibon 售票系統"),
    KHAM("寬宏售票"),
    KLOOK("KLOOK"),
    OTHER("其他")
}

enum class RewatchIntent(val label: String) {
    WILL("會"),
    MAYBE("不一定"),
    WONT("不會")
}

// 取票機台常用選項（超商 + 機台名；也可自訂輸入）
val PICKUP_MACHINES = listOf("7-11 ibon", "全家 FamiPort")

// label：設定畫面的選項（「演出前多久提醒」）。
// countdown：通知內文用的倒數語氣。不能拿 label 直接放進通知：「1 週前」在通知裡會被讀成「一週前的演出」。
enum class ReminderOffset(val label: String, val countdown: String) {
    ONE_WEEK("1 週前", "還有 1 週開演"),
    THREE_DAYS("3 天前", "還有 3 天開演"),
    ONE_DAY("1 天前", "還有 1 天開演"),
    TWO_HOURS("2 小時前", "再 2 小時開演"),
    THIRTY_MIN("30 分前", "再 30 分鐘開演")
}

/** 自訂提醒（演出前 N 分鐘）的倒數文字。 */
fun reminderCountdown(minutes: Int): String = when {
    minutes % 1440 == 0 -> "還有 ${minutes / 1440} 天開演"
    minutes % 60 == 0 -> "再 ${minutes / 60} 小時開演"
    else -> "再 $minutes 分鐘開演"
}

/** 入場提醒通知內文：倒數・日期時間・活動名・場地，例如「還有 1 週開演・9/23（三）19:30・秋分・Legacy Taipei」。 */
fun entryReminderMessage(countdown: String, dateTime: LocalDateTime, name: String, venue: String): String {
    val weekday = "一二三四五六日"[dateTime.dayOfWeek.isoDayNumber - 1]
    val whenText = "${dateTime.monthNumber}/${dateTime.dayOfMonth}（$weekday）%02d:%02d".format(dateTime.hour, dateTime.minute)
    return listOf(countdown, whenText, name, venue).filter { it.isNotBlank() }.joinToString("・")
}

data class FestivalSlot(
    val startTime: String,
    val endTime: String,
    val stage: String,
    val artistName: String,
    val isSelected: Boolean = false,
    val day: String = "",         // 多日音樂祭：這個團屬於哪一天（yyyy-MM-dd），單日留空
    // 客串／嘉賓：算進「看過哪些藝人」，但**不併入現場時數計算**——
    // 嘉賓只上台唱一兩首，把整場時數再算他一份會嚴重灌水。
    val feat: Boolean = false,
    // 這位嘉賓是誰找來的（主要藝人名）。只有 feat = true 時有意義，供 UI 排回同一行。
    val featOf: String = ""
)

// ── DailySong (每日一歌) ──────────────────────────────────────
data class DailySong(
    val id: String = "",
    val date: LocalDate,
    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String? = null,
    val reaction: SongReaction? = null,
    val memo: String? = null,
    val rank: Int = 0,                       // 同一天內排序，0 = 最喜歡（置頂）
    val spotifyTrackId: String? = null,      // 預留給 Spotify 串接
    val createdAt: Long = System.currentTimeMillis(),
    // 這首歌關聯到哪一場演唱會（從該場歌單一鍵記錄時自動帶入）。null＝一般的每日選歌。
    // 場次被刪除時這裡會變成孤兒 id，讀取端一律容忍查不到。
    val concertId: String? = null
)

// 感受標籤：DB 只存 enum name，這裡的中文僅是顯示字串，改字不需遷移資料。
enum class SongReaction(val label: String) {
    FIRE("精彩"),
    TREASURE("收藏"),
    TEARS("感動"),
    PERFECT("難忘")
}

// ── FreeEvent（免費聽團活動，Firestore 雲端共用）─────────────────
// 管理員新增 / 從 Google 日曆匯入；所有使用者可讀、可一鍵複製為自己的演唱會。
data class FreeEvent(
    val id: String = "",
    val name: String = "",
    val artistName: String = "",
    // 嘉賓 / feat.（客串）：主藝人 → 他的嘉賓名單，顯示成「主藝人 feat. 嘉賓」。
    // 分藝人存（與演唱會表單一致）；活動不進個人統計，所以只是名單、不標時數。
    val guestsByArtist: Map<String, List<String>> = emptyMap(),
    val venue: String = "",
    val city: String? = null,
    // 國家。null / 空白一律當台灣——既有資料全是台灣的，補一個預設值比回頭改幾千筆實在。
    // 之後要收日韓場次、Billboard Live 這種跨國場地，就靠這個欄位分流與篩選。
    val country: String? = null,
    val dateTime: LocalDateTime,
    val endDate: LocalDate? = null,          // 多日活動結束日（連續）；null 或等於開始日視為單日
    val endTime: LocalTime? = null,          // 結束時間
    val ticketPrice: Double? = null,         // 票價（null / 0 = 免費）——活動可含付費
    // 票價區間。現實票價常常是「早鳥 400／現場 500」「一日票 1800、雙日 3200」，
    // 一個數字表達不了。兩個都有值時卡片顯示「NT$400–500」，否則退回 ticketPrice / priceText。
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    // 票價說明文字。現實票價常常不是一個數字（「早鳥 400／現場 500」「$300 起」「一日票 1800」），
    // 硬塞進 Double 會失真。有填就以這串為主顯示，ticketPrice 仍保留給「免費 / 付費」判斷與統計。
    val priceText: String? = null,
    val ticketUrl: String? = null,           // 售票 / 報名連結
    // 多組開賣（搶票）時間，格式與演唱會同一套 SaleTime。活動免費時通常留空。
    val saleTimes: List<SaleTime> = emptyList(),
    val note: String? = null,
    val sourceUrl: String? = null,           // 來源網址（活動頁 / 粉專）
    val imageUri: String? = null,            // 主圖（＝imageUris 第一張）；保留給舊資料 / 卡片 / widget
    val imageUris: List<String> = emptyList(), // 活動圖，最多 5 張（本機壓縮後的 Base64；或舊資料的下載網址）
    val status: String = FreeEventStatus.APPROVED,  // 審核狀態：pending / approved / rejected
    val reviewComment: String? = null,       // 管理員審核意見（駁回時可填，非必填）
    val createdBy: String? = null,           // 新增者 email
    val createdAt: Long = System.currentTimeMillis(),
    // 非管理員的「修改提案」：值＝被修改的活動 id。這是一份待審核的獨立文件，
    // 核准時才把內容套回原活動並刪掉提案。Firestore 規則是文件粒度、一般人只有 create，
    // 所以「改別人的活動」只能用新增一份提案來表達，不能直接寫原文件。
    val editOfId: String? = null
)

/** 活動圖清單（相容舊的單張 imageUri）：優先用 imageUris，空的話退回 [imageUri]。 */
val FreeEvent.images: List<String>
    get() = imageUris.ifEmpty { listOfNotNull(imageUri?.takeIf { it.isNotBlank() }) }

/** 免費活動的所有發生日期（多日活動＝起始日到 endDate 連續區間；單日就一天）。 */
fun FreeEvent.occurrenceDates(): List<LocalDate> {
    val start = dateTime.date
    val end = endDate?.takeIf { it > start } ?: return listOf(start)
    val out = mutableListOf<LocalDate>()
    var d = start
    while (d <= end) { out.add(d); d = d.plus(1, DateTimeUnit.DAY) }
    return out
}

/** 免費活動是否多日。 */
fun FreeEvent.isMultiDay(): Boolean = endDate?.let { it > dateTime.date } == true

/** 顯示用的完整卡司：每位主藝人接上自己的嘉賓「主藝人 feat. 嘉賓1、嘉賓2」，再以 / 串起。 */
fun FreeEvent.castLabel(): String {
    val mains = artistName.split(" / ").map { it.trim() }.filter { it.isNotBlank() }
    if (mains.isEmpty()) return artistName
    return mains.joinToString(" / ") { m ->
        val g = guestsByArtist[m].orEmpty().filter { it.isNotBlank() }
        if (g.isEmpty()) m else "$m feat. ${g.joinToString("、")}"
    }
}

// 免費活動審核狀態（以字串存 Firestore，舊資料無此欄位時視為已核准）
object FreeEventStatus {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val REJECTED = "rejected"
    fun label(value: String): String = when (value) {
        PENDING -> "審核中"
        APPROVED -> "已通過"
        REJECTED -> "已駁回"
        else -> value
    }
}

// ── Feedback（App 內意見回饋，Firestore 雲端）──────────────────
data class Feedback(
    val id: String = "",
    val category: String = FeedbackCategory.OTHER,  // bug / feature / other
    val area: String? = null,                        // 哪些區塊（FeedbackCategory.AREAS，可複選，「、」分隔）
    val issueType: String? = null,                   // 哪些問題 / 建議（FeedbackCategory.issueTypes，可複選，「、」分隔）
    val message: String = "",
    val contact: String? = null,                     // 使用者填的聯絡方式（可選）
    val rating: Int? = null,                          // 滿意度 1–5（可選）
    val nps: Int? = null,                             // 推薦分數 0–10（NPS，可選）
    val usageFrequency: String? = null,              // 使用頻率（每天 / 每週 / 每月 / 偶爾）
    val favoriteFeature: String? = null,             // 最常用 / 最喜歡的功能（可選）
    val role: String? = null,                        // 身分（樂迷 / 主辦或工作人員 / 其他）
    val source: String? = null,                      // 從哪裡知道 Bandary
    val wishes: String? = null,                      // 最希望加強的方向（多選，「、」分隔）
    val painPoint: String? = null,                   // 最困擾 / 最不滿意的地方
    val payIntent: String? = null,                   // 付費意願
    val appVersion: String? = null,                  // 送出時的 App 版本（自動帶入，回報問題時對得上版本）
    val createdBy: String? = null,                   // 登入 email
    val createdAt: Long = System.currentTimeMillis()
)

object FeedbackCategory {
    const val BUG = "bug"
    const val FEATURE = "feature"
    const val OTHER = "other"
    fun label(value: String): String = when (value) {
        BUG -> "問題回報"
        FEATURE -> "功能建議"
        else -> "其他"
    }

    /**
     * 出問題 / 想改的是 App 的哪個區塊。先分好類，開發者一眼就知道要去看哪裡，
     * 不用從一段描述裡猜（實機回報：回饋都是一大段文字，很難分）。
     */
    val AREAS: List<String> = listOf(
        "首頁 / 演唱會清單", "新增 / 編輯演唱會", "演唱會詳情", "活動（免費活動）",
        "每日選歌", "統計", "票根分享", "批量匯入",
        "雲端備份 / 還原", "Google 日曆", "登入 / 帳號", "好友 / 共享日曆",
        "通知 / 提醒", "Pro / 付費", "其他"
    )

    /** 依回饋類型給的細項。「其他」類型不問細項。 */
    fun issueTypes(category: String): List<String> = when (category) {
        BUG -> listOf(
            "閃退 / 當掉", "按了沒反應", "資料不見 / 錯誤", "照片問題",
            "畫面跑版 / 顯示錯", "很慢 / 卡頓", "同步 / 備份失敗", "其他"
        )
        FEATURE -> listOf("想要新功能", "改善現有功能", "操作更方便", "畫面 / 設計", "其他")
        else -> emptyList()
    }
}

// ── 好友 / 共享日曆（Firestore 雲端）─────────────────────────────
// 使用者「發布」自己的即將到來演出（精簡版）到 shared_calendars/{uid}，
// 好友（已登入者）可依 email 找到並檢視。好友清單存在 users/{uid}/friends。
data class Friend(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val handle: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

// 好友請求（雙向流程）：A 送出 → 寫進 B 的收件匣 users/{B}/friend_requests/{A}；
// B 接受後雙方互列為好友。fromEmail 只在對方開放 email 搜尋時才有意義。
data class FriendRequest(
    val fromUid: String = "",
    val fromHandle: String = "",
    val fromDisplayName: String = "",
    val fromEmail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class SharedShow(
    val name: String = "",
    val artistName: String = "",
    val venue: String = "",
    val city: String? = null,
    val dateTime: LocalDateTime,
    val type: String = "",       // ConcertType.name
    val wantTicket: Boolean = false  // 還沒有票（ticketStatus = NOT_BOUGHT）
)

/**
 * 好友可見的「年度歌單」——**只有聚合結果**：總數、前幾名藝人與歌曲。
 * 刻意不含日期、備忘（memo）與感受標籤：選歌日記的價值來自誠實記錄，
 * 一旦每天寫什麼都被朋友看到，記錄就會開始表演化。預設不分享，要在設定裡自己打開。
 */
data class SharedTopSong(val title: String = "", val artist: String = "", val count: Int = 0)

data class SharedSongProfile(
    val year: Int = 0,
    val totalSongs: Int = 0,
    val liveSongs: Int = 0,                       // 其中在演唱會現場記下的
    val topArtists: List<String> = emptyList(),   // 前 5 名
    val topSongs: List<SharedTopSong> = emptyList() // 前 10 首
)

data class SharedCalendar(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val handle: String = "",                    // 使用者唯一好友代碼（可被搜尋加友）
    val discoverableByEmail: Boolean = false,   // 是否允許他人用 email 搜尋到我
    val updatedAt: Long = 0L,
    val shows: List<SharedShow> = emptyList(),
    val interestedFreeEventIds: List<String> = emptyList(),  // 有興趣的免費活動 id（供好友互看）
    val songProfile: SharedSongProfile? = null               // 年度歌單（需自行開啟分享，否則為 null）
)

// ── 藝人名稱處理（追蹤藝人 / 貼上卡司都用這一套）────────────────────────
// 分隔符：｜ | 、 ／ ; ； 換行，外加「全形空格」與「連續兩個以上半形空格」。
// 單一半形空格不能當分隔——很多團名本身就有空格（Sabrina 胡恂舞、The Dinoaur'y skin 恐龍的皮），
// 拆下去會把一個團變成好幾個。
// 另含「前後有空白的半形斜線」： "A / B / C" 是官方卡司最常見的寫法。
// 只認有空白的，所以 AC/DC、7/18 不會被切開。
private val ARTIST_SEPARATORS = Regex("""(?:[｜|、／;；\r\n　]|\x20{2,}|\s+/\s+)+""")
private val ARTIST_DATE_PREFIX = Regex("""^\s*\d{1,2}\s*/\s*\d{1,2}\s*[:：]?\s*""")
private val ARTIST_PURE_DATE = Regex("""^\s*\d{1,2}\s*/\s*\d{1,2}\s*[:：]?\s*$""")

/** 把一串卡司文字拆成藝人名單。 */
fun splitArtistNames(text: String): List<String> =
    text.split(ARTIST_SEPARATORS)
        .map { it.replace(ARTIST_DATE_PREFIX, "").trim() }
        .filter { it.isNotBlank() && !ARTIST_PURE_DATE.matches(it) }
        .distinct()

// 「明確的」分隔符：不含任何空格。**使用者一邊打字時只能用這組。**
// 用上面那組會在打字途中就拆：團名裡剛好連按兩個空格、或還沒打完就被切成好幾位，
// 而且拆完的列無法再合回來，體感是「按兩下空格就自己新增一位」。
private val ARTIST_SEPARATORS_EXPLICIT = Regex("""[｜|、／;；\r\n]+""")

/** 打字時用的保守拆分：只認 ｜ | 、 ／ ; ； 與換行，空格一律不拆。 */
fun splitArtistNamesExplicit(text: String): List<String> =
    text.split(ARTIST_SEPARATORS_EXPLICIT)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

// 「貼上」時用的分隔符：在打字組之外，多認「前後有空白的半形斜線」。
// 官方卡司最常見的寫法就是 "A / B / C"，原本不拆，貼進來會整串黏在同一列。
// 只認「有空白」的斜線，所以 AC/DC、7/18 這種沒空白的不受影響。
private val ARTIST_SEPARATORS_PASTE = Regex("""(?:[｜|、／;；\r\n]|\s+/\s+)+""")

/**
 * 貼上整份卡司時用的拆分：認 ｜ | 、 ／ ; ； 換行，以及「 / 」（前後有空白的半形斜線）。
 * 會去掉「7/18：」這種日期前綴與純日期 token。
 */
fun splitArtistNamesPasted(text: String): List<String> =
    text.split(ARTIST_SEPARATORS_PASTE)
        .map { it.replace(ARTIST_DATE_PREFIX, "").trim() }
        .filter { it.isNotBlank() && !ARTIST_PURE_DATE.matches(it) }
        .distinct()

/**
 * 把一段文字（暱稱 / Google 名稱 / email 前綴）洗成合法的好友代碼片段：
 * 轉小寫、非 [a-z0-9] 一律轉底線、把連續底線收成一個、去掉頭尾的底線。
 *
 * 網頁版有一份一模一樣的實作（`suggestedHandleBase` 裡的 `clean`）。兩邊只要有一邊改了規則，
 * 同一個人在手機與網頁自動配到的代碼就會不一樣——所以規則放這裡並用測試釘住。
 * 中文暱稱洗完通常會變空字串，呼叫端要準備好往下一個來源退。
 */
fun cleanHandleSegment(raw: String): String =
    raw.lowercase()
        .map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')

/**
 * 藝人比對用的正規化鍵。大小寫、前後空白、全形空格都不該讓「同一位藝人」被當成兩位。
 * 只用來比對，顯示一律用使用者輸入的原始名稱。
 */
fun artistFollowKey(artist: String): String =
    artist.trim().replace('　', ' ').replace(Regex("""\s+"""), " ").lowercase()

/** 沒填國家一律當台灣：既有資料都是台灣的，這個預設讓舊資料不必回頭補。 */
const val DEFAULT_COUNTRY = "台灣"

fun FreeEvent.countryOrDefault(): String =
    country?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_COUNTRY

/**
 * 票價顯示。兩端都有就給區間，只有一端就給「N 起」，都沒有才退回舊的單一價／自由文字。
 * 「免費」的判斷仍以 ticketPrice 為準（null 或 0），跟卡片上的免費標籤同一套規則。
 */
fun FreeEvent.priceLabel(): String? {
    val lo = minPrice?.takeIf { it > 0 }
    val hi = maxPrice?.takeIf { it > 0 }
    fun fmt(v: Double) = "NT$" + v.toInt().toString().reversed().chunked(3).joinToString(",").reversed()
    return when {
        lo != null && hi != null && hi > lo -> "${fmt(lo)}–${fmt(hi)}"
        lo != null && hi != null -> fmt(lo)
        lo != null -> "${fmt(lo)} 起"
        hi != null -> "${fmt(hi)} 以下"
        !priceText.isNullOrBlank() -> priceText
        (ticketPrice ?: 0.0) > 0.0 -> fmt(ticketPrice!!)
        else -> null
    }
}

/** 這個活動的卡司裡有沒有出現追蹤名單中的藝人；有的話回傳「追蹤名單裡那一個」的原名。 */
fun matchFollowedArtist(cast: String, followed: Set<String>): String? {
    if (cast.isBlank() || followed.isEmpty()) return null
    val keys = splitArtistNames(cast).map { artistFollowKey(it) }.toSet()
    return followed.firstOrNull { artistFollowKey(it) in keys }
}
