package com.alive.alive.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alive.alive.util.CryptoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.crypto.SecretKey

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

/**
 * SMTP 配置仓库。
 *
 * 安全模型：
 *  - 用户首次使用时通过 [setMasterPassword] 设置一个"加密密码"
 *  - 该加密密码通过 PBKDF2 派生 AES 密钥，加密 SMTP 应用密码后持久化
 *  - 加密密码本身只存校验哈希 + salt，无法反推
 *  - 应用启动后处于"已锁"状态，必须调用 [unlock] 输入正确加密密码后，
 *    [SmtpConfig.pass] 才会从密文解密到内存中供使用
 *  - 忘记加密密码 → 无法解密 → 调用 [resetAll] 删除全部配置重置
 *
 * 导入导出：
 *  - [exportConfig] 输出 JSON 字符串（含密文 pass + salt + hash），可跨设备迁移
 *  - [importConfig] 读取 JSON，导入后仍需原加密密码解锁
 */
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
        val MASTER_PWD_SALT = stringPreferencesKey("master.salt")
        val MASTER_PWD_HASH = stringPreferencesKey("master.hash")
    }

    /** 内存中解密后的 SMTP 应用密码，null 表示未解锁。 */
    private val _decryptedPass = MutableStateFlow<String?>(null)

    /** 内存中缓存的 AES 密钥（仅 unlock 后可用），用于修改 pass 时重新加密。 */
    private var cachedKey: SecretKey? = null

    /** 是否已设置加密密码。 */
    val isMasterPasswordSet: Flow<Boolean> = context.aliveDataStore.data.map { p ->
        p[Keys.MASTER_PWD_HASH]?.isNotBlank() == true &&
            p[Keys.MASTER_PWD_SALT]?.isNotBlank() == true
    }

    /** 是否已解锁（内存中有解密的 pass）。 */
    val isUnlocked: Flow<Boolean> = _decryptedPass.map { it != null }

    val configFlow: Flow<SmtpConfig> =
        context.aliveDataStore.data.combine(_decryptedPass) { p, pass ->
            SmtpConfig(
                enabled = p[Keys.ENABLED] ?: false,
                host = p[Keys.HOST] ?: "smtp.qq.com",
                port = p[Keys.PORT] ?: 465,
                user = p[Keys.USER] ?: "",
                pass = pass ?: "",
                to = p[Keys.TO] ?: "",
                subject = p[Keys.SUBJECT] ?: "你今天还好吗？",
                body = p[Keys.BODY] ?: "今天一天没有收到 Alive 的签到，请确认手机主人状态。",
                resendHours = (p[Keys.RESEND_HOURS] ?: 6).coerceIn(1, 24)
            )
        }

    suspend fun current(): SmtpConfig = configFlow.first()

    suspend fun isLocked(): Boolean = _decryptedPass.value == null

    /**
     * 首次设置加密密码并用它加密保存 SMTP 应用密码。
     * 若之前已设置过加密密码，请先用 [resetAll] 清空再重新设置。
     */
    suspend fun setMasterPassword(password: String, cfg: SmtpConfig) {
        require(password.isNotBlank()) { "加密密码不能为空" }
        val salt = CryptoHelper.randomSalt()
        val key = CryptoHelper.deriveKey(password.toCharArray(), salt)
        val hash = CryptoHelper.hashPassword(password.toCharArray(), salt)
        val encryptedPass = if (cfg.pass.isNotBlank()) {
            CryptoHelper.bytesToBase64(CryptoHelper.encrypt(cfg.pass, key))
        } else ""
        context.aliveDataStore.edit { p ->
            p[Keys.MASTER_PWD_SALT] = CryptoHelper.bytesToBase64(salt)
            p[Keys.MASTER_PWD_HASH] = CryptoHelper.bytesToBase64(hash)
            p[Keys.ENABLED] = cfg.enabled
            p[Keys.HOST] = cfg.host
            p[Keys.PORT] = cfg.port
            p[Keys.USER] = cfg.user
            p[Keys.ENCRYPTED_PASS] = encryptedPass
            p[Keys.TO] = cfg.to
            p[Keys.SUBJECT] = cfg.subject
            p[Keys.BODY] = cfg.body
            p[Keys.RESEND_HOURS] = cfg.resendHours.coerceIn(1, 24)
        }
        cachedKey = key
        _decryptedPass.value = cfg.pass
    }

    /**
     * 用加密密码解锁：校验哈希，通过则解密 pass 到内存。
     * 返回 true 表示密码正确并已解锁；false 表示密码错误或无配置。
     */
    suspend fun unlock(password: String): Boolean {
        val p = context.aliveDataStore.data.first()
        val saltStr = p[Keys.MASTER_PWD_SALT] ?: return false
        val hashStr = p[Keys.MASTER_PWD_HASH] ?: return false
        val salt = CryptoHelper.base64ToBytes(saltStr)
        val storedHash = CryptoHelper.base64ToBytes(hashStr)
        val testHash = CryptoHelper.hashPassword(password.toCharArray(), salt)
        if (!CryptoHelper.constantTimeEquals(testHash, storedHash)) return false
        val key = CryptoHelper.deriveKey(password.toCharArray(), salt)
        val encPassStr = p[Keys.ENCRYPTED_PASS] ?: ""
        _decryptedPass.value = if (encPassStr.isBlank()) {
            ""
        } else {
            try {
                CryptoHelper.decrypt(CryptoHelper.base64ToBytes(encPassStr), key)
            } catch (t: Throwable) {
                null
            }
        }
        cachedKey = key
        return true
    }

    /**
     * 保存 SMTP 配置（必须先解锁）。
     * - 若 pass 未修改：保留原密文
     * - 若 pass 被修改：用 unlock 时缓存的 AES 密钥重新加密
     * - 若 pass 被清空：清空密文
     * 若未解锁（无缓存密钥）则抛异常。
     */
    suspend fun save(cfg: SmtpConfig) {
        val p = context.aliveDataStore.data.first()
        val saltStr = p[Keys.MASTER_PWD_SALT]
            ?: throw IllegalStateException("尚未设置加密密码")
        val hashStr = p[Keys.MASTER_PWD_HASH]
            ?: throw IllegalStateException("尚未设置加密密码")
        val key = cachedKey
            ?: throw IllegalStateException("请先解锁加密存储")
        val oldEncPass = p[Keys.ENCRYPTED_PASS] ?: ""
        val newPass = cfg.pass
        val currentDecrypted = _decryptedPass.value ?: ""
        val finalEncPass = when {
            // pass 未修改：保留原密文
            newPass == currentDecrypted && oldEncPass.isNotBlank() -> oldEncPass
            // pass 被清空
            newPass.isBlank() -> ""
            // pass 被修改：用缓存密钥重新加密
            else -> CryptoHelper.bytesToBase64(CryptoHelper.encrypt(newPass, key))
        }
        context.aliveDataStore.edit { pp ->
            pp[Keys.ENABLED] = cfg.enabled
            pp[Keys.HOST] = cfg.host
            pp[Keys.PORT] = cfg.port
            pp[Keys.USER] = cfg.user
            pp[Keys.ENCRYPTED_PASS] = finalEncPass
            pp[Keys.TO] = cfg.to
            pp[Keys.SUBJECT] = cfg.subject
            pp[Keys.BODY] = cfg.body
            pp[Keys.RESEND_HOURS] = cfg.resendHours.coerceIn(1, 24)
        }
        _decryptedPass.value = newPass
    }

    /**
     * 删除全部 SMTP 配置 + 加密密码。用于"忘记密码"重置场景。
     */
    suspend fun resetAll() {
        context.aliveDataStore.edit { it.clear() }
        _decryptedPass.value = null
        cachedKey = null
    }

    /** 锁定：清空内存中的解密 pass 与缓存密钥。 */
    fun lock() {
        _decryptedPass.value = null
        cachedKey = null
    }

    /**
     * 导出 SMTP 配置为 JSON 字符串（含密文 pass + salt + hash）。
     * 可写入文件分享到其他设备，导入后仍需原加密密码解锁。
     */
    suspend fun exportConfig(): String {
        val p = context.aliveDataStore.data.first()
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("enabled", p[Keys.ENABLED] ?: false)
            put("host", p[Keys.HOST] ?: "smtp.qq.com")
            put("port", p[Keys.PORT] ?: 465)
            put("user", p[Keys.USER] ?: "")
            put("encryptedPass", p[Keys.ENCRYPTED_PASS] ?: "")
            put("masterPwdSalt", p[Keys.MASTER_PWD_SALT] ?: "")
            put("masterPwdHash", p[Keys.MASTER_PWD_HASH] ?: "")
            put("to", p[Keys.TO] ?: "")
            put("subject", p[Keys.SUBJECT] ?: "你今天还好吗？")
            put("body", p[Keys.BODY] ?: "今天一天没有收到 Alive 的签到，请确认手机主人状态。")
            put("resendHours", p[Keys.RESEND_HOURS] ?: 6)
        }.toString(2)
    }

    /**
     * 从 JSON 字符串导入配置。导入后处于锁定状态，需用原加密密码 [unlock]。
     * 返回 true 表示导入成功。
     */
    suspend fun importConfig(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            context.aliveDataStore.edit { p ->
                p[Keys.ENABLED] = obj.optBoolean("enabled", false)
                p[Keys.HOST] = obj.optString("host", "smtp.qq.com")
                p[Keys.PORT] = obj.optInt("port", 465)
                p[Keys.USER] = obj.optString("user", "")
                p[Keys.ENCRYPTED_PASS] = obj.optString("encryptedPass", "")
                p[Keys.MASTER_PWD_SALT] = obj.optString("masterPwdSalt", "")
                p[Keys.MASTER_PWD_HASH] = obj.optString("masterPwdHash", "")
                p[Keys.TO] = obj.optString("to", "")
                p[Keys.SUBJECT] = obj.optString("subject", "你今天还好吗？")
                p[Keys.BODY] = obj.optString("body", "今天一天没有收到 Alive 的签到，请确认手机主人状态。")
                p[Keys.RESEND_HOURS] = obj.optInt("resendHours", 6).coerceIn(1, 24)
            }
            _decryptedPass.value = null
            true
        } catch (t: Throwable) {
            false
        }
    }
}
