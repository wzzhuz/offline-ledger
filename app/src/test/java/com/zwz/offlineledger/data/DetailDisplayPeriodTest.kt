package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.paging.DetailGranularity
import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第 48 批：明细页单一周期源。
 *
 * 回归「明细停 11 月、统计看 10 月跳转后表头仍 11 月，× 掉筛选后表头 11 月列表 10 月」：
 * 周期 MUST 由 _filter 派生（displayYearMonth / displayPeriodLabel），
 * 统计下钻 MUST 覆盖月 / 年 / 多年，清空筛选 MUST 真正回默认并重查。
 * 纯 JVM，不依赖 Android / Room。
 */
class DetailDisplayPeriodTest {

    private val nov = 202611
    private val oct = 202610

    private fun drillOct() = RecordFilter.forCategory(
        yearMonth = oct, revision = 3,
        rootCategoryId = 3L, categoryId = null,
        currencyCode = null, categoryDisplayName = "餐饮",
        fromDate = 20261001, toDate = 20261031,
        periodDisplayName = "2026-10"
    )

    @Test
    fun defaultUsesFilterYearMonth() {
        val f = RecordFilter.currentMonth().copy(yearMonth = nov)
        assertEquals(nov, f.displayYearMonth())
        assertEquals(DetailGranularity.MONTH, f.displayGranularity())
        assertTrue(f.displayPeriodLabel().contains("2026-11"))
        assertTrue(f.showMonthOverview())
    }

    @Test
    fun statsDrillDisplaysTargetMonth() {
        val f = drillOct()
        assertEquals(oct, f.displayYearMonth())
        assertEquals("2026-10", f.displayPeriodLabel())
        assertFalse(f.showMonthOverview())
        assertTrue(f.timeSummary().contains("来自统计"))
    }

    @Test
    fun yearRangeDisplaysYear() {
        val f = RecordFilter.currentMonth().copy(
            yearMonth = nov, fromDate = 20260101, toDate = 20261231
        )
        assertEquals(DetailGranularity.YEAR, f.displayGranularity())
        assertEquals(2026, f.displayYearMonth() / 100)
        assertTrue(f.displayPeriodLabel().contains("2026 年"))
        assertFalse(f.showMonthOverview())
    }

    @Test
    fun multiYearRangeUsesDrillLabel() {
        val f = RecordFilter.forCounterparty(
            yearMonth = nov, revision = 1,
            counterpartyQuery = "虚拟-不记录", currencyCode = null,
            counterpartyDisplayName = "虚拟-不记录",
            fromDate = 20210101, toDate = 20261231,
            periodDisplayName = "2021–2026 年"
        )
        assertEquals("2021–2026 年", f.displayPeriodLabel())
        assertEquals(2021, f.displayYearMonth() / 100)
        assertFalse(f.showMonthOverview())
    }

    @Test
    fun relocateUnderDrillPreservesOtherFilters() {
        val moved = drillOct().relocateToMonth(202609)
        assertEquals(20260901, moved.fromDate!!)
        assertEquals(20260930, moved.toDate!!)
        assertEquals(3L, moved.rootCategoryId!!)
        assertEquals("餐饮", moved.categoryDisplayName)
        assertNull(moved.periodDisplayName)
        assertFalse(moved.allMonths)
        assertEquals(202609, moved.displayYearMonth())
        assertEquals(4, moved.revision)
    }

    @Test
    fun relocateWithoutRangeOnlyMovesAnchor() {
        val f = RecordFilter.currentMonth().copy(
            yearMonth = nov, revision = 2, counterpartyQuery = "肯德基", allMonths = true
        )
        val moved = f.relocateToMonth(202609)
        assertNull(moved.fromDate)
        assertNull(moved.toDate)
        assertTrue(moved.allMonths)
        assertEquals(202609, moved.yearMonth)
        assertEquals("肯德基", moved.counterpartyQuery)
        assertEquals(3, moved.revision)
    }

    @Test
    fun clearReturnsToDefaultAndBumpsRevision() {
        val cleared = drillOct().cleared()
        assertNull(cleared.fromDate)
        assertNull(cleared.toDate)
        assertNull(cleared.periodDisplayName)
        assertEquals(0, cleared.activeCount())
        assertFalse(cleared.allMonths)
        assertEquals(DetailGranularity.MONTH, cleared.granularity)
        assertEquals(4, cleared.revision)
        assertTrue(cleared.showMonthOverview())
    }

    @Test
    fun overviewHiddenWhenPeriodMismatch() {
        val f = RecordFilter.currentMonth().copy(
            yearMonth = nov, fromDate = 20260101, toDate = 20260331
        )
        assertFalse(f.showMonthOverview())
    }
}
