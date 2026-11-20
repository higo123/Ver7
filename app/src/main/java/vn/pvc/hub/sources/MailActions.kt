package vn.pvc.hub.sources

import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.pvc.hub.data.MailAccount
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder

enum class MailAction { ARCHIVE, TRASH, SPAM }

/** Dọn thư thật trên máy chủ thư (IMAP): lưu trữ, chuyển vào Thùng rác, báo spam. */
object MailActions {

    /** Trả về null nếu thành công, hoặc thông báo lỗi tiếng Việt. */
    suspend fun run(account: MailAccount, uids: List<Long>, action: MailAction): String? =
        withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext null
            var store: Store? = null
            var inbox: Folder? = null
            try {
                val props = Properties()
                props["mail.imaps.connectiontimeout"] = "15000"
                props["mail.imaps.timeout"] = "60000"
                props["mail.imaps.ssl.protocols"] = "TLSv1.2"
                val st = Session.getInstance(props).getStore("imaps")
                store = st
                st.connect(account.imapHost, account.imapPort, account.loginName, account.password)
                val f = st.getFolder("INBOX")
                inbox = f
                f.open(Folder.READ_WRITE)

                val msgs = uids.mapNotNull { runCatching { (f as UIDFolder).getMessageByUID(it) }.getOrNull() }
                    .toTypedArray<Message>()
                if (msgs.isEmpty()) return@withContext "Không tìm thấy thư trên máy chủ (có thể đã bị xóa hoặc chuyển đi)."

                val isGmail = account.imapHost.contains("gmail", ignoreCase = true)
                val target: Folder? = when (action) {
                    MailAction.TRASH -> special(st, "\\Trash", listOf("[Gmail]/Trash", "[Gmail]/Thùng rác", "Trash", "Deleted Items", "Deleted Messages"))
                    MailAction.SPAM -> special(st, "\\Junk", listOf("[Gmail]/Spam", "Junk", "Spam", "Bulk Mail"))
                    // Gmail: xóa khỏi INBOX là lưu trữ (thư còn trong "Tất cả thư").
                    MailAction.ARCHIVE -> if (isGmail) null else special(st, "\\Archive", listOf("Archive", "Archives"))
                }
                if (action != MailAction.ARCHIVE || !isGmail) {
                    if (target == null) {
                        return@withContext when (action) {
                            MailAction.TRASH -> "Không tìm thấy thư mục Thùng rác trên máy chủ."
                            MailAction.SPAM -> "Không tìm thấy thư mục Spam trên máy chủ."
                            MailAction.ARCHIVE -> "Máy chủ thư này không có thư mục Lưu trữ."
                        }
                    }
                    f.copyMessages(msgs, target)
                }
                msgs.forEach { it.setFlag(Flags.Flag.DELETED, true) }
                f.close(true) // đóng và xóa hẳn khỏi INBOX
                inbox = null
                null
            } catch (e: javax.mail.AuthenticationFailedException) {
                "Sai tài khoản hoặc Mật khẩu ứng dụng."
            } catch (e: Exception) {
                "Không thực hiện được trên máy chủ thư: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                runCatching { inbox?.close(false) }
                runCatching { store?.close() }
            }
        }

    /** Tìm thư mục theo thuộc tính chuẩn (\Trash, \Junk...), không có thì thử các tên thường gặp. */
    private fun special(store: Store, attr: String, names: List<String>): Folder? {
        runCatching {
            val all = store.defaultFolder.list("*")
            all.firstOrNull { f ->
                (f as? IMAPFolder)?.attributes?.any { it.equals(attr, ignoreCase = true) } == true
            }?.let { return it }
        }
        for (n in names) {
            val f = runCatching { store.getFolder(n) }.getOrNull() ?: continue
            if (runCatching { f.exists() }.getOrDefault(false)) return f
        }
        return null
    }
}
