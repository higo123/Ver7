package vn.pvc.hub.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.Source
import vn.pvc.hub.sources.NotificationCaptureService

private data class AppEntry(val pkg: String, val label: String)

private fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
    context.packageManager.getPackageInfo(pkg, 0)
    true
}.getOrDefault(false)

private fun loadLaunchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .filter { it.pkg != context.packageName && it.pkg !in NotificationCaptureService.TRACKED.keys }
        .sortedBy { it.label.lowercase() }
}

/** Thẻ quản lý các app chat được nhận tin, gồm cả app cài thêm về sau. */
@Composable
fun ChatAppsCard(prefs: AppPrefs) {
    val context = LocalContext.current
    var auto by remember { mutableStateOf(prefs.autoDetect) }
    var extras by remember { mutableStateOf(prefs.extraApps) }
    var disabled by remember { mutableStateOf(prefs.disabledSources) }
    var showPicker by remember { mutableStateOf(false) }

    // Nhóm các gói có sẵn theo từng nguồn, rồi xem nguồn nào đã cài trên máy.
    val builtIn = remember {
        NotificationCaptureService.TRACKED.entries
            .groupBy({ it.value }, { it.key })
            .map { (source, pkgs) -> source to pkgs.any { isInstalled(context, it) } }
    }

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ứng dụng được nhận tin (chat và mạng xã hội)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Các app dưới đây tự được nhận tin ngay khi cài (chỉ cần bước 4 đã bật). " +
                    "Muốn thôi nhận từ app nào, tắt công tắc của app đó. Facebook, Instagram… " +
                    "cũng phải đang bật thông báo trong chính app đó.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            builtIn.forEach { (source, installed) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.label)
                        Text(
                            if (installed) "đã cài trên máy" else "chưa cài",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (installed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Switch(
                        checked = source.name !in disabled,
                        onCheckedChange = { on ->
                            val next = if (on) disabled - source.name else disabled + source.name
                            disabled = next
                            prefs.disabledSources = next
                        }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tự nhận app nhắn tin mới cài", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Mọi app có thông báo loại \"tin nhắn\" (Skype, Discord, Slack, Kakao…) sẽ tự " +
                            "xuất hiện trong mục \"Ứng dụng khác\" khi có tin đến. App SMS và Gmail " +
                            "được loại trừ để khỏi trùng.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = auto, onCheckedChange = {
                    auto = it
                    prefs.autoDetect = it
                })
            }

            Text(
                if (extras.isEmpty()) {
                    "Chưa thêm app nào thủ công."
                } else {
                    "Đã thêm thủ công ${extras.size} app."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { showPicker = true }) { Text("Chọn thêm ứng dụng khác") }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            selected = extras,
            onToggle = { pkg ->
                val next = if (pkg in extras) extras - pkg else extras + pkg
                extras = next
                prefs.extraApps = next
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun AppPickerDialog(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember { loadLaunchableApps(context) }
    var search by remember { mutableStateOf("") }
    val shown = remember(search, apps) {
        if (search.isBlank()) apps else apps.filter { it.label.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn app nhận tin") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    placeholder = { Text("Tìm tên ứng dụng") },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.pkg }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(app.pkg) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = app.pkg in selected, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } }
    )
}

/** Nhật ký thông báo: để biết chính xác app chat có gửi thông báo cho Hub hay không. */
@Composable
fun NotifLogCard(prefs: AppPrefs, log: String, onChanged: () -> Unit) {
    var logAll by remember { mutableStateOf(prefs.logAll) }

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Nhật ký thông báo (chẩn đoán)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Mỗi khi một app chat đang theo dõi gửi thông báo, PVC-Hub ghi lại một dòng ở đây. " +
                    "Nếu bạn nhận tin Zalo mà không thấy dòng nào, nghĩa là Zalo không gửi thông " +
                    "báo cho Hub (kiểm tra bước 4 và thông báo trong chính Zalo). Dòng cuối ghi " +
                    "\"ĐÃ NHẬN\" là tin đã vào hộp thư.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ghi cả thông báo của mọi app khác (để tìm đúng tên gói của Zalo)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Switch(checked = logAll, onCheckedChange = {
                    logAll = it
                    prefs.logAll = it
                })
            }
            Text(
                if (log.isBlank()) "(Chưa có dòng nào)" else log,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChanged) { Text("Tải lại nhật ký") }
                OutlinedButton(onClick = {
                    prefs.clearNotifLog()
                    onChanged()
                }) { Text("Xóa nhật ký") }
            }
        }
    }
}
