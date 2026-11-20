package vn.pvc.hub.sources

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vn.pvc.hub.data.HubDatabase
import vn.pvc.hub.data.HubItem

/** Sao lưu và khôi phục các tin trong Hub ra file JSON. Mật khẩu email KHÔNG được xuất. */
object BackupManager {

    /** Trả về số mục đã xuất. */
    suspend fun export(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val items = HubDatabase.get(context).dao().all()
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key)
                    .put("source", it.source)
                    .put("account", it.account)
                    .put("sender", it.sender)
                    .put("preview", it.preview)
                    .put("time", it.time)
                    .put("read", it.read)
                    .put("packageName", it.packageName ?: JSONObject.NULL)
                    .put("replyTo", it.replyTo)
                    .put("snoozedUntil", it.snoozedUntil)
                    .put("hidden", it.hidden)
            )
        }
        val root = JSONObject().put("app", "PVC-Hub").put("version", 1).put("items", arr)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("Không ghi được file")
        items.size
    }

    /** Trả về số mục mới được thêm (mục đã có sẵn thì bỏ qua). */
    suspend fun import(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        // Refuse oversized files before allocating their full contents in memory.
        val maxBytes = 25L * 1024 * 1024
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "Tệp sao lưu vượt quá giới hạn 25 MB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Không đọc được file")
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("app") == "PVC-Hub") { "Tệp không phải bản sao lưu PVC-Hub." }
        require(root.optInt("version", -1) == 1) { "Phiên bản tệp sao lưu không được hỗ trợ." }
        val arr = root.getJSONArray("items")
        require(arr.length() <= 50_000) { "Tệp có quá nhiều bản ghi (tối đa 50.000)." }
        val items = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val key = o.getString("key")
            val source = o.getString("source")
            require(key.length in 1..512) { "Khóa bản ghi không hợp lệ tại mục ${i + 1}." }
            require(source.length in 1..64) { "Nguồn dữ liệu không hợp lệ tại mục ${i + 1}." }
            require(o.optString("sender").length <= 2_000) { "Tên người gửi quá dài tại mục ${i + 1}." }
            require(o.optString("preview").length <= 20_000) { "Nội dung quá dài tại mục ${i + 1}." }
            HubItem(
                key = key,
                source = source,
                account = o.optString("account"),
                sender = o.optString("sender"),
                preview = o.optString("preview"),
                time = o.optLong("time"),
                read = o.optBoolean("read"),
                packageName = if (o.isNull("packageName")) null else o.optString("packageName"),
                replyTo = o.optString("replyTo"),
                snoozedUntil = o.optLong("snoozedUntil"),
                hidden = o.optInt("hidden")
            )
        }
        val ids = HubDatabase.get(context).dao().insertAll(items)
        HubWidgetProvider.refresh(context)
        ids.count { it != -1L }
    }
}
