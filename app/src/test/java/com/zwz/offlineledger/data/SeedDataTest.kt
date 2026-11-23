package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.local.SeedData
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.engine.CategoryTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    @Test
    fun `has enough level 0 categories`() {
        val roots = SeedData.categories.filter { it.level == 0 }
        assertTrue(roots.count { it.type == RecordType.EXPENSE } >= 7)
        assertTrue(roots.count { it.type == RecordType.INCOME } >= 3)
    }

    @Test
    fun `two level tree is valid`() {
        CategoryTree.validate(SeedData.asLedgerCategories())
        assertTrue(SeedData.categories.all { it.level == 0 || it.level == 1 })
    }

    @Test
    fun `no preset accounts remain`() {
        assertTrue("预制账户行已删除，首启应为零账户", SeedData.accounts.isEmpty())
    }

    @Test
    fun `id layout has no collision and no semantic parsing`() {
        val rows = SeedData.categories
        assertEquals(rows.size, rows.map { it.id }.toSet().size)
        val roots = rows.filter { it.level == 0 }
        val leaves = rows.filter { it.level == 1 }
        assertTrue("二级最小 ID 必须大于一级最大 ID",
            leaves.minOf { it.id } > roots.maxOf { it.id })
        leaves.forEach { assertEquals("二级必须落在父×1000 段", it.parentId, it.id / 1000) }
        assertTrue(roots.all { it.id in 1L..99L })
    }

    @Test
    fun `insert statements are generated for every seed row`() {
        val statements = SeedData.allInsertStatements()
        assertEquals(SeedData.categories.size + SeedData.accounts.size, statements.size)
        assertTrue(statements.all { it.startsWith("INSERT INTO ") })
        assertTrue(statements.none { it.contains("level, 2") })
    }
}
