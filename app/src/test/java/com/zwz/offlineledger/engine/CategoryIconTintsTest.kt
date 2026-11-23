package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 55 批：领域配色表覆盖清单全部 iconKey，且未知 key 必须回退 null。 */
class CategoryIconTintsTest {

    private fun rows(): List<Map<String, Any?>> {
        val json = javaClass.classLoader!!
            .getResourceAsStream("default_categories.json")!!
            .bufferedReader().use { it.readText() }
        // 极简解析，只取 iconKey；不引入 org.json（与 SeedCatalog 同约束）。
        return Regex("\\{([^{}]*)\\}").findAll(json)
            .map { m ->
                val s = m.groupValues[1]
                val id = Regex("\"id\"\\s*:\\s*(\\d+)").find(s)?.groupValues?.get(1)
                val key = Regex("\"iconKey\"\\s*:\\s*\"([^\"]*)\"").find(s)?.groupValues?.get(1)
                mapOf("id" to id, "iconKey" to key)
            }.toList()
    }

    @Test
    fun `every preset iconKey has a tint`() {
        val rows = rows()
        assertTrue(rows.size >= 106)
        rows.forEach { r ->
            val key = r["iconKey"] as? String
            assertNotNull("id=${r["id"]} 缺 iconKey", key)
            assertNotNull("id=${r["id"]} 的 iconKey=$key 无配色",
                CategoryIconTints.tintOf(key!!))
        }
    }

    @Test
    fun `unknown key falls back to null`() {
        assertNull(CategoryIconTints.tintOf("not-a-tabler-icon"))
        assertNull(CategoryIconTints.tintOf(""))
    }

    @Test
    fun `income roots share income green`() {
        // 收入统一绿；支出按领域分组，同组同色。
        assertEquals(CategoryIconTints.IncomeGreen, CategoryIconTints.tintOf("wallet")!!)
        assertEquals(CategoryIconTints.FoodOrange, CategoryIconTints.tintOf("bowl-chopsticks")!!)
        assertEquals(CategoryIconTints.FoodOrange, CategoryIconTints.tintOf("bubble-tea")!!)
        assertEquals(CategoryIconTints.TransportBlue, CategoryIconTints.tintOf("motorbike")!!)
    }
}
