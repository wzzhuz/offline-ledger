package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTreeTest {

    private val categories = listOf(
        LedgerCategory(1, "餐饮", RecordType.EXPENSE, null, 0),
        LedgerCategory(101, "食材", RecordType.EXPENSE, 1L, 1),
        LedgerCategory(102, "正餐", RecordType.EXPENSE, 1L, 1),
        LedgerCategory(2, "交通", RecordType.EXPENSE, null, 0),
        LedgerCategory(201, "打车", RecordType.EXPENSE, 2L, 1)
    )
    private val map = categories.associateBy { it.id }

    @Test
    fun `root id resolution for level 0 and level 1`() {
        assertEquals(1L, CategoryTree.resolveRootId(1, map))
        assertEquals(1L, CategoryTree.resolveRootId(101, map))
        assertEquals(2L, CategoryTree.resolveRootId(201, map))
    }

    @Test
    fun `validate accepts well formed two level tree`() {
        CategoryTree.validate(categories)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validate rejects type mismatch`() {
        CategoryTree.validate(categories + LedgerCategory(103, "错误", RecordType.INCOME, 1L, 1))
    }

    @Test
    fun `level 1 sum equals level 0 sum equals total`() {
        // 支出：餐饮->食材 3000、餐饮->正餐 5000、交通->打车 2000、直接记一级餐饮 1000
        val rows = listOf(1L to 3000L, 1L to 5000L, 2L to 2000L, 1L to 1000L)
        val byRoot = CategoryTree.sumByRoot(rows)
        assertEquals(9000L, byRoot[1])
        assertEquals(2000L, byRoot[2])
        assertEquals(rows.sumOf { it.second }, byRoot.values.sum())
    }
}
