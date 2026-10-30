package com.bandary.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import com.bandary.domain.model.DailySong
import com.bandary.domain.model.FestivalSlot
import com.bandary.domain.model.SaleTime
import com.bandary.domain.model.saleStartAt
import com.bandary.domain.model.ReminderOffset
import com.bandary.domain.model.RewatchIntent
import com.bandary.domain.model.SongReaction
import com.bandary.domain.model.TicketStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

// ── Type Converters ───────────────────────────────────────────
class Converters {
    @TypeConverter fun fromLocalDate(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun toLocalDate(v: String?): LocalDate? = v?.let { LocalDate.parse(it) }
    @TypeConverter fun fromLocalDateTime(v: LocalDateTime?): String? = v?.toString()
    @TypeConverter fun toLocalDateTime(v: String?): LocalDateTime? = v?.let { LocalDateTime.parse(it) }
    @TypeConverter fun fromLocalTime(v: LocalTime?): String? = v?.toString()
    @TypeConverter fun toLocalTime(v: String?): LocalTime? = v?.let { LocalTime.parse(it) }
    @TypeConverter fun fromReminderList(v: List<ReminderOffset>): String = v.joinToString(",") { it.name }
    @TypeConverter fun toReminderList(v: String): List<ReminderOffset> =
        if (v.isBlank()) emptyList()
        else v.split(",").mapNotNull { name -> runCatching { ReminderOffset.valueOf(name) }.getOrNull() }

    @TypeConverter fun fromStringList(v: List<String>): String = v.joinToString("\n")
    @TypeConverter fun toStringList(v: String): List<String> =
        if (v.isBlank()) emptyList() else v.split("\n").filter { it.isNotBlank() }

    @TypeConverter fun fromLocalDateList(v: List<LocalDate>): String = v.joinToString(",") { it.toString() }
    @TypeConverter fun toLocalDateList(v: String): List<LocalDate> =
        if (v.isBlank()) emptyList()
        else v.split(",").mapNotNull { s -> runCatching { LocalDate.parse(s.trim()) }.getOrNull() }

    @TypeConverter fun fromIntList(v: List<Int>): String = v.joinToString(",")
    @TypeConverter fun toIntList(v: String): List<Int> =
        if (v.isBlank()) emptyList()
        else v.split(",").mapNotNull { it.trim().toIntOrNull() }

    // 尾端欄位一律用 getOrNull 取，所以新增欄位不需要 Room 遷移：舊資料少的那格自動吃預設值。
    @TypeConverter fun fromFestivalSlots(v: List<FestivalSlot>): String =
        v.joinToString(";") { "${it.startTime}|${it.endTime}|${it.stage}|${it.artistName}|${it.isSelected}|${it.day}|${it.feat}|${it.featOf}" }
    @TypeConverter fun toFestivalSlots(v: String): List<FestivalSlot> =
        if (v.isBlank()) emptyList()
        else v.split(";").mapNotNull { segment ->
            val parts = segment.split("|")
            if (parts.size >= 5) FestivalSlot(
                parts[0], parts[1], parts[2], parts[3], parts[4].toBoolean(),
                day = parts.getOrNull(5) ?: "",
                feat = parts.getOrNull(6)?.toBoolean() ?: false,
                featOf = parts.getOrNull(7) ?: ""
            )
            else null
        }

    // 開賣時間：「名稱|ISO 時間」，多組用 ; 分隔。名稱裡的分隔字元先換掉，
    // 否則使用者打一個 | 就會把自己的資料切壞（存進去不會報錯，讀出來才發現少一半）。
    @TypeConverter fun fromSaleTimes(v: List<SaleTime>): String =
        v.joinToString(";") { "${it.label.replace('|', '／').replace(';', '，')}|${it.at}" }
    @TypeConverter fun toSaleTimes(v: String): List<SaleTime> =
        if (v.isBlank()) emptyList()
        else v.split(";").mapNotNull { segment ->
            val i = segment.indexOf('|')
            if (i < 0) return@mapNotNull null
            val at = runCatching { LocalDateTime.parse(segment.substring(i + 1)) }.getOrNull()
            at?.let { SaleTime(segment.substring(0, i), it) }
        }
}

// ── Concert Entity ────────────────────────────────────────────
@Entity(tableName = "concerts")
data class ConcertEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artistName: String,
    val venue: String,
    val hall: String?,
    val address: String? = null,
    val city: String?,
    val country: String? = "台灣",
    val dateTime: LocalDateTime,
    val endTime: LocalTime?,
    val endDate: LocalDate? = null,
    val extraDates: List<LocalDate> = emptyList(),
    val type: String,
    val customType: String? = null,
    val ticketStatus: String,
    val ticketPrice: Double?,
    val ticketPlatform: String?,
    val ticketUrl: String?,
    val infoUrl: String? = null,
    val pickupNumber: String? = null,
    val pickupIdNumber: String? = null,
    val pickupTicketNo: String? = null,
    val pickupMachine: String? = null,
    val pickupShotUri: String? = null,
    val seatInfo: String?,
    val setlist: List<String> = emptyList(),
    val companions: List<String> = emptyList(),
    val dayCovers: List<String> = emptyList(),
    val notes: String?,
    val rating: Int?,
    val rewatchIntent: String? = null,
    val photoUri: String?,
    val photoUris: List<String>,
    val seatMapUri: String?,
    val timetableUris: List<String> = emptyList(),
    val coverColorHex: String? = null,
    val coverGradientLight: Boolean = false,
    val reminders: List<ReminderOffset>,
    val customReminderMinutes: Int? = null,
    val extraReminderMinutes: List<Int> = emptyList(),
    val festivals: List<FestivalSlot>,
    // 保留這欄＝「最早的一組開賣時間」，由 saleTimes 推導後一起寫入。
    // 舊版只有這一欄，留著就不必重建整張表（Room 對多出來的欄位會驗證失敗），
    // 讀取一律以 saleTimes 為準，這欄只給既有查詢／相容用。
    val saleStartAt: LocalDateTime?,
    val saleTimes: List<SaleTime> = emptyList(),
    val saleReminderMinutes: Int? = null,
    val syncToCalendar: Boolean = false,
    val createdAt: Long,
    val deletedAt: Long? = null,
    val updatedAt: Long = 0L    // 最後修改時間（雲端同步 last-write-wins 用）
)

