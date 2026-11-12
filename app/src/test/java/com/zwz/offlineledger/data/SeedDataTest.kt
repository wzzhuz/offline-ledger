package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.local.SeedData
import com.zwz.offlineledger.data.model.AccountType
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
    fun `default accounts include a liability with credit limit`() {
        assertTrue(SeedData.accounts.any { it.type == AccountType.ASSET })
        val liability = SeedData.accounts.first { it.type == AccountType.LIABILITY }
        assertTrue(liability.creditLimitCents != null && liability.creditLimitCents!! > 0)
    }

    @Test
    fun `insert statements are generated for every seed row`() {
        val statements = SeedData.allInsertStatements()
        assertEquals(SeedData.categories.size + SeedData.accounts.size, statements.size)
        assertTrue(statements.all { it.startsWith("INSERT INTO ") })
        assertTrue(statements.none { it.contains("level, 2") })
    }
}
