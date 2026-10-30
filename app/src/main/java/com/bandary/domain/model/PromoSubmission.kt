package com.bandary.domain.model

/**
 * 推廣任務：使用者發文推薦 App、貼文達到一定讚數，管理員審核通過後給試用期。
 *
 * 為什麼要人工審核：「有沒有真的發文」「那篇有幾個讚」App 端驗不了——
 * 按一下分享鈕就送試用期等於無限續杯。改成送出貼文連結、管理員點進去看，
 * 才是真的驗得到的做法。
 *
 * 讚數欄位是**使用者自填的**，只當排序與初步篩選用；
 * 真正的判斷是管理員點開連結看到的數字。UI 上要把這件事講清楚，
 * 不要讓人以為填一個大數字就會過。
 */
data class PromoSubmission(
    val id: String = "",
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    /** Threads / Instagram / X / 其他。自由文字，不做成列舉——平台會變。 */
    val platform: String = "",
    val postUrl: String = "",
    /** 使用者自填的讚數。 */
    val claimedLikes: Int = 0,
    val note: String = "",
    val status: PromoStatus = PromoStatus.PENDING,
    /** 管理員的審核意見（駁回原因 / 核准備註）。 */
    val reviewComment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long = 0L
)

enum class PromoStatus(val label: String) {
    PENDING("審核中"),
    APPROVED("已通過"),
    REJECTED("未通過");

    companion object {
        fun from(name: String?): PromoStatus =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}

/**
 * 目前的過關門檻。放在程式裡而不是雲端設定：這是會寫進說明文字、
 * 跟著行銷檔期一起改的東西，改它本來就該伴隨一次改版說明。
 */
const val PROMO_MIN_LIKES = 30

/** 通過一次給幾天試用。 */
const val PROMO_TRIAL_DAYS = 30

/**
 * 使用者目前的推廣獎勵。存在 `promo_rewards/{uid}`，**只有管理員寫得進去**。
 *
 * 為什麼不跟其他權限一樣放 users/{uid}：那份文件本人可以寫，
 * 等於自己就能把試用期加到十年後。獎勵一定要放在本人寫不到的地方。
 */
data class PromoReward(
    val uid: String = "",
    /** 試用期到哪一刻（epoch millis）。0 = 沒有。 */
    val trialUntil: Long = 0L,
    val grantedBy: String = "",
    val grantedAt: Long = 0L,
    val reason: String = ""
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean = trialUntil > now

    /** 還剩幾天（無條件進位，剩幾小時也要算一天，不然最後一天會顯示 0）。 */
    fun daysLeft(now: Long = System.currentTimeMillis()): Int {
        if (trialUntil <= now) return 0
        val ms = trialUntil - now
        val day = 24L * 60 * 60 * 1000
        return ((ms + day - 1) / day).toInt()
    }
}
