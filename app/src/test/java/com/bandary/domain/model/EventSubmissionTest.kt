package com.bandary.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 活動投稿／重複偵測／合併的規則。名稱比對網頁版（web/index.html）有同一份實作，
 * 這裡的名稱案例也用網頁那份跑過一次，兩邊結果要一致。
 */
class EventSubmissionTest {

    private val day = LocalDateTime(2026, 9, 20, 19, 0)

    private fun ev(id: String, name: String, dt: LocalDateTime = day, endDate: LocalDate? = null) =
        FreeEvent(id = id, name = name, dateTime = dt, endDate = endDate)

    // ── 名稱比對 ──

    @Test
    fun `正規化只留字母與數字 忽略大小寫空白標點`() {
        assertEquals("decajoins巡演", normalizeEventName("Deca Joins 巡演！"))
        assertEquals("", normalizeEventName("  —！？ "))
    }

    @Test
    fun `正規化後相同就算相似`() {
        assertTrue(isSimilarEventName("Deca Joins 巡演", "deca joins巡演"))
    }

    @Test
    fun `較短的至少四個字且被包含 算相似`() {
        assertTrue(isSimilarEventName("大港開唱", "2026 大港開唱 Day1"))
    }

    @Test
    fun `太短的通用詞不因為被包含就算相似`() {
        assertFalse(isSimilarEventName("演唱會", "五月天演唱會"))
    }

    @Test
    fun `錯字或異體字 兩字組相似度夠高就算相似`() {
        assertTrue(isSimilarEventName("落日飛車巡演台北", "落日飛車巡演臺北"))
    }

    @Test
    fun `完全不同的名稱不相似 空白名稱不相似`() {
        assertFalse(isSimilarEventName("Sunset Rollercoaster", "No Party For Cao Dong"))
        assertFalse(isSimilarEventName("", "大港開唱"))
    }

    // ── 找重複 ──

    @Test
    fun `名稱相似但不同天 不算重複`() {
        val cand = ev("new", "大港開唱")
        val pool = listOf(ev("a", "大港開唱", LocalDateTime(2026, 9, 21, 19, 0)))
        assertNull(findSimilarEvent(cand, pool))
    }

    @Test
    fun `多日活動區間重疊 算同一天`() {
        val cand = ev("new", "大港開唱", LocalDateTime(2026, 9, 21, 12, 0))
        val pool = listOf(ev("a", "2026 大港開唱", day, endDate = LocalDate(2026, 9, 22)))
        assertEquals("a", findSimilarEvent(cand, pool)?.id)
    }

    @Test
    fun `不會把自己當成重複`() {
        val self = ev("same", "大港開唱")
        assertNull(findSimilarEvent(self, listOf(self)))
    }

    // ── 演出轉投稿 ──

    @Test
    fun `投稿只帶公開資訊 不帶照片座位心得同行者`() {
        val c = Concert(
            name = "巡演台北站", artistName = "落日飛車", venue = " Zepp ", city = "台北",
            dateTime = day, ticketPrice = 1800.0,
            ticketUrl = "\nhttps://tix.example/a\nhttps://tix.example/b",
            infoUrl = "https://info.example",
            notes = "超好聽", seatInfo = "A區12排", photoUri = "file:///p.jpg",
            photoUris = listOf("file:///q.jpg"), companions = listOf("朋友"), rating = 5,
            festivals = listOf(
                FestivalSlot("", "", "", "告五人"),
                FestivalSlot("", "", "", "嘉賓甲", feat = true, featOf = "落日飛車")
            )
        )
        val s = c.toEventSubmission()!!
        assertEquals("巡演台北站", s.name)
        assertEquals("落日飛車 / 告五人", s.artistName)
        assertEquals(mapOf("落日飛車" to listOf("嘉賓甲")), s.guestsByArtist)
        assertEquals("Zepp", s.venue)
        // 票價**刻意不照抄**（使用者要求）：1800 是投稿者買的那張，
        // 不代表這場只有那一個價位。記成 -1.0＝「付費但金額未知」，
        // 實際金額留給管理員或爬蟲補。詳見 EventSubmissionPriceTest。
        assertEquals(-1.0, s.ticketPrice!!, 0.0)
        assertEquals("https://tix.example/a", s.ticketUrl)
        assertEquals("https://info.example", s.sourceUrl)
        assertEquals(FreeEventStatus.PENDING, s.status)
        assertNull(s.note)
        assertTrue(s.imageUris.isEmpty())
        assertNull(s.imageUri)
    }

