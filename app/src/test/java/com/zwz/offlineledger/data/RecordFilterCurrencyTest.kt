package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordFilterCurrencyTest {

    private fun full() = RecordFilter(
        yearMonth = 202611, accountId = 1L, type = "EXPENSE",
        rootCategoryId = 1L, categoryId = 101L,
        minAmountCents = 100L, maxAmountCents = 9999L,
        noteQuery = "午饭"
    )

    @Test
    fun default_currency_is_null_meaning_all() {
        val f = RecordFilter(202611)
        assertNull(f.currencyCode)
        assertEquals(0, f.activeCount())
    }

    /** 用相对断言，避免阶段 2 那种「维度数硬编码成常数」再次踩坑。 */
    @Test
    fun currency_adds_exactly_one_active_dimension() {
        val base = full()
        assertEquals(base.activeCount() + 1, base.copy(currencyCode = "HKD").activeCount())
    }

    @Test
    fun bumped_keeps_currency() {
        val f = full().copy(currencyCode = "CNY").bumped()
        assertEquals("CNY", f.currencyCode)
        assertEquals(1, f.revision)
    }
}
