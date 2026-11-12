package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zwz.offlineledger.data.local.entity.RecordEntity

data class AccountDeltaRow(val accountId: Long, val deltaCents: Long)
data class CategoryTotalRow(val categoryId: Long, val totalCents: Long)
data class MonthTotalRow(val yearMonth: Int, val type: String, val totalCents: Long)

@Dao
interface RecordDao {
    @Insert suspend fun insert(r: RecordEntity): Long
    @Update suspend fun update(r: RecordEntity)

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun byId(id: Long): RecordEntity?

    /** 备份导出用；日常明细分页走范围查询（阶段 2接入 Paging 3）。 */
    @Query("SELECT * FROM records ORDER BY localDate DESC, id DESC")
    suspend fun all(): List<RecordEntity>

    @Query("SELECT * FROM records WHERE localDate BETWEEN :from AND :to ORDER BY localDate DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun pageByDate(from: Int, to: Int, limit: Int, offset: Int): List<RecordEntity>

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM records")
    suspend fun clear()

    /**
     * 余额重算的声明式聚合：把 N 条流水压成每账户一行。
     * 与 LedgerEngine.deltasFrom 等价：from 减、to 加，不按 type/账户类型分支。
     */
    @Query(
        """
        SELECT accountId, SUM(deltaCents) AS deltaCents FROM (
            SELECT fromAccountId AS accountId, -amountCents AS deltaCents
              FROM records WHERE fromAccountId IS NOT NULL
            UNION ALL
            SELECT toAccountId AS accountId, amountCents AS deltaCents
              FROM records WHERE toAccountId IS NOT NULL
        ) AS d GROUP BY accountId
        """
    )
    suspend fun aggregateAccountDeltas(): List<AccountDeltaRow>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth = :ym")
    suspend fun totalExpense(ym: Int): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM records WHERE type = 'INCOME' AND excludeFromStats = 0 AND yearMonth = :ym")
    suspend fun totalIncome(ym: Int): Long

    /** 按一级分类：GROUP BY rootCategoryId。 */
    @Query("SELECT rootCategoryId AS categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth = :ym GROUP BY rootCategoryId ORDER BY totalCents DESC")
    suspend fun expenseByRoot(ym: Int): List<CategoryTotalRow>

    /** 按二级分类：GROUP BY categoryId。 */
    @Query("SELECT categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth = :ym GROUP BY categoryId ORDER BY totalCents DESC")
    suspend fun expenseByCategory(ym: Int): List<CategoryTotalRow>

    @Query("SELECT yearMonth, type, SUM(amountCents) AS totalCents FROM records WHERE excludeFromStats = 0 AND type IN ('EXPENSE', 'INCOME') AND yearMonth BETWEEN :from AND :to GROUP BY yearMonth, type ORDER BY yearMonth")
    suspend fun monthlyTrend(from: Int, to: Int): List<MonthTotalRow>

    @Query("SELECT * FROM records WHERE amountCents = :amountCents AND fromAccountId = :accountId ORDER BY timestamp DESC")
    suspend fun findByAmount(accountId: Long, amountCents: Long): List<RecordEntity>
}
