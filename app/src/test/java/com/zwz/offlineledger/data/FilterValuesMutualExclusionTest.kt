package com.zwz.offlineledger.data

import com.zwz.offlineledger.data.paging.RecordFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 第 28 批：三个跨 Tab 下钻入口 MUST 从全新 RecordFilter 起手、
 * 只写本维度字段，且三条只读展示名各自独立（不进 SQL / 不计入 activeCount）。
 *
 * 回归背景：第 27 批用单条 filterDisplayName，按收款方跳转后筛选面板
 * 「分类」行会同时显示收款方名（串味）。
 */
class FilterValuesMutualExclusionTest {

    @Test
    fun categoryEntryDoesNotLeakIntoCounterpartyOrAccount() {
        val f = RecordFilter.forCategory(
            yearMonth = 202611, revision = 4,
            rootCategoryId = 12L, categoryId = null,
            currencyCode = "CNY", categoryDisplayName = "餐饮"
        )
        assertEquals(12L, f.rootCategoryId)
        assertNull(f.categoryId)
        assertNull(f.counterpartyQuery)
        assertNull(f.accountId)
        assertEquals("餐饮", f.categoryDisplayName)
        assertNull(f.counterpartyDisplayName)
        assertNull(f.accountDisplayName)
    }

    @Test
    fun counterpartyEntryLeavesCategoryRowClean() {
        val f = RecordFilter.forCounterparty(
            yearMonth = 202611, revision = 4,
            counterpartyQuery = "盒马", currencyCode = "CNY",
            counterpartyDisplayName = "盒马"
        )
        assertNull(f.rootCategoryId)
        assertNull(f.categoryId)
        assertNull(f.categoryDisplayName)
        assertEquals("盒马", f.counterpartyQuery)
        assertEquals("盒马", f.counterpartyDisplayName)
        assertNull(f.accountId)
        assertNull(f.accountDisplayName)
    }

    @Test
    fun accountEntryLeavesOtherDimensionsClean() {
        val f = RecordFilter.forAccount(
            yearMonth = 202611, revision = 4,
            accountId = 7L, currencyCode = "CNY",
            accountDisplayName = "招行"
        )
        assertNull(f.rootCategoryId)
        assertNull(f.categoryId)
        assertNull(f.counterpartyQuery)
        assertEquals(7L, f.accountId)
        assertEquals("招行", f.accountDisplayName)
        assertNull(f.categoryDisplayName)
        assertNull(f.counterpartyDisplayName)
    }

    @Test
    fun activeCountEqualsNonNullDimensionsOnly() {
        val f = RecordFilter.forCounterparty(
            yearMonth = 202611, revision = 1,
            counterpartyQuery = "盒马", currencyCode = "CNY",
            counterpartyDisplayName = "盒马"
        )
        // currencyCode + counterpartyQuery 两条；展示名不是独立条件。
        assertEquals(2, f.activeCount())
    }

    @Test
    fun displayNamesAreReadOnlyAndDoNotAffectCount() {
        val plain = RecordFilter(yearMonth = 202611)
        val named = plain.copy(
            categoryDisplayName = "餐饮",
            counterpartyDisplayName = "盒马",
            accountDisplayName = "招行"
        )
        assertEquals(plain.activeCount(), named.activeCount())
    }
}
