package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zwz.offlineledger.data.local.entity.RecordEntity

data class AccountDeltaRow(val accountId: Long, val deltaCents: Long)
data class CategoryTotalRow(val categoryId: Long, val totalCents: Long)
data class MonthTotalRow(val yearMonth: Int, val type: String, val totalCents: Long)

/** 阶段 8：年度资产趋势的月度净变动行。 */
data class MonthDeltaRow(val yearMonth: Int, val totalCents: Long)

/** 第 13 批：洞察只读聚合行；全部由 GROUP BY 返回，不加载流水实体。 */
data class InsightNameRow(val name: String, val totalCents: Long, val cnt: Int)
data class InsightIdRow(val id: Long, val totalCents: Long, val cnt: Int)
data class InsightCountRow(val categoryId: Long, val cnt: Int)

/** 阶段 8：账户最近使用频次，用于记账页常用账户置顶。 */
data class AccountUsageRow(val id: Long, val uses: Int)


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
    val toAccountName: String?,
    val counterparty: String = "",
    val importSource: String = "",
    val sourceId: String = "",
    /** 第 43 批：列表主标题在有描述时用它；缺列时按空串兼容。 */
    val description: String = "",
    /** 第 62 批：DAO 只带出原值，组合一律交给 `accountLabel()`。 */
    val fromAccountLast4: String? = null,
    val toAccountLast4: String? = null,
    /** 第 67 批：附加金额；明细行画划线原价与「优惠 X」标签用。 */
    val discountCents: Long? = null,
    val feeCents: Long? = null
)

/** 账户详情专属流水行。 */
data class AccountRecordRow(
    val id: Long,
    val type: String,
    val amountCents: Long,
    val localDate: Int,
    val note: String,
    val categoryName: String?,
    val categoryColorHex: String?
)

/** 统计页下钻明细行：带唯一统计口径，LIMIT 由 SQL 限定。 */
data class DrillRow(
    val id: Long,
    val type: String,
    val amountCents: Long,
    val localDate: Int,
    val note: String,
    val categoryName: String?
)

/**
 * 核对视图行。suspectFlags 是 SQL CASE 算出的位掩码：
 * 1=金额 0 / 2=未分类 / 4=异常大额；第 4 位「短时重复」在 ViewModel 补算。
 */
data class ReviewRow(
    val id: Long,
    val type: String,
    val amountCents: Long,
    val localDate: Int,
    val timestamp: Long,
    val note: String,
    val categoryName: String?,
    val fromAccountName: String?,
    val toAccountName: String?,
    val categoryId: Long?,
    val suspectFlags: Int,
    /** 第 62 批：同上，投影只带原值。 */
    val fromAccountLast4: String? = null,
    val toAccountLast4: String? = null
)

/** 日期头用的整月按日合计，最多 31 行，不随分页加载跳变。 */
data class MonthCountRow(val ym: Int, val cnt: Int)

data class DailyTotalRow(
    val localDate: Int,
    val expenseCents: Long,
    val incomeCents: Long
)

/** 阶段 6c-2：CSV 导出的 SQL 源行，名称由 JOIN 取出，不 inflate Entity。 */
data class CsvSourceRow(
    val type: String,
    val amountCents: Long,
    val localDate: Int,
    val note: String,
    val currencyCode: String,
    val accountName: String,
    val rootName: String,
    val categoryName: String,
    val counterparty: String = "",
    val importSource: String = "",
    val sourceId: String = "",
    /** 第 43 批：列表主标题在有描述时用它；缺列时按空串兼容。 */
    val description: String = "",
    /** 第 63 批：账户卡号后四位，导出复合匹配键的第二列。 */
    val accountCardLast4: String? = null,
    /** 第 67 批：CSV 导出附加金额两列。 */
    val discountCents: Long? = null,
    val feeCents: Long? = null
)

