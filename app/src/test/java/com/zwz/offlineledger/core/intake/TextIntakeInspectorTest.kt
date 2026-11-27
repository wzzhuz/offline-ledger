package com.zwz.offlineledger.core.intake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 86 批：只读分析器的纯 JVM 契约。本批不落库，故只验「摊开原文」的正确性。 */
class TextIntakeInspectorTest {

    private val alipayBill = listOf(
        "账单详情",
        "-12.00",
        "交易成功",
        "支付时间  2026-11-26 17:45:47",
        "付款方式  交通银行信用卡(2203)",
        "南京青谷酒店管理有限公司",
        "商品说明  数码电器",
    ).joinToString("\n")

    @Test
    fun `alipay bill exposes single amount and four signals`() {
        val p = TextIntakeInspector.from("send", alipayBill)
        assertEquals("send", p.source)
        assertEquals(listOf("-12.00"), p.amountCandidates)
        assertTrue(p.hasDirection)
        assertTrue(p.hasTime)
        assertTrue(p.hasCounterparty)
        assertTrue(p.readyForAutoCommit)
        assertFalse(p.truncated)
    }

    @Test
    fun `multiple amount candidates never collapse into one guessed answer`() {
        val p = TextIntakeInspector.from("process_text", "合计 -12.00 优惠 0.20 实付 11.80 2026-11-26 17:45 交易成功 某某有限公司")
        assertTrue(p.amountCandidates.size >= 2)
        assertTrue(p.amountCandidates.contains("-12.00"))
        assertFalse(p.readyForAutoCommit)
    }

    @Test
    fun `direction also inferred from a negative amount`() {
        val p = TextIntakeInspector.from("send", "-8.50 2026-11-26 17:45 某某超市")
        assertTrue(p.hasDirection)
    }

    @Test
    fun `long text is truncated and marked, never silently dropped`() {
        val raw = "a".repeat(TextIntakeInspector.MAX_CHARS + 123)
        val p = TextIntakeInspector.from("send", raw)
        assertTrue(p.truncated)
        assertEquals(TextIntakeInspector.MAX_CHARS, p.rawText.length)
        assertEquals(raw.length, p.originalCharCount)
        assertFalse(p.readyForAutoCommit)
    }

    @Test
    fun `empty or null input is a safe no-op`() {
        val a = TextIntakeInspector.from("empty", "")
        val b = TextIntakeInspector.from("none", null)
        assertTrue(a.amountCandidates.isEmpty())
        assertTrue(b.amountCandidates.isEmpty())
        assertFalse(a.readyForAutoCommit)
        assertFalse(b.readyForAutoCommit)
    }

    @Test
    fun `cleared text releases the raw string`() {
        val p = TextIntakeInspector.from("send", "")
        assertEquals("", p.rawText)
        assertEquals(0, p.originalCharCount)
    }
}
