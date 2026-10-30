package com.bandary.domain.model

/**
 * 粉絲 PR 值：在同一位藝人的所有記錄者裡，你贏過多少百分比的人。
 *
 * 用 PR 而不是只給名次，是因為名次本身沒有尺度——「第 8 名」在 10 個人裡很後面，
 * 在 500 個人裡很前面。PR 直接回答「我算不算鐵粉」。
 *
 * 採台灣學測那一套的定義：PR = 分數**不高於**你的人數 ÷ 總人數 × 100，無條件捨去。
 * 跟你同分的人算在你這邊（不是打敗他們，而是「你至少跟他們一樣」），
 * 否則一堆人都看 1 場時，每個人的 PR 都會是 0，看起來像沒人理你。
 */
object FanPercentile {

    /**
     * [myCount] 我看過幾場，[allCounts] 所有記錄者的場次數（含我自己）。
     *
     * 回傳 1–99。刻意不給 0 與 100：
     *   - 0 會讓場次最少的人看到「PR 0」，像是被歸類成沒資格。
     *   - 100 在 PR 的慣例裡不存在（你贏不過自己）。
     * 人數不足（只有我一個）時回 null——一個人算不出百分位，硬給數字是騙人的。
     */
    fun of(myCount: Int, allCounts: List<Int>): Int? {
        if (myCount <= 0) return null
        val total = allCounts.size
        if (total < MIN_SAMPLE) return null
        val notAbove = allCounts.count { it <= myCount }
        val raw = notAbove * 100 / total
        return raw.coerceIn(1, 99)
    }

    /**
     * 至少要幾個人才給 PR。
     *
     * 2 個人的「PR 50」沒有任何資訊量，只會讓人誤以為自己贏過一半的粉絲。
     * 設 5 是個折衷：夠小到大部分藝人都算得出來，又不至於一兩個人就下結論。
     */
    const val MIN_SAMPLE = 5

    /** 給畫面用的一句話。null＝人數不足，呼叫端要改顯示「還太少人記錄」。 */
    fun label(pr: Int?): String? = pr?.let { "PR $it" }

    /**
     * PR 對應的稱呼。分段刻意粗，只有三段——
     * 分太細會讓人為了升一級去補資料，那不是這個功能的用意。
     */
    fun tier(pr: Int?): String? = when {
        pr == null -> null
        pr >= 90 -> "死忠"
        pr >= 60 -> "常客"
        else -> "入坑中"
    }
}
