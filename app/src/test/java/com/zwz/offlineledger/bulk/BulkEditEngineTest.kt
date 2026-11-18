package com.zwz.offlineledger.bulk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkEditEngineTest {

    private val a = BulkTarget(1L, "南京盒马网络科技有限公司", "晚饭", 3L, 1234L, 100L, false)
    private val b = BulkTarget(2L, "盒马", "午饭", 3L, 5678L, 200L, false)
    private val c = BulkTarget(3L, "地铁", "通勤", 4L, 300L, 300L, false)

    @Test
    fun `all conditions must match`() {
        val cond = BulkConditions(
            counterpartyContains = "盒马",
            minAmountCents = 1000L,
            maxAmountCents = 2000L
        )
        val changes = BulkEditEngine.apply(listOf(a, b, c), cond, emptyList())
        assertEquals(1, changes.size)
        assertEquals(1L, changes[0].before.id)
    }

    @Test
    fun `actions apply in order and revert restores every field`() {
        val actions = listOf(
            BulkAction.SetCategory(9L),
            BulkAction.RenameCounterparty("盒马"),
            BulkAction.ReplaceInNote("晚饭", "晚餐"),
            BulkAction.SetExcluded(true)
        )
        val changes = BulkEditEngine.apply(
            listOf(a, b, c), BulkConditions(counterpartyContains = "盒马"), actions
        )
        assertEquals(2, changes.size)
        val after = changes[0].after
        assertEquals(9L, after.categoryId)
        assertEquals("盒马", after.counterparty)
        assertEquals("晚餐", after.note)
        assertTrue(after.excludedFromTotals)
        assertEquals(listOf(a, b), BulkEditEngine.revert(changes))
        assertEquals(a.note, BulkEditEngine.revert(changes)[0].note)
    }

    @Test
    fun `empty from does not wipe note`() {
        val changes = BulkEditEngine.apply(
            listOf(a), BulkConditions(), listOf(BulkAction.ReplaceInNote("", "x"))
        )
        assertEquals("晚饭", changes[0].after.note)
    }
}
