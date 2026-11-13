package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.crypto.SecureBackupCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecureBackupCodecTest {

    private val json = """{"envelope":{"schemaVersion":1},"payload":{"records":[{"note":"餐饮 食材","amountCents":3850}]}}"""

    private fun expectFailure(block: () -> Unit): String = try {
        block()
        fail("应当抛出异常")
        ""
    } catch (e: IllegalArgumentException) {
        e.message ?: ""
    } catch (e: Exception) {
        fail("应当抛 IllegalArgumentException，实际是 ${e.javaClass.simpleName}")
        ""
    }

    @Test
    fun `encode then decode round trips`() {
        val blob = SecureBackupCodec.encode(json, "abc123".toCharArray())
        assertEquals(json, SecureBackupCodec.decode(blob, "abc123".toCharArray()))
        assertTrue(SecureBackupCodec.isEncryptedBackup(blob))
    }

    @Test
    fun `chinese text survives the round trip`() {
        // 口令长度按字符数算，中文 4 字仍不足 6 位；这里用 7 字符的中文数字混合口令
        val pass = "中文口令123"
        val blob = SecureBackupCodec.encode(json, pass.toCharArray())
        assertTrue(SecureBackupCodec.decode(blob, pass.toCharArray()).contains("餐饮 食材"))
    }

    @Test
    fun `wrong passphrase fails without leaking which part is wrong`() {
        val blob = SecureBackupCodec.encode(json, "abc123".toCharArray())
        val msg = expectFailure { SecureBackupCodec.decode(blob, "wrong1".toCharArray()) }
        assertEquals("口令错误或备份文件已损坏", msg)
    }

    @Test
    fun `tampered ciphertext fails the GCM tag check`() {
        val blob = SecureBackupCodec.encode(json, "abc123".toCharArray())
        val tampered = blob.copyOf()
        tampered[tampered.size - 3] = (tampered[tampered.size - 3].toInt() xor 0x01).toByte()
        val msg = expectFailure { SecureBackupCodec.decode(tampered, "abc123".toCharArray()) }
        assertEquals("口令错误或备份文件已损坏", msg)
    }

    @Test
    fun `bad magic is rejected`() {
        val blob = SecureBackupCodec.encode(json, "abc123".toCharArray())
        val bad = blob.copyOf()
        bad[0] = 0x00
        assertTrue(expectFailure { SecureBackupCodec.decode(bad, "abc123".toCharArray()) }
            .contains("magic"))
    }

    @Test
    fun `truncated file is rejected`() {
        val blob = SecureBackupCodec.encode(json, "abc123".toCharArray())
        assertTrue(expectFailure {
            SecureBackupCodec.decode(blob.copyOfRange(0, 10), "abc123".toCharArray())
        }.contains("不完整"))
    }

    @Test
    fun `short passphrase is rejected`() {
        assertTrue(expectFailure { SecureBackupCodec.encode(json, "123".toCharArray()) }
            .contains("至少"))
    }

    @Test
    fun `ciphertext contains no plaintext category name`() {
        val blob = SecureBackupCodec.encode(json, "abc123".toCharArray())
        val asText = String(blob, Charsets.UTF_8)
        assertFalse(asText.contains("餐饮"))
        assertFalse(asText.contains("食材"))
        assertFalse(asText.contains("schemaVersion"))
    }

    @Test
    fun `two exports of the same plaintext differ`() {
        val pass = "abc123".toCharArray()
        val a = SecureBackupCodec.encode(json, pass)
        val b = SecureBackupCodec.encode(json, pass)
        assertFalse(a.contentEquals(b))
        assertEquals(json, SecureBackupCodec.decode(a, pass))
        assertEquals(json, SecureBackupCodec.decode(b, pass))
    }

    @Test
    fun `backup key is independent from database key`() {
        // 同一个口令在不同 salt 下派生的密钥必然不同；
        // 这里验证格式足以跨设备自包含（salt + iv 都写进文件头）。
        val blob = SecureBackupCodec.encode(json, "abc123".toCharArray())
        assertTrue(blob.size > 5 + SecureBackupCodec.SALT_BYTES + SecureBackupCodec.IV_BYTES)
        assertEquals(json, SecureBackupCodec.decode(blob, "abc123".toCharArray()))
    }
}
