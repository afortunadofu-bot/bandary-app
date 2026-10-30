package com.bandary.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 圓角尺度。**新程式碼請從這裡挑，不要再自己寫數字。**
 *
 * ## 為什麼需要
 * 盤點過一次，全專案出現過 **13 種不同的圓角值**：
 * `0 / 2 / 3 / 4 / 5 / 6 / 8 / 10 / 11 / 12 / 14 / 16 / 20`。
 * 那不是設計系統，是每個地方各寫各的——其中還有明顯是手滑的 `11.dp`（只出現一次）。
 *
 * 後果不是「某一頁醜」，而是**整個 App 看起來不像同一個人做的**：
 * 統計頁的卡片是 2dp 近乎直角、活動頁是 12dp 圓潤，同一個使用者在兩頁之間切換，
 * 感覺到的是「質感不一致」，但他說不出是哪裡不對（實機回報只會是一句「不好看」）。
 *
 * ## 四階就夠
 * 階數多不會更精緻，只會更難維持一致。每一階有明確的用途：
 */
object Radius {
    /** 小元件：色塊、進度條、徽章、標籤。 */
    val Small = RoundedCornerShape(6.dp)

    /** 可點的控制項：晶片、按鈕、輸入框。 */
    val Control = RoundedCornerShape(10.dp)

    /** 卡片與面板。**這是預設值**——不確定用哪個就用這個。 */
    val Card = RoundedCornerShape(12.dp)

    /** 大面積的容器：底部面板、強調卡、全螢幕覆蓋層。 */
    val Large = RoundedCornerShape(16.dp)
}

/**
 * 卡片的陰影。
 *
 * 專案裡 39 處用 `1.dp`、29 處用 `0.dp`——同一個清單裡有的浮起有的平貼。
 * **預設用平的**：這個 App 的卡片大多是「一整段資料」而不是「可以拖動的物件」，
 * 陰影在這裡不提供任何資訊，只是讓畫面變吵。
 * 真的需要表達層級時（例如浮在內容之上的東西）才給陰影。
 */
object Elevation {
    val Flat = 0.dp
    val Raised = 1.dp
}
