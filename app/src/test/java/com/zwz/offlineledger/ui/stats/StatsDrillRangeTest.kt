package com.zwz.offlineledger.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** 第 44 批：三种统计粒度带进明细的日期区间，单一出口。 */
class StatsDrillRangeTest {

    @Test
    fun `month drill down covers exactly the viewed month`() {
        val r = StatsDrillRange.of(StatsMode.MONTH, 202611, 2026)
        assertEquals(20261101, r.fromDate)
        assertEquals(20261130, r.toDate)
        assertEquals(202611, r.yearMonth)
        assertEquals("2026-11", r.label)
    }

    @Test
    fun `year drill down covers the whole year`() {
        val r = StatsDrillRange.of(StatsMode.YEAR, 202611, 2026)
        assertEquals(20260101, r.fromDate)
        assertEquals(20261231, r.toDate)
        assertEquals("2026 年", r.label)
    }

    @Test
    fun `multi year drill down covers five full years`() {
        val r = StatsDrillRange.of(StatsMode.MULTI_YEAR, 202611, 2026)
        assertEquals(20220101, r.fromDate)
        assertEquals(20261231, r.toDate)
    }

    @Test
    fun `february leap year is handled by MonthRange`() {
        val r = StatsDrillRange.of(StatsMode.MONTH, 202402, 2024)
        assertEquals(20240201, r.fromDate)
        assertEquals(20240229, r.toDate)
    }
}
