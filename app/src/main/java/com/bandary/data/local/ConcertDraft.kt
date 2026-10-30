package com.bandary.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bandary.data.local.entity.ConcertEntity
import com.bandary.data.local.entity.Converters
import com.bandary.data.local.entity.toDomain
import com.bandary.data.local.entity.toEntity
import com.bandary.domain.model.Concert
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.json.JSONObject

/**
 * 編輯演唱會的「自動草稿」（spec §8）：欄位變更 debounce 後把進行中的內容存這裡，
 * App 被關掉也不會掉；按「儲存」提交到正式 concerts 後即清掉此草稿。
 *
 * 刻意用「單一 JSON blob」而非鏡射 ConcertEntity 的 30+ 欄位：
 *  - 建表 SQL 只有 3 欄，遷移不會與 Room 期望 schema 對不上（本專案有 fallbackToDestructiveMigration，
 *    schema 不符會清庫，風險太高）。
 *  - 新增欄位時不必同步維護第二張表。
 * draftKey：編輯既有場次＝該場 id；新增＝"new"。
 */
@Entity(tableName = "concert_drafts")
data class ConcertDraftEntity(
    @PrimaryKey val draftKey: String,
    val payload: String,
    val updatedAt: Long
)

private val draftConv = Converters()

/** 把進行中的 Concert 轉成 JSON 字串（欄位以 ConcertEntity 為準，複雜型別走既有 Converters 轉字串）。 */
fun Concert.toDraftPayload(): String {
    val e = toEntity()
    return JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("artistName", e.artistName)
        put("venue", e.venue)
        putOrNull("hall", e.hall)
        putOrNull("address", e.address)
        putOrNull("city", e.city)
        putOrNull("country", e.country)
        put("dateTime", draftConv.fromLocalDateTime(e.dateTime))
        putOrNull("endTime", draftConv.fromLocalTime(e.endTime))
        putOrNull("endDate", draftConv.fromLocalDate(e.endDate))
        put("extraDates", draftConv.fromLocalDateList(e.extraDates))
        put("type", e.type)
        putOrNull("customType", e.customType)
        put("ticketStatus", e.ticketStatus)
        putOrNull("ticketPrice", e.ticketPrice)
        putOrNull("ticketPlatform", e.ticketPlatform)
        putOrNull("ticketUrl", e.ticketUrl)
        putOrNull("infoUrl", e.infoUrl)
        putOrNull("pickupNumber", e.pickupNumber)
        putOrNull("pickupIdNumber", e.pickupIdNumber)
        putOrNull("pickupTicketNo", e.pickupTicketNo)
        putOrNull("pickupMachine", e.pickupMachine)
        putOrNull("pickupShotUri", e.pickupShotUri)
        putOrNull("seatInfo", e.seatInfo)
        put("setlist", draftConv.fromStringList(e.setlist))
        put("companions", draftConv.fromStringList(e.companions))
        put("dayCovers", draftConv.fromStringList(e.dayCovers))
        putOrNull("notes", e.notes)
        putOrNull("rating", e.rating)
        putOrNull("rewatchIntent", e.rewatchIntent)
        putOrNull("photoUri", e.photoUri)
        put("photoUris", draftConv.fromStringList(e.photoUris))
        putOrNull("seatMapUri", e.seatMapUri)
        put("timetableUris", draftConv.fromStringList(e.timetableUris))
        putOrNull("coverColorHex", e.coverColorHex)
        put("coverGradientLight", e.coverGradientLight)
        put("reminders", draftConv.fromReminderList(e.reminders))
        putOrNull("customReminderMinutes", e.customReminderMinutes)
        put("extraReminderMinutes", draftConv.fromIntList(e.extraReminderMinutes))
        put("festivals", draftConv.fromFestivalSlots(e.festivals))
        // saleStartAt 仍寫出，讓舊版本讀得到這份草稿；saleTimes 才是完整內容。
        putOrNull("saleStartAt", draftConv.fromLocalDateTime(e.saleStartAt))
        put("saleTimes", draftConv.fromSaleTimes(e.saleTimes))
        putOrNull("saleReminderMinutes", e.saleReminderMinutes)
        put("syncToCalendar", e.syncToCalendar)
        put("createdAt", e.createdAt)
        putOrNull("deletedAt", e.deletedAt)
    }.toString()
}

