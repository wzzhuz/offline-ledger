package com.zwz.offlineledger.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 第 55 批：覆盖安装兜底 —— 预置 106 个 id 必须全部能回查到 iconKey。 */
class CategoryIconKeysTest {

    @Test
    fun `all preset ids resolve to a key with a tint`() {
        val ids = listOf<Long>(
            1L,
            1001L,
            1002L,
            1003L,
            1004L,
            1005L,
            1006L,
            1007L,
            1008L,
            1009L,
            1010L,
            2L,
            2001L,
            2002L,
            2003L,
            2004L,
            2005L,
            2006L,
            2007L,
            3L,
            3001L,
            3002L,
            3003L,
            3004L,
            3005L,
            3006L,
            3007L,
            3008L,
            3009L,
            4L,
            4001L,
            4002L,
            4003L,
            4004L,
            4005L,
            4006L,
            4007L,
            4008L,
            4009L,
            4010L,
            4011L,
            5L,
            5001L,
            5002L,
            5003L,
            5004L,
            5005L,
            5006L,
            5007L,
            5008L,
            6L,
            6001L,
            6002L,
            6003L,
            6004L,
            6005L,
            6006L,
            6007L,
            6008L,
            7L,
            7001L,
            7002L,
            7003L,
            7004L,
            7005L,
            7006L,
            7007L,
            7008L,
            8L,
            8001L,
            8002L,
            8003L,
            8004L,
            8005L,
            8006L,
            8007L,
            9L,
            9001L,
            9002L,
            9003L,
            9004L,
            9005L,
            9006L,
            10L,
            10001L,
            10002L,
            10003L,
            30L,
            30001L,
            30002L,
            30003L,
            31L,
            31001L,
            31002L,
            32L,
            32001L,
            32002L,
            33L,
            33001L,
            33002L,
            33003L,
            34L,
            34001L,
            34002L,
            34003L,
            34004L
        )
        assertEquals(106, ids.size)
        ids.forEach { id ->
            val key = CategoryIconKeys.keyOfPreset(id) ?: error("预置 id=$id 缺 iconKey")
            check(CategoryIconTints.tintOf(key) != null) { "id=$id 的 key=$key 缺配色" }
        }
    }

    @Test
    fun `custom category ids do not resolve`() {
        // 手工分类 id 从 max+1 起、永不回填空洞，查不到 → 中性圆兜底。
        assertNull(CategoryIconKeys.keyOfPreset(999999L))
        assertNull(CategoryIconKeys.keyOfPreset(0L))
        assertNull(CategoryIconKeys.keyOfPreset(-1L))
    }

    @Test
    fun `a few representative mappings`() {
        assertEquals("tools-kitchen-2", CategoryIconKeys.keyOfPreset(1L))
        assertEquals("bowl-chopsticks", CategoryIconKeys.keyOfPreset(1001L))
        assertEquals("bubble-tea", CategoryIconKeys.keyOfPreset(1007L))
        assertEquals("home-move", CategoryIconKeys.keyOfPreset(4011L))
        assertEquals("wallet", CategoryIconKeys.keyOfPreset(30L))
    }
}
