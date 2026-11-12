package com.zwz.offlineledger.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * SQLCipher 数据库口令的保管。
 *
 * 口令本身由 SecureRandom 生成 32 字节，用 Android Keystore 里的
 * AES-256/GCM 密钥加密后（密文 + IV）存 SharedPreferences。
 * 密钥材料不离开 Keystore，口令不以明文持久化。
 * 重启后解密得到同一口令，否则数据库会打不开（等于数据全丢）。
 */
object DatabaseKeyManager {

    private const val KEY_ALIAS = "offline_ledger_db_key"
    private const val PREFS = "offline_ledger_key_meta"
    private const val KEY_CIPHER = "ciphertext"
    private const val KEY_IV = "iv"
    private const val PASSPHRASE_BYTES = 32
    private const val GCM_TAG_BITS = 128

    fun getOrCreatePassphrase(context: Context): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secretKey = if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            generator.init(spec)
            generator.generateKey()
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_CIPHER, null)
        if (stored == null) {
            val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encrypted = cipher.doFinal(passphrase)
            prefs.edit()
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
            return passphrase
        }

        val iv = Base64.decode(
            prefs.getString(KEY_IV, null)
                ?: error("数据库口令密文存在但 IV 缺失"),
            Base64.NO_WRAP
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(Base64.decode(stored, Base64.NO_WRAP))
    }
}