/** 反向：JSON 字串還原成 Concert；壞掉就回 null（草稿是 best-effort，不影響正式資料）。 */
fun draftPayloadToConcert(payload: String): Concert? = runCatching {
    val o = JSONObject(payload)
    fun str(k: String): String? = if (o.isNull(k)) null else o.optString(k)
    fun dbl(k: String): Double? = if (o.isNull(k)) null else o.optDouble(k)
    fun int(k: String): Int? = if (o.isNull(k)) null else o.optInt(k)
    fun lng(k: String): Long = if (o.isNull(k)) 0L else o.optLong(k)
    ConcertEntity(
        id = str("id") ?: "",
        name = str("name") ?: "",
        artistName = str("artistName") ?: "",
        venue = str("venue") ?: "",
        hall = str("hall"),
        address = str("address"),
        city = str("city"),
        country = str("country"),
        dateTime = draftConv.toLocalDateTime(str("dateTime")) ?: return@runCatching null,
        endTime = draftConv.toLocalTime(str("endTime")),
        endDate = draftConv.toLocalDate(str("endDate")),
        extraDates = draftConv.toLocalDateList(str("extraDates") ?: ""),
        type = str("type") ?: "CONCERT",
        customType = str("customType"),
        ticketStatus = str("ticketStatus") ?: "NONE",
        ticketPrice = dbl("ticketPrice"),
        ticketPlatform = str("ticketPlatform"),
        ticketUrl = str("ticketUrl"),
        infoUrl = str("infoUrl"),
        pickupNumber = str("pickupNumber"),
        pickupIdNumber = str("pickupIdNumber"),
        pickupTicketNo = str("pickupTicketNo"),
        pickupMachine = str("pickupMachine"),
        pickupShotUri = str("pickupShotUri"),
        seatInfo = str("seatInfo"),
        setlist = draftConv.toStringList(str("setlist") ?: ""),
        companions = draftConv.toStringList(str("companions") ?: ""),
        dayCovers = draftConv.toStringList(str("dayCovers") ?: ""),
        notes = str("notes"),
        rating = int("rating"),
        rewatchIntent = str("rewatchIntent"),
        photoUri = str("photoUri"),
        photoUris = draftConv.toStringList(str("photoUris") ?: ""),
        seatMapUri = str("seatMapUri"),
        timetableUris = draftConv.toStringList(str("timetableUris") ?: ""),
        coverColorHex = str("coverColorHex"),
        coverGradientLight = o.optBoolean("coverGradientLight", false),
        reminders = draftConv.toReminderList(str("reminders") ?: ""),
        customReminderMinutes = int("customReminderMinutes"),
        extraReminderMinutes = draftConv.toIntList(str("extraReminderMinutes") ?: ""),
        festivals = draftConv.toFestivalSlots(str("festivals") ?: ""),
        saleStartAt = draftConv.toLocalDateTime(str("saleStartAt")),
        // 舊草稿沒有 saleTimes，toDomain() 會用 saleStartAt 補一筆，這裡不必再處理。
        saleTimes = draftConv.toSaleTimes(str("saleTimes") ?: ""),
        saleReminderMinutes = int("saleReminderMinutes"),
        syncToCalendar = o.optBoolean("syncToCalendar", false),
        createdAt = lng("createdAt"),
        deletedAt = if (o.isNull("deletedAt")) null else o.optLong("deletedAt")
    ).toDomain()
}.getOrNull()

private fun JSONObject.putOrNull(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}
