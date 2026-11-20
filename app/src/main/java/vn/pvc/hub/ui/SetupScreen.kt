package vn.pvc.hub.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import vn.pvc.hub.data.AccountStore
import vn.pvc.hub.data.MailAccount
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.sources.SetupState
import vn.pvc.hub.sources.SyncRunner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Green = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    store: AccountStore,
    prefs: AppPrefs,
    notifLog: String,
    syncing: Boolean,
    setup: SetupState,
    report: String,
    lastSync: Long,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onSync: () -> Unit,
    onClearData: () -> Unit,
    onCheckUpdate: ((String) -> Unit) -> Unit = {}
) {
    var updateResult by remember { mutableStateOf("") }
    val context = LocalContext.current
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    var imapHost by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("993") }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("465") }
    var loginUser by remember { mutableStateOf("") }
    var mailAccounts by remember { mutableStateOf(store.accounts()) }
    var confirmClear by remember { mutableStateOf(false) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onChanged() }
    val callLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onChanged() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thiết lập và hướng dẫn", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Tiến độ ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (setup.allDone) {
                            "Đã thiết lập xong. PVC-Hub sẵn sàng hoạt động."
                        } else {
                            "Đã xong ${setup.doneCount}/${setup.total} bước"
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    LinearProgressIndicator(
                        progress = { setup.doneCount / setup.total.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!setup.allDone) {
                        Text(
                            "Làm lần lượt từ bước 1 đến 5. Mỗi bước xong sẽ có dấu tích xanh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ---- Bước 1: Email (một hoặc nhiều tài khoản) ----
            StepCard(1, "Kết nối email (1 đến nhiều tài khoản)", setup.gmail) {
                Body("Gmail không cho app ngoài dùng mật khẩu thường. Mỗi tài khoản cần một \"Mật khẩu ứng dụng\" gồm 16 ký tự:")
                Body(
                    "1. Bật xác minh 2 bước cho tài khoản Google (nếu chưa bật).\n" +
                        "2. Bấm nút bên dưới để mở trang Mật khẩu ứng dụng, đặt tên PVC-Hub rồi bấm Tạo.\n" +
                        "3. Chép 16 ký tự hiện ra, dán vào ô bên dưới (dấu cách không quan trọng).\n" +
                        "4. Muốn thêm tài khoản khác: nhập địa chỉ và mật khẩu mới rồi bấm lại \"Lưu và đồng bộ\". " +
                        "Có thể thêm bao nhiêu tài khoản tùy ý."
                )
                Body("Yahoo và iCloud cũng dùng được với mật khẩu ứng dụng của họ. Email công ty hoặc hosting thì bật \"Máy chủ riêng\" bên dưới. Outlook/Hotmail cá nhân thì chưa hỗ trợ.")
                OutlinedButton(onClick = {
                    openUrl(context, "https://myaccount.google.com/apppasswords")
                }) { Text("Mở trang Mật khẩu ứng dụng") }

                if (mailAccounts.isNotEmpty()) {
                    Text("Tài khoản đã thêm (${mailAccounts.size})", fontWeight = FontWeight.SemiBold)
                    mailAccounts.forEach { acc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(acc.address, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                store.remove(acc.address)
                                mailAccounts = store.accounts()
                                SyncRunner.schedule(context)
                                onChanged()
                            }) { Text("Xóa") }
                        }
                    }
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Địa chỉ email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu ứng dụng (16 ký tự)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { custom = !custom },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = custom, onCheckedChange = { custom = it })
                    Text(
                        "  Máy chủ riêng (email công ty, hosting, nhà cung cấp khác)",
                        modifier = Modifier.weight(1f)
                    )
                }
                if (custom) {
                    Body(
                        "Hỏi quản trị mail hoặc nhà cung cấp 4 thông tin: máy chủ nhận (IMAP, cổng SSL thường là 993) " +
                            "và máy chủ gửi (SMTP, cổng 465 dùng SSL hoặc 587 dùng STARTTLS). " +
                            "Mật khẩu là mật khẩu hộp thư (hoặc mật khẩu ứng dụng nếu nhà cung cấp yêu cầu)."
                    )
                    OutlinedTextField(
                        value = imapHost, onValueChange = { imapHost = it },
                        label = { Text("Máy chủ nhận (IMAP), ví dụ imap.congty.vn") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = imapPort, onValueChange = { imapPort = it.filter(Char::isDigit) },
                        label = { Text("Cổng IMAP (SSL)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = smtpHost, onValueChange = { smtpHost = it },
                        label = { Text("Máy chủ gửi (SMTP), ví dụ smtp.congty.vn") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = smtpPort, onValueChange = { smtpPort = it.filter(Char::isDigit) },
                        label = { Text("Cổng SMTP (465 hoặc 587)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = loginUser, onValueChange = { loginUser = it },
                        label = { Text("Tên đăng nhập (chỉ điền nếu khác địa chỉ email)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        if (address.isNotBlank() && password.isNotBlank()) {
                            store.addOrUpdate(
                                if (custom && imapHost.isNotBlank()) {
                                    MailAccount(
                                        address.trim(), password.replace(" ", ""),
                                        imapHost.trim(), imapPort.toIntOrNull() ?: 993,
                                        smtpHost.trim(), smtpPort.toIntOrNull() ?: 465,
                                        loginUser.trim()
                                    )
                                } else {
                                    MailAccount(address.trim(), password.replace(" ", ""))
                                }
                            )
                            custom = false
                            imapHost = ""
                            smtpHost = ""
                            loginUser = ""
                            address = ""
                            password = ""
                            mailAccounts = store.accounts()
                        }
                        SyncRunner.schedule(context)
                        onChanged()
                        onSync()
                    }) { Text("Lưu và đồng bộ") }
                }
                // Kết quả đồng bộ từng tài khoản.
                val mailLines = report.lines().filter { it.startsWith("Email") }
                if (syncing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Body("Đang đồng bộ, chờ khoảng 10 đến 20 giây mỗi tài khoản...")
                } else {
                    mailLines.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ---- Bước 2: SMS ----
            StepCard(2, "Cho phép đọc tin nhắn SMS", setup.sms) {
                Body("Để SMS hiện chung trong hộp thư. Chỉ đọc, PVC-Hub không gửi hay xóa tin của bạn.")
                if (setup.sms) {
                    Body("Quyền đã được cấp.")
                } else {
                    Button(onClick = { smsLauncher.launch(Manifest.permission.READ_SMS) }) {
                        Text("Cấp quyền đọc SMS")
                    }
                    Body("Nếu bấm mà không hiện hộp thoại: mở Thông tin ứng dụng, vào Quyền, bật SMS thủ công.")
                }
            }

            // ---- Bước 3: Cuộc gọi ----
            StepCard(3, "Cho phép đọc cuộc gọi nhỡ", setup.calls) {
                Body("Để cuộc gọi nhỡ hiện chung trong hộp thư.")
                if (setup.calls) {
                    Body("Quyền đã được cấp.")
                } else {
                    Button(onClick = { callLauncher.launch(Manifest.permission.READ_CALL_LOG) }) {
                        Text("Cấp quyền đọc cuộc gọi")
                    }
                    Body("Nếu không hiện hộp thoại: mở Thông tin ứng dụng, vào Quyền, bật Nhật ký cuộc gọi.")
                }
            }

            // ---- Bước 4: Thông báo chat ----
            StepCard(4, "Đọc thông báo Zalo, Telegram, Messenger", setup.notif) {
                Body(
                    "Android không cho app đọc tin của Zalo, Telegram, Messenger trực tiếp, nên PVC-Hub " +
                        "lấy nội dung từ thông báo của các app đó."
                )
                if (!setup.notif) {
                    Body(
                        "1. Bấm \"Mở cài đặt thông báo\" bên dưới, tìm PVC-Hub và bật công tắc, bấm Cho phép.\n" +
                            "2. Nếu công tắc bị mờ hoặc hiện \"Cài đặt bị hạn chế\" (Android 13 trở lên): " +
                            "bấm \"Mở thông tin ứng dụng\", bấm dấu ba chấm ở góc trên bên phải, chọn " +
                            "\"Cho phép cài đặt bị hạn chế\", rồi quay lại bước 1."
                    )
                    Button(onClick = {
                        safeStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) { Text("Mở cài đặt thông báo") }
                    OutlinedButton(onClick = { openAppDetails(context) }) {
                        Text("Mở thông tin ứng dụng")
                    }
                } else {
                    Body("Quyền đã được cấp.")
                }
                Body(
                    "Lưu ý quan trọng:\n" +
                        "• Chỉ thấy tin MỚI đến sau khi bật, không có tin cũ.\n" +
                        "• Thông báo của Zalo, Telegram, Messenger phải đang BẬT trong chính các app đó " +
                        "và trong Cài đặt Android, và phải cho hiện nội dung tin (không chọn ẩn nội dung).\n" +
                        "• Chế độ Không làm phiền có thể chặn thông báo, khi đó Hub cũng không thấy."
                )
            }

            // ---- Bước 5: Pin ----
            StepCard(5, "Cho phép chạy nền (không bị tiết kiệm pin chặn)", setup.battery) {
                Body("Nếu hệ thống tắt PVC-Hub để tiết kiệm pin, tin sẽ về chậm hoặc mất.")
                if (setup.battery) {
                    Body("Đã bật chạy nền không hạn chế.")
                } else {
                    Button(onClick = {
                        safeStart(
                            context,
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }) { Text("Cho phép chạy nền") }
                }
                Body(
                    "Thêm tùy hãng máy (nếu tin vẫn về chậm):\n" +
                        "• Samsung: Cài đặt > Pin > Giới hạn sử dụng nền > Ứng dụng không bao giờ ngủ, thêm PVC-Hub.\n" +
                        "• Xiaomi/Redmi/Poco: Thông tin ứng dụng > Tiết kiệm pin: Không hạn chế, và bật Tự khởi động.\n" +
                        "• Oppo/Realme/Vivo: Thông tin ứng dụng > Pin: cho phép hoạt động nền, bật Tự khởi động.\n" +
                        "• Có thể ghim app ở màn hình Đa nhiệm (khóa ứng dụng) để hệ thống không dọn."
                )
            }

            MailSettingsCard(prefs, onChanged)

            ChatAppsCard(prefs)

            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lưu ý khi app chat chưa gửi tin vào Hub", style = MaterialTheme.typography.titleMedium)
                    Body(
                        "Hub chỉ nhận được tin khi chính app đó (Telegram, Facebook, X...) hiện thông báo. " +
                            "Nếu một app không lên tin, vào Cài đặt Android của app đó: bật thông báo, " +
                            "chọn Pin là \"Không tối ưu hóa\" (hoặc \"Không hạn chế\"), và bật \"Tự khởi động\" " +
                            "nếu máy có mục này (Xiaomi, Oppo, Realme, Vivo, Huawei, Samsung)."
                    )
                }
            }

            // ---- Kiểm tra ----
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Kiểm tra hoạt động", style = MaterialTheme.typography.titleMedium)
                    Body(
                        "• Nhờ ai đó nhắn Zalo, Telegram hoặc Messenger cho bạn: tin phải hiện trong Hub sau vài giây.\n" +
                            "• Gửi một email vào Gmail rồi bấm nút Làm mới (mũi tên tròn) ở màn hình chính.\n" +
                            "• Để một cuộc gọi nhỡ rồi bấm Làm mới.\n" +
                            "Email, SMS và cuộc gọi nhỡ tự cập nhật mỗi 15 phút, tin chat vào ngay khi có thông báo."
                    )
                    Button(onClick = onSync) { Text("Đồng bộ ngay") }
                }
            }

            // ---- Tình trạng ----
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tình trạng đồng bộ", style = MaterialTheme.typography.titleMedium)
                    Body("Lần gần nhất: " + formatSync(lastSync))
                    if (report.isNotBlank()) Body(report)
                }
            }

            PeekCard(prefs)

            QuietCard(prefs)

            RulesCard(prefs)

            SignatureCard(prefs)

            CleanCard(prefs)

            LockCard(prefs)

            UpdateCard(prefs, onCheck = { onCheckUpdate { r -> updateResult = r } }, result = updateResult)

            BackupCard()

            NotifLogCard(prefs, notifLog, onChanged)

            // ---- Khắc phục sự cố ----
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Khắc phục sự cố", style = MaterialTheme.typography.titleMedium)
                    Body(
                        "Gmail báo lỗi đăng nhập (AUTHENTICATIONFAILED, Invalid credentials):\n" +
                            "Bạn đang dùng mật khẩu Gmail thường. Phải dùng Mật khẩu ứng dụng 16 ký tự và tài khoản " +
                            "phải bật xác minh 2 bước. Gmail của công ty/trường có thể bị quản trị chặn mục này."
                    )
                    Body(
                        "Không thấy tin Zalo/Telegram/Messenger:\n" +
                            "Kiểm tra bước 4 đã có dấu tích. Thử tắt rồi bật lại quyền đọc thông báo của PVC-Hub. " +
                            "Kiểm tra thông báo trong chính app chat còn bật và không bị ẩn nội dung."
                    )
                    Body(
                        "Tin chat đến chậm hoặc thỉnh thoảng mất:\n" +
                            "Làm lại bước 5 và phần tùy hãng máy. Tắt chế độ Siêu tiết kiệm pin."
                    )
                    Body(
                        "Không thấy SMS hoặc cuộc gọi nhỡ:\n" +
                            "Kiểm tra bước 2, 3 có dấu tích, rồi bấm Làm mới. Máy đã bật RCS/chat của Google " +
                            "Tin nhắn thì tin RCS có thể không nằm trong hộp thư SMS thường."
                    )
                    Body(
                        "Cài app báo bị chặn: chọn Chi tiết rồi Vẫn cài đặt, hoặc tạm tắt Quét ứng dụng bằng " +
                            "Play Protect."
                    )
                }
            }

            // ---- Dữ liệu ----
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dữ liệu và quyền riêng tư", style = MaterialTheme.typography.titleMedium)
                    Body(
                        "Mọi tin lưu ngay trên máy bạn, không gửi về máy chủ nào. Mật khẩu Gmail được mã hóa " +
                            "bằng Android Keystore."
                    )
                    OutlinedButton(onClick = { confirmClear = true }) {
                        Text("Xóa toàn bộ tin đã lưu trong Hub")
                    }
                    Body("PVC-Hub 0.11.0 · Thiết kế bởi P.V.C")
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xóa dữ liệu?") },
            text = {
                Text("Chỉ xóa bản sao trong PVC-Hub. Email, SMS và tin chat gốc vẫn còn trong các app của chúng.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearData()
                }) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun StepCard(
    index: Int,
    title: String,
    done: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(if (done) Green else Color(0xFF9AA0A6), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (done) "✓" else "$index",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (done) {
                    Text(
                        "Đã xong",
                        color = Green,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatSync(time: Long): String =
    if (time > 0L) {
        SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date(time))
    } else {
        "chưa đồng bộ lần nào"
    }

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Máy không hỗ trợ màn hình này: bỏ qua.
    }
}

private fun openUrl(context: Context, url: String) {
    safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun openAppDetails(context: Context) {
    safeStart(
        context,
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    )
}
