package vn.pvc.hub.sources

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Nhớ (trong bộ nhớ) ô "Trả lời" của các thông báo chat để trả lời nhanh từ Hub.
 * Mất khi dịch vụ đọc thông báo khởi động lại, hoặc khi app chat đã hủy thông báo đó.
 */
object ReplyRegistry {
    private const val MAX = 200

    private val map = object : LinkedHashMap<String, Notification.Action>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Notification.Action>?): Boolean =
            size > MAX
    }

    fun put(key: String, action: Notification.Action) {
        synchronized(map) { map[key] = action }
    }

    fun has(key: String): Boolean = synchronized(map) { map.containsKey(key) }

    /** Trả về null nếu gửi đi được, hoặc thông báo lỗi. */
    fun send(context: Context, key: String, text: String): String? {
        val action = synchronized(map) { map[key] }
            ?: return "Không còn trả lời nhanh được tin này (thông báo đã bị app chat xóa, hoặc Hub vừa khởi động lại). Hãy mở app gốc để trả lời."
        val inputs = action.remoteInputs
            ?: return "Tin này không có ô trả lời."
        return try {
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(context, 0, intent)
            null
        } catch (e: PendingIntent.CanceledException) {
            "App chat đã hủy thông báo này, không trả lời nhanh được. Hãy mở app gốc."
        } catch (e: Exception) {
            "Gửi không được: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
