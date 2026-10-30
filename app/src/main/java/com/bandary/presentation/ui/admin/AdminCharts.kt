package com.bandary.presentation.ui.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandary.domain.model.KpiStatus
import com.bandary.domain.model.RatioKpi
import com.bandary.domain.model.FunnelStep
import com.bandary.domain.model.formatPercent
import kotlin.math.abs
import kotlin.math.max

/**
 * 後台圖表組。
 *
 * ## 選圖的原則
 * 每種資料形狀只有一兩種圖是對的，其他都是在裝飾：
 *
 * | 資料形狀 | 用什麼 | 為什麼不用別的 |
 * |---|---|---|
 * | 一條流程逐層流失 | [FunnelChart] | 長條圖看得到高低，但看不到「上一層掉了幾成」 |
 * | 時間序列 | [TrendChart] 折線 | 時間是連續的；用長條會暗示每期彼此獨立 |
 * | 構成比例（2～5 類） | [DonutChart] | 類別一多就讀不出角度差，所以限 5 類 |
 * | 排行（名稱長、項目多） | [RankBarList] 橫條 | 直條放不下中文團名，標籤會轉 45 度變難讀 |
 * | 週期性分布（星期） | [CycleBarChart] 直條 | 星期有固定順序，不能照大小排 |
 * | 單一比率對目標 | [KpiGauge] | 一個數字配一條參考線，比畫成圖表清楚 |
 *
 * ## 顏色
 * 一律取自主題，不寫死色票——使用者可以換主題色，後台跟著走才不會變成另一個 App。
 * 好壞只用在「有目標值」的地方；沒有目標的數字不上紅綠，避免暗示一個不存在的判斷。
 */

/** 卡片外框。整頁的圖表共用同一種容器，視覺才不會每張卡都在吵。 */
@Composable
fun ChartCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

/** 沒有資料時的佔位。空白一片會讓人以為是載入中或壞掉。 */
@Composable
fun ChartEmpty(text: String = "還沒有資料") {
    Box(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── 漏斗 ─────────────────────────────────────────────────────

/**
 * 行銷漏斗。每一層畫成一條寬度正比於人數的長條，右邊標出
 * **相對上一層**的轉換率——那才是能拿來行動的數字。
 *
 * 只標「佔最上層幾 %」會看不出瓶頸在哪一層：
 * 100 → 90 → 12 → 11，整體轉換率一路遞減看起來很平順，
 * 但真正的問題是第二層到第三層掉了 87%。
 */
@Composable
fun FunnelChart(steps: List<FunnelStep>) {
    if (steps.isEmpty()) { ChartEmpty(); return }
    val top = steps.first().count
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, step ->
            val prev = if (i == 0) step.count else steps[i - 1].count
            val widthFrac = if (top <= 0) 0f else (step.count.toFloat() / top).coerceIn(0.04f, 1f)
            // 愈往下愈深：顏色本身就在表達「愈後面愈少、愈珍貴」。
            val alpha = 0.35f + 0.13f * i
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        step.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${step.count}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (i > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatPercent(step.stepRate(prev)),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(widthFrac)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.primary.copy(alpha = alpha.coerceAtMost(0.95f)))
                    )
                }
                if (step.hint.isNotBlank()) {
                    Text(
                        step.hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── 時間序列 ──────────────────────────────────────────────────

/**
 * 折線趨勢圖（含面積）。時間是連續的，折線才讀得出走勢；
 * 用長條會暗示每一期彼此獨立。
 *
 * 只標頭、尾與最高點三個標籤：十二個月全標上去會疊成一團，
 * 而看趨勢需要的本來就只有「從哪裡來、到哪裡去、最高到過哪」。
 */
@Composable
fun TrendChart(
    data: List<Pair<String, Int>>,
    height: androidx.compose.ui.unit.Dp = 140.dp
) {
    if (data.size < 2) { ChartEmpty("至少要兩期才看得出趨勢"); return }
    val scheme = MaterialTheme.colorScheme
    val line = scheme.primary
    val maxV = max(1, data.maxOf { it.second })
    val peakIdx = data.indexOfFirst { it.second == maxV }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val stepX = if (data.size <= 1) w else w / (data.size - 1)
            fun pt(i: Int): Offset {
                val v = data[i].second.toFloat() / maxV
                return Offset(i * stepX, h - v * (h - 8f) - 4f)
            }
            // 基準格線：沒有刻度的折線看不出幅度，但格線太多會喧賓奪主，所以只畫三條。
            for (g in 0..2) {
                val y = h * g / 2f
                drawLine(
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }
            val path = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1 until data.size) lineTo(pt(i).x, pt(i).y)
            }
            // 面積：讓「量」有體積感，單一條細線在深色底下很容易看漏。
            val area = Path().apply {
                addPath(path)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = area,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(line.copy(alpha = 0.22f), Color.Transparent)
                )
            )
            drawPath(path = path, color = line, style = Stroke(width = 3f))
            // 只在最後一點畫實心圓：那是「現在在哪」，是讀者第一個想找的點。
            val last = pt(data.size - 1)
            drawCircle(color = line, radius = 5f, center = last)
            drawCircle(color = scheme.surface, radius = 2.5f, center = last)
            if (peakIdx in data.indices && peakIdx != data.size - 1) {
                drawCircle(color = line.copy(alpha = 0.5f), radius = 3.5f, center = pt(peakIdx))
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                data.first().first,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                "最高 $maxV",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                data.last().first + " · " + data.last().second,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
        }
    }
}

