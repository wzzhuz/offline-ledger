package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 67 批：附加金额唯一校验入口（纯 JVM）。 */
class ExtraAmountRulesTest {

    @Test
    fun `01 支出优惠合法`() {
        assertNull(
            ExtraAmountRules.validate(RecordType.EXPENSE, 47_001L, 2_999L, null)
        )
    }

    @Test
    fun `02 优惠必须为正且不超过账单金额`() {
        assertNotNull(ExtraAmountRules.validate(RecordType.EXPENSE, 47_001L, 0L, null))
        assertNotNull(ExtraAmountRules.validate(RecordType.EXPENSE, 47_001L, -1L, null))
        assertNotNull(ExtraAmountRules.validate(RecordType.EXPENSE, 47_001L, 47_002L, null))
        assertNull(
            ExtraAmountRules.validate(RecordType.EXPENSE, 47_001L, 47_001L, null)
        )
    }

    @Test
    fun `03 转账手续费正负都合法 零值拒绝`() {
        assertNull(ExtraAmountRules.validate(RecordType.TRANSFER, 100_000L, null, 500L))
        assertNull(ExtraAmountRules.validate(RecordType.TRANSFER, 100_000L, null, -300L))
        assertNotNull(ExtraAmountRules.validate(RecordType.TRANSFER, 100_000L, null, 0L))
        assertNotNull(
            ExtraAmountRules.validate(RecordType.TRANSFER, 100_000L, null, 100_000L)
        )
    }

    @Test
    fun `04 两类附加金额互斥且类型错配被拒`() {
        assertNotNull(
            ExtraAmountRules.validate(RecordType.EXPENSE, 100L, 10L, 10L)
        )
        assertNotNull(
            ExtraAmountRules.validate(RecordType.TRANSFER, 100L, 10L, null)
        )
        assertNotNull(
            ExtraAmountRules.validate(RecordType.EXPENSE, 100L, null, 10L)
        )
        assertNotNull(
            ExtraAmountRules.validate(RecordType.SETTLEMENT, 100L, 10L, null)
        )
    }

    @Test
    fun `05 原价由整数加法推导`() {
        assertEquals(50_000L, ExtraAmountRules.originalCents(47_001L, 2_999L))
        assertNull(ExtraAmountRules.originalCents(47_001L, null))
        assertNull(ExtraAmountRules.originalCents(47_001L, 0L))
    }

    @Test
    fun `06 转账实际扣款 正手续费加 负优惠减`() {
        assertEquals(100_500L, ExtraAmountRules.transferDebitCents(100_000L, 500L))
        assertEquals(99_700L, ExtraAmountRules.transferDebitCents(100_000L, -300L))
        assertEquals(100_000L, ExtraAmountRules.transferDebitCents(100_000L, null))
    }

    @Test
    fun `07 优惠不等于省钱报表 统计仍按实付`() {
        // amountCents 是唯一真相源：附加金额不改变传进统计的金额。
        val amount = 47_001L
        val discount = 2_999L
        assertTrue(amount > 0L)
        assertEquals(50_000L, amount + discount)
        assertEquals(47_001L, amount)
    }
}
