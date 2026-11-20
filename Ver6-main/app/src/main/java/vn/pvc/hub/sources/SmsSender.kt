package vn.pvc.hub.sources

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/** Gửi SMS trả lời. Tin gửi đi không nằm trong ứng dụng nhắn tin mặc định của máy. */
object SmsSender {
    /** Trả về null nếu gửi đi được, hoặc thông báo lỗi. */
    fun send(context: Context, to: String, text: String): String? {
        if (to.isBlank()) return "Không có số điện thoại để trả lời."
        if (!Perms.has(context, Manifest.permission.SEND_SMS)) return "Chưa cấp quyền gửi SMS."
        return try {
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = sm.divideMessage(text)
            if (parts.size > 1) {
                sm.sendMultipartTextMessage(to, null, parts, null, null)
            } else {
                sm.sendTextMessage(to, null, text, null, null)
            }
            null
        } catch (e: Exception) {
            "Gửi SMS không được: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
