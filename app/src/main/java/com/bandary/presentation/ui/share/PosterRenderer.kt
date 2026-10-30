package com.bandary.presentation.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 統一海報渲染管線（Batch D）：一份 [PosterSpec]（背景 × 配色 × 排版樣式 × 內容區塊）→ 一張 Bitmap。
 * 目前先在統計分享頁當「新排版樣式」的額外選項接上，票根分享/演唱會詳情頁尚未切換（見 ROADMAP Batch D）。
 *
 * 字體：先用系統 Serif / SansSerif 加粗體＋字距近似 Fraunces / Space Grotesk 的視覺方向
 * （不依賴網路下載字型，避免 Google Fonts provider 在未驗證裝置上出問題）。
 */
object PosterRenderer {

    // 這些名稱只作為程式內的識別，**畫面上不顯示**——樣式選擇器一律用構圖縮圖（StyleThumb），
    // 因為「格線誌」「大字出血」這種名字本來就猜不出長什麼樣。
    enum class TypeStyle(val label: String) {
        GRID("格線誌"),
        BLEED("大字出血"),
        VERTICAL("直排"),
        BILINGUAL("中英對照"),
        MINIMAL("簡潔")
    }

    sealed class Background {
        data class Preset(val index: Int) : Background()
        data class Solid(val hex: String) : Background()
        data class Photo(val uri: String) : Background()
        data class Bitmap(val bitmap: android.graphics.Bitmap) : Background()  // 預先合成好的圖（如封面拼圖）
        data object None : Background()
    }

    data class Block(val heading: String, val lines: List<String>)

    data class Spec(
        val width: Int = 1080,
        val height: Int = 1920,
        val background: Background,
        val accentHex: String,
        val style: TypeStyle,
        val title: String,
        val subtitle: String? = null,     // 藝人 / 副標
        val day: String,                  // 給 GRID/BILINGUAL 用的日（例："07"）
        val month: String,                // 給 GRID 用的月小字（例："JUL"）
        val fullDate: String,             // 完整日期字串，MINIMAL/VERTICAL 用
        val venue: String,
        val city: String? = null,
        val blocks: List<Block> = emptyList(),   // 最多 1–2 塊，內容依 TypeStyle 容量顯示
        val textColorHex: String? = null,        // 指定內文顏色（票根的黑白字選擇）；null = 依底色自動選白/深
        // 右下角的邀請 QR（分享網址）。null = 不畫。
        // 票根本來就是大家最愛轉發的圖，把邀請碼印在上面比另外做一張分享圖自然得多。
        val qrPayload: String? = null,
        val qrCaption: String? = null            // QR 下面那行小字（通常是邀請碼本身）
    )

    /**
     * BLEED 樣式的大字最多放幾個字。超過就只放前幾個當視覺，
     * 完整名稱仍由下方的小字標題負責——那一行本來就會自動換行顯示全名。
     */
    private const val MAX_BLEED_CHARS = 8

    /**
     * BLEED 大字要顯示哪幾個字。
     *
     * 活動名稱很常以主辦或系列名的方括號開頭（「【Legacy Presents 秋分：完美的平衡】南西肯恩…」），
     * 直接取前幾個字會得到「【Legacy P」這種看不出是什麼的東西（實機回報）。
     * 先把開頭那一段括號內容整段拿掉，再去掉標點，剩下的才是真正的主體。
     */
    private fun bleedDisplayChars(title: String): String {
        var t = title.trim()
        // 開頭的【…】/（…）/(…)：整段拿掉，那是前綴不是主體。
        // 用字元比對而不是 regex——這裡要處理的括號全是成對的固定字元，
        // 寫成 regex 只會多一堆跳脫。
        val openers = "[【（("
        val closers = "]】）)"
        val at = if (t.isEmpty()) -1 else openers.indexOf(t[0])
        if (at >= 0) {
            val end = t.indexOf(closers[at])
            if (end > 0) t = t.substring(end + 1).trim()
        }
        return t.filter { !it.isWhitespace() && !isPunctuation(it) }
    }

