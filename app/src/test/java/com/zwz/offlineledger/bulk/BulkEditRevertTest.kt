package com.zwz.offlineledger.bulk

import org.junit.Assert.assertEquals
import org.junit.Test

class BulkEditRevertTest {

    @Test
    fun revertRestoresEveryField() {
        val target = BulkTarget(
            id = 1L,
            counterparty = "南京盒马网络科技有限公司",
            note = "午餐",
            categoryId = 7L,
            amountCents = 3850L,
            bookedAtEpochMilli = 1700000000000L,
            excludedFromTotals = false
        )
        val changes = BulkEditEngine.apply(
            listOf(target),
            BulkConditions(counterpartyContains = "盒马"),
            listOf(
                BulkAction.RenameCounterparty("盒马"),
                BulkAction.SetCategory(9L),
                BulkAction.ReplaceInNote("午餐", "晚餐"),
                BulkAction.SetExcluded(true)
            )
        )
        assertEquals(1, changes.size)
        assertEquals("盒马", changes[0].after.counterparty)
        assertEquals(listOf(target), BulkEditEngine.revert(changes))
    }

    @Test
    fun allConditionsMustMatch() {
        val target = BulkTarget(
            id = 2L,
            counterparty = "盒马",
            note = "午餐",
            categoryId = 7L,
            amountCents = 3850L,
            bookedAtEpochMilli = 1700000000000L,
            excludedFromTotals = false
        )
        val conditions = BulkConditions(
            counterpartyContains = "盒马",
            noteContains = "晚餐",
            minAmountCents = 1000L
        )
        assertEquals(
            0,
            BulkEditEngine.apply(
                listOf(target), conditions, listOf(BulkAction.SetExcluded(true))
            ).size
        )
    }
}
