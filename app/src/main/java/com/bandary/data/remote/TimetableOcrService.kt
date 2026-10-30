package com.bandary.data.remote

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 一個 OCR 偵測到的文字方塊（座標為原始圖片像素）。 */
data class TextHotspot(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class OcrResult(
    val spots: List<TextHotspot>,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * 圖片文字辨識：掃海報找卡司、掃歌單、解讀票根都走這裡。
 *
 * 原本只是「丟檔案給中文辨識器」，實機回報歌單與票根的辨識度都很差。
 * 三個已知原因，這裡一次處理：
 *
 * 1. **沒有前處理**。演唱會現場的歌單／電光看板照片多半又暗又糊，票根則是熱感應紙
 *    低對比、常泛灰。ML Kit 對「灰底灰字」特別不行。先轉灰階再拉對比，命中率差很多。
 * 2. **小圖直接送進去**。ML Kit 需要文字高度約 16px 以上才穩；遠遠拍的看板、
 *    或被相簿壓過的縮圖，字根本不到。太小的圖先放大再送。
 * 3. **只用中文辨識器**。中文模型認得拉丁字母，但對**純英數**（取票序號、訂單編號、
 *    場地英文名）的準確度明顯不如專門的拉丁模型。現在兩個模型都跑，結果合併。
 *
 * 合併而不是二選一：兩邊各有擅長的行，取聯集讓票根解讀（靠標籤比對）與逐行挑選
 * 都拿到更多候選；重複的行會正規化後去掉。
 */
@Singleton
class TimetableOcrService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val chineseRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /** 拉丁模型：票根上的取票序號、訂單編號這種純英數，它比中文模型準得多。 */
    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(uri: String): OcrResult = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = loadForOcr(Uri.parse(uri)) ?: return@withContext OcrResult(emptyList(), 0, 0)
            val image = InputImage.fromBitmap(bitmap, 0)

            val chinese = runCatching { Tasks.await(chineseRecognizer.process(image)) }.getOrNull()
            val latin = runCatching { Tasks.await(latinRecognizer.process(image)) }.getOrNull()

            val spots = (hotspotsOf(chinese) + hotspotsOf(latin)).dedupeByText()
            OcrResult(spots, bitmap.width, bitmap.height)
        }.getOrElse { OcrResult(emptyList(), 0, 0) }
    }

    private fun hotspotsOf(text: com.google.mlkit.vision.text.Text?): List<TextHotspot> =
        text?.textBlocks.orEmpty()
            .flatMap { it.lines }
            .mapNotNull { line ->
                val bb = line.boundingBox ?: return@mapNotNull null
                TextHotspot(line.text.trim(), bb.left, bb.top, bb.right, bb.bottom)
            }
            .filter { it.text.isNotBlank() }

    /**
     * 兩個模型會抓到同一行。以「去空白 + 轉小寫」當鍵去重，保留先出現的那筆
     * （中文模型排前面，中文行的斷行通常比較正確）。
     */
    private fun List<TextHotspot>.dedupeByText(): List<TextHotspot> {
        val seen = HashSet<String>()
        return filter { seen.add(it.text.replace(Regex("\\s+"), "").lowercase()) }
            // 依版面由上到下、由左到右排；票根解讀靠「標籤在上、值在下」，順序不能亂。
            .sortedWith(compareBy({ it.top / 16 }, { it.left }))
    }

    // ── 前處理 ──────────────────────────────────────────────────────────

    /**
     * 讀圖 → 轉正 → 尺寸調整 → 灰階拉對比。回傳可直接餵給 ML Kit 的 bitmap。
     */
    private fun loadForOcr(uri: Uri): android.graphics.Bitmap? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 太大的圖先用 inSampleSize 粗縮，避免一次解一張 4000px 的圖進記憶體。
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIM * 2 || bounds.outHeight / sample > MAX_DIM * 2) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        // BitmapFactory 不看 EXIF：直拍的照片解出來是躺著的，ML Kit 對旋轉的文字很不行。
        val upright = com.bandary.data.util.ImageStore.applyExifRotation(context, uri, decoded)
        if (upright !== decoded) decoded.recycle()

        val resized = resizeForOcr(upright)
        if (resized !== upright) upright.recycle()

        val enhanced = enhanceContrast(resized)
        if (enhanced !== resized) resized.recycle()
        enhanced
    }.getOrNull()

    /** 太小放大、太大縮小。放大用雙線性即可——目的是讓筆畫夠粗，不是還原細節。 */
    private fun resizeForOcr(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val long = maxOf(src.width, src.height)
        if (long in MIN_DIM..MAX_DIM) return src
        val scale = if (long < MIN_DIM) MIN_DIM.toFloat() / long else MAX_DIM.toFloat() / long
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return runCatching { android.graphics.Bitmap.createScaledBitmap(src, w, h, true) }.getOrDefault(src)
    }

    /**
     * 轉灰階並拉對比。
     *
     * 用 ColorMatrix 做：先去飽和（saturation = 0），再以 scale/translate 拉對比。
     * 係數 1.6 / -60 是對「暗場照片」與「熱感應紙」都還堪用的折衷——
     * 拉太兇會把淡的筆畫直接壓成白底，反而更糟。
     */
    private fun enhanceContrast(src: android.graphics.Bitmap): android.graphics.Bitmap = runCatching {
        val out = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val contrast = 1.6f
        val shift = -60f
        val matrix = android.graphics.ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, shift,
                        0f, contrast, 0f, 0f, shift,
                        0f, 0f, contrast, 0f, shift,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            isAntiAlias = true
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        out
    }.getOrDefault(src)

    private companion object {
        /** ML Kit 建議的長邊上限。再大不會更準，只會更慢更耗記憶體。 */
        const val MAX_DIM = 2400

        /** 長邊小於這個值就放大：字太小 ML Kit 認不出來。 */
        const val MIN_DIM = 1280
    }
}
