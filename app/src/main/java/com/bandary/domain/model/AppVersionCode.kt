package com.bandary.domain.model

/**
 * `versionName` → `versionCode`，**與 app/build.gradle.kts 同一套公式**。
 *
 * ## 為什麼要獨立成一個函式
 * 這段公式原本在 `AdminDashboardViewModel.saveLatestVersion` 裡抄了一份，用的是舊式
 * `MAJOR * 10000 + MINOR * 100 + PATCH`；而 build.gradle.kts 早就改成
 * `MAJOR * 1000000 + MINOR * 1000 + PATCH`（舊式下 `1.100.0` 會撞到 `2.0.0`）。
 *
 * 兩邊不一致的後果不是「數字難看」，而是**更新提示永遠不會亮**：
 * 後台公告 `1.108.0` 存成 `20800`，實機的 versionCode 是 `1108000`，
 * 判斷式 `雲端版號 > 本機版號` 永遠不成立。管理員按了「公告」，
 * 使用者端什麼都不會發生——而且不會報錯，只會安靜地沒反應，光看畫面查不出來。
 *
 * 抄一份公式就會有抄錯的那一天。所以只留一份，並且用
 * `AppVersionCodeTest` 把它跟 build.gradle.kts 綁在一起：
 * 哪天有人只改其中一邊，測試會紅。
 */
object AppVersionCode {

    /**
     * 回傳 [versionName] 對應的 versionCode，格式不合或超出範圍回 `null`。
     *
     * 接受開頭的 `v` / `V`（管理員常常會順手打 "v1.108.0"）。
     * MINOR 與 PATCH 各限 0–999，與 build.gradle.kts 的 `require` 一致——
     * 超過就會進位到上一層，`1.0.1000` 會變成 `1.1.0`，那是錯的版號而不是大一點的版號。
     */
    fun of(versionName: String): Long? {
        val parts = versionName.trim().removePrefix("v").removePrefix("V").split(".")
        if (parts.size != 3) return null
        val nums = parts.map { it.trim().toIntOrNull() ?: return null }
        val (mj, mn, pt) = nums
        if (mj < 0 || mn !in 0..999 || pt !in 0..999) return null
        return mj * 1_000_000L + mn * 1_000L + pt
    }
}
