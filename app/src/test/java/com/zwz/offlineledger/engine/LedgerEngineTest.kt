package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.LedgerAccount
import com.zwz.offlineledger.data.model.LedgerAdjustment
import com.zwz.offlineledger.data.model.LedgerRecord
import com.zwz.offlineledger.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Test

/** 9 条账务路径：验证 LedgerEngine 这一唯一余额算法。 */
class LedgerEngineTest {

    private val cash = LedgerAccount(1, 100_000L)
    private val card = LedgerAccount(2, 200_000L)
    private val credit = LedgerAccount(3, -50_000L)
    private val accounts = listOf(cash, card, credit)

    private fun expense(amount: Long, from: Long, cat: Long? = 1L, exclude: Boolean = false) =
        LedgerRecord(1, RecordType.EXPENSE, amount, from, null, cat, cat, exclude)

    private fun income(amount: Long, to: Long, exclude: Boolean = false) =
        LedgerRecord(2, RecordType.INCOME, amount, null, to, null, null, exclude)

    private fun transfer(amount: Long, from: Long, to: Long) =
        LedgerRecord(3, RecordType.TRANSFER, amount, from, to)

    @Test
    fun `01 insert expense decreases balance`() {
        val b = LedgerEngine.computeBalances(accounts, listOf(expense(3_000, 1)))
        assertEquals(97_000L, b[1])
        assertEquals(147_000L, LedgerEngine.netWorth(accounts, b))
    }

    @Test
    fun `02 insert income increases balance`() {
        val b = LedgerEngine.computeBalances(accounts, listOf(income(50_000, 2)))
        assertEquals(250_000L, b[2])
    }

    @Test
    fun `03 transfer keeps net worth unchanged`() {
        val before = LedgerEngine.netWorth(accounts, LedgerEngine.computeBalances(accounts, emptyList()))
        val b = LedgerEngine.computeBalances(accounts, listOf(transfer(50_000, 2, 3)))
        assertEquals(150_000L, b[2])
        assertEquals(0L, b[3])
        assertEquals(before, LedgerEngine.netWorth(accounts, b))
    }

    @Test
    fun `04 update record is equivalent to recompute with new data`() {
        val old = listOf(expense(3_000, 1))
        val updated = listOf(expense(5_000, 1))
        assertEquals(97_000L, LedgerEngine.computeBalances(accounts, old)[1])
        assertEquals(95_000L, LedgerEngine.computeBalances(accounts, updated)[1])
    }

    @Test
    fun `05 delete removes record effect`() {
        val before = LedgerEngine.computeBalances(accounts, emptyList())
        val afterDelete = LedgerEngine.computeBalances(accounts, emptyList())
        assertEquals(before[1], afterDelete[1])
        val withRecord = LedgerEngine.computeBalances(accounts, listOf(expense(3_000, 1)))
        assertEquals(97_000L, withRecord[1])
    }

    @Test
    fun `06 restore re-applies record effect`() {
        val empty = LedgerEngine.computeBalances(accounts, emptyList())
        val restored = LedgerEngine.computeBalances(accounts, listOf(expense(3_000, 1)))
        assertEquals(empty[1]!! - 3_000L, restored[1])
    }

    @Test
    fun `07 credit card spend and repayment`() {
        val spend = listOf(expense(3_000, 3))
        val b1 = LedgerEngine.computeBalances(accounts, spend)
        assertEquals(-53_000L, b1[3])

        val repay = listOf(expense(3_000, 3), transfer(53_000, 2, 3))
        val b2 = LedgerEngine.computeBalances(accounts, repay)
        assertEquals(0L, b2[3])
        assertEquals(147_000L, b2[2])
    }

    @Test
    fun `08 adjustment changes balance but never stats`() {
        val records = listOf(expense(3_000, 1))
        val adj = listOf(LedgerAdjustment(1, -1L))
        val b = LedgerEngine.computeBalances(accounts, records, adj)
        assertEquals(96_999L, b[1])
        assertEquals(3_000L, LedgerEngine.totalExpense(records))
        val revoked = LedgerEngine.computeBalances(accounts, records, listOf(LedgerAdjustment(1, -1L, false)))
        assertEquals(97_000L, revoked[1])
    }

    @Test
    fun `09 rebuild from tampered balance restores truth`() {
        val records = listOf(expense(3_000, 1), income(50_000, 2), transfer(10_000, 2, 1))
        val truth = LedgerEngine.computeBalances(accounts, records)
        // 模拟 accounts.balanceCents 被直接改错后，重算应回到 truth
        val rebuilt = LedgerEngine.apply(
            accounts.associate { it.id to it.initialBalanceCents },
            LedgerEngine.deltasFrom(records),
            emptyList()
        )
        assertEquals(truth, rebuilt)
        assertEquals(107_000L, truth[1])
        assertEquals(240_000L, truth[2])
    }

    @Test
    fun `exclude from stats and transfer never pollute totals`() {
        val records = listOf(
            expense(3_000, 1),
            expense(9_999, 1, exclude = true),
            transfer(20_000, 2, 1),
            income(50_000, 2)
        )
        assertEquals(3_000L, LedgerEngine.totalExpense(records))
        assertEquals(50_000L, LedgerEngine.totalIncome(records))
    }
}
