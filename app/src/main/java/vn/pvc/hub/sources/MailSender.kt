package vn.pvc.hub.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.pvc.hub.data.MailAccount
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** Gửi email qua SMTP bằng chính Mật khẩu ứng dụng đã lưu. */
object MailSender {

    /** Trả về null nếu gửi thành công, hoặc thông báo lỗi tiếng Việt. */
    /** Tệp đính kèm đã đọc sẵn vào bộ nhớ. */
    class Attach(val name: String, val mime: String, val bytes: ByteArray)

    const val MAX_ATTACH_BYTES = 20 * 1024 * 1024

    suspend fun send(
        account: MailAccount,
        to: String,
        subject: String,
        body: String,
        attachments: List<Attach> = emptyList()
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val recipients = InternetAddress.parse(to.trim(), true)
                if (recipients.isEmpty()) return@withContext "Chưa nhập địa chỉ người nhận."

                val props = Properties()
                props["mail.smtp.host"] = account.smtpHost
                props["mail.smtp.port"] = account.smtpPort.toString()
                props["mail.smtp.auth"] = "true"
                props["mail.smtp.connectiontimeout"] = "15000"
                props["mail.smtp.timeout"] = "30000"
                props["mail.smtp.writetimeout"] = "30000"
                props["mail.smtp.ssl.protocols"] = "TLSv1.2"
                if (account.smtpPort == 465) {
                    // Implicit TLS: reject certificates for the wrong host.
                    props["mail.smtp.ssl.enable"] = "true"
                    props["mail.smtp.ssl.checkserveridentity"] = "true"
                } else {
                    // STARTTLS must be negotiated; never silently downgrade to plaintext.
                    props["mail.smtp.starttls.enable"] = "true"
                    props["mail.smtp.starttls.required"] = "true"
                    props["mail.smtp.ssl.checkserveridentity"] = "true"
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(account.loginName, account.password)
                })
                val msg = MimeMessage(session)
                msg.setFrom(InternetAddress(account.address))
                msg.setRecipients(Message.RecipientType.TO, recipients)
                msg.setSubject(subject, "UTF-8")
                if (attachments.isEmpty()) {
                    msg.setText(body, "UTF-8")
                } else {
                    val multipart = javax.mail.internet.MimeMultipart()
                    val text = javax.mail.internet.MimeBodyPart()
                    text.setText(body, "UTF-8")
                    multipart.addBodyPart(text)
                    for (a in attachments) {
                        val part = javax.mail.internet.MimeBodyPart()
                        part.dataHandler = javax.activation.DataHandler(
                            javax.mail.util.ByteArrayDataSource(a.bytes, a.mime.ifBlank { "application/octet-stream" })
                        )
                        part.fileName = javax.mail.internet.MimeUtility.encodeText(a.name, "UTF-8", null)
                        multipart.addBodyPart(part)
                    }
                    msg.setContent(multipart)
                }
                msg.sentDate = Date()
                Transport.send(msg)
                null
            } catch (e: javax.mail.internet.AddressException) {
                "Địa chỉ người nhận không hợp lệ: ${e.message}"
            } catch (e: javax.mail.AuthenticationFailedException) {
                "Sai tài khoản hoặc Mật khẩu ứng dụng khi gửi thư."
            } catch (e: Exception) {
                "Gửi không được: ${e.message ?: e.javaClass.simpleName}"
            }
        }
}
