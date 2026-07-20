package com.alive.alive.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 用户 SMTP 应用密码的本机加密工具。
 *
 * - 使用用户自定义的"加密密码"通过 PBKDF2WithHmacSHA256 派生 256-bit AES 密钥
 * - 加密算法：AES/GCM/NoPadding（带 IV 与认证标签）
 * - 加密密码本身只存 PBKDF2 哈希 + salt，用于校验输入是否正确
 * - 忘记加密密码 → 无法解密 pass → 只能 [SettingsRepository.resetAll] 删除全部配置重置
 */
object CryptoHelper {

    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12 // GCM 推荐 12 字节
    private const val GCM_TAG_LENGTH_BITS = 128

    fun randomSalt(): ByteArray = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }

    /** 用 password + salt 派生 AES 密钥。 */
    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /** 计算 password 的校验哈希（与密钥派生同参数，但仅用于比对，不直接当密钥）。 */
    fun hashPassword(password: CharArray, salt: ByteArray): ByteArray =
        deriveKey(password, salt).encoded

    /** 加密明文，返回 iv + cipherText 的拼接字节。 */
    fun encrypt(plain: String, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return iv + cipherText
    }

    /** 解密 [encrypt] 产生的字节。失败会抛异常。 */
    fun decrypt(data: ByteArray, key: SecretKey): String {
        require(data.size > IV_LENGTH) { "密文长度非法" }
        val iv = data.copyOfRange(0, IV_LENGTH)
        val cipherText = data.copyOfRange(IV_LENGTH, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    fun bytesToBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun base64ToBytes(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
