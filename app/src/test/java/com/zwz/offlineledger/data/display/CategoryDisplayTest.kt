package com.zwz.offlineledger.data.display

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryDisplayTest {

    @Test
    fun `same leaf name under different parents is distinguishable`() {
        assertEquals("餐饮 / 其他", CategoryDisplay.resolve("餐饮", "其他"))
        assertEquals("交通 / 其他", CategoryDisplay.resolve("交通", "其他"))
    }

    @Test
    fun `root category has no parent prefix`() {
        assertEquals("餐饮", CategoryDisplay.resolve(null, "餐饮"))
        assertEquals("餐饮", CategoryDisplay.resolve("", "餐饮"))
        assertEquals("餐饮", CategoryDisplay.resolve("  ", "餐饮"))
    }

    @Test
    fun `all three consumers share one formatter`() {
        // 统计占比图例/下钻、筛选面板手工选、明细 chip 兜底都调 resolve，
        // 输出必须与手工选的历史文案一致。
        val fromDonut = CategoryDisplay.resolve("餐饮", "其他")
        val fromFilterSheet = CategoryDisplay.resolve("餐饮", "其他")
        val fromDetailFallback = CategoryDisplay.resolve("餐饮", "其他")
        assertEquals("餐饮 / 其他", fromDonut)
        assertEquals(fromDonut, fromFilterSheet)
        assertEquals(fromDonut, fromDetailFallback)
    }
}
