package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM：导入「先全量校验，失败不产生写入计划」的契约。 */
class CsvImportPlanTest {

    private fun rows(n: Int): List<CsvRow> = (1..n).map { i ->
        CsvRow("2026-11-13", "EXPENSE", "10.00", "微信", "餐饮", "食材", "第${i}笔", "HKD")
    }

    @Test
    fun valid_plan_keeps_all_rows() {
        val back = CsvCodec.parseImport(CsvCodec.export(rows(10)))
        assertTrue(back.ok)
        assertEquals(10, back.rows.size)
    }

    @Test
    fun one_illegal_row_discards_whole_plan() {
        val lines = (1..9).map { i ->
            listOf("2026-11-13", "EXPENSE", "10.00", "微信", "餐饮", "食材", "第${i}笔", "HKD")
                .joinToString(",")
        } + listOf(
            listOf("2026-11-13", "EXPENSE", "oops", "微信", "餐饮", "食材", "坏行", "HKD")
                .joinToString(",")
        )
        val bytes = (CsvCodec.HEADER.joinToString(",") + "\r\n" +
            lines.joinToString("\r\n") + "\r\n").toByteArray(Charsets.UTF_8)
        val back = CsvCodec.parseImport(bytes)
        assertFalse(back.ok)
        assertTrue(back.errors.any { it.contains("第 11 行") })
        // 前 9 行合法仍留在 back.rows，但调用方按 ok 判定，整个计划不落库。
    }

    @Test
    fun duplicate_key_is_stable_across_currency() {
        val a = rows(1).single()
        assertEquals(a.duplicateKey(), a.copy(currencyCode = "USD").duplicateKey())
        assertTrue(a.duplicateKey().contains(a.accountName))
    }
}