    private fun isCjk(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3040..0x30FF || c.code in 0xAC00..0xD7AF

    private fun isPunctuation(c: Char): Boolean =
        !c.isLetterOrDigit()

    private val displaySerif = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val bodySans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bodySansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    /**
     * 給文字加一層「對比色柔和陰影」：淺字配深影、深字配淺影。
     * 不論底是亮照片、深照片還是純色，字都描得出來——這是「圖上文字看不清楚」最穩的解法。
     * 陰影半徑依字級縮放（大字影子大一點才看得出來）。
     */
    private fun Paint.legible(): Paint {
        val darkText = ColorUtils.calculateLuminance(color) < 0.5
        val shadow = if (darkText) 0x73FFFFFF else 0xB3000000.toInt()
        setShadowLayer((textSize * 0.10f).coerceIn(4f, 14f), 0f, 2f, shadow)
        return this
    }

    fun render(context: Context, spec: Spec): Bitmap {
        val w = spec.width; val h = spec.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val accent = runCatching { AndroidColor.parseColor(spec.accentHex) }.getOrDefault(AndroidColor.WHITE)

        val onBgAuto = drawBackground(context, canvas, w, h, spec.background, accent)
        // 指定內文顏色（票根黑白字）優先；否則用依底色自動判斷的白/深字。
        val onBg = spec.textColorHex?.let { runCatching { AndroidColor.parseColor(it) }.getOrNull() } ?: onBgAuto
        // 12 欄網格，安全邊距 = 短邊 7.5%（四周留白多一點）。
        val margin = (min(w, h) * 0.075f)
        val colW = (w - margin * 2) / 12f
        fun colX(col: Int) = margin + colW * col

        // 固定錨點：頂部品牌行、底部資訊條（每個樣式同位置）。
        drawBrandLine(canvas, margin, onBg, accent)
        drawBottomInfoBar(canvas, margin, w, h, spec, onBg)

        when (spec.style) {
            TypeStyle.GRID -> drawGrid(canvas, spec, margin, colW, ::colX, w, h, onBg, accent)
            TypeStyle.BLEED -> drawBleed(canvas, spec, margin, w, h, onBg, accent)
            TypeStyle.VERTICAL -> drawVertical(canvas, spec, margin, w, h, onBg, accent)
            TypeStyle.BILINGUAL -> drawBilingual(canvas, spec, margin, w, h, onBg, accent)
            TypeStyle.MINIMAL -> drawMinimal(canvas, spec, margin, w, h, onBg, accent)
        }
        drawInviteQr(canvas, spec, margin, w, h, onBg)
        return bitmap
    }

    /**
     * 右下角的邀請 QR。畫在白底圓角方塊上——票根的底可能是深色照片，
     * QR 直接壓在上面會掃不出來（掃描器需要明確的明暗對比）。
     *
     * 產不出來（內容太長之類）就整塊不畫，不要留一個空白方塊在票根上。
     */
    private fun drawInviteQr(
        canvas: Canvas, spec: Spec, margin: Float, w: Int, h: Int, onBg: Int
    ) {
        val payload = spec.qrPayload?.takeIf { it.isNotBlank() } ?: return
        val box = min(w, h) * 0.13f
        val qr = com.bandary.data.util.QrCode.generate(payload, box.toInt()) ?: return

        val pad = box * 0.08f
        val left = w - margin - box - pad * 2
        val top = h - margin - box - pad * 2 - (if (spec.qrCaption.isNullOrBlank()) 0f else box * 0.2f)

        canvas.drawRoundRect(
            left, top, left + box + pad * 2, top + box + pad * 2,
            box * 0.1f, box * 0.1f,
            Paint().apply { color = AndroidColor.WHITE; isAntiAlias = true }
        )
        canvas.drawBitmap(qr, left + pad, top + pad, null)

        spec.qrCaption?.takeIf { it.isNotBlank() }?.let { caption ->
            canvas.drawText(
                caption,
                left + pad + box / 2f,
                top + box + pad * 2 + box * 0.16f,
                Paint().apply {
                    color = onBg
                    textSize = box * 0.15f
                    typeface = bodySansBold
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }
    }

    /**
     * 只產生「背景圖」的 bitmap（供歌單圖等共用票根同一套底圖邏輯：純色 / 內建底圖 / 照片 / 拼圖）。
     * 回傳 (bitmap, 這張底上建議的文字顏色)。呼叫端可在其上自行疊字。
     */
    fun renderBackgroundBitmap(
        context: Context, background: Background, w: Int = 1080, h: Int = 1920, accentHex: String = "#6B9080"
    ): Pair<Bitmap, Int> {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val accent = runCatching { AndroidColor.parseColor(accentHex) }.getOrDefault(AndroidColor.WHITE)
        val onBg = drawBackground(context, canvas, w, h, background, accent)
        return bitmap to onBg
    }

    /** 畫背景（預設圖 / 純色 / 自訂照片 / 無），回傳這張底上文字該用的顏色（白或深色）。 */
    private fun drawBackground(context: Context, canvas: Canvas, w: Int, h: Int, bg: Background, accent: Int): Int {
        when (bg) {
            is Background.Preset -> {
                val photo = PosterBackgrounds.load(context, bg.index, w, h)
                if (photo != null) {
                    drawPhoto(canvas, photo, w, h)
                    return AndroidColor.WHITE
                }
            }
            is Background.Photo -> {
                val photo = loadPhotoUri(context, bg.uri)
                if (photo != null) {
                    drawPhoto(canvas, photo, w, h)
                    return AndroidColor.WHITE
                }
            }
            is Background.Bitmap -> {
                drawPhoto(canvas, bg.bitmap, w, h)
                return AndroidColor.WHITE
            }
            is Background.Solid -> {
                val c = runCatching { AndroidColor.parseColor(bg.hex) }.getOrDefault(AndroidColor.DKGRAY)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = c })
                return if (ColorUtils.calculateLuminance(c) > 0.6) AndroidColor.parseColor("#1E1E1E") else AndroidColor.WHITE
            }
            Background.None -> {}
        }
        // 無圖 / 讀取失敗：用主題色推導的直向漸層當底。
        val c1 = accent
        val c2 = ColorUtils.blendARGB(accent, AndroidColor.BLACK, 0.55f)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), c1, c2, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return AndroidColor.WHITE
    }

