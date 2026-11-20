package vn.pvc.hub.sources

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.pvc.hub.data.AccountStore
import vn.pvc.hub.data.HubDao
import vn.pvc.hub.data.HubItem
import vn.pvc.hub.data.MailAccount
import vn.pvc.hub.data.Source
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress

/**
 * Đồng bộ các thư mới nhất trong INBOX qua IMAP (Gmail, Yahoo, iCloud).
 * Đăng nhập bằng "Mật khẩu ứng dụng" (Gmail cần bật xác minh 2 bước).
 */
object GmailImapSync {

    /** Đồng bộ mọi tài khoản; mỗi dòng một tài khoản: "Email địa-chỉ: kết quả". */
    suspend fun syncAll(
        dao: HubDao,
        accounts: AccountStore,
        limit: Int = 50,
        onNew: (MailAccount, List<HubItem>) -> Unit = { _, _ -> }
    ): String {
        val list = accounts.accounts()
        if (list.isEmpty()) return "Email: chưa thêm tài khoản (vào Thiết lập, bước 1)"
        val lines = mutableListOf<String>()
        for (acc in list) {
            val result = runCatching { sync(dao, acc, limit, onNew) }
                .getOrElse { "lỗi: ${it.message}" }
            lines.add("Email ${acc.address}: $result")
        }
        return lines.joinToString("\n")
    }

    /** `onNew` được gọi (khi đồng bộ thành công) với danh sách thư vừa mới thêm vào Hub. */
    suspend fun sync(
        dao: HubDao,
        account: MailAccount,
        limit: Int = 50,
        onNew: (MailAccount, List<HubItem>) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val address = account.address
        val password = account.password
        if (address.isBlank() || password.isBlank()) {
            return@withContext "chưa nhập địa chỉ hoặc Mật khẩu ứng dụng"
        }

        var mailStore: Store? = null
        var folder: Folder? = null
        try {
            val props = Properties()
            props["mail.imaps.connectiontimeout"] = "15000"
            props["mail.imaps.timeout"] = "30000"
            props["mail.imaps.ssl.protocols"] = "TLSv1.2"
            props["mail.imaps.ssl.checkserveridentity"] = "true"
            val session = Session.getInstance(props)
            val st = session.getStore("imaps")
            mailStore = st
            st.connect(account.imapHost, account.imapPort, account.loginName, password)

            val f = st.getFolder("INBOX")
            folder = f
            f.open(Folder.READ_ONLY)

            val total = f.messageCount
            if (total == 0) {
                runCatching { onNew(account, emptyList()) }
                return@withContext "kết nối được, nhưng hộp thư đến trống"
            }
            val start = maxOf(1, total - (limit - 1))
            val messages = f.getMessages(start, total)

            val profile = FetchProfile()
            profile.add(FetchProfile.Item.ENVELOPE)
            profile.add(FetchProfile.Item.FLAGS)
            profile.add(UIDFolder.FetchProfileItem.UID)
            f.fetch(messages, profile)

            val uidFolder = f as UIDFolder
            val items = messages.map { m ->
                HubItem(
                    key = "gmail:$address:${uidFolder.getUID(m)}",
                    source = Source.EMAIL.name,
                    account = address,
                    sender = senderOf(m),
                    preview = m.subject ?: "(không có tiêu đề)",
                    time = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
                    read = m.isSet(Flags.Flag.SEEN),
                    packageName = "com.google.android.gm",
                    replyTo = replyAddressOf(m)
                )
            }
            val ids = dao.insertAll(items)
            // Thư cũ (từ bản trước) chưa có địa chỉ trả lời thì bổ sung.
            items.forEach { if (it.replyTo.isNotBlank()) dao.setReplyTo(it.key, it.replyTo) }
            // Thư đã đọc trên máy chủ thì đánh dấu đã đọc trong Hub.
            dao.markReadByKeys(items.filter { it.read }.map { it.key })

            // ID -1 nghĩa là thư đã có từ trước, không phải thư mới.
            val fresh = items.zip(ids).filter { it.second != -1L }.map { it.first }
            runCatching { onNew(account, fresh) }
            "đã đồng bộ ${items.size} thư"
        } catch (e: javax.mail.AuthenticationFailedException) {
            "SAI tài khoản hoặc Mật khẩu ứng dụng. Phải dùng Mật khẩu ứng dụng 16 ký tự " +
                "(không phải mật khẩu thường) và đã bật xác minh 2 bước. Chi tiết: ${e.message}"
        } catch (e: Exception) {
            val net = e.cause is java.net.UnknownHostException ||
                e.cause is java.net.ConnectException ||
                e.cause is java.net.SocketTimeoutException
            if (net) {
                "không kết nối được tới máy chủ thư, kiểm tra mạng Wi-Fi/4G rồi thử lại"
            } else {
                "lỗi: ${e.message ?: e.javaClass.simpleName}"
            }
        } finally {
            runCatching { folder?.close(false) }
            runCatching { mailStore?.close() }
        }
    }

