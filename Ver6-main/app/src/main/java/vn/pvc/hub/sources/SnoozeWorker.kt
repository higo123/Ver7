package vn.pvc.hub.sources

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import vn.pvc.hub.MainActivity
import vn.pvc.hub.R
import vn.pvc.hub.data.HubDatabase

/** Nhắc khi tới giờ một tin đã hoãn (Snooze). Giờ nhắc có thể lệch vài phút do Android tiết kiệm pin. */
class SnoozeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong("id", -1L)
        if (id < 0) return Result.success()
        val item = HubDatabase.get(applicationContext).dao().byId(id) ?: return Result.success()
        // Nếu tin được hoãn lại sang giờ khác thì lần nhắc đó do công việc mới lo.
        if (item.hidden == 1 || item.snoozedUntil == 0L) return Result.success()
        if (item.snoozedUntil > System.currentTimeMillis() + 60_000L) return Result.success()
        val nowMs = System.currentTimeMillis()
        val due = HubDatabase.get(applicationContext).dao().dueCount(nowMs - 20 * 60_000L, nowMs)
        runCatching {
            val app = applicationContext
            if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return Result.success()
            val nm = app.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Tin hoãn đến giờ", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val open = PendingIntent.getActivity(
                app, 0, Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val locked = vn.pvc.hub.data.AppPrefs(app).appLock
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(if (due > 1) "$due tin đã hoãn đến giờ" else "Tin đã hoãn đến giờ")
                .setContentText(
                    if (locked || due > 1) "Mở PVC-Hub để xem" else "${item.sender}: ${item.preview}"
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(app).notify(3000, n)
        }
        HubWidgetProvider.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "pvc_snooze"

        fun schedule(context: Context, id: Long, until: Long) {
            val delay = maxOf(0L, until - System.currentTimeMillis())
            val req = androidx.work.OneTimeWorkRequestBuilder<SnoozeWorker>()
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(androidx.work.workDataOf("id" to id))
                .build()
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork("snooze_$id", androidx.work.ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(context: Context, id: Long) {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("snooze_$id")
        }
    }
}
