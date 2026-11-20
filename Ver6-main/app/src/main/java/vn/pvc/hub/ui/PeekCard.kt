package vn.pvc.hub.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.sources.PeekService

/** Thẻ bật Peek: tay cầm nổi ở mép màn hình để xem nhanh tin chưa đọc. */
@Composable
fun PeekCard(prefs: AppPrefs) {
    val context = LocalContext.current
    var resume by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resume++ }
    val granted = remember(resume) { Settings.canDrawOverlays(context) }
    var enabled by remember { mutableStateOf(prefs.peekEnabled) }
    var side by remember { mutableIntStateOf(prefs.peekSide) }
    var size by remember { mutableIntStateOf(prefs.peekSize) }
    var alpha by remember { mutableIntStateOf(prefs.peekAlpha) }
    var panelSize by remember { mutableIntStateOf(prefs.peekPanelSize) }

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Peek (xem nhanh tin ở mép màn hình)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hiện một tay cầm nhỏ màu xanh ở mép màn hình, trên mọi app. Chạm vào để xem các tin chưa đọc, " +
                    "kéo lên xuống để đổi chỗ. Cần quyền \"Hiển thị trên các ứng dụng khác\" và một thông báo nhỏ " +
                    "cố định. Nếu tay cầm ở mép trái vướng cử chỉ quay lại của Android, đổi sang mép phải.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                Text(
                    "Chưa cấp quyền hiển thị trên ứng dụng khác.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Mở trang cấp quyền") }
                Text(
                    "Cấp xong, quay lại đây và bật công tắc bên dưới.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bật Peek", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled && granted,
                    enabled = granted,
                    onCheckedChange = {
                        enabled = it
                        prefs.peekEnabled = it
                        PeekService.apply(context)
                    }
                )
            }
            Text("Độ lớn của nút: $size%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = size.toFloat(),
                onValueChange = {
                    size = it.toInt()
                    prefs.peekSize = size
                },
                onValueChangeFinished = { PeekService.apply(context) },
                valueRange = 60f..200f
            )
            Text("Độ lớn của khung hiện tin: $panelSize%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = panelSize.toFloat(),
                onValueChange = {
                    panelSize = it.toInt()
                    prefs.peekPanelSize = panelSize
                },
                valueRange = 80f..160f
            )
            Text("Độ đậm của nút: $alpha% (thấp hơn thì nút trong suốt hơn)", fontWeight = FontWeight.SemiBold)
            Slider(
                value = alpha.toFloat(),
                onValueChange = {
                    alpha = it.toInt()
                    prefs.peekAlpha = alpha
                },
                onValueChangeFinished = { PeekService.apply(context) },
                valueRange = 30f..100f
            )
            Text("Mép đặt tay cầm", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(0 to "Trái", 1 to "Phải").forEach { (v, label) ->
                    Row(
                        Modifier.clickable {
                            side = v
                            prefs.peekSide = v
                            PeekService.apply(context)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = side == v, onClick = null)
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                }
            }
        }
    }
}
