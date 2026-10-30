package com.bandary.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 活動圖排序用的佔位符。
 *
 * 這組解析寫錯不會編譯失敗、也不會丟例外——只會讓圖片默默排錯或整張消失，
 * 而且要等使用者存檔後才看得出來。v1.57.0 第一版就把索引解析寫錯過
 * （只去前綴會剩 "3__"，toIntOrNull() 是 null，那張圖會直接被丟掉）。
 */
class FreeEventImageOrderTest {

    @Test fun `佔位符可以正確還原成索引`() {
        assertEquals(0, FreeEventsRepository.newImageIndex(FreeEventsRepository.newImagePlaceholder(0)))
        assertEquals(3, FreeEventsRepository.newImageIndex(FreeEventsRepository.newImagePlaceholder(3)))
        assertEquals(12, FreeEventsRepository.newImageIndex(FreeEventsRepository.newImagePlaceholder(12)))
    }

    @Test fun `佔位符帶有前後綴`() {
        assertEquals("__NEW_2__", FreeEventsRepository.newImagePlaceholder(2))
    }

    @Test fun `不是佔位符的字串回傳 null`() {
        assertNull(FreeEventsRepository.newImageIndex("iVBORw0KGgo="))
        assertNull(FreeEventsRepository.newImageIndex("__NEW___"))
        assertNull(FreeEventsRepository.newImageIndex(""))
    }

    @Test fun `既有圖片的 Base64 不會被誤判成佔位符`() {
        // Base64 內容本來就可能出現底線，但不會以 __NEW_ 開頭。
        val base64 = "__NOT_A_PLACEHOLDER__"
        assertEquals(false, base64.startsWith(FreeEventsRepository.NEW_IMAGE_PREFIX))
    }
}
