package com.bandary.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bandary.data.remote.FirestoreService
import com.bandary.data.repository.ConcertRepository
import com.bandary.data.repository.DailySongRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 每日 / 每月自動把演唱會 + 選歌備份到 Firestore。未登入時直接略過（成功，等下次）。
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val concertRepo: ConcertRepository,
    private val dailySongRepo: DailySongRepository,
    private val firestore: FirestoreService,
    private val settings: com.bandary.data.local.SettingsDataStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!firestore.isSignedIn()) return Result.success()
        return runCatching {
            // 演唱會雙向同步：雲端（例如網頁端）較新的先寫回本機，再上傳本機較新的。
            // 背景備份不會讓雲端照片變少：本機照片比較少的場次先不推（KEEP_CLOUD），
            // 等使用者下次手動備份時跳確認視窗再決定。
            val sync = firestore.syncConcerts(concertRepo.getAllForSync(), FirestoreService.ImageSyncMode.KEEP_CLOUD)
            sync.getOrNull()?.pulled?.let { firestore.materializeImages(it) }?.forEach { concertRepo.applyFromCloud(it) }
            val songs = dailySongRepo.getAllSongs().first()
            val deleted = settings.pendingSongDeletes.first()
            if (firestore.backupDailySongs(songs, deleted).isSuccess) settings.clearPendingSongDeletes(deleted)
            val concerts = concertRepo.getAllConcerts().first()
            // 背景備份一律不清理雲端圖片：清理只在使用者確認「以本機為準」時做。
            val imageReport = firestore.backupImages(concerts).getOrNull()
            firestore.backupProfile(settings.nickname.first(), settings.avatarUri.first())
            // 記下這次自動備份的時間與筆數。少了這行，設定頁的「上次備份」永遠只反映
            // 手動備份，自動備份跑得再勤，畫面上的日期也不會動——這就是「感覺沒好」的原因。
            settings.setLastBackup(
                System.currentTimeMillis(), concerts.size, songs.size,
                // 雲端共幾張＝這次新上傳 + 先前已備份；拿不到就傳 -1，保留上一次的數字
                photos = imageReport?.let { it.uploaded + it.alreadyBackedUp } ?: -1
            )
            settings.setLastBackupAuto(true)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object { const val WORK_NAME = "auto_backup" }
}

object AutoBackupScheduler {
    /** mode = "off" / "daily" / "monthly"。 */
    fun apply(context: Context, mode: String) {
        val wm = WorkManager.getInstance(context)
        if (mode == "off") {
            wm.cancelUniqueWork(AutoBackupWorker.WORK_NAME)
            return
        }
        val days = if (mode == "monthly") 30L else 1L
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        wm.enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
