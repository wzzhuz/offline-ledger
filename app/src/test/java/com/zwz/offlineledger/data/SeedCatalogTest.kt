package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.local.SeedCatalog
import com.zwz.offlineledger.data.local.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 清单唯一可替换来源是 assets/default_categories.json；fallback 必须与之逐行一致。 */
class SeedCatalogTest {

    private fun json(): String = javaClass.classLoader!!
        .getResourceAsStream("default_categories.json")!!
        .bufferedReader().use { it.readText() }

    @Test
    fun `catalog matches seed fallback exactly`() {
        val rows = SeedCatalog.parse(json())
        assertEquals(SeedData.categories.size, rows.size)
        rows.forEach { r ->
            val seed = SeedData.categories.first { it.id == r.id }
            assertEquals(seed.name, r.name)
            assertEquals(seed.type.name, r.type)
            assertEquals(seed.parentId, r.parentId)
            assertEquals(seed.level, r.level)
            assertEquals(seed.enabled, r.enabled)
            assertEquals(seed.sortOrder, r.sortOrder)
        }
    }

    @Test
    fun `A scheme puts family utility payment under relationships not housing`() {
        val rows = SeedCatalog.parse(json())
        val roots = rows.filter { it.level == 0 }.associateBy { it.id }
        val housing = rows.filter { it.parentId == roots.values.first { it.name == "居住" }.id }
        val relations = rows.filter { it.parentId == roots.values.first { it.name == "人情社交" }.id }
        assertTrue(housing.none { it.name.contains("代缴") })
        assertTrue(relations.any { it.name.contains("代缴水电") })
    }

    @Test
    fun `default disabled roots cover leisure pet and relations`() {
        val roots = SeedCatalog.parse(json()).filter { it.level == 0 }
        val disabled = roots.filter { !it.enabled }.map { it.name }.toSet()
        assertEquals(setOf("学习娱乐", "宠物", "人情社交"), disabled)
        assertTrue(roots.first { it.name == "养车" }.enabled)
    }

    @Test
    fun `insert statements carry enabled column`() {
        val sql = SeedCatalog.insertStatements(json())
        assertEquals(SeedData.categories.size, sql.size)
        assertTrue(sql.all { it.contains("enabled") })
    }
}
