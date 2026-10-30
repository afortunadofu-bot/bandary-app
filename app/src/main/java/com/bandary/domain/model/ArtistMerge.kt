package com.bandary.domain.model

/**
 * 同一個團的不同寫法，找出來並選一個代表名。
 *
 * 實機例子：`ggteen`、`公館青少年`、`公館青少年GGteen` 其實是同一個團，
 * 但在資料庫裡是三筆。追蹤只追得到其中一種寫法、排行把一個團拆成三份、
 * 統計怎麼看都不對——而且使用者完全不知道自己追的是「哪一個版本」。
 *
 * ## 判斷規則：包含，而不是相似度
 * 刻意**不用**編輯距離（Levenshtein）那種相似度：
 * 「告五人」與「告白氣球」距離很近但毫無關係；
 * 「滅火器」與「滅火器 Fire EX.」距離很遠卻是同一個團。
 * 中文團名短、外文別名長，相似度在這個資料上會兩邊都錯。
 *
 * 改用**正規化後的包含關係**：A 整個出現在 B 裡面就算同一組。
 * 這條規則抓得到真正常見的那幾種寫法差異（加英文名、加前綴、加空格與符號），
 * 而且**不會把不相關的名字湊成一組**——誤判的代價比漏判高得多，
 * 合併錯了是把兩個團的紀錄混在一起，使用者自己也救不回來。
 *
 * ## 代表名選最長的
 * 最長的那個通常同時含中文與外文（`公館青少年GGteen`），
 * 資訊量最大，而且從它反推得到其他寫法，反過來不行。
 */
object ArtistMerge {

    /** 合併比對用的鍵：去掉空白與常見標點、全形轉半形、轉小寫。 */
    fun mergeKey(name: String): String =
        name.trim()
            .lowercase()
            .replace("臺", "台")
            .filter { it.isLetterOrDigit() }

    /**
     * 一組同義的寫法。[canonical] 是建議的代表名，[variants] 含代表名本身。
     */
    data class Group(
        val canonical: String,
        val variants: List<String>,
        /**
         * 中文維基上的正式條目名稱；查不到是 null。
         *
         * 有值的時候它**優先於「最長的那個」**當代表名：維基的條目標題是公認的寫法，
         * 而最長的那個只是我們在沒有別的依據時的猜測。
         * 例如 `Fire EX.` / `滅火器` 這一組，最長的是 `滅火器 Fire EX.`，
         * 但維基的條目叫「滅火器樂團」。
         */
        val wikiTitle: String? = null
    ) {
        /** 實際要用的代表名：有維基資料就用維基的，否則用最長的那個。 */
        val preferred: String get() = wikiTitle?.takeIf { it.isNotBlank() } ?: canonical

        /** 要被合併掉的（代表名以外的）。 */
        val others: List<String> get() = variants.filter { it != preferred }
    }

    /**
     * 從一份名單裡找出可以合併的組。只回傳**真的有兩個以上寫法**的組。
     *
     * [minKeyLength] 是安全閥：太短的鍵（一兩個字）幾乎一定會跟別人撞，
     * 例如 `hue` 會被 `thue`、`huell` 吃進去。短名字寧可漏，不要錯。
     */
    fun findGroups(names: List<String>, minKeyLength: Int = 3): List<Group> {
        val clean = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val keyed = clean.mapNotNull { n ->
            val k = mergeKey(n)
            if (k.length >= minKeyLength) n to k else null
        }
        // 長的先處理：這樣短的才會被併進長的，而不是反過來。
        val sorted = keyed.sortedByDescending { it.second.length }
        val used = HashSet<String>()
        val groups = mutableListOf<Group>()

        sorted.forEach { (name, key) ->
            if (name in used) return@forEach
            val members = mutableListOf(name)
            sorted.forEach inner@{ (other, otherKey) ->
                if (other == name || other in used) return@inner
                // 包含關係：短的整個出現在長的裡面。
                if (key.contains(otherKey) || otherKey.contains(key)) {
                    members += other
                }
            }
            if (members.size > 1) {
                members.forEach { used += it }
                // 代表名：最長的；同長度時用字典序，結果才穩定（不會每次跑出不同答案）。
                val canonical = members.maxWithOrNull(
                    compareBy<String> { it.length }.thenByDescending { it }
                ) ?: name
                groups += Group(canonical, members.sortedByDescending { it.length })
            }
        }
        return groups.sortedByDescending { it.variants.size }
    }

    /**
     * 把一串卡司裡屬於某組的寫法換成代表名，並去掉因此產生的重複。
     *
     * 給「整理既有資料」用：同一場的卡司裡同時出現 `公館青少年` 與 `公館青少年GGteen`
     * 是很常見的（爬蟲從不同欄位抓到的），換完要記得去重。
     */
    fun applyTo(names: List<String>, groups: List<Group>): List<String> {
        if (groups.isEmpty()) return names
        val lookup = HashMap<String, String>()
        groups.forEach { g -> g.variants.forEach { v -> lookup[mergeKey(v)] = g.preferred } }
        return names.map { n -> lookup[mergeKey(n)] ?: n }.distinct()
    }
}
