package com.zwz.offlineledger.quick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 50 批：脱敏的账单页 fixture，把「OCR 错字」与「选错块」分开锁死。 */
class ScreenTextParserTraceTest {

    private val now = 1732000000000L

    /** 脱敏样本：金额行、余额行、日期行、商户与描述共存。 */
    private fun billPage() = listOf(
        "交易详情",
        "永辉超市",
        "实付金额",
        "¥38.50",
        "账户余额 ¥1,286.40",
        "2026-11-19 12:30",
        "商品说明：牛奶鸡蛋",
        "交易成功"
    )

    @Test
    fun weakAmountIsRejectedNotPicked() {
        val trace = ScreenTextParser.parseWithTrace(billPage(), now)
        assertNotNull(trace)
        assertEquals(3850L, trace!!.bill.amountCents)
        assertTrue(trace.amountOptions.any { it.amountCents == 128640L && it.rejected != null })
        assertTrue(trace.amountOptions.any { it.rejected == "日期 / 时间" })
    }

    @Test
    fun picksAmountRuleAndMerchantDescriptionAreSeparated() {
        val trace = ScreenTextParser.parseWithTrace(billPage(), now)!!
        val amountTrace = trace.fields.first { it.field == "金额" }
        // AMOUNT 正则的 group 1 只取数字字面量，货币符号在行级规则里判定。
        assertEquals("38.50", amountTrace.rawText)
        assertTrue(amountTrace.rule.isNotBlank())
        assertEquals("永辉超市", trace.bill.counterparty)
        assertEquals("牛奶鸡蛋", trace.bill.description)
        assertEquals("", trace.bill.note)
    }

    @Test
    fun parseStillDelegatesToTrace() {
        val a = ScreenTextParser.parse(billPage(), now)
        val b = ScreenTextParser.parseWithTrace(billPage(), now)!!.bill
        assertNotNull(a)
        assertEquals(a!!.amountCents, b.amountCents)
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test
    fun manualAmountSelectionKeepsFingerprintForIdempotency() {
        val trace = ScreenTextParser.parseWithTrace(billPage(), now)!!
        val alt = trace.amountOptions.first { it.amountCents == 128640L }
        val changed = ScreenTextParser.withAmount(trace.bill, alt.amountCents)
        assertEquals(128640L, changed.amountCents)
        assertEquals(trace.bill.fingerprint, changed.fingerprint)
    }

    @Test
    fun manualCounterpartyAndDescriptionSelection() {
        val trace = ScreenTextParser.parseWithTrace(billPage(), now)!!
        val changed = ScreenTextParser.withCounterparty(
            ScreenTextParser.withDescription(trace.bill, "自有描述"), "自有对方"
        )
        assertEquals("自有对方", changed.counterparty)
        assertEquals("自有描述", changed.description)
        assertEquals(trace.bill.fingerprint, changed.fingerprint)
    }

    @Test
    fun onlyWeakAmountsNeedsManual() {
        val lines = listOf("账户余额 ¥1,286.40", "交易成功")
        assertNull(ScreenTextParser.parse(lines, now))
    }
}
