package com.bandary.presentation.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min

/**
 * 單一統計圖表 → 一張可以直接發到社群的方形卡片。
 *
 * 跟 [PosterRenderer] 的分工：那個是「一整期的摘要海報」（票根風、直式 1080×1920、有底圖與版型選擇）；
 * 這裡是「把某一張圖表單獨抽出來」——正方形、資料本身就是主角、不需要選版型。
 * 使用者在統計頁看到哪張圖表好看，就分享哪一張，不必先進分享頁再重挑一次內容。
 *
 * 為什麼要重畫而不是螢幕截圖：截圖會連狀態列、底部導覽列、置頂按鈕一起入鏡，
 * 發出去看起來像「軟體截圖」而不是作品。
 */
object StatCardRenderer {

    /** 卡片呈現方式。依資料形狀選，不是依畫面順序。 */
    enum class Kind {
        BARS,     // 橫條排行：藝人、場館、縣市、平台、花費
        DONUT,    // 佔比：類型分布、縣市分布
        NUMBER    // 單一大數字：總場次、總時數、回訪率
    }

    /** 一列資料。[value] 用來算長度／角度，[display] 是要印出來的字（已含單位與千分位）。 */
    data class Row(val label: String, val value: Double, val display: String)

    data class Card(
        val title: String,            // 例：「看最多次的藝人」
        val period: String,           // 例：「2026 年」或「全部時間」
        val kind: Kind,
        val rows: List<Row> = emptyList(),
        val headline: String? = null, // NUMBER 用的大字；BARS/DONUT 可留空
        val caption: String? = null,  // 一句補充，例：「回訪的藝人 20 / 55 組」
        val accentHex: String,
        val size: Int = 1080
    )

    // 一張卡最多列幾條。超過會擠成細線，反而看不出排名。
    private const val MAX_ROWS = 6

