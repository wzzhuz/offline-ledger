package com.zwz.offlineledger.backup.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第 63 批：账户表允许同名，唯一键是 id。CSV 导出带卡号后，
 * 导入 MUST 按 (name, cardLast4) 复合匹配；老 CSV 缺列时同名多条
 * MUST 报歧义，MUST NOT 静默取 firstOrNull。
 */
class AccountCsvMatchTest {

    private fun refs() = listOf(
        AccountRef(1L, "工商银行", "1122"),
        AccountRef(2L, "工商银行", "3344"),
        AccountRef(3L, "微信零钱", null)
    )

    @Test
    fun `own export cell plus explicit column resolves the right card`() {
        val parsed = parseAccountCell("工商银行 (3344)", "3344")
        assertEquals("工商银行", parsed.name)
        assertEquals("3344", parsed.last4)
        val m = resolveAccount(refs(), parsed)
        assertTrue(m is AccountMatch.Unique)
        assertEquals(2L, (m as AccountMatch.Unique).ref.id)
    }

    @Test
    fun `display-name cell without column still resolves`() {
        val parsed = parseAccountCell("工商银行 (1122)", null)
        assertEquals("工商银行", parsed.name)
        assertEquals("1122", parsed.last4)
        val m = resolveAccount(refs(), parsed)
        assertEquals(1L, (m as AccountMatch.Unique).ref.id)
    }

    @Test
    fun `explicit column wins over inline suffix`() {
        val parsed = parseAccountCell("工商银行 (1122)", "3344")
        assertEquals("3344", parsed.last4)
        assertEquals(2L, (resolveAccount(refs(), parsed) as AccountMatch.Unique).ref.id)
    }

    @Test
    fun `legacy plain csv with unique name still resolves`() {
        val parsed = parseAccountCell("微信零钱", null)
        assertEquals(null, parsed.last4)
        assertEquals(3L, (resolveAccount(refs(), parsed) as AccountMatch.Unique).ref.id)
    }

    @Test
    fun `legacy plain csv with duplicate names is ambiguous not silently first`() {
        val parsed = parseAccountCell("工商银行", null)
        val m = resolveAccount(refs(), parsed)
        assertTrue(m is AccountMatch.Ambiguous)
        val amb = m as AccountMatch.Ambiguous
        assertEquals(2, amb.candidates.size)
        assertTrue(amb.reason.contains("cardLast4"))
        assertTrue(amb.reason.contains("工商银行 (1122)"))
        assertTrue(amb.reason.contains("工商银行 (3344)"))
    }

    @Test
    fun `unknown account is reported as missing`() {
        val m = resolveAccount(refs(), parseAccountCell("招商银行 (9999)", "9999"))
        assertTrue(m is AccountMatch.Missing)
    }

    @Test
    fun `legacy db name already carrying suffix still matches`() {
        // v0.2.40 及更早把 " (1122)" 拼进了 name 本身。
        val legacy = listOf(AccountRef(9L, "中行储蓄卡 (1122)", "1122"))
        val m = resolveAccount(legacy, parseAccountCell("中行储蓄卡 (1122)", "1122"))
        assertTrue(m is AccountMatch.Unique)
        assertEquals(9L, (m as AccountMatch.Unique).ref.id)
    }

    @Test
    fun `non-four-digit suffix is a literal part of the name`() {
        val parsed = parseAccountCell("我的卡 (12)", null)
        assertEquals("我的卡 (12)", parsed.name)
        assertEquals(null, parsed.last4)
    }
}
