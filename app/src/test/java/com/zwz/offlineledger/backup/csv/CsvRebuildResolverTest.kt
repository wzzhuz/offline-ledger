package com.zwz.offlineledger.backup.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvRebuildResolverTest {

    private fun row(acc: String, root: String, child: String = "") = CsvRow(
        date = "2026-11-15", type = "EXPENSE", amountYuan = "38.50",
        accountName = acc, categoryRoot = root, category = child,
        note = "午餐", currencyCode = "CNY", counterparty = "盒马",
        importSource = "CSV", sourceId = "k-" + acc + root + child
    )

    private val knownCategories = setOf("餐饮", "餐饮/外卖", "居住", "居住/水费", "人情")

    @Test
    fun `archived and disabled entries are still known`() {
        // 调用方把归档账户 / 停用分类一并放进集合，resolver 不会把它们判成缺失。
        val r = CsvRebuildResolver.resolve(
            listOf(row("招行储蓄", "居住", "水费")),
            setOf("招行储蓄"), knownCategories
        )
        assertEquals(1, r.ready.size)
        assertEquals(0, r.skippedMissing)
        assertTrue(r.missingAccounts.isEmpty())
    }

    @Test
    fun `empty database reports all missing items instead of throwing`() {
        val rows = listOf(
            row("招行储蓄", "居住", "水费"),
            row("支付宝", "餐饮", "外卖"),
            row("招行储蓄", "餐饮", "外卖")
        )
        val r = CsvRebuildResolver.resolve(rows, emptySet(), emptySet())
        assertTrue(r.ready.isEmpty())
        assertEquals(3, r.skippedMissing)
        assertEquals(listOf("招行储蓄", "支付宝"), r.missingAccounts)
        assertEquals(listOf("居住/水费", "餐饮/外卖"), r.missingCategories)
    }

    @Test
    fun `renamed category list is reported not swallowed`() {
        val r = CsvRebuildResolver.resolve(
            listOf(row("招行储蓄", "居住", "水费"), row("招行储蓄", "人情", "")),
            setOf("招行储蓄"), setOf("居住", "人情", "人情/赡养代缴水电")
        )
        assertEquals(1, r.ready.size)
        assertEquals("人情", r.ready[0].categoryRoot)
        assertEquals(1, r.skippedMissing)
        assertEquals(listOf("居住/水费"), r.missingCategories)
    }

    @Test
    fun `same child name under different roots resolves separately`() {
        val r = CsvRebuildResolver.resolve(
            listOf(row("招行储蓄", "餐饮", "其他"), row("招行储蓄", "养车", "其他")),
            setOf("招行储蓄"),
            setOf("餐饮", "餐饮/其他", "养车")
        )
        assertEquals(1, r.ready.size)
        assertEquals(listOf("养车/其他"), r.missingCategories)
    }
}
