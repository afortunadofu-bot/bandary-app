package com.bandary.domain.model

// 「活動」頁的投稿、重複偵測與合併——純計算，不碰 Android / Firestore，才能直接單元測試。
// 名稱比對規則網頁版（web/index.html 的 normalizeEventName / isSimilarEventName）有同一份，
// 兩邊要一起改，否則同一筆投稿在手機上被標「可能重複」、在網頁上卻不會。

private const val EVENT_MAX_IMAGES = 5   // 與 FreeEventsRepository.MAX_IMAGES 一致

/**
 * 活動名稱正規化：只留字母與數字（含中日韓文字），忽略大小寫、空白與標點。
 * 以 code point 處理，避免罕用字（代理對）被拆壞；數字只認十進位（對應 JS 的 \p{Nd}）。
 */
fun normalizeEventName(raw: String): String {
    val sb = StringBuilder()
    raw.lowercase().codePoints().forEach { cp ->
        if (Character.isLetter(cp) || Character.isDigit(cp)) sb.appendCodePoint(cp)
    }
    return sb.toString()
}

private fun codePointList(s: String): List<Int> = s.codePoints().toArray().toList()

private fun bigrams(cps: List<Int>): List<Pair<Int, Int>> =
    if (cps.size < 2) cps.map { it to -1 } else (0 until cps.size - 1).map { cps[it] to cps[it + 1] }

/**
 * 兩個活動名稱是否「看起來是同一場」：
 *  1. 正規化後相同（「Deca Joins 巡演」＝「deca joins巡演」）
 *  2. 其中一個包含另一個，且較短的至少 4 個字（「大港開唱」⊂「2026 大港開唱 Day1」；太短的像「演唱會」不算）
 *  3. 兩字組（bigram）Dice 相似度 ≥ 0.6（錯字、少一兩個字）
 */
fun isSimilarEventName(a: String, b: String): Boolean {
    val na = normalizeEventName(a)
    val nb = normalizeEventName(b)
    if (na.isEmpty() || nb.isEmpty()) return false
    if (na == nb) return true
    val ca = codePointList(na)
    val cb = codePointList(nb)
    if (minOf(ca.size, cb.size) >= 4 && (na.contains(nb) || nb.contains(na))) return true
    val ga = bigrams(ca)
    val gb = bigrams(cb)
    val pool = gb.toMutableList()
    var hit = 0
    for (g in ga) {
        val i = pool.indexOf(g)
        if (i >= 0) { hit++; pool.removeAt(i) }
    }
    return 2.0 * hit / (ga.size + gb.size) >= 0.6
}

/** 兩個活動有沒有落在同一天（多日活動看日期區間有沒有重疊）。 */
fun FreeEvent.sharesDayWith(other: FreeEvent): Boolean =
    occurrenceDates().toSet().intersect(other.occurrenceDates().toSet()).isNotEmpty()

/** 在 [pool] 裡找「同一天且名稱相似」的活動；找不到回 null。 */
fun findSimilarEvent(candidate: FreeEvent, pool: List<FreeEvent>): FreeEvent? =
    pool.firstOrNull { it.id != candidate.id && it.sharesDayWith(candidate) && isSimilarEventName(it.name, candidate.name) }

/**
 * 使用者新增的演出 → 送審用的活動。只帶「公開的活動資訊」：
 * 名稱、藝人陣容、場地、城市、時間、票價、售票／資訊連結、開賣時間。
 * **不帶**照片、座位、取票資訊、心得、評分、同行者、歌單——那些是個人紀錄。
 * 名稱與藝人都空白時回 null（沒有東西可以審）。
 */
fun Concert.toEventSubmission(): FreeEvent? {
    val lineup = (listOf(artistName) + festivals.filter { !it.feat }.map { it.artistName })
        .map { it.trim() }.filter { it.isNotBlank() }.distinct()
    val guests = festivals
        .filter { it.feat && it.artistName.isNotBlank() && it.featOf.isNotBlank() }
        .groupBy { it.featOf.trim() }
        .mapValues { (_, slots) -> slots.map { it.artistName.trim() }.distinct() }
    val title = name.trim().ifBlank { lineup.firstOrNull().orEmpty() }
    if (title.isBlank()) return null
    // **不帶使用者自己那張票的價格。**
    //
    // 他填的是「我這張花了多少」——那是一個票種、一個座位區的價格，
    // 不代表這場活動只有那一個價位。直接寫進公開活動的 ticketPrice，
    // 別人看到的就會是「這場 NT$2800」，而實際上可能有 1800 / 2800 / 3600 三種票。
    // 寫錯的票價比沒有票價糟得多：沒有票價的人會自己去售票頁查，
    // 看到錯票價的人則會直接相信，然後在現場或結帳時才發現不對。
    //
    // 只保留「免費 / 付費」這一個位元：0 元是使用者**確定**這場不用錢（那個值得保留），
    // 大於 0 就只標記成付費（-1.0 是這個專案既有的「付費但金額未知」慣例，
    // 見 FreeEventsScreen 新增表單那裡的同一個寫法），實際金額留給管理員或
    // 後續的爬蟲資料補上。
    // 免費（null 或 0）維持原本的 null——FreeEvent 裡 null 與 0 都代表免費，
    // 是既有慣例，沒有理由在這裡多製造一種寫法。
    // 只有付費的情況改掉：記成「付費但金額未知」而不是他付的那個數字。
    val price = if ((ticketPrice ?: 0.0) > 0.0) -1.0 else null
    return FreeEvent(
        name = title,
        artistName = lineup.joinToString(" / "),
        guestsByArtist = guests,
        venue = venue.trim(),
        city = city?.trim()?.ifBlank { null },
        dateTime = dateTime,
        endDate = endDate?.takeIf { it > dateTime.date },
        endTime = endTime,
        ticketPrice = price,
        ticketUrl = ticketUrl?.split("\n")?.map { it.trim() }?.firstOrNull { it.isNotBlank() },
        saleTimes = saleTimes.sortedBy { it.at },
        sourceUrl = infoUrl?.trim()?.ifBlank { null },
        status = FreeEventStatus.PENDING
    )
}

