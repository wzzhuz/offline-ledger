package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.paging.FilterField
import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 76 批：clearField 联动 / activeCount 含标签 / 清除后视图口径。 */
class FilterChipClearTest {

    private fun base() = RecordFilter(
        yearMonth = 202611, revision = 3, allMonths = true
    )

    @Test
    fun `01 activeCount 计入 tagName`() {
        val f = base().copy(tagName = "家用")
        assertEquals(1, f.activeCount())
        assertTrue(f.resolveAllMonths(false))
    }

    @Test
    fun `02 只读展示名与视图模式不计入`() {
        val f = base().copy(
            categoryDisplayName = "餐饮 / 早餐",
            counterpartyDisplayName = "盒马",
            accountDisplayName = "工商银行 (1122)",
            periodDisplayName = "2026-11",
            allMonths = false
        )
        assertEquals(0, f.activeCount())
    }

    @Test
    fun `03 只清自己不动其余条件`() {
        val f = base().copy(
            type = "EXPENSE", tagName = "家用", currencyCode = "CNY",
            rootCategoryId = 1L
        )
        assertEquals(4, f.activeCount())
        val next = f.clearField(FilterField.TAG)
        assertNull(next.tagName)
        assertEquals("EXPENSE", next.type)
        assertEquals("CNY", next.currencyCode)
        assertEquals(1L, next.rootCategoryId)
        assertEquals(3, next.activeCount())
        assertEquals(4, next.revision)
    }

    @Test
    fun `04 分类三字段联动清除`() {
        val f = base().copy(
            rootCategoryId = 1L, categoryId = 2L,
            categoryDisplayName = "餐饮 / 早餐"
        )
        val next = f.clearField(FilterField.CATEGORY)
        assertNull(next.rootCategoryId)
        assertNull(next.categoryId)
        assertNull(next.categoryDisplayName)
        assertEquals(0, next.activeCount())
    }

    @Test
    fun `05 对方两字段联动清除`() {
        val f = base().copy(
            counterpartyQuery = "盒马", counterpartyDisplayName = "盒马鲜生"
        )
        val next = f.clearField(FilterField.COUNTERPARTY)
        assertNull(next.counterpartyQuery)
        assertNull(next.counterpartyDisplayName)
    }

    @Test
    fun `06 时间范围三字段联动清除`() {
        val f = base().copy(
            fromDate = 20261101, toDate = 20261130,
            periodDisplayName = "2026-11"
        )
        val next = f.clearField(FilterField.RANGE)
        assertNull(next.fromDate)
        assertNull(next.toDate)
        assertNull(next.periodDisplayName)
        assertFalse(next.allMonths)
    }

    @Test
    fun `07 清掉最后一个条件回落到当前月口径`() {
        val f = base().copy(tagName = "家用")
        val next = f.clearField(FilterField.TAG)
        assertEquals(0, next.activeCount())
        assertFalse("无筛选时 allMonths 应为 false（当前月视图）", next.allMonths)
        assertEquals(4, next.revision)
    }

    @Test
    fun `08 仍保留其它条件时维持看全部`() {
        val f = base().copy(tagName = "家用", noteQuery = "周末")
        val next = f.clearField(FilterField.TAG)
        assertTrue(next.allMonths)
        assertEquals("周末", next.noteQuery)
    }

    @Test
    fun `09 PERIOD 只清只读周期名不动范围`() {
        val f = base().copy(
            fromDate = 20261101, toDate = 20261130,
            periodDisplayName = "2026-11"
        )
        val next = f.clearField(FilterField.PERIOD)
        assertNull(next.periodDisplayName)
        assertEquals(20261101, next.fromDate)
        assertEquals(20261130, next.toDate)
    }

    @Test
    fun `10 每次清除都递增 revision 触发 Pager 重建`() {
        val f = base().copy(revision = 9, tagName = "家用")
        assertEquals(10, f.clearField(FilterField.TAG).revision)
        assertEquals(10, f.clearField(FilterField.TAG).revision)
    }
}
