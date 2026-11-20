package vn.pvc.hub.sources

import android.Manifest
import android.content.Context
import android.provider.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.pvc.hub.data.HubDao
import vn.pvc.hub.data.HubItem
import vn.pvc.hub.data.Source

/** Đọc 50 cuộc gọi nhỡ gần nhất (type = 3). */
object CallLogReader {
    suspend fun import(context: Context, dao: HubDao): String = withContext(Dispatchers.IO) {
        if (!Perms.has(context, Manifest.permission.READ_CALL_LOG)) {
            return@withContext "chưa cấp quyền đọc cuộc gọi"
        }
        val items = mutableListOf<HubItem>()
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf("_id", "number", "name", "date", "is_read"),
            "type = 3",
            null,
            "date DESC"
        )
        cursor?.use { c ->
            while (c.moveToNext() && items.size < 50) {
                val number = c.getString(1) ?: ""
                val name = c.getString(2)
                items += HubItem(
                    key = "call:${c.getLong(0)}",
                    source = Source.CALL.name,
                    account = "Điện thoại",
                    sender = if (name.isNullOrBlank()) number.ifBlank { "(số ẩn)" } else name,
                    preview = if (name.isNullOrBlank()) "Cuộc gọi nhỡ" else "Cuộc gọi nhỡ · $number",
                    time = c.getLong(3),
                    read = c.getInt(4) == 1
                )
            }
        }
        if (items.isNotEmpty()) dao.insertAll(items)
        if (cursor == null) "không truy cập được nhật ký cuộc gọi" else "đọc được ${items.size} cuộc gọi nhỡ"
    }
}
