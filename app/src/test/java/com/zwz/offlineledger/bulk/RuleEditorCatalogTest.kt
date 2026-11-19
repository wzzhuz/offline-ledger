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

    /**
     * 第 40 批：草稿（id = 0、未入库、name 为空）与已存规则走同一求值出口，
     * 预览本身只产生新对象、不就地改写原始记录；撤销逐字段还原。
     */
    @Test
    fun draftPreviewUsesSameEngineAndRevertsFieldByField() {
        val targets = listOf(t(1, "盒马", 3L), t(2, "地铁", 5L))
        val draft = RuleDefinition(
            id = 0L,
            name = "",
            conditions = listOf(
                RuleCondition(field = RuleField.CATEGORY, op = RuleOp.EQUALS, categoryId = 3L)
            ),
            actions = listOf(
                RuleActionSpec(kind = RuleActionKind.SET_CATEGORY, categoryId = 9L)
            )
        )
        val changes = RuleEngine.applyGroup(targets, listOf(draft), true)
        assertEquals(1, changes.size)
        assertEquals(9L, changes[0].after.categoryId!!)
        assertEquals(3L, targets[0].categoryId!!)
        assertEquals(listOf(targets[0]), RuleEngine.revert(changes))
    }

    /** 分类迁移就是一条普通规则，不再有映射表 / 批量录入第二套入口。 */
    @Test
    fun categoryMigrationIsJustAnOrdinaryRule() {
        val draft = RuleDefinition(
            conditions = listOf(
                RuleCondition(field = RuleField.CATEGORY, op = RuleOp.EQUALS, categoryId = 3L)
            ),
            actions = listOf(
                RuleActionSpec(kind = RuleActionKind.SET_CATEGORY, categoryId = 9L)
            )
        )
        assertEquals(1, draft.conditions.size)
        assertEquals(1, draft.actions.size)
        assertEquals(RuleField.CATEGORY, draft.conditions[0].field)
        assertEquals(RuleActionKind.SET_CATEGORY, draft.actions[0].kind)
    }
}
