package com.bandary.presentation.ui.shows

// ── 歌單編碼：分藝人區塊存成 List<String>，以 "▶ 藝人" 當區塊標頭 ─────────
// 純計算（不碰 Compose / Android），才能直接單元測試。詳情頁、選歌候選、票根分享都讀同一套格式。
const val SETLIST_ARTIST_PREFIX = "▶ "
// 段落分隔（同一位藝人歌單內再分段，例如「安可」），以 "◆ 段落名" 存成一列；編號會在段落後重新從 1 起算。
const val SETLIST_SECTION_PREFIX = "◆ "

/**
 * 把「每位藝人的歌單」攤平成 List<String>。
 *
 * 標頭規則：**只有一位藝人有歌、而且他就是卡司第一位**時才省略標頭（舊資料就長這樣，維持不變），
 * 其他情況一律寫標頭。以前是「多位藝人有歌才寫」——音樂祭裡只記了第二位以後某一團的歌時，
 * 標頭被省略，重新打開就被 [parseSetlist] 整包歸給第一位藝人，詳情頁也看不出是誰的歌單。
 */
fun encodeSetlist(byArtist: Map<String, List<String>>, order: List<String>): List<String> {
    val keys = (order + byArtist.keys).distinct()
    val withSongs = keys.filter { k -> byArtist[k]?.any { it.isNotBlank() } == true }
    val firstInOrder = order.firstOrNull { it.isNotBlank() }
    val omitHeader = withSongs.size == 1 && withSongs[0] == firstInOrder
    val out = ArrayList<String>()
    withSongs.forEach { a ->
        val songs = byArtist[a].orEmpty().map { it.trim() }.filter { it.isNotBlank() }
        if (a.isNotBlank() && !omitHeader) out.add(SETLIST_ARTIST_PREFIX + a)
        out.addAll(songs)
    }
    return out
}

/** 把攤平的歌單解析回「每位藝人的歌單」。無標頭的舊資料整包歸給 firstArtist。 */
fun parseSetlist(list: List<String>, firstArtist: String): Map<String, List<String>> {
    val map = LinkedHashMap<String, MutableList<String>>()
    var cur = firstArtist
    list.forEach { line ->
        if (line.startsWith(SETLIST_ARTIST_PREFIX)) {
            cur = line.removePrefix(SETLIST_ARTIST_PREFIX).trim()
            map.getOrPut(cur) { ArrayList() }
        } else if (line.isNotBlank()) {
            map.getOrPut(cur) { ArrayList() }.add(line)
        }
    }
    return map
}

/**
 * 把 [from] 藝人歌單裡指定位置（[indices]，含段落列）的項目移到 [to] 藝人歌單最後面，保持原本的相對順序。
 * 用在「藝人 key 錯」的時候：全選＝整份換給另一位藝人，挑幾首＝只搬那幾首。
 * 搬完 [from] 沒有剩下任何內容就把這個區塊拿掉（卡司裡已經改掉的錯名字，才不會留一個空區塊）；
 * [to] 原本的空白輸入列一併清掉，搬過來的歌才不會接在空行後面。
 */
fun moveSetlistItems(
    byArtist: Map<String, List<String>>,
    from: String,
    indices: Set<Int>,
    to: String
): Map<String, List<String>> {
    if (from == to || indices.isEmpty()) return byArtist
    val source = byArtist[from].orEmpty()
    val moving = source.filterIndexed { i, _ -> i in indices }.filter { it.isNotBlank() }
    if (moving.isEmpty()) return byArtist
    val remaining = source.filterIndexed { i, _ -> i !in indices }
    val out = LinkedHashMap<String, List<String>>(byArtist)
    if (remaining.any { it.isNotBlank() }) out[from] = remaining else out.remove(from)
    out[to] = out[to].orEmpty().filter { it.isNotBlank() } + moving
    return out
}