    private val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    fun render(context: Context, card: Card): Bitmap {
        val s = card.size
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val accent = runCatching { AndroidColor.parseColor(card.accentHex) }.getOrDefault(0xFF6B9080.toInt())

        // 底色：主色調很淡的一層，讓卡片有身分又不搶資料。純白太像截圖。
        val bg = ColorUtils.blendARGB(accent, AndroidColor.WHITE, 0.94f)
        val ink = ColorUtils.blendARGB(accent, AndroidColor.BLACK, 0.82f)
        val inkSoft = ColorUtils.blendARGB(accent, AndroidColor.BLACK, 0.45f)
        canvas.drawColor(bg)

        val margin = s * 0.085f
        val contentW = s - margin * 2

        // ── 頁首：品牌行 + 標題 + 期間 ──
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent; typeface = sansBold; textSize = s * 0.026f; letterSpacing = 0.22f
        }
        canvas.drawText("BANDARY 聽團曆", margin, margin + brand.textSize, brand)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = serifBold; textSize = s * 0.062f
        }
        var y = margin + s * 0.10f + titlePaint.textSize
        canvas.drawText(ellipsize(card.title, titlePaint, contentW), margin, y, titlePaint)

        val periodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSoft; typeface = sans; textSize = s * 0.030f
        }
        y += s * 0.048f
        canvas.drawText(card.period, margin, y, periodPaint)

        // 標題與內容之間拉一條主色細線當分隔，比留白更明確
        y += s * 0.030f
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = s * 0.005f }
        canvas.drawLine(margin, y, margin + s * 0.10f, y, rule)

        val bodyTop = y + s * 0.055f
        val bodyBottom = s - margin - s * 0.055f   // 留給頁尾
        when (card.kind) {
            Kind.BARS -> drawBars(canvas, card, margin, contentW, bodyTop, bodyBottom, accent, ink, inkSoft, s)
            Kind.DONUT -> drawDonut(canvas, card, margin, contentW, bodyTop, bodyBottom, accent, ink, inkSoft, s)
            Kind.NUMBER -> drawNumber(canvas, card, margin, contentW, bodyTop, bodyBottom, accent, ink, inkSoft, s)
        }

        // ── 頁尾：補充說明 ──
        card.caption?.takeIf { it.isNotBlank() }?.let {
            val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkSoft; typeface = sans; textSize = s * 0.026f
            }
            canvas.drawText(ellipsize(it, cap, contentW), margin, s - margin, cap)
        }
        return bmp
    }

    /** 橫條排行。名次、名稱、長條、數值四欄各自對齊，長名稱截斷而不是換行擠壞版面。 */
    private fun drawBars(
        canvas: Canvas, card: Card, margin: Float, contentW: Float,
        top: Float, bottom: Float, accent: Int, ink: Int, inkSoft: Int, s: Int
    ) {
        val rows = card.rows.take(MAX_ROWS)
        if (rows.isEmpty()) return
        val maxV = max(rows.maxOf { it.value }, 1.0)
        val slot = (bottom - top) / rows.size
        val barH = min(slot * 0.30f, s * 0.030f)

        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansBold; textSize = s * 0.028f; color = inkSoft
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansBold; textSize = s * 0.036f; color = ink
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansBold; textSize = s * 0.034f; color = accent
            textAlign = Paint.Align.RIGHT
        }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.blendARGB(accent, AndroidColor.WHITE, 0.80f)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }

        val rankW = s * 0.048f
        rows.forEachIndexed { i, r ->
            val rowTop = top + slot * i
            val textY = rowTop + labelPaint.textSize
            canvas.drawText("${i + 1}", margin, textY, rankPaint)

            // 數值先量寬，名稱能用的寬度才算得準——先畫名稱再截斷會壓到數值。
            val valW = valuePaint.measureText(r.display)
            val labelMax = contentW - rankW - valW - s * 0.03f
            canvas.drawText(ellipsize(r.label, labelPaint, labelMax), margin + rankW, textY, labelPaint)
            canvas.drawText(r.display, margin + contentW, textY, valuePaint)

            val barTop = textY + s * 0.016f
            val barW = contentW - rankW
            val radius = barH / 2f
            canvas.drawRoundRect(
                RectF(margin + rankW, barTop, margin + rankW + barW, barTop + barH), radius, radius, trackPaint
            )
            // 最短也留一點長度，否則只看過 1 次的那條會消失，看起來像資料壞了。
            val w = max((r.value / maxV).toFloat() * barW, barH)
            canvas.drawRoundRect(
                RectF(margin + rankW, barTop, margin + rankW + w, barTop + barH), radius, radius, fillPaint
            )
        }
    }

    /** 甜甜圈 + 圖例。中央放總數，一眼看得出母體有多大。 */
    private fun drawDonut(
        canvas: Canvas, card: Card, margin: Float, contentW: Float,
        top: Float, bottom: Float, accent: Int, ink: Int, inkSoft: Int, s: Int
    ) {
        val rows = card.rows.take(MAX_ROWS)
        if (rows.isEmpty()) return
        val total = rows.sumOf { it.value }.takeIf { it > 0 } ?: return

        val d = min(bottom - top, contentW * 0.52f)
        val stroke = d * 0.20f
        val cx = margin + d / 2f
        val cy = top + d / 2f
        val box = RectF(cx - d / 2f + stroke / 2f, cy - d / 2f + stroke / 2f,
            cx + d / 2f - stroke / 2f, cy + d / 2f - stroke / 2f)

        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke
        }
        var start = -90f
        rows.forEachIndexed { i, r ->
            arc.color = shade(accent, i, rows.size)
            val sweep = ((r.value / total) * 360.0).toFloat()
            // 每段留一點縫，相鄰的深淺色才分得出來
            canvas.drawArc(box, start + 0.8f, max(sweep - 1.6f, 0.5f), false, arc)
            start += sweep
        }
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = serifBold; textSize = d * 0.22f; color = ink; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            formatNumber(total), cx, cy + centerPaint.textSize * 0.35f, centerPaint
        )

        // 圖例排右側
        val legendX = margin + d + s * 0.045f
        val legendW = contentW - d - s * 0.045f
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = s * 0.030f; color = ink }
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansBold; textSize = s * 0.030f; color = inkSoft; textAlign = Paint.Align.RIGHT
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val step = min((bottom - top) / rows.size, s * 0.062f)
        rows.forEachIndexed { i, r ->
            val ly = top + step * i + lp.textSize
            dot.color = shade(accent, i, rows.size)
            val dotR = s * 0.011f
            canvas.drawCircle(legendX + dotR, ly - lp.textSize * 0.32f, dotR, dot)
            val pctText = "${Math.round(r.value / total * 100)}%"
            val pctW = pp.measureText(pctText)
            val nameMax = legendW - dotR * 2 - s * 0.018f - pctW - s * 0.012f
            canvas.drawText(
                ellipsize(r.label, lp, nameMax), legendX + dotR * 2 + s * 0.018f, ly, lp
            )
            canvas.drawText(pctText, margin + contentW, ly, pp)
        }
    }

    /** 單一大數字：數字本身就是主角，佔滿版心。 */
    private fun drawNumber(
        canvas: Canvas, card: Card, margin: Float, contentW: Float,
        top: Float, bottom: Float, accent: Int, ink: Int, inkSoft: Int, s: Int
    ) {
        val text = card.headline.orEmpty()
        if (text.isBlank()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serifBold; color = accent }
        // 依字數自動縮到剛好塞得下版心寬，長數字（NT$1,234,567）才不會被切掉。
        paint.textSize = s * 0.28f
        while (paint.measureText(text) > contentW && paint.textSize > s * 0.06f) {
            paint.textSize -= s * 0.006f
        }
        val cy = (top + bottom) / 2f
        canvas.drawText(text, margin, cy + paint.textSize * 0.32f, paint)

        // 大數字底下把組成列出來，讓卡片不只是一個數字
        val rows = card.rows.take(3)
        if (rows.isNotEmpty()) {
            val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = s * 0.032f; color = ink }
            var ry = cy + paint.textSize * 0.32f + s * 0.075f
            rows.forEach { r ->
                canvas.drawText(ellipsize("${r.label}　${r.display}", rp, contentW), margin, ry, rp)
                ry += s * 0.048f
            }
        }
    }

    /** 同一個主色的深淺階，用來區分甜甜圈的每一段——不引進第二個色相，卡片才有一致的身分。 */
    private fun shade(accent: Int, index: Int, count: Int): Int {
        if (count <= 1) return accent
        val t = index / (count - 1).toFloat()      // 0 → 1
        return ColorUtils.blendARGB(accent, AndroidColor.WHITE, t * 0.62f)
    }

    private fun formatNumber(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite()) "%,d".format(v.toLong()) else "%,.1f".format(v)

    /** 超出寬度就截斷加省略號。Paint 沒有內建，自己來——不做的話長團名會直接壓到隔壁欄。 */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ell = "…"
        val ellW = paint.measureText(ell)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ellW > maxWidth) end--
        return if (end <= 0) ell else text.substring(0, end) + ell
    }
}
