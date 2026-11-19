package com.zwz.offlineledger.bulk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun t(id: Long, cp: String, cat: Long? = null) = BulkTarget(
        id = id, counterparty = cp, note = "note", categoryId = cat,
        amountCents = 1000L, bookedAtEpochMilli = 1700000000000L,
        excludedFromTotals = false
    )

    private fun rule(
        name: String, order: Int, keywords: List<String>,
        anyMode: Boolean, toCategoryId: Long
    ) = RuleDefinition(
        id = order.toLong(), groupId = 1, name = name, sortOrder = order,
        matchMode = if (anyMode) "ANY" else "ALL",
        conditions = keywords.map {
            RuleCondition(field = RuleField.COUNTERPARTY, op = RuleOp.CONTAINS, value = it)
        },
        actions = listOf(RuleActionSpec(kind = RuleActionKind.SET_CATEGORY, categoryId = toCategoryId))
    )

    @Test
    fun anyModeHitsEitherKeyword() {
        val r = rule("散乱记录", 0, listOf("AMZN", "盒马", "超市"), true, 9L)
        assertTrue(RuleEngine.evaluate(t(1, "AMZN MKTP"), r))
        assertTrue(RuleEngine.evaluate(t(2, "盒马鲜生"), r))
        assertFalse(RuleEngine.evaluate(t(3, "地铁"), r))
    }

    @Test
    fun allModeRequiresEveryKeyword() {
        val r = rule("严格", 0, listOf("盒马", "外卖"), false, 9L)
        assertFalse(RuleEngine.evaluate(t(1, "盒马鲜生"), r))
    }

    @Test
    fun negateInvertsSingleCondition() {
        val c = RuleCondition(field = RuleField.COUNTERPARTY, op = RuleOp.CONTAINS, value = "盒马", negate = true)
        val r = rule("取反", 0, emptyList(), false, 9L).copy(conditions = listOf(c))
        assertFalse(RuleEngine.evaluate(t(1, "盒马鲜生"), r))
        assertTrue(RuleEngine.evaluate(t(2, "地铁"), r))
    }

    @Test
    fun groupAppliesInSortOrder() {
        val rules = listOf(
            rule("先归一", 0, listOf("AMZN"), false, 9L),
            rule("再改分类", 1, listOf("MKTP"), false, 8L)
        )
        val changes = RuleEngine.applyGroup(listOf(t(1, "AMZN MKTP")), rules, true)
        assertEquals(1, changes.size)
        assertEquals(8L, changes[0].after.categoryId)
    }

    @Test
    fun migrationReportsHitsAndConflicts() {
        val targets = listOf(t(1, "盒马", 3L), t(2, "盒马", 4L), t(3, "地铁", 5L))
        val mappings = listOf(
            CategoryMigration(fromCategoryId = 3L, toCategoryId = 9L, label = "旧餐饮->新"),
            CategoryMigration(counterpartyContains = "盒马", toCategoryId = 8L, label = "对方->新")
        )
        val report = RuleEngine.migrationReport(targets, mappings)
        assertEquals(1, report[0].hits)
        assertEquals(2, report[1].hits)
        assertEquals(listOf(1), report[0].conflicts)
        val changes = RuleEngine.migrate(targets, mappings)
        assertEquals(2, changes.size)
    }

    @Test
    fun revertRestoresEveryField() {
        val before = t(1, "盒马", 3L)
        val change = BulkChange(before, before.copy(categoryId = 9L, counterparty = "新名"))
        assertEquals(listOf(before), RuleEngine.revert(listOf(change)))
    }
}
