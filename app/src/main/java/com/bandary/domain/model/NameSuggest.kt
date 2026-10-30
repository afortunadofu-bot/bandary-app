package com.bandary.domain.model

/**
 * 邊打字邊問「你是不是要找這個」。**比的是名字，不限藝人**——
 * 場館輸入也走這一套（同一種打字習慣：記得字、不記得順序）。
 *
 * ## 為什麼不能只用 contains
 * 選歌頁與新增演唱會頁原本各寫一份 `knownArtists.filter { it.contains(q) }`。
 * `contains` 是**照順序**比的，所以這些全都比不到：
 *
 * | 你打的 | 資料庫裡的 | contains | 這裡 |
 * |---|---|---|---|
 * | `青少年公館` | `公館青少年GGteen` | ✗ | ✓ |
 * | `GGteen 公館` | `公館青少年GGteen` | ✗ | ✓ |
 * | `公館 青少年` | `公館青少年GGteen` | ✗ | ✓ |
 * | `滅火器 fire` | `滅火器 Fire EX.` | ✗ | ✓ |
 *
 * 實機回報：「選歌跟演唱會藝人名單還是沒有自動把相似的整合，
 * 或輸入到一半問你是不是這個（不考慮字序那種）」。
 * 重點就在括號裡那句——人記團名不會記順序，中英混名更是想到哪打到哪。
 *
 * ## 跟 [ArtistMerge] 的分工
 * [ArtistMerge] 是**事後合併資料庫裡的重複**，那裡誤判的代價很高
 * （把兩個團的紀錄混在一起，使用者自己救不回來），所以只用嚴格的包含關係。
 *
 * 這裡是**輸入時的建議**：列錯了使用者不點就沒事，代價低得多，
 * 所以可以放寬到「字序無關」。但仍然有門檻（見 [MIN_UNORDERED_LEN]）——
 * 建議列表一旦變成「什麼都列」，使用者就會完全不看它。
 */
object NameSuggest {

    /**
     * 字序無關比對的最短長度。
     *
     * 兩個字（例如「一人」）在中文團名裡幾乎一定撞到一堆無關的名字；
     * 三個字開始才是一個有辨識度的片段。寧可少列，不要讓建議變成雜訊。
     */
    private const val MIN_UNORDERED_LEN = 3

    /**
     * 回傳建議名單，最相關的在前面。
     *
     * [query] 是使用者目前打的字，[known] 是已知的藝人名（記過的歌 + 看過的演出），
     * [exclude] 是已經填在這張表單上的名字（不要建議重複的）。
     * 完全相同的不列——他已經打完了，再建議一次是廢話。
     */
    fun suggest(
        query: String,
        known: List<String>,
        exclude: Collection<String> = emptyList(),
        limit: Int = 6
    ): List<String> {
        val key = ArtistMerge.mergeKey(query)
        if (key.isEmpty()) return emptyList()
        val excludeKeys = exclude.map { ArtistMerge.mergeKey(it) }.toSet()

        return known.asSequence()
            .distinct()
            .mapNotNull { name ->
                val k = ArtistMerge.mergeKey(name)
                if (k.isEmpty() || k == key || k in excludeKeys) return@mapNotNull null
                val tier = tierOf(key, k) ?: return@mapNotNull null
                Triple(name, tier, k.length)
            }
            // 同一層級內短的排前面：短的通常是正式名，長的是加了英文或後綴的變體。
            .sortedWith(compareBy({ it.second }, { it.third }, { it.first }))
            .map { it.first }
            .take(limit)
            .toList()
    }

    /** 比對層級，數字越小越相關；完全比不到回 null。 */
    private fun tierOf(key: String, candidate: String): Int? = when {
        candidate.startsWith(key) -> 0
        candidate.contains(key) -> 1
        // 使用者打得比資料庫裡的名字還長（打了「公館青少年GGteen」，庫裡只有「公館青少年」）。
        key.contains(candidate) -> 2
        // 字序無關：打的每一個字都在候選名字裡（含重複次數）。
        key.length >= MIN_UNORDERED_LEN && isSubMultiset(key, candidate) -> 3
        else -> null
    }

    /**
     * [a] 的每個字元都能在 [b] 裡找到（含出現次數）。
     *
     * 用「可重複的集合」而不是 `all { b.contains(it) }`：
     * 後者會讓「告告五人」比中「告五人」，而那是打錯字不是別的寫法。
     */
    private fun isSubMultiset(a: String, b: String): Boolean {
        val left = HashMap<Char, Int>(b.length)
        b.forEach { left[it] = (left[it] ?: 0) + 1 }
        a.forEach { c ->
            val n = left[c] ?: return false
            if (n == 1) left.remove(c) else left[c] = n - 1
        }
        return true
    }
}
