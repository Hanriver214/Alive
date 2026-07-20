package com.alive.alive.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alive.alive.util.CryptoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.aliveDataStore by preferencesDataStore(name = "alive_settings")

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
        val ENCRYPTED_PASS = stringPreferencesKey("smtp.encrypted_pass")
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
            pass = runCatching { CryptoHelper.decrypt(p[Keys.ENCRYPTED_PASS] ?: "") }.getOrDefault(""),
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
            p[Keys.ENCRYPTED_PASS] = if (cfg.pass.isBlank()) "" else CryptoHelper.encrypt(cfg.pass)
            p[Keys.TO] = cfg.to
            p[Keys.SUBJECT] = cfg.subject
            p[Keys.BODY] = cfg.body
            p[Keys.RESEND_HOURS] = cfg.resendHours.coerceIn(1, 24)
        }
    }

    suspend fun resetAll() {
        context.aliveDataStore.edit { it.clear() }
        CryptoHelper.deleteKey()
    }

    suspend fun exportConfig(): String {
        val p = context.aliveDataStore.data.first()
        return JSONObject().apply {
            put("schemaVersion", 3)
            put("enabled", p[Keys.ENABLED] ?: false)
            put("host", p[Keys.HOST] ?: "smtp.qq.com")
            put("port", p[Keys.PORT] ?: 465)
            put("user", p[Keys.USER] ?: "")
            // 密码使用设备绑定的 KeyStore 加密，无法跨设备转移，故不导出
            put("hasPass", (p[Keys.ENCRYPTED_PASS] ?: "").isNotBlank())
            put("to", p[Keys.TO] ?: "")
            put("subject", p[Keys.SUBJECT] ?: "你今天还好吗？")
            put("body", p[Keys.BODY] ?: "今天一天没有收到 Alive 的签到，请确认手机主人状态。")
            put("resendHours", p[Keys.RESEND_HOURS] ?: 6)
        }.toString(2)
    }

    suspend fun importConfig(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            context.aliveDataStore.edit { p ->
                p[Keys.ENABLED] = obj.optBoolean("enabled", false)
                p[Keys.HOST] = obj.optString("host", "smtp.qq.com")
                p[Keys.PORT] = obj.optInt("port", 465)
                p[Keys.USER] = obj.optString("user", "")
                // 密码设备绑定，导入时清空，需重新输入
                p[Keys.ENCRYPTED_PASS] = ""
                p[Keys.TO] = obj.optString("to", "")
                p[Keys.SUBJECT] = obj.optString("subject", "你今天还好吗？")
                p[Keys.BODY] = obj.optString("body", "今天一天没有收到 Alive 的签到，请确认手机主人状态。")
                p[Keys.RESEND_HOURS] = obj.optInt("resendHours", 6).coerceIn(1, 24)
            }
            true
        } catch (t: Throwable) {
            false
        }
    }
}
