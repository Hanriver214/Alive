package com.alive.alive.mail

import com.alive.alive.data.SmtpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * 用 JavaMail 发送 SMTP 邮件。运行在 IO 调度器上。
 *
 * 默认按 ssl:// + 465 配置（QQ/163/Gmail 等常见应用密码方案）。
 * 端口若为 587 则自动启用 STARTTLS。
 */
object SmtpMailer {

    suspend fun send(cfg: SmtpConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(cfg.user.isNotBlank()) { "发件邮箱为空" }
            require(cfg.pass.isNotBlank()) { "应用密码为空" }
            require(cfg.to.isNotBlank()) { "收件邮箱为空" }

            val props = Properties().apply {
                put("mail.transport.protocol", "smtp")
                put("mail.smtp.auth", "true")
                put("mail.smtp.host", cfg.host)
                put("mail.smtp.port", cfg.port.toString())
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "30000")
                put("mail.smtp.writetimeout", "30000")
                if (cfg.port == 465) {
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.port", cfg.port.toString())
                } else if (cfg.port == 587) {
                    // STARTTLS 需要显式信任主机并限定 TLS 版本，避免 [EOF] 握手失败
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                    put("mail.smtp.ssl.trust", cfg.host)
                    put("mail.smtp.ssl.protocols", "TLSv1.2")
                }
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(cfg.user, cfg.pass)
            })

            val msg = MimeMessage(session).apply {
                setFrom(InternetAddress(cfg.user))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(cfg.to))
                subject = cfg.subject
                setText(cfg.body, "UTF-8")
                sentDate = java.util.Date()
            }
            Transport.send(msg)
        }
    }
}
