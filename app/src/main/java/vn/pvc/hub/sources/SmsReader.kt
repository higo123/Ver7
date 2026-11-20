package vn.pvc.hub.sources

import android.Manifest
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.pvc.hub.data.HubDao
import vn.pvc.hub.data.HubItem
import vn.pvc.hub.data.Source

/** Đọc 100 tin SMS đến gần nhất. */
object SmsReader {
    suspend fun import(context: Context, dao: HubDao): String = withContext(Dispatchers.IO) {
        if (!Perms.has(context, Manifest.permission.READ_SMS)) {
            return@withContext "chưa cấp quyền đọc SMS"
        }
        val items = mutableListOf<HubItem>()
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("_id", "address", "body", "date", "read"),
            null,
            null,
            "date DESC"
        )
        cursor?.use { c ->
            while (c.moveToNext() && items.size < 100) {
                val address = c.getString(1) ?: ""
                items += HubItem(
                    key = "sms:${c.getLong(0)}",
                    source = Source.SMS.name,
                    account = "SMS",
                    sender = c.getString(1) ?: "(không rõ)",
                    preview = c.getString(2) ?: "",
                    time = c.getLong(3),
                    read = c.getInt(4) == 1,
                    replyTo = address
                )
            }
        }
        if (items.isNotEmpty()) {
            dao.insertAll(items)
            // SMS cũ (từ bản trước) chưa có số để trả lời thì bổ sung.
            items.forEach { if (it.replyTo.isNotBlank()) dao.setReplyTo(it.key, it.replyTo) }
        }
        if (cursor == null) "không truy cập được hộp thư SMS" else "đọc được ${items.size} tin"
    }
}
