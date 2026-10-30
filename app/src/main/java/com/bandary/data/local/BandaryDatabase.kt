package com.bandary.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bandary.data.local.dao.ConcertDao
import com.bandary.data.local.dao.DailySongDao
import com.bandary.data.local.entity.ConcertEntity
import com.bandary.data.local.entity.Converters
import com.bandary.data.local.entity.DailySongEntity

@Database(
    entities = [ConcertEntity::class, DailySongEntity::class, ConcertDraftEntity::class],
    version = 24,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BandaryDatabase : RoomDatabase() {
    abstract fun concertDao(): ConcertDao
    abstract fun dailySongDao(): DailySongDao

    companion object {
        const val DB_NAME = "bandary.db"

        // v12 → v13：新增 concerts.setlist（歌單，List<String> 以 \n 串接存 TEXT）。
        // 以正式 Migration 保留既有本機資料，不走破壞性重建。
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concerts ADD COLUMN setlist TEXT NOT NULL DEFAULT ''")
            }
        }

        // v13 → v14：新增 companions（同行者）與 dayCovers（各日封面）。
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concerts ADD COLUMN companions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE concerts ADD COLUMN dayCovers TEXT NOT NULL DEFAULT ''")
            }
        }

        // v14 → v15：新增取票身份證 / 取票號碼，以及多個自訂提醒（List<Int> 以逗號串接存 TEXT）。
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concerts ADD COLUMN pickupIdNumber TEXT")
                db.execSQL("ALTER TABLE concerts ADD COLUMN pickupTicketNo TEXT")
                db.execSQL("ALTER TABLE concerts ADD COLUMN extraReminderMinutes TEXT NOT NULL DEFAULT ''")
            }
        }

        // v15 → v16：新增 timetableUris（節目表圖片，List<String> 以分隔符串接存 TEXT）。
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concerts ADD COLUMN timetableUris TEXT NOT NULL DEFAULT ''")
            }
        }

        // v16 → v17：新增 customType（演出類型選「自訂」時的自訂名稱，可為 NULL）。
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concerts ADD COLUMN customType TEXT")
            }
        }

        // v17 → v18：新增 deletedAt（軟刪除時間戳；在垃圾桶裡保留 7 天）。
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concerts ADD COLUMN deletedAt INTEGER")
            }
        }

        // v18 → v19：購票狀況精簡為 已購票 / 未購票。就地改寫舊值，避免舊資料「自動變成無」：
        //   待購票(WISHLIST)、抽票(LOTTERY) → 未購票(NOT_BOUGHT)
        //   免費(FREE) → 已購票(BOUGHT)，並把票價補 0（免費改由票價 0 表示）
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE concerts SET ticketPrice = 0 WHERE ticketStatus = 'FREE' AND (ticketPrice IS NULL OR ticketPrice = 0)")
                db.execSQL("UPDATE concerts SET ticketStatus = 'BOUGHT' WHERE ticketStatus = 'FREE'")
                db.execSQL("UPDATE concerts SET ticketStatus = 'NOT_BOUGHT' WHERE ticketStatus IN ('WISHLIST', 'LOTTERY')")
            }
        }

        // v19 → v20：購票狀況改四態（無 / 已購票 / 已取票 / 已看過，見 TicketStatus）。
        //   未購票(NOT_BOUGHT) → 無(NONE)；已購票維持不變（已取票 / 已看過為新值，舊資料沒有）。
        //   同時建立編輯頁自動草稿表（3 欄 JSON blob，見 ConcertDraftEntity）。
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE concerts SET ticketStatus = 'NONE' WHERE ticketStatus = 'NOT_BOUGHT'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `concert_drafts` " +
                        "(`draftKey` TEXT NOT NULL, `payload` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`draftKey`))"
                )
            }
        }

        // v20 → v21：選歌與演唱會連動 —— daily_songs 加 concertId（可為 null，舊資料全部留 null）
        // 並補上索引（Room 的 schema 驗證會比對索引，缺了會在啟動時丟 IllegalStateException）。
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_songs ADD COLUMN concertId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_songs_concertId` ON `daily_songs` (`concertId`)")
            }
        }

        // v21 → v22：concerts 加 updatedAt（雲端雙向同步的 last-write-wins 依據）。
        // 舊資料一律以 createdAt 當初始值——不能填 0 或「現在」：
        //   填 0    → 首次同步時本機全部被雲端舊備份蓋掉
        //   填「現在」→ 本機全部視為最新，雲端上真正較新的修改會被蓋掉
        // 用 createdAt 至少反映這筆資料實際存在的時間，兩邊比較才有意義。
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concerts ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE concerts SET updatedAt = createdAt")
            }
        }

        // v22 → v23：多組開賣時間。concerts 加 saleTimes（Converters 的「名稱|ISO;名稱|ISO」字串）。
        // 舊的單一 saleStartAt **欄位保留不動**：拿掉它得重建整張表，風險遠大於留著一欄；
        // 它之後由 saleTimes 推導後一起寫入（＝最早的一組），讀取一律以 saleTimes 為準。
        // 既有資料轉成一筆「無名稱」的開賣時間，前面加的 '|' 就是空名稱那一段。
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concerts ADD COLUMN saleTimes TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE concerts SET saleTimes = '|' || saleStartAt " +
                        "WHERE saleStartAt IS NOT NULL AND saleStartAt != ''"
                )
            }
        }

        // v23 → v24：取票序號截圖。單純加一欄，沒有資料要轉換。
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concerts ADD COLUMN pickupShotUri TEXT")
            }
        }
    }
}
