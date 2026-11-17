package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightDataTest {

    @Test
    fun `average uses natural period units not active days`() {
        // 3100.00 元 / 31 天 = 100.00 元
        assertEquals(10_000L, InsightData.averageCents(310_000L, 31))
        assertEquals(0L, InsightData.averageCents(310_000L, 0))
        assertEquals(0L, InsightData.averageCents(0L, 12))
    }

    @Test
    fun `ratio is zero for non positive totals`() {
        assertEquals(0f, InsightData.ratio(1_000L, 0L), 0.0001f)
        assertEquals(0f, InsightData.ratio(0L, 1_000L), 0.0001f)
        assertEquals(0.25f, InsightData.ratio(2_500L, 10_000L), 0.0001f)
    }

    @Test
    fun `top rows sorted by amount with ratio and id preserved`() {
        val items = listOf(
            InsightData.RankItem("甲", 1_000L, 2, 1L),
            InsightData.RankItem("乙", 9_000L, 5, 2L),
            InsightData.RankItem("丙", 5_000L, 3, 3L),
            InsightData.RankItem("零", 0L, 0, 4L)
        )
        val rows = InsightData.topRows(items, 15_000L, 3)
        assertEquals(3, rows.size)
        assertEquals("乙", rows[0].name)
        assertEquals(9_000L, rows[0].cents)
        assertEquals(2L, rows[0].id)
        assertEquals(0.6f, rows[0].ratio, 0.0001f)
        assertEquals("丙", rows[1].name)
        assertEquals("甲", rows[2].name)
        assertTrue(rows.none { it.name == "零" })
    }

    @Test
    fun `top rows on empty data is empty`() {
        assertTrue(InsightData.topRows(emptyList(), 0L, 5).isEmpty())
    }

    @Test
    fun `rollup counts adds children into their root`() {
        // 一级 1 自身 3 笔；二级 101/102 归它，共 12 笔；一级 2 自身 5 笔。
        val direct = mapOf(1L to 3, 101L to 7, 102L to 5, 2L to 5)
        val parentOf = mapOf<Long, Long?>(1L to null, 2L to null, 101L to 1L, 102L to 1L)
        val out = InsightData.rollupCounts(direct, parentOf)
        assertEquals(15, out[1L])
        assertEquals(5, out[2L])
        assertEquals(7, out[101L])
    }

    @Test
    fun `rollup counts leaves map empty when no records`() {
        assertTrue(InsightData.rollupCounts(emptyMap(), emptyMap()).isEmpty())
    }
}
