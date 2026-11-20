package vn.pvc.hub.sources

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Thông tin bản mới đọc từ tệp JSON: {"versionCode":18,"versionName":"0.11.0","apkUrl":"https://...","notes":"..."} */
data class UpdateInfo(val versionCode: Long, val versionName: String, val apkUrl: String, val notes: String)

object UpdateChecker {

    sealed interface Result {
        data class Newer(val info: UpdateInfo) : Result
        data object UpToDate : Result
        data class Error(val message: String) : Result
    }

    fun currentVersionCode(context: Context): Long = runCatching {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        p.longVersionCode
    }.getOrDefault(0L)

    suspend fun check(context: Context, url: String): Result = withContext(Dispatchers.IO) {
        val u = url.trim()
        if (!u.startsWith("https://")) return@withContext Result.Error("Địa chỉ phải bắt đầu bằng https://")
        try {
            val conn = URL(u).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            if (code != 200) return@withContext Result.Error("Máy chủ trả về mã $code")
            val text = conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 100_000) throw IllegalArgumentException("Tệp thông tin cập nhật vượt quá 100 KB.")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            val o = JSONObject(text)
            val apkUrl = o.optString("apkUrl")
            if (!apkUrl.startsWith("https://")) {
                return@withContext Result.Error("Liên kết APK phải sử dụng HTTPS.")
            }
            val info = UpdateInfo(
                o.getLong("versionCode"),
                o.optString("versionName").take(40),
                apkUrl,
                o.optString("notes").take(4_000)
            )
            if (info.versionCode > currentVersionCode(context)) Result.Newer(info) else Result.UpToDate
        } catch (e: Exception) {
            Result.Error("Không kiểm tra được: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
