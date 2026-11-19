package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 第 44 批：统计下钻 MUST 带周期，明细页手工筛选仍回全部记录。 */
class RecordFilterDrillRangeTest {

    @Test
    fun `category drill down carries the stats period`() {
        val f = RecordFilter.forCategory(
            yearMonth = 202611, revision = 1,
            rootCategoryId = 3L, categoryId = null,
            currencyCode = "CNY", categoryDisplayName = "餐饮",
            fromDate = 20261101, toDate = 20261130,
            periodDisplayName = "2026-11"
        )
        assertEquals(20261101, f.effectiveFromDate())
        assertEquals(20261130, f.effectiveToDate())
        assertFalse(f.allMonths)
        assertEquals("时间：2026-11（来自统计）", f.timeSummary())
        // 只读展示名不进 activeCount；fromDate/toDate/rootCategoryId/currencyCode 共 4 条
        assertEquals(4, f.activeCount())
    }

    @Test
    fun `counterparty and account drill down also carry the period`() {
        val c = RecordFilter.forCounterparty(
            202611, 2, "盒马", "CNY", "盒马",
            fromDate = 20260101, toDate = 20261231, periodDisplayName = "2026 年"
        )
        val a = RecordFilter.forAccount(
            202611, 3, 9L, "CNY", "招行",
            fromDate = 20260101, toDate = 20261231, periodDisplayName = "2026 年"
        )
        assertEquals(20260101, c.effectiveFromDate())
        assertEquals(20261231, a.effectiveToDate())
    }

    @Test
    fun `no period means all records as before`() {
        val f = RecordFilter.forCategory(
            yearMonth = 202611, revision = 1,
            rootCategoryId = 3L, categoryId = null,
            currencyCode = null, categoryDisplayName = "餐饮"
        )
        assertNull(f.effectiveFromDate())
        assertNull(f.effectiveToDate())
        assertEquals(true, f.allMonths)
    }
}
