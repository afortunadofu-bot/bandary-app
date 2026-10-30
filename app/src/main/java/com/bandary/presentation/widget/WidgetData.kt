package com.bandary.presentation.widget

import android.content.Context
import com.bandary.data.repository.ConcertRepository
import com.bandary.domain.model.occurrenceDates
import com.bandary.domain.model.saleStartAt
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Hilt 進入點：讓非 Hilt 的 Glance widget 也能取到 App 圖層的 Repository。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun concertRepository(): ConcertRepository
    fun dailySongRepository(): com.bandary.data.repository.DailySongRepository
}

/** Widget 用的精簡演唱會資料。 */
data class WidgetConcert(
    val id: String,
    val name: String,
    val artist: String,
    val venue: String,
    val date: LocalDate,
    val endDate: LocalDate?,
    val timeLabel: String,     // "19:30"，多日或全天時可能空字串
    val daysUntil: Int,        // 0 = 今天、負數 = 進行中的多日活動
    val imageUri: String? = null   // 封面圖（下一場倒數 widget 拿來當背景）
)

/** Widget 用的精簡開賣資料。 */
data class WidgetSale(
    val id: String,
    val name: String,
    val artist: String,
    val date: LocalDate,
    val timeLabel: String,     // 開賣時間 "12:00"
    val daysUntil: Int
)

/** 月曆 widget 的當月單筆活動（給日曆下方的預覽清單用）。 */
data class WidgetMonthEvent(
    val day: Int,          // 當月起始日
    val label: String,     // 藝人/活動名稱
    val isSale: Boolean    // true=開賣、false=演出
)

/** 一次載入所有 widget 需要的資料。 */
data class WidgetSnapshot(
    val upcoming: List<WidgetConcert> = emptyList(),
    val upcomingSales: List<WidgetSale> = emptyList(),  // 即將開賣
    val concertDays: Set<Int> = emptySet(),   // 當月有演出的日
    val saleDays: Set<Int> = emptySet(),      // 當月有開賣的日
    val monthEvents: List<WidgetMonthEvent> = emptyList(), // 當月活動（演出＋開賣），依日排序
    val year: Int = 0,
    val month: Int = 0,
    val today: Int = 0,
    val monthLabel: String = ""
)

/** 讀取並縮小圖片給 widget 當背景（RemoteViews 有大小限制，需降採樣）。 */
fun loadWidgetBitmap(context: Context, uri: String, reqW: Int = 500, reqH: Int = 800): android.graphics.Bitmap? = runCatching {
    val u = android.net.Uri.parse(uri)
    fun open() = context.contentResolver.openInputStream(u)
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > reqW * 2 || bounds.outHeight / sample > reqH * 2) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    open()?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

object WidgetRepository {

    private val TZ = TimeZone.of("Asia/Taipei")

    suspend fun load(context: Context, monthOffset: Int = 0): WidgetSnapshot {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .concertRepository()
        val all = runCatching { repo.getAllConcerts().first() }.getOrDefault(emptyList())

        val now = Clock.System.now()
        val nowDate = now.toLocalDateTime(TZ).date
        // 月曆顯示的月份（可用 ◀▶ 切換）。
        val shownDate = nowDate.plus(monthOffset, DateTimeUnit.MONTH)

        // 即將到來（含仍在進行中的多日活動），依開始時間排序。
        val upcoming = all
            .filter { c ->
                val end = c.endDate?.takeIf { it > c.dateTime.date } ?: c.dateTime.date
                end >= nowDate
            }
            .sortedBy { it.dateTime }
            .map { c ->
                WidgetConcert(
                    id = c.id,
                    name = c.name,
                    artist = c.artistName.ifBlank { c.name },
                    venue = c.venue,
                    date = c.dateTime.date,
                    endDate = c.endDate?.takeIf { it > c.dateTime.date },
                    timeLabel = "%02d:%02d".format(c.dateTime.hour, c.dateTime.minute),
                    daysUntil = nowDate.daysUntil(c.dateTime.date),
                    imageUri = c.photoUri?.takeIf { it.isNotBlank() } ?: c.photoUris.firstOrNull()
                )
            }

        // 即將開賣（開賣時間 >= 現在），依開賣時間排序。
        val upcomingSales = all
            .filter { c -> c.saleStartAt?.let { it.toInstant(TZ) >= now } ?: false }
            .sortedBy { it.saleStartAt }
            .map { c ->
                val s = c.saleStartAt!!
                WidgetSale(
                    id = c.id,
                    name = c.name,
                    artist = c.artistName.ifBlank { c.name },
                    date = s.date,
                    timeLabel = "%02d:%02d".format(s.hour, s.minute),
                    daysUntil = nowDate.daysUntil(s.date)
                )
            }

        // 顯示月份的標記：演出日（多日展開）+ 開賣日。
        val concertDays = mutableSetOf<Int>()
        val saleDays = mutableSetOf<Int>()
        val monthEvents = mutableListOf<WidgetMonthEvent>()
        all.forEach { c ->
            val label = c.artistName.ifBlank { c.name }
            var firstDayInMonth: Int? = null
            c.occurrenceDates().forEach { d ->
                if (d.year == shownDate.year && d.monthNumber == shownDate.monthNumber) {
                    concertDays += d.dayOfMonth
                    if (firstDayInMonth == null || d.dayOfMonth < firstDayInMonth!!) firstDayInMonth = d.dayOfMonth
                }
            }
            firstDayInMonth?.let { monthEvents += WidgetMonthEvent(it, label, isSale = false) }
            c.saleStartAt?.date?.let { s ->
                if (s.year == shownDate.year && s.monthNumber == shownDate.monthNumber) {
                    saleDays += s.dayOfMonth
                    monthEvents += WidgetMonthEvent(s.dayOfMonth, label, isSale = true)
                }
            }
        }
        monthEvents.sortWith(compareBy({ it.day }, { it.isSale }))
        // 只有顯示當前月份時才高亮「今天」。
        val todayHighlight = if (shownDate.year == nowDate.year && shownDate.monthNumber == nowDate.monthNumber)
            nowDate.dayOfMonth else -1

        return WidgetSnapshot(
            upcoming = upcoming,
            upcomingSales = upcomingSales,
            concertDays = concertDays,
            saleDays = saleDays,
            monthEvents = monthEvents,
            year = shownDate.year,
            month = shownDate.monthNumber,
            today = todayHighlight,
            monthLabel = "${shownDate.year} 年 ${shownDate.monthNumber} 月"
        )
    }
}