    private fun drawPhoto(canvas: Canvas, photo: Bitmap, w: Int, h: Int) {
        val scale = max(w.toFloat() / photo.width, h.toFloat() / photo.height)
        val dw = photo.width * scale; val dh = photo.height * scale
        val left = (w - dw) / 2f; val top = (h - dh) / 2f
        canvas.drawBitmap(photo, null, RectF(left, top, left + dw, top + dh), Paint().apply { isAntiAlias = true })
        // 兩端加深的遮罩：頂部（品牌 / 標題 / 大日期）與底部（地點資訊條）都要壓暗，中間讓照片露出來。
        // 之前只有「上淺下深」，亮照片的頂部標題會糊掉。
        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(
                    AndroidColor.parseColor("#B3000000"),  // 頂：70%
                    AndroidColor.parseColor("#40000000"),  // 上中：25%
                    AndroidColor.parseColor("#40000000"),  // 下中：25%
                    AndroidColor.parseColor("#E6000000")   // 底：90%
                ),
                floatArrayOf(0f, 0.30f, 0.60f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), scrim)
    }

    private fun loadPhotoUri(context: Context, uriStr: String): Bitmap? = runCatching {
        context.contentResolver.openInputStream(android.net.Uri.parse(uriStr))?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
    }.getOrNull()

    private fun drawBrandLine(canvas: Canvas, margin: Float, onBg: Int, accent: Int) {
        canvas.drawText("BANDARY", margin, margin + 44f, Paint().apply {
            color = accent; textSize = 34f; isAntiAlias = true; letterSpacing = 0.24f
            typeface = bodySansBold
        }.legible())
    }

    private fun drawBottomInfoBar(canvas: Canvas, margin: Float, w: Int, h: Int, spec: Spec, onBg: Int) {
        // 地點 · 縣市 · 日期＝全圖唯一放這組資訊的地方（各樣式的內文不再重複標記，避免資訊撞車）。
        val text = buildString {
            append(spec.venue)
            spec.city?.takeIf { it.isNotBlank() }?.let { append("  ·  ").append(it) }
            append("  ·  ").append(spec.fullDate)
        }
        val p = Paint().apply {
            color = ColorUtils.setAlphaComponent(onBg, 0xF0); textSize = 38f; isAntiAlias = true; typeface = bodySans
        }.legible()
        // 最多兩行（長場館＋縣市＋日期塞不下一行時不再被截掉）；從底部往上留兩行高度。
        val lineH = p.textSize * 1.2f
        val lines = wrapLines(text, w - margin * 2, p).size.coerceIn(1, 2)
        val y = h - margin - 6f - (lines - 1) * lineH
        drawWrapped(canvas, text, margin, y, w - margin * 2, p, maxLines = 2)
    }

    /** 內容區塊：標題 + 逐行（每行會換行避免超出框線），回傳畫完後的 Y。 */
    private fun drawBlock(canvas: Canvas, block: Block, x: Float, topY: Float, maxWidth: Float, onBg: Int, maxLines: Int): Float {
        var by = topY
        canvas.drawText(block.heading, x, by, Paint().apply {
            color = ColorUtils.setAlphaComponent(onBg, 0xCC); textSize = 30f; isAntiAlias = true
            typeface = bodySansBold; letterSpacing = 0.1f
        }.legible())
        by += 48f
        var remain = maxLines
        for (line in block.lines) {
            if (remain <= 0) break
            val p = Paint().apply { color = onBg; textSize = 34f; isAntiAlias = true; typeface = bodySans }.legible()
            val end = drawWrapped(canvas, line, x, by, maxWidth, p, maxLines = remain)
            val used = (((end - by) / (p.textSize * 1.2f)).toInt()).coerceAtLeast(1)
            remain -= used
            by = end + 6f
        }
        return by
    }

    /**
     * 依序畫出「所有」資訊塊，畫到超過 [bottomLimit] 就停。
     *
     * 之前 VERTICAL / MINIMAL 完全沒畫 blocks、BLEED 只畫第一塊的第一行，
     * 於是統計分享選到那幾種樣式時只剩一個年份、沒有任何數據。
     * 這裡統一成「每種樣式都至少把資料塊畫出來」，版面不夠就自然截止。
     */
    private fun drawBlocks(
        canvas: Canvas, spec: Spec, x: Float, topY: Float, maxWidth: Float,
        onBg: Int, bottomLimit: Float, maxLinesEach: Int = 4
    ): Float {
        var y = topY
        for (b in spec.blocks) {
            if (y + 90f > bottomLimit) break
            // 依剩餘高度推算這一塊還能畫幾行，避免壓到底條。
            val room = (((bottomLimit - y - 48f) / 42f).toInt()).coerceAtLeast(1)
            y = drawBlock(canvas, b, x, y, maxWidth, onBg, maxLines = minOf(maxLinesEach, room)) + 26f
        }
        return y
    }

    // ── GRID 格線誌：左欄標題＋藝人 / 右欄超大日期 / 左下資訊、右下演出（無地點色塊，地點只在底條）──
    private fun drawGrid(canvas: Canvas, spec: Spec, margin: Float, colW: Float, colX: (Int) -> Float, w: Int, h: Int, onBg: Int, accent: Int) {
        val titlePaint = Paint().apply { color = onBg; textSize = 62f; isAntiAlias = true; typeface = displaySerif }.legible()
        val titleEndY = drawTitleAutoFit(canvas, spec.title, colX(0), margin + 210f, colW * 7, titlePaint, maxLines = 3)
        // 藝人（副標）在標題下
        spec.subtitle?.takeIf { it.isNotBlank() }?.let {
            drawWrapped(canvas, it, colX(0), titleEndY + 18f, colW * 7, Paint().apply {
                color = ColorUtils.setAlphaComponent(onBg, 0xD0); textSize = 36f; isAntiAlias = true; typeface = bodySans
            }.legible(), maxLines = 2)
        }
        // 右欄超大日期（月小字 + DD 特大），靠右
        val rightX = w - margin
        canvas.drawText(spec.month, rightX, margin + 200f, Paint().apply {
            color = ColorUtils.setAlphaComponent(onBg, 0xCC); textSize = 34f; isAntiAlias = true
            textAlign = Paint.Align.RIGHT; typeface = bodySansBold; letterSpacing = 0.1f
        }.legible())
        canvas.drawText(spec.day, rightX, margin + 380f, Paint().apply {
            color = accent; textSize = 150f; isAntiAlias = true
            textAlign = Paint.Align.RIGHT; typeface = displaySerif
        }.legible())
        // 左下＝資訊(blocks[0])、右下＝演出(blocks[1])
        val blockTop = h - margin - 380f
        spec.blocks.getOrNull(0)?.let { drawBlock(canvas, it, colX(0), blockTop, colW * 5, onBg, maxLines = 5) }
        spec.blocks.getOrNull(1)?.let { drawBlock(canvas, it, colX(6), blockTop, colW * 6, onBg, maxLines = 5) }
    }

    // ── BLEED 大字出血：取標題前 2–4 字放大到出血，其餘小字塊縮左下 ──
    private fun drawBleed(canvas: Canvas, spec: Spec, margin: Float, w: Int, h: Int, onBg: Int, accent: Int) {
        // 原本固定 take(4) 再配死字級：「當大人高校季」會被切成「當大人高」，
        // 看起來像壞掉而不像設計。改成先決定要放幾個字，再由可用高度回推字級。
        val all = bleedDisplayChars(spec.title)
        val chars = all.take(MAX_BLEED_CHARS).ifEmpty { "B" }
        // 直排只對中日韓文字成立。拉丁字母一個一個往下疊會變成「L/e/g/a/c/y」那種
        // 看不出是什麼的東西（實機回報「分享很跑版」），這種情況改用橫排自動縮放。
        if (chars.none { isCjk(it) }) {
            drawTitleAutoFit(
                canvas, all.ifEmpty { spec.title }, margin, h * 0.42f, w - margin * 2,
                Paint().apply { color = accent; textSize = 150f; isAntiAlias = true; typeface = displaySerif }.legible(),
                maxLines = 3
            )
            drawBleedFooter(canvas, spec, margin, w, h, onBg)
            return
        }
        // 上緣留一點給品牌行，下緣要讓出底部小字資訊塊的高度。
        val top = margin + 150f
        val bottom = h - margin - 340f
        val avail = (bottom - top).coerceAtLeast(240f)
        // 0.92 是下面每個字往下推的行距係數，算字級時要一起算進去。
        val size = min(600f, avail / (chars.length * 0.92f))
        val paint = Paint().apply { color = accent; textSize = size; isAntiAlias = true; typeface = displaySerif }.legible()
        var y = top + size
        val x = margin
        chars.forEach { ch ->
            canvas.drawText(ch.toString(), x, y, paint)
            y += size * 0.92f
        }
        drawBleedFooter(canvas, spec, margin, w, h, onBg)
    }

    /** BLEED 左下的小字資訊塊。直排與橫排兩條路都用同一份，不會長得不一樣。 */
    private fun drawBleedFooter(canvas: Canvas, spec: Spec, margin: Float, w: Int, h: Int, onBg: Int) {
        // 小字資訊塊：左下（標題 / 藝人 / 資訊第一行皆換行，不超出框線）
        val infoY = h - margin - 260f
        val titleEnd = drawTitleAutoFit(canvas, spec.title, margin, infoY, w - margin * 2, Paint().apply {
            color = onBg; textSize = 40f; isAntiAlias = true; typeface = bodySansBold
        }.legible(), maxLines = 2)
        var yy = titleEnd + 10f
        spec.subtitle?.takeIf { it.isNotBlank() }?.let {
            yy = drawWrapped(canvas, it, margin, yy, w - margin * 2, Paint().apply {
                color = ColorUtils.setAlphaComponent(onBg, 0xCC); textSize = 34f; isAntiAlias = true; typeface = bodySans
            }.legible(), maxLines = 2) + 8f
        }
        drawBlocks(canvas, spec, margin, yy, w - margin * 2, onBg, bottomLimit = h - margin - 96f, maxLinesEach = 3)
    }

    // ── VERTICAL 直排：標題直排、右側頂天立地；藝人靠左下；地點/日期在底條，不重複 ──
    private fun drawVertical(canvas: Canvas, spec: Spec, margin: Float, w: Int, h: Int, onBg: Int, accent: Int) {
        val chars = spec.title.filter { !it.isWhitespace() }.take(10)
        val size = 62f
        val paint = Paint().apply { color = accent; textSize = size; isAntiAlias = true; typeface = displaySerif; textAlign = Paint.Align.CENTER }.legible()
        val x = w - margin - 40f
        var y = margin + 240f
        chars.forEach { ch ->
            canvas.drawText(ch.toString(), x, y, paint)
            y += size * 1.15f
        }
        // 左側：藝人 + 資料塊。直排標題在右邊，左邊整條是空的，正好放資訊。
        var ly = margin + 260f
        spec.subtitle?.takeIf { it.isNotBlank() }?.let {
            ly = drawWrapped(canvas, it, margin, ly, w * 0.55f, Paint().apply {
                color = onBg; textSize = 38f; isAntiAlias = true; typeface = bodySansBold
            }.legible(), maxLines = 3) + 40f
        }
        drawBlocks(canvas, spec, margin, ly, w * 0.58f, onBg, bottomLimit = h - margin - 96f)
    }

    // ── BILINGUAL 中英對照：中文大標 + 藝人 + 資訊塊，全部換行避免超出框線 ──
    private fun drawBilingual(canvas: Canvas, spec: Spec, margin: Float, w: Int, h: Int, onBg: Int, accent: Int) {
        val titlePaint = Paint().apply { color = accent; textSize = 84f; isAntiAlias = true; typeface = displaySerif }.legible()
        val titleEndY = drawTitleAutoFit(canvas, spec.title, margin, margin + 300f, w - margin * 2, titlePaint, maxLines = 3)

        val sub = spec.subtitle?.takeIf { it.isNotBlank() } ?: "BANDARY LIVE ARCHIVE"
        val subEndY = drawWrapped(canvas, sub, margin, titleEndY + 60f, w - margin * 2, Paint().apply {
            color = ColorUtils.setAlphaComponent(onBg, 0xCC); textSize = 36f; isAntiAlias = true
            letterSpacing = 0.08f; typeface = bodySansBold
        }.legible(), maxLines = 2)

        drawBlocks(canvas, spec, margin, subEndY + 70f, w - margin * 2, onBg, bottomLimit = h - margin - 96f)
    }

    // ── MINIMAL 簡潔：大量留白、大標 + 藝人；地點/日期在底條，不重複 ──
    private fun drawMinimal(canvas: Canvas, spec: Spec, margin: Float, w: Int, h: Int, onBg: Int, accent: Int) {
        // 有資料塊時標題往上挪，讓下半部留給資訊；沒有才維持原本置中偏下的大量留白。
        val hasBlocks = spec.blocks.any { it.lines.isNotEmpty() }
        val titlePaint = Paint().apply { color = onBg; textSize = 58f; isAntiAlias = true; typeface = displaySerif }.legible()
        val titleEndY = drawTitleAutoFit(
            canvas, spec.title, margin, if (hasBlocks) h * 0.30f else h * 0.46f,
            w - margin * 2, titlePaint, maxLines = 3
        )
        var y = titleEndY + 30f
        spec.subtitle?.takeIf { it.isNotBlank() }?.let {
            y = drawWrapped(canvas, it, margin, y, w - margin * 2, Paint().apply {
                color = ColorUtils.setAlphaComponent(onBg, 0xCC); textSize = 38f; isAntiAlias = true; typeface = bodySans
            }.legible(), maxLines = 2) + 50f
        }
        drawBlocks(canvas, spec, margin, y, w - margin * 2, onBg, bottomLimit = h - margin - 96f)
    }

    /**
     * 折行：優先在「空白」斷詞（英文 / 有空格的團名），過長的單一詞（如中文無空白）再逐字斷。
     * 回傳實際折出的行（不截斷，交由呼叫端決定 maxLines）。
     */
    // 字元類別：空白 / 中日韓 / 英文字母 / 數字 / 其他。用來只在「換字型類型或空白」處斷行。
    private fun charClass(ch: Char): Int = when {
        ch == ' ' || ch == '　' -> 0
        ch.code in 0x4E00..0x9FFF || ch.code in 0x3040..0x30FF || ch.code in 0xAC00..0xD7A3 || ch.code in 0xF900..0xFAFF -> 1
        ch.isLetter() -> 2
        ch.isDigit() -> 3
        else -> 4
    }
    // 依字元類別切成 token（同類連續為一段），斷行只發生在 token 之間或空白處。
    private fun tokenizeByClass(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var cls = -1
        for (ch in text) {
            val c = charClass(ch)
            if (c != cls && sb.isNotEmpty()) { out.add(sb.toString()); sb.setLength(0) }
            sb.append(ch); cls = c
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    private fun wrapLines(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank()) return emptyList()
        val bounds = Rect()
        fun width(s: String): Int { paint.getTextBounds(s, 0, s.length, bounds); return bounds.width() }
        val out = mutableListOf<String>()
        val line = StringBuilder()
        fun pushLine() { if (line.isNotEmpty()) { out.add(line.toString()); line.setLength(0) } }
        // 最後手段：單一 token 仍超寬才逐字斷（中文正常、英文盡量靠 auto-fit 縮字避免走到這）。
        fun charWrap(word: String) {
            for (ch in word) {
                if (width(line.toString() + ch) > maxWidth && line.isNotEmpty()) { pushLine(); line.append(ch) }
                else line.append(ch)
            }
        }
        for (tok in tokenizeByClass(text)) {
            if (tok.isBlank()) {                       // 空白：斷行機會，放得下才保留、放不下就換行
                if (line.isNotEmpty()) { if (width(line.toString() + tok) <= maxWidth) line.append(tok) else pushLine() }
                continue
            }
            when {
                width(line.toString() + tok) <= maxWidth -> line.append(tok)  // 接在同一行
                width(tok) <= maxWidth -> { pushLine(); line.append(tok) }    // 換行整段放得下（＝在類型/空白處斷）
                else -> { pushLine(); charWrap(tok) }                          // 單段太長才逐字斷
            }
        }
        pushLine()
        return out
    }

    /** 依 [wrapLines] 折行後繪製（超過 maxLines 的行會被截掉），回傳畫完後的 Y。 */
    private fun drawWrapped(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint, maxLines: Int = Int.MAX_VALUE): Float {
        if (text.isBlank()) return startY
        val lines = wrapLines(text, maxWidth, paint).take(maxLines)
        val lineHeight = paint.textSize * 1.2f
        lines.forEachIndexed { i, l -> canvas.drawText(l, x, startY + i * lineHeight, paint) }
        return startY + lines.size * lineHeight
    }

    /**
     * 標題自動縮字：從 [paint] 目前字級往下縮，直到整段標題能在 maxLines 行內完整呈現（不截斷、不卡字）。
     * 空格會優先換行。回傳畫完後的 Y。
     */
    private fun drawTitleAutoFit(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint, maxLines: Int): Float {
        if (text.isBlank()) return startY
        val baseSize = paint.textSize
        val minSize = baseSize * 0.55f
        var size = baseSize
        while (size > minSize && wrapLines(text, maxWidth, paint.apply { textSize = size }).size > maxLines) {
            size -= 2f
        }
        paint.textSize = size
        return drawWrapped(canvas, text, x, startY, maxWidth, paint, maxLines)
    }
}
