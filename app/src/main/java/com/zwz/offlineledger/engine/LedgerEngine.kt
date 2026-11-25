package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.LedgerAccount
import com.zwz.offlineledger.data.model.LedgerAdjustment
import com.zwz.offlineledger.data.model.LedgerRecord
import com.zwz.offlineledger.data.model.RecordType

/**
 * 账本唯一余额算法（纯 JVM）。
 *
 * 真相定义：balance = initialBalance + 流水净变动 + 未撤销的校准 delta
 * 流水净变动：fromAccountId -> -amount，toAccountId -> +amount
 * 不按 EXPENSE/INCOME/TRANSFER 分支，也不按 ASSET/LIABILITY 分支。
 *
 * 生产路径由 RecordDao.aggregateAccountDeltas() 的 SQL 做同样的聚合，
 * 再调用 apply() 写回；本文件的 deltasFrom 是它的纯 JVM 等价实现。
 */
object LedgerEngine {

    fun deltasFrom(records: List<LedgerRecord>): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        for (r in records) {
            // 第 67 批：TRANSFER 的转出侧额外扣 fee（负 fee = 转账优惠，少扣）。
            // 与 RecordDao.aggregateAccountDeltas 的 SQL 严格等价，MUST 同步改两处。
            val fee = if (r.type == RecordType.TRANSFER) (r.feeCents ?: 0L) else 0L
            r.fromAccountId?.let { result[it] = (result[it] ?: 0L) - r.amountCents - fee }
            r.toAccountId?.let { result[it] = (result[it] ?: 0L) + r.amountCents }
        }
        return result
    }

    /** 生产与单测共用的唯一余额写入逻辑。 */
    fun apply(
        initial: Map<Long, Long>,
        deltas: Map<Long, Long>,
        adjustments: List<LedgerAdjustment>
    ): Map<Long, Long> {
        val result = initial.toMutableMap()
        for ((id, delta) in deltas) {
            result[id] = (result[id] ?: 0L) + delta
        }
        for (a in adjustments) {
            if (a.active) {
                result[a.accountId] = (result[a.accountId] ?: 0L) + a.deltaCents
            }
        }
        return result
    }

    fun computeBalances(
        accounts: List<LedgerAccount>,
        records: List<LedgerRecord>,
        adjustments: List<LedgerAdjustment> = emptyList()
    ): Map<Long, Long> = apply(
        accounts.associate { it.id to it.initialBalanceCents },
        deltasFrom(records),
        adjustments
    )

    /** 净资产 = SUM(balance WHERE includeInTotal)，绝不用「总资产 − 总负债」。 */
    fun netWorth(accounts: List<LedgerAccount>, balances: Map<Long, Long>): Long =
        accounts.filter { it.includeInTotal }.sumOf { balances[it.id] ?: 0L }

    fun isStatRelevant(r: LedgerRecord): Boolean =
        (r.type == RecordType.EXPENSE || r.type == RecordType.INCOME) && !r.excludeFromStats

    fun totalExpense(records: List<LedgerRecord>): Long = records
        .filter { isStatRelevant(it) && it.type == RecordType.EXPENSE }
        .sumOf { it.amountCents }

    fun totalIncome(records: List<LedgerRecord>): Long = records
        .filter { isStatRelevant(it) && it.type == RecordType.INCOME }
        .sumOf { it.amountCents }
}
