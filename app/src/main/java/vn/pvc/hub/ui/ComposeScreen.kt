package vn.pvc.hub.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.MailAccount
import vn.pvc.hub.sources.MailSender

/** Nội dung điền sẵn khi mở màn hình soạn thư (trả lời thì có sẵn người nhận và tiêu đề). */
data class ComposeDraft(
    val from: String = "",
    val to: String = "",
    val subject: String = "",
    val body: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(accounts: List<MailAccount>, draft: ComposeDraft, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fromIdx by remember {
        mutableIntStateOf(accounts.indexOfFirst { it.address.equals(draft.from, true) }.coerceAtLeast(0))
    }
    val prefs = remember { AppPrefs(context) }
    // Thư mới (không phải trả lời) thì khôi phục bản nháp đã lưu, nếu có.
    val isNew = draft.to.isBlank() && draft.body.isBlank()
    val saved = remember {
        if (isNew) {
            runCatching { org.json.JSONObject(prefs.composeDraft) }.getOrNull()
        } else {
            null
        }
    }
    val sig = remember { prefs.signature.trim() }
    var to by remember { mutableStateOf(saved?.optString("to") ?: draft.to) }
    var subject by remember { mutableStateOf(saved?.optString("subject") ?: draft.subject) }
    var body by remember {
        mutableStateOf(
            saved?.optString("body")
                ?: if (draft.body.isBlank() && sig.isNotEmpty()) "\n\n-- \n$sig" else draft.body
        )
    }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf(saved != null && prefs.composeDraft.isNotBlank()) }
    val attachments = remember { mutableStateListOf<Pair<Uri, String>>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u ->
            if (attachments.none { it.first == u }) attachments.add(u to displayName(context, u))
        }
    }

    // Rời màn hình mà chưa gửi thì lưu nháp (chỉ cho thư mới).
    val latest by rememberUpdatedState(Triple(to, subject, body))
    DisposableEffect(Unit) {
        onDispose {
            if (isNew && !sent) {
                val (t, s, b) = latest
                val onlySig = b.trim() == "-- \n$sig".trim() || b.isBlank()
                if (t.isBlank() && s.isBlank() && onlySig) {
                    prefs.composeDraft = ""
                } else {
                    prefs.composeDraft = org.json.JSONObject()
                        .put("to", t).put("subject", s).put("body", b).toString()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (draft.to.isBlank()) "Soạn email" else "Trả lời",
                        fontWeight = FontWeight.Bold
                    )
                },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (accounts.isEmpty()) {
                Text("Chưa có tài khoản email. Vào Thiết lập, bước 1 để thêm.")
                return@Column
            }
            if (restored) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Đã khôi phục bản nháp.", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        to = ""
                        subject = ""
                        body = if (sig.isNotEmpty()) "\n\n-- \n$sig" else ""
                        prefs.composeDraft = ""
                        restored = false
                    }) { Text("Xóa nháp") }
                }
            }
            if (accounts.size > 1) {
                Text("Gửi từ", fontWeight = FontWeight.SemiBold)
                accounts.forEachIndexed { i, acc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { fromIdx = i },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = fromIdx == i, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(acc.address)
                    }
                }
            } else {
                Text("Gửi từ: ${accounts[0].address}", fontWeight = FontWeight.SemiBold)
            }
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("Đến (nhiều người thì cách nhau dấu phẩy)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Tiêu đề") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Nội dung") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp)
            )
            Text("Tệp đính kèm (${attachments.size})", fontWeight = FontWeight.SemiBold)
            attachments.forEach { (u, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { attachments.remove(u to name) }) { Text("Bỏ") }
                }
            }
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Thêm tệp đính kèm") }
            Button(
                enabled = !sending && to.isNotBlank(),
                onClick = {
                    sending = true
                    status = ""
                    val acc = accounts[fromIdx.coerceIn(0, accounts.lastIndex)]
                    scope.launch {
                        val files = withContext(Dispatchers.IO) { readAttachments(context, attachments.toList()) }
                        if (files == null) {
                            sending = false
                            status = "Tệp đính kèm quá lớn (tối đa 20 MB tổng cộng) hoặc không đọc được."
                            return@launch
                        }
                        val err = MailSender.send(acc, to, subject, body, files)
                        sending = false
                        if (err == null) {
                            sent = true
                            prefs.composeDraft = ""
                            Toast.makeText(context, "Đã gửi", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            status = err
                        }
                    }
                }
            ) { Text(if (sending) "Đang gửi..." else "Gửi") }
            if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) {
                Text(status, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: (uri.lastPathSegment ?: "tệp")

/** Đọc các tệp đính kèm vào bộ nhớ; trả về null nếu quá giới hạn hoặc lỗi đọc. */
private fun readAttachments(context: Context, list: List<Pair<Uri, String>>): List<MailSender.Attach>? {
    var total = 0
    val out = ArrayList<MailSender.Attach>()
    for ((uri, name) in list) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        total += bytes.size
        if (total > MailSender.MAX_ATTACH_BYTES) return null
        out.add(MailSender.Attach(name, context.contentResolver.getType(uri).orEmpty(), bytes))
    }
    return out
}
