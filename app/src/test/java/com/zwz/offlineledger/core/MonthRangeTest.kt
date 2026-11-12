package com.zwz.offlineledger.core

import com.zwz.offlineledger.core.time.MonthRange
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthRangeTest {

    @Test
    fun `normal month range`() {
        assertEquals(20261101 to 20261130, MonthRange.of(202611))
    }

    @Test
    fun `february leap year`() {
        assertEquals(20280201 to 20280229, MonthRange.of(202802))
        assertEquals(20270201 to 20270228, MonthRange.of(202702))
    }

    @Test
    fun `cross year shift`() {
        assertEquals(202701, MonthRange.shift(202612, 1))
        assertEquals(202512, MonthRange.shift(202601, -1))
        assertEquals(202611, MonthRange.shift(202611, 0))
    }

    @Test
    fun `labels`() {
        assertEquals("2026-11", MonthRange.label(202611))
        assertEquals("2026-11-12", MonthRange.dateLabel(20261112))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid month rejected`() {
        MonthRange.of(202613)
    }
}
