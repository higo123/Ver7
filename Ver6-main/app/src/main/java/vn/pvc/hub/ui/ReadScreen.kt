package vn.pvc.hub.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.key
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.pvc.hub.data.HubItem
import vn.pvc.hub.data.MailAccount
import vn.pvc.hub.sources.GmailImapSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Đọc một thư ngay trong Hub: nội dung được tải về từ máy chủ khi mở. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    item: HubItem,
    account: MailAccount?,
    onBack: () -> Unit,
    onReply: (HubItem) -> Unit
) {
    val context = LocalContext.current
    var content by remember(item.id) { mutableStateOf<GmailImapSync.MailContent?>(null) }
    var busyIndex by remember(item.id) { mutableStateOf(-1) }
    var htmlMode by remember(item.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(item.id) {
        val uid = item.key.substringAfterLast(':').toLongOrNull()
        content = when {
            account == null -> GmailImapSync.MailContent(error = "Tài khoản ${item.account} không còn trong Hub.")
            uid == null -> GmailImapSync.MailContent(error = "Thư này thiếu mã nhận diện.")
            else -> GmailImapSync.fetchBody(account, uid)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đọc thư", fontWeight = FontWeight.Bold) },
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
        val full = content
        if (htmlMode && full != null && full.html.isNotBlank()) {
            HtmlView(full.html, Modifier.padding(padding)) { htmlMode = false }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val c = content
            Text(
                c?.subject?.ifBlank { null } ?: item.preview,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Từ: " + (c?.from?.ifBlank { null } ?: item.sender),
                style = MaterialTheme.typography.bodyMedium
            )
            if (c != null && c.to.isNotBlank()) {
                Text(
                    "Đến: ${c.to}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val time = if (c != null && c.date > 0L) c.date else item.time
            Text(
                SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date(time)) +
                    "  ·  ${item.account}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.replyTo.isNotBlank()) {
                    Button(onClick = { onReply(item) }) { Text("Trả lời") }
                }
                OutlinedButton(onClick = { openGmailApp(context, item) }) { Text("Mở bằng Gmail") }
                if (c != null && c.html.isNotBlank()) {
                    OutlinedButton(onClick = { htmlMode = true }) { Text("Bản đầy đủ") }
                }
            }
            HorizontalDivider()
            when {
                c == null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Đang tải nội dung thư...")
                }
                c.error.isNotBlank() -> Text(c.error, color = MaterialTheme.colorScheme.error)
                else -> {
                    if (c.attachments.isNotEmpty()) {
                        Text(
                            "Tệp đính kèm (${c.attachments.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        c.attachments.forEach { a ->
                            OutlinedButton(
                                enabled = busyIndex != a.index,
                                onClick = {
                                    val uid = item.key.substringAfterLast(':').toLongOrNull()
                                    if (account != null && uid != null) {
                                        busyIndex = a.index
                                        scope.launch {
                                            val f = GmailImapSync.fetchAttachment(account, uid, a.index, context.cacheDir)
                                            busyIndex = -1
                                            if (f == null) {
                                                Toast.makeText(context, "Không tải được tệp", Toast.LENGTH_SHORT).show()
                                            } else {
                                                openFile(context, f, a.mime)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    (if (busyIndex == a.index) "Đang tải... " else "") +
                                        a.name + sizeText(a.size),
                                    maxLines = 1
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                    SelectionContainer {
                        Text(c.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private fun openGmailApp(context: Context, item: HubItem) {
    try {
        val pkg = item.packageName ?: "com.google.android.gm"
        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    } catch (e: Exception) {
        // Không mở được: bỏ qua.
    }
}

private fun sizeText(bytes: Int): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> "  (${maxOf(1, bytes / 1024)} KB)"
    else -> "  (" + String.format(Locale.US, "%.1f", bytes / 1048576.0) + " MB)"
}

private fun openFile(context: Context, file: java.io.File, mime: String) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Không có ứng dụng nào mở được tệp này", Toast.LENGTH_LONG).show()
    }
}

/**
 * Xem thư HTML đầy đủ. Mặc định chặn hình tải từ internet (tránh bị theo dõi), tắt JavaScript.
 * Hình nhúng sẵn trong thư (cid) chưa hiển thị được.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlView(html: String, modifier: Modifier, onBackToText: () -> Unit) {
    val context = LocalContext.current
    var showImages by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBackToText) { Text("Xem dạng chữ") }
            OutlinedButton(onClick = { showImages = !showImages }) {
                Text(if (showImages) "Chặn hình ảnh" else "Hiện hình ảnh")
            }
        }
        if (!showImages) {
            Text(
                "Hình ảnh từ internet đang bị chặn để bảo vệ bạn. Bấm \"Hiện hình ảnh\" nếu tin cậy người gửi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        key(showImages) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.WHITE)
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.blockNetworkImage = !showImages
                        settings.loadsImagesAutomatically = showImages
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val u = request.url
                                if (u.scheme == "http" || u.scheme == "https" || u.scheme == "mailto") {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                                return true
                            }
                        }
                        loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    }
                },
                onRelease = { it.destroy() }
            )
        }
    }
}
