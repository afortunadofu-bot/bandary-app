package com.bandary.presentation.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import androidx.core.graphics.ColorUtils

/**
 * 「我最常聽的 N 首歌」分享圖。
 *
 * ## 為什麼另外做一支，不沿用 [StatCardRenderer]
 * 那支是**方形的資料卡**：淡底、深字、橫條，為「看得懂」設計的。
 * 這張是為了**被轉發**設計的，兩者的取捨正好相反：
 *
 * | | StatCardRenderer | 這一支 |
 * |---|---|---|
 * | 比例 | 1:1（貼文） | **9:16（限時動態）** |
 * | 底色 | 很淡（不搶資料） | **深色大色塊**（滑過去會停） |
 * | 重點 | 數值精確 | **歌名大、排名大** |
 * | 行數 | 最多 6 | **最多 10**（「十首歌」本身就是個梗） |
 *
 * 限時動態是這類內容真正會被分享的地方，而它是直的；用方形圖去貼，
 * 上下會被裁或留黑邊，第一眼就輸了。
 *
 * ## 為什麼不放封面圖
 * 專輯封面是唱片公司的，拼成一張圖發出去是散布他人著作。
 * 文字排版做得夠好就不需要圖——Spotify Wrapped 最紅的那幾版也都是純文字。
 */
object TopSongsCardRenderer {

    /** 一首歌。[count] 是被記錄的次數；1 次的不顯示「×1」（沒有資訊量）。 */
    data class Song(val title: String, val artist: String, val count: Int)

    data class Card(
        val songs: List<Song>,
        /** 例：「2026 年」「全部時間」。 */
        val period: String,
        /** 總共記了幾首、幾天。放在頁尾當佐證，讓這張圖不只是排行。 */
        val totalSongs: Int,
        val activeDays: Int,
        val accentHex: String = "#6B9080",
        val nickname: String? = null,
        val width: Int = 1080,
        val height: Int = 1920
    )

    /** 最多列幾首。十是這個梗的標準長度，也是 9:16 放得下又不擠的上限。 */
    private const val MAX_ROWS = 10

    /** [listLayout] 算出來的清單版面：每列多高、從哪裡開始畫。 */
    internal data class ListLayout(val rowH: Float, val startY: Float)

    /**
     * 清單要怎麼排。**重點是歌數少的時候不要把列高撐開。**
     *
     * ## 原本是壞的
     * 原本直接 `rowH = 可用高度 / 歌數`，十首剛好，但：
     *
     * | 歌數 | 每列高 | 藝人名離歌名 |
     * |---|---|---|
     * | 10 | 118 px | 35 px ✓ |
     * | 3 | 392 px | **118 px** |
     * | 1 | 1176 px | **353 px** |
     *
     * 藝人名是畫在 `baseline + rowH * 0.30` 的位置，列高一撐開，
     * 它就跟自己的歌名隔了一整個手指寬——看起來不像「留白」，像**壞掉**。
     * 而歌數少的人正是剛開始用的新使用者，也就是最可能分享這張圖的人。
     *
     * ## 現在的作法
     * 列高**不超過十首時的理想值太多**（上限 1.35 倍），多出來的空間不平分給每一列，
     * 而是讓整塊清單**垂直置中**。少數幾首就是一塊置中的清單配大量留白——
     * 那是刻意的版面，不是沒填滿的版面。
     */
    internal fun listLayout(top: Float, bottom: Float, rowCount: Int, idealRowH: Float): ListLayout {
        if (rowCount <= 0) return ListLayout(0f, top)
        val available = (bottom - top).coerceAtLeast(0f)
        val rowH = (available / rowCount).coerceAtMost(idealRowH * 1.35f)
        val used = rowH * rowCount
        return ListLayout(rowH, top + (available - used) / 2f)
    }

