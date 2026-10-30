package com.bandary.domain.model

/**
 * 單曲的合作藝人（feat.）。
 *
 * 歌單存的是 `List<String>`（見 SetlistCodec），一首歌就是一列字串。
 * 要記「這首是跟誰合唱的」時，最省事的做法是**寫進同一列**而不是另開一張表：
 *   - 不用動 Room / Firestore 的 schema，舊資料照樣讀得出來。
 *   - 分享出去的票根、匯出的純文字都自然帶著 feat.，不必另外處理。
 *   - 使用者自己打「愛你 feat. 老王」本來就會這樣寫，格式跟直覺一致。
 *
 * 代價是統計要記得**只算歌名**，否則「愛你」與「愛你 feat. 老王」會被當成兩首。
 * [titleOf] 就是為此而存在，凡是要比對或計數歌曲的地方都該先過它。
 */
object SongFeat {

    /** 寫進字串時用的分隔。前後留空白，避免把歌名結尾的字吃掉。 */
    const val MARKER = " feat. "

    /** 多位合作藝人之間的分隔（顯示與解析都用這個）。 */
    const val JOIN = "、"

    /** 使用者可能打出來的各種寫法，解析時一律接受。順序有意義：長的要先比。 */
    private val MARKERS = listOf(
        " feat. ", " feat.", " feat ", " ft. ", " ft.", " ft ",
        "（feat.", "（feat ", "(feat.", "(feat ", " with "
    )

    /**
     * 把歌名與合作藝人組成一列。沒有 feat. 就回歌名本身——
     * 空的 feat. 不該在歌單上留下「愛你 feat.」這種半截字串。
     */
    fun format(title: String, feats: List<String>): String {
        val t = title.trim()
        val f = feats.map { it.trim() }.filter { it.isNotBlank() }
        return if (f.isEmpty()) t else t + MARKER + f.joinToString(JOIN)
    }

    /**
     * 拆出（歌名, 合作藝人）。認不出 feat. 就整串當歌名，合作藝人為空。
     *
     * 刻意**不猜**：只有出現明確的 feat./ft./with 標記才拆。
     * 歌名裡本來就有括號的（「愛你（Live）」）不會被誤判。
     */
    fun parse(line: String): Pair<String, List<String>> {
        val raw = line.trim()
        val lower = raw.lowercase()
        for (m in MARKERS) {
            val idx = lower.indexOf(m.lowercase())
            if (idx <= 0) continue          // idx == 0 代表整列以 feat 開頭，那不是歌名
            val title = raw.substring(0, idx).trim()
            val rest = raw.substring(idx + m.length)
                .trim()
                .trimEnd(')', '）')          // 「愛你（feat. 老王）」的收尾括號
                .trim()
            if (title.isBlank() || rest.isBlank()) continue
            val feats = rest.split(JOIN, ",", "，", "&", "/", "＆")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (feats.isEmpty()) continue
            return title to feats
        }
        return raw to emptyList()
    }

    /** 只要歌名。統計與去重一律用這個，否則同一首歌會因為 feat. 被算成兩首。 */
    fun titleOf(line: String): String = parse(line).first

    /** 只要合作藝人。 */
    fun featsOf(line: String): List<String> = parse(line).second

    /** 這一列有沒有合作藝人。 */
    fun hasFeat(line: String): Boolean = featsOf(line).isNotEmpty()
}
