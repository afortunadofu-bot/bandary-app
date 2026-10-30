package com.bandary.presentation.ui.home

import com.bandary.domain.model.DailySong
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * 選歌的「月回顧」純計算層。
 *
 * 這裡刻意不引用任何 Compose / Android 的東西，才能用單元測試直接跑——排名、佔比、
 * 同分處理這種容易寫錯又不容易被人工測出來的邏輯，都放在這一層。
 * 畫面只負責把算好的結果排出來。
 */

/** 藝人與這個月被記了幾首。 */
data class RecapArtist(val name: String, val count: Int)

/** 歌曲與這個月被記了幾次（同一首歌不同天算多次）。 */
data class RecapSong(val title: String, val artist: String, val count: Int)

data class MonthSongRecap(
    val totalSongs: Int,
    val activeDays: Int,
    val artistCount: Int,
    val topArtists: List<RecapArtist>,
    val topSongs: List<RecapSong>,
    /** 一句話的解讀。沒有足夠資料時為 null，畫面就不顯示那一行。 */
    val headline: String?
) {
    val isEmpty: Boolean get() = totalSongs == 0
}

/**
 * 算出某個月的選歌回顧。
 *
 * [songs] 必須已經是那個月的紀錄（呼叫端本來就照月份查）。
 * [previousMonthTotal] 是上個月的總首數，用來產生「比上個月多／少」那句話；不比較就給 null。
 *
 * 排序規則：次數多的在前，同次數以名稱排序，讓每次算出來的順序是穩定的
 * （否則同分項目會隨資料庫回傳順序跳動，看起來像資料在變）。
 */
fun buildMonthSongRecap(
    songs: List<DailySong>,
    previousMonthTotal: Int? = null,
    topN: Int = 3
): MonthSongRecap {
    if (songs.isEmpty()) {
        return MonthSongRecap(0, 0, 0, emptyList(), emptyList(), null)
    }

    val named = songs.filter { it.artistName.isNotBlank() }
    val artistCounts = named.groupingBy { it.artistName.trim() }.eachCount()
    val topArtists = artistCounts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(topN)
        .map { RecapArtist(it.key, it.value) }

    // 同名不同藝人的歌要分開算（「離開」不只一首），所以用「歌名 + 藝人」當鍵。
    val songCounts = songs.filter { it.songTitle.isNotBlank() }
        .groupingBy { it.songTitle.trim() to it.artistName.trim() }.eachCount()
    val topSongs = songCounts.entries
        .sortedWith(
            compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }
                .thenBy { it.key.first }
        )
        .take(topN)
        .map { RecapSong(it.key.first, it.key.second, it.value) }

    return MonthSongRecap(
        totalSongs = songs.size,
        activeDays = songs.map { it.date }.distinct().size,
        artistCount = artistCounts.size,
        topArtists = topArtists,
        topSongs = topSongs,
        headline = buildHeadline(songs.size, artistCounts, topArtists.firstOrNull(), previousMonthTotal)
    )
}

/**
 * 挑一句最有內容的解讀。優先序：
 * 1. 有人佔了三成以上 → 講「這個月是誰的月份」，這是最像人會說的話。
 * 2. 有上個月可比 → 講增減。
 * 3. 都沒有 → 講聽得多廣。
 */
private fun buildHeadline(
    total: Int,
    artistCounts: Map<String, Int>,
    top: RecapArtist?,
    previousMonthTotal: Int?
): String? {
    if (total == 0) return null

    if (top != null && total >= 4) {
        val share = top.count * 100 / total
        // 只有一位藝人時說「佔 100%」很怪，改講他一個人包辦。
        if (artistCounts.size == 1) return "這個月只記了 ${top.name}，${total} 首都是他的。"
        if (share >= 30) return "${top.name} 是這個月的主角，${total} 首裡有 ${top.count} 首是他的（${share}%）。"
    }

    if (previousMonthTotal != null && previousMonthTotal > 0) {
        val diff = total - previousMonthTotal
        return when {
            diff > 0 -> "比上個月多記了 $diff 首。"
            diff < 0 -> "比上個月少記了 ${-diff} 首。"
            else -> "跟上個月一樣是 $total 首。"
        }
    }

    if (artistCounts.size >= 5) return "這個月聽了 ${artistCounts.size} 位不同的藝人，蠻雜食的。"
    return null
}

/**
 * 這個月的回顧能不能顯示。
 *
 * 月中就把「這個月」的回顧攤開來，看到的是還沒結束的半套數字，使用者回報說不想在月底前看到。
 * 規則：已經過完的月份隨時可看；正在進行的這個月，要等到最後一天才顯示；未來月份不顯示。
 */
fun isMonthRecapReady(year: Int, monthNumber: Int, today: LocalDate): Boolean = when {
    year != today.year -> year < today.year
    monthNumber != today.monthNumber -> monthNumber < today.monthNumber
    else -> today == lastDayOfMonth(year, monthNumber)
}

private fun lastDayOfMonth(year: Int, monthNumber: Int): LocalDate =
    LocalDate(year, monthNumber, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
