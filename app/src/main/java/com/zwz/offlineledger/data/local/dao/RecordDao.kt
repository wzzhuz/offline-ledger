package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zwz.offlineledger.data.local.entity.RecordEntity

data class AccountDeltaRow(val accountId: Long, val deltaCents: Long)
data class CategoryTotalRow(val categoryId: Long, val totalCents: Long)
data class MonthTotalRow(val yearMonth: Int, val type: String, val totalCents: Long)


/** 明细行：一次 LEFT JOIN 取回分类与账户名，避免列表渲染时逐行查库（O(n²)）。 */
data class RecordRow(
    val id: Long,
    val type: String,
    val amountCents: Long,
    val categoryId: Long?,
    val rootCategoryId: Long?,
    val fromAccountId: Long?,
    val toAccountId: Long?,
    val timestamp: Long,
    val localDate: Int,
    val yearMonth: Int,
    val note: String,
    val excludeFromStats: Boolean,
    val categoryName: String?,
    val categoryColorHex: String?,
    val fromAccountName: String?,
    val toAccountName: String?
)

/** 日期头用的整月按日合计，最多 31 行，不随分页加载跳变。 */
data class DailyTotalRow(
    val localDate: Int,
    val expenseCents: Long,
    val incomeCents: Long
)

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

    /**
     * 一条 SQL 覆盖时间 / 账户 / 类型 / 一级 / 二级分类 / 金额区间 / 备注全文
     * 的全部组合。可选参数为 null 时短路，全部值通过参数绑定，无字符串拼接。
     */
    @Query(
        """
        SELECT r.id AS id, r.type AS type, r.amountCents AS amountCents,
               r.categoryId AS categoryId, r.rootCategoryId AS rootCategoryId,
               r.fromAccountId AS fromAccountId, r.toAccountId AS toAccountId,
               r.timestamp AS timestamp, r.localDate AS localDate,
               r.yearMonth AS yearMonth, r.note AS note,
               r.excludeFromStats AS excludeFromStats,
               c.name AS categoryName, c.colorHex AS categoryColorHex,
               fa.name AS fromAccountName, ta.name AS toAccountName
          FROM records r
          LEFT JOIN categories c ON r.categoryId = c.id
          LEFT JOIN accounts fa ON r.fromAccountId = fa.id
          LEFT JOIN accounts ta ON r.toAccountId = ta.id
         WHERE (:fromDate IS NULL OR r.localDate >= :fromDate)
           AND (:toDate IS NULL OR r.localDate <= :toDate)
           AND (:accountId IS NULL OR r.fromAccountId = :accountId
                                OR r.toAccountId = :accountId)
           AND (:type IS NULL OR r.type = :type)
           AND (:rootCategoryId IS NULL OR r.rootCategoryId = :rootCategoryId)
           AND (:categoryId IS NULL OR r.categoryId = :categoryId)
           AND (:minAmount IS NULL OR r.amountCents >= :minAmount)
           AND (:maxAmount IS NULL OR r.amountCents <= :maxAmount)
           AND (:noteQuery IS NULL OR r.note LIKE '%' || :noteQuery || '%')
         ORDER BY r.localDate DESC, r.id DESC
         LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pagedFiltered(
        fromDate: Int?, toDate: Int?, accountId: Long?, type: String?,
        rootCategoryId: Long?, categoryId: Long?, minAmount: Long?,
        maxAmount: Long?, noteQuery: String?, limit: Int, offset: Int
    ): List<RecordRow>

    /** 日期头真实值：整月按日聚合，不用已加载分页数据求和。 */
    @Query(
        """
        SELECT localDate,
               SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END) AS expenseCents,
               SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END) AS incomeCents
          FROM records
         WHERE yearMonth = :ym AND excludeFromStats = 0
           AND type IN ('EXPENSE', 'INCOME')
         GROUP BY localDate
         ORDER BY localDate DESC
        """
    )
    suspend fun dailyTotals(ym: Int): List<DailyTotalRow>
}
