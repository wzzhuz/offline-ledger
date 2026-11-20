package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.paging.DetailGranularity
import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第 47 批：顶部月概览只在「无筛选 · 月粒度 · 单月窄视图」显示。
 *
 * 回归「统计页看 10 月跳明细、列表 10 月但支出 / 结余还是 11 月」：
 * overview 按 _overview.yearMonth 聚合，与下钻范围无关；
 * 跨范围 MUST NOT 显示数字，范围说明仍由 timeSummary() 单一出口承担。
 * 纯 JVM，不依赖 Android / Room。
 */
class DetailOverviewVisibilityTest {

    private val nov = 202611

    @Test
    fun defaultMonthScopeShowsOverview() {
        val f = RecordFilter.currentMonth().copy(yearMonth = nov)
        assertTrue(f.showMonthOverview())
    }

    @Test
    fun yearGranularityHidesOverview() {
        val f = RecordFilter.currentMonth().copy(
            yearMonth = nov, granularity = DetailGranularity.YEAR
        )
        assertFalse(f.showMonthOverview())
    }

    @Test
    fun filterWithoutRangeHidesOverview() {
        // 先带上筛选条件，再由 resolveAllMonths 裁决：空对象上调用会得到 false。
        val withFilter = RecordFilter.currentMonth().copy(
            yearMonth = nov, counterpartyQuery = "肯德基"
        )
        val f = withFilter.copy(allMonths = withFilter.resolveAllMonths(true))
        assertTrue("有筛选无时间段 MUST 为全部时间", f.allMonths)
        assertFalse(f.showMonthOverview())
        assertTrue(f.timeSummary().contains("全部记录"))
    }

    @Test
    fun explicitRangeHidesOverview() {
        val f = RecordFilter.currentMonth().copy(
            yearMonth = nov, fromDate = 20260101, toDate = 20260331
        )
        assertFalse(f.showMonthOverview())
    }

    @Test
    fun statsDrillHidesOverview() {
        val f = RecordFilter.forCategory(
            yearMonth = 202610, revision = 1,
            rootCategoryId = 3L, categoryId = null,
            currencyCode = null, categoryDisplayName = "餐饮",
            fromDate = 20261001, toDate = 20261031,
            periodDisplayName = "2026-10"
        )
        assertFalse(f.showMonthOverview())
        assertTrue(f.timeSummary().contains("来自统计"))
    }

    @Test
    fun unboundedStatsDrillStillHidesOverview() {
        val f = RecordFilter.forCategory(
            yearMonth = nov, revision = 1,
            rootCategoryId = 3L, categoryId = null,
            currencyCode = null, categoryDisplayName = "餐饮",
            periodDisplayName = "全部年份"
        )
        assertFalse(f.showMonthOverview())
    }

    @Test
    fun clearFilterRestoresOverview() {
        val withFilter = RecordFilter.currentMonth().copy(yearMonth = nov, accountId = 7L)
        val filtered = withFilter.copy(allMonths = withFilter.resolveAllMonths(true))
        assertFalse(filtered.showMonthOverview())
        val cleared = filtered.copy(
            accountId = null, allMonths = false, periodDisplayName = null
        )
        assertTrue(cleared.showMonthOverview())
    }
}
