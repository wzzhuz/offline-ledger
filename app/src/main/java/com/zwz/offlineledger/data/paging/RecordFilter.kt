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
    val accountDisplayName: String? = null,
    /**
     * 第 41 批：true = 时间范围留空时不按月份夹击，列表跨月查看。
     * 这是「视图模式」而非筛选条件，MUST NOT 计入 activeCount()。
     */
    val allMonths: Boolean = false
) {
    /**
     * 第 41 批：返回 null 交给 SQL 的 `:fromDate IS NULL` 短路，
     * MUST NOT 再把「当前查看月份」隐式注入成筛选条件。
     * - 显式范围任一端填了 → 另一端不设限（跨月，不与月份双重夹击）
     * - 全空 + allMonths → 全部月份
     * - 全空 + 按月视图 → 当前查看月份
     */
    fun effectiveFromDate(): Int? = fromDate
        ?: if (allMonths || toDate != null) null else MonthRange.of(yearMonth).first
    fun effectiveToDate(): Int? = toDate
        ?: if (allMonths || fromDate != null) null else MonthRange.of(yearMonth).second

    /** 是否跨月模式，用于 UI 明示「月份条只作定位」。 */
    fun isCrossMonth(): Boolean = allMonths || fromDate != null || toDate != null

    /** 第 41 批：时间语义单一出口，chip 与月份条共用，不允许各处自己拼。 */
    fun timeSummary(): String = when {
        fromDate != null || toDate != null ->
            "时间：" + (fromDate?.let { MonthRange.dateLabel(it) } ?: "不限") +
                " ~ " + (toDate?.let { MonthRange.dateLabel(it) } ?: "不限")
        allMonths -> "时间：全部月份 · 当前显示 " + MonthRange.label(yearMonth)
        else -> "时间：当前月 " + MonthRange.label(yearMonth) + "（切月看其他月）"
    }

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
