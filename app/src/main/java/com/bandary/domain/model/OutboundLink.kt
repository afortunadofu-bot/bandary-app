package com.bandary.domain.model

/**
 * 所有「會把人帶出 App」的連結，集中在這裡。
 *
 * ## 為什麼要有一份清單
 * 這些連結散在三個地方：品牌頁（其他作品）、贊助入口、未來可能再加的工具推薦。
 * 如果各寫各的，就會發生「加了連結忘了加追蹤」或「後台的名字跟畫面上的不一樣」——
 * 而那兩件事都不會報錯，只會讓數據悄悄少一塊或對不起來。
 *
 * 清單是單一真相來源：**畫面照它渲染、點擊照它記錄、後台照它分組**。
 * 加一個新連結只要在這裡加一筆，三邊自動都有。
 *
 * ## [id] 不可以改
 * 它是 Firestore 文件 id 的一部分（`outbound_clicks/{installId}_{id}`），
 * 改了等於把過去的數據切斷，後台會看到一個舊名字歸零、一個新名字從零開始。
 * 顯示名稱 [title] 想怎麼改都可以，改那個不影響數據。
 */
data class OutboundLink(
    /** 統計用的固定代號。**上線後不要改**（見類別說明）。 */
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String,
    /** 品牌頁要不要列出來。贊助入口在「支持與回饋」有自己的位置，不重複列。 */
    val onBrandPage: Boolean = true
)

object OutboundLinks {

    /** 小額贊助。入口在「我的 →【支持與回饋】」，不列進品牌頁。 */
    const val DONATE = "donate"

    // 其餘 id 也給常數。理由是 BrandFooter 要照 id 取網址，
    // 而字面字串打錯不會編譯失敗——只會讓那一列的連結變成空的，
    // 而且要等有人在實機上點到才發現。
    const val BIRTHDAY = "birthday"
    const val RELATESTATS = "relatestats"
    const val BANDARY_PLAY = "bandary_play"

    val ALL: List<OutboundLink> = listOf(
        OutboundLink(
            id = BIRTHDAY,
            title = "壽星日",
            subtitle = "生日當天有哪些店家優惠，一頁看完",
            url = "https://birthdaydeals.celine-fu-work.workers.dev/"
        ),
        OutboundLink(
            id = RELATESTATS,
            title = "RelateStats",
            subtitle = "把你的人際關係變成看得懂的數據",
            url = "https://play.google.com/store/apps/details?id=com.relatestats.app"
        ),
        OutboundLink(
            id = BANDARY_PLAY,
            title = "Bandary 聽團曆",
            subtitle = "就是你正在用的這個 ‧ 到商店頁給個評分或分享給朋友",
            url = "https://play.google.com/store/apps/details?id=com.bandary.app"
        ),
        OutboundLink(
            id = DONATE,
            title = "請開發者喝杯咖啡",
            subtitle = "站外贊助",
            url = "https://buymeacoffee.com/celinefu",
            onBrandPage = false
        )
    )

    /** 品牌頁要列的（依上面的順序）。 */
    val brandPage: List<OutboundLink> get() = ALL.filter { it.onBrandPage }

    fun byId(id: String): OutboundLink? = ALL.firstOrNull { it.id == id }
}
