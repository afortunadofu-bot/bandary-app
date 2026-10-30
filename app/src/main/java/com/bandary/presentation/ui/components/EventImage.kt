package com.bandary.presentation.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 顯示免費活動圖：[data] 可能是 Base64 字串（新版本機壓縮後存 Firestore），
 * 或是舊資料的 http(s) 下載網址。自動判斷來源。
 *
 * 兩個原本會讓畫面「一片空白」的問題（實機回報「網路很爛時圖片看不到」）：
 *
 * 1. **失敗就什麼都不畫**。Base64 解不出來、或網路圖載不到時，整塊是空的——
 *    使用者只看到一個洞，不知道是還在載、載壞了、還是本來就沒圖。
 *    現在一律畫出狀態：載入中給骨架，失敗給「載入失敗・點一下重試」。
 * 2. **Base64 在主執行緒解碼**。一張圖的字串接近 900KB，在 `remember` 裡同步解
 *    等於在組合期間卡住畫面；清單一次滾過好幾張就是明顯的頓。
 *    改用 `produceState` 丟到 Default 執行緒。
 */
@Composable
fun EventImage(
    data: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    // 重試計數：改變它就會讓 produceState / Coil 重新跑一次。
    var attempt by rememberSaveable(data) { mutableIntStateOf(0) }

    if (data.startsWith("http", ignoreCase = true)) {
        // SubcomposeAsyncImage 才拿得到 loading / error 狀態；AsyncImage 只能給靜態的佔位圖。
        SubcomposeAsyncImage(
            model = remember(data, attempt) { data },
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> ImageSkeleton(Modifier.fillMaxSize())
                is AsyncImagePainter.State.Error -> ImageFailed(
                    modifier = Modifier.fillMaxSize(),
                    onRetry = { attempt++ }
                )
                else -> SubcomposeAsyncImageContent()
            }
        }
        return
    }

    // Base64：解碼放到背景執行緒。null = 還在解，Result.failure = 解壞了。
    val bitmap by produceState<Result<ImageBitmap>?>(null, data, attempt) {
        value = null
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(data, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    ?: error("decode failed")
            }
        }
    }

    val result = bitmap
    when {
        result == null -> ImageSkeleton(modifier)
        result.isSuccess -> Image(
            bitmap = result.getOrThrow(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
        )
        // 這裡的失敗多半是資料本身壞掉（截斷的 Base64），重試不一定有用，
        // 但給一個按鈕總比一塊空白好——至少使用者知道「不是我的手機壞了」。
        else -> ImageFailed(modifier = modifier, onRetry = { attempt++ })
    }
}

/** 載入中的骨架。用底色方塊而不是轉圈圈：清單裡十幾個轉圈圈會很吵。 */
@Composable
private fun ImageSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImageFailed(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onRetry() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                Icons.Outlined.BrokenImage, null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "載入失敗・點一下重試",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
