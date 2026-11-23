package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.local.SeedCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 55 批：图标字段可选，旧清单 / 旧备份零改动仍可解析；colorHex 不被改写。 */
class SeedCatalogOptionalIconTest {

    private fun legacyJson(): String = """
        {
          "version": 4,
          "categories": [
            {"id": 1, "name": "餐饮", "type": "EXPENSE", "parentId": null, "level": 0, "enabled": true, "sortOrder": 1},
            {"id": 1001, "name": "正餐", "type": "EXPENSE", "parentId": 1, "level": 1, "enabled": true, "sortOrder": 1}
          ]
        }
    """.trimIndent()

    @Test
    fun `legacy json without icon fields still parses`() {
        val rows = SeedCatalog.parse(legacyJson())
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.iconName.isEmpty() && it.iconTintHex.isEmpty() })
        val sql = SeedCatalog.insertStatements(legacyJson())
        assertEquals(2, sql.size)
        assertTrue(sql.all { it.contains("enabled") })
        assertTrue(sql.all { it.contains("'#888888'") })
    }

    @Test
    fun `optional icon fields parsed and iconName written to insert`() {
        val json = """
            {
              "version": 4,
              "categories": [
                {"id": 1, "name": "餐饮", "type": "EXPENSE", "parentId": null, "level": 0,
                 "enabled": true, "sortOrder": 1, "iconKey": "bowl", "iconTintHex": "#FF8800"},
                {"id": 1001, "name": "正餐", "type": "EXPENSE", "parentId": 1, "level": 1,
                 "enabled": true, "sortOrder": 1}
              ]
            }
        """.trimIndent()
        val rows = SeedCatalog.parse(json)
        assertEquals(2, rows.size)
        assertEquals("bowl", rows[0].iconName)
        assertEquals("#FF8800", rows[0].iconTintHex)
        assertEquals("", rows[1].iconName)

        val sql = SeedCatalog.insertStatements(json)
        assertTrue(sql[0].contains("'bowl'"))
        // 图标配色单独一套：INSERT 里的 colorHex 仍是统计扇区用的原值。
        assertTrue(sql[0].contains("'#888888'"))
        assertTrue(sql[1].contains("'', '#888888'"))
    }
}
