package com.zwz.offlineledger.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CentsTest {

    @Test
    fun `one cent is stored as one`() {
        assertEquals(1L, Cents.fromYuan("0.01").value)
        assertEquals("0.01", Cents(1).toYuanString())
    }

    @Test
    fun `half up rounding`() {
        assertEquals(2L, Cents.fromYuan("0.015").value)
        assertEquals(12346L, Cents.fromYuan("123.456").value)
    }

    @Test
    fun `negative balance is representable`() {
        val debt = Cents(-50000)
        assertEquals("-500.00", debt.toYuanString())
        assertEquals(Cents(-49900), debt + Cents(100))
    }

    @Test
    fun `illegal input fails loudly`() {
        assertTrue(runCatching { Cents.fromYuan("abc") }.exceptionOrNull()
            is IllegalArgumentException)
        assertTrue(runCatching { Cents.fromYuan("") }.exceptionOrNull()
            is IllegalArgumentException)
    }

    @Test
    fun `no floating point drift`() {
        // 0.1 + 0.2 这类计算若用 Double 会得到 0.30000000000000004
        val a = Cents.fromYuan("0.10")
        val b = Cents.fromYuan("0.20")
        assertEquals("0.30", (a + b).toYuanString())
    }
}
