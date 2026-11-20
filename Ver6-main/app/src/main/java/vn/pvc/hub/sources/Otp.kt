package vn.pvc.hub.sources

/** Tìm mã OTP (4 đến 8 chữ số) trong nội dung tin nhắn. */
object Otp {
    private val WITH_KEYWORD = Regex(
        "(?i)(otp|mã|ma xac|code|xác thực|xac thuc|xác nhận|xac nhan|verification|pin)[^0-9]{0,25}(\\d{4,8})"
    )
    private val SIX_DIGITS = Regex("(?<!\\d)(\\d{6})(?!\\d)")

    fun find(text: String): String? =
        WITH_KEYWORD.find(text)?.groupValues?.get(2) ?: SIX_DIGITS.find(text)?.groupValues?.get(1)
}

/** Sao chép mã vào clipboard (đánh dấu nhạy cảm để Android 13+ không hiện xem trước mã). */
fun copyCode(context: android.content.Context, code: String) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
    val clip = android.content.ClipData.newPlainText("Mã xác thực", code)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    cm.setPrimaryClip(clip)
}
