package com.bandary.presentation.ui.admin

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bandary.data.remote.FirestoreService
import com.bandary.domain.model.FunnelStep
import com.bandary.domain.model.RatioKpi
import com.bandary.domain.model.concentration
import com.bandary.domain.model.formatPercent
import com.bandary.domain.model.momGrowth
import com.bandary.domain.model.trendSlope

/**
 * 後台的「數據分析」頁。
 *
 * 這一頁的設計原則是：**每一張圖都要能回答一個會改變行動的問題**。
 * 看起來厲害但看完不知道要做什麼的圖表一律不放。
 *
 * | 區塊 | 回答什麼問題 | 看完可以做什麼 |
 * |---|---|---|
 * | 北極星 | 現在有多少人在用、在成長還是在縮 | 決定要衝成長還是衝留存 |
 * | 轉換漏斗 | 人卡在哪一層 | 那一層就是這個月要改的地方 |
 * | 關鍵比率 | 相對目標，哪幾項落後 | 排優先順序 |
 * | 活躍與回訪 | 來了之後還會不會再來 | 判斷是獲取問題還是產品問題 |
 * | 內容供給 | 資料庫長得夠不夠快、夠不夠健康 | 決定要不要加爬蟲來源 |
 * | 集中度 | 會不會只靠少數幾個人撐著 | 風險預警 |
 *
 * ## 分母一致
 * 全站彙整一次最多掃 300 位使用者。漏斗與所有比率**一律用掃到的那批人當分母**，
 * 不跟「使用者總數」混用——分子抽樣、分母全體，算出來的轉換率會莫名其妙地低。
 * 畫面上也會把抽樣數寫出來。
 */
