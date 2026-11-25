package com.zwz.offlineledger.data.paging

import com.zwz.offlineledger.core.time.MonthRange

/**
 * 明细与组合筛选的唯一条件对象。
 *
 * revision 是刷新信号：写操作成功后 +1，让 flatMapLatest 重建 Pager，
 * 不必持有 PagingSource 引用去手工 invalidate()。
 */
/** 第 45 批：无筛选时的默认视图粒度。月 = 只看当前月，年 = 只看当前年。 */
enum class DetailGranularity { MONTH, YEAR }

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
    /** 第 43 批：描述模糊匹配；null = 不过滤。与规则字段目录共用语义。 */
    val descriptionQuery: String? = null,
    /** 第 75 批：按标签名精确筛选；null = 不过滤。走 EXISTS 而非 LIKE。 */
    val tagName: String? = null,
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
     * 第 42 批：默认 true = 时间范围留空即查看全部记录，不再隐式夹击当前月。
     * 这是「视图模式」而非筛选条件，MUST NOT 计入 activeCount()。
     * false 仅在主动选「当前月」快捷段 / 明确切按月视图时置位。
     */
    /** 第 44 批：统计下钻带进明细的周期只读展示名，不进 SQL、不进 activeCount()。 */
    val periodDisplayName: String? = null,
    val allMonths: Boolean = true,
    /** 第 45 批：无筛选时按此粒度收窄；有筛选无范围时 allMonths=true 优先。 */
    val granularity: DetailGranularity = DetailGranularity.MONTH
) {
    /**
     * 第 42 批：返回 null 交给 SQL 的 `:fromDate IS NULL` 短路。
     * - 显式范围任一端填了 → 另一端不设限（跨月，不与月份双重夹击）
     * - 全空（默认 allMonths=true）→ 全部记录，MUST NOT 注入当前月
     * - 全空 + 主动切 allMonths=false → 仅当前查看月份
     */
    /** 第 45 批：无筛选且 allMonths=false 时按 granularity 取闭区间，单一出口。 */
    private fun periodBounds(): Pair<Int, Int> = when (granularity) {
        DetailGranularity.MONTH -> MonthRange.of(yearMonth)
        DetailGranularity.YEAR -> {
            val y = yearMonth / 100
            (y * 10000 + 101) to (y * 10000 + 1231)
        }
    }

    fun effectiveFromDate(): Int? = fromDate
        ?: if (allMonths || toDate != null) null else periodBounds().first
    fun effectiveToDate(): Int? = toDate
        ?: if (allMonths || fromDate != null) null else periodBounds().second

    /**
     * 第 48 批：表头 / 年月选择器 / 左右箭头 / 锚点 / 概览隐藏判断共用的单一周期源。
     * 显式范围（含统计下钻）以范围首日派生年月，否则用 yearMonth；
     * MUST NOT 再从 _overview 另存一份月份，那是「列表 10 月、表头 11 月」的根因。
     */
    fun displayYearMonth(): Int = fromDate?.let { it / 100 } ?: yearMonth

    /** 整年闭区间只读地识别为年粒度；跨年区间仍按 granularity 交代。 */
    fun displayGranularity(): DetailGranularity {
        val f = fromDate
        val t = toDate
        return if (f != null && t != null &&
            f % 10000 == 101 && t % 10000 == 1231 &&
            f / 10000 == t / 10000
        ) DetailGranularity.YEAR else granularity
    }

    /** 第 48 批：顶部标题单一出口。下钻态直接用统计周期只读展示名。 */
    fun displayPeriodLabel(): String = periodDisplayName ?: when (displayGranularity()) {
        DetailGranularity.MONTH -> MonthRange.label(displayYearMonth())
        DetailGranularity.YEAR -> (displayYearMonth() / 100).toString() + " 年"
    }

    /** 第 45 批：顶部标题用的周期文案，月 / 年共用单一出口。 */
    fun periodLabel(anchorYearMonth: Int = yearMonth): String = when (granularity) {
        DetailGranularity.MONTH -> MonthRange.label(anchorYearMonth)
        DetailGranularity.YEAR -> (anchorYearMonth / 100).toString() + " 年"
    }

    /**
     * 第 45 批：筛选面板提交时的语义裁决，纯函数可单测。
     * 有显式范围 → 尊重面板选择；无范围 + 有筛选条件 → 全部时间；
     * 无范围 + 无筛选条件 → 回到按周期的窄视图。
     */
    fun resolveAllMonths(requested: Boolean): Boolean =
        if (fromDate != null || toDate != null) requested else activeCount() > 0

    /** 是否跨月模式，用于 UI 明示「月份条只作定位」。 */
    fun isCrossMonth(): Boolean = allMonths || fromDate != null || toDate != null ||
        granularity == DetailGranularity.YEAR

    /**
     * 第 47 批：顶部月概览（支出 / 收入 / 结余）是否可见，单一出口纯函数。
     * overview 按 _overview.yearMonth 整月聚合，与筛选 / 下钻范围无关；
     * 只有「无筛选 + 月粒度 + 单月窄视图」时它才和列表同口径。
     * 年粒度 / 全部时间 / 显式范围 / 统计下钻 / 任意筛选条件下
     * MUST NOT 显示错月数字，只留 timeSummary() 的范围说明。
     */
    fun showMonthOverview(): Boolean =
        displayGranularity() == DetailGranularity.MONTH &&
            !allMonths &&
            activeCount() == 0 &&
            periodDisplayName == null &&
            displayYearMonth() == yearMonth

    /**
     * 第 42 批：时间语义单一出口，chip 与月份条共用，不允许各处自己拼。
     * totalCount 由 UI 传已加载命中数，不另跑聚合查询。
     */
    fun timeSummary(totalCount: Int? = null): String = when {
        periodDisplayName != null -> "时间：" + periodDisplayName + "（来自统计）"
        fromDate != null || toDate != null ->
            "时间：" + (fromDate?.let { MonthRange.dateLabel(it) } ?: "不限") +
                " ~ " + (toDate?.let { MonthRange.dateLabel(it) } ?: "不限")
        allMonths -> "时间：全部记录 · 当前定位 " + MonthRange.label(displayYearMonth()) +
            (totalCount?.let { " · 共 " + it + " 条命中" } ?: "")
        else -> when (displayGranularity()) {
            DetailGranularity.MONTH ->
                "时间：当前月 " + MonthRange.label(displayYearMonth()) + "（切月看其他月）"
            DetailGranularity.YEAR ->
                "时间：" + (displayYearMonth() / 100).toString() + " 年（切年看其他年）"
        }
    }

    fun activeCount(): Int = listOf(
        fromDate, toDate, accountId, type, rootCategoryId,
        categoryId, minAmountCents, maxAmountCents, noteQuery, currencyCode,
        counterpartyQuery, descriptionQuery
    ).count { it != null }

    fun bumped(): RecordFilter = copy(revision = revision + 1)

    /**
     * 第 48 批：下钻 / 显式范围态切月 = 把范围换成目标月闭区间，
     * 清掉「来自统计」标记、保留其余筛选（分类 / 对方 / 账户 / 金额 / 币种），
     * 来源转成普通「筛选结果」；无范围态（含全部时间）仍只定位、MUST NOT 注入月份过滤。
     * 两路都递增 revision，Pager 必然重建，表头与列表自然一致。
     */
    fun relocateToMonth(target: Int): RecordFilter {
        val hasRange = fromDate != null || toDate != null || periodDisplayName != null
        return if (hasRange) {
            val (first, last) = MonthRange.of(target)
            copy(
                yearMonth = target,
                fromDate = first,
                toDate = last,
                periodDisplayName = null,
                allMonths = false,
                granularity = DetailGranularity.MONTH,
                revision = revision + 1
            )
        } else {
            copy(yearMonth = target, revision = revision + 1)
        }
    }

    /** 第 48 批：清空筛选 MUST 真正回到默认当前月窄视图，而非留下下钻月份。 */
    fun cleared(): RecordFilter = currentMonth().copy(revision = revision + 1)

    companion object {
        /** 第 45 批：首次进入 / 清空筛选 = 无筛选 → 按当前周期（默认月），不再是全库。 */
        fun currentMonth(): RecordFilter = RecordFilter(
            MonthRange.currentYearMonth(), allMonths = false
        )

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
            categoryDisplayName: String?,
            fromDate: Int? = null,
            toDate: Int? = null,
            periodDisplayName: String? = null
        ): RecordFilter = RecordFilter(
            yearMonth = yearMonth,
            revision = revision,
            fromDate = fromDate,
            toDate = toDate,
            allMonths = fromDate == null && toDate == null,
            rootCategoryId = rootCategoryId,
            categoryId = categoryId,
            currencyCode = currencyCode,
            categoryDisplayName = categoryDisplayName,
            periodDisplayName = periodDisplayName
        )

        fun forCounterparty(
            yearMonth: Int,
            revision: Int,
            counterpartyQuery: String,
            currencyCode: String?,
            counterpartyDisplayName: String?,
            fromDate: Int? = null,
            toDate: Int? = null,
            periodDisplayName: String? = null
        ): RecordFilter = RecordFilter(
            yearMonth = yearMonth,
            revision = revision,
            fromDate = fromDate,
            toDate = toDate,
            allMonths = fromDate == null && toDate == null,
            counterpartyQuery = counterpartyQuery,
            currencyCode = currencyCode,
            counterpartyDisplayName = counterpartyDisplayName,
            periodDisplayName = periodDisplayName
        )

        fun forAccount(
            yearMonth: Int,
            revision: Int,
            accountId: Long,
            currencyCode: String?,
            accountDisplayName: String?,
            fromDate: Int? = null,
            toDate: Int? = null,
            periodDisplayName: String? = null
        ): RecordFilter = RecordFilter(
            yearMonth = yearMonth,
            revision = revision,
            fromDate = fromDate,
            toDate = toDate,
            allMonths = fromDate == null && toDate == null,
            accountId = accountId,
            currencyCode = currencyCode,
            accountDisplayName = accountDisplayName,
            periodDisplayName = periodDisplayName
        )
    }
}
