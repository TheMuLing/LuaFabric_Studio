package com.luafabric.studio.falling.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的密钥设施（仅 :app 模块，产物不携带）：
 * 1. [deriveMmkvKey]：为 Studio 私有 MMKV 派生 AES-256 cryptKey（32B）。
 *    用 Keystore HMAC-SHA256 密钥对固定明文做确定性 KDF（Keystore GCM 禁止调用方
 *    提供 IV，固定 IV GCM 派生在实机会抛 InvalidAlgorithmParameterException）——
 *    「密钥派生」用法，固定明文只用于派生，绝不用于真实数据加密，
 *    保证跨进程重启唯一、不可导出、未经 Keystore 无法复原。
 * 2. [sealString]/[openString]：对敏感值（pass / user_json）做 AES-GCM（随机 IV）双层加密，
 *    输出 Base64(iv + 密文 + tag)。
 */
object KeystoreCrypto {
    private const val KEY_ALIAS = "luafabric_studio_mmkv"
    private const val DERIVE_ALIAS = "luafabric_studio_mmkv_derive"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    // 仅用于确定性派生（KDF），非真实数据加密 —— 固定明文在此场景安全
    private val DERIVE_PLAINTEXT = "luafabric-studio-derive".toByteArray(Charsets.UTF_8)

    private val key: SecretKey by lazy { getOrCreateKey() }
    private val hmacKey: SecretKey by lazy { getOrCreateHmacKey() }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    /** Keystore HMAC-SHA256 密钥：无 IV 约束、输出确定，且密钥不可导出。 */
    private fun getOrCreateHmacKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(DERIVE_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(DERIVE_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    /**
     * 派生 Studio MMKV 的 AES-256 cryptKey（32B，确定性）。
     * Keystore GCM 禁止调用方提供 IV（实机抛 InvalidAlgorithmParameterException），
     * 故改用 Keystore HMAC-SHA256 对固定明文做确定性派生 —— 同 key 同明文输出恒定，
     * key 不可导出，未经 Keystore 无法复原。
     */
    fun deriveMmkvKey(): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        return mac.doFinal(DERIVE_PLAINTEXT).copyOf(32)
    }

    /** AES-GCM 加密敏感值 → Base64(iv+ct+tag)；失败抛异常由调用方决定兜底 */
    fun sealString(plain: String): String {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key)
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(c.iv + ct)
    }

    /** 解密 [sealString] 产物；解密失败（密钥缺失/数据损坏）返回 null */
    fun openString(sealed: String): String? = try {
        val raw = Base64.getDecoder().decode(sealed)
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}