package vn.pvc.hub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.pvc.hub.sources.BackupManager

/** Thẻ sao lưu và khôi phục dữ liệu ra file. */
@Composable
fun BackupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            status = runCatching { "Đã xuất ${BackupManager.export(context, uri)} mục ra file." }
                .getOrElse { "Xuất không được: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            status = runCatching { "Đã nhập ${BackupManager.import(context, uri)} mục mới." }
                .getOrElse { "Nhập không được (file không đúng định dạng?): ${it.message}" }
        }
    }

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sao lưu và khôi phục", style = MaterialTheme.typography.titleMedium)
            Text(
                "Xuất toàn bộ tin trong Hub ra một file để cất giữ hoặc chuyển sang máy khác. " +
                    "File không chứa mật khẩu email, nên trên máy mới vẫn phải nhập lại tài khoản ở bước 1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("pvc-hub-backup.json") }) { Text("Xuất ra file") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Nhập từ file") }
            }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
