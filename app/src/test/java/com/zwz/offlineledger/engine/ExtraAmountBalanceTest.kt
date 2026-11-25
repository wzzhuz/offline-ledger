package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.LedgerAccount
import com.zwz.offlineledger.data.model.LedgerRecord
import com.zwz.offlineledger.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 第 67 批：余额双实现的纯 JVM 侧。
 * RecordDao.aggregateAccountDeltas 的 SQL 由 scripts/check_extra_amount.py
 * 静态断言「同样只在 TRANSFER 的 from 侧加 fee」，两者 MUST 同步。
 */
class ExtraAmountBalanceTest {

    private val a = LedgerAccount(1L, 200_000L)
    private val b = LedgerAccount(2L, 0L)
    private val accounts = listOf(a, b)

    private fun transfer(amount: Long, fee: Long?) = LedgerRecord(
        id = 1L, type = RecordType.TRANSFER, amountCents = amount,
        fromAccountId = 1L, toAccountId = 2L, feeCents = fee
    )

    @Test
    fun `01 无手续费时转账净资产不变`() {
        val d = LedgerEngine.deltasFrom(listOf(transfer(100_000L, null)))
        assertEquals(-100_000L, d[1L])
        assertEquals(100_000L, d[2L])
    }

    @Test
    fun `02 手续费只扣转出侧 净资产减少手续费`() {
        val d = LedgerEngine.deltasFrom(listOf(transfer(100_000L, 500L)))
        assertEquals(-100_500L, d[1L])
        assertEquals(100_000L, d[2L])
        val balances = LedgerEngine.computeBalances(accounts, listOf(transfer(100_000L, 500L)))
        assertEquals(99_500L, balances[1L])
        assertEquals(100_000L, balances[2L])
        assertEquals(199_500L, LedgerEngine.netWorth(accounts, balances))
    }

    @Test
    fun `03 负 fee 是转账优惠 少扣转出侧`() {
        val d = LedgerEngine.deltasFrom(listOf(transfer(100_000L, -300L)))
        assertEquals(-99_700L, d[1L])
        assertEquals(100_000L, d[2L])
    }

    @Test
    fun `04 非 TRANSFER 的 fee 不参与余额`() {
        val expense = LedgerRecord(
            id = 2L, type = RecordType.EXPENSE, amountCents = 3_000L,
            fromAccountId = 1L, toAccountId = null, feeCents = 999L
        )
        val d = LedgerEngine.deltasFrom(listOf(expense))
        assertEquals(-3_000L, d[1L])
    }

    @Test
    fun `05 优惠支出按实付扣款 原价不进余额`() {
        // LedgerRecord 是纯 JVM 余额模型，只带 feeCents；优惠不参与余额，
        // 因此这里没有 discountCents 参数（MUST NOT 给余额模型加无意义字段）。
        val expense = LedgerRecord(
            id = 3L, type = RecordType.EXPENSE, amountCents = 47_001L,
            fromAccountId = 1L, toAccountId = null
        )
        val d = LedgerEngine.deltasFrom(listOf(expense))
        assertEquals(-47_001L, d[1L])
    }
}
