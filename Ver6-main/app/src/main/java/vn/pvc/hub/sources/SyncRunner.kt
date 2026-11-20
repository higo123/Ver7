package vn.pvc.hub.sources

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import vn.pvc.hub.data.AccountStore
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.HubDatabase
import java.util.concurrent.TimeUnit

object SyncRunner {

    /** Chạy đồng bộ tất cả nguồn và trả về báo cáo từng nguồn (nhiều dòng). */
    suspend fun runAll(context: Context, notify: Boolean = true): String {
        val app = context.applicationContext
        val dao = HubDatabase.get(app).dao()
        AppPrefs(app).keepDays.takeIf { it > 0 }?.let { days ->
            val nowMs = System.currentTimeMillis()
            runCatching { dao.autoClean(nowMs - days * 86_400_000L, nowMs) }
        }

        val sms = runCatching { SmsReader.import(app, dao) }
            .getOrElse { "lỗi: ${it.message}" }
        val calls = runCatching { CallLogReader.import(app, dao) }
            .getOrElse { "lỗi: ${it.message}" }
        val mail = runCatching {
            GmailImapSync.syncAll(dao, AccountStore(app), AppPrefs(app).mailLimit) { acc, fresh ->
                MailNotifier.process(app, acc.address, fresh, notify)
            }
        }
            .getOrElse { "Email: lỗi: ${it.message}" }

        val chat = if (Perms.notificationAccess(app)) {
            // Nhờ hệ thống nối lại dịch vụ đọc thông báo nếu nó bị dừng.
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(app, NotificationCaptureService::class.java)
                )
            }
            "đã bật quyền (chỉ thấy tin mới đến từ lúc bật)"
        } else {
            "CHƯA bật quyền đọc thông báo"
        }

        val report = "$mail\nSMS: $sms\nCuộc gọi nhỡ: $calls\nTin chat: $chat"
        val prefs = AppPrefs(app)
        prefs.lastReport = report
        prefs.lastSyncTime = System.currentTimeMillis()
        HubWidgetProvider.refresh(app)
        return report
    }

    private const val WORK = "pvc_hub_sync"

    /**
     * Áp dụng cài đặt tần suất: lịch nền (từ 15 phút trở lên) và/hoặc dịch vụ có thông báo cố định
     * (tức thì, 5, 10 phút). Chế độ thủ công thì tắt cả hai.
     */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val prefs = AppPrefs(app)
        val min = prefs.mailIntervalMin
        val wm = WorkManager.getInstance(app)
        if (min == MailFreq.MANUAL) {
            wm.cancelUniqueWork(WORK)
        } else {
            // Chế độ dịch vụ vẫn giữ lịch 15 phút làm dự phòng (SMS, cuộc gọi, khi dịch vụ bị hệ thống tắt).
            val period = if (MailFreq.needsService(min)) 15L else min.toLong()
            val network = if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request = PeriodicWorkRequestBuilder<SyncWorker>(period, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
        MailService.apply(app)
        PeekService.apply(app)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        SyncRunner.runAll(applicationContext)
        return ListenableWorker.Result.success()
    }
}
