package com.bandary.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.bandary.data.local.ConcertDraftEntity
import com.bandary.data.local.entity.ConcertEntity
import com.bandary.data.local.entity.DailySongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConcertDao {

    // 一般查詢都排除垃圾桶內（deletedAt 有值）的演唱會。
    @Query("SELECT * FROM concerts WHERE deletedAt IS NULL ORDER BY dateTime DESC")
    fun getAllConcerts(): Flow<List<ConcertEntity>>

    @Query("SELECT * FROM concerts WHERE dateTime >= :nowIso AND deletedAt IS NULL ORDER BY dateTime ASC LIMIT 1")
    fun getNextConcert(nowIso: String): Flow<ConcertEntity?>

    @Query("SELECT * FROM concerts WHERE dateTime >= :fromIso AND dateTime <= :toIso AND deletedAt IS NULL ORDER BY dateTime ASC")
    fun getConcertsBetween(fromIso: String, toIso: String): Flow<List<ConcertEntity>>

    @Query("SELECT * FROM concerts WHERE id = :id")
    suspend fun getConcertById(id: String): ConcertEntity?

    // 雲端同步用：連垃圾桶內的也要拿（deletedAt 就是刪除墓碑，
    // 不送上去的話雲端/其他裝置永遠不知道這筆被刪了，下次同步又會被拉回來）。
    @Query("SELECT * FROM concerts")
    suspend fun getAllConcertsForSync(): List<ConcertEntity>

    @Upsert
    suspend fun upsertConcert(concert: ConcertEntity)

    @Delete
    suspend fun deleteConcert(concert: ConcertEntity)

    @Query("DELETE FROM concerts WHERE id = :id")
    suspend fun deleteConcertById(id: String)

    // ── 垃圾桶（軟刪除，保留 7 天）──
    @Query("SELECT * FROM concerts WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashedConcerts(): Flow<List<ConcertEntity>>

    @Query("UPDATE concerts SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDeleteConcert(id: String, ts: Long)

    @Query("UPDATE concerts SET deletedAt = NULL, updatedAt = :ts WHERE id = :id")
    suspend fun restoreConcert(id: String, ts: Long)

    @Query("DELETE FROM concerts WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeExpiredTrash(cutoff: Long)

    @Query("SELECT COUNT(*) FROM concerts WHERE strftime('%Y', dateTime) = :year")
    fun countConcertsByYear(year: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(ticketPrice), 0.0) FROM concerts WHERE strftime('%Y', dateTime) = :year")
    fun totalSpentByYear(year: String): Flow<Double>

    @Query("""
        SELECT artistName, COUNT(*) as cnt FROM concerts
        WHERE strftime('%Y', dateTime) = :year
        GROUP BY artistName
        ORDER BY cnt DESC
        LIMIT 10
    """)
    fun topArtistsByYear(year: String): Flow<List<ArtistCount>>

    @Query("""
        SELECT CAST(strftime('%m', dateTime) AS INTEGER) as month, COUNT(*) as count
        FROM concerts
        WHERE strftime('%Y', dateTime) = :year
        GROUP BY month
        ORDER BY month
    """)
    fun monthlyConcertCounts(year: String): Flow<List<MonthCount>>

    // ── 編輯頁自動草稿（spec §8）──
    @Upsert
    suspend fun upsertDraft(draft: ConcertDraftEntity)

    @Query("SELECT * FROM concert_drafts WHERE draftKey = :key")
    suspend fun getDraft(key: String): ConcertDraftEntity?

    @Query("DELETE FROM concert_drafts WHERE draftKey = :key")
    suspend fun deleteDraft(key: String)
}

data class ArtistCount(val artistName: String, val cnt: Int)
data class MonthCount(val month: Int, val count: Int)

@Dao
interface DailySongDao {

    @Query("SELECT * FROM daily_songs ORDER BY date DESC, rank ASC")
    fun getAllDailySongs(): Flow<List<DailySongEntity>>

    @Query("SELECT * FROM daily_songs WHERE date = :date ORDER BY rank ASC LIMIT 1")
    suspend fun getDailySongForDate(date: String): DailySongEntity?

    @Query("SELECT * FROM daily_songs WHERE date = :date ORDER BY rank ASC")
    fun getSongsForDate(date: String): Flow<List<DailySongEntity>>

    @Query("SELECT * FROM daily_songs WHERE date >= :fromDate AND date <= :toDate ORDER BY date ASC, rank ASC")
    fun getSongsBetween(fromDate: String, toDate: String): Flow<List<DailySongEntity>>

    // 明確關聯到某場演唱會的選歌（從該場歌單一鍵記錄而來）。
    @Query("SELECT * FROM daily_songs WHERE concertId = :concertId ORDER BY date ASC, rank ASC")
    fun getSongsForConcert(concertId: String): Flow<List<DailySongEntity>>

    // 依歌手名找選歌（藝人詳情頁「記過的歌」用；大小寫不敏感由 SQLite 的 = 對 ASCII 處理，中文為精確比對）。
    @Query("SELECT * FROM daily_songs WHERE artistName = :artist ORDER BY date DESC, rank ASC")
    fun getSongsByArtist(artist: String): Flow<List<DailySongEntity>>

    @Query("SELECT * FROM daily_songs ORDER BY date DESC, rank ASC LIMIT :limit")
    fun getRecentDailySongs(limit: Int = 30): Flow<List<DailySongEntity>>

    @Upsert
    suspend fun upsertDailySong(song: DailySongEntity)

    @Query("DELETE FROM daily_songs WHERE id = :id")
    suspend fun deleteDailySongById(id: String)

    @Delete
    suspend fun deleteDailySong(song: DailySongEntity)

    @Query("SELECT COUNT(*) FROM daily_songs WHERE date = :date")
    suspend fun countSongsForDate(date: String): Int
}
