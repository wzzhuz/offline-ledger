package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 第 55 批：领域配色为编译期常量表，同组同色、未知键返 null。
 * 106 个预置分类的全覆盖由 CategoryIconKeysTest 用内联 id 列表验证，
 * 本类刻意不读 classpath 资源，避免测试副本与 assets 不同步造成假失败。
 */
class CategoryIconTintsTest {

    @Test
    fun `representative keys all have tints`() {
        val keys = listOf(
            "tools-kitchen-2", "bowl-chopsticks", "bubble-tea", "motorbike",
            "home-move", "wallet", "chart-line", "heart-handshake"
        )
        keys.forEach { assertNotNull(it, CategoryIconTints.tintOf(it)) }
    }

    @Test
    fun `unknown key falls back to null`() {
        assertNull(CategoryIconTints.tintOf("not-a-tabler-icon"))
        assertNull(CategoryIconTints.tintOf(""))
    }

    @Test
    fun `domain colors are grouped`() {
        assertEquals(CategoryIconTints.IncomeGreen, CategoryIconTints.tintOf("wallet")!!)
        assertEquals(CategoryIconTints.FoodOrange, CategoryIconTints.tintOf("bowl-chopsticks")!!)
        assertEquals(CategoryIconTints.FoodOrange, CategoryIconTints.tintOf("bubble-tea")!!)
        assertEquals(CategoryIconTints.TransportBlue, CategoryIconTints.tintOf("motorbike")!!)
    }
}
