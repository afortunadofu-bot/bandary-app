package com.bandary.domain.model

/**
 * 把後台的數字翻成「現在該做什麼」。
 *
 * ## 為什麼需要這一層
 * 儀表板做完之後還是有個問題：它告訴你**現在長什麼樣**，但不告訴你**該做什麼**。
 * 「沉睡率 62%」是一個事實，不是一個行動；管理員每次都要自己在腦裡
 * 把每個數字跟目標比一遍，才知道哪裡要處理。那件事應該由程式做。
 *
 * ## 挑選的原則：**只列需要動作的**
 * 這張卡不是第二份儀表板。一切正常時它要能說「沒事」然後消失，
 * 不然它就變成另一個「為了放而放」的區塊——而那正是要避免的東西。
 *
 * 每一條都必須同時具備：
 *   1. **可判斷**：有明確的門檻，不是感覺
 *   2. **可行動**：看完知道下一步按哪裡
 *   3. **會變**：處理完之後它會自己消失
 *
 * 想不出第 2 點的指標就不要放進來——那種東西放了也只是讓人焦慮。
 */
object AdminAlerts {

    enum class Level {
        /** 擋住事情的，先處理。 */
        URGENT,
        /** 該注意，但不急。 */
        WATCH
    }

    data class Alert(
        val level: Level,
        val title: String,
        /** 為什麼這是問題 + 下一步做什麼。缺了這句，這條就只是另一個數字。 */
        val action: String
    )

    /** 門檻集中在這裡，不要散在各處——改目標時只有一個地方要動。 */
    const val DORMANT_LIMIT = 0.60      // 沉睡率超過就該看
    const val PRO_TARGET = 0.05         // 付費轉換率目標
    const val ACTIVATION_TARGET = 0.40  // 啟用率目標
    const val IMAGE_TARGET = 0.50       // 活動有圖比例
    const val CONCENTRATION_LIMIT = 0.80 // 投稿集中度風險線
    const val UPCOMING_FLOOR = 20       // 未來場次低於這個數，內容快見底了

    /**
     * @param scannedUsers 全站彙整抽樣到的人數；0 代表還沒撈到資料，整段跳過
     *                     （用 0 當分母算出來的比率全是 0，會誤報成「全部都有問題」）。
     */
    fun of(
        pendingEvents: Int,
        pendingVenueViews: Int,
        upcomingEvents: Int,
        totalEvents: Int,
        withImage: Int,
        contributorTop3Share: Double,
        scannedUsers: Int,
        active30d: Int,
        proUsers: Int,
        usersWithConcerts: Int
    ): List<Alert> {
        val out = mutableListOf<Alert>()

        if (pendingEvents > 0) {
            out += Alert(
                Level.URGENT,
                "有 $pendingEvents 筆活動等審核",
                "待審的活動不會出現在使用者的活動頁。到「活動」頁右上角 ⋮ → 審核投稿。"
            )
        }
        if (pendingVenueViews > 0) {
            out += Alert(
                Level.URGENT,
                "有 $pendingVenueViews 張座位視野圖等審核",
                "審過才會出現在對應場館的視野圖裡。到後台「活動」頁籤最上面的「座位視野圖待審」。"
            )
        }
        if (upcomingEvents in 1 until UPCOMING_FLOOR) {
            out += Alert(
                Level.URGENT,
                "未來只剩 $upcomingEvents 場活動",
                "活動頁快見底了。到「活動蒐集來源」加來源或按立即抓取，" +
                    "也可以加一篇維基的巡演條目一次補進歷史場次。"
            )
        } else if (upcomingEvents == 0 && totalEvents > 0) {
            out += Alert(
                Level.URGENT,
                "未來沒有任何活動",
                "活動頁現在是空的。先到「活動蒐集來源」確認排程有在跑。"
            )
        }

        if (totalEvents > 0) {
            val imageRate = withImage.toDouble() / totalEvents
            if (imageRate < IMAGE_TARGET) {
                out += Alert(
                    Level.WATCH,
                    "只有 ${formatPercent(imageRate)} 的活動有圖",
                    "有海報的活動在列表上明顯比較會被點。審核投稿時順手補圖，" +
                        "或在活動編輯頁用「找海報」。"
                )
            }
        }
        if (contributorTop3Share >= CONCENTRATION_LIMIT) {
            out += Alert(
                Level.WATCH,
                "前三名投稿者佔了 ${formatPercent(contributorTop3Share)}",
                "內容過度依賴少數人，他們停下來資料庫就不長了。" +
                    "加爬蟲來源或維基匯入可以分散這個風險。"
            )
        }

        // 使用者相關的指標：沒撈到人就整段跳過，不要用 0 當分母報一堆假警訊。
        if (scannedUsers > 0) {
            val dormant = 1.0 - active30d.toDouble() / scannedUsers
            if (dormant > DORMANT_LIMIT) {
                out += Alert(
                    Level.WATCH,
                    "沉睡率 ${formatPercent(dormant)}",
                    "超過六成的人一個月沒回來。發一則公告，或先看漏斗是卡在哪一層。"
                )
            }
            val activation = usersWithConcerts.toDouble() / scannedUsers
            if (activation < ACTIVATION_TARGET) {
                out += Alert(
                    Level.WATCH,
                    "啟用率 ${formatPercent(activation)}",
                    "註冊了但沒記過任何一場。問題通常在第一次使用的引導，不在獲取。"
                )
            }
            val pro = proUsers.toDouble() / scannedUsers
            if (pro < PRO_TARGET) {
                out += Alert(
                    Level.WATCH,
                    "付費轉換率 ${formatPercent(pro)}",
                    "先確認重度使用者（記滿 10 場）有多少——那才是會付費的人。" +
                        "基數太小的話，衝轉換率不如先衝留存。"
                )
            }
        }

        // 急的排前面；同級維持原本的順序（那個順序本來就是依重要性寫的）。
        return out.sortedBy { it.level.ordinal }
    }
}
