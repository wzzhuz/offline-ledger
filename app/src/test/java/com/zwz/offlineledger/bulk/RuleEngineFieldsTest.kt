package com.zwz.offlineledger.bulk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineFieldsTest {

    private fun target(
        id: Long,
        cp: String,
        note: String = "原始备注",
        cat: Long? = null,
        accountId: Long? = 7L,
        currency: String = "CNY",
        source: String = "firefly"
    ) = BulkTarget(
        id = id,
        counterparty = cp,
        note = note,
        categoryId = cat,
        amountCents = 1250L,
        bookedAtEpochMilli = 1700000000000L,
        excludedFromTotals = false,
        accountId = accountId,
        currencyCode = currency,
        importSource = source
    )

    @Test
    fun categoryIsMatchedByIdSo同名二级DoesNotCollide() {
        val r = RuleDefinition(
            name = "分类改到新类",
            conditions = listOf(
                RuleCondition(field = RuleField.CATEGORY, op = RuleOp.EQUALS, categoryId = 31L)
            ),
            actions = listOf(RuleActionSpec(kind = RuleActionKind.SET_CATEGORY, categoryId = 99L))
        )
        assertTrue(RuleEngine.evaluate(target(1, "盒马", cat = 31L), r))
        assertFalse(RuleEngine.evaluate(target(2, "盒马", cat = 32L), r))
        val changes = RuleEngine.applyGroup(
            listOf(target(1, "盒马", cat = 31L), target(2, "盒马", cat = 32L)),
            listOf(r),
            true
        )
        assertEquals(1, changes.size)
        assertEquals(99L, changes[0].after.categoryId!!)
    }

    @Test
    fun accountCurrencySourceAndExcludedAreReadableConditionFields() {
        val r = RuleDefinition(
            conditions = listOf(
                RuleCondition(field = RuleField.ACCOUNT, op = RuleOp.EQUALS, accountId = 7L),
                RuleCondition(field = RuleField.CURRENCY, op = RuleOp.EQUALS, value = "CNY"),
                RuleCondition(field = RuleField.IMPORT_SOURCE, op = RuleOp.CONTAINS, value = "firefly"),
                RuleCondition(field = RuleField.EXCLUDED, op = RuleOp.EQUALS, value = "false")
            ),
            actions = listOf(RuleActionSpec(kind = RuleActionKind.APPEND_NOTE, text = " / 已归类"))
        )
        assertTrue(RuleEngine.evaluate(target(1, "盒马"), r))
        assertFalse(RuleEngine.evaluate(target(2, "盒马", accountId = 8L, currency = "USD"), r))
        val after = RuleEngine.applyRule(target(1, "盒马"), r)
        assertEquals("原始备注 / 已归类", after.note)
    }

    @Test
    fun notEqualsAndNegateCompose() {
        val r = RuleDefinition(
            matchMode = "ALL",
            conditions = listOf(
                RuleCondition(field = RuleField.COUNTERPARTY, op = RuleOp.NOT_EQUALS, value = "盒马"),
                RuleCondition(field = RuleField.NOTE, op = RuleOp.CONTAINS, value = "代缴", negate = true)
            ),
            actions = listOf(RuleActionSpec(kind = RuleActionKind.RENAME_COUNTERPARTY, text = "归一后"))
        )
        assertTrue(RuleEngine.evaluate(target(1, "地铁", note = "普通"), r))
        assertFalse(RuleEngine.evaluate(target(2, "盒马", note = "普通"), r))
        assertFalse(RuleEngine.evaluate(target(3, "地铁", note = "代缴水电"), r))
    }

    @Test
    fun amountAndDateRangesStillShortCircuit() {
        val r = RuleDefinition(
            conditions = listOf(
                RuleCondition(
                    field = RuleField.AMOUNT,
                    op = RuleOp.EQUALS,
                    minAmountCents = 1000L,
                    maxAmountCents = 2000L
                ),
                RuleCondition(
                    field = RuleField.DATE,
                    op = RuleOp.EQUALS,
                    fromEpochMilli = 1600000000000L,
                    toEpochMilli = 1800000000000L
                )
            ),
            actions = listOf(RuleActionSpec(kind = RuleActionKind.SET_EXCLUDED, excluded = true))
        )
        assertTrue(RuleEngine.evaluate(target(1, "盒马"), r))
        val after = RuleEngine.applyRule(target(1, "盒马"), r)
        assertTrue(after.excludedFromTotals)
    }
}
