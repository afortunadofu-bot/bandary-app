package com.bandary.data.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.unit.Density
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

/**
 * Saves a bitmap to MediaStore Pictures folder and returns content URI.
 * Then calls a system share-sheet so the user can post to IG / Twitter / etc.
 */
object BitmapExporter {

    @Deprecated("分享與下載已拆開，改用 shareOnly() / downloadToGallery()")
    fun saveAndShare(
        context: Context,
        bitmap: Bitmap,
        title: String = "bandary_ticket"
    ): Uri? {
        val uri = saveToGallery(context, bitmap, title)
        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "分享票根").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
        return uri
    }

    /** 只開系統分享單，不寫入本機相簿——分享跟下載是兩個獨立動作。 */
    fun shareOnly(context: Context, bitmap: Bitmap, title: String = "bandary_share") {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, "${title}_${System.currentTimeMillis()}.png")
        runCatching {
            file.outputStream().use { os -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, os) }
        }.onFailure {
            android.util.Log.e("BitmapExporter", "shareOnly write failed", it)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "分享").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        )
    }

    /** 手動下載鍵：明確存進系統相簿 Pictures/Bandary，回傳存檔位置的 Uri。 */
    fun downloadToGallery(context: Context, bitmap: Bitmap, title: String = "bandary_share"): Uri? =
        saveToGallery(context, bitmap, title)

    /** 把已經算好的圖（如票根匯出留在 cache 的 FileProvider Uri）另存一份進系統相簿，不影響原本的分享流程。 */
    fun downloadUriToGallery(context: Context, source: Uri, filename: String): Uri? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(source)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Bandary")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) } }
                uri
            } else {
                val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Bandary")
                    .apply { mkdirs() }
                val file = File(outDir, filename)
                file.writeBytes(bytes)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        } catch (e: Exception) {
            android.util.Log.e("BitmapExporter", "downloadUriToGallery failed", e)
            null
        }
    }

    private fun saveToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
        val filename = "${title}_${System.currentTimeMillis()}.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Bandary"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                }
                uri
            } else {
                // Pre-Q fallback: write to cache and use FileProvider
                val cacheFile = File(context.cacheDir, filename)
                cacheFile.outputStream().use { os: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
                FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", cacheFile
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BitmapExporter", "save failed", e)
            null
        }
    }

    /**
     * Renders an Android View into a Bitmap of the given dimensions.
     */
    fun renderViewToBitmap(view: android.view.View, width: Int, height: Int): Bitmap {
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}