// ── 構成比例 ──────────────────────────────────────────────────

/**
 * 甜甜圈圖。**最多五類**——再多就讀不出角度差，那時候橫條圖才是對的工具。
 * 中間放總數：構成圖最常被問的第一個問題就是「總共多少」。
 */
@Composable
fun DonutChart(
    slices: List<Pair<String, Int>>,
    centerLabel: String = "總計"
) {
    val data = slices.filter { it.second > 0 }.take(5)
    val total = data.sumOf { it.second }
    if (total <= 0) { ChartEmpty(); return }
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primary,
        scheme.tertiary,
        scheme.secondary,
        scheme.primary.copy(alpha = 0.45f),
        scheme.tertiary.copy(alpha = 0.4f)
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(104.dp)) {
                var start = -90f
                val stroke = size.minDimension * 0.22f
                data.forEachIndexed { i, (_, v) ->
                    val sweep = 360f * v / total
                    drawArc(
                        color = palette[i % palette.size],
                        startAngle = start,
                        sweepAngle = sweep - 1.5f,   // 留一點縫，相鄰色塊才分得開
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
            data.forEachIndexed { i, (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(9.dp).clip(CircleShape)
                            .background(palette[i % palette.size])
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatPercent(v.toDouble() / total),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── 排行 ─────────────────────────────────────────────────────

/**
 * 橫條排行。中文團名、場館名都很長，直條圖的標籤會被迫轉 45 度或截斷；
 * 橫條讓名稱有一整行可以放。
 */
@Composable
fun RankBarList(
    items: List<Pair<String, Int>>,
    limit: Int = 10,
    valueSuffix: String = ""
) {
    val data = items.take(limit)
    if (data.isEmpty()) { ChartEmpty(); return }
    val maxV = max(1, data.maxOf { it.second })
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        data.forEachIndexed { i, (label, v) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.width(18.dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$v$valueSuffix",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(start = 18.dp)
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((v.toFloat() / maxV).coerceIn(0.02f, 1f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(scheme.primary.copy(alpha = 0.75f))
                    )
                }
            }
        }
    }
}

// ── 週期性分布 ────────────────────────────────────────────────

/**
 * 直條圖，給**順序固定**的週期性資料（星期、月份）用。
 * 這種資料不能照大小排序——「週三最多」只有在週一到週日排好時才讀得出來。
 */
@Composable
fun CycleBarChart(data: List<Pair<String, Int>>) {
    if (data.isEmpty()) { ChartEmpty(); return }
    val maxV = max(1, data.maxOf { it.second })
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { (label, v) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    "$v",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 最高的那根上 primary，其他淡一階：一眼看出尖峰在哪。
                        .height((70f * v / maxV).coerceAtLeast(2f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (v == maxV) scheme.primary
                            else scheme.primary.copy(alpha = 0.35f)
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── 單一比率 ──────────────────────────────────────────────────

/**
 * 一條比率 + 目標參考線。
 *
 * 沒有目標值的數字不上色——紅綠會讓人以為系統對這個數字有判斷，
 * 但「有圖比例 62%」本身沒有好壞，硬塗成紅色只是製造焦慮。
 */
@Composable
fun KpiGauge(kpi: RatioKpi) {
    val scheme = MaterialTheme.colorScheme
    val color = when (kpi.status()) {
        KpiStatus.GOOD -> scheme.primary
        KpiStatus.WARN -> Color(0xFFC79A2E)
        KpiStatus.BAD -> scheme.error
        KpiStatus.NEUTRAL -> scheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                kpi.label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                kpi.percentText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(kpi.value.toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.8f))
            )
            // 目標線畫在條子上，而不是另外寫一行字——要比較的兩個東西要並排。
            kpi.target?.let { t ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(t.toFloat().coerceIn(0f, 1f))
                        .height(8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(12.dp)
                            .background(scheme.onSurface.copy(alpha = 0.55f))
                    )
                }
            }
        }
        Text(
            kpi.caption + (kpi.target?.let { " · 目標 " + formatPercent(it) } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
    }
}

/** 一個大數字 + 說明 + 可選的環比。用在「這個數字本身就是結論」的地方。 */
@Composable
fun BigMetric(
    value: String,
    label: String,
    delta: Double? = null,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            delta?.let { d ->
                Spacer(Modifier.width(6.dp))
                // 成長用主色、衰退用錯誤色，並且一定帶正負號——
                // 只寫「12%」沒人知道是漲還是跌。
                Text(
                    (if (d >= 0) "▲ " else "▼ ") + formatPercent(abs(d)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (d >= 0) scheme.primary else scheme.error,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}
