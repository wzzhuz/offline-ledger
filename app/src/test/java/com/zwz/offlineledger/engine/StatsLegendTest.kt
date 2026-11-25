package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 68 批：一羽式图例列表的纯 JVM 数据准备层。 */
class StatsLegendTest {

    private val slices = listOf(
        ChartData.ChartSlice(1L, "餐饮", null, 22_300L),
        ChartData.ChartSlice(2L, "交通", null, 9_500L),
        ChartData.ChartSlice(3L, "日用品", null, 8_900L),
        ChartData.ChartSlice(4L, "购物", null, 5_800L),
        ChartData.ChartSlice(5L, "外卖", null, 4_500L)
    )
    private val total = 51_000L

    @Test
    fun `01 一羽截图口径 餐饮 43_73`() {
        assertEquals("43.73%", StatsLegend.percentText(22_300L, total))
        assertEquals("18.63%", StatsLegend.percentText(9_500L, total))
        assertEquals("8.82%", StatsLegend.percentText(4_500L, total))
    }

    @Test
    fun `02 五行金额合计等于中心净支出`() {
        assertEquals(total, slices.sumOf { it.cents })
        val rows = StatsLegend.rows(slices, total)
        assertEquals(slices.size, rows.size)
        assertEquals(total, rows.sumOf { it.cents })
    }

    @Test
    fun `03 顺序与扇区一致 不在此重新排序`() {
        val rows = StatsLegend.rows(slices, total)
        assertEquals(slices.map { it.name }, rows.map { it.name })
        assertEquals(slices.indices.toList(), rows.map { it.index })
    }

    @Test
    fun `04 总量为 0 或负数一律 0 不崩溃`() {
        assertEquals(0f, StatsLegend.percent(100L, 0L), 0f)
        assertEquals(0f, StatsLegend.percent(-100L, total), 0f)
        assertEquals("0.00%", StatsLegend.percentText(100L, 0L))
    }

    @Test
    fun `05 占比钳制在 0 到 1 之间`() {
        assertTrue(StatsLegend.percent(60_000L, total) <= 1f)
        assertTrue(StatsLegend.percent(1L, total) >= 0f)
    }

    @Test
    fun `06 空切片返回空列表`() {
        assertTrue(StatsLegend.rows(emptyList(), total).isEmpty())
    }

    @Test
    fun `07 附加金额不进图例金额`() {
        // 第 67 批：amountCents 是实付真相源，discountCents 不参与统计，
        // 所以图例金额就是 amountCents 本身，不另加优惠。
        val paid = 9_900L
        val discount = 100L
        assertEquals("19.41%", StatsLegend.percentText(paid, total))
        assertEquals(10_000L, paid + discount)
    }
}
