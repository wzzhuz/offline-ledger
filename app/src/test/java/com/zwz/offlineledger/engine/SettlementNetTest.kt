package com.zwz.offlineledger.engine

import com.zwz.offlineledger.engine.ChartData.ChartSlice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementNetTest {

    private fun slice(id: Long, cents: Long) = ChartSlice(id, "分类$id", "#123456", cents)

    @Test
    fun subtract_by_id() {
        val expense = listOf(slice(1, 5000), slice(2, 3000))
        val net = ChartData.netSlices(expense, mapOf(1L to 2000L))
        assertEquals(2, net.size)
        assertEquals(3000L, net.first { it.id == 1L }.cents)
        assertEquals(3000L, net.first { it.id == 2L }.cents)
    }

    @Test
    fun fully_refunded_category_is_dropped() {
        assertTrue(ChartData.netSlices(listOf(slice(1, 3850)), mapOf(1L to 3850L)).isEmpty())
    }

    @Test
    fun over_refund_is_clamped_to_zero_and_dropped() {
        assertTrue(ChartData.netSlices(listOf(slice(1, 100)), mapOf(1L to 500L)).isEmpty())
    }

    @Test
    fun empty_settlement_map_keeps_gross() {
        assertEquals(700L, ChartData.netSlices(listOf(slice(1, 700)), emptyMap()).single().cents)
    }

    @Test
    fun null_id_is_never_subtracted() {
        val s = ChartSlice(null, "未分类", null, 900)
        assertEquals(900L, ChartData.netSlices(listOf(s), mapOf(1L to 900L)).single().cents)
    }

    @Test
    fun name_and_color_are_preserved() {
        val net = ChartData.netSlices(listOf(slice(1, 1000)), mapOf(1L to 100L))
        assertEquals("分类1", net.single().name)
        assertEquals("#123456", net.single().colorHex)
    }
}
