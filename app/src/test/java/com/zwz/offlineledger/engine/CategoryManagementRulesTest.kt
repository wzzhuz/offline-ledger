package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 55 批：分类管理三页的纯 JVM 规则锁死，避免三元组作用域被悄悄改回全局数组。 */
class CategoryManagementRulesTest {

    private val all = listOf(
        // 支出一级 1 / 2 / 3，收入一级 30，归档 99
        item(1, "EXPENSE", 0, null, 1),
        item(11, "EXPENSE", 1, 1L, 2),
        item(12, "EXPENSE", 1, 1L, 3),
        item(2, "EXPENSE", 0, null, 4),
        item(21, "EXPENSE", 1, 2L, 5),
        item(22, "EXPENSE", 1, 2L, 6),
        item(3, "EXPENSE", 0, null, 7),
        item(31, "EXPENSE", 1, 3L, 8),
        item(30, "INCOME", 0, null, 9),
        item(301, "INCOME", 1, 30L, 10),
        item(99, "EXPENSE", 0, null, 11, archived = true)
    )

    private fun item(
        id: Long, type: String, level: Int, parentId: Long?, order: Int,
        archived: Boolean = false
    ) = CategoryManagementRules.Item(id, type, level, parentId, order, archived)

    @Test
    fun `grid pads short last row with null cells`() {
        val cells = CategoryManagementRules.gridCells(11, 5)
        assertEquals(15, cells.size)
        assertEquals(3, CategoryManagementRules.rowCount(11, 5))
        assertEquals(11, cells.count { it != null })
        assertEquals(4, cells.count { it == null })
        assertEquals(0, cells[0])
        assertEquals(10, cells[10])
    }

    @Test
    fun `grid handles zero and exact multiples`() {
        assertTrue(CategoryManagementRules.gridCells(0, 5).isEmpty())
        assertEquals(0, CategoryManagementRules.rowCount(0, 5))
        assertEquals(5, CategoryManagementRules.gridCells(5, 5).size)
        assertEquals(1, CategoryManagementRules.rowCount(5, 5))
    }

    @Test
    fun `duplicate ignores case and surrounding blanks`() {
        val names = listOf("正餐", "外卖", "早餐")
        assertTrue(CategoryManagementRules.isDuplicate(names, " 正餐 "))
        assertTrue(CategoryManagementRules.isDuplicate(names, "正餐"))
        assertFalse(CategoryManagementRules.isDuplicate(names, "   "))
        assertFalse(CategoryManagementRules.isDuplicate(names, "夜宵"))
    }

    @Test
    fun `save rebuilds order by type level parent triple`() {
        val result = CategoryManagementRules.rebuildOrder(
            all = all,
            type = "EXPENSE",
            draftRoots = listOf(3L, 1L, 2L),
            draftChildren = mapOf(1L to listOf(12L, 11L), 2L to listOf(21L, 22L))
        )
        // 一级只在支出一级间换位；二级只在自己父级内换位；
        // 收入与归档按原 sortOrder 补在末尾，MUST NOT 丢项。
        assertEquals(
            listOf(3L, 31L, 1L, 12L, 11L, 2L, 21L, 22L, 30L, 301L, 99L),
            result
        )
        assertEquals(all.size, result.size)
        assertEquals(all.size, result.distinct().size)
    }

    @Test
    fun `missing draft entries are backfilled without dropping rows`() {
        val result = CategoryManagementRules.rebuildOrder(
            all = all,
            type = "EXPENSE",
            draftRoots = listOf(1L),
            draftChildren = emptyMap()
        )
        assertEquals(
            listOf(1L, 11L, 12L, 2L, 21L, 22L, 3L, 31L, 30L, 301L, 99L),
            result
        )
        assertEquals(all.map { it.id }.toSet(), result.toSet())
    }

    @Test
    fun `rebuildOrder is pure and does not mutate input`() {
        val snapshot = all.toList()
        CategoryManagementRules.rebuildOrder(
            all = all, type = "EXPENSE",
            draftRoots = listOf(2L, 1L), draftChildren = emptyMap()
        )
        assertEquals(snapshot, all)
    }
}
