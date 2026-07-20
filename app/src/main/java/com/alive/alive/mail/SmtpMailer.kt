package com.alive.alive.mail

import android.util.Log
import com.alive.alive.data.SmtpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
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

    /**
     * 先做一次原始 TCP 连通性探测，帮助区分是网络问题还是配置问题。
     */
    suspend fun diagnose(cfg: SmtpConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder()
            sb.appendLine("探测 ${cfg.host}:${cfg.port} ...")
            Socket().use { socket ->
                socket.connect(InetSocketAddress(cfg.host, cfg.port), 10000)
                sb.appendLine("TCP 连接成功 (${socket.localAddress}:${socket.localPort})")
            }
            sb.appendLine("网络层连通，问题应在 TLS/认证层。")
            sb.toString()
        }.recoverCatching { e ->
            "TCP 连接失败: ${e.javaClass.simpleName}: ${e.message}\n" +
            "提示：你的网络可能拦截了 ${cfg.port} 端口，尝试切换为 465 端口或更换网络。"
        }
    }

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
                    // STARTTLS 增强配置：显式信任主机、限定 TLS 版本、关闭主机名校验
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                    put("mail.smtp.ssl.trust", "*")
                    put("mail.smtp.ssl.protocols", "TLSv1.2")
                    put("mail.smtp.ssl.checkserveridentity", "false")
                }
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(cfg.user, cfg.pass)
            }).apply {
                // 开启 debug 输出到 Android Logcat（tag 为 "Alive/JavaMail"）
                debug = true
            }

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
