package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM：CSV 字节格式与全量校验，不需要 Context / Room / 模拟器。 */
class CsvCodecTest {

    private fun row() = CsvRow(
        date = "2026-11-13", type = "EXPENSE", amountYuan = "38.50",
        accountName = "微信", categoryRoot = "餐饮", category = "食材",
        note = "午餐, 加饮料", currencyCode = "HKD"
    )

    @Test
    fun export_starts_with_utf8_bom_and_fixed_header() {
        val bytes = CsvCodec.export(listOf(row()))
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        assertTrue(text.startsWith(CsvCodec.HEADER.joinToString(",")))
        assertTrue(text.contains("38.50"))
    }

    @Test
    fun roundtrip_keeps_comma_in_note() {
        val back = CsvCodec.parseImport(CsvCodec.export(listOf(row())))
        assertTrue(back.errors.isEmpty())
        assertEquals(1, back.rows.size)
        assertEquals("午餐, 加饮料", back.rows.single().note)
        assertEquals("38.50", back.rows.single().amountYuan)
    }

    @Test
    fun roundtrip_two_currencies() {
        val r2 = row().copy(amountYuan = "20.00", currencyCode = "CNY", note = "地铁")
        val back = CsvCodec.parseImport(CsvCodec.export(listOf(row(), r2)))
        assertTrue(back.errors.isEmpty())
        assertEquals(2, back.rows.size)
        assertTrue(back.rows.any { it.amountYuan == "38.50" && it.currencyCode == "HKD" })
        assertTrue(back.rows.any { it.amountYuan == "20.00" && it.currencyCode == "CNY" })
    }

    @Test
    fun duplicate_key_ignores_currency_column() {
        val a = row()
        val b = a.copy(currencyCode = "CNY")
        assertEquals(a.duplicateKey(), b.duplicateKey())
    }

    private fun csv(vararg lines: String): ByteArray =
        (CsvCodec.HEADER.joinToString(",") + "\r\n" +
            lines.joinToString("\r\n") + "\r\n").toByteArray(Charsets.UTF_8)

    private fun line(
        date: String = "2026-11-13", type: String = "EXPENSE", amount: String = "38.50",
        account: String = "微信", root: String = "餐饮", child: String = "食材",
        note: String = "午餐", cc: String = "HKD"
    ) = listOf(date, type, amount, account, root, child, note, cc).joinToString(",")

    @Test
    fun missing_header_column_is_reported() {
        val bad = ("date,type,amount,account,categoryRoot,category,note\r\n" + line())
            .toByteArray(Charsets.UTF_8)
        val r = CsvCodec.parseImport(bad)
        assertFalse(r.ok)
        assertTrue(r.errors.any { it.contains("currencyCode") })
    }

    @Test
    fun illegal_amount_reports_line_number_and_no_rows() {
        val r = CsvCodec.parseImport(csv(line(), line(amount = "abc")))
        assertFalse(r.ok)
        assertTrue(r.errors.any { it.contains("第 3 行") })
        // 合法行仍留在 rows 里，但调用方 MUST 按 ok 整体废弃，不会进入写入事务。
    }

    @Test
    fun illegal_type_and_date_are_rejected() {
        val r = CsvCodec.parseImport(csv(line(type = "TRANSFER"), line(date = "2026-13-45")))
        assertFalse(r.ok)
        assertEquals(2, r.errors.size)
    }

    @Test
    fun tsv_is_supported() {
        val text = CsvCodec.HEADER.joinToString("\t") + "\n" +
            listOf("2026-11-13", "EXPENSE", "38.50", "微信", "餐饮", "食材", "午餐", "HKD")
                .joinToString("\t") + "\n"
        val r = CsvCodec.parseImport(text.toByteArray(Charsets.UTF_8))
        assertTrue(r.errors.isEmpty())
        assertEquals(1, r.rows.size)
    }

    @Test
    fun blank_lines_are_skipped() {
        val bytes = (CsvCodec.HEADER.joinToString(",") + "\r\n\r\n" + line() + "\r\n\r\n")
            .toByteArray(Charsets.UTF_8)
        val r = CsvCodec.parseImport(bytes)
        assertTrue(r.errors.isEmpty())
        assertEquals(1, r.rows.size)
    }

    @Test
    fun empty_file_is_reported() {
        assertFalse(CsvCodec.parseImport(ByteArray(0)).ok)
    }
}
