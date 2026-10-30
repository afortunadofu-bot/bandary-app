package com.bandary.domain.model

/**
 * 功能許願：使用者提出想要的功能，其他人看得到也能按讚。
 *
 * 跟「意見回饋」的差別：回饋是寫給開發者看的私訊（只有管理員讀得到），
 * 許願是公開的——大家看得到彼此想要什麼，按讚數就是排序依據。
 *
 * 存在頂層 collection `feature_wishes/{id}`，按讚記在子集合 `likes/{uid}`
 * （用文件 id＝uid 保證一人一票），`likeCount` 是冗餘的計數欄位，按讚時一起更新，
 * 這樣列表排序不必把每個人的 likes 子集合都讀一遍。
 */
data class FeatureWish(
    val id: String = "",
    val title: String = "",
    val detail: String = "",
    val authorUid: String = "",
    val authorName: String = "",          // 暱稱；沒設就用「匿名」
    val likeCount: Int = 0,
    /** 留言數。冗餘計數，跟 likeCount 同一套理由：列表不必為了顯示數字去讀每篇的留言。 */
    val commentCount: Int = 0,
    val status: WishStatus = WishStatus.OPEN,
    /**
     * 管理員置頂。讚數排序是民主的，但有些許願是「我們真的要做這個」——
     * 讓管理員把它標出來釘在最上面，使用者才知道哪些已經被接住了。
     */
    val pinned: Boolean = false,
    val appVersion: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** 我有沒有按過讚。只在本機判斷用，不寫進雲端。 */
    val likedByMe: Boolean = false
)

/**
 * 許願底下的一則留言。
 *
 * 為什麼要留言而不只是按讚：按讚只能表達「我也要」，說不出「我想要的是這個變形」
 * 或「這個用現有功能就做得到」。許願區沒有討論就只是一面投票牆。
 *
 * 存在 `feature_wishes/{wishId}/comments/{id}`。留言本身也能按讚——
 * 一串討論裡哪個說法最多人認同，是比留言順序更有用的資訊。
 */
data class WishComment(
    val id: String = "",
    val wishId: String = "",
    val text: String = "",
    val authorUid: String = "",
    val authorName: String = "匿名",
    val likeCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 我有沒有按過讚。本機判斷用，不寫進雲端。 */
    val likedByMe: Boolean = false
)

/** 許願的處理狀態，由管理員更新。 */
enum class WishStatus(val label: String) {
    OPEN("徵求中"),
    PLANNED("已排進計畫"),
    DONE("已完成"),
    DECLINED("暫不考慮");

    companion object {
        fun from(name: String?): WishStatus =
            entries.firstOrNull { it.name == name } ?: OPEN
    }
}

fun FeatureWish.toCloudMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "detail" to detail,
    "authorUid" to authorUid,
    "authorName" to authorName,
    "likeCount" to likeCount,
    "commentCount" to commentCount,
    "status" to status.name,
    "pinned" to pinned,
    "appVersion" to appVersion,
    "createdAt" to createdAt
)