fun ConcertEntity.toDomain() = Concert(
    id = id,
    name = name,
    artistName = artistName,
    venue = venue,
    hall = hall,
    address = address,
    city = city,
    country = country,
    dateTime = dateTime,
    endTime = endTime,
    endDate = endDate,
    extraDates = extraDates,
    type = ConcertType.entries.firstOrNull { it.name == type } ?: ConcertType.CONCERT,
    customType = customType,
    ticketStatus = TicketStatus.fromStored(ticketStatus),
    ticketPrice = ticketPrice,
    ticketPlatform = ticketPlatform,
    ticketUrl = ticketUrl,
    infoUrl = infoUrl,
    pickupNumber = pickupNumber,
    pickupIdNumber = pickupIdNumber,
    pickupTicketNo = pickupTicketNo,
    pickupMachine = pickupMachine,
    pickupShotUri = pickupShotUri,
    seatInfo = seatInfo,
    setlist = setlist,
    companions = companions,
    dayCovers = dayCovers,
    notes = notes,
    rating = rating,
    rewatchIntent = rewatchIntent?.let { ri -> RewatchIntent.entries.firstOrNull { it.name == ri } },
    photoUri = photoUri,
    photoUris = photoUris,
    seatMapUri = seatMapUri,
    timetableUris = timetableUris,
    coverColorHex = coverColorHex,
    coverGradientLight = coverGradientLight,
    reminders = reminders,
    customReminderMinutes = customReminderMinutes,
    extraReminderMinutes = extraReminderMinutes,
    festivals = festivals,
    // saleTimes 空但舊欄位有值＝遷移前寫入的資料，補成一筆無名稱的開賣時間。
    saleTimes = saleTimes.ifEmpty { saleStartAt?.let { listOf(SaleTime("", it)) }.orEmpty() },
    saleReminderMinutes = saleReminderMinutes,
    syncToCalendar = syncToCalendar,
    createdAt = createdAt,
    deletedAt = deletedAt,
    updatedAt = updatedAt
)

