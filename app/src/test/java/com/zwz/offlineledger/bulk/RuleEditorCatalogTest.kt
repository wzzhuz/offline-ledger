package com.zwz.offlineledger.bulk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEditorCatalogTest {

    private fun t(id: Long, cp: String, cat: Long? = null) = BulkTarget(
        id = id,
        counterparty = cp,
        note = "note",
        categoryId = cat,
        amountCents = 1000L,
        bookedAtEpochMilli = 1700000000000L,
        excludedFromTotals = false
    )

    @Test
    fun everyFieldHasOpsAndEveryActionHasValueKind() {
        for (f in RuleEditorCatalog.fields()) {
            assertTrue(RuleEditorCatalog.opsFor(f).isNotEmpty())
            assertTrue(RuleEditorCatalog.fieldLabel(f).isNotBlank())
        }
        for (k in RuleEditorCatalog.actions()) {
            assertTrue(RuleEditorCatalog.actionLabel(k).isNotBlank())
        }
        assertEquals(RuleValueKind.CATEGORY, RuleEditorCatalog.valueKind(RuleField.CATEGORY))
        assertEquals(RuleValueKind.TEXT, RuleEditorCatalog.valueKind(RuleField.COUNTERPARTY))
        assertEquals(3, RuleEditorCatalog.opsFor(RuleField.CATEGORY).size)
        assertEquals(8, RuleEditorCatalog.opsFor(RuleField.COUNTERPARTY).size)
    }

    @Test
    fun batchRulesGenerateOrdinaryRulesNotASecondEngine() {
        val cats = mapOf("旧餐饮" to 3L, "餐饮 / 其他" to 9L)
        val rules = RuleEditorCatalog.batchRules(
            "旧餐饮|餐饮 / 其他\n盒马|餐饮 / 其他",
            groupId = 1L,
            startOrder = 0
        ) { cats[it.trim()] }
        assertEquals(2, rules.size)
        assertEquals(RuleField.CATEGORY, rules[0].conditions[0].field)
        assertEquals(3L, rules[0].conditions[0].categoryId!!)
        assertEquals(RuleField.COUNTERPARTY, rules[1].conditions[0].field)
        assertEquals("盒马", rules[1].conditions[0].value)
        for (r in rules) {
            assertEquals(RuleActionKind.SET_CATEGORY, r.actions[0].kind)
            assertEquals(9L, r.actions[0].categoryId!!)
        }
        assertEquals(0, rules[0].sortOrder)
        assertEquals(1, rules[1].sortOrder)
    }

    @Test
    fun batchReportShowsHitsConflictsAndMisses() {
        val targets = listOf(t(1, "盒马", 3L), t(2, "盒马", 4L), t(3, "地铁", 5L))
        val cats = mapOf("旧餐饮" to 3L, "餐饮 / 其他" to 9L)
        val rules = RuleEditorCatalog.batchRules(
            "旧餐饮|餐饮 / 其他\n盒马|餐饮 / 其他\n不存在的词|餐饮 / 其他",
            1L,
            0
        ) { cats[it.trim()] }
        val report = RuleEditorCatalog.batchReport(targets, rules)
        assertEquals(3, report.size)
        assertEquals(1, report[0].hits)
        assertEquals(listOf(1), report[0].conflicts)
        assertEquals(2, report[1].hits)
        assertEquals(0, report[2].hits)
    }

    @Test
    fun generatedRulesApplyThroughTheSameEngine() {
        val targets = listOf(t(1, "盒马", 3L), t(2, "地铁", 5L))
        val cats = mapOf("旧餐饮" to 3L, "餐饮 / 其他" to 9L)
        val rules = RuleEditorCatalog.batchRules("旧餐饮|餐饮 / 其他", 1L, 0) { cats[it.trim()] }
        val changes = RuleEngine.applyGroup(targets, rules, true)
        assertEquals(1, changes.size)
        assertEquals(9L, changes[0].after.categoryId!!)
        assertEquals(targets, RuleEngine.revert(changes))
    }
}
