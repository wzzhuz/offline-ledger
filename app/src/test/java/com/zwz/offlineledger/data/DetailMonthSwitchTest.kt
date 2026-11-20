package com.zwz.offlineledger.data

import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.paging.DetailGranularity
import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 第 46 批：切月必须默认刷新列表。
 *
 * 回归的是「标题变了、列表没动、还提示继续下滑」：locateMonth 现在把 yearMonth
 * 写回 RecordFilter，periodBounds() 必须跟着新月份重算；跨月模式仍不能夹击月份。
 * 纯 JVM，不依赖 Android / Room。
 */
class DetailMonthSwitchTest {

    private val nov = 202611
    private val oct = 202610

    @Test
    fun monthScopeFollowsYearMonthAfterSwitch() {
        val base = RecordFilter.currentMonth().copy(yearMonth = nov)
        assertEquals(MonthRange.of(nov).first, base.effectiveFromDate())
        assertEquals(MonthRange.of(nov).second, base.effectiveToDate())

        val switched = base.copy(yearMonth = oct, revision = base.revision + 1)
        assertEquals(MonthRange.of(oct).first, switched.effectiveFromDate())
        assertEquals(MonthRange.of(oct).second, switched.effectiveToDate())
        assertEquals("2026-10", switched.periodLabel(oct))
    }

    @Test
    fun yearScopeFollowsYearAfterSwitch() {
        val base = RecordFilter.currentMonth().copy(
            yearMonth = nov, granularity = DetailGranularity.YEAR
        )
        assertEquals(20260101, base.effectiveFromDate())
        assertEquals(20261231, base.effectiveToDate())

        val switched = base.copy(yearMonth = 202511, revision = base.revision + 1)
        assertEquals(20250101, switched.effectiveFromDate())
        assertEquals(20251231, switched.effectiveToDate())
        assertEquals("2025 年", switched.periodLabel(202511))
    }

    @Test
    fun crossMonthModeStillIgnoresMonthAfterSwitch() {
        val base = RecordFilter.currentMonth().copy(
            yearMonth = nov, allMonths = true, counterpartyQuery = "肯德基"
        )
        assertNull(base.effectiveFromDate())
        assertNull(base.effectiveToDate())

        val switched = base.copy(yearMonth = 202407, revision = base.revision + 1)
        assertNull("切月 MUST NOT 把跨月模式夹回按月", switched.effectiveFromDate())
        assertNull(switched.effectiveToDate())
        assertEquals("肯德基", switched.counterpartyQuery)
    }

    @Test
    fun explicitRangeWinsOverSwitchedMonth() {
        val base = RecordFilter.currentMonth().copy(
            yearMonth = nov, fromDate = 20260101, toDate = 20260331
        )
        val switched = base.copy(yearMonth = 202609, revision = base.revision + 1)
        assertEquals(20260101, switched.effectiveFromDate())
        assertEquals(20260331, switched.effectiveToDate())
    }

    @Test
    fun switchKeepsOtherFilterConditions() {
        val base = RecordFilter.currentMonth().copy(
            yearMonth = nov,
            accountId = 7L,
            currencyCode = "CNY",
            descriptionQuery = "牛奶"
        )
        val switched = base.copy(yearMonth = oct, revision = base.revision + 1)
        assertEquals(7L, switched.accountId)
        assertEquals("CNY", switched.currencyCode)
        assertEquals("牛奶", switched.descriptionQuery)
    }
}
