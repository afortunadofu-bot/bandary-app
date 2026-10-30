// 這整支檔案就是 CropImageContract 的包裝，而那個 contract 已被套件標為棄用。
// 整檔抑制在這裡是**對應範圍的**：檔案只有這一個用途、不到一百行，
// 換掉 contract 等於重寫這支。要換是獨立一批（動到選圖與裁切的實際行為），
// 不是併進「清警告」。
@file:Suppress("DEPRECATION")

package com.bandary.presentation.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.net.Uri
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions

/**
 * 「選一張照片 → 裁切 → 拿到結果」的共用流程。
 *
 * 為什麼每個上傳點都該有裁切：
 *   - **OCR**：裁到只剩歌單／票根那一塊，雜訊直接歸零。這是辨識率最大的單一槓桿，
 *     比任何前處理都有效（實機回報辨識度差）。
 *   - **封面與頭貼**：手機照片是 4:3 或 9:16，直接塞進卡片會被裁掉重點，
 *     自己裁才知道要留哪裡。
 *   - **票根／證明單**：照片裡常常連桌面、手指一起入鏡。
 *
 * 裁切是**可跳過的**：使用者按返回就直接用原圖，不會因為不想裁而卡住流程。
 */
@Composable
fun rememberPickAndCrop(
    /** 裁切框比例；null＝自由裁切（文件、歌單用這個）。 */
    aspect: Pair<Int, Int>? = null,
    onResult: (Uri) -> Unit
): () -> Unit {
    // 使用者取消裁切時要退回原圖，所以得記住剛剛挑的是哪一張。
    var picked by remember { mutableStateOf<Uri?>(null) }
    // 裁切選項的單位是像素，要自己把 dp 換算過去（見下方說明）。
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val cropped = result.uriContent
        when {
            result.isSuccessful && cropped != null -> onResult(cropped)
            // 取消或裁切失敗：照樣用原圖。硬要人重挑一次只會讓人放棄上傳。
            else -> picked?.let(onResult)
        }
        picked = null
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        picked = uri
        cropLauncher.launch(
            CropImageContractOptions(
                uri,
                CropImageOptions().apply {
                    guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                    outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
                    outputCompressQuality = 92
                    // ── 以下幾項是為了「好按」。**上一次改錯了單位，反而更難按。**
                    //
                    // 這個套件的這些欄位單位是**像素**，不是 dp——它自己的預設值是用
                    // `TypedValue.applyDimension(COMPLEX_UNIT_DIP, …)` 換算出來的
                    //（用 javap 看 CropImageOptions 的建構子確認過）。
                    // 上一版直接寫 `touchRadius = 48f`，那是 48 **像素**：
                    // 在 3x 的手機上只有 16dp，比套件預設還小。
                    // 實機回報從「很難按」變成「手勢根本無法進行裁切」就是這麼來的。
                    //
                    // 所以這裡一律 dp × density 自己換算。
                    val dp = density

                    // 四周留一點邊：上一版設 0 讓裁切框貼齊整張圖，看起來「不用調」，
                    // 但把手就正好壓在圖片邊緣（常常也是螢幕邊緣），手指根本搆不到角落。
                    // 留 5% 的代價只是進來要拉一下，換來的是抓得到。
                    initialCropWindowPaddingRatio = 0.05f
                    // 可觸碰半徑 28dp。Material 的最小觸控目標是 48dp（半徑 24dp），
                    // 裁切的把手在角落又更難瞄，所以再放寬一點。
                    touchRadius = 28f * dp
                    // 把手畫得看得見：看不到就不知道那裡可以拉。
                    cropperLabelTextSize = 14f * dp
                    borderCornerThickness = 3f * dp
                    borderCornerLength = 20f * dp
                    // 固定比例只用在頭貼那種「一定要方的」情況；
                    // 文件類一律自由裁，強制比例反而會逼人切掉需要的部分。
                    fixAspectRatio = aspect != null
                    aspect?.let { (x, y) -> aspectRatioX = x; aspectRatioY = y }
                    // 允許旋轉：直拍橫放的票根很常見，在裁切畫面順手轉正最快。
                    allowRotation = true
                    allowFlipping = false
                    cropMenuCropButtonTitle = "完成"
                }
            )
        )
    }

    return { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