/** 第 23 批：账单导入前的疑似匹配候选（金额 + 账户 + 时间窗）。 */
data class ImportCandidateRow(
    val id: Long,
    val localDate: Int,
    val amountCents: Long,
    val note: String
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
            SELECT fromAccountId AS accountId,
                   -(amountCents + COALESCE(
                       CASE WHEN type = 'TRANSFER' THEN feeCents ELSE 0 END, 0
                   )) AS deltaCents
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
               COALESCE(r.counterparty, '') AS counterparty,
               COALESCE(r.importSource, '') AS importSource,
               COALESCE(r.sourceId, '') AS sourceId,
               COALESCE(r.description, '') AS description,
               r.discountCents AS discountCents, r.feeCents AS feeCents,
               c.name AS categoryName, c.colorHex AS categoryColorHex,
               fa.name AS fromAccountName, ta.name AS toAccountName,
               fa.cardLast4 AS fromAccountLast4, ta.cardLast4 AS toAccountLast4
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
           AND (:currencyCode IS NULL OR r.currencyCode = :currencyCode)
           AND (:counterpartyQuery IS NULL
                OR COALESCE(r.counterparty, '') LIKE '%' || :counterpartyQuery || '%')
           AND (:descriptionQuery IS NULL
                OR COALESCE(r.description, '') LIKE '%' || :descriptionQuery || '%')
         ORDER BY r.localDate DESC, r.id DESC
         LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pagedFiltered(
        fromDate: Int?, toDate: Int?, accountId: Long?, type: String?,
        rootCategoryId: Long?, categoryId: Long?, minAmount: Long?,
        maxAmount: Long?, noteQuery: String?, currencyCode: String?,
        counterpartyQuery: String?, descriptionQuery: String?,
        limit: Int, offset: Int
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
           AND (:currencyCode IS NULL OR currencyCode = :currencyCode)
         GROUP BY localDate
         ORDER BY localDate DESC
        """
    )
    suspend fun dailyTotals(ym: Int, currencyCode: String?): List<DailyTotalRow>

    /**
     * 第 41 批：一条 GROUP BY 取某年 12 个月的「列表命中数」角标。
     * 刻意不带 excludeFromStats：角标口径与 pagedFiltered 一致，
     * 不是统计口径，MUST NOT 复用 StatRules.SQL_STAT_RELEVANT。
     * 名字不以任何 check_stat_rules marker 为前缀，避免回溯到错误的 @Query。
     */
    @Query(
        """
        SELECT yearMonth AS ym, COUNT(*) AS cnt
          FROM records
         WHERE yearMonth BETWEEN :fromYm AND :toYm
           AND (:currencyCode IS NULL OR currencyCode = :currencyCode)
         GROUP BY yearMonth
         ORDER BY yearMonth
        """
    )
    suspend fun monthCounts(fromYm: Int, toYm: Int, currencyCode: String?): List<MonthCountRow>


    /**
     * 阶段 4：点一级扇区后展开该大类下的二级构成。
     * 用 yearMonth 范围而非单月：月度视图传 from=to=ym，年度视图传 1..12 月。
     * 名字刻意不以 expenseByCategory 开头，避免 check_stat_rules.py
     * 用 find('fun ' + marker) 回溯 @Query 时指向错误的方法。
     */
    @Query(
        """
        SELECT categoryId, SUM(amountCents) AS totalCents
          FROM records
         WHERE type = 'EXPENSE' AND excludeFromStats = 0
           AND yearMonth BETWEEN :fromYm AND :toYm
           AND rootCategoryId = :rootId
         GROUP BY categoryId
         ORDER BY totalCents DESC
        """
    )
    suspend fun subcategoryTotalsUnderRoot(
        fromYm: Int, toYm: Int, rootId: Long
    ): List<CategoryTotalRow>

    /** 账户详情流水：LEFT JOIN 一次取分类名，LIMIT 兜底。 */
    @Query(
        """
        SELECT r.id AS id, r.type AS type, r.amountCents AS amountCents,
               r.localDate AS localDate, r.note AS note,
               c.name AS categoryName, c.colorHex AS categoryColorHex
          FROM records r
          LEFT JOIN categories c ON r.categoryId = c.id
         WHERE (r.fromAccountId = :accountId OR r.toAccountId = :accountId)
         ORDER BY r.localDate DESC, r.id DESC
         LIMIT :limit
        """
    )
    suspend fun accountRecords(accountId: Long, limit: Int): List<AccountRecordRow>

    /**
     * 核对视图基础集：范围由 SQL 限定（账户 + 锚点后 + 可选金额区间 + LIMIT），
     * MUST NOT 全表加载后在 Kotlin 过滤。三个疑似标记由 CASE 算出。
     */
    @Query(
        """
        SELECT r.id AS id, r.type AS type, r.amountCents AS amountCents,
               r.localDate AS localDate, r.timestamp AS timestamp, r.note AS note,
               c.name AS categoryName,
               fa.name AS fromAccountName, ta.name AS toAccountName,
               fa.cardLast4 AS fromAccountLast4, ta.cardLast4 AS toAccountLast4,
               r.categoryId AS categoryId,
                 (CASE WHEN r.amountCents = 0 THEN 1 ELSE 0 END)
               + (CASE WHEN r.categoryId IS NULL AND r.type <> 'TRANSFER' THEN 2 ELSE 0 END)
               + (CASE WHEN r.amountCents >= :largeThreshold THEN 4 ELSE 0 END) AS suspectFlags
          FROM records r
          LEFT JOIN categories c ON r.categoryId = c.id
          LEFT JOIN accounts fa ON r.fromAccountId = fa.id
          LEFT JOIN accounts ta ON r.toAccountId = ta.id
         WHERE (r.fromAccountId = :accountId OR r.toAccountId = :accountId)
           AND r.timestamp >= :fromTimestamp
           AND (:minAmount IS NULL OR r.amountCents >= :minAmount)
           AND (:maxAmount IS NULL OR r.amountCents <= :maxAmount)
         ORDER BY r.timestamp DESC
         LIMIT :limit
        """
    )
    suspend fun reviewRows(
        accountId: Long,
        fromTimestamp: Long,
        minAmount: Long?,
        maxAmount: Long?,
        largeThreshold: Long,
        limit: Int
    ): List<ReviewRow>

    /** 供「未找到 0.01 的记录」判断；同样按锚点后范围。 */
    @Query(
        """
        SELECT COUNT(*) FROM records
         WHERE (fromAccountId = :accountId OR toAccountId = :accountId)
           AND amountCents = :amountCents
           AND timestamp >= :fromTimestamp
        """
    )
    suspend fun countExactAmount(accountId: Long, amountCents: Long, fromTimestamp: Long): Int

    /** 年度视图按一级分类聚合；与月度 expenseByRoot 口径完全一致。 */
    @Query(
        """
        SELECT rootCategoryId AS categoryId, SUM(amountCents) AS totalCents
          FROM records
         WHERE type = 'EXPENSE' AND excludeFromStats = 0
           AND yearMonth BETWEEN :fromYm AND :toYm
         GROUP BY rootCategoryId
         ORDER BY totalCents DESC
        """
    )
    suspend fun rootTotalsInRange(fromYm: Int, toYm: Int): List<CategoryTotalRow>

    /** 年度视图按二级分类聚合。 */
    @Query(
        """
        SELECT categoryId, SUM(amountCents) AS totalCents
          FROM records
         WHERE type = 'EXPENSE' AND excludeFromStats = 0
           AND yearMonth BETWEEN :fromYm AND :toYm
         GROUP BY categoryId
         ORDER BY totalCents DESC
        """
    )
    suspend fun categoryTotalsInRange(fromYm: Int, toYm: Int): List<CategoryTotalRow>

    /**
     * 下钻明细：不走 pagedFiltered，因为后者不带 excludeFromStats 口径，
     * 而统计页进来的明细 MUST NOT 显示「不计入收支」的流水。
     * LIMIT 由 SQL 限定，MUST NOT 全量加载该分类流水。
     */
    @Query(
        """
        SELECT r.id AS id, r.type AS type, r.amountCents AS amountCents,
               r.localDate AS localDate, r.note AS note, c.name AS categoryName
          FROM records r
          LEFT JOIN categories c ON r.categoryId = c.id
         WHERE r.categoryId = :categoryId
           AND r.yearMonth BETWEEN :fromYm AND :toYm
           AND r.type IN ('EXPENSE', 'INCOME') AND r.excludeFromStats = 0
         ORDER BY r.localDate DESC, r.id DESC
         LIMIT :limit
        """
    )
    suspend fun drillDownRows(
        fromYm: Int, toYm: Int, categoryId: Long, limit: Int
    ): List<DrillRow>

    // ---------------------------------------------------------------
    // 阶段 6b：SETTLEMENT 聚合。既有 6 条收支聚合 SQL 一行未动，
    // 净支出在 ChartData 展示层做减法。三条查询都带唯一统计口径，
    // 已纳入 check_stat_rules.py 的 marker 清单。
    // ---------------------------------------------------------------

    @Query("SELECT rootCategoryId AS categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'SETTLEMENT' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm GROUP BY rootCategoryId")
    suspend fun settlementByRootInRange(fromYm: Int, toYm: Int): List<CategoryTotalRow>

    @Query("SELECT categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'SETTLEMENT' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm GROUP BY categoryId")
    suspend fun settlementByCategoryInRange(fromYm: Int, toYm: Int): List<CategoryTotalRow>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM records WHERE type = 'SETTLEMENT' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm")
    suspend fun settlementTotalInRange(fromYm: Int, toYm: Int): Long

    /** 累计已报销：recordSettlement 校验「累计 + 本次 <= 原支出」用。 */
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM records WHERE type = 'SETTLEMENT' AND linkedRecordId = :originalId")
    suspend fun settlementTotalFor(originalId: Long): Long

    // ---------------------------------------------------------------
    // 阶段 6c-1：按币种统计的独立查询组。
    // 既有 12 条聚合 SQL 一行未动（避免误伤 aggregateAccountDeltas 与余额重算）；
    // 每条都带唯一统计口径 excludeFromStats = 0 与 currencyCode 条件。
    // 已纳入 scripts/check_stat_rules.py 的 marker 清单。
    // ---------------------------------------------------------------

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth = :ym AND currencyCode = :currencyCode")
    suspend fun totalExpenseForCurrency(ym: Int, currencyCode: String): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM records WHERE type = 'INCOME' AND excludeFromStats = 0 AND yearMonth = :ym AND currencyCode = :currencyCode")
    suspend fun totalIncomeForCurrency(ym: Int, currencyCode: String): Long

    @Query("SELECT rootCategoryId AS categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth = :ym AND currencyCode = :currencyCode GROUP BY rootCategoryId ORDER BY totalCents DESC")
    suspend fun expenseByRootForCurrency(ym: Int, currencyCode: String): List<CategoryTotalRow>

    @Query("SELECT categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth = :ym AND currencyCode = :currencyCode GROUP BY categoryId ORDER BY totalCents DESC")
    suspend fun expenseByCategoryForCurrency(ym: Int, currencyCode: String): List<CategoryTotalRow>

    @Query("SELECT yearMonth, type, SUM(amountCents) AS totalCents FROM records WHERE type IN ('EXPENSE','INCOME') AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :currencyCode GROUP BY yearMonth, type ORDER BY yearMonth")
    suspend fun monthlyTrendForCurrency(fromYm: Int, toYm: Int, currencyCode: String): List<MonthTotalRow>

    @Query("SELECT localDate, SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END) AS expenseCents, SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END) AS incomeCents FROM records WHERE type IN ('EXPENSE','INCOME') AND excludeFromStats = 0 AND yearMonth = :ym AND currencyCode = :currencyCode GROUP BY localDate ORDER BY localDate")
    suspend fun dailyTotalsForCurrency(ym: Int, currencyCode: String): List<DailyTotalRow>

    @Query("SELECT rootCategoryId AS categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :currencyCode GROUP BY rootCategoryId ORDER BY totalCents DESC")
    suspend fun rootTotalsForCurrency(fromYm: Int, toYm: Int, currencyCode: String): List<CategoryTotalRow>

    @Query("SELECT categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :currencyCode GROUP BY categoryId ORDER BY totalCents DESC")
    suspend fun categoryTotalsForCurrency(fromYm: Int, toYm: Int, currencyCode: String): List<CategoryTotalRow>

    @Query("SELECT categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND rootCategoryId = :rootId AND currencyCode = :currencyCode GROUP BY categoryId ORDER BY totalCents DESC")
    suspend fun subcategoryTotalsUnderRootForCurrency(fromYm: Int, toYm: Int, rootId: Long, currencyCode: String): List<CategoryTotalRow>

    @Query("SELECT r.id AS id, r.type AS type, r.amountCents AS amountCents, r.localDate AS localDate, r.note AS note, c.name AS categoryName, c.colorHex AS categoryColorHex FROM records r LEFT JOIN categories c ON r.categoryId = c.id WHERE r.type IN ('EXPENSE','INCOME') AND r.excludeFromStats = 0 AND r.yearMonth BETWEEN :fromYm AND :toYm AND r.categoryId = :categoryId AND r.currencyCode = :currencyCode ORDER BY r.localDate DESC, r.id DESC LIMIT :limit")
    suspend fun drillDownRowsForCurrency(fromYm: Int, toYm: Int, categoryId: Long, limit: Int, currencyCode: String): List<DrillRow>

    @Query("SELECT rootCategoryId AS categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'SETTLEMENT' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :currencyCode GROUP BY rootCategoryId")
    suspend fun settlementByRootForCurrency(fromYm: Int, toYm: Int, currencyCode: String): List<CategoryTotalRow>

    @Query("SELECT categoryId, SUM(amountCents) AS totalCents FROM records WHERE type = 'SETTLEMENT' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :currencyCode GROUP BY categoryId")
    suspend fun settlementByCategoryForCurrency(fromYm: Int, toYm: Int, currencyCode: String): List<CategoryTotalRow>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM records WHERE type = 'SETTLEMENT' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :currencyCode")
    suspend fun settlementTotalForCurrency(fromYm: Int, toYm: Int, currencyCode: String): Long

    // ---------------------------------------------------------------
    // 阶段 6c-2：CSV 明文导出与账单导入去重。
    // 导出走一条 JOIN SQL 直接拿名称，不把 5 万条 Entity inflate 进内存。
    // ---------------------------------------------------------------
    @Query(
        """
        SELECT r.type AS type, r.amountCents AS amountCents,
               r.localDate AS localDate, COALESCE(r.note, '') AS note,
               r.currencyCode AS currencyCode,
               COALESCE(r.counterparty, '') AS counterparty,
               COALESCE(r.importSource, '') AS importSource,
               COALESCE(r.sourceId, '') AS sourceId,
               COALESCE(r.description, '') AS description,
               COALESCE(a.name, '') AS accountName,
               a.cardLast4 AS accountCardLast4,
               r.discountCents AS discountCents, r.feeCents AS feeCents,
               COALESCE(rc.name, '') AS rootName,
               COALESCE(c.name, '') AS categoryName
          FROM records r
          LEFT JOIN accounts a
            ON a.id = (CASE WHEN r.type = 'EXPENSE' THEN r.fromAccountId
                            ELSE r.toAccountId END)
          LEFT JOIN categories rc ON rc.id = r.rootCategoryId
          LEFT JOIN categories c ON c.id = r.categoryId
         ORDER BY r.localDate DESC, r.id DESC
        """
    )
    suspend fun csvRows(): List<CsvSourceRow>

    /** 导入去重：账户 + 金额 + 日期 + 备注，全部参数绑定，不加新字段。 */
    @Query(
        """
        SELECT COUNT(*) FROM records
         WHERE amountCents = :amountCents
           AND localDate = :localDate
           AND COALESCE(note, '') = :note
           AND (fromAccountId = :accountId OR toAccountId = :accountId)
        """
    )
    suspend fun countDuplicate(
        accountId: Long, amountCents: Long, localDate: Int, note: String
    ): Int

    /** 第 23 批：数据库级导入幂等。复合唯一索引之外的显式查重入口。 */
    @Query("SELECT COUNT(*) FROM records WHERE importSource = :source AND sourceId = :id")
    suspend fun countByImportSource(source: String, id: String): Int

    /**
     * 第 23 批：手工流水 vs 第三方账单的「疑似匹配」只读查询。
     * 只按金额分 + 账户 + localDate 窗口找候选，刻意不比较备注文本：
     * 「盒马」与「南京盒马网络科技有限公司」永远不相等。
     */
    @Query(
        """
        SELECT id, localDate, amountCents, COALESCE(note, '') AS note
          FROM records
         WHERE amountCents = :amountCents
           AND (fromAccountId = :accountId OR toAccountId = :accountId)
           AND localDate BETWEEN :fromDate AND :toDate
           AND type IN ('EXPENSE', 'INCOME')
         ORDER BY localDate DESC
         LIMIT 5
        """
    )
    suspend fun importCandidates(
        accountId: Long, amountCents: Long, fromDate: Int, toDate: Int
    ): List<ImportCandidateRow>

    /** 阶段 7：常用收款方自动补全，纯本地 DISTINCT，最多 20 个。 */
    @Query("SELECT DISTINCT counterparty FROM records WHERE counterparty IS NOT NULL AND counterparty <> '' ORDER BY counterparty ASC LIMIT 20")
    suspend fun distinctCounterparties(): List<String>

    /**
     * 第 11 批：筛选面板交易对方联想，纯本地 DISTINCT、不带 LIMIT。
     * 与 distinctCounterparties / searchCounterparties 分开，避免记一笔的
     * 「最近 20 个」口径被误用到筛选面板的全量匹配上。
     */
    @Query("SELECT DISTINCT counterparty FROM records WHERE counterparty IS NOT NULL AND counterparty <> '' ORDER BY counterparty ASC")
    suspend fun allCounterparties(): List<String>

    @Query("UPDATE records SET currencyCode = :code")
    suspend fun updateAllCurrency(code: String)

    /** 阶段 8：输入联想，按历史交易对方模糊匹配，纯本地 DISTINCT。 */
    @Query("SELECT DISTINCT counterparty FROM records WHERE counterparty IS NOT NULL AND counterparty <> '' AND counterparty LIKE '%' || :q || '%' ORDER BY counterparty ASC LIMIT 10")
    suspend fun searchCounterparties(q: String): List<String>

    /** 第 37 批：规则编辑器备注联想，纯本地 DISTINCT、限量 10 条，不把全历史灌进内存。 */
    @Query("SELECT DISTINCT note FROM records WHERE note IS NOT NULL AND note <> '' AND note LIKE '%' || :q || '%' ORDER BY note ASC LIMIT 10")
    suspend fun searchNotes(q: String): List<String>

    /** 第 43 批：规则编辑器描述联想，纯本地 DISTINCT、限量 10 条。 */
    @Query("SELECT DISTINCT description FROM records WHERE description IS NOT NULL AND description <> '' AND description LIKE '%' || :q || '%' ORDER BY description ASC LIMIT 10")
    suspend fun searchDescriptions(q: String): List<String>

    /** 阶段 8：指定日期之后的账户使用频次，用于记账页常用账户置顶。 */
    @Query("SELECT accountId AS id, COUNT(*) AS uses FROM (SELECT fromAccountId AS accountId FROM records WHERE localDate >= :fromDate AND fromAccountId IS NOT NULL UNION ALL SELECT toAccountId AS accountId FROM records WHERE localDate >= :fromDate AND toAccountId IS NOT NULL) GROUP BY accountId ORDER BY uses DESC")
    suspend fun recentAccountUsage(fromDate: Int): List<AccountUsageRow>

    /** 阶段 8：年度资产趋势的月度净变动，按币种、不混算；转账天然净额为 0。 */
    @Query("SELECT yearMonth, SUM(CASE WHEN type IN ('INCOME','SETTLEMENT') THEN amountCents WHEN type = 'EXPENSE' THEN -amountCents ELSE 0 END) AS totalCents FROM records WHERE yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :cc AND excludeFromStats = 0 GROUP BY yearMonth ORDER BY yearMonth")
    suspend fun monthlyBalanceDeltaForCurrency(fromYm: Int, toYm: Int, cc: String): List<MonthDeltaRow>

    // ---- 第 13 批：洞察聚合。全部带唯一统计口径 + 当前币种，严格不混算。 ----
    @Query("SELECT COUNT(*) FROM records WHERE type IN ('EXPENSE', 'INCOME') AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :cc")
    suspend fun insightRecordCount(fromYm: Int, toYm: Int, cc: String): Long

    @Query("SELECT counterparty AS name, SUM(amountCents) AS totalCents, COUNT(*) AS cnt FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :cc AND counterparty IS NOT NULL AND counterparty <> '' GROUP BY counterparty ORDER BY totalCents DESC LIMIT :limit")
    suspend fun insightTopCounterparties(fromYm: Int, toYm: Int, cc: String, limit: Int): List<InsightNameRow>

    @Query("SELECT fromAccountId AS id, SUM(amountCents) AS totalCents, COUNT(*) AS cnt FROM records WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND yearMonth BETWEEN :fromYm AND :toYm AND currencyCode = :cc AND fromAccountId IS NOT NULL GROUP BY fromAccountId ORDER BY totalCents DESC LIMIT :limit")
    suspend fun insightTopAccounts(fromYm: Int, toYm: Int, cc: String, limit: Int): List<InsightIdRow>

    @Query("SELECT categoryId AS categoryId, COUNT(*) AS cnt FROM records WHERE type IN ('EXPENSE','INCOME') AND excludeFromStats = 0 AND categoryId IS NOT NULL GROUP BY categoryId")
    suspend fun insightCategoryCounts(): List<InsightCountRow>

    @Query("SELECT r.id AS id, r.type AS type, r.amountCents AS amountCents, r.localDate AS localDate, r.note AS note, c.name AS categoryName FROM records r LEFT JOIN categories c ON r.categoryId = c.id WHERE r.type IN ('EXPENSE','INCOME') AND r.excludeFromStats = 0 AND (r.categoryId = :categoryId OR r.rootCategoryId = :categoryId) ORDER BY r.localDate DESC, r.id DESC LIMIT :limit")
    suspend fun insightCategoryDetailRows(categoryId: Long, limit: Int): List<DrillRow>
}
