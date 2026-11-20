package vn.pvc.hub.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

/** Một tài khoản email: địa chỉ + Mật khẩu ứng dụng. */
data class MailAccount(
    val address: String,
    val password: String,
    /** Máy chủ riêng (tùy chọn). Để trống thì tự chọn theo đuôi địa chỉ (Gmail, Yahoo, iCloud). */
    val customImap: String = "",
    val customImapPort: Int = 993,
    val customSmtp: String = "",
    val customSmtpPort: Int = 465,
    /** Tên đăng nhập nếu khác địa chỉ email (một số máy chủ công ty); trống = dùng địa chỉ email. */
    val username: String = ""
) {
    val isCustom: Boolean get() = customImap.isNotBlank()

    /** Tên đăng nhập thực tế khi kết nối IMAP/SMTP. */
    val loginName: String get() = username.ifBlank { address }

    /** Cổng IMAP (luôn dùng SSL). */
    val imapPort: Int get() = if (isCustom) customImapPort else 993

    /** Máy chủ IMAP tự chọn theo đuôi địa chỉ. Đuôi lạ (Google Workspace…) dùng Gmail. */
    val imapHost: String
        get() {
            if (isCustom) return customImap.trim()
            val domain = address.substringAfter('@', "").lowercase()
            return when {
                domain == "yahoo.com" || domain.startsWith("yahoo.") || domain == "ymail.com" ->
                    "imap.mail.yahoo.com"
                domain == "icloud.com" || domain == "me.com" || domain == "mac.com" ->
                    "imap.mail.me.com"
                else -> "imap.gmail.com"
            }
        }

    /** Máy chủ gửi thư (SMTP) tương ứng. */
    val smtpHost: String
        get() = if (isCustom) customSmtp.trim().ifBlank { "smtp." + address.substringAfter('@', "") } else when (imapHost) {
            "imap.mail.yahoo.com" -> "smtp.mail.yahoo.com"
            "imap.mail.me.com" -> "smtp.mail.me.com"
            else -> "smtp.gmail.com"
        }

    /** Cổng 465 (SSL) cho Gmail/Yahoo, 587 (STARTTLS) cho iCloud. */
    val smtpPort: Int
        get() = if (isCustom) customSmtpPort else if (imapHost == "imap.mail.me.com") 587 else 465
}

/** Lưu nhiều tài khoản email, mã hóa bằng Android Keystore. */
class AccountStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "pvc_hub_secure",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun accounts(): List<MailAccount> {
        migrateLegacy()
        val raw = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                MailAccount(
                    o.getString("a"), o.getString("p"),
                    o.optString("ih"), o.optInt("ip", 993),
                    o.optString("sh"), o.optInt("sp", 465),
                    o.optString("u")
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Thêm mới, hoặc cập nhật mật khẩu nếu địa chỉ đã có. */
    fun addOrUpdate(account: MailAccount) {
        val list = accounts().filter { !it.address.equals(account.address, ignoreCase = true) } + account
        save(list)
    }

    fun remove(address: String) {
        save(accounts().filter { !it.address.equals(address, ignoreCase = true) })
    }

    private fun save(list: List<MailAccount>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("a", it.address).put("p", it.password)
                    .put("ih", it.customImap).put("ip", it.customImapPort)
                    .put("sh", it.customSmtp).put("sp", it.customSmtpPort)
                    .put("u", it.username)
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    /** Bản 0.4 trở về trước chỉ lưu 1 tài khoản: chuyển sang danh sách mới. */
    private fun migrateLegacy() {
        if (prefs.contains(KEY_ACCOUNTS)) return
        val a = prefs.getString(KEY_ADDRESS, "") ?: ""
        val p = prefs.getString(KEY_PASSWORD, "") ?: ""
        val arr = JSONArray()
        if (a.isNotBlank() && p.isNotBlank()) {
            arr.put(JSONObject().put("a", a).put("p", p))
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_ACCOUNTS = "mail_accounts"
        private const val KEY_ADDRESS = "gmail_address"
        private const val KEY_PASSWORD = "gmail_app_password"
    }
}
