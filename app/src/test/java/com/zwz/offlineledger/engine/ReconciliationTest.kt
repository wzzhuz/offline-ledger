package com.zwz.offlineledger.engine

import com.zwz.offlineledger.engine.Reconciliation.AccountTotalsInput
import com.zwz.offlineledger.engine.Reconciliation.LookupMode
import com.zwz.offlineledger.engine.Reconciliation.SuspectFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconciliationTest {

    @Test
    fun `difference is actual minus book and can be negative`() {
        assertEquals(-1L, Reconciliation.difference(100_000L, 99_999L))
        assertEquals(3_850L, Reconciliation.difference(100_000L, 103_850L))
        assertEquals(0L, Reconciliation.difference(100_000L, 100_000L))
    }

    @Test
    fun `tail difference boundary includes 100 cents`() {
        assertTrue(Reconciliation.isTailDifference(100L))
        assertTrue(Reconciliation.isTailDifference(-100L))
        assertFalse(Reconciliation.isTailDifference(101L))
        assertFalse(Reconciliation.isTailDifference(-101L))
        assertTrue(Reconciliation.isTailDifference(1L))
    }

    @Test
    fun `available credit adds the negative balance`() {
        // 额度 20000.00、欠款 -500.00 -> 可用 195.00（不是 205.00）
        assertEquals(1_950_000L, Reconciliation.availableCredit(2_000_000L, -50_000L))
        assertNull(Reconciliation.availableCredit(null, -50_000L))
    }

    @Test
    fun `net worth is a sum not a subtraction`() {
        val accounts = listOf(
            AccountTotalsInput(1_000_000L, isLiability = false, includeInTotal = true),
            AccountTotalsInput(-50_000L, isLiability = true, includeInTotal = true),
            // 被排除的账户若用「总资产 − 总负债」倒推就会污染净资产
            AccountTotalsInput(200_000L, isLiability = false, includeInTotal = false)
        )
        val t = Reconciliation.displayTotals(accounts)
        assertEquals(950_000L, t.netWorthCents)
        assertEquals(1_000_000L, t.totalAssetsCents)
        assertEquals(50_000L, t.totalLiabilitiesCents)
        // 排除账户确实没进任何展示值
        assertEquals(950_000L, t.totalAssetsCents - t.totalLiabilitiesCents)
    }

    @Test
    fun `display totals with no liabilities`() {
        val t = Reconciliation.displayTotals(
            listOf(AccountTotalsInput(12_345L, false, true))
        )
        assertEquals(12_345L, t.netWorthCents)
        assertEquals(0L, t.totalLiabilitiesCents)
    }

    @Test
    fun `exact and approx lookup bounds`() {
        assertEquals(3_850L to 3_850L,
            Reconciliation.amountLookupBounds(3_850L, LookupMode.EXACT))
        assertEquals(3_750L to 3_950L,
            Reconciliation.amountLookupBounds(3_850L, LookupMode.APPROX))
        // 1 分钱近似反查下界 clamp 到 0，不产生负数
        assertEquals(0L to 101L,
            Reconciliation.amountLookupBounds(1L, LookupMode.APPROX))
    }

    @Test
    fun `range lookup uses user bounds`() {
        assertEquals(2_000L to 5_000L,
            Reconciliation.amountLookupBounds(0L, LookupMode.RANGE,
                rangeMinCents = 2_000L, rangeMaxCents = 5_000L))
        assertEquals(null to null,
            Reconciliation.amountLookupBounds(0L, LookupMode.RANGE))
    }

    @Test
    fun `suspect flag decoding`() {
        val flags = SuspectFlags.ZERO_AMOUNT or SuspectFlags.LARGE_AMOUNT
        assertTrue(Reconciliation.hasFlag(flags, SuspectFlags.ZERO_AMOUNT))
        assertTrue(Reconciliation.hasFlag(flags, SuspectFlags.LARGE_AMOUNT))
        assertFalse(Reconciliation.hasFlag(flags, SuspectFlags.UNCATEGORIZED))
        assertEquals(listOf("金额为 0", "异常大额"), Reconciliation.labels(flags))
        assertEquals(emptyList<String>(), Reconciliation.labels(0))
    }

    @Test
    fun `near duplicate only within 24 hours`() {
        val t0 = 1_700_000_000_000L
        val rows = listOf(
            Reconciliation.ReviewItem(1L, 3_850L, t0),
            Reconciliation.ReviewItem(2L, 3_850L, t0 + 60_000L),          // 1 分钟后，重复
            Reconciliation.ReviewItem(3L, 3_850L, t0 + 90_000_000L),      // 隔天，不算
            Reconciliation.ReviewItem(4L, 9_900L, t0 + 60_000L)           // 金额不同，不算
        )
        val marks = Reconciliation.markNearDuplicates(rows)
        assertEquals(true, marks[1L])
        assertEquals(true, marks[2L])
        assertEquals(false, marks[3L])
        assertEquals(false, marks[4L])
    }
}
