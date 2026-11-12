package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecordFilterTest {

    @Test
    fun `default range covers whole month`() {
        val f = RecordFilter(202611)
        assertEquals(20261101, f.effectiveFromDate())
        assertEquals(20261130, f.effectiveToDate())
        assertEquals(0, f.activeCount())
    }

    @Test
    fun `custom range overrides month`() {
        val f = RecordFilter(202611, fromDate = 20261025, toDate = 20261205)
        assertEquals(20261025, f.effectiveFromDate())
        assertEquals(20261205, f.effectiveToDate())
        assertEquals(2, f.activeCount())
    }

    @Test
    fun `bump produces a distinct instance for flatMapLatest`() {
        val a = RecordFilter(202611)
        val b = a.bumped()
        assertNotEquals(a, b)
        assertEquals(1, b.revision)
        // 除 revision 外其余条件不变
        assertEquals(a.copy(revision = b.revision), b)
    }

    @Test
    fun `active count includes all filter dimensions`() {
        val f = RecordFilter(
            yearMonth = 202611, accountId = 1L, type = "EXPENSE",
            rootCategoryId = 1L, categoryId = 101L,
            minAmountCents = 100L, maxAmountCents = 9999L,
            noteQuery = "午饭"
        )
        assertEquals(7, f.activeCount())
    }
}
