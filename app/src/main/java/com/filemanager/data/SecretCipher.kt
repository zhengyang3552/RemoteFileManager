package com.filemanager.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 连接密码的本地加密存储。
 *
 * - 优先使用 Android Keystore 中的 AES-256/GCM 密钥（密钥不出安全硬件，应用无需自管主密钥）；
 * - 若设备 Keystore 异常（部分定制 ROM / 模拟器），退化为 Base64 编码并标记 `b64:`，
 *   保证功能可用，同时在文档中明确该降级路径的安全边界。
 */
object SecretCipher {

    private const val PREFIX_AES = "aes:v1:"
    private const val PREFIX_PLAIN = "b64:v1:"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "rfm_connection_secret"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            PREFIX_AES +
                Base64.encodeToString(iv, Base64.NO_WRAP) +
                ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            PREFIX_PLAIN + Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return try {
            when {
                stored.startsWith(PREFIX_AES) -> {
                    val payload = stored.removePrefix(PREFIX_AES)
                    val parts = payload.split(":", limit = 2)
                    if (parts.size != 2) return ""
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val data = Base64.decode(parts[1], Base64.NO_WRAP)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
                    String(cipher.doFinal(data), Charsets.UTF_8)
                }
                stored.startsWith(PREFIX_PLAIN) -> {
                    String(Base64.decode(stored.removePrefix(PREFIX_PLAIN), Base64.NO_WRAP), Charsets.UTF_8)
                }
                // 兼容历史明文配置
                else -> stored
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
