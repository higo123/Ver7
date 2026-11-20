package vn.pvc.hub.sources

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import vn.pvc.hub.MainActivity
import vn.pvc.hub.R
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.HubItem
import vn.pvc.hub.data.Vip
import kotlin.math.abs

/** Hiện thông báo Android khi có thư mới (chỉ thư thực sự mới, không báo thư cũ). */
object MailNotifier {
    const val CHANNEL_NEW = "pvc_mail_new"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_NEW) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_NEW, "Thư mới", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    /**
     * `post` = false khi người dùng đang tự bấm Làm mới (đã thấy rồi, khỏi báo).
     * Lần đồng bộ đầu tiên của một tài khoản không báo, tránh báo hàng loạt thư cũ.
     */
    fun process(context: Context, address: String, fresh: List<HubItem>, post: Boolean) {
        val app = context.applicationContext
        val prefs = AppPrefs(app)
        val seeded = prefs.seededAccounts
        if (address !in seeded) {
            prefs.seededAccounts = seeded + address
            return
        }
        if (!post || !prefs.notifyMail || prefs.inQuietHours()) return
        val rules = prefs.rules
        val vip = prefs.vipSenders
        var show = fresh.filter { !it.read && !Vip.isHidden(it, rules) }
        if (prefs.notifyVipOnly) show = show.filter { Vip.isStarred(it, vip, rules) }
        if (show.isEmpty()) return
        runCatching {
            if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
            ensureChannel(app)
            val open = PendingIntent.getActivity(
                app, 0,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val b = NotificationCompat.Builder(app, CHANNEL_NEW)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(open)
                .setAutoCancel(true)
            if (prefs.appLock) {
                b.setContentTitle(if (show.size == 1) "Có thư mới" else "${show.size} thư mới")
                b.setContentText(address)
            } else if (show.size == 1) {
                b.setContentTitle(show[0].sender)
                b.setContentText(show[0].preview)
                b.setSubText(address)
            } else {
                b.setContentTitle("${show.size} thư mới")
                b.setContentText(address)
                val style = NotificationCompat.InboxStyle()
                show.take(5).forEach { style.addLine("${it.sender}: ${it.preview}") }
                b.setStyle(style)
            }
            NotificationManagerCompat.from(app).notify(2000 + abs(address.hashCode()) % 1000, b.build())
        }
    }
}
