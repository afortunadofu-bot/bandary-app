package com.bandary.domain.model

/**
 * 場館座位視野圖：某個位子實際看出去長什麼樣。
 *
 * ## 為什麼是「使用者上傳」而不是「找現成的座位圖」
 * 這件事在待辦上掛了很久，卡在版權：場館的官方座位圖、售票網站的視野圖都是別人的作品，
 * 抓進 App 裡再散給所有使用者，不是「參考」而是重製散布。
 *
 * 使用者自己在座位上拍的照片沒有這個問題——那是他自己拍的，他可以決定要不要分享。
 * 而且實際上**更有用**：官方座位圖只告訴你座位在平面圖的哪一格，
 * 自己拍的照片才看得到「這個角度會不會被柱子擋住」「看不看得到大螢幕」。
 *
 * ## 比對鍵
 * 場館名稱的寫法很亂（「臺北小巨蛋」「台北小巨蛋」「小巨蛋」），
 * 所以查詢一律用 [venueKey]，顯示才用原字串。
 */
data class VenueView(
    val id: String = "",
    /** 場館原始名稱，顯示用。 */
    val venue: String = "",
    /** 比對鍵，查詢用。寫入時一起存，雲端才查得動。 */
    val venueKey: String = "",
    /** 區域 / 看台，例如「3A 區」「搖滾區」「2 樓 B 排」。自由文字——各場館的叫法差太多。 */
    val section: String = "",
    /** 一句話心得：「柱子會擋到左半邊」這種才是真正有用的資訊。 */
    val note: String = "",
    /** 圖片：Base64（與活動圖同一套），或舊資料的下載網址。 */
    val image: String = "",
    val createdBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** pending / approved / rejected。與活動投稿同一套，人留在迴圈裡把關。 */
    val status: String = VenueViewStatus.PENDING
)

object VenueViewStatus {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val REJECTED = "rejected"

    fun label(value: String): String = when (value) {
        PENDING -> "審核中"
        REJECTED -> "未通過"
        else -> "已通過"
    }
}

/**
 * 場館比對鍵：去空白、去全半形差異、拿掉「臺/台」這種常見異體字，轉小寫。
 *
 * 不做模糊比對——「小巨蛋」與「臺北小巨蛋」會被當成兩個場館。
 * 寧可分開，也不要把高雄巨蛋的視野圖配到台北小巨蛋上：
 * 這個功能的全部價值就在於「我那個位子看出去真的長這樣」，配錯比沒有更糟。
 */
fun venueKey(name: String): String =
    name.trim()
        .replace("臺", "台")
        .replace(Regex("""[\s　()（）\-－_]"""), "")
        .lowercase()
