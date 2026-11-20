package vn.pvc.hub.sources

/** Các mức tần suất nhận email để người dùng chọn. */
object MailFreq {
    const val INSTANT = 0
    const val MANUAL = -1

    val OPTIONS: List<Pair<Int, String>> = listOf(
        INSTANT to "Tức thì (giữ kết nối, có thông báo cố định)",
        5 to "Mỗi 5 phút (có thông báo cố định)",
        10 to "Mỗi 10 phút (có thông báo cố định)",
        15 to "Mỗi 15 phút (mặc định, tiết kiệm pin)",
        30 to "Mỗi 30 phút",
        60 to "Mỗi 1 giờ",
        120 to "Mỗi 2 giờ",
        MANUAL to "Thủ công (chỉ khi mở app hoặc bấm Làm mới)"
    )

    val LIMITS = listOf(50, 100, 200)

    /** Dưới 15 phút Android không cho việc nền chạy, phải dùng dịch vụ có thông báo cố định. */
    fun needsService(minutes: Int): Boolean = minutes in 0..14
}