    // 字型用 lazy 而不是直接初始化：Typeface 是 Android framework 的類別，
    // 在一般 JVM 單元測試裡建不起來。直接初始化的話，光是碰到這個 object
    // （例如去測 listLayout 的幾何）就會 ExceptionInInitializerError。
    // 延後到 render 真的要畫的時候才建，幾何就測得到了。
    private val serifBold by lazy { Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    private val sans by lazy { Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
    private val sansBold by lazy { Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }

    fun render(context: Context, card: Card): Bitmap {
        val w = card.width
        val h = card.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val accent = runCatching { AndroidColor.parseColor(card.accentHex) }
            .getOrDefault(0xFF6B9080.toInt())

        // ── 底：主色往深處壓的漸層 ─────────────────────────────
        // 不用純黑也不用淡色。純黑沒有身分（誰的圖都長一樣），
        // 淡色在限時動態的白色介面裡會整張糊掉。
        val top = ColorUtils.blendARGB(accent, AndroidColor.BLACK, 0.55f)
        val bottom = ColorUtils.blendARGB(accent, AndroidColor.BLACK, 0.82f)
        canvas.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        })

        val margin = w * 0.095f
        val contentW = w - margin * 2
        // 深底上的字：亮色系。副標降透明度而不是換色，才不會多出一個顏色。
        val paper = ColorUtils.blendARGB(accent, AndroidColor.WHITE, 0.92f)
        val paperSoft = ColorUtils.setAlphaComponent(paper, 165)
        val highlight = ColorUtils.blendARGB(accent, AndroidColor.WHITE, 0.55f)

        // ── 頁首 ──────────────────────────────────────────────
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlight; typeface = sansBold; textSize = w * 0.026f; letterSpacing = 0.24f
        }
        var y = margin + brand.textSize
        canvas.drawText("BANDARY 聽團曆", margin, y, brand)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paper; typeface = serifBold; textSize = w * 0.098f
        }
        y += w * 0.085f + titlePaint.textSize * 0.2f
        canvas.drawText("我最常聽的", margin, y, titlePaint)
        y += titlePaint.textSize * 1.05f
        val n = minOf(card.songs.size, MAX_ROWS)
        canvas.drawText("$n 首歌", margin, y, titlePaint)

        val periodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paperSoft; typeface = sans; textSize = w * 0.032f
        }
        y += w * 0.052f
        canvas.drawText(card.period, margin, y, periodPaint)

        // ── 清單 ──────────────────────────────────────────────
        val rows = card.songs.take(MAX_ROWS)
        val listTop = y + h * 0.045f
        val listBottom = h - margin - h * 0.075f
        // 理想列高＝滿十首時的高度。少於十首不要把列撐開，見 listLayout 的說明。
        val layout = listLayout(listTop, listBottom, rows.size, (listBottom - listTop) / MAX_ROWS)
        val rowH = layout.rowH

        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansBold; textSize = w * 0.040f; letterSpacing = 0.04f
        }
        val songPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paper; typeface = sansBold; textSize = w * 0.050f
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paperSoft; typeface = sans; textSize = w * 0.032f
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlight; typeface = sansBold; textSize = w * 0.032f
        }

        val rankW = w * 0.105f
        rows.forEachIndexed { i, song ->
            val rowTop = layout.startY + rowH * i
            val baseline = rowTop + rowH * 0.42f

            // 第一名用亮色、其餘半透明：排名靠顏色就讀得出來，不用再加裝飾。
            rankPaint.color = if (i == 0) highlight else ColorUtils.setAlphaComponent(paper, 110)
            canvas.drawText("%02d".format(i + 1), margin, baseline, rankPaint)

            // 次數靠右，先量寬度再決定歌名能用多少空間——不先量的話長歌名會壓在次數上。
            val countText = if (song.count > 1) "×${song.count}" else ""
            val countW = if (countText.isEmpty()) 0f else countPaint.measureText(countText) + w * 0.02f
            if (countText.isNotEmpty()) {
                canvas.drawText(countText, margin + contentW - countW + w * 0.02f, baseline, countPaint)
            }

            val textX = margin + rankW
            val textW = contentW - rankW - countW
            canvas.drawText(ellipsize(song.title, songPaint, textW), textX, baseline, songPaint)
            if (song.artist.isNotBlank()) {
                canvas.drawText(
                    ellipsize(song.artist, artistPaint, textW),
                    textX, baseline + rowH * 0.30f, artistPaint
                )
            }

            // 分隔線：最後一列不畫（底下沒有東西要分隔）。
            if (i < rows.size - 1) {
                canvas.drawLine(
                    margin, rowTop + rowH * 0.92f, margin + contentW, rowTop + rowH * 0.92f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = ColorUtils.setAlphaComponent(paper, 38); strokeWidth = w * 0.0015f
                    }
                )
            }
        }

        // ── 頁尾：佐證 ────────────────────────────────────────
        // 只放排行的話，看的人不知道這是「記了三首」還是「記了三百首」選出來的。
        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paperSoft; typeface = sans; textSize = w * 0.030f
        }
        val foot = buildString {
            card.nickname?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
            append("共記了 ${card.totalSongs} 首")
            if (card.activeDays > 0) append(" · ${card.activeDays} 天有記錄")
        }
        canvas.drawText(ellipsize(foot, footPaint, contentW), margin, h - margin, footPaint)

        return bmp
    }

    /** 超過寬度就截字加省略號。直接畫出去的話會被裁掉一半，看起來像壞掉。 */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
