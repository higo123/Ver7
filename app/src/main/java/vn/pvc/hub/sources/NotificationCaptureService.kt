package vn.pvc.hub.sources

import android.app.Notification
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.HubDatabase
import vn.pvc.hub.data.HubItem
import vn.pvc.hub.data.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bắt thông báo của các app nhắn tin rồi đưa vào hộp thư hợp nhất.
 * Nhận tin từ: danh sách có sẵn (Zalo, Messenger, Telegram, WhatsApp, Viber, Signal, LINE),
 * app người dùng tự thêm, và (nếu bật) mọi app có thông báo loại "tin nhắn" cài về sau.
 * Chỉ thấy tin đến SAU khi bật quyền, và chỉ phần chữ hiện trong thông báo.
 */
class NotificationCaptureService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val n = sbn.notification ?: return
        val prefs = AppPrefs(applicationContext)

        val builtIn = TRACKED[pkg]
        // Nguồn đã bị người dùng tắt trong Thiết lập: bỏ qua im lặng.
        if (builtIn != null && builtIn.name in prefs.disabledSources) return
        val manual = prefs.extraApps.contains(pkg)
        val smsPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(applicationContext) }.getOrNull()
        val auto = prefs.autoDetect &&
            n.category == Notification.CATEGORY_MESSAGE &&
            pkg !in IGNORED &&
            pkg != smsPkg
        val tracked = builtIn != null || manual || auto
        if (tracked) prefs.markSeen(pkg, System.currentTimeMillis())

        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extractText(extras)
        val label = appLabel(pkg)

        if (!tracked) {
            if (prefs.logAll) {
                // Diagnostic logs must not copy notification content or personal names.
                prefs.appendNotifLog(logLine(label, pkg, "", "", "không theo dõi app này"))
            }
            return
        }

        val reason = when {
            n.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "bỏ qua: thông báo tổng hợp nhóm"
            n.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
                n.category != Notification.CATEGORY_MESSAGE -> "bỏ qua: thông báo đang chạy"
            title.isBlank() && text.isBlank() -> "bỏ qua: không có nội dung"
            else -> null
        }
        // Keep diagnostics useful without persisting message bodies or sender names.
        prefs.appendNotifLog(logLine(label, pkg, "", "", reason ?: "ĐÃ NHẬN"))
        if (reason != null) return

        val source = builtIn ?: Source.OTHER
        val appName = if (source == Source.OTHER) label else source.label
        // Thông báo mạng xã hội thường chỉ có tiêu đề ("Lan đã bình luận..."), không có nội dung phụ:
        // khi đó lấy tên app làm người gửi và tiêu đề làm nội dung.
        val item = HubItem(
            key = "notif:$pkg:${sbn.id}:${sbn.postTime}",
            source = source.name,
            account = appName,
            sender = if (text.isBlank()) appName else title.ifBlank { appName },
            preview = if (text.isBlank()) title else text,
            time = sbn.postTime,
            read = false,
            packageName = pkg
        )
        // Nếu thông báo có ô "Trả lời" (RemoteInput) thì nhớ lại để trả lời nhanh từ Hub.
        val replyAction = n.actions?.firstOrNull { a ->
            a.remoteInputs?.any { it.allowFreeFormInput } == true
        }
        if (replyAction != null) ReplyRegistry.put(item.key, replyAction)
        val dao = HubDatabase.get(applicationContext).dao()
        scope.launch {
            dao.insert(item)
            HubWidgetProvider.refresh(applicationContext)
        }
    }

    private fun extractText(extras: Bundle?): String {
        if (extras == null) return ""
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (!big.isNullOrBlank()) return big
        val plain = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!plain.isNullOrBlank()) return plain
        val lastLine = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.lastOrNull()?.toString()
        if (!lastLine.isNullOrBlank()) return lastLine
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val lastMessage = messages?.lastOrNull() as? Bundle
        return lastMessage?.getCharSequence("text")?.toString().orEmpty()
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = applicationContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun logLine(label: String, pkg: String, title: String, text: String, verdict: String): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        return "$time | $label ($pkg) | \"${title.take(24)}\" | \"${text.take(40)}\" | $verdict"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Yêu cầu Android nối lại dịch vụ đọc thông báo (phòng khi hệ thống đã ngắt). */
        fun rebind(context: android.content.Context) {
            runCatching {
                NotificationListenerService.requestRebind(
                    android.content.ComponentName(context, NotificationCaptureService::class.java)
                )
            }
        }

        /** App nhắn tin nhận diện sẵn, tự hoạt động ngay khi cài. */
        val TRACKED: Map<String, Source> = mapOf(
            "com.zing.zalo" to Source.ZALO,
            "org.telegram.messenger" to Source.TELEGRAM,
            "org.telegram.messenger.web" to Source.TELEGRAM,
            "org.thunderdog.challegram" to Source.TELEGRAM,
            "org.telegram.plus" to Source.TELEGRAM,
            "com.facebook.orca" to Source.MESSENGER,
            "com.facebook.mlite" to Source.MESSENGER,
            "com.whatsapp" to Source.WHATSAPP,
            "com.whatsapp.w4b" to Source.WHATSAPP,
            "com.viber.voip" to Source.VIBER,
            "org.thoughtcrime.securesms" to Source.SIGNAL,
            "jp.naver.line.android" to Source.LINE,
            // Mạng xã hội
            "com.facebook.katana" to Source.FACEBOOK,
            "com.facebook.lite" to Source.FACEBOOK,
            "com.instagram.android" to Source.INSTAGRAM,
            "com.instagram.lite" to Source.INSTAGRAM,
            "com.instagram.barcelona" to Source.THREADS,
            "com.zhiliaoapp.musically" to Source.TIKTOK,
            "com.ss.android.ugc.trill" to Source.TIKTOK,
            "com.twitter.android" to Source.X,
            "com.linkedin.android" to Source.LINKEDIN
        )

        /** App không bao giờ tự nhận (đã có nguồn riêng hoặc không phải tin nhắn chat). */
        val IGNORED: Set<String> = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.google.android.gm",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.android.server.telecom"
        )
    }
}