fun Concert.toEntity() = ConcertEntity(
    id = id,
    name = name,
    artistName = artistName,
    venue = venue,
    hall = hall,
    address = address,
    city = city,
    country = country,
    dateTime = dateTime,
    endTime = endTime,
    endDate = endDate,
    extraDates = extraDates,
    type = type.name,
    customType = customType,
    ticketStatus = ticketStatus.name,
    ticketPrice = ticketPrice,
    ticketPlatform = ticketPlatform,
    ticketUrl = ticketUrl,
    infoUrl = infoUrl,
    pickupNumber = pickupNumber,
    pickupIdNumber = pickupIdNumber,
    pickupTicketNo = pickupTicketNo,
    pickupMachine = pickupMachine,
    pickupShotUri = pickupShotUri,
    seatInfo = seatInfo,
    setlist = setlist,
    companions = companions,
    dayCovers = dayCovers,
    notes = notes,
    rating = rating,
    rewatchIntent = rewatchIntent?.name,
    photoUri = photoUri,
    photoUris = photoUris,
    seatMapUri = seatMapUri,
    timetableUris = timetableUris,
    coverColorHex = coverColorHex,
    coverGradientLight = coverGradientLight,
    reminders = reminders,
    customReminderMinutes = customReminderMinutes,
    extraReminderMinutes = extraReminderMinutes,
    festivals = festivals,
    saleStartAt = saleStartAt,   // 由 saleTimes 推導（Concert.saleStartAt 擴充屬性）
    saleTimes = saleTimes,
    saleReminderMinutes = saleReminderMinutes,
    syncToCalendar = syncToCalendar,
    createdAt = createdAt,
    deletedAt = deletedAt,
    updatedAt = updatedAt
)

// ── DailySong Entity ──────────────────────────────────────────
// Multiple songs allowed per day → index on date is NON-unique now.
// concertId：這首歌是從哪一場演唱會關聯來的（v21 新增）。刻意不用外鍵 —— 演唱會被刪除時
// 這筆選歌仍應保留（日記本身有價值），只是失去連結，查詢端一律容忍找不到場次。
@Entity(
    tableName = "daily_songs",
    indices = [Index(value = ["date"], unique = false), Index(value = ["concertId"], unique = false)]
)
data class DailySongEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val songTitle: String,
    val artistName: String,
    val albumName: String?,
    val reaction: String?,
    val memo: String?,
    val rank: Int,
    val spotifyTrackId: String?,
    val createdAt: Long,
    val concertId: String? = null
)

fun DailySongEntity.toDomain() = DailySong(
    id = id,
    date = date,
    songTitle = songTitle,
    artistName = artistName,
    albumName = albumName,
    reaction = reaction?.let { r -> SongReaction.entries.firstOrNull { it.name == r } },
    memo = memo,
    rank = rank,
    spotifyTrackId = spotifyTrackId,
    createdAt = createdAt,
    concertId = concertId
)

fun DailySong.toEntity() = DailySongEntity(
    id = id,
    date = date,
    songTitle = songTitle,
    artistName = artistName,
    albumName = albumName,
    reaction = reaction?.name,
    memo = memo,
    rank = rank,
    spotifyTrackId = spotifyTrackId,
    createdAt = createdAt,
    concertId = concertId
)
