package com.bandary.presentation.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 分享海報 / 演唱會封面的內建背景圖（Batch D）。
 *
 * 圖檔放在 assets/posters/bg_1.* … bg_5.*（見該資料夾 README）。副檔名接受 webp / jpg / jpeg / png，
 * 放進去就自動偵測，不用轉檔。偵測不到就回空 / null，呼叫端沿用既有的程式繪製漸層底，不會壞。
 */
object PosterBackgrounds {
    const val COUNT = 5

    /** 分享底圖清單用的特殊值：以 "poster:" 開頭 + index（0..4）。 */
    const val PREFIX = "poster:"
    fun key(index: Int) = "$PREFIX$index"
    fun indexOfKey(key: String?): Int? =
        key?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toIntOrNull()?.takeIf { it in 0 until COUNT }

    private val EXTS = listOf("webp", "jpg", "jpeg", "png")

    @Volatile private var assetFilesCache: Set<String>? = null

    private fun assetFiles(context: Context): Set<String> =
        assetFilesCache ?: runCatching { context.assets.list("posters")?.toSet().orEmpty() }
            .getOrDefault(emptySet())
            .also { assetFilesCache = it }

    /**
     * 已解好的縮圖（key = index@寬x高）。底圖只有 5 張、縮圖也小，全部留著比每次重解便宜太多；
     * 原圖尺寸（reqWidth/reqHeight <= 0 或很大）的不快取，那是產生分享圖時才用、用完就該放掉。
     */
    private val thumbCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    /** 該 index 實際存在的檔名（含副檔名），沒有回 null。 */
    private fun fileName(context: Context, index: Int): String? {
        val files = assetFiles(context)
        return EXTS.map { "bg_${index + 1}.$it" }.firstOrNull { it in files }
    }

    /** 目前實際放進 assets 的背景圖 index（沒放圖時為空清單）。 */
    fun availableIndices(context: Context): List<Int> = (0 until COUNT).filter { fileName(context, it) != null }
    fun hasAny(context: Context): Boolean = availableIndices(context).isNotEmpty()

    /** 當作演唱會封面 / 一般圖片來源用的 asset URI —— Coil (AsyncImage) 可直接載入，
     *  所以存進 concert.photoUri 後，卡片 / 月曆 / 詳情頁都能照常顯示，無需特別處理。放圖後才有值。 */
    fun coverUri(context: Context, index: Int): String? =
        fileName(context, index)?.let { "file:///android_asset/posters/$it" }
    fun isCoverUri(uri: String?) = uri?.startsWith("file:///android_asset/posters/") == true

    /** 讀取某張背景圖；讀不到回 null。[reqWidth]/[reqHeight] 提供時做等比縮取樣省記憶體。 */
    fun load(context: Context, index: Int, reqWidth: Int = 0, reqHeight: Int = 0): Bitmap? {
        val path = fileName(context, index)?.let { "posters/$it" } ?: return null
        val cacheKey = "$index@${reqWidth}x$reqHeight".takeIf {
            reqWidth in 1..400 && reqHeight in 1..400
        }
        cacheKey?.let { k -> thumbCache[k]?.let { return it } }
        return runCatching {
            if (reqWidth <= 0 || reqHeight <= 0) {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > reqWidth * 2 || bounds.outHeight / sample > reqHeight * 2) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            }
        }.getOrNull()?.also { bmp -> cacheKey?.let { thumbCache[it] = bmp } }
    }
}
