package com.bandary.data.util

import com.bandary.domain.model.Concert
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 備份「兩邊取多的」。這段寫錯不會當掉，只會讓雲端照片默默變少
 * （實機：原本 50 幾張，備份完只剩 7 張）或是同一張照片被重複合併。
 */
class ConcertImageMergeTest {

    private fun concert(
        photos: List<String> = emptyList(),
        cover: String? = null,
        dayCovers: List<String> = emptyList()
    ) = Concert(
        id = "c1",
        dateTime = LocalDateTime(2026, 7, 18, 19, 0),
        photoUri = cover,
        photoUris = photos,
        dayCovers = dayCovers
    )

    private val cloudA = "content://media/picker/0/1"
    private val cloudB = "content://media/picker/0/2"

    @Test fun `本機比較少時算得出少了幾張`() {
        val local = concert(photos = listOf(cloudA))
        val cloud = concert(photos = listOf(cloudA, cloudB, "content://x/3"))
        assertEquals(2, ConcertImageMerge.missingCount(local, cloud))
        assertEquals(0, ConcertImageMerge.missingCount(cloud, local))
    }

    @Test fun `合併後兩邊的照片都在，而且不重複`() {
        val local = concert(photos = listOf(cloudA, "file:///data/images/new.jpg"))
        val cloud = concert(photos = listOf(cloudA, cloudB))
        val merged = ConcertImageMerge.union(local, cloud)
        assertEquals(listOf(cloudA, "file:///data/images/new.jpg", cloudB), merged.photoUris)
    }

    @Test fun `從雲端還原回來的檔案跟原網址算同一張`() {
        val restored = "file:///data/user/0/com.bandary.app/files/restored_images/${ConcertImageMerge.sha1(cloudB)}.jpg"
        val local = concert(photos = listOf(restored))
        val cloud = concert(photos = listOf(cloudB))
        assertEquals(0, ConcertImageMerge.missingCount(local, cloud))
        assertEquals(listOf(restored), ConcertImageMerge.union(local, cloud).photoUris)
    }

    @Test fun `封面只在本機沒有時才補，各日封面依位置補空格`() {
        val local = concert(cover = "file:///mine.jpg", dayCovers = listOf("", "file:///d2.jpg"))
        val cloud = concert(cover = "content://theirs", dayCovers = listOf("content://d1", "content://d2"))
        val merged = ConcertImageMerge.union(local, cloud)
        assertEquals("file:///mine.jpg", merged.photoUri)
        assertEquals(listOf("content://d1", "file:///d2.jpg"), merged.dayCovers)
    }

    @Test fun `內建封面不算照片`() {
        val c = concert(cover = "file:///android_asset/posters/3.jpg", photos = listOf(cloudA))
        assertEquals(listOf(cloudA), ConcertImageMerge.images(c))
    }
}
