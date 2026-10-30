package com.bandary.domain.model

/**
 * 共用藝人資料庫的一筆（Firestore `artists/{id}`）。
 *
 * 為什麼要有這個：以前「藝人」只是每筆記錄裡的一個字串，
 * 所以只搜得到**自己看過**的藝人；想追蹤一個還沒看過的團，App 裡根本沒有那個名字。
 * 而且「滅火器」「滅火器 Fire EX.」「Fire EX.」會被當成三位不同的藝人。
 *
 * 這份資料庫是大家共用的：任何登入者都能新增，新增之後其他人也搜得到。
 * 顯示一律用 [name]；比對一律用 [key]（`artistFollowKey`）。
 */
data class ArtistEntry(
    val id: String = "",
    val name: String = "",
    /** 比對鍵＝`artistFollowKey(name)`。寫入時一起存，才能在雲端直接用它查重。 */
    val key: String = "",
    /** 別名／外文名。「Fire EX.」是「滅火器」的別名這種情況。 */
    val aliases: List<String> = emptyList(),
    val createdBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** 管理員確認過的名字。搜尋結果排前面。 */
    val verified: Boolean = false
) {
    /** 這筆的所有可搜尋字串（本名＋別名）的比對鍵。 */
    val allKeys: Set<String>
        get() = (listOf(name) + aliases).filter { it.isNotBlank() }
            .map { artistFollowKey(it) }.toSet()
}

/**
 * 打字時的比對：比對鍵「包含」輸入字串就算命中。
 * Firestore 沒有子字串查詢，所以搜尋是在本機對整份（已快取的）名單做的
 * ——藝人資料庫在可預見的規模內是幾百到幾千筆，這樣最快也最單純。
 * 真的長到上萬筆再改成前綴查詢 + 分頁。
 */
fun List<ArtistEntry>.searchArtists(query: String, limit: Int = 8): List<ArtistEntry> {
    val q = artistFollowKey(query)
    if (q.isBlank()) return emptyList()
    return asSequence()
        .mapNotNull { e ->
            val keys = e.allKeys
            val rank = when {
                keys.any { it == q } -> 0            // 完全相同
                keys.any { it.startsWith(q) } -> 1   // 開頭符合
                keys.any { it.contains(q) } -> 2     // 中間出現
                else -> return@mapNotNull null
            }
            e to rank
        }
        // 同分時已驗證的優先，再來才是字典序——避免每次搜尋結果順序跳動。
        .sortedWith(
            compareBy<Pair<ArtistEntry, Int>> { it.second }
                .thenByDescending { it.first.verified }
                .thenBy { it.first.name }
        )
        .map { it.first }
        .take(limit)
        .toList()
}

/** 這個名字在名單裡已經有了嗎（本名或別名都算）。 */
fun List<ArtistEntry>.containsArtist(name: String): Boolean {
    val k = artistFollowKey(name)
    return k.isNotBlank() && any { k in it.allKeys }
}