    @Test
    fun `票價零視為免費 名稱空白時用第一位藝人 都空白就不投稿`() {
        val free = Concert(artistName = "草東沒有派對", dateTime = day, ticketPrice = 0.0).toEventSubmission()!!
        assertNull(free.ticketPrice)
        assertEquals("草東沒有派對", free.name)
        assertNull(Concert(dateTime = day).toEventSubmission())
    }

    // ── 合併 ──

    @Test
    fun `合併沿用主活動身分 空欄位補上 圖片取聯集最多五張`() {
        val primary = FreeEvent(
            id = "p", name = "大港開唱", artistName = "A", dateTime = day,
            imageUris = listOf("i1", "i2", "i3"), createdBy = "admin@x", status = FreeEventStatus.APPROVED
        )
        val other = FreeEvent(
            id = "o", name = "2026 大港開唱", artistName = "a / B", venue = "駁二", dateTime = day,
            ticketPrice = 2500.0, imageUris = listOf("i3", "i4", "i5", "i6")
        )
        val m = mergeFreeEvents(primary, listOf(other))
        assertEquals("p", m.id)
        assertEquals("大港開唱", m.name)
        assertEquals("admin@x", m.createdBy)
        assertEquals(FreeEventStatus.APPROVED, m.status)
        assertEquals("A / B", m.artistName)          // 大小寫不同視為同一位
        assertEquals("駁二", m.venue)
        assertEquals(2500.0, m.ticketPrice!!, 0.0)
        assertEquals(listOf("i1", "i2", "i3", "i4", "i5"), m.imageUris)
        assertEquals("i1", m.imageUri)
    }

    @Test
    fun `合併票價 有金額優先 其次付費未知 都沒有才是免費`() {
        val unknownPaid = FreeEvent(id = "a", name = "x", dateTime = day, ticketPrice = -1.0)
        val free = FreeEvent(id = "b", name = "x", dateTime = day)
        assertEquals(-1.0, mergeFreeEvents(free, listOf(unknownPaid)).ticketPrice!!, 0.0)
        assertNull(mergeFreeEvents(free, listOf(free.copy(id = "c"))).ticketPrice)
    }

    @Test
    fun `手動合併挑資料最完整的當主活動 一樣完整挑最早建立`() {
        val sparse = FreeEvent(id = "s", name = "x", dateTime = day, createdAt = 1)
        val rich = FreeEvent(id = "r", name = "x", dateTime = day, venue = "v", artistName = "a", createdAt = 9)
        assertEquals("r", pickMergePrimary(listOf(sparse, rich))?.id)
        val older = sparse.copy(id = "old", createdAt = 1)
        val newer = sparse.copy(id = "new", createdAt = 5)
        assertEquals("old", pickMergePrimary(listOf(newer, older))?.id)
    }

    // ── 編輯頁相簿 ──

    @Test
    fun `封面不在相簿裡就補到最前面 內建底圖與已在相簿的不補`() {
        val builtin = setOf("builtin://1")
        assertEquals(listOf("cover", "a"), albumIncludingCover("cover", listOf("a")) { it in builtin })
        assertEquals(listOf("a", "cover"), albumIncludingCover("cover", listOf("a", "cover")) { it in builtin })
        assertEquals(listOf("a"), albumIncludingCover("builtin://1", listOf("a")) { it in builtin })
        assertEquals(listOf("a"), albumIncludingCover(null, listOf("a")) { false })
    }
}
