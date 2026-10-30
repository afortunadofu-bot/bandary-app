package com.bandary.data.remote

/**
 * 把雲端函式（callable）丟回來的錯誤翻成**一句能定位原因**的話。
 *
 * 為什麼需要這個東西：Spotify 搜尋與「找海報」兩支都是呼叫 Cloud Function，
 * 失敗時原本的處理是
 *   - Spotify：畫面只說「暫時無法使用」；
 *   - 圖片搜尋：連狀態都沒有，只 `Log.w` 進 logcat。
 * 結果實機上這兩個功能壞掉的樣子，跟「真的沒有結果」長得一模一樣
 * ——「找海報」甚至會理直氣壯地說「搜過『某某』都沒有結果」，那是假話。
 * 使用者手上沒有 logcat，看雲端 log 又只有我能看，於是回報只能停在
 * 「還沒好」，誰都不知道是沒登入、沒部署、金鑰沒設、還是額度用完。
 *
 * 伺服器端其實**已經**把原因寫在 HttpsError 的訊息裡（`Spotify token HTTP 400`、
 * `圖片搜尋今天的額度用完了`…），那串字會原封傳回用戶端。這支就是把它
 * 撿起來、補上「所以要去做什麼」，再交給畫面顯示。
 *
 * 訊息裡不會有金鑰：伺服器只回 HTTP 狀態碼，金鑰留在 Secret Manager。
 */
object CallableFailure {

    /**
     * 純文字版本（方便測試）：給 callable 的錯誤代碼名稱與伺服器訊息，回一句話。
     *
     * @param code [com.google.firebase.functions.FirebaseFunctionsException.Code] 的名稱，
     *   例如 `UNAUTHENTICATED`；不是 callable 的錯誤（網路斷線等）傳 null。
     * @param message 伺服器寫的訊息，或例外本身的訊息。
     */
    fun describe(code: String?, message: String?): String {
        val msg = message?.trim().orEmpty()

        // ── 先看伺服器訊息裡的 HTTP 狀態碼 ──────────────────────
        // 這是最有用的一層：同一個 UNAVAILABLE，400 跟 503 要做的事完全不同。
        // 400/401 幾乎一定是「金鑰沒設或設錯」，而那只有專案擁有者能修；
        // 503 是對方暫時掛掉，等一下就好。分不開的話就會白花時間找錯方向。
        httpHint(msg)?.let { return it }

        return when (code) {
            // 代理刻意只開給登入者用，不然就變成公開的免費 API。
            "UNAUTHENTICATED" -> "要先登入才能用（這項功能只開給登入者）"
            // 函式根本不在雲端上——這是「寫好但沒部署」的樣子。
            "NOT_FOUND", "UNIMPLEMENTED" -> "雲端函式還沒部署（要跑 firebase deploy --only functions）"
            "PERMISSION_DENIED" -> "雲端函式拒絕這次呼叫（權限設定）"
            "RESOURCE_EXHAUSTED" -> msg.ifBlank { "今天的額度用完了，明天會重置" }
            "DEADLINE_EXCEEDED" -> "等太久沒有回應（網路慢，或雲端函式冷啟動）"
            "UNAVAILABLE" -> msg.ifBlank { "雲端函式暫時連不上" }
            "INTERNAL" -> if (msg.isBlank()) "雲端函式執行時出錯" else "雲端函式出錯：$msg"
            "INVALID_ARGUMENT" -> if (msg.isBlank()) "送出去的參數不對" else "參數不對：$msg"
            null -> if (msg.isBlank()) "連不上網路" else msg
            else -> if (msg.isBlank()) code else "$code：$msg"
        }
    }

    /**
     * 伺服器訊息長這樣：`Spotify token HTTP 400`、`Custom Search HTTP 403`
     *（見 functions/index.js）。把狀態碼翻成「該去修什麼」。
     */
    private fun httpHint(msg: String): String? {
        val status = Regex("""HTTP (\d{3})""").find(msg)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val spotify = msg.contains("Spotify", ignoreCase = true)
        val who = if (spotify) "Spotify" else "圖片搜尋"
        return when (status) {
            400, 401 -> if (spotify)
                "Spotify 金鑰無效或沒設定（伺服器拿不到 token，HTTP $status）"
            else
                "圖片搜尋的設定不對（HTTP $status，多半是搜尋引擎 ID 填錯）"
            403 -> if (spotify)
                "Spotify 拒絕這次查詢（HTTP 403）"
            else
                "圖片搜尋的 API 金鑰被拒（HTTP 403：金鑰無效、已刪除，或額度用完）"
            429 -> "$who 今天的額度用完了（HTTP 429）"
            404 -> "$who 的網址找不到（HTTP 404）"
            in 500..599 -> "$who 那邊暫時掛掉（HTTP $status），等一下再試"
            else -> "$who 回了 HTTP $status"
        }
    }

    /**
     * 從真的丟出來的例外取出原因。
     *
     * **一定要先拆殼**：這兩支都用 `Tasks.await()`，失敗時外面包的是
     * `ExecutionException`，它的 `message` 是內層例外的 `toString()`
     *（`com.google.firebase.functions.FirebaseFunctionsException: ...`）。
     * 直接拿 `message` 顯示，使用者會看到一串類別名稱，而 code 整個拿不到。
     */
    fun of(t: Throwable?): String? {
        val e = unwrap(t) ?: return null
        val code = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.code?.name
        return describe(code, e.message)
    }

    private fun unwrap(t: Throwable?): Throwable? {
        var cur = t
        var hops = 0
        while (cur != null && hops < 5) {
            if (cur is java.util.concurrent.ExecutionException ||
                cur is java.util.concurrent.CompletionException
            ) {
                cur = cur.cause ?: return cur
                hops++
            } else {
                return cur
            }
        }
        return cur
    }
}
