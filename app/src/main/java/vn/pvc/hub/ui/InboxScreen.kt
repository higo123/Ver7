package vn.pvc.hub.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.widget.Toast
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.pvc.hub.data.HubItem
import vn.pvc.hub.data.Source
import vn.pvc.hub.data.Vip
import vn.pvc.hub.sources.MailAction
import vn.pvc.hub.sources.Otp
import vn.pvc.hub.sources.copyCode
import vn.pvc.hub.sources.Perms
import vn.pvc.hub.sources.ReplyRegistry
import vn.pvc.hub.sources.SetupState
import vn.pvc.hub.sources.SmsSender
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface InboxRow {
    data class Header(val title: String) : InboxRow
    data class Entry(val item: HubItem, val group: List<HubItem> = listOf(item)) : InboxRow
}

private val PHONE_SOURCES = listOf(Source.SMS, Source.CALL)
private val CORE_CHAT = listOf(Source.ZALO, Source.MESSENGER, Source.TELEGRAM)
private val SOCIAL = listOf(
    Source.FACEBOOK, Source.INSTAGRAM, Source.THREADS, Source.TIKTOK, Source.X, Source.LINKEDIN
)
private val SOCIAL_ALWAYS = listOf(Source.FACEBOOK, Source.INSTAGRAM)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    vm: HubViewModel,
    setup: SetupState,
    onOpenSetup: () -> Unit,
    onCompose: () -> Unit,
    onReply: (HubItem) -> Unit,
    onRead: (HubItem) -> Unit
) {
    val list by vm.inbox.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val accountUnread by vm.accountUnread.collectAsStateWithLifecycle()
    val snoozedCount by vm.snoozedCount.collectAsStateWithLifecycle()
    val priorityUnread by vm.priorityUnread.collectAsStateWithLifecycle()
    val vips by vm.vips.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val counts by vm.unreadCounts.collectAsStateWithLifecycle()
    val present by vm.present.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val rows = remember(list, view) { buildRows(list, grouping = view != InboxView.Snoozed) }
    val totalUnread = counts.values.sum()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Các app chat cài thêm (WhatsApp, Viber, ...) chỉ hiện trong menu khi đã có tin.
    val otherChat = Source.entries.filter {
        it != Source.EMAIL && it !in PHONE_SOURCES && it !in CORE_CHAT && it !in SOCIAL && it.name in present
    }
    // Facebook, Instagram luôn hiện; các mạng khác chỉ hiện khi đã có thông báo.
    val socialList = SOCIAL_ALWAYS + SOCIAL.filter { it !in SOCIAL_ALWAYS && it.name in present }

    val select: (InboxView) -> Unit = { v ->
        vm.view.value = v
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(Unit) { vm.refresh() }

    // Trả lời nhanh: SMS (cần quyền gửi SMS) và tin chat (qua ô trả lời của thông báo).
    var smsTarget by remember { mutableStateOf<HubItem?>(null) }
    var pendingSms by remember { mutableStateOf<HubItem?>(null) }
    var chatTarget by remember { mutableStateOf<HubItem?>(null) }
    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            smsTarget = pendingSms
        } else {
            Toast.makeText(context, "Chưa cấp quyền gửi SMS", Toast.LENGTH_SHORT).show()
        }
        pendingSms = null
    }
    smsTarget?.let { t ->
        ReplyDialog(
            title = "Trả lời ${t.sender} (SMS)",
            onDismiss = { smsTarget = null },
            onSend = { text ->
                val err = SmsSender.send(context, t.replyTo, text)
                if (err == null) Toast.makeText(context, "Đã gửi SMS", Toast.LENGTH_SHORT).show()
                err
            }
        )
    }
    chatTarget?.let { t ->
        ReplyDialog(
            title = "Trả lời ${t.sender}",
            onDismiss = { chatTarget = null },
            onSend = { text ->
                val err = ReplyRegistry.send(context, t.key, text)
                if (err == null) {
                    vm.markRead(t)
                    Toast.makeText(context, "Đã gửi", Toast.LENGTH_SHORT).show()
                }
                err
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DrawerHeader(totalUnread)
                    DrawerEntry("Tất cả", null, totalUnread, view == InboxView.All) { select(InboxView.All) }
                    DrawerEntry("Ưu tiên ★", Color(0xFFF9A825), priorityUnread, view == InboxView.Priority) {
                        select(InboxView.Priority)
                    }
                    DrawerEntry("Đã hoãn", Color(0xFF78909C), snoozedCount, view == InboxView.Snoozed) {
                        select(InboxView.Snoozed)
                    }

                    DrawerSection("Email")
                    if (accounts.size != 1) {
                        DrawerEntry(
                            if (accounts.isEmpty()) "Email" else "Tất cả email",
                            SourceStyle.color(Source.EMAIL.name),
                            counts[Source.EMAIL.name] ?: 0,
                            view == InboxView.Src(Source.EMAIL)
                        ) { select(InboxView.Src(Source.EMAIL)) }
                    }
                    accounts.forEach { addr ->
                        DrawerEntry(
                            addr, SourceStyle.color(Source.EMAIL.name),
                            accountUnread[addr] ?: 0, view == InboxView.Account(addr)
                        ) { select(InboxView.Account(addr)) }
                    }

                    DrawerSection("Điện thoại")
                    PHONE_SOURCES.forEach { s ->
                        DrawerEntry(
                            s.label, SourceStyle.color(s.name),
                            counts[s.name] ?: 0, view == InboxView.Src(s)
                        ) { select(InboxView.Src(s)) }
                    }

                    DrawerSection("Chat")
                    (CORE_CHAT + otherChat).forEach { s ->
                        DrawerEntry(
                            s.label, SourceStyle.color(s.name),
                            counts[s.name] ?: 0, view == InboxView.Src(s)
                        ) { select(InboxView.Src(s)) }
                    }

                    DrawerSection("Mạng xã hội")
                    socialList.forEach { s ->
                        DrawerEntry(
                            s.label, SourceStyle.color(s.name),
                            counts[s.name] ?: 0, view == InboxView.Src(s)
                        ) { select(InboxView.Src(s)) }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text("Thiết lập và hướng dẫn") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenSetup()
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        badge = {
                            if (!setup.allDone) {
                                Text(
                                    "${setup.total - setup.doneCount}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Thiết kế bởi P.V.C",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    NavigationBarItem(
                        selected = view !is InboxView.Priority && view !is InboxView.Snoozed,
                        onClick = { vm.view.value = InboxView.All },
                        icon = { Icon(Icons.Default.Email, contentDescription = null) },
                        label = { Text("Hộp thư") },
                        alwaysShowLabel = true
                    )
                    NavigationBarItem(
                        selected = view == InboxView.Priority,
                        onClick = { vm.view.value = InboxView.Priority },
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = { Text("Ưu tiên") },
                        alwaysShowLabel = true
                    )
                    NavigationBarItem(
                        selected = view == InboxView.Snoozed,
                        onClick = { vm.view.value = InboxView.Snoozed },
                        icon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                        label = { Text("Đã hoãn") },
                        alwaysShowLabel = true
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onOpenSetup,
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Thiết lập") },
                        alwaysShowLabel = true
                    )
                }
            },
            floatingActionButton = {
                if (setup.gmail) {
                    FloatingActionButton(onClick = onCompose, containerColor = BrandGreen) {
                        Icon(
                            Icons.Default.Create,
                            contentDescription = "Soạn email",
                            tint = Color.White
                        )
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                viewTitle(view),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Mở menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) {
                                vm.query.value = ""
                                vm.unreadOnly.value = false
                                vm.dayRange.value = 0
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
                        }
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
                        }
                        IconButton(onClick = { vm.markAllRead() }) {
                            Icon(Icons.Default.Done, contentDescription = "Đánh dấu đã đọc hết")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())

                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        placeholder = { Text("Tìm người gửi hoặc nội dung") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    val unreadOnly by vm.unreadOnly.collectAsStateWithLifecycle()
                    val dayRange by vm.dayRange.collectAsStateWithLifecycle()
                    val searchAll by vm.searchAll.collectAsStateWithLifecycle()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = searchAll,
                            onClick = { vm.searchAll.value = !searchAll },
                            label = { Text("Mọi nguồn") }
                        )
                        androidx.compose.material3.FilterChip(
                            selected = unreadOnly,
                            onClick = { vm.unreadOnly.value = !unreadOnly },
                            label = { Text("Chưa đọc") }
                        )
                        listOf(1 to "Hôm nay", 7 to "7 ngày", 30 to "30 ngày").forEach { (d, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = dayRange == d,
                                onClick = { vm.dayRange.value = if (dayRange == d) 0 else d },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                if (!setup.allDone) {
                    SetupBanner(setup, onOpenSetup)
                }
                val upd by vm.update.collectAsStateWithLifecycle()
                upd?.let { u ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Có bản mới ${u.versionName}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (u.apkUrl.startsWith("https://")) {
                            androidx.compose.material3.TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(u.apkUrl)
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }) { Text("Tải về") }
                        }
                    }
                }
                SummaryStrip(
                    totalUnread, priorityUnread, snoozedCount, view,
                    onUnread = { vm.view.value = InboxView.All },
                    onPriority = { vm.view.value = InboxView.Priority },
                    onSnoozed = { vm.view.value = InboxView.Snoozed }
                )

                if (view == InboxView.Snoozed && rows.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.unsnoozeAll() }) { Text("Bỏ hoãn tất cả") }
                            OutlinedButton(onClick = { vm.hideAllSnoozed() }) { Text("Ẩn tất cả") }
                        }
                        OutlinedButton(onClick = {
                            vm.archiveAllSnoozed { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                        }) { Text("Ẩn và lưu trữ email trên Gmail") }
                    }
                }

                if (rows.isEmpty()) {
                    EmptyState(setup, view, onOpenSetup)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(
                            rows,
                            key = { row ->
                                when (row) {
                                    is InboxRow.Header -> "h:${row.title}"
                                    is InboxRow.Entry -> "i:${row.item.id}"
                                }
                            }
                        ) { row ->
                            when (row) {
                                is InboxRow.Header -> DayHeader(row.title)
                                is InboxRow.Entry -> {
                                    val cur: InboxRow.Entry = row
                                    val entry by rememberUpdatedState(cur)
                                    val itemRow: @Composable () -> Unit = {
                                        ItemRow(
                                            item = row.item,
                                            count = row.group.size,
                                            unread = row.group.any { !it.read },
                                            starred = Vip.isStarred(row.item, vips, rules),
                                            snoozedView = view == InboxView.Snoozed,
                                            onClick = {
                                                vm.markReadAll(row.group)
                                                // Email đọc ngay trong Hub; nguồn khác mở app gốc.
                                                if (row.item.source == Source.EMAIL.name) {
                                                    onRead(row.item)
                                                } else {
                                                    openSource(context, row.item)
                                                }
                                            },
                                            onReply = if (row.item.source == Source.EMAIL.name &&
                                                row.item.replyTo.isNotBlank()
                                            ) {
                                                { onReply(row.item) }
                                            } else {
                                                null
                                            },
                                            onSmsReply = if (row.item.source == Source.SMS.name &&
                                                row.item.replyTo.isNotBlank()
                                            ) {
                                                {
                                                    if (Perms.has(context, Manifest.permission.SEND_SMS)) {
                                                        smsTarget = row.item
                                                    } else {
                                                        pendingSms = row.item
                                                        smsPermLauncher.launch(Manifest.permission.SEND_SMS)
                                                    }
                                                }
                                            } else {
                                                null
                                            },
                                            onQuickReply = if (ReplyRegistry.has(row.item.key)) {
                                                { chatTarget = row.item }
                                            } else {
                                                null
                                            },
                                            onSnooze = { until ->
                                                vm.snooze(row.item, until)
                                                Toast.makeText(
                                                    context,
                                                    "Đã hoãn tới " + SimpleDateFormat(
                                                        "HH:mm dd/MM", Locale.getDefault()
                                                    ).format(Date(until)),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onUnsnooze = { vm.unsnooze(row.item) },
                                            onMailAction = if (row.item.source == Source.EMAIL.name) {
                                                { act ->
                                                    vm.mailAction(row.group, act) { msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            } else {
                                                null
                                            },
                                            onHide = {
                                                vm.hide(row.group)
                                                Toast.makeText(context, "Đã ẩn khỏi Hub", Toast.LENGTH_SHORT).show()
                                            },
                                            onStar = { vm.toggleStar(row.item) },
                                            onCopyOtp = { code ->
                                                copyCode(context, code)
                                                Toast.makeText(context, "Đã sao chép mã $code", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                    if (view == InboxView.Snoozed) {
                                        itemRow()
                                    } else {
                                        val swipe = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { v ->
                                                when (v) {
                                                    SwipeToDismissBoxValue.StartToEnd ->
                                                        vm.markReadAll(entry.group)
                                                    SwipeToDismissBoxValue.EndToStart -> {
                                                        vm.hide(entry.group)
                                                        Toast.makeText(
                                                            context,
                                                            "Đã ẩn khỏi Hub (thư gốc vẫn còn). Khôi phục trong Thiết lập.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    else -> Unit
                                                }
                                                false
                                            }
                                        )
                                        SwipeToDismissBox(
                                            state = swipe,
                                            backgroundContent = { SwipeBackground(swipe.dismissDirection) }
                                        ) {
                                            Box(Modifier.background(MaterialTheme.colorScheme.background)) { itemRow() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun viewTitle(v: InboxView): String = when (v) {
    InboxView.All -> "PVC-Hub"
    InboxView.Priority -> "Ưu tiên"
    InboxView.Snoozed -> "Đã hoãn"
    is InboxView.Account -> v.address
    is InboxView.Src -> v.source.label
}

@Composable
private fun SwipeBackground(dir: SwipeToDismissBoxValue) {
    val read = dir == SwipeToDismissBoxValue.StartToEnd
    val snooze = dir == SwipeToDismissBoxValue.EndToStart
    Box(
        Modifier
            .fillMaxSize()
            .background(
                when {
                    read -> MaterialTheme.colorScheme.primaryContainer
                    snooze -> Color(0xFFFFD9D6)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 20.dp),
        contentAlignment = if (read) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        if (read) Text("Đã đọc", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        if (snooze) Text("Ẩn", fontWeight = FontWeight.Bold, color = Color(0xFF7A1B14))
    }
}

@Composable
private fun SummaryStrip(
    unread: Int,
    priority: Int,
    snoozed: Int,
    view: InboxView,
    onUnread: () -> Unit,
    onPriority: () -> Unit,
    onSnoozed: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .height(IntrinsicSize.Min)
    ) {
        StatCell(unread, "Chưa đọc", view == InboxView.All, Modifier.weight(1f), onUnread)
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        StatCell(priority, "Ưu tiên", view == InboxView.Priority, Modifier.weight(1f), onPriority)
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        StatCell(snoozed, "Đã hoãn", view == InboxView.Snoozed, Modifier.weight(1f), onSnoozed)
    }
}

@Composable
private fun StatCell(count: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (count > 999) "999+" else "$count",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                count == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            }
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DrawerHeader(totalUnread: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BrandGreen)
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Text(
            "PVC-Hub",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (totalUnread > 0) "$totalUnread tin chưa đọc" else "Không có tin chưa đọc",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerEntry(
    label: String,
    color: Color?,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                Modifier
                    .size(14.dp)
                    .background(color ?: MaterialTheme.colorScheme.primary, CircleShape)
            )
        },
        badge = {
            if (count > 0) Text("$count", fontWeight = FontWeight.Bold)
        },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun SetupBanner(setup: SetupState, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Còn ${setup.total - setup.doneCount} bước thiết lập chưa xong",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Chạm để xem hướng dẫn từng bước.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun EmptyState(setup: SetupState, view: InboxView, onOpenSetup: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            when (view) {
                InboxView.All -> "Hộp thư trống"
                InboxView.Priority -> "Chưa có tin ưu tiên"
                InboxView.Snoozed -> "Không có tin nào đang hoãn"
                else -> "Chưa có tin ở mục " + viewTitle(view)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (setup.allDone) {
                "Tin chat mới, email, SMS và cuộc gọi nhỡ sẽ hiện ở đây."
            } else {
                "Hãy hoàn tất các bước thiết lập để PVC-Hub bắt đầu gom tin."
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!setup.allDone) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSetup) { Text("Mở hướng dẫn thiết lập") }
        }
    }
}

@Composable
private fun ItemRow(
    item: HubItem,
    count: Int,
    unread: Boolean,
    starred: Boolean,
    snoozedView: Boolean,
    onClick: () -> Unit,
    onReply: (() -> Unit)?,
    onSmsReply: (() -> Unit)?,
    onQuickReply: (() -> Unit)?,
    onSnooze: (Long) -> Unit,
    onUnsnooze: () -> Unit,
    onHide: () -> Unit,
    onMailAction: ((MailAction) -> Unit)?,
    onStar: () -> Unit,
    onCopyOtp: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val otp = remember(item.preview) { Otp.find(item.preview) }
    val color = SourceStyle.color(item.source)
    val label = if (item.source == Source.OTHER.name || item.source == Source.EMAIL.name) {
        item.account
    } else {
        Source.entries.firstOrNull { it.name == item.source }?.label ?: item.source
    }
    val nameWeight = if (unread) FontWeight.Bold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Avatar(item.sender, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (starred) Text("★ ", color = Color(0xFFE09B00), fontWeight = FontWeight.Bold)
                Text(
                    item.sender,
                    fontWeight = nameWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(item.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unread) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                item.preview,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (snoozedView) {
                Text(
                    "Hiện lại lúc " + SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
                        .format(Date(item.snoozedUntil)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(label, color)
                if (count > 1) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$count tin",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (otp != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Sao chép mã $otp",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                            .clickable { onCopyOtp(otp) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        if (unread) {
            Spacer(Modifier.width(8.dp))
            Spacer(
                Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (starred) "Bỏ sao người gửi" else "Gắn sao người gửi") },
                    onClick = {
                        menu = false
                        onStar()
                    }
                )
                if (onMailAction != null) {
                    DropdownMenuItem(
                        text = { Text("Lưu trữ trên Gmail") },
                        onClick = {
                            menu = false
                            onMailAction(MailAction.ARCHIVE)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Xóa (vào Thùng rác)") },
                        onClick = {
                            menu = false
                            onMailAction(MailAction.TRASH)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Báo spam") },
                        onClick = {
                            menu = false
                            onMailAction(MailAction.SPAM)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Ẩn khỏi Hub") },
                    onClick = {
                        menu = false
                        onHide()
                    }
                )
                if (snoozedView) {
                    DropdownMenuItem(
                        text = { Text("Bỏ hoãn, hiện lại ngay") },
                        onClick = {
                            menu = false
                            onUnsnooze()
                        }
                    )
                }
                if (!snoozedView && onReply != null) {
                    DropdownMenuItem(
                        text = { Text("Trả lời") },
                        onClick = {
                            menu = false
                            onReply()
                        }
                    )
                }
                if (!snoozedView && onSmsReply != null) {
                    DropdownMenuItem(
                        text = { Text("Trả lời SMS") },
                        onClick = {
                            menu = false
                            onSmsReply()
                        }
                    )
                }
                if (!snoozedView && onQuickReply != null) {
                    DropdownMenuItem(
                        text = { Text("Trả lời nhanh") },
                        onClick = {
                            menu = false
                            onQuickReply()
                        }
                    )
                }
                if (!snoozedView) SNOOZE_OPTIONS.forEach { (label, calc) ->
                    DropdownMenuItem(
                        text = { Text("Hoãn: $label") },
                        onClick = {
                            menu = false
                            onSnooze(calc())
                        }
                    )
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Các mức Snooze: tên hiển thị và cách tính thời điểm tin hiện lại. */
private val SNOOZE_OPTIONS: List<Pair<String, () -> Long>> = listOf(
    "1 giờ" to { System.currentTimeMillis() + 3_600_000L },
    "3 giờ" to { System.currentTimeMillis() + 3 * 3_600_000L },
    "tối nay (18:00)" to { nextAt(18, 0) },
    "sáng mai (8:00)" to { nextAt(8, 1) },
    "thứ Hai tuần sau (8:00)" to { nextMonday8() }
)

/** Thời điểm `hour`:00 gần nhất trong tương lai, cộng thêm tối thiểu `minDays` ngày. */
private fun nextAt(hour: Int, minDays: Int): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, minDays)
    c.set(Calendar.HOUR_OF_DAY, hour)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
}

private fun nextMonday8(): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, 1)
    while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) c.add(Calendar.DAY_OF_YEAR, 1)
    c.set(Calendar.HOUR_OF_DAY, 8)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun buildRows(list: List<HubItem>, grouping: Boolean): List<InboxRow> {
    val byDay = LinkedHashMap<String, MutableList<HubItem>>()
    for (item in list) byDay.getOrPut(dayLabel(item.time)) { mutableListOf() }.add(item)
    val out = ArrayList<InboxRow>()
    for ((label, items) in byDay) {
        out.add(InboxRow.Header(label))
        // Tin chat/SMS cùng người gửi trong một ngày gộp thành một dòng.
        val index = HashMap<String, Int>()
        val entries = ArrayList<InboxRow.Entry>()
        for (item in items) {
            val groupable = grouping && item.source != Source.EMAIL.name && item.source != Source.CALL.name
            if (!groupable) {
                entries.add(InboxRow.Entry(item, listOf(item)))
                continue
            }
            val k = item.source + "|" + item.sender.lowercase()
            val at = index[k]
            if (at == null) {
                index[k] = entries.size
                entries.add(InboxRow.Entry(item, listOf(item)))
            } else {
                val e = entries[at]
                entries[at] = InboxRow.Entry(e.item, e.group + item)
            }
        }
        out.addAll(entries)
    }
    return out
}

private fun dayLabel(time: Long): String {
    if (DateUtils.isToday(time)) return "Hôm nay"
    if (DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS)) return "Hôm qua"
    return SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi")).format(Date(time))
}

private fun formatTime(time: Long): String {
    val pattern = if (DateUtils.isToday(time)) "HH:mm" else "dd/MM HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}

/** Mở ứng dụng gốc của mục được chạm. */
private fun openSource(context: Context, item: HubItem) {
    try {
        val intent: Intent? = when (item.source) {
            Source.SMS.name -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            Source.CALL.name -> Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)
            else -> item.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
        }
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    } catch (e: Exception) {
        // Không có ứng dụng phù hợp: bỏ qua.
    }
}
