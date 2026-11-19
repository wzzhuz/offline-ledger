package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordFilterTest {

    /** 第 42 批：不带时间 = 全部记录，不再隐式夹击当前月。 */
    @Test
    fun `defaults to all records when time range is empty`() {
        val f = RecordFilter(202611)
        assertEquals(true, f.allMonths)
        assertNull(f.effectiveFromDate())
        assertNull(f.effectiveToDate())
        assertEquals(0, f.activeCount())
        assertEquals("时间：全部记录 · 当前定位 2026-11", f.timeSummary())
    }

    @Test
    fun `explicit month view still clamps to one month`() {
        val f = RecordFilter(202611, allMonths = false)
        assertEquals(20261101, f.effectiveFromDate())
        assertEquals(20261130, f.effectiveToDate())
        assertEquals(false, f.isCrossMonth())
    }

    @Test
    fun `time summary reports hit count without a new query`() {
        val f = RecordFilter(202611)
        assertEquals(
            "时间：全部记录 · 当前定位 2026-11 · 共 37 条命中",
            f.timeSummary(37)
        )
    }

    @Test
    fun `custom range overrides month`() {
        val f = RecordFilter(202611, fromDate = 20261025, toDate = 20261205)
        assertEquals(20261025, f.effectiveFromDate())
        assertEquals(20261205, f.effectiveToDate())
        assertEquals(2, f.activeCount())
    }

    @Test
    fun `bump produces a distinct instance for flatMapLatest`() {
        val a = RecordFilter(202611)
        val b = a.bumped()
        assertNotEquals(a, b)
        assertEquals(1, b.revision)
        // 除 revision 外其余条件不变
        assertEquals(a.copy(revision = b.revision), b)
    }

    @Test
    fun `active count includes all filter dimensions`() {
        val f = RecordFilter(
            yearMonth = 202611, accountId = 1L, type = "EXPENSE",
            rootCategoryId = 1L, categoryId = 101L,
            minAmountCents = 100L, maxAmountCents = 9999L,
            noteQuery = "午饭"
        )
        assertEquals(7, f.activeCount())
    }

    /** 第 41 批：全部月份模式不把「当前查看月」隐式注入成筛选条件。 */
    @Test
    fun `all months mode does not inject current month`() {
        val f = RecordFilter(202611, allMonths = true)
        assertNull(f.effectiveFromDate())
        assertNull(f.effectiveToDate())
        assertEquals(0, f.activeCount())
        assertEquals(true, f.isCrossMonth())
        assertEquals("时间：全部记录 · 当前定位 2026-11", f.timeSummary())
    }

    /** 第 41 批：范围只填一端时另一端不设限，不与月份双重夹击。 */
    @Test
    fun `one sided range does not double clamp with month`() {
        val f = RecordFilter(202611, fromDate = 20250101)
        assertEquals(20250101, f.effectiveFromDate())
        assertNull(f.effectiveToDate())
        assertEquals(true, f.isCrossMonth())
    }

    /** 6c-2 加了 currencyCode 维度，用相对断言而非手写常数。 */
    @Test
    fun `currency dimension is included`() {
        val base = RecordFilter(
            yearMonth = 202611, accountId = 1L, type = "EXPENSE",
            rootCategoryId = 1L, categoryId = 101L,
            minAmountCents = 100L, maxAmountCents = 9999L,
            noteQuery = "午饭"
        )
        assertEquals(base.activeCount() + 1, base.copy(currencyCode = "HKD").activeCount())
    }
}
