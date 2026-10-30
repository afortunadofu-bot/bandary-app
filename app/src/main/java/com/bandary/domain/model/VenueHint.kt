package com.bandary.domain.model

/**
 * 一個你去過的場館，連同上次填過的地址與縣市。
 *
 * ## 為什麼要帶地址
 * 使用者要求：「場館要有 key 到一半自動出現類似場館來選擇，最好都能直接帶入完整地址」。
 * 地址只會從**你自己填過的紀錄**來——免費活動（爬蟲來的）只有場館名與縣市，沒有地址欄，
 * 所以那邊補不出地址。這點不要假裝：選到沒地址的場館就是沒地址，不要填一個猜的進去。
 *
 * ## [count] 決定排序
 * 常去的場館排前面。打「小巨蛋」時先出現你去過十次的那個，
 * 而不是資料庫裡剛好名字像的另一個。
 */
data class VenueHint(
    val name: String,
    val address: String? = null,
    val city: String? = null,
    /** 你在這個場館看過幾場。排序用。 */
    val count: Int = 0
) {
    /** 選單上第二行要顯示的字：有地址給地址，否則給縣市，都沒有就空白。 */
    val hintLine: String get() = address?.takeIf { it.isNotBlank() }
        ?: city?.takeIf { it.isNotBlank() }
        ?: ""
}

/**
 * 從演唱會紀錄整理出場館清單。
 *
 * 同一個場館會被填過很多次，地址有時填有時沒填——**取最後一次有填的那個**
 * （最新的資訊最可能是對的，例如場館搬家或當初填得比較潦草）。
 */
fun buildVenueHints(
    rows: List<Triple<String, String?, String?>>
): List<VenueHint> {
    val byName = LinkedHashMap<String, VenueHint>()
    rows.forEach { (rawName, address, city) ->
        val name = rawName.trim()
        if (name.isBlank()) return@forEach
        val prev = byName[name]
        byName[name] = VenueHint(
            name = name,
            // 後來填的覆蓋先前的，但空白不要覆蓋掉已經有的。
            address = address?.takeIf { it.isNotBlank() } ?: prev?.address,
            city = city?.takeIf { it.isNotBlank() } ?: prev?.city,
            count = (prev?.count ?: 0) + 1
        )
    }
    return byName.values.sortedByDescending { it.count }
}
