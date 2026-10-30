package com.bandary.data.util

/**
 * 從別的 App「分享」進來的一段文字，等著被新增演唱會的表單接走。
 *
 * ## 為什麼是一個全域的暫存，而不是導覽參數
 * 分享進來的是**整封信**——訂票確認信動輒好幾千字，還含有換行、`&`、`#` 這些
 * 會把網址型的導覽路由弄壞的字元。塞進 `add_show?shared=...` 要先 URL 編碼，
 * 長度也可能超過系統限制，而且那串東西會留在返回堆疊裡。
 *
 * 一次性的暫存簡單得多：[set] 放進來、[take] 拿走就清空。
 * 清空是重點——不清的話轉螢幕或返回重進表單會再套用一次。
 *
 * ## 為什麼不做成 ViewModel 的狀態
 * 寫的人是 Activity（收到 Intent 的地方），讀的人是另一個畫面的 ViewModel，
 * 兩者沒有共同的 scope。這種「跨 Intent 的一次性交接」用暫存是標準做法。
 */
object SharedTicketText {

    @Volatile
    private var pending: String? = null

    fun set(text: String?) {
        pending = text?.takeIf { it.isNotBlank() }
    }

    /** 取出並清空。重複呼叫只有第一次拿得到東西。 */
    fun take(): String? {
        val t = pending
        pending = null
        return t
    }
}
