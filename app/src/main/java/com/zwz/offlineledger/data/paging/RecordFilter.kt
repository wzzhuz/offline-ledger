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
    val noteQuery: String? = null
) {
    fun effectiveFromDate(): Int = fromDate ?: MonthRange.of(yearMonth).first
    fun effectiveToDate(): Int = toDate ?: MonthRange.of(yearMonth).second

    fun activeCount(): Int = listOf(
        fromDate, toDate, accountId, type, rootCategoryId,
        categoryId, minAmountCents, maxAmountCents, noteQuery
    ).count { it != null }

    fun bumped(): RecordFilter = copy(revision = revision + 1)

    companion object {
        fun currentMonth(): RecordFilter = RecordFilter(MonthRange.currentYearMonth())
    }
}
