package com.bandary.data.util

import com.bandary.domain.model.Concert
import java.security.MessageDigest

/**
 * 同一場演唱會在手機與雲端各有一份時，比較兩邊的照片、以及「兩邊取多的」合併。
 *
 * 為什麼需要：場次同步原本是「誰的 updatedAt 新就整筆蓋掉對方」。只要較新的那一份照片比較少
 * （換手機後照片還沒還原、清理過找不到的照片、在另一台少了照片的裝置上改過一個字），
 * 雲端的照片清單就被整批蓋成少的那份，接著圖片清理再把沒人引用的照片刪掉——
 * 實機回報：原本 50 幾張，備份完只剩 7 張。
 *
 * 照片比對用 [identity] 而不是網址字串：從雲端還原回來的照片檔名是
 * restored_images/{sha1(原網址)}.jpg，跟原本那支手機上的網址不同，但其實是同一張。
 */
object ConcertImageMerge {

    private const val RESTORED_DIR = "/restored_images/"

    fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** 一張照片的身分：還原檔用檔名裡的原網址 hash，其餘用網址本身的 hash（與雲端圖片文件 id 相同）。 */
    fun identity(uri: String): String {
        if (uri.contains(RESTORED_DIR) && uri.endsWith(".jpg")) {
            return uri.substringAfterLast('/').removeSuffix(".jpg")
        }
        return sha1(uri)
    }

    /** 這場的所有照片（不含內建封面，那是每台裝置都有的 App 內建圖）。 */
    fun images(c: Concert): List<String> =
        (listOfNotNull(c.photoUri, c.seatMapUri, c.pickupShotUri) + c.photoUris + c.timetableUris + c.dayCovers)
            .filter { it.isNotBlank() && !it.startsWith("file:///android_asset/") }
            .distinctBy(::identity)

    /** [other] 有、[primary] 沒有的照片張數。 */
    fun missingCount(primary: Concert, other: Concert): Int {
        val have = images(primary).map(::identity).toSet()
        return images(other).count { identity(it) !in have }
    }

    /**
     * 以 [primary] 為主（文字資料、updatedAt 都用它的），把 [other] 有、[primary] 沒有的照片補進來。
     * 單張欄位（封面、座位圖、取票截圖）只在 primary 空白時補；各日封面依位置補空格；
     * 多張的照片 / 節目表接在後面。
     */
    fun union(primary: Concert, other: Concert): Concert {
        val have = images(primary).map(::identity).toMutableSet()
        fun take(uri: String?): String? =
            uri?.takeIf { it.isNotBlank() && !it.startsWith("file:///android_asset/") && have.add(identity(it)) }
        fun single(mine: String?, theirs: String?): String? =
            mine?.takeIf { it.isNotBlank() } ?: take(theirs) ?: mine

        val photoUri = single(primary.photoUri, other.photoUri)
        val seatMapUri = single(primary.seatMapUri, other.seatMapUri)
        val pickupShotUri = single(primary.pickupShotUri, other.pickupShotUri)
        val dayCovers = primary.dayCovers.mapIndexed { i, mine ->
            mine.takeIf { it.isNotBlank() } ?: take(other.dayCovers.getOrNull(i)) ?: mine
        }
        val photoUris = primary.photoUris + other.photoUris.mapNotNull(::take)
        val timetableUris = primary.timetableUris + other.timetableUris.mapNotNull(::take)
        return primary.copy(
            photoUri = photoUri,
            seatMapUri = seatMapUri,
            pickupShotUri = pickupShotUri,
            dayCovers = dayCovers,
            photoUris = photoUris,
            timetableUris = timetableUris
        )
    }
}