@Composable
fun AdminAnalyticsSection(
    users: FirestoreService.UserSummary,
    guests: FirestoreService.GuestSummary,
    g: FirestoreService.GlobalStats?,
    stats: AdminStats,
    pendingVenueViews: Int = 0
) {
    if (g == null) {
        ChartCard("數據分析", "需要先載入全平台彙整") {
            ChartEmpty("按右上角重新整理，或確認這個帳號在 admins/{uid} 有文件")
        }
        return
    }
    val scanned = g.scannedUsers.coerceAtLeast(1)
    val sampleNote = if (users.total > g.scannedUsers && users.total > 0)
        "抽樣 ${g.scannedUsers} 位（全站 ${users.total} 位）" else "全部 ${g.scannedUsers} 位"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── 現在該做什麼 ───────────────────────────────────────
        // 儀表板告訴你「現在長什麼樣」，但不告訴你「該做什麼」。
        // 「沉睡率 62%」是一個事實不是一個行動；把每個數字跟目標比一遍
        // 這件事應該由程式做，不是每次都讓管理員在腦裡算。
        //
        // 關鍵：**沒事的時候這張卡要消失**。不然它就變成另一個
        // 「為了放而放」的區塊，而那正是要避免的東西。
        val alerts = com.bandary.domain.model.AdminAlerts.of(
            pendingEvents = stats.pending,
            pendingVenueViews = pendingVenueViews,
            upcomingEvents = stats.upcoming,
            totalEvents = stats.total,
            withImage = stats.withImage,
            contributorTop3Share = concentration(stats.contributorRanking, 3),
            scannedUsers = g.scannedUsers,
            active30d = g.active30d,
            proUsers = g.proUsers,
            usersWithConcerts = g.usersWithConcerts
        )
        if (alerts.isNotEmpty()) {
            val urgent = alerts.count { it.level == com.bandary.domain.model.AdminAlerts.Level.URGENT }
            ChartCard(
                "現在該做什麼",
                if (urgent > 0) "$urgent 件要先處理，共 ${alerts.size} 件"
                else "${alerts.size} 件值得注意"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    alerts.forEach { a ->
                        val isUrgent =
                            a.level == com.bandary.domain.model.AdminAlerts.Level.URGENT
                        Row {
                            // 左側色條分級：急件用錯誤色，注意用主色。
                            // 不用圖示是因為一排圖示會讓這張卡看起來像錯誤列表，
                            // 但多數情況它只是提醒。
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (isUrgent) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    a.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUrgent) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    a.action,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 全部正常時也要說一聲——不然管理員不知道是「沒事」還是「還沒算完」。
            ChartCard("現在該做什麼", "沒有需要處理的事") {
                Text(
                    "待審、內容量、活躍與轉換都在目標內。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── 北極星 ─────────────────────────────────────────────
        // 一頁的最上面只放「如果只能看三個數字，看哪三個」。
        ChartCard("北極星指標", sampleNote) {
            Row(modifier = Modifier.fillMaxWidth()) {
                BigMetric(
                    value = "${g.activeToday}",
                    label = "今日活躍",
                    modifier = Modifier.weight(1f)
                )
                BigMetric(
                    value = "${g.totalConcerts}",
                    label = "累計演出紀錄",
                    delta = momGrowth(g.monthlyConcertTrend),
                    modifier = Modifier.weight(1f)
                )
                BigMetric(
                    value = "${g.totalSongs}",
                    label = "累計選歌",
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // 黏著度（DAU/MAU）是產品健康度最常用的單一指標：
            // 20% 代表平均每位月活使用者每月來 6 天，一般消費型 App 到 20% 就算很好。
            val stickiness = if (g.active30d > 0) g.activeToday.toDouble() / g.active30d else 0.0
            KpiGauge(
                RatioKpi(
                    label = "黏著度 DAU / MAU",
                    value = stickiness,
                    caption = "每位月活使用者平均每月來 ${Math.round(stickiness * 30)} 天",
                    target = 0.20
                )
            )
        }

        // ── 訪客 vs 會員 ───────────────────────────────────────
        // 訪客是「裝了在用但沒登入」的人。沒有這一層，漏斗最上層就只有登入者，
        // 「註冊轉換率」永遠是 100%——那是整個漏斗最沒用的一種算法。
        if (guests.total > 0) {
            ChartCard(
                "訪客與會員",
                "訪客＝裝了但還沒登入。算的是**安裝**不是人（重裝會變成新的一筆）"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row {
                        BigMetric("${guests.total}", "訪客安裝", modifier = Modifier.weight(1f))
                        BigMetric("${guests.active30d}", "訪客 30 日活躍", modifier = Modifier.weight(1f))
                        BigMetric("${users.total}", "已登入帳號", modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KpiGauge(
                        RatioKpi(
                            label = "註冊轉換率（訪客 → 登入）",
                            value = if (guests.total + users.total > 0)
                                users.total.toDouble() / (guests.total + users.total) else 0.0,
                            caption = "沒登入就沒有雲端備份，換手機資料會不見——這是最值得推的一步",
                            target = 0.35
                        )
                    )
                }
            }
        }

        // ── 漏斗 ──────────────────────────────────────────────
        // 這是整頁最重要的一張。不是看「有多少人」，是看「人在哪一層掉光」。
        ChartCard(
            "使用者轉換漏斗",
            "以目前狀態計算，不是隨時間追蹤（需要 firstSeenAt 累積後才做得到世代分析）"
        ) {
            FunnelChart(
                listOfNotNull(
                    // 有訪客數字時才放最上面這一層：沒有的話它會等於下一層，
                    // 畫出一條 100% 的假轉換率。
                    if (guests.total > 0)
                        FunnelStep("安裝（含訪客）", guests.total + users.total, "訪客安裝 + 已登入帳號")
                    else null,
                    FunnelStep("有帳號", g.scannedUsers, "登入過 Google 的帳號"),
                    FunnelStep("30 天內活躍", g.active30d, "近 30 天開過 App"),
                    FunnelStep("記過至少一場", g.usersWithConcerts, "啟用：真的用了核心功能"),
                    FunnelStep("記過 10 場以上", g.powerUsers, "養成習慣的重度使用者"),
                    FunnelStep("Pro 訂閱", g.proUsers, "付費轉換")
                )
            )
        }

        // ── 關鍵比率 ───────────────────────────────────────────
        ChartCard("關鍵轉換率", "有目標值的才上色；沒有目標的數字不做好壞判斷") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiGauge(
                    RatioKpi(
                        label = "啟用率（有帳號 → 記過一場）",
                        value = g.usersWithConcerts.toDouble() / scanned,
                        caption = "註冊之後真的用起來的比例",
                        target = 0.40
                    )
                )
                KpiGauge(
                    RatioKpi(
                        label = "習慣養成率（記過一場 → 記滿 10 場）",
                        value = if (g.usersWithConcerts > 0)
                            g.powerUsers.toDouble() / g.usersWithConcerts else 0.0,
                        caption = "用起來之後留下來的比例",
                        target = 0.25
                    )
                )
                KpiGauge(
                    RatioKpi(
                        label = "付費轉換率",
                        value = g.proUsers.toDouble() / scanned,
                        caption = "${g.proUsers} 位已解鎖 Pro",
                        target = 0.05
                    )
                )
                KpiGauge(
                    RatioKpi(
                        label = "選歌功能採用率",
                        value = g.usersWithSongs.toDouble() / scanned,
                        caption = "有記過每日選歌的比例",
                        target = 0.30
                    )
                )
                KpiGauge(
                    RatioKpi(
                        // 沉睡是「愈低愈好」，所以顏色要反過來判斷。
                        label = "沉睡率（30 天沒回來）",
                        value = 1.0 - g.active30d.toDouble() / scanned,
                        caption = "${scanned - g.active30d} 位超過 30 天沒開過",
                        target = 0.50,
                        higherIsBetter = false
                    )
                )
            }
        }

        // ── 活躍 ──────────────────────────────────────────────
        ChartCard("活躍層級", "同一批人、同一個分母") {
            DonutChart(
                slices = listOf(
                    "今日活躍" to g.activeToday,
                    "7 日內" to (g.active7d - g.activeToday).coerceAtLeast(0),
                    "30 日內" to (g.active30d - g.active7d).coerceAtLeast(0),
                    "沉睡" to (scanned - g.active30d).coerceAtLeast(0)
                ),
                centerLabel = "使用者"
            )
        }

        // ── 成長趨勢 ───────────────────────────────────────────
        val slope = trendSlope(g.monthlyConcertTrend)
        ChartCard(
            "演出紀錄月趨勢",
            when {
                g.monthlyConcertTrend.size < 2 -> "資料還不夠畫趨勢"
                slope > 0.5 -> "整體向上，平均每月多 ${"%.1f".format(slope)} 筆"
                slope < -0.5 -> "整體向下，平均每月少 ${"%.1f".format(-slope)} 筆"
                else -> "大致持平"
            }
        ) {
            TrendChart(g.monthlyConcertTrend)
        }

        if (g.monthlySignupTrend.isNotEmpty()) {
            ChartCard("新帳號月趨勢", "只含有 firstSeenAt 的帳號（v1.96.0 之後才開始記）") {
                TrendChart(g.monthlySignupTrend)
            }
        } else {
            // 說清楚為什麼沒有，比讓它神秘地消失好。
            ChartCard("新帳號月趨勢", "還沒有資料") {
                ChartEmpty(
                    "這版開始才記錄帳號建立時間。" +
                        "累積一段時間之後，留存率與世代分析才做得出來——" +
                        "在那之前硬畫一條曲線只會是假的。"
                )
            }
        }

        // ── 消費結構 ───────────────────────────────────────────
        ChartCard(
            "票價結構",
            "這是**使用者的購票支出**，不是 App 的營收。別把它當成收入看"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DonutChart(
                    slices = listOf("付費場次" to g.paidConcerts, "免費 / 未填票價" to g.freeConcerts),
                    centerLabel = "場次"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row {
                    BigMetric(
                        value = "NT$" + (if (g.paidConcerts > 0) g.totalSpend / g.paidConcerts else 0),
                        label = "平均票價",
                        modifier = Modifier.weight(1f)
                    )
                    BigMetric(
                        value = "%.1f".format(g.avgConcertsPerActiveUser),
                        label = "每位使用者平均場數",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 內容供給健康度 ─────────────────────────────────────
        val reviewTotal = stats.approved + stats.pending
        ChartCard("活動資料庫健康度", "資料庫長得夠不夠快、夠不夠乾淨") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    BigMetric("${stats.total}", "活動總數", modifier = Modifier.weight(1f))
                    BigMetric("${stats.pending}", "待審積壓", modifier = Modifier.weight(1f))
                    BigMetric("${stats.upcoming}", "未來場次", modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                KpiGauge(
                    RatioKpi(
                        label = "審核通過率",
                        value = if (reviewTotal > 0) stats.approved.toDouble() / reviewTotal else 0.0,
                        caption = "通過 ${stats.approved} / 送審 $reviewTotal"
                    )
                )
                KpiGauge(
                    RatioKpi(
                        label = "有圖比例",
                        value = if (stats.total > 0) stats.withImage.toDouble() / stats.total else 0.0,
                        caption = "有海報的活動在列表上點擊率明顯比較高"
                    )
                )
                KpiGauge(
                    RatioKpi(
                        label = "有來源比例",
                        value = if (stats.total > 0) stats.withSource.toDouble() / stats.total else 0.0,
                        caption = "可追溯來源的比例，爬蟲與維基匯入都會帶"
                    )
                )
            }
        }

        // ── 集中度（風險）──────────────────────────────────────
        // 這張圖看的不是成績是風險：靠太少人撐著的內容，那些人一走就斷。
        val contributorTop3 = concentration(stats.contributorRanking, 3)
        ChartCard(
            "投稿集中度",
            if (contributorTop3 >= 0.8) "前三名佔了 ${formatPercent(contributorTop3)}——內容過度依賴少數人"
            else "前三名佔 ${formatPercent(contributorTop3)}"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RankBarList(stats.contributorRanking, limit = 8, valueSuffix = " 筆")
                if (contributorTop3 >= 0.8 && stats.contributorRanking.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    ) {
                        Text(
                            "風險：這幾位停下來，活動資料庫就會停止成長。" +
                                "可以考慮加爬蟲來源或維基匯入來分散。",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // ── 排行與分布 ─────────────────────────────────────────
        ChartCard("熱門藝人 TOP", "卡司已展開成個別藝人") {
            RankBarList(stats.artistRanking, limit = 10, valueSuffix = " 場")
        }
        ChartCard("熱門場地 TOP") {
            RankBarList(stats.venueRanking, limit = 8, valueSuffix = " 場")
        }
        ChartCard("縣市分布") {
            RankBarList(stats.cityRanking, limit = 8, valueSuffix = " 場")
        }
        ChartCard("星期分布", "排檔期用：看哪幾天本來就擠") {
            CycleBarChart(stats.weekdayRanking)
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "說明：全站彙整一次最多掃 300 位使用者，上面所有比率都以「掃到的這批人」為分母。" +
                "留存率與世代分析需要帳號建立時間，這版開始才記，之後才會出現。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
