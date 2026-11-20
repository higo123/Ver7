package vn.pvc.hub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.pvc.hub.data.AccountStore
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.FilterRule
import vn.pvc.hub.data.HubDatabase
import vn.pvc.hub.data.HubItem
import vn.pvc.hub.data.Source
import vn.pvc.hub.data.Vip
import vn.pvc.hub.sources.HubWidgetProvider
import vn.pvc.hub.sources.SnoozeWorker
import vn.pvc.hub.sources.SyncRunner

/** Chế độ xem của hộp thư. */
sealed interface InboxView {
    data object All : InboxView
    data object Priority : InboxView
    data object Snoozed : InboxView
    data class Account(val address: String) : InboxView
    data class Src(val source: Source) : InboxView
}

class HubViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = HubDatabase.get(app).dao()
    private val prefs = AppPrefs(app)

    val view = MutableStateFlow<InboxView>(InboxView.All)
    val query = MutableStateFlow("")

    /** Bộ lọc khi tìm kiếm: chỉ tin chưa đọc, khoảng ngày (0 = mọi lúc, 1 = hôm nay, 7, 30), tìm trong mọi nguồn. */
    val unreadOnly = MutableStateFlow(false)
    val dayRange = MutableStateFlow(0)
    val searchAll = MutableStateFlow(true)
    val syncing = MutableStateFlow(false)
    val report = MutableStateFlow(prefs.lastReport)
    val lastSync = MutableStateFlow(prefs.lastSyncTime)

    /** Người gửi gắn sao và quy tắc lọc (đọc lại khi có thay đổi). */
    val vips = MutableStateFlow(prefs.vipSenders)
    val rules = MutableStateFlow(prefs.rules)
    val accounts = MutableStateFlow(AccountStore(app).accounts().map { it.address })

    /** Mốc "bây giờ", cập nhật mỗi 30 giây để tin hết hạn Snooze tự hiện lại. */
    private val now = MutableStateFlow(System.currentTimeMillis())

    /** Bản mới (nếu có) từ địa chỉ kiểm tra người dùng đặt. */
    val update = MutableStateFlow<vn.pvc.hub.sources.UpdateInfo?>(null)

    init {
        checkUpdate()
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                now.value = System.currentTimeMillis()
            }
        }
    }

    /** Bản mới (nếu có), đọc từ địa chỉ kiểm tra người dùng đặt; kiểm tra tự động tối đa 1 lần mỗi ngày. */

    fun checkUpdate(force: Boolean = false, onDone: (String) -> Unit = {}) {
        val url = prefs.updateUrl
        if (url.isBlank()) {
            onDone("Chưa nhập địa chỉ kiểm tra bản mới.")
            return
        }
        if (!force && System.currentTimeMillis() - prefs.updateCheckedAt < 24 * 3_600_000L) return
        viewModelScope.launch {
            prefs.updateCheckedAt = System.currentTimeMillis()
            when (val r = vn.pvc.hub.sources.UpdateChecker.check(getApplication<Application>(), url)) {
                is vn.pvc.hub.sources.UpdateChecker.Result.Newer -> {
                    update.value = r.info
                    onDone("Có bản mới ${r.info.versionName}.")
                }
                vn.pvc.hub.sources.UpdateChecker.Result.UpToDate -> {
                    update.value = null
                    onDone("Bạn đang dùng bản mới nhất.")
                }
                is vn.pvc.hub.sources.UpdateChecker.Result.Error -> onDone(r.message)
            }
        }
    }

    fun reloadSettings() {
        vips.value = prefs.vipSenders
        rules.value = prefs.rules
        accounts.value = AccountStore(getApplication<Application>()).accounts().map { it.address }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val source: Flow<List<HubItem>> =
        combine(view, now, query, searchAll) { v, n, q, all ->
            // Đang tìm kiếm và chọn "Mọi nguồn" thì tìm trong toàn bộ Hub, bất kể mục đang xem.
            (if (q.isNotBlank() && all) InboxView.All else v) to n
        }
        .flatMapLatest { (v, n) ->
            when (v) {
                InboxView.All, InboxView.Priority -> dao.observeAll(n)
                InboxView.Snoozed -> dao.observeSnoozed(n)
                is InboxView.Account -> dao.observeByAccount(v.address, n)
                is InboxView.Src -> dao.observeBySource(v.source.name, n)
            }
        }

    private val filters = combine(unreadOnly, dayRange) { u, d -> u to d }

    val inbox: StateFlow<List<HubItem>> = combine(source, query, vips, rules, view) { list, q, vip, r, v ->
        list.filter { !Vip.isHidden(it, r) }
            .filter { v != InboxView.Priority || Vip.isStarred(it, vip, r) }
            .filter {
                q.isBlank() || it.sender.contains(q, ignoreCase = true) ||
                    it.preview.contains(q, ignoreCase = true)
            }
    }.combine(filters) { list, (unread, days) ->
        val since = when (days) {
            1 -> java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            7, 30 -> System.currentTimeMillis() - days * 86_400_000L
            else -> 0L
        }
        list.filter { (!unread || !it.read) && it.time >= since }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadCounts: StateFlow<Map<String, Int>> = now
        .flatMapLatest { dao.observeUnreadCounts(it) }
        .map { list -> list.associate { it.source to it.cnt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val accountUnread: StateFlow<Map<String, Int>> = now
        .flatMapLatest { dao.observeUnreadByAccount(it) }
        .map { list -> list.associate { it.source to it.cnt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val snoozedCount: StateFlow<Int> = now
        .flatMapLatest { dao.observeSnoozedCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Số tin chưa đọc của người được gắn sao. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val priorityUnread: StateFlow<Int> = now
        .flatMapLatest { dao.observeAll(it) }
        .combine(vips) { l, v -> l to v }
        .combine(rules) { (l, v), r ->
            l.count { !it.read && !Vip.isHidden(it, r) && Vip.isStarred(it, v, r) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Các nguồn đã có ít nhất một tin (để menu chỉ hiện app chat thực sự có tin). */
    val present: StateFlow<Set<String>> = dao.observePresentSources()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun refresh() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            try {
                report.value = SyncRunner.runAll(getApplication<Application>(), notify = false)
                lastSync.value = prefs.lastSyncTime
                reloadSettings()
            } finally {
                syncing.value = false
            }
        }
    }

    fun markRead(item: HubItem) {
        viewModelScope.launch {
            dao.markRead(item.id)
            HubWidgetProvider.refresh(getApplication<Application>())
        }
    }

    fun markReadAll(items: List<HubItem>) {
        viewModelScope.launch {
            dao.markReadByIds(items.map { it.id })
            HubWidgetProvider.refresh(getApplication<Application>())
        }
    }

    /** Hoãn một tin tới thời điểm `until`; tới giờ nó hiện lại và có thông báo nhắc. */
    fun snooze(item: HubItem, until: Long) {
        viewModelScope.launch {
            dao.snooze(item.id, until)
            SnoozeWorker.schedule(getApplication<Application>(), item.id, until)
            now.value = System.currentTimeMillis()
            HubWidgetProvider.refresh(getApplication<Application>())
        }
    }

    fun unsnooze(item: HubItem) {
        viewModelScope.launch {
            dao.snooze(item.id, 0L)
            SnoozeWorker.cancel(getApplication<Application>(), item.id)
            now.value = System.currentTimeMillis()
            HubWidgetProvider.refresh(getApplication<Application>())
        }
    }

    /** Ẩn khỏi Hub (không xóa trên máy chủ thư). */
    fun hide(items: List<HubItem>) {
        viewModelScope.launch {
            dao.hideByIds(items.map { it.id })
            items.forEach { SnoozeWorker.cancel(getApplication<Application>(), it.id) }
            HubWidgetProvider.refresh(getApplication<Application>())
        }
    }

    /** Dọn thư email trên máy chủ rồi ẩn khỏi Hub. Chỉ ẩn khi máy chủ báo thành công. */
    fun mailAction(items: List<HubItem>, action: vn.pvc.hub.sources.MailAction, onDone: (String) -> Unit) {
        viewModelScope.launch { onDone(doMailAction(items, action)) }
    }

    private suspend fun doMailAction(items: List<HubItem>, action: vn.pvc.hub.sources.MailAction): String {
        val store = AccountStore(getApplication<Application>())
        val mails = items.filter { it.source == Source.EMAIL.name }
        if (mails.isEmpty()) return "Chỉ email mới dọn được trên máy chủ."
        var ok = 0
        var error: String? = null
        for ((addr, group) in mails.groupBy { it.account }) {
            val acc = store.accounts().firstOrNull { it.address.equals(addr, true) }
            if (acc == null) {
                error = "Không tìm thấy tài khoản $addr"
                continue
            }
            val uids = group.mapNotNull { it.key.substringAfterLast(':').toLongOrNull() }
            val err = vn.pvc.hub.sources.MailActions.run(acc, uids, action)
            if (err == null) {
                dao.hideByIds(group.map { it.id })
                group.forEach { SnoozeWorker.cancel(getApplication<Application>(), it.id) }
                ok += group.size
            } else {
                error = err
            }
        }
        HubWidgetProvider.refresh(getApplication<Application>())
        val what = when (action) {
            vn.pvc.hub.sources.MailAction.ARCHIVE -> "lưu trữ"
            vn.pvc.hub.sources.MailAction.TRASH -> "chuyển vào Thùng rác"
            vn.pvc.hub.sources.MailAction.SPAM -> "báo spam"
        }
        return (if (ok > 0) "Đã $what $ok thư trên máy chủ." else "") +
            (if (error != null) (if (ok > 0) " " else "") + error else "")
    }

    /** Lưu trữ trên máy chủ mọi email đang hoãn rồi ẩn tất cả tin hoãn. */
    fun archiveAllSnoozed(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val list = dao.snoozedNow(System.currentTimeMillis())
            val msg = doMailAction(list, vn.pvc.hub.sources.MailAction.ARCHIVE)
            // Email lưu trữ lỗi thì giữ lại để thử lại; tin không phải email chỉ ẩn trong Hub.
            val failed = msg.contains("Không") || msg.contains("Sai")
            if (!failed) dao.hideAllSnoozed(System.currentTimeMillis())
            else dao.hideNonEmailSnoozed(System.currentTimeMillis())
            now.value = System.currentTimeMillis()
            onDone(msg)
        }
    }

    fun unsnoozeAll() {
        viewModelScope.launch {
            dao.unsnoozeAll(System.currentTimeMillis())
            now.value = System.currentTimeMillis()
            HubWidgetProvider.refresh(getApplication<Application>())
        }
    }

    fun hideAllSnoozed() {
        viewModelScope.launch {
            dao.hideAllSnoozed(System.currentTimeMillis())
            now.value = System.currentTimeMillis()
            HubWidgetProvider.refresh(getApplication<Application>())
        }
    }

    fun toggleStar(item: HubItem) {
        val key = Vip.key(item)
        val next = vips.value.toMutableSet()
        if (!next.add(key)) next.remove(key)
        prefs.vipSenders = next
        vips.value = next
    }

    fun markAllRead() {
        viewModelScope.launch {
            dao.markAllRead()
            HubWidgetProvider.refresh(getApplication<Application>())
        }
    }

    fun clearAll() {
        viewModelScope.launch { dao.deleteAll() }
    }
}
