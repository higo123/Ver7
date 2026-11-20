package vn.pvc.hub.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Các nguồn liên lạc được gom về một hộp thư hợp nhất. */
enum class Source(val label: String) {
    EMAIL("Email"),
    SMS("SMS"),
    CALL("Cuộc gọi nhỡ"),
    ZALO("Zalo"),
    TELEGRAM("Telegram"),
    MESSENGER("Messenger"),
    WHATSAPP("WhatsApp"),
    VIBER("Viber"),
    SIGNAL("Signal"),
    LINE("LINE"),
    FACEBOOK("Facebook"),
    INSTAGRAM("Instagram"),
    THREADS("Threads"),
    TIKTOK("TikTok"),
    X("X (Twitter)"),
    LINKEDIN("LinkedIn"),
    OTHER("Ứng dụng khác")
}

/**
 * Một mục trong hộp thư hợp nhất. `key` là khóa chống trùng
 * (ví dụ "gmail:abc@gmail.com:1234", "sms:99").
 */
@Entity(
    tableName = "hub_item",
    indices = [Index(value = ["key"], unique = true), Index(value = ["time"])]
)
data class HubItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val source: String,
    val account: String,
    val sender: String,
    val preview: String,
    val time: Long,
    val read: Boolean = false,
    val packageName: String? = null,
    /** Địa chỉ email để trả lời (chỉ có với thư email). */
    @ColumnInfo(defaultValue = "''") val replyTo: String = "",
    /** Hoãn tới thời điểm này (mili giây); 0 là không hoãn. */
    @ColumnInfo(defaultValue = "0") val snoozedUntil: Long = 0,
    /** 1 = đã ẩn khỏi Hub (vẫn lưu để lần đồng bộ sau không hiện lại). */
    @ColumnInfo(defaultValue = "0") val hidden: Int = 0
)
