package vn.pvc.hub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.pvc.hub.data.AccountStore
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.HubItem
import vn.pvc.hub.sources.Perms

enum class Screen { INBOX, SETUP, COMPOSE, READ }

@Composable
fun HubApp(vm: HubViewModel = viewModel()) {
    val context = LocalContext.current
    val store = remember { AccountStore(context) }
    val prefs = remember { AppPrefs(context) }

    // Lần đầu mở app: vào thẳng màn hình hướng dẫn thiết lập.
    var screen by remember {
        mutableStateOf(if (prefs.onboardingDone) Screen.INBOX else Screen.SETUP)
    }

    var draft by remember { mutableStateOf(ComposeDraft()) }
    var readItem by remember { mutableStateOf<HubItem?>(null) }

    // Đọc lại trạng thái quyền mỗi khi quay lại app (vd. từ màn hình Cài đặt Android).
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val setup = remember(tick) { Perms.readSetup(context, store) }
    val notifLog = remember(tick) { prefs.notifLog }

    val report by vm.report.collectAsStateWithLifecycle()
    val lastSync by vm.lastSync.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()

    // Quay lại hộp thư thì nạp lại sao/quy tắc/tài khoản vừa chỉnh.
    androidx.compose.runtime.LaunchedEffect(screen, tick) { vm.reloadSettings() }

    when (screen) {
        Screen.SETUP -> {
            val leave = {
                prefs.onboardingDone = true
                screen = Screen.INBOX
            }
            BackHandler { leave() }
            SetupScreen(
                store = store,
                prefs = prefs,
                notifLog = notifLog,
                syncing = syncing,
                setup = setup,
                report = report,
                lastSync = lastSync,
                onBack = { leave() },
                onChanged = { tick++ },
                onSync = { vm.refresh() },
                onClearData = { vm.clearAll() },
                onCheckUpdate = { cb -> vm.checkUpdate(true, cb) }
            )
        }
        Screen.INBOX -> InboxScreen(
            vm = vm,
            setup = setup,
            onOpenSetup = { screen = Screen.SETUP },
            onRead = { item ->
                readItem = item
                screen = Screen.READ
            },
            onCompose = {
                draft = ComposeDraft()
                screen = Screen.COMPOSE
            },
            onReply = { item ->
                draft = ComposeDraft(
                    from = item.account,
                    to = item.replyTo,
                    subject = if (item.preview.startsWith("Re:", ignoreCase = true)) {
                        item.preview
                    } else {
                        "Re: ${item.preview}"
                    }
                )
                screen = Screen.COMPOSE
            }
        )
        Screen.READ -> {
            val item = readItem
            BackHandler { screen = Screen.INBOX }
            if (item == null) {
                screen = Screen.INBOX
            } else {
                ReadScreen(
                    item = item,
                    account = store.accounts().firstOrNull { it.address.equals(item.account, true) },
                    onBack = { screen = Screen.INBOX },
                    onReply = { it0 ->
                        draft = ComposeDraft(
                            from = it0.account,
                            to = it0.replyTo,
                            subject = if (it0.preview.startsWith("Re:", ignoreCase = true)) {
                                it0.preview
                            } else {
                                "Re: ${it0.preview}"
                            }
                        )
                        screen = Screen.COMPOSE
                    }
                )
            }
        }
        Screen.COMPOSE -> {
            BackHandler { screen = Screen.INBOX }
            ComposeScreen(
                accounts = store.accounts(),
                draft = draft,
                onBack = { screen = Screen.INBOX }
            )
        }
    }
}
