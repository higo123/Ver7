package vn.pvc.hub.ui

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.FilterRule
import vn.pvc.hub.data.HubDatabase
import kotlinx.coroutines.launch
import vn.pvc.hub.sources.HubWidgetProvider

private fun hhmm(min: Int) = String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60)

/** Giờ yên tĩnh và chỉ báo thư của người gắn sao. */
@Composable
fun QuietCard(prefs: AppPrefs) {
    val context = LocalContext.current
    var quiet by remember { mutableStateOf(prefs.quietEnabled) }
    var start by remember { mutableStateOf(prefs.quietStartMin) }
    var end by remember { mutableStateOf(prefs.quietEnd()) }
    var vipOnly by remember { mutableStateOf(prefs.notifyVipOnly) }

    fun pick(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
    }

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Giờ yên tĩnh và ưu tiên", style = MaterialTheme.typography.titleMedium)
            Text(
                "Trong giờ yên tĩnh Hub không hiện thông báo thư mới (thư vẫn được tải về). " +
                    "Người gửi gắn sao ★ lấy từ menu ba chấm của từng tin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bật giờ yên tĩnh", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = quiet, onCheckedChange = {
                    quiet = it
                    prefs.quietEnabled = it
                })
            }
            if (quiet) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Từ")
                    OutlinedButton(onClick = {
                        pick(start) {
                            start = it
                            prefs.quietStartMin = it
                        }
                    }) { Text(hhmm(start)) }
                    Text("đến")
                    OutlinedButton(onClick = {
                        pick(end) {
                            end = it
                            prefs.quietEndMin = it
                        }
                    }) { Text(hhmm(end)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Chỉ báo thư của người gắn sao ★",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = vipOnly, onCheckedChange = {
                    vipOnly = it
                    prefs.notifyVipOnly = it
                })
            }
        }
    }
}

private fun AppPrefs.quietEnd(): Int = quietEndMin

/** Khóa ứng dụng bằng vân tay hoặc khóa màn hình. */
@Composable
fun LockCard(prefs: AppPrefs) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(prefs.appLock) }

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Khóa ứng dụng", style = MaterialTheme.typography.titleMedium)
            Text(
                "Mở PVC-Hub phải quét vân tay, khuôn mặt hoặc nhập khóa màn hình (khóa màn hình dùng được từ Android 11). " +
                    "Khi bật, nội dung tin ở widget, Peek và thông báo bị ẩn, ảnh chụp màn hình bị chặn. " +
                    "Rời app quá 30 giây thì khóa lại.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bật khóa", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { want ->
                    val act = context as? FragmentActivity
                    if (!want) {
                        // Tắt khóa cũng phải xác thực để người khác cầm máy không tắt được.
                        if (act != null && AppLock.available(context)) {
                            AppLock.prompt(act, "Tắt khóa PVC-Hub", {
                                on = false
                                prefs.appLock = false
                                HubWidgetProvider.refresh(context)
                            }) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                        } else {
                            on = false
                            prefs.appLock = false
                            HubWidgetProvider.refresh(context)
                        }
                    } else if (act == null || !AppLock.available(context)) {
                        Toast.makeText(
                            context,
                            "Máy chưa cài vân tay, khuôn mặt hoặc khóa màn hình. Hãy cài trong Cài đặt Android trước.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        AppLock.prompt(act, "Bật khóa PVC-Hub", {
                            on = true
                            prefs.appLock = true
                            HubWidgetProvider.refresh(context)
                        }) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    }
                })
            }
        }
    }
}

/** Quy tắc lọc theo từ khóa: gắn sao hoặc ẩn khỏi Hub. */
@Composable
fun RulesCard(prefs: AppPrefs) {
    var rules by remember { mutableStateOf(prefs.rules) }
    var keyword by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(FilterRule.STAR) }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var hiddenCount by remember { mutableStateOf<Int?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        hiddenCount = HubDatabase.get(context).dao().hiddenCount()
    }

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quy tắc lọc", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tin có người gửi hoặc nội dung chứa từ khóa sẽ được gắn sao ★ hoặc ẩn khỏi Hub " +
                    "(không xóa khỏi máy chủ thư). Ví dụ: \"hóa đơn\" gắn sao, \"khuyến mãi\" ẩn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            rules.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (r.action == FilterRule.STAR) "★ Gắn sao: " else "Ẩn: ") + r.keyword,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        rules = rules - r
                        prefs.rules = rules
                    }) { Text("Xóa") }
                }
            }
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                singleLine = true,
                label = { Text("Từ khóa") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = action == FilterRule.STAR, onClick = { action = FilterRule.STAR })
                Text("Gắn sao")
                RadioButton(selected = action == FilterRule.HIDE, onClick = { action = FilterRule.HIDE })
                Text("Ẩn")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tin đã ẩn: " + (hiddenCount?.toString() ?: "..."), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    scope.launch {
                        HubDatabase.get(context).dao().unhideAll()
                        hiddenCount = 0
                        HubWidgetProvider.refresh(context)
                    }
                }) { Text("Khôi phục tất cả") }
            }
            Button(
                enabled = keyword.isNotBlank(),
                onClick = {
                    rules = rules + FilterRule(keyword.trim(), action)
                    prefs.rules = rules
                    keyword = ""
                }
            ) { Text("Thêm quy tắc") }
        }
    }
}

/** Tự dọn tin cũ khỏi Hub (không xóa trên máy chủ thư). */
@Composable
fun CleanCard(prefs: AppPrefs) {
    var days by remember { mutableStateOf(prefs.keepDays) }
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tự dọn tin cũ", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tin cũ hơn mốc đã chọn sẽ tự ẩn khỏi Hub mỗi lần đồng bộ. Chỉ ẩn trong Hub, " +
                    "thư trong Gmail, SMS trong máy không bị xóa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            listOf(0 to "Không tự dọn", 7 to "Giữ 7 ngày", 30 to "Giữ 30 ngày", 90 to "Giữ 90 ngày").forEach { (v, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            days = v
                            prefs.keepDays = v
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = days == v, onClick = null)
                    Text(label, modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
                }
            }
        }
    }
}

/** Chữ ký tự thêm vào thư mới và thư trả lời. */
@Composable
fun SignatureCard(prefs: AppPrefs) {
    var sig by remember { mutableStateOf(prefs.signature) }
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chữ ký email", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tự thêm vào cuối thư mới và thư trả lời (sửa hoặc xóa được khi soạn).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = sig,
                onValueChange = {
                    sig = it
                    prefs.signature = it
                },
                label = { Text("Chữ ký") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Kiểm tra bản mới của app (cài bằng APK nên không có Google Play tự cập nhật). */
@Composable
fun UpdateCard(prefs: AppPrefs, onCheck: (String) -> Unit, result: String) {
    var url by remember { mutableStateOf(prefs.updateUrl) }
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cập nhật ứng dụng", style = MaterialTheme.typography.titleMedium)
            Text(
                "App tự kiểm tra bản mới mỗi ngày một lần và báo ở hộp thư, bấm Tải về rồi tự cài APK như trước. " +
                    "Địa chỉ kiểm tra đã có sẵn trong app, bạn không cần nhập gì. " +
                    "Ô bên dưới chỉ dùng khi muốn trỏ tới tệp JSON riêng (xóa trống để quay về địa chỉ có sẵn).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    prefs.updateUrl = it
                },
                label = { Text("Địa chỉ tệp JSON (không bắt buộc)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { onCheck(url) }) { Text("Kiểm tra bản mới") }
            if (result.isNotBlank()) Text(result, fontWeight = FontWeight.SemiBold)
        }
    }
}
