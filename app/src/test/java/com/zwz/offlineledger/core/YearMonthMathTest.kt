package com.zwz.offlineledger.core

import com.zwz.offlineledger.core.time.YearMonthMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YearMonthMathTest {

    @Test
    fun `twelve months are visible at once`() {
        val ms = YearMonthMath.monthsOf(2026)
        assertEquals(12, ms.size)
        assertEquals(202601, ms.first())
        assertEquals(202612, ms.last())
    }

    @Test
    fun `clamp day keeps jan31 valid in february`() {
        assertEquals(28, YearMonthMath.clampDay(202702, 31))
        assertEquals(29, YearMonthMath.clampDay(202802, 31))
        assertEquals(20270228, YearMonthMath.clampDateTo(202702, 20270131))
        assertEquals(20280229, YearMonthMath.clampDateTo(202802, 20280131))
    }

    @Test
    fun `first day utc millis maps back to same month`() {
        val ms = YearMonthMath.firstDayUtcMillis(202611)
        assertEquals(YearMonthMath.utcMillisOf(20261101), ms)
        assertEquals(20261101, YearMonthMath.dateIntOfUtcMillis(ms))
    }

    @Test
    fun `years around covers center and both ends`() {
        val ys = YearMonthMath.yearsAround(2026, 12)
        assertTrue(ys.contains(2026))
        assertTrue(ys.contains(2014))
        assertTrue(ys.contains(2038))
    }
}
