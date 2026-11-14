package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** 阶段 8：年度资产趋势是纯 JVM 累计，可单测，不依赖模拟器。 */
class AssetTrendTest {

    @Test
    fun asset_series_accumulates_month_by_month() {
        val s = ChartData.assetSeries(100000L, mapOf(1 to 5000L, 3 to -2000L))
        assertEquals(12, s.size)
        assertEquals(105000L, s[0])   // 1 月
        assertEquals(105000L, s[1])   // 2 月无变动
        assertEquals(103000L, s[2])   // 3 月
        assertEquals(103000L, s[11])  // 之后持平
    }

    @Test
    fun asset_series_handles_empty_and_negative() {
        val a = ChartData.assetSeries(0L, emptyMap())
        assertEquals(12, a.size)
        assertEquals(0L, a[11])
        val b = ChartData.assetSeries(1000L, mapOf(12 to -1500L))
        assertEquals(-500L, b[11])
    }

    @Test
    fun year_series_still_has_12_months() {
        assertEquals(12, ChartData.fillYearSeries(2026, emptyList()).size)
    }
}
