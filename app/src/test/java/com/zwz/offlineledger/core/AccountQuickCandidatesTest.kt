package com.zwz.offlineledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 44 批：转账编辑时冷门 / 归档账户 MUST 出现在可见候选里。 */
class AccountQuickCandidatesTest {

    private val accounts = listOf(
        AccountQuickItem(1, "支付宝", false),
        AccountQuickItem(2, "微信", false),
        AccountQuickItem(3, "招行储蓄", false),
        AccountQuickItem(4, "现金", false),
        AccountQuickItem(5, "冷门卡", false),
        AccountQuickItem(6, "旧卡", true)
    )

    @Test
    fun `selected unpopular account is appended and stays visible`() {
        val shown = AccountQuickCandidates.select(accounts, 5, 4)
        assertEquals(5, shown.size)
        assertEquals(5L, shown.last().id)
        // 常用顺序不变
        assertEquals(listOf(1L, 2L, 3L, 4L), shown.take(4).map { it.id })
    }

    @Test
    fun `selected popular account is not duplicated`() {
        val shown = AccountQuickCandidates.select(accounts, 2, 4)
        assertEquals(4, shown.size)
        assertEquals(1, shown.count { it.id == 2L })
    }

    @Test
    fun `archived account is kept and labelled`() {
        val shown = AccountQuickCandidates.select(accounts, 6, 4)
        assertTrue(shown.any { it.id == 6L })
        assertEquals("旧卡（已归档）", AccountQuickCandidates.labelOf(shown.last()))
    }

    @Test
    fun `deleted account is detected instead of silently replaced`() {
        assertNull(AccountQuickCandidates.currentOrNull(accounts, 99))
        assertFalse(AccountQuickCandidates.select(accounts, 99, 4).any { it.id == 99L })
    }
}
