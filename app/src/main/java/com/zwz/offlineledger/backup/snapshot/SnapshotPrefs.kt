package com.zwz.offlineledger.backup.snapshot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * 自动快照的目录、开关与口令保管。
 *
 * 口令由使用者自设，用 AndroidKeystore 里的 AES-256/GCM 密钥加密后存
 * SharedPreferences；明文不落盘。卸载重装后 Keystore 条目会消失，但快照
 * 解密不依赖它 —— 恢复时由使用者再次输入同一口令即可（SecureBackupCodec
 * 本身就是自包含加密）。数据库口令与本口令完全独立，不带出设备。
 */
object SnapshotPrefs {

    private const val PREFS = "offline_ledger_snapshot"
    private const val ALIAS = "offline_ledger_snapshot_pass"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_AUTO = "auto_enabled"
    private const val KEY_CIPHER = "pass_cipher"
    private const val KEY_IV = "pass_iv"
    private const val TAG_BITS = 128

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun treeUri(c: Context): String? = prefs(c).getString(KEY_TREE, null)

    fun setTreeUri(c: Context, uri: String?) {
        prefs(c).edit().apply {
            if (uri == null) remove(KEY_TREE) else putString(KEY_TREE, uri)
            apply()
        }
    }

    fun autoEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO, false)

    fun setAutoEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_AUTO, on).apply()
    }

    fun hasPassphrase(c: Context): Boolean = prefs(c).getString(KEY_CIPHER, null) != null

    private fun secretKey(): java.security.Key {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(spec) }
                .generateKey()
        }
        return (ks.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun savePassphrase(c: Context, pass: CharArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(String(pass).toByteArray(Charsets.UTF_8))
        prefs(c).edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    /** Keystore 条目随卸载丢失时返回 null，页面会让使用者手动输入口令。 */
    fun loadPassphrase(c: Context): CharArray? {
        val p = prefs(c)
        val encrypted = p.getString(KEY_CIPHER, null) ?: return null
        val iv = p.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, secretKey(),
                GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                Charsets.UTF_8
            ).toCharArray()
        } catch (e: Exception) {
            null
        }
    }
}
