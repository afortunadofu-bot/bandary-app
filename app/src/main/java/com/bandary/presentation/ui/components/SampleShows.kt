package com.bandary.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import com.bandary.domain.model.TicketStatus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.LocalDate

/**
 * 全 App 共用的「範例演出」。第一次打開時畫面是空的，光靠文字說明不曉得記下來會長什麼樣子，
 * 所以直接用示意資料把真正的卡片畫出來。
 *
 * 用大家認得出來的藝人與真實場館，不再用「範例樂團 / 示意海灘園區」——
 * 看不出那是什麼的假資料，等於沒有示範到「記下來之後會長什麼樣子」。
 * 場次本身是虛構的（沒有這幾場演出），所以每張卡片都會被半透明處理並掛上「範例」標籤。
 *
 * 日期以「今天」為基準往後推，這樣不管哪一天打開，範例都還是「即將到來」而不是過期的場次。
 */
fun sampleConcerts(today: LocalDate): List<Concert> = listOf(
    Concert(
        id = "sample-1",
        name = "五月天 好好好想見到你",
        artistName = "五月天",
        venue = "台北小巨蛋",
        city = "臺北",
        dateTime = LocalDateTime(today.plusDaysSafe(6), LocalTime(19, 30)),
        type = ConcertType.CONCERT,
        ticketStatus = TicketStatus.BOUGHT,
        ticketPrice = 3200.0,
        ticketPlatform = "拓元 tixCraft",
        seatInfo = "2 樓 C 區 14 排 08 號"
    ),
    Concert(
        id = "sample-2",
        name = "TWICE READY TO BE 世界巡迴",
        artistName = "TWICE",
        venue = "高雄國家體育場",
        city = "高雄",
        dateTime = LocalDateTime(today.plusDaysSafe(21), LocalTime(18, 30)),
        type = ConcertType.CONCERT,
        ticketStatus = TicketStatus.NONE,
        ticketPrice = 5800.0,
        ticketPlatform = "寬宏售票"
    ),
    Concert(
        id = "sample-3",
        name = "告五人 唱唱唱巡迴・台中場",
        artistName = "告五人",
        venue = "Legacy Taichung",
        city = "臺中",
        dateTime = LocalDateTime(today.plusDaysSafe(45), LocalTime(20, 0)),
        type = ConcertType.CONCERT,
        ticketStatus = TicketStatus.NONE,
        ticketPrice = 1200.0,
        ticketPlatform = "KKTIX"
    )
)

/**
 * 範例卡片的外框：整塊調暗＋降透明度。
 *
 * 實機回報「第一眼看不出來是範例」——光靠上方一行「範例」標籤，
 * 卡片本身跟真實資料長得一模一樣，很容易以為自己的資料被灌了奇怪的東西。
 * 半透明是最直接的訊號：看起來就是「還沒有東西，這是示意」。
 *
 * 用 alpha 而不是灰階，是因為卡片底圖是照片，去飽和後反而更像正常的黑白照。
 */
@Composable
fun SampleContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.alpha(SAMPLE_ALPHA)
    ) { content() }
}

/** 範例的透明度。夠淡才看得出是示意，又不能淡到看不清內容。 */
const val SAMPLE_ALPHA = 0.55f

/** kotlinx-datetime 的 LocalDate 沒有 plusDays；用 epoch 天數加減最單純。 */
private fun LocalDate.plusDaysSafe(days: Int): LocalDate =
    LocalDate.fromEpochDays(this.toEpochDays() + days)

/**
 * 範例區塊的標題。統一寫法，讓使用者一眼知道「這不是我的資料」。
 */
@Composable
fun SampleBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Outlined.AutoAwesome, contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                "範例",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
