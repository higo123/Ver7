package vn.pvc.hub.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.sources.MailFreq
import vn.pvc.hub.sources.SyncRunner

/** Thẻ chọn tần suất nhận email và các tùy chọn liên quan. */
@Composable
fun MailSettingsCard(prefs: AppPrefs, onChanged: () -> Unit) {
    val context = LocalContext.current
    var interval by remember { mutableIntStateOf(prefs.mailIntervalMin) }
    var limit by remember { mutableIntStateOf(prefs.mailLimit) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnly) }
    var notify by remember { mutableStateOf(prefs.notifyMail) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onChanged() }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nhận email: tần suất và tùy chọn", style = MaterialTheme.typography.titleMedium)
            Text(
                "Chọn bao lâu PVC-Hub kiểm tra email một lần. Tức thì, 5 và 10 phút cần một thông báo " +
                    "cố định trên thanh trạng thái và tốn pin hơn. Mức này cũng áp dụng cho SMS và cuộc gọi nhỡ " +
                    "(riêng chế độ Thủ công thì tắt tự động hoàn toàn).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MailFreq.OPTIONS.forEach { (min, label) ->
                OptionRow(label, interval == min) {
                    interval = min
                    prefs.mailIntervalMin = min
                    if (MailFreq.needsService(min)) askNotificationPermission()
                    SyncRunner.schedule(context)
                    onChanged()
                }
            }

            Text("Số thư lấy về mỗi lần (mỗi tài khoản)", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MailFreq.LIMITS.forEach { n ->
                    Row(
                        Modifier.clickable {
                            limit = n
                            prefs.mailLimit = n
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = limit == n, onClick = null)
                        Spacer(Modifier.width(6.dp))
                        Text("$n")
                    }
                }
            }

            SwitchRow(
                "Chỉ đồng bộ khi có Wi-Fi",
                "Tiết kiệm dữ liệu di động. Bấm Làm mới bằng tay thì vẫn chạy.",
                wifiOnly
            ) {
                wifiOnly = it
                prefs.wifiOnly = it
                SyncRunner.schedule(context)
            }
            SwitchRow(
                "Báo khi có thư mới",
                "Hiện thông báo Android cho thư mới đến (không báo thư cũ).",
                notify
            ) {
                notify = it
                prefs.notifyMail = it
                if (it) askNotificationPermission()
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
