package com.zwz.offlineledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 92 批：标签选择器「新建」契约，纯 JVM 可跑。 */
class TagNamesTest {

    @Test
    fun newNameEntersPickedAndBecomesVisible() {
        val (next, err) = addPickedTag(emptyList(), listOf("旅行", "家用"), "结婚礼金")
        assertNull(err)
        assertEquals(listOf("结婚礼金"), next)
        // 关键回归：新建名 MUST 出现在 chip 渲染名单里，否则用户看不到任何反馈
        val visible = visibleTagNames(listOf("旅行", "家用"), next)
        assertTrue("结婚礼金" in visible)
        assertEquals(3, visible.size)
    }

    @Test
    fun existingButUnpickedNameIsSelectedDirectly() {
        val (next, err) = addPickedTag(emptyList(), listOf("旅行"), "旅行")
        assertNull(err)
        assertEquals(listOf("旅行"), next)
    }

    @Test
    fun duplicatePickedNameIsRejectedWithoutChangingList() {
        val picked = listOf("家用")
        val (next, err) = addPickedTag(picked, listOf("家用"), "家用")
        assertEquals(picked, next)
        assertTrue(err!!.contains("已选择"))
    }

    @Test
    fun validationRulesStillApply() {
        assertEquals(listOf("礼金"), addPickedTag(emptyList(), emptyList(), "  礼金  ").first)
        assertTrue(addPickedTag(emptyList(), emptyList(), "含,逗号").second != null)
        assertTrue(addPickedTag(emptyList(), emptyList(), "  ").second != null)
        assertTrue(addPickedTag(emptyList(), emptyList(), "x".repeat(21)).second != null)
    }

    @Test
    fun maxPerRecordIsEnforced() {
        val full = (1..TagNameRules.MAX_PER_RECORD).map { "t$it" }
        val (next, err) = addPickedTag(full, emptyList(), "再多一个")
        assertEquals(full, next)
        assertTrue(err!!.contains("最多"))
    }
}
