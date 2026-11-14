package com.zwz.offlineledger

import com.zwz.offlineledger.engine.ChartData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第三批：独立 Y 轴缩放与竖轴刻度的纯 JVM 契约。 */
class ChartAxisTest {

    @Test
    fun singleSeriesMaxIsIndependentOfIncome() {
        val expense = listOf(100L, 320L, 180L)
        val income = listOf(500_000L)
        assertEquals(320L, ChartData.seriesMaxSingle(expense))
        assertEquals(500_000L, ChartData.seriesMaxSingle(income))
        // 共用 max 会把支出压成贴底，独立 max 让支出线用满画布
        val normalized = ChartData.normalize(expense, ChartData.seriesMaxSingle(expense))
        assertEquals(1f, normalized[1], 0.0001f)
    }

    @Test
    fun yAxisLabelsAreFiveDescendingYuanValues() {
        val labels = ChartData.yAxisLabels(1000_00L)
        assertEquals(5, labels.size)
        assertEquals("1000.00", labels.first())
        assertEquals("0.00", labels.last())
        assertEquals("500.00", labels[2])
    }

    @Test
    fun emptyMaxProducesZeroLabels() {
        val labels = ChartData.yAxisLabels(0L)
        assertEquals(5, labels.size)
        assertTrue(labels.all { it == "0.00" })
    }

    @Test
    fun singleMaxNeverNegative() {
        assertTrue(ChartData.seriesMaxSingle(emptyList()) >= 0L)
        assertTrue(ChartData.seriesMaxSingle(listOf(-50L)) >= 0L)
    }
}
