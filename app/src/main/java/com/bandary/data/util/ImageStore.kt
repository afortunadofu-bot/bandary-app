package com.bandary.data.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 把使用者從系統相簿選的圖片複製一份進 App 私有儲存空間（filesDir/images），
 * 回傳 file:// URI。
 *
 * 為什麼需要這層拷貝：直接存 content:// picker URI 會在兩種情況下失效——
 * (1) 未呼叫 takePersistableUriPermission 時，App process 結束後權限被系統收回；
 * (2) 使用者事後在相簿 App 搬移/刪除原圖，即使權限還在，URI 指向的檔案也不存在了。
 * 複製一份到 App 專屬目錄後，圖片的生命週期完全由 App 自己掌控，不受這兩種情況影響。
 */
object ImageStore {

    private fun imagesDir(context: Context): File =
        File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    /** 複製單張圖片；失敗（例如來源已不存在）時回傳 null，呼叫端應保留原值不覆蓋。 */
    fun persist(context: Context, uri: Uri): String? {
        return runCatching {
            val ext = context.contentResolver.getType(uri)?.let { mime ->
                when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    else -> "jpg"
                }
            } ?: "jpg"
            val dest = File(imagesDir(context), "${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Uri.fromFile(dest).toString()
        }.getOrNull()
    }

    /**
     * 依照片的 EXIF 方向把 bitmap 轉正。
     *
     * 為什麼需要：手機直拍的 JPEG 多半是「橫著存、靠 EXIF 標記告訴看圖程式要轉 90 度」。
     * `BitmapFactory.decodeStream` **不看 EXIF**，所以解出來就是躺著的。
     * 顯示端（Coil）自己會讀 EXIF 所以看起來正常，但只要經過「解碼 → 重新壓成 JPEG」
     * 這條路（雲端備份、活動投稿），EXIF 就掉了、圖也就真的躺著存進雲端——
     * 這就是實機回報「照片傳上去會顛倒」的原因。
     *
     * 讀不到 EXIF（例如 PNG、或來源不給再開一次 stream）就原樣回傳，不要硬轉。
     */
    fun applyExifRotation(context: Context, uri: Uri, bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                androidx.exifinterface.media.ExifInterface(input)
                    .getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )
            }
        }.getOrNull() ?: return bitmap

        val matrix = android.graphics.Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            // 鏡像的幾種：自拍相機常見。只處理翻轉，不另外轉角度的那兩種也一起帶到。
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap   // ORIENTATION_NORMAL / UNDEFINED：不用動
        }
        return runCatching {
            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    /**
     * 把一段圖片位元組存成本機檔，回傳 `file://` URI。
     *
     * 給「網路找到的海報」與「活動資料庫的 Base64 圖」用：這兩種來源都**不能**直接把
     * 網址或 Base64 字串塞進 `photoUris`——
     *   - 遠端網址：對方哪天把圖撤掉就變破圖，而且離線時整張不見。
     *   - Base64：一張近 900KB 的字串塞進本機資料庫，列表一滾就卡。
     * 一律落地成檔案之後，它跟使用者自己拍的照片走同一條路：同樣進相簿、同樣能備份。
     */
    fun persistBytes(context: Context, bytes: ByteArray, ext: String = "jpg"): String? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val dest = File(imagesDir(context), "${UUID.randomUUID()}.$ext")
            dest.outputStream().use { it.write(bytes) }
            Uri.fromFile(dest).toString()
        }.getOrNull()
    }

    /**
     * 壓縮成適合存進 Firestore 的 Base64 字串。
     *
     * 原本只有活動投稿那條路有這段（`FreeEventsRepository.encodeImage`），
     * 現在場館視野圖也要用，所以抽到這裡——兩份各自演化的壓縮參數
     * 會讓同一個雲端集合裡出現大小差十倍的圖。
     *
     * 參數的理由：
     *   - **長邊 800**：Firestore 單一文件上限 1MB，而這些圖是列表裡的縮圖尺寸，
     *     再大也看不出差別，只是把文件撐爆。
     *   - **JPEG 60**：照片類在 60 以下才會開始看得出壓縮痕跡。
     *   - **先轉正**：`BitmapFactory` 不看 EXIF，不轉的話傳上去會是躺著的
     *     （實機回報過「照片傳上去會顛倒」）。
     */
    fun encodeForUpload(context: Context, uri: Uri, maxDim: Int = 800, quality: Int = 60): String? =
        runCatching {
            val decoded = context.contentResolver.openInputStream(uri).use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            } ?: return null
            val bitmap = applyExifRotation(context, uri, decoded)
            if (bitmap !== decoded) decoded.recycle()
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = if (scale < 1f)
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
                )
            else bitmap
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, baos)
            android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
        }.getOrNull()

    /**
     * 照片的拍攝時間（epoch millis）；讀不到回 null。
     *
     * 為什麼需要：原本的需求是「選照片時自動滑到那天的相簿」，
     * 但 Android 的系統相簿選取器（`PickVisualMedia`）**沒有任何依日期跳轉的參數**，
     * 要做只能自己寫相簿瀏覽器，那需要 `READ_MEDIA_IMAGES` 全相簿權限
     * ——一個演唱會紀錄 App 要全相簿權限，在 Play 審核是要寫核心功能聲明去賭的。
     *
     * 所以改成從另一端解決同一個痛點：選完之後讀 EXIF，
     * 照拍攝時間排好、並標出「這張不是那天拍的」。
     * 找照片還是得自己捲，但**挑錯了會被抓出來**，而那才是真正會造成損失的部分。
     */
    fun capturedAt(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = androidx.exifinterface.media.ExifInterface(input)
            val raw = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)
                ?: return null
            // EXIF 的格式固定是 "yyyy:MM:dd HH:mm:ss"，而且是**拍攝當下的本地時間**、
            // 不帶時區。用裝置目前時區解讀是唯一能做的近似。
            java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                .parse(raw)?.time
        }
    }.getOrNull()

    /** 已經是本機 file:// URI（先前已 persist 過）就不用重複複製。 */
    fun isLocal(value: String?): Boolean = value != null && value.startsWith("file://")

    /**
     * 刪掉本機的一份複製檔。只在確定這個 URI 已經沒有任何演唱會記錄引用時才呼叫
     * （見 AddShowViewModel.saveConcert 的舊/新照片清單差集），避免刪到還在用的照片。
     * 檔案不存在或不是本機路徑都當作沒事，不拋例外。
     */
    fun delete(context: Context, value: String?) {
        if (!isLocal(value)) return
        runCatching { Uri.parse(value)?.path?.let { File(it) } }.getOrNull()
            ?.takeIf { it.exists() && it.parentFile == imagesDir(context) }
            ?.delete()
    }
}
