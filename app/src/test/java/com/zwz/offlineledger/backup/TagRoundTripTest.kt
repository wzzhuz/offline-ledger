package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvRow
import com.zwz.offlineledger.backup.dto.BackupRecord
import com.zwz.offlineledger.core.joinTagNames
import com.zwz.offlineledger.core.splitTagNames
import com.zwz.offlineledger.core.TagNameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 75 批：标签在 CSV / 备份 / 名称规则上的纯 JVM 往返。 */
class TagRoundTripTest {

    private fun row(tags: String = "家用,旅行") = CsvRow(
        date = "2026-11-13", type = "EXPENSE", amountYuan = "200.00",
        accountName = "微信", categoryRoot = "人情社交", category = "红包",
        note = "给老婆", currencyCode = "CNY", tags = tags
    )

    @Test
    fun `01 HEADER 末尾为 tags 且共 16 列`() {
        assertEquals("tags", CsvCodec.HEADER.last())
        assertEquals(16, CsvCodec.HEADER.size)
    }

    @Test
    fun `02 CSV 导出导入保留多标签`() {
        val back = CsvCodec.parseImport(CsvCodec.export(listOf(row())))
        assertTrue(back.errors.isEmpty())
        assertEquals("家用,旅行", back.rows.single().tags)
    }

    @Test
    fun `03 旧文件缺 tags 列按空串兼容`() {
        val oldHeader = CsvCodec.HEADER.dropLast(1)
        val cells = listOf(
            "2026-11-13", "EXPENSE", "200.00", "微信", "人情社交", "红包",
            "给老婆", "CNY", "", "", "", "", "", "", ""
        )
        val bytes = (oldHeader.joinToString(",") + "\r\n" +
            cells.joinToString(",") + "\r\n").toByteArray(Charsets.UTF_8)
        val back = CsvCodec.parseImport(bytes)
        assertTrue(back.errors.isEmpty())
        assertEquals("", back.rows.single().tags)
    }

    @Test
    fun `04 duplicateKey 不含 tags`() {
        assertEquals(row("家用,旅行").duplicateKey(), row("").duplicateKey())
    }

    @Test
    fun `05 名称含逗号被拒`() {
        assertNotNull(TagNameRules.validate("家用,旅行"))
        assertEquals(null, TagNameRules.validate("家庭"))
    }

    @Test
    fun `06 split 去重并截到 8 个`() {
        val raw = (1..10).joinToString(",") { "t$it" } + ",t1"
        val out = splitTagNames(raw)
        assertEquals(8, out.size)
        assertEquals(8, joinTagNames(out).split(",").size)
    }

    @Test
    fun `07 旧备份无 tagNames 按空列表解析`() {
        val bare = BackupRecord(
            id = 1L, type = "EXPENSE", amountCents = 20000L,
            timestamp = 1_700_000_000_000L, localDate = 20261113, yearMonth = 202611
        )
        assertTrue(bare.tagNames.isEmpty())
    }

    @Test
    fun `08 加密备份 JSON 往返保留标签`() {
        val record = BackupRecord(
            id = 9L, type = "EXPENSE", amountCents = 20000L,
            timestamp = 1_700_000_000_000L, localDate = 20261113, yearMonth = 202611,
            tagNames = listOf("家用", "给老婆")
        )
        val snapshot = com.zwz.offlineledger.backup.dto.LedgerSnapshot(
            accounts = emptyList(), categories = emptyList(),
            records = listOf(record), balanceAdjustments = emptyList(),
            tags = listOf(
                com.zwz.offlineledger.backup.dto.BackupTag(1L, "家用"),
                com.zwz.offlineledger.backup.dto.BackupTag(2L, "给老婆")
            )
        )
        val back = BackupImporter.decode(BackupExporter.encode(snapshot))
        assertEquals(listOf("家用", "给老婆"), back.payload.records.single().tagNames)
        assertEquals(2, back.payload.tags.size)
    }
}
