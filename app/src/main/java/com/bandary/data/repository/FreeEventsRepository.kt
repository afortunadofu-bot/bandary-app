package com.bandary.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.bandary.data.remote.FirestoreService
import com.bandary.domain.model.Concert
import com.bandary.domain.model.ConcertType
import com.bandary.domain.model.FreeEvent
import com.bandary.domain.model.FreeEventStatus
import com.bandary.domain.model.TicketStatus
import com.bandary.domain.model.images
import com.bandary.domain.model.toEventSubmission
import kotlinx.datetime.toLocalDateTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * 免費活動：管理員新增 / 從日曆匯入，存進 Firestore 共用 collection；
 * 一般使用者讀取後可一鍵複製成自己的演唱會。
 *
 * 活動圖不走 Firebase Storage（需付費方案），改在本機**縮圖 + JPEG 壓縮 + Base64**
 * 後直接存進 Firestore 文件的 imageUri 欄位（免費方案即可；單張壓到 ~100KB 內，遠低於
 * Firestore 單文件 1MiB 上限）。
 */
@Singleton
class FreeEventsRepository @Inject constructor(
    private val firestore: FirestoreService,
    private val concertRepo: ConcertRepository,
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val MAX_IMAGES = 5   // 活動圖上限
        /**
         * 「還沒上傳的新圖」在排序清單裡的佔位符：__NEW_0__、__NEW_1__…
         * 有了它，編輯畫面就能讓新舊圖混在一起自由排序，而不是新圖一律被推到最後。
         * 存檔時會換成實際編碼後的圖，不會留在雲端資料裡。
         */
        const val NEW_IMAGE_PREFIX = "__NEW_"
        private const val NEW_IMAGE_SUFFIX = "__"
        fun newImagePlaceholder(index: Int) = "$NEW_IMAGE_PREFIX$index$NEW_IMAGE_SUFFIX"
        /** 從 __NEW_3__ 取出 3。前後綴都要去掉——只去前綴會剩 "3__"，toIntOrNull() 會是 null。 */
        fun newImageIndex(placeholder: String): Int? =
            placeholder.removePrefix(NEW_IMAGE_PREFIX).removeSuffix(NEW_IMAGE_SUFFIX).toIntOrNull()
    }

    private val _events = MutableStateFlow<List<FreeEvent>>(emptyList())
    val events: StateFlow<List<FreeEvent>> = _events.asStateFlow()    // 已核准（所有人可見）

    private val _pending = MutableStateFlow<List<FreeEvent>>(emptyList())
    val pending: StateFlow<List<FreeEvent>> = _pending.asStateFlow()  // 待審核（管理員可見）

    private val _mine = MutableStateFlow<List<FreeEvent>>(emptyList())
    val mine: StateFlow<List<FreeEvent>> = _mine.asStateFlow()        // 我的投稿（任何狀態）

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    suspend fun refresh() {
        if (_loading.value) return
        _loading.update { true }
        firestore.fetchFreeEvents()
            .onSuccess { list ->
                // 依「名稱 + 日期時間」去重，避免重複匯入造成清單重複
                val approved = list.filter { it.status == FreeEventStatus.APPROVED }
                    .distinctBy { it.name.trim().lowercase() + "|" + it.dateTime }
                _events.update { approved }
                _pending.update { list.filter { it.status == FreeEventStatus.PENDING } }
                val myEmail = firestore.currentEmail()?.lowercase()
                _mine.update {
                    if (myEmail == null) emptyList()
                    else list.filter { it.createdBy?.lowercase() == myEmail }.sortedByDescending { it.createdAt }
                }
            }
            .onFailure { e -> _message.update { "讀取失敗：${e.message}" } }
        _loading.update { false }
    }

    /**
     * 管理員新增活動（直接核准）。若帶本機圖片 [imageLocalUri]，先上傳 Firebase Storage 取得公開網址
     * 再寫入，這樣其他使用者才看得到圖（本機 URI 對別人無效）。上傳失敗仍會以無圖方式新增。
     */
    suspend fun add(event: FreeEvent, imageLocalUris: List<Uri> = emptyList()) {
        save(event.copy(status = FreeEventStatus.APPROVED), imageLocalUris, "已新增「${event.name}」")
    }

    /** 一般使用者投稿活動，狀態為待審核，核准後才會公開。 */
    suspend fun submit(event: FreeEvent, imageLocalUris: List<Uri> = emptyList()) {
        save(event.copy(status = FreeEventStatus.PENDING), imageLocalUris, "已送出「${event.name}」，待管理員審核")
    }

    /** 管理員編輯既有活動內容（保留 id / 狀態 / 投稿者；保留的舊圖放在 event.imageUris，新圖另外編碼附上）。 */
    suspend fun update(event: FreeEvent, imageLocalUris: List<Uri> = emptyList()) {
        // 先留修改紀錄再存：存完就拿不到「改之前」了。
        // 內容沒變時 recordEventRevision 自己會跳過，這裡不用再判斷一次。
        _events.value.firstOrNull { it.id == event.id }?.let { before ->
            firestore.recordEventRevision(before, event, "管理員編輯")
        }
        save(event, imageLocalUris, "已更新「${event.name}」")
    }

    /** 某個活動的修改紀錄（新的在上）。 */
    suspend fun revisions(eventId: String): List<com.bandary.domain.model.EventRevision> =
        firestore.fetchEventRevisions(eventId).getOrDefault(emptyList())

    /**
     * 回溯到某一筆修改之前的內容。回溯本身也是一次修改，所以同樣留紀錄——
     * 不然「誰把它改回去的」又變成沒人知道。
     */
    suspend fun restore(revision: com.bandary.domain.model.EventRevision) {
        val current = _events.value.firstOrNull { it.id == revision.eventId }
        if (current == null) {
            _message.update { "找不到這個活動，可能已被刪除" }
            return
        }
        val restored = firestore.revisionToEvent(revision, current)
        if (restored == null) {
            _message.update { "這筆紀錄的內容已無法還原" }
            return
        }
        firestore.recordEventRevision(current, restored, "回溯到 " + revision.changedBy + " 修改前的版本")
        save(restored, emptyList(), "已回溯「${restored.name}」")
    }

    /**
     * 這個活動上還沒審核的修改提案。所有人都看得到——
     * 不然同一筆錯誤資料會被不同人重複提案，管理員也得一筆筆讀重複的東西。
     */
    fun proposalsFor(eventId: String): List<FreeEvent> =
        _pending.value.filter { it.editOfId == eventId }

    /**
     * 一般使用者提出修改：不直接改原活動（規則不允許，也不該讓任何人改公開資料），
     * 而是新增一份 editOfId 指向原活動的待審核提案，管理員核准後才套用。
     */
    suspend fun proposeEdit(event: FreeEvent, imageLocalUris: List<Uri> = emptyList()) {
        val target = event.id
        if (target.isBlank()) return
        save(
            event.copy(
                id = "",                       // 空 id → Firestore 產生新文件，不會蓋掉原活動
                status = FreeEventStatus.PENDING,
                editOfId = target,
                createdBy = null,              // 由 addFreeEvent 填入提案者，不沿用原投稿者
                reviewComment = null
            ),
            imageLocalUris,
            "已送出「${event.name}」的修改，待管理員審核"
        )
    }

    /**
     * event.imageUris＝要保留的舊圖（已是 Base64/網址）；[imageLocalUris]＝這次新選的本機圖，逐張編碼後接在後面。
     * 合起來最多留 5 張；imageUri（單張）同步成第一張，舊版與只讀單張的地方才不會空白。
     */
    private suspend fun save(event: FreeEvent, imageLocalUris: List<Uri>, successMsg: String) {
        var toSave = event
        if (imageLocalUris.isNotEmpty()) {
            _loading.update { true }
            // 保持索引對齊：某張編碼失敗時後面幾張的位置不能跟著位移，否則佔位符會對到錯的圖。
            val encoded = imageLocalUris.map { encodeImage(it) }
            if (encoded.any { it == null }) _message.update { "部分圖片處理失敗，會以成功的那幾張送出" }
            // 呼叫端可以在 imageUris 裡放 __NEW_n__ 佔位符，表示「第 n 張新圖要排在這個位置」。
            // 沒有佔位符時沿用舊行為（新圖接在舊圖後面），舊的呼叫端不受影響。
            val hasPlaceholder = event.imageUris.any { it.startsWith(NEW_IMAGE_PREFIX) }
            val merged = if (hasPlaceholder) {
                event.imageUris.mapNotNull { u ->
                    if (u.startsWith(NEW_IMAGE_PREFIX))
                        encoded.getOrNull(newImageIndex(u) ?: -1)
                    else u
                }
            } else {
                event.imageUris + encoded.filterNotNull()
            }
            toSave = toSave.copy(imageUris = merged.take(MAX_IMAGES))
            _loading.update { false }
        } else {
            // 沒有新圖時也要濾掉殘留的佔位符，避免把 __NEW_0__ 當成圖片存進雲端。
            toSave = toSave.copy(imageUris = event.imageUris.filterNot { it.startsWith(NEW_IMAGE_PREFIX) })
        }
        toSave = toSave.copy(imageUri = toSave.imageUris.firstOrNull())
        firestore.addFreeEvent(toSave)
            .onSuccess { _message.update { successMsg }; refresh() }
            .onFailure { e -> _message.update { "送出失敗：${e.message}" } }
    }

    /** 縮圖 + JPEG 壓縮 + Base64，輸出可直接存進 Firestore 的字串。 */
    // 壓縮參數與實作已抽到 ImageStore.encodeForUpload：場館視野圖也要用同一套，
    // 兩份各自演化的話，同一個雲端集合裡會出現大小差十倍的圖。
    private suspend fun encodeImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        com.bandary.data.util.ImageStore.encodeForUpload(context, uri)
    }

    /**
     * 管理員核准投稿。
     * 若這筆是修改提案（editOfId 有值），核准＝把提案內容套回原活動、再刪掉提案本身，
     * 否則清單上會同時留著原活動與提案兩筆。
     */
    suspend fun approve(id: String) {
        val proposal = _pending.value.firstOrNull { it.id == id }
        val targetId = proposal?.editOfId?.takeIf { it.isNotBlank() }
        if (proposal != null && targetId != null) {
            val original = _events.value.firstOrNull { it.id == targetId }
            if (original == null) {
                _message.update { "原活動已不存在，無法套用這筆修改" }
                return
            }
            // 沿用原活動的 id / 投稿者 / 建立時間，只換使用者改過的內容。
            val applied = proposal.copy(
                id = targetId,
                status = FreeEventStatus.APPROVED,
                editOfId = null,
                reviewComment = null,
                createdBy = original.createdBy,
                createdAt = original.createdAt
            )
            firestore.recordEventRevision(
                original, applied,
                "核准了 " + (proposal.createdBy ?: "某位使用者") + " 的修改提案"
            )
            firestore.addFreeEvent(applied)
                .onSuccess {
                    firestore.deleteFreeEvent(id)
                    _message.update { "已套用修改到「${applied.name}」" }
                    refresh()
                }
                .onFailure { e -> _message.update { "套用失敗：${e.message}" } }
            return
        }
        firestore.approveFreeEvent(id)
            .onSuccess { _message.update { "已核准" }; refresh() }
            .onFailure { e -> _message.update { "核准失敗：${e.message}" } }
    }

    /**
     * 批次核准 / 駁回。
     *
     * 為什麼要獨立一支而不是在 UI 迴圈呼叫 approve()：
     * approve() 每成功一筆就 refresh() 一次（重抓整個 collection）。
     * 勾 24 筆等於同時射出 24 個協程、24 次全量重抓，`_pending` 在中途被反覆覆寫，
     * 後面的 approve() 查 `_pending` 已經查不到那筆；畫面則整個卡住，
     * 看起來就是「核准已選按不下去」（實機回報）。
     *
     * 這裡改成**循序**處理、最後只 refresh 一次，並回報成功幾筆。
     */
    suspend fun approveAll(ids: List<String>) = reviewAll(ids, approve = true, comment = null)

    suspend fun rejectAll(ids: List<String>, comment: String? = null) =
        reviewAll(ids, approve = false, comment = comment)

    private suspend fun reviewAll(ids: List<String>, approve: Boolean, comment: String?) {
        if (ids.isEmpty()) return
        _loading.update { true }
        var ok = 0
        for (id in ids) {
            val result = if (approve) firestore.approveFreeEvent(id)
            else firestore.rejectFreeEvent(id, comment?.trim()?.ifBlank { null })
            if (result.isSuccess) ok++
        }
        _loading.update { false }
        val verb = if (approve) "核准" else "駁回"
        _message.update {
            if (ok == ids.size) "已$verb ${ok} 筆"
            else "已$verb $ok 筆，${ids.size - ok} 筆失敗"
        }
        refresh()
    }

    /** 管理員駁回投稿（保留資料 + 可附意見，讓投稿者看得到）。 */
    suspend fun reject(id: String, comment: String? = null) {
        firestore.rejectFreeEvent(id, comment?.trim()?.ifBlank { null })
            .onSuccess { _message.update { "已駁回" }; refresh() }
            .onFailure { e -> _message.update { "駁回失敗：${e.message}" } }
    }

    suspend fun delete(id: String) {
        firestore.deleteFreeEvent(id)
            .onSuccess { refresh() }
            .onFailure { e -> _message.update { "刪除失敗：${e.message}" } }
    }

    /** 檢查我的演唱會裡是否已有同名 + 同日的活動（避免重複加入）。 */
    /** 自己的演唱會清單。活動篩選「我參加的」用：看哪些活動已經加進自己的演唱會。 */
    fun myConcerts(): kotlinx.coroutines.flow.Flow<List<Concert>> = concertRepo.getAllConcerts()

    suspend fun findDuplicateConcert(event: FreeEvent): Concert? {
        val all = runCatching { concertRepo.getAllConcerts().first() }.getOrDefault(emptyList())
        return all.firstOrNull {
            it.name.trim().equals(event.name.trim(), ignoreCase = true) &&
                it.dateTime.date == event.dateTime.date
        }
    }

    /**
     * 一鍵參加：複製成自己的演唱會（免費 / 待參加），活動圖全部帶過去。
     * 圖要同時放進相簿（photoUris），不能只寫封面欄：編輯頁的相簿只列 photoUris，
     * 以前只寫 photoUri 時使用者在編輯頁看不到這張圖，一上傳新圖設成封面，原本的圖就不再被
     * 任何欄位引用、等於不見了。
     */
    suspend fun join(event: FreeEvent) {
        val photos = event.images.mapNotNull { materializeImage(it) }
        val coverUri = photos.firstOrNull()
        val paid = (event.ticketPrice ?: 0.0) > 0.0
        concertRepo.saveConcert(
            Concert(
                id = UUID.randomUUID().toString(),
                name = event.name,
                artistName = event.artistName,
                venue = event.venue,
                city = event.city,
                dateTime = event.dateTime,
                endDate = event.endDate,   // 多日活動：加入我的演唱會時保留結束日
                endTime = event.endTime,
                // 付費活動→一般演唱會 + 無（還沒買）；免費→免費類型 + 已購票（免費靠票價 0 表示，不會標「還沒買票」）。
                type = if (paid) ConcertType.CONCERT else ConcertType.FREE,
                ticketStatus = if (paid) TicketStatus.NONE else TicketStatus.BOUGHT,
                ticketPrice = if (paid) event.ticketPrice else 0.0,
                ticketUrl = event.ticketUrl,
                notes = event.note,
                photoUri = coverUri,
                photoUris = photos
            )
        )
        _message.update { "已加入「${event.name}」到你的演唱會" }
    }

    /**
     * 把免費活動的圖（Base64 或舊版 http 網址）轉成演唱會封面可用的來源：
     * Base64 → 解碼寫進 app 內部檔案回傳 file:// URI；http 網址 → 直接沿用。
     * 寫進跟使用者自己上傳的照片同一個資料夾（filesDir/images，見 ImageStore），
     * 之後在編輯頁移除這張圖時，才會跟一般照片一樣被清掉，不會留下沒人引用的檔案。
     */
    private suspend fun materializeImage(data: String): String? = withContext(Dispatchers.IO) {
        if (data.isBlank()) return@withContext null
        if (data.startsWith("http", ignoreCase = true)) return@withContext data
        runCatching {
            val bytes = Base64.decode(data.substringAfter("base64,"), Base64.NO_WRAP)
            val dir = java.io.File(context.filesDir, "images").apply { mkdirs() }
            val file = java.io.File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { it.write(bytes) }
            Uri.fromFile(file).toString()
        }.onFailure { android.util.Log.w("FreeEventsRepository", "materializeImage failed", it) }
            .getOrNull()
    }

    /**
     * 使用者新增演出後，自動把這場的公開活動資訊送去「活動」頁審核（見 [Concert.toEventSubmission]
     * 帶哪些欄位）。整個是附帶動作、完全靜默：未登入、已經發生過的場次、同一天已有相似名稱的活動
     * （含自己送過、被駁回的）都直接略過；失敗也不打擾使用者，演出本身早就存好了。
     */
    suspend fun submitFromConcert(concert: Concert) {
        if (!firestore.isSignedIn() || concert.deletedAt != null) return
        // 使用者可在設定關掉；讀不到設定時當成關閉，寧可少送一筆也不要違反使用者的選擇。
        if (!firestore.fetchShareNewShowsToEvents().getOrDefault(false)) return
        val submission = concert.toEventSubmission() ?: return
        val today = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.of("Asia/Taipei")).date
        if ((submission.endDate ?: submission.dateTime.date) < today) return
        val existing = firestore.fetchFreeEvents().getOrNull() ?: return
        if (com.bandary.domain.model.findSimilarEvent(submission, existing) != null) return
        firestore.addFreeEvent(submission)
            .onFailure { android.util.Log.w("FreeEventsRepository", "submitFromConcert failed", it) }
    }

    /** 審核：把一筆投稿併入已公開的活動（補上缺的欄位與圖片），再刪掉投稿本身。 */
    suspend fun mergeInto(pendingId: String, targetId: String) {
        val submission = _pending.value.firstOrNull { it.id == pendingId } ?: return
        val target = _events.value.firstOrNull { it.id == targetId }
        if (target == null) {
            _message.update { "原活動已不存在，無法併入" }
            return
        }
        firestore.addFreeEvent(com.bandary.domain.model.mergeFreeEvents(target, listOf(submission)))
            .onSuccess {
                firestore.deleteFreeEvent(pendingId)
                _message.update { "已併入「${target.name}」" }
                refresh()
            }
            .onFailure { e -> _message.update { "合併失敗：${e.message}" } }
    }

    /** 管理員手動合併多筆已公開的活動：資料最完整的那筆當主活動，其餘補進去後刪除。 */
    suspend fun mergeEvents(ids: Collection<String>) {
        val picked = _events.value.filter { it.id in ids }
        if (picked.size < 2) return
        val primary = com.bandary.domain.model.pickMergePrimary(picked) ?: return
        val others = picked.filter { it.id != primary.id }
        firestore.addFreeEvent(com.bandary.domain.model.mergeFreeEvents(primary, others))
            .onSuccess {
                others.forEach { firestore.deleteFreeEvent(it.id) }
                _message.update { "已把 ${picked.size} 筆合併為「${primary.name}」" }
                refresh()
            }
            .onFailure { e -> _message.update { "合併失敗：${e.message}" } }
    }

    fun clearMessage() = _message.update { null }
}
