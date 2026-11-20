package com.zwz.offlineledger.data

import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.paging.DetailGranularity
import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 45 批：无筛选收窄到周期；有筛选无范围放开到全部时间。 */
class DetailScopeTest {

    @Test
    fun `no filter defaults to current month bounds`() {
        val f = RecordFilter.currentMonth()
        assertFalse(f.allMonths)
        val (a, b) = MonthRange.of(f.yearMonth)
        assertEquals(a, f.effectiveFromDate())
        assertEquals(b, f.effectiveToDate())
        assertEquals(0, f.activeCount())
    }

    @Test
    fun `year granularity spans the whole year`() {
        val f = RecordFilter(
            202611, allMonths = false, granularity = DetailGranularity.YEAR
        )
        assertEquals(20260101, f.effectiveFromDate())
        assertEquals(20261231, f.effectiveToDate())
        assertEquals("2026 年", f.periodLabel())
        assertEquals("2026 年", f.periodLabel(202611))
        assertTrue(f.isCrossMonth())
    }

    @Test
    fun `month label stays year month`() {
        val f = RecordFilter(202611, allMonths = false)
        assertEquals("2026-11", f.periodLabel())
        assertFalse(f.isCrossMonth())
    }

    @Test
    fun `filter with no range resolves to all time`() {
        val f = RecordFilter(202611, allMonths = false, type = "EXPENSE")
        assertTrue(f.resolveAllMonths(false))
        assertNull(f.copy(allMonths = true).effectiveFromDate())
    }

    @Test
    fun `no condition without range resolves back to period`() {
        val f = RecordFilter(202611, allMonths = true)
        assertFalse(f.resolveAllMonths(true))
        assertEquals(20261101, f.copy(allMonths = false).effectiveFromDate())
    }

    @Test
    fun `explicit range keeps the panel choice and never double clamps`() {
        val f = RecordFilter(202611, fromDate = 20260101, toDate = 20260331)
        assertTrue(f.resolveAllMonths(true))
        assertFalse(f.resolveAllMonths(false))
        assertEquals(20260101, f.effectiveFromDate())
        assertEquals(20260331, f.effectiveToDate())
    }

    @Test
    fun `stats drill with range is unaffected by granularity`() {
        val f = RecordFilter(
            202611, fromDate = 20261101, toDate = 20261130,
            periodDisplayName = "2026-11",
            granularity = DetailGranularity.YEAR
        )
        assertEquals(20261101, f.effectiveFromDate())
        assertEquals(20261130, f.effectiveToDate())
        assertEquals("时间：2026-11（来自统计）", f.timeSummary())
    }
}
