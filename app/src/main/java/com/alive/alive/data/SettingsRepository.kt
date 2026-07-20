package com.alive.alive.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aliveDataStore by preferencesDataStore(name = "alive_settings")

/** 用户可编辑的 SMTP 邮件 + 监测开关配置。 */
data class SmtpConfig(
    val enabled: Boolean = false,
    val host: String = "smtp.qq.com",
    val port: Int = 465,
    val user: String = "",
    val pass: String = "",
    val to: String = "",
    val subject: String = "你今天还好吗？",
    val body: String = "今天一天没有收到 Alive 的签到，请确认手机主人状态。",
    val resendHours: Int = 6
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("smtp.enabled")
        val HOST = stringPreferencesKey("smtp.host")
        val PORT = intPreferencesKey("smtp.port")
        val USER = stringPreferencesKey("smtp.user")
        val PASS = stringPreferencesKey("smtp.pass")
        val TO = stringPreferencesKey("smtp.to")
        val SUBJECT = stringPreferencesKey("mail.subject")
        val BODY = stringPreferencesKey("mail.body")
        val RESEND_HOURS = intPreferencesKey("mail.resend_hours")
    }

    val configFlow: Flow<SmtpConfig> = context.aliveDataStore.data.map { p ->
        SmtpConfig(
            enabled = p[Keys.ENABLED] ?: false,
            host = p[Keys.HOST] ?: "smtp.qq.com",
            port = p[Keys.PORT] ?: 465,
            user = p[Keys.USER] ?: "",
            pass = p[Keys.PASS] ?: "",
            to = p[Keys.TO] ?: "",
            subject = p[Keys.SUBJECT] ?: "你今天还好吗？",
            body = p[Keys.BODY] ?: "今天一天没有收到 Alive 的签到，请确认手机主人状态。",
            resendHours = (p[Keys.RESEND_HOURS] ?: 6).coerceIn(1, 24)
        )
    }

    suspend fun current(): SmtpConfig = configFlow.first()

    suspend fun save(cfg: SmtpConfig) {
        context.aliveDataStore.edit { p ->
            p[Keys.ENABLED] = cfg.enabled
            p[Keys.HOST] = cfg.host
            p[Keys.PORT] = cfg.port
            p[Keys.USER] = cfg.user
            p[Keys.PASS] = cfg.pass
            p[Keys.TO] = cfg.to
            p[Keys.SUBJECT] = cfg.subject
            p[Keys.BODY] = cfg.body
            p[Keys.RESEND_HOURS] = cfg.resendHours.coerceIn(1, 24)
        }
    }
}
