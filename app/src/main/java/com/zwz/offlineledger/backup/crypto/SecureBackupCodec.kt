package com.zwz.offlineledger.backup.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件加密（纯 JVM，只用 javax.crypto / java.util.zip / java.security）。
 *
 * 与 SQLCipher 的数据库口令**完全无关**：
 * - 数据库口令由 Android Keystore 保管，换设备即失效、用户不可见
 * - 备份口令由用户自设、不落盘、跨设备必须仍可解密
 *
 * 文件布局（MAGIC 之后的字节全部是密文或随机数）：
 *   [0..4]   MAGIC   = "OLBK" + 0x01        5 bytes
 *   [5..20]  SALT    = 16 bytes，每次随机
 *   [21..32] IV      = 12 bytes，每次随机
 *   [33..]   AES-256/GCM( Gzip(JSON) )      含 128-bit tag
 */
object SecureBackupCodec {

    private val MAGIC = byteArrayOf(0x4F, 0x4C, 0x42, 0x4B, 0x01) // "OLBK" + v1
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val PBKDF2_ITERATIONS = 120_000
    const val KEY_BITS = 256
    const val GCM_TAG_BITS = 128
    const val MIN_PASSPHRASE_LENGTH = 6

    private const val HEADER_BYTES = 5 + SALT_BYTES + IV_BYTES

    /** 压缩与加密都是流式的；不产生第二份完整明文副本。 */
    fun encode(plainJson: String, passphrase: CharArray): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "口令至少 $MIN_PASSPHRASE_LENGTH 位" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(passphrase, salt),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )

        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { gz ->
            gz.write(plainJson.toByteArray(Charsets.UTF_8))
        }
        val ciphertext = cipher.doFinal(compressed.toByteArray())

        val out = ByteArrayOutputStream(HEADER_BYTES + ciphertext.size)
        out.write(MAGIC, 0, MAGIC.size)
        out.write(salt, 0, salt.size)
        out.write(iv, 0, iv.size)
        out.write(ciphertext, 0, ciphertext.size)
        return out.toByteArray()
    }

    /** 任何失败都归到同一句提示，避免口令探测。调用方 MUST NOT 在失败后写账本。 */
    fun decode(blob: ByteArray, passphrase: CharArray): String {
        require(blob.size > HEADER_BYTES + GCM_TAG_BITS / 8) { "备份文件不完整" }
        for (i in MAGIC.indices) {
            require(blob[i] == MAGIC[i]) { "不是离线账本加密备份（magic 不匹配）" }
        }
        var p = MAGIC.size
        val salt = blob.copyOfRange(p, p + SALT_BYTES)
        p += SALT_BYTES
        val iv = blob.copyOfRange(p, p + IV_BYTES)
        p += IV_BYTES
        val ciphertext = blob.copyOfRange(p, blob.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(passphrase, salt),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val plain = try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw IllegalArgumentException("口令错误或备份文件已损坏", e)
        }
        return try {
            GZIPInputStream(ByteArrayInputStream(plain)).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("口令错误或备份文件已损坏", e)
        }
    }

    fun isEncryptedBackup(blob: ByteArray): Boolean =
        blob.size > HEADER_BYTES && MAGIC.indices.all { blob[it] == MAGIC[it] }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(secret, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
