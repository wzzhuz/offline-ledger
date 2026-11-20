package com.zwz.offlineledger.quick

import com.zwz.offlineledger.bulk.RuleActionKind
import com.zwz.offlineledger.bulk.RuleActionSpec
import com.zwz.offlineledger.bulk.RuleCondition
import com.zwz.offlineledger.bulk.RuleDefinition
import com.zwz.offlineledger.bulk.RuleField
import com.zwz.offlineledger.bulk.RuleOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickEntryEngineTest {

    private fun bill() = ParsedBill(
        amountCents = 3850L, isIncome = false,
        counterparty = "永辉超市", description = "牛奶鸡蛋", note = "",
        timestamp = 1732000000000L, fingerprint = "abc"
    )

    @Test
    fun ruleSetsCategory() {
        val rule = RuleDefinition(
            name = "超市归食品", enabled = true, sortOrder = 0,
            conditions = listOf(RuleCondition(RuleField.COUNTERPARTY, RuleOp.CONTAINS, "超市")),
            actions = listOf(RuleActionSpec(RuleActionKind.SET_CATEGORY, categoryId = 42L))
        )
        val out = QuickEntryEngine.applyRules(
            QuickEntryEngine.targetOf(bill(), 1L, "CNY"), listOf(rule)
        )
        assertEquals(42L, out.categoryId)
    }

    @Test
    fun rulesApplyInSortOrder() {
        val first = RuleDefinition(
            name = "a", enabled = true, sortOrder = 0,
            conditions = listOf(RuleCondition(RuleField.COUNTERPARTY, RuleOp.CONTAINS, "永辉")),
            actions = listOf(RuleActionSpec(RuleActionKind.RENAME_COUNTERPARTY, text = "永辉"))
        )
        val second = RuleDefinition(
            name = "b", enabled = true, sortOrder = 1,
            conditions = listOf(RuleCondition(RuleField.COUNTERPARTY, RuleOp.EQUALS, "永辉")),
            actions = listOf(RuleActionSpec(RuleActionKind.SET_CATEGORY, categoryId = 7L))
        )
        val out = QuickEntryEngine.applyRules(
            QuickEntryEngine.targetOf(bill(), 1L, "CNY"), listOf(second, first)
        )
        assertEquals(7L, out.categoryId)
    }

    @Test
    fun disabledRuleDoesNotApply() {
        val rule = RuleDefinition(
            name = "off", enabled = false, sortOrder = 0,
            conditions = listOf(RuleCondition(RuleField.COUNTERPARTY, RuleOp.CONTAINS, "超市")),
            actions = listOf(RuleActionSpec(RuleActionKind.SET_CATEGORY, categoryId = 42L))
        )
        val out = QuickEntryEngine.applyRules(
            QuickEntryEngine.targetOf(bill(), 1L, "CNY"), listOf(rule)
        )
        assertNull(out.categoryId)
    }

    @Test
    fun targetCarriesImportSourceForDedup() {
        val t = QuickEntryEngine.targetOf(bill(), 1L, "CNY")
        assertEquals("screen", t.importSource)
        assertEquals(3850L, t.amountCents)
        assertEquals("牛奶鸡蛋", t.description)
    }
}
