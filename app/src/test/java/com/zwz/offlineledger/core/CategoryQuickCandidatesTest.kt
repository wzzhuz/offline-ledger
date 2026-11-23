package com.zwz.offlineledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 52 批：停用分类默认不进快捷栏，但全量选择器与历史编辑必须仍可见可选。 */
class CategoryQuickCandidatesTest {

    private fun root(id: Long, name: String, enabled: Boolean = true, archived: Boolean = false) =
        CategoryQuickItem(id, name, "EXPENSE", null, 0, enabled, archived, id.toInt())
    private fun child(id: Long, parent: Long, name: String, enabled: Boolean = true) =
        CategoryQuickItem(id, name, "EXPENSE", parent, 1, enabled, false, id.toInt())

    private val items = listOf(
        root(1, "餐饮"), root(2, "交通"), root(4, "居住"), root(7, "学习娱乐", enabled = false),
        root(9, "人情社交", enabled = false), root(99, "旧分类", archived = true),
        child(1001, 1, "正餐"), child(1002, 1, "外卖"), child(1005, 1, "零食", enabled = false),
        child(4007, 4, "话费", enabled = false), child(7001, 7, "课程书籍", enabled = false),
        child(9001, 9, "礼金", enabled = false)
    )

    @Test
    fun `default quick roots only contain enabled roots`() {
        val shown = CategoryQuickCandidates.visibleRoots(items, "EXPENSE", null)
        assertEquals(listOf(1L, 2L, 4L), shown.map { it.id })
        assertFalse(shown.any { !it.enabled })
    }

    @Test
    fun `selected disabled root stays visible in quick strip`() {
        val shown = CategoryQuickCandidates.visibleRoots(items, "EXPENSE", 9L)
        assertTrue(shown.any { it.id == 9L })
        assertEquals(setOf(1L, 2L, 4L, 9L), shown.map { it.id }.toSet())
    }

    @Test
    fun `selected disabled child shows its root and itself`() {
        val roots = CategoryQuickCandidates.visibleRoots(items, "EXPENSE", 1005L)
        assertTrue(roots.any { it.id == 1L })
        val children = CategoryQuickCandidates.visibleChildren(items, 1L, 1005L)
        assertTrue(children.any { it.id == 1005L })
        assertEquals("零食（停用）", CategoryQuickCandidates.labelOf(children.last()))
    }

    @Test
    fun `picker groups include disabled roots and children but not archived`() {
        val groups = CategoryQuickCandidates.pickerGroups(items, "EXPENSE")
        assertTrue(groups.any { it.root.id == 7L && !it.root.enabled })
        assertTrue(groups.first { it.root.id == 1L }.children.any { it.id == 1005L && !it.enabled })
        assertFalse(groups.any { it.root.id == 99L })
    }

    @Test
    fun `search hits carry parent full name`() {
        val hits = CategoryQuickCandidates.searchHits(items, "EXPENSE", "话费")
        assertEquals(1, hits.size)
        assertEquals("居住 / 话费（停用）", hits[0].label)
        assertEquals(4007L, hits[0].id)
        assertEquals(4L, hits[0].rootId)
    }

    @Test
    fun `root id of a child resolves to its parent`() {
        assertEquals(1L, CategoryQuickCandidates.rootIdOf(items, 1002L))
        assertEquals(2L, CategoryQuickCandidates.rootIdOf(items, 2L))
        assertEquals(null, CategoryQuickCandidates.rootIdOf(items, 8888L))
    }

    @Test
    fun `income roots never leak into expense strip`() {
        val mixed = items + CategoryQuickItem(30, "工资", "INCOME", null, 0, true, false, 30)
        assertFalse(CategoryQuickCandidates.visibleRoots(mixed, "EXPENSE", null).any { it.id == 30L })
        assertEquals(listOf(30L), CategoryQuickCandidates.visibleRoots(mixed, "INCOME", null).map { it.id })
    }
}
