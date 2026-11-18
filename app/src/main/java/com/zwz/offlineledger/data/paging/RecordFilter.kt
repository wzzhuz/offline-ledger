package com.zwz.offlineledger.data.paging

import com.zwz.offlineledger.core.time.MonthRange

/**
 * 明细与组合筛选的唯一条件对象。
 *
 * revision 是刷新信号：写操作成功后 +1，让 flatMapLatest 重建 Pager，
 * 不必持有 PagingSource 引用去手工 invalidate()。
 */
data class RecordFilter(
    val yearMonth: Int,
    val revision: Int = 0,
    val fromDate: Int? = null,
    val toDate: Int? = null,
    val accountId: Long? = null,
    val type: String? = null,
    val rootCategoryId: Long? = null,
    val categoryId: Long? = null,
    val minAmountCents: Long? = null,
    val maxAmountCents: Long? = null,
    val noteQuery: String? = null,
    /** 阶段 6c-2：null = 全部币种；选中后列表与日合计只统计该币种。 */
    val currencyCode: String? = null,
    /** 第 11 批：交易对方模糊匹配；null = 不过滤。 */
    val counterpartyQuery: String? = null,
    /**
     * 第 28 批：从统计页下钻带入的三条「只读展示名」，按维度各自独立。
     * 第 27 批用单条 filterDisplayName，被分类行 / 对方框 / 账户行共用，
     * 按收款方跳转时分类行会错误显示收款方名（串味）。
     * 只用于明细页筛选 chip 与筛选面板显示具体值，MUST NOT 参与 SQL，
     * MUST NOT 计入 activeCount()（它们不是独立筛选条件）。
     */
    val categoryDisplayName: String? = null,
    val counterpartyDisplayName: String? = null,
    val accountDisplayName: String? = null
) {
    fun effectiveFromDate(): Int = fromDate ?: MonthRange.of(yearMonth).first
    fun effectiveToDate(): Int = toDate ?: MonthRange.of(yearMonth).second

    fun activeCount(): Int = listOf(
        fromDate, toDate, accountId, type, rootCategoryId,
        categoryId, minAmountCents, maxAmountCents, noteQuery, currencyCode,
        counterpartyQuery
    ).count { it != null }

    fun bumped(): RecordFilter = copy(revision = revision + 1)

    companion object {
        fun currentMonth(): RecordFilter = RecordFilter(MonthRange.currentYearMonth())

        /**
         * 第 28 批：三个跨 Tab 下钻入口 MUST 从全新 RecordFilter 起手、
         * 只写本维度字段与对应的展示名，MUST NOT 沿用上一份条件快照。
         * 抽成纯 JVM 工厂是为了让互斥性可直接单测（ViewModel 依赖 Android）。
         */
        fun forCategory(
            yearMonth: Int,
            revision: Int,
            rootCategoryId: Long?,
            categoryId: Long?,
            currencyCode: String?,
            categoryDisplayName: String?
        ): RecordFilter = RecordFilter(
            yearMonth = yearMonth,
            revision = revision,
            rootCategoryId = rootCategoryId,
            categoryId = categoryId,
            currencyCode = currencyCode,
            categoryDisplayName = categoryDisplayName
        )

        fun forCounterparty(
            yearMonth: Int,
            revision: Int,
            counterpartyQuery: String,
            currencyCode: String?,
            counterpartyDisplayName: String?
        ): RecordFilter = RecordFilter(
            yearMonth = yearMonth,
            revision = revision,
            counterpartyQuery = counterpartyQuery,
            currencyCode = currencyCode,
            counterpartyDisplayName = counterpartyDisplayName
        )

        fun forAccount(
            yearMonth: Int,
            revision: Int,
            accountId: Long,
            currencyCode: String?,
            accountDisplayName: String?
        ): RecordFilter = RecordFilter(
            yearMonth = yearMonth,
            revision = revision,
            accountId = accountId,
            currencyCode = currencyCode,
            accountDisplayName = accountDisplayName
        )
    }
}
