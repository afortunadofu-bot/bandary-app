package com.bandary.domain.model

/**
 * 邀請碼：每個帳號一組固定的 6 碼英數。
 *
 * 刻意用 uid 推導而不是另外存一份：
 *   - 不用擔心「碰撞了要重發」「換裝置後對不上」「雲端那份被誤刪」。
 *   - 使用者隨時算得出自己的碼，不需要先連上網。
 *
 * 字母表拿掉了 0/O、1/I/L、2/Z、5/S、8/B——邀請碼會被手打與口述，
 * 這幾組是最常被聽錯與看錯的。剩下 26 個字元，6 碼約 3 億組，對這個規模綽綽有餘。
 */
object InviteCode {

    /** 去掉易混淆字元後的字母表。順序固定，改動會讓所有既有邀請碼失效。 */
    const val ALPHABET = "ACDEFGHJKMNPQRTUVWXY34679"

    const val LENGTH = 6

    /**
     * uid → 邀請碼。同一個 uid 永遠得到同一組碼。
     *
     * 用 SHA-1 而不是 hashCode()：hashCode 只有 32 bits 而且分布很差，
     * 相近的 uid 會產生相近的碼，看起來就像有規律。
     */
    fun of(uid: String): String {
        if (uid.isBlank()) return ""
        val digest = java.security.MessageDigest.getInstance("SHA-1")
            .digest(uid.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(LENGTH)
        for (i in 0 until LENGTH) {
            // toInt() and 0xFF：Kotlin 的 Byte 有號，直接取模會出現負的索引。
            val v = digest[i].toInt() and 0xFF
            sb.append(ALPHABET[v % ALPHABET.length])
        }
        return sb.toString()
    }

    /**
     * 把使用者輸入的碼正規化：去空白、轉大寫，並修掉最常見的手打錯誤
     * （O→0 那類字元根本不在字母表裡，所以是反過來修：把 0 當成 O 之類）。
     */
    fun normalize(raw: String): String {
        val up = raw.trim().uppercase().filter { !it.isWhitespace() && it != '-' }
        return buildString {
            for (ch in up) {
                val fixed = when (ch) {
                    '0', 'O' -> 'Q'   // 0/O 都不在字母表，最接近的是 Q
                    '1', 'I', 'L' -> 'J'
                    '2', 'Z' -> '3'
                    '5', 'S' -> 'X'
                    '8', 'B' -> 'A'
                    else -> ch
                }
                if (fixed in ALPHABET) append(fixed)
            }
        }.take(LENGTH)
    }

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }

    /** 分享用的網址。帶 ref 參數，網頁端可以讀出來記在註冊流程上。 */
    fun shareUrl(code: String): String = "https://bandary.web.app/?ref=$code"

    /** 從分享網址反解出邀請碼；解不出來回 null（不要硬猜）。 */
    fun fromUrl(url: String): String? {
        val m = Regex("""[?&]ref=([A-Za-z0-9]+)""").find(url) ?: return null
        return m.groupValues[1].uppercase().takeIf { isValid(it) }
    }
}
