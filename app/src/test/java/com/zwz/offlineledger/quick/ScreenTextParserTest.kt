package com.zwz.offlineledger.quick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTextParserTest {

    private val now = 1732000000000L

    @Test
    fun parseExpenseWithYuanSign() {
        val bill = ScreenTextParser.parse(
            listOf("永辉超市", "付款金额", "¥38.50", "2026-11-19 12:30", "牛奶鸡蛋"), now
        )
        assertNotNull(bill)
        assertEquals(3850L, bill!!.amountCents)
        assertFalse(bill.isIncome)
        assertEquals("永辉超市", bill.counterparty)
    }

    @Test
    fun parseIncomeByKeyword() {
        val bill = ScreenTextParser.parse(
            listOf("退款成功", "¥12.00", "收款方：某商城", "2026-11-18"), now
        )
        assertNotNull(bill)
        assertTrue(bill!!.isIncome)
        assertEquals(1200L, bill.amountCents)
    }

    @Test
    fun noAmountReturnsNull() {
        assertNull(ScreenTextParser.parse(listOf("交易成功", "账单详情"), now))
        assertNull(ScreenTextParser.parse(emptyList(), now))
    }

    @Test
    fun amountUsesBigDecimalNotFloat() {
        val bill = ScreenTextParser.parse(listOf("金额", "0.10"), now)
        assertEquals(10L, bill!!.amountCents)
        val bill2 = ScreenTextParser.parse(listOf("金额", "1,234.56"), now)
        assertEquals(123456L, bill2!!.amountCents)
    }

    @Test
    fun fingerprintStableAcrossRepeatedTileClicks() {
        val a = ScreenTextParser.parse(listOf("肯德基", "¥25.00", "2026-11-19"), now)
        val b = ScreenTextParser.parse(listOf("肯德基", "¥25.00", "2026-11-19"), now + 5000L)
        assertNotNull(a); assertNotNull(b)
        assertEquals(a!!.fingerprint, b!!.fingerprint)
    }

    @Test
    fun fingerprintDiffersWhenAmountDiffers() {
        val a = ScreenTextParser.fingerprint(2500L, false, "肯德基", "", "2026-11-19")
        val b = ScreenTextParser.fingerprint(2600L, false, "肯德基", "", "2026-11-19")
        assertTrue(a != b)
    }
}
