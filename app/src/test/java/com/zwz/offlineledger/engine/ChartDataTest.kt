package com.zwz.offlineledger.engine

import com.zwz.offlineledger.engine.ChartData.ChartSlice
import com.zwz.offlineledger.engine.ChartData.DailyPoint
import com.zwz.offlineledger.engine.ChartData.MonthPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDataTest {

    private fun slice(id: Long, cents: Long) =
        ChartSlice(id, "分类$id", "#3F51B5", cents)

    @Test
    fun `three totals agree`() {
        val root = listOf(slice(1L, 5_000L), slice(2L, 3_000L))
        val child = listOf(slice(101L, 2_000L), slice(102L, 3_000L), slice(201L, 3_000L))
        val t = ChartData.checkTotals(root, child, 8_000L)
        assertTrue(t.consistent)
        assertEquals(0L, t.driftCents)
        assertEquals(8_000L, t.rootCents)
        assertEquals(8_000L, t.childCents)
    }

    @Test
    fun `inconsistent totals are reported with drift`() {
        val root = listOf(slice(1L, 5_000L), slice(2L, 3_000L))   // 8000
        val child = listOf(slice(101L, 2_000L), slice(102L, 3_000L)) // 5000
        val t = ChartData.checkTotals(root, child, 8_000L)
        assertFalse(t.consistent)
        assertEquals(3_000L, t.driftCents)
    }

    @Test
    fun `leap february is filled to 29 zero points`() {
        val rows = listOf(DailyPoint(3, 1_250L, 0L))
        val series = ChartData.fillDailySeries(202802, rows)
        assertEquals(29, series.size)
        assertEquals(1_250L, series[2].expenseCents)
        assertEquals(0L, series[0].expenseCents)
        assertEquals(0L, series[28].expenseCents)
        assertEquals(29, series.last().day)
    }

    @Test
    fun `short month is filled to 30 points`() {
        assertEquals(30, ChartData.fillDailySeries(202604, emptyList()).size)
        assertEquals(31, ChartData.fillDailySeries(202612, emptyList()).size)
    }

    @Test
    fun `year series always has 12 months`() {
        val rows = listOf(
            MonthPoint(1, 10_000L, 4_000L),
            MonthPoint(12, 12_000L, 5_000L)
        )
        val series = ChartData.fillYearSeries(2026, rows)
        assertEquals(12, series.size)
        assertEquals(10_000L, series[0].incomeCents)
        assertEquals(4_000L, series[0].expenseCents)
        assertEquals(0L, series[1].incomeCents)
        assertEquals(0L, series[1].expenseCents)
        assertEquals(5_000L, series[11].expenseCents)
    }

    @Test
    fun `sector angles sum to 360 and skip zero slices`() {
        val sectors = ChartData.sectors(
            listOf(slice(1L, 1_000L), slice(2L, 2_000L), slice(3L, 3_000L), slice(4L, 0L))
        )
        assertEquals(3, sectors.size)
        assertEquals(-90f, sectors[0].startAngle, 0.001f)
        assertEquals(60f, sectors[0].sweepAngle, 0.001f)
        assertEquals(120f, sectors[1].sweepAngle, 0.001f)
        assertEquals(180f, sectors[2].sweepAngle, 0.001f)
        assertEquals(360f, sectors.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun `empty or zero totals produce no sectors`() {
        assertTrue(ChartData.sectors(emptyList()).isEmpty())
        assertTrue(ChartData.sectors(listOf(slice(1L, 0L))).isEmpty())
    }

    @Test
    fun `sector hit handles the wrap across zero degrees`() {
        val sectors = ChartData.sectors(listOf(slice(1L, 3_000L), slice(2L, 1_000L)))
        // 第一个扇区 -90..180，归一化后 270..360 与 0..180 两段
        assertEquals(0, ChartData.sectorHit(sectors, -80f))
        assertEquals(0, ChartData.sectorHit(sectors, 10f))
        assertEquals(1, ChartData.sectorHit(sectors, 200f))
        assertEquals(1, ChartData.sectorHit(sectors, 265f))
        assertNull(ChartData.sectorHit(emptyList(), 0f))
    }

    @Test
    fun `normalize uses shared max across series`() {
        val expense = listOf(1_000L, 2_000L, 0L)
        val income = listOf(4_000L, 0L, 1_000L)
        val max = ChartData.seriesMax(listOf(expense, income))
        assertEquals(4_000L, max)
        assertEquals(listOf(0.25f, 0.5f, 0f), ChartData.normalize(expense, max))
        assertEquals(listOf(1f, 0f, 0.25f), ChartData.normalize(income, max))
        assertEquals(listOf(0f, 0f), ChartData.normalize(listOf(0L, 0L), 0L))
    }

    @Test
    fun `hex parsing is pure jvm and rejects garbage`() {
        assertEquals(0xFF3F51B5L, ChartData.parseHexArgb("#3F51B5"))
        assertEquals(0xFFFF0000L, ChartData.parseHexArgb("FF0000"))
        assertNull(ChartData.parseHexArgb(null))
        assertNull(ChartData.parseHexArgb("#FFF"))
        assertNull(ChartData.parseHexArgb("GGGGGG"))
    }
}
