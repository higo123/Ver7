package vn.pvc.hub.data

/** Quy tắc lọc: tin có người gửi hoặc nội dung chứa từ khóa thì gắn sao hoặc ẩn khỏi Hub. */
data class FilterRule(val keyword: String, val action: String) {
    fun matches(item: HubItem): Boolean =
        keyword.isNotBlank() &&
            (item.sender.contains(keyword, ignoreCase = true) ||
                item.preview.contains(keyword, ignoreCase = true))

    companion object {
        const val STAR = "STAR"
        const val HIDE = "HIDE"
    }
}

/** Người gửi được gắn sao (VIP) và quy tắc ẩn. */
object Vip {
    fun key(item: HubItem): String = "${item.source}|${item.sender.lowercase()}"

    fun isStarred(item: HubItem, vip: Set<String>, rules: List<FilterRule>): Boolean =
        key(item) in vip || rules.any { it.action == FilterRule.STAR && it.matches(item) }

    fun isHidden(item: HubItem, rules: List<FilterRule>): Boolean =
        rules.any { it.action == FilterRule.HIDE && it.matches(item) }
}
