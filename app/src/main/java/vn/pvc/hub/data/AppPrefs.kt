package vn.pvc.hub.data

import android.content.Context

/** Cài đặt không nhạy cảm của app. */
class AppPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("pvc_hub_prefs", Context.MODE_PRIVATE)

    init {
        // One-time privacy migration: turn off broad auto-detection for existing installs too.
        // Users may explicitly enable it again from the Chat Apps settings.
        if (!prefs.getBoolean("privacy_auto_detect_migrated_v1", false)) {
            prefs.edit()
                .putBoolean("auto_detect", false)
                .putBoolean("privacy_auto_detect_migrated_v1", true)
                .apply()
        }
    }

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) {
            prefs.edit().putBoolean("onboarding_done", value).apply()
        }

    var lastReport: String
        get() = prefs.getString("last_report", "") ?: ""
        set(value) {
            prefs.edit().putString("last_report", value).apply()
        }

    var lastSyncTime: Long
        get() = prefs.getLong("last_sync_time", 0L)
        set(value) {
            prefs.edit().putLong("last_sync_time", value).apply()
        }

    /** Tự nhận tin từ mọi app nhắn tin (thông báo loại "tin nhắn") chưa có trong danh sách. */
    var autoDetect: Boolean
        get() = prefs.getBoolean("auto_detect", false)
        set(value) {
            prefs.edit().putBoolean("auto_detect", value).apply()
        }

    /** Các app do người dùng tự chọn thêm (tên gói). */
    var extraApps: Set<String>
        get() = prefs.getStringSet("extra_apps", emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("extra_apps", value.toSet()).apply()
        }

    /** Các nguồn người dùng đã tắt (tên Source), vd. tắt Facebook nếu quá nhiều thông báo. */
    var disabledSources: Set<String>
        get() = prefs.getStringSet("disabled_sources", emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("disabled_sources", value.toSet()).apply()
        }

    /** Chế độ chẩn đoán: ghi cả thông báo của app không được theo dõi. */
    var logAll: Boolean
        get() = prefs.getBoolean("log_all", false)
        set(value) {
            prefs.edit().putBoolean("log_all", value).apply()
        }

    /** Tần suất nhận email (phút): 0 = tức thì, -1 = thủ công. Mặc định 15. */
    var mailIntervalMin: Int
        get() = prefs.getInt("mail_interval_min", 15)
        set(value) {
            prefs.edit().putInt("mail_interval_min", value).apply()
        }

    /** Chỉ đồng bộ khi dùng Wi-Fi (không dùng dữ liệu di động). */
    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", false)
        set(value) {
            prefs.edit().putBoolean("wifi_only", value).apply()
        }

    /** Số thư mới nhất lấy về mỗi lần (mỗi tài khoản). */
    var mailLimit: Int
        get() = prefs.getInt("mail_limit", 50)
        set(value) {
            prefs.edit().putInt("mail_limit", value).apply()
        }

    /** Hiện thông báo của Android khi có thư mới. */
    var notifyMail: Boolean
        get() = prefs.getBoolean("notify_mail", true)
        set(value) {
            prefs.edit().putBoolean("notify_mail", value).apply()
        }

    /** Tài khoản đã đồng bộ lần đầu (để không báo hàng loạt thư cũ). */
    var seededAccounts: Set<String>
        get() = prefs.getStringSet("seeded_accounts", emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("seeded_accounts", value.toSet()).apply()
        }

    /** Bật Peek: tay cầm nổi ở mép màn hình, chạm vào để xem nhanh tin. */
    var peekEnabled: Boolean
        get() = prefs.getBoolean("peek_enabled", false)
        set(value) {
            prefs.edit().putBoolean("peek_enabled", value).apply()
        }

    /** Mép đặt tay cầm Peek: 0 = trái, 1 = phải. */
    var peekSide: Int
        get() = prefs.getInt("peek_side", 0)
        set(value) {
            prefs.edit().putInt("peek_side", value).apply()
        }

    /** Vị trí dọc của tay cầm Peek (lệch so với giữa màn hình, px). */
    /** Độ lớn Peek theo phần trăm (60 đến 200, mặc định 100). */
    var peekSize: Int
        get() = prefs.getInt("peek_size", 100)
        set(value) {
            prefs.edit().putInt("peek_size", value.coerceIn(60, 200)).apply()
        }

    /** Độ lớn khung hiện tin của Peek theo phần trăm (80 đến 160, mặc định 110). */
    var peekPanelSize: Int
        get() = prefs.getInt("peek_panel_size", 110)
        set(value) {
            prefs.edit().putInt("peek_panel_size", value.coerceIn(80, 160)).apply()
        }

    /** Độ đậm (không trong suốt) của Peek theo phần trăm (30 đến 100, mặc định 85). */
    var peekAlpha: Int
        get() = prefs.getInt("peek_alpha", 85)
        set(value) {
            prefs.edit().putInt("peek_alpha", value.coerceIn(30, 100)).apply()
        }

    var peekY: Int
        get() = prefs.getInt("peek_y", 0)
        set(value) {
            prefs.edit().putInt("peek_y", value).apply()
        }

    /** Người gửi được gắn sao (khóa dạng "NGUỒN|tên viết thường"). */
    var vipSenders: Set<String>
        get() = prefs.getStringSet("vip_senders", emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("vip_senders", value.toSet()).apply()
        }

    /** Quy tắc lọc theo từ khóa: gắn sao hoặc ẩn. */
    var rules: List<FilterRule>
        get() {
            val raw = prefs.getString("filter_rules", "[]") ?: "[]"
            return runCatching {
                val arr = org.json.JSONArray(raw)
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    FilterRule(o.getString("k"), o.getString("a"))
                }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val arr = org.json.JSONArray()
            value.forEach { arr.put(org.json.JSONObject().put("k", it.keyword).put("a", it.action)) }
            prefs.edit().putString("filter_rules", arr.toString()).apply()
        }

    /** Khóa app bằng vân tay hoặc khóa màn hình. */
    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(value) {
            prefs.edit().putBoolean("app_lock", value).apply()
        }

    /** Tự dọn tin cũ: giữ bao nhiêu ngày (0 = không dọn). */
    var keepDays: Int
        get() = prefs.getInt("keep_days", 0)
        set(value) {
            prefs.edit().putInt("keep_days", value).apply()
        }

    /** Chữ ký email tự thêm vào thư mới và thư trả lời. */
    var signature: String
        get() = prefs.getString("mail_signature", "") ?: ""
        set(value) {
            prefs.edit().putString("mail_signature", value).apply()
        }

    /** Bản nháp thư mới (JSON: from, to, subject, body); rỗng nếu không có. */
    var composeDraft: String
        get() = prefs.getString("compose_draft", "") ?: ""
        set(value) {
            prefs.edit().putString("compose_draft", value).apply()
        }

    /** Địa chỉ tệp JSON báo bản mới (tùy chọn). */
    var updateUrl: String
        get() = (prefs.getString("update_url", "") ?: "").ifBlank { DEFAULT_UPDATE_URL }
        set(value) {
            prefs.edit().putString("update_url", value.trim()).apply()
        }

    /** Lần kiểm tra bản mới gần nhất (mili giây). */
    var updateCheckedAt: Long
        get() = prefs.getLong("update_checked", 0L)
        set(value) {
            prefs.edit().putLong("update_checked", value).apply()
        }

    /** Giờ yên tĩnh: Hub không hiện thông báo thư mới trong khoảng này. */
    var quietEnabled: Boolean
        get() = prefs.getBoolean("quiet_enabled", false)
        set(value) {
            prefs.edit().putBoolean("quiet_enabled", value).apply()
        }

    /** Giờ bắt đầu yên tĩnh, tính bằng phút từ 0:00 (mặc định 22:00). */
    var quietStartMin: Int
        get() = prefs.getInt("quiet_start", 22 * 60)
        set(value) {
            prefs.edit().putInt("quiet_start", value).apply()
        }

    /** Giờ kết thúc yên tĩnh (mặc định 6:00). */
    var quietEndMin: Int
        get() = prefs.getInt("quiet_end", 6 * 60)
        set(value) {
            prefs.edit().putInt("quiet_end", value).apply()
        }

    /** Chỉ báo thông báo thư mới của người được gắn sao. */
    var notifyVipOnly: Boolean
        get() = prefs.getBoolean("notify_vip_only", false)
        set(value) {
            prefs.edit().putBoolean("notify_vip_only", value).apply()
        }

    fun inQuietHours(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!quietEnabled) return false
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = nowMs
        val m = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
        val s = quietStartMin
        val e = quietEndMin
        return if (s <= e) m in s until e else (m >= s || m < e)
    }

    val notifLog: String
        get() = prefs.getString("notif_log", "") ?: ""

    /** Lần cuối Hub nhận được thông báo của một app (mili giây), 0 nếu chưa từng. */
    fun lastSeen(pkg: String): Long = prefs.getLong("seen_$pkg", 0L)

    fun markSeen(pkg: String, time: Long) {
        prefs.edit().putLong("seen_$pkg", time).apply()
    }

    fun appendNotifLog(line: String) {
        synchronized(LOCK) {
            val old = notifLog.lines().filter { it.isNotBlank() }
            val merged = (listOf(line) + old).take(30).joinToString("\n")
            prefs.edit().putString("notif_log", merged).apply()
        }
    }

    fun clearNotifLog() {
        synchronized(LOCK) {
            prefs.edit().putString("notif_log", "").apply()
        }
    }

    companion object {
        private val LOCK = Any()

        /** Địa chỉ tệp JSON báo bản mới có sẵn trong app; người dùng không cần nhập gì. */
        const val DEFAULT_UPDATE_URL =
            "https://gist.githubusercontent.com/higo123/be787953879f540a3dd3f2f4d830408c/raw/version.json"
    }
}
