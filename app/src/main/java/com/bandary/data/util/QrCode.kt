package com.bandary.data.util

import android.graphics.Bitmap
import android.graphics.Color

/**
 * QR code 產生。用 zxing 的 QRCodeWriter（純 Java、離線、沒有 Android 相依）。
 *
 * 刻意不引入 zxing 的 android-embedded：那包會帶一整個掃描用的 Activity 與相機權限，
 * 而這裡只需要「把一段文字畫成方塊」。
 */
object QrCode {

    /**
     * [content] → 正方形 QR 點陣圖，邊長 [sizePx]。
     *
     * 容錯等級用 M（約 15%）：票根上的 QR 會被印出來、被拍照、被截圖再轉傳，
     * L 級在那種情況下常常掃不出來；H 級則會讓同樣尺寸下的點變得太密。
     *
     * 失敗回 null（內容太長、尺寸太小）——呼叫端該把 QR 藏起來，不要畫一塊黑的上去。
     */
    fun generate(
        content: String,
        sizePx: Int,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null
        return runCatching {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.ERROR_CORRECTION
                    to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
                // 預設的 4 格 quiet zone 在小尺寸時會吃掉超過三分之一的面積。
                // 縮到 1：掃描器實務上仍讀得到，而且點會大很多。
                com.google.zxing.EncodeHintType.MARGIN to 1
            )
            val matrix = com.google.zxing.qrcode.QRCodeWriter()
                .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    pixels[offset + x] = if (matrix[x, y]) foreground else background
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, w, 0, 0, w, h)
            }
        }.getOrNull()
    }
}