    /** Nội dung một thư để đọc ngay trong Hub. `error` khác rỗng nghĩa là không lấy được. */
    data class MailContent(
        val from: String = "",
        val to: String = "",
        val date: Long = 0L,
        val subject: String = "",
        val text: String = "",
        val error: String = "",
        val attachments: List<Attachment> = emptyList(),
        /** Bản HTML gốc của thư (rỗng nếu thư chỉ có chữ). */
        val html: String = ""
    )

    data class Attachment(val index: Int, val name: String, val size: Int, val mime: String)

    /** Các phần đính kèm của thư, theo thứ tự cố định để tải lại đúng phần khi cần. */
    private fun collectAttachments(p: Part, out: MutableList<Part>) {
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as? Multipart ?: return
            for (i in 0 until mp.count) collectAttachments(mp.getBodyPart(i), out)
        } else {
            val disp = p.disposition
            val named = !p.fileName.isNullOrBlank()
            if (Part.ATTACHMENT.equals(disp, ignoreCase = true) || (named && !p.isMimeType("text/plain"))) {
                out.add(p)
            }
        }
    }

    /** Tải một tệp đính kèm về thư mục tạm của app; trả về tệp hoặc null nếu lỗi. */
    suspend fun fetchAttachment(
        account: MailAccount,
        uid: Long,
        index: Int,
        cacheDir: java.io.File
    ): java.io.File? = withContext(Dispatchers.IO) {
        var mailStore: Store? = null
        var folder: Folder? = null
        try {
            val props = Properties()
            props["mail.imaps.connectiontimeout"] = "15000"
            props["mail.imaps.timeout"] = "60000"
            props["mail.imaps.ssl.protocols"] = "TLSv1.2"
            props["mail.imaps.ssl.checkserveridentity"] = "true"
            val st = Session.getInstance(props).getStore("imaps")
            mailStore = st
            st.connect(account.imapHost, account.imapPort, account.loginName, account.password)
            val f = st.getFolder("INBOX")
            folder = f
            f.open(Folder.READ_ONLY)
            val m = (f as UIDFolder).getMessageByUID(uid) ?: return@withContext null
            val parts = ArrayList<Part>()
            collectAttachments(m, parts)
            val part = parts.getOrNull(index) ?: return@withContext null
            val dir = java.io.File(cacheDir, "attachments")
            dir.mkdirs()
            val safe = (part.fileName ?: "tep-dinh-kem").replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
            val out = java.io.File(dir, "${uid}_$safe")
            part.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            out
        } catch (e: Exception) {
            null
        } finally {
            runCatching { folder?.close(false) }
            runCatching { mailStore?.close() }
        }
    }

    /** Tải nội dung thư theo UID (lấy theo yêu cầu, không lưu trong máy). Thư được đánh dấu đã đọc trên máy chủ. */
    suspend fun fetchBody(account: MailAccount, uid: Long): MailContent = withContext(Dispatchers.IO) {
        var mailStore: Store? = null
        var folder: Folder? = null
        try {
            val props = Properties()
            props["mail.imaps.connectiontimeout"] = "15000"
            props["mail.imaps.timeout"] = "30000"
            props["mail.imaps.ssl.protocols"] = "TLSv1.2"
            props["mail.imaps.ssl.checkserveridentity"] = "true"
            val st = Session.getInstance(props).getStore("imaps")
            mailStore = st
            st.connect(account.imapHost, account.imapPort, account.loginName, account.password)
            val f = st.getFolder("INBOX")
            folder = f
            f.open(Folder.READ_WRITE)
            val m = (f as UIDFolder).getMessageByUID(uid)
                ?: return@withContext MailContent(error = "Không tìm thấy thư này trên máy chủ (có thể đã bị xóa hoặc chuyển đi).")
            val text = (textOf(m) ?: "(Thư không có nội dung chữ, hoặc chỉ có tệp đính kèm/hình ảnh.)")
                .replace("\r\n", "\n")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
            runCatching { m.setFlag(Flags.Flag.SEEN, true) }
            val attParts = ArrayList<Part>()
            runCatching { collectAttachments(m, attParts) }
            val atts = attParts.mapIndexed { i, part ->
                Attachment(
                    index = i,
                    name = runCatching { javax.mail.internet.MimeUtility.decodeText(part.fileName ?: "") }
                        .getOrDefault(part.fileName ?: "").ifBlank { "tệp đính kèm" },
                    size = part.size,
                    mime = part.contentType?.substringBefore(';')?.trim().orEmpty()
                )
            }
            val htmlBody = runCatching { htmlOf(m) }.getOrNull().orEmpty()
            MailContent(
                attachments = atts,
                html = if (htmlBody.length > 1_500_000) "" else htmlBody,
                from = (m.from?.firstOrNull() as? InternetAddress)?.toUnicodeString().orEmpty(),
                to = m.getRecipients(Message.RecipientType.TO)
                    ?.joinToString(", ") { (it as? InternetAddress)?.address ?: it.toString() }.orEmpty(),
                date = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
                subject = m.subject.orEmpty(),
                text = if (text.length > 30_000) text.take(30_000) + "\n\n[... thư dài, đã cắt bớt ...]" else text
            )
        } catch (e: Exception) {
            MailContent(error = "Không tải được nội dung thư: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { folder?.close(false) }
            runCatching { mailStore?.close() }
        }
    }

    /** Phần HTML đầu tiên của thư (bỏ qua tệp đính kèm), hoặc null. */
    private fun htmlOf(p: Part): String? {
        if (p.isMimeType("text/html")) return p.content as? String
        if (p.isMimeType("message/rfc822")) return (p.content as? Part)?.let { htmlOf(it) }
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as? Multipart ?: return null
            for (i in 0 until mp.count) {
                val part = mp.getBodyPart(i)
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)) continue
                htmlOf(part)?.let { return it }
            }
        }
        return null
    }

    private fun textOf(p: Part): String? {
        if (p.isMimeType("text/plain")) return p.content as? String
        if (p.isMimeType("text/html")) {
            val html = (p.content as? String) ?: return null
            val clean = html.replace(Regex("(?is)<(style|script|head).*?</\\1>"), "")
            return Html.fromHtml(clean, Html.FROM_HTML_MODE_LEGACY).toString()
        }
        if (p.isMimeType("message/rfc822")) return (p.content as? Part)?.let { textOf(it) }
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as? Multipart ?: return null
            var htmlText: String? = null
            for (i in 0 until mp.count) {
                val part = mp.getBodyPart(i)
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)) continue
                val t = textOf(part) ?: continue
                if (part.isMimeType("text/html")) {
                    if (htmlText == null) htmlText = t
                } else if (t.isNotBlank()) {
                    return t
                }
            }
            return htmlText
        }
        return null
    }

    private fun senderOf(m: Message): String {
        val a = m.from?.firstOrNull() as? InternetAddress
        return a?.personal?.takeIf { it.isNotBlank() } ?: a?.address ?: "(không rõ)"
    }

    /** Địa chỉ để trả lời: Reply-To nếu có, không thì người gửi. */
    private fun replyAddressOf(m: Message): String {
        val a = (m.replyTo?.firstOrNull() ?: m.from?.firstOrNull()) as? InternetAddress
        return a?.address.orEmpty()
    }
}
