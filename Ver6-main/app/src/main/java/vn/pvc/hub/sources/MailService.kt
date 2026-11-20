package vn.pvc.hub.sources

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.pvc.hub.MainActivity
import vn.pvc.hub.R
import vn.pvc.hub.data.AccountStore
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.HubDatabase
import vn.pvc.hub.data.MailAccount
import java.util.Collections
import java.util.Properties
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store

/**
 * Dịch vụ nền có thông báo cố định, dùng cho chế độ "Tức thì" (IMAP IDLE)
 * và các mức dưới 15 phút (5, 10 phút).
 */
class MailService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val openStores = Collections.synchronizedList(mutableListOf<Store>())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val min = AppPrefs(applicationContext).mailIntervalMin
        showForeground(min)
        job?.cancel()
        closeStores()
        job = scope.launch { watch() }
        return START_STICKY
    }

    private fun showForeground(min: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Đang nhận email", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (min == MailFreq.INSTANT) "Nhận email tức thì" else "Kiểm tra email mỗi $min phút"
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PVC-Hub")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private suspend fun watch() = coroutineScope {
        val prefs = AppPrefs(applicationContext)
        val min = prefs.mailIntervalMin
        val accounts = AccountStore(applicationContext).accounts()
        if (accounts.isEmpty() || !MailFreq.needsService(min)) {
            stopSelf()
            return@coroutineScope
        }
        if (min == MailFreq.INSTANT) {
            accounts.forEach { acc -> launch { idleLoop(acc) } }
        } else {
            launch { pollLoop(min) }
        }
    }

    private suspend fun pollLoop(min: Int) {
        while (currentCoroutineContext().isActive) {
            if (networkOk()) syncOnce()
            delay(min * 60_000L)
        }
    }

    private suspend fun syncOnce() {
        val app = applicationContext
        val prefs = AppPrefs(app)
        val dao = HubDatabase.get(app).dao()
        GmailImapSync.syncAll(dao, AccountStore(app), prefs.mailLimit) { acc, fresh ->
            MailNotifier.process(app, acc.address, fresh, true)
        }
    }

    /** Giữ một kết nối IDLE; có thư mới thì máy chủ báo, ta đồng bộ ngay. */
    private suspend fun idleLoop(acc: MailAccount) {
        val app = applicationContext
        val dao = HubDatabase.get(app).dao()
        while (currentCoroutineContext().isActive) {
            var st: Store? = null
            try {
                if (!networkOk()) {
                    delay(60_000)
                    continue
                }
                val props = Properties()
                props["mail.imaps.connectiontimeout"] = "15000"
                props["mail.imaps.ssl.protocols"] = "TLSv1.2"
                val store = Session.getInstance(props).getStore("imaps")
                st = store
                store.connect(acc.imapHost, acc.imapPort, acc.loginName, acc.password)
                openStores.add(store)
                val folder = store.getFolder("INBOX") as IMAPFolder
                folder.open(Folder.READ_ONLY)
                val limit = AppPrefs(app).mailLimit
                val notify: (MailAccount, List<vn.pvc.hub.data.HubItem>) -> Unit = { a, fresh ->
                    MailNotifier.process(app, a.address, fresh, true)
                }
                GmailImapSync.sync(dao, acc, limit, notify)
                while (currentCoroutineContext().isActive) {
                    folder.idle(true) // chờ tới khi máy chủ báo có thay đổi
                    GmailImapSync.sync(dao, acc, limit, notify)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(20_000) // mất kết nối: chờ chút rồi nối lại
            } finally {
                st?.let {
                    openStores.remove(it)
                    runCatching { it.close() }
                }
            }
        }
    }

    private fun networkOk(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return if (AppPrefs(applicationContext).wifiOnly) {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } else {
            true
        }
    }

    private fun closeStores() {
        val copy = synchronized(openStores) { openStores.toList() }
        openStores.clear()
        copy.forEach { s -> Thread { runCatching { s.close() } }.start() }
    }

    override fun onDestroy() {
        job?.cancel()
        closeStores()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "pvc_mail_service"
        private const val FG_ID = 1001

        /** Bật hoặc tắt dịch vụ cho khớp với cài đặt tần suất hiện tại. */
        fun apply(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, MailService::class.java)
            try {
                val want = MailFreq.needsService(AppPrefs(app).mailIntervalMin) &&
                    AccountStore(app).accounts().isNotEmpty()
                if (want) ContextCompat.startForegroundService(app, intent) else app.stopService(intent)
            } catch (e: Exception) {
                // Android không cho khởi động dịch vụ khi app đang ở nền: sẽ thử lại khi mở app.
            }
        }
    }
}
