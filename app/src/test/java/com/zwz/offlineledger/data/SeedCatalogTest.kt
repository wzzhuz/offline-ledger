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
    fun `default disabled roots cover leisure and relations only`() {
        // 第 90 批：宠物改默认启用，停用一级只剩学习娱乐 / 人情社交。
        val roots = SeedCatalog.parse(json()).filter { it.level == 0 }
        val disabled = roots.filter { !it.enabled }.map { it.name }.toSet()
        assertEquals(setOf("学习娱乐", "人情社交"), disabled)
        assertTrue(roots.first { it.name == "养车" }.enabled)
        assertTrue(roots.first { it.name == "宠物" }.enabled)
        assertTrue(roots.first { it.name == "日常" }.enabled)
    }

    @Test
    fun `v3 daily root and housing boundary`() {
        val rows = SeedCatalog.parse(json())
        val roots = rows.filter { it.level == 0 }.associateBy { it.id }
        val byName = { n: String ->
            val parentId = roots.values.first { root -> root.name == n }.id
            rows.filter { row -> row.parentId == parentId }
        }
        val housing = byName("居住").map { it.name }
        val daily = byName("日常").map { it.name }
        val shopping = byName("购物").map { it.name }
        // 居住只留绑在房子上的成本
        assertTrue(housing.none { it == "话费" || it == "家具家电" || it == "保洁搬家" })
        assertTrue(housing.containsAll(listOf("房租", "水费", "电费", "燃气", "物业", "宽带")))
        // 话费 / 家政迁日常，家具家电迁购物
        assertTrue(daily.containsAll(listOf("话费", "理发美容", "快递邮寄", "家政保洁")))
        assertTrue(shopping.contains("家具家电"))
        assertTrue(shopping.contains("家居家纺") && !shopping.contains("家居"))
        // 迁入后必须换到新父段，不能留旧 ID
        assertTrue(rows.none { it.id == 4007L || it.id == 4009L || it.id == 4011L })
        assertTrue(rows.any { it.id == 11002L && it.name == "话费" })
        assertTrue(rows.any { it.id == 11004L && it.name == "家政保洁" })
        assertTrue(rows.any { it.id == 5009L && it.name == "家具家电" })
    }

    @Test
    fun `insert statements carry enabled column`() {
        val sql = SeedCatalog.insertStatements(json())
        assertEquals(SeedData.categories.size, sql.size)
        assertTrue(sql.all { it.contains("enabled") })
    }
}
