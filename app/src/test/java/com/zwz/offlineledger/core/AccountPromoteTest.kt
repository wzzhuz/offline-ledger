package com.zwz.offlineledger.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** 第 45 批：编辑态当前值置顶，只改顺序、不改选中项。 */
class AccountPromoteTest {

    private val accounts = listOf(
        AccountQuickItem(1L, "常用 A", false),
        AccountQuickItem(2L, "常用 B", false),
        AccountQuickItem(7L, "冷门转出", false),
        AccountQuickItem(9L, "归档转入", true),
        AccountQuickItem(3L, "常用 C", false)
    )

    @Test
    fun `both transfer accounts are promoted to the front`() {
        val out = AccountQuickCandidates.promoteIds(accounts, listOf(9L, 7L))
        assertEquals(listOf(9L, 7L, 1L, 2L, 3L), out.map { it.id })
        assertEquals(accounts.size, out.size)
        assertEquals(accounts.toSet(), out.toSet())
    }

    @Test
    fun `empty selection keeps original order`() {
        assertEquals(accounts, AccountQuickCandidates.promoteIds(accounts, emptyList()))
    }

    @Test
    fun `missing id is ignored without dropping accounts`() {
        val out = AccountQuickCandidates.promoteIds(accounts, listOf(999L, 2L))
        assertEquals(2L, out.first().id)
        assertEquals(accounts.size, out.size)
    }

    @Test
    fun `current counterparty is first and never duplicated`() {
        val out = AccountQuickCandidates.promoteText(
            listOf("肯德基", "盒马", "虚拟-不记录"), "虚拟-不记录"
        )
        assertEquals(listOf("虚拟-不记录", "肯德基", "盒马"), out)
        assertEquals(3, out.size)
    }

    @Test
    fun `blank current keeps the original suggestions`() {
        val src = listOf("肯德基", "盒马")
        assertEquals(src, AccountQuickCandidates.promoteText(src, ""))
        assertEquals(src, AccountQuickCandidates.promoteText(src, null))
    }
}