/**
 * 合併活動：以 [primary] 為主（id、名稱、時間、狀態、投稿者都沿用它），
 * 主活動空著的欄位依序從 [others] 補上；藝人、嘉賓、開賣時間、圖片取聯集（圖最多 5 張）。
 */
fun mergeFreeEvents(primary: FreeEvent, others: List<FreeEvent>): FreeEvent {
    val all = listOf(primary) + others
    fun text(get: (FreeEvent) -> String?): String? =
        all.asSequence().mapNotNull(get).map { it.trim() }.firstOrNull { it.isNotBlank() }

    val artists = all.flatMap { it.artistName.split(" / ") }
        .map { it.trim() }.filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
    val guests = LinkedHashMap<String, List<String>>()
    all.forEach { e ->
        e.guestsByArtist.forEach { (host, list) ->
            guests[host] = (guests[host].orEmpty() + list).distinct()
        }
    }
    // 票價：有明確金額的優先，其次「付費但金額未知」（-1），都沒有才是免費（null）。
    val price = all.mapNotNull { it.ticketPrice }.firstOrNull { it > 0.0 }
        ?: all.mapNotNull { it.ticketPrice }.firstOrNull { it < 0.0 }
    val images = all.flatMap { it.images }.distinct().take(EVENT_MAX_IMAGES)
    return primary.copy(
        artistName = artists.joinToString(" / "),
        guestsByArtist = guests,
        venue = text { it.venue }.orEmpty(),
        city = text { it.city },
        endDate = all.firstNotNullOfOrNull { it.endDate },
        endTime = all.firstNotNullOfOrNull { it.endTime },
        ticketPrice = price,
        priceText = text { it.priceText },
        ticketUrl = text { it.ticketUrl },
        saleTimes = all.flatMap { it.saleTimes }.distinctBy { it.at }.sortedBy { it.at },
        note = text { it.note },
        sourceUrl = text { it.sourceUrl },
        imageUris = images,
        imageUri = images.firstOrNull()
    )
}

/** 手動合併時挑誰當主活動：資料最完整的；一樣完整就挑最早建立的。 */
fun pickMergePrimary(events: List<FreeEvent>): FreeEvent? =
    events.sortedWith(
        compareByDescending<FreeEvent> { e ->
            listOf(e.artistName, e.venue, e.city, e.ticketUrl, e.sourceUrl, e.priceText)
                .count { !it.isNullOrBlank() } +
                (if (e.endDate != null) 1 else 0) + (if (e.ticketPrice != null) 1 else 0) +
                e.saleTimes.size + e.images.size
        }.thenBy { it.createdAt }
    ).firstOrNull()

/**
 * 演唱會編輯頁的相簿清單：封面不在相簿裡時補到最前面。
 * 從活動「我要參加」帶進來的封面（舊版只寫進 photoUri）原本不會出現在相簿，
 * 使用者看不到它，一換封面它就不再被任何欄位引用、等於被丟掉。內建底圖不是照片，不補。
 */
fun albumIncludingCover(cover: String?, album: List<String>, isBuiltinCover: (String) -> Boolean): List<String> {
    val c = cover?.takeIf { it.isNotBlank() } ?: return album
    if (c in album || isBuiltinCover(c)) return album
    return listOf(c) + album
}

/**
 * 這筆活動是爬蟲抓來的，還是使用者投稿的？
 *
 * 判斷依據是 `createdBy` 的前綴：排程蒐集寫入時固定是 `crawler:<來源名稱>`
 *（見 functions/crawl.js），使用者投稿寫的則是他的 email。
 *
 * ## 為什麼要分開
 * 兩者要審的東西完全不同：
 *  - **使用者投稿**是一筆一筆手打的，要看的是「這是真的活動嗎、資料對不對」，
 *    而且背後有個人在等回覆——駁回要給理由。
 *  - **爬蟲**是整批進來的，要看的是「這個來源這次抓得對不對」，
 *    通常一次處理幾十筆，而且錯了是來源解析的問題，不是哪個人的問題。
 *
 * 混在同一個清單裡，爬蟲那幾十筆會把使用者那三筆洗掉——
 * 而等回覆的是那三筆的人。
 */
fun FreeEvent.isFromCrawler(): Boolean =
    createdBy?.startsWith("crawler:") == true
