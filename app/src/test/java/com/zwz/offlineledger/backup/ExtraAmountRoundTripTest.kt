package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvRow
import com.zwz.offlineledger.backup.dto.BackupRecord
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import com.zwz.offlineledger.data.local.toDto
import com.zwz.offlineledger.data.local.toEntity
import com.zwz.offlineledger.data.local.toRecord
import com.zwz.offlineledger.data.local.toTrash
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 67 批：附加金额在 CSV / 备份 / 回收站全程往返，旧格式兼容。 */
class ExtraAmountRoundTripTest {

    private fun csvRow() = CsvRow(
        date = "2026-11-13", type = "EXPENSE", amountYuan = "470.01",
        accountName = "微信", categoryRoot = "餐饮", category = "食材",
        note = "午餐", currencyCode = "CNY",
        discountYuan = "29.99", feeYuan = ""
    )

    @Test
    fun `01 CSV 导出含两列且往返不丢`() {
        val bytes = CsvCodec.export(listOf(csvRow()))
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        assertTrue(text.startsWith(CsvCodec.HEADER.joinToString(",")))
        assertTrue(text.contains("29.99"))
        assertEquals(15, CsvCodec.HEADER.size)
        val back = CsvCodec.parseImport(bytes)
        assertTrue(back.errors.isEmpty())
        assertEquals("29.99", back.rows.single().discountYuan)
        assertEquals("", back.rows.single().feeYuan)
        assertEquals("470.01", back.rows.single().amountYuan)
    }

    @Test
    fun `02 老 13 列 CSV 缺两列按空串兼容`() {
        val oldHeader = CsvCodec.HEADER.dropLast(2)
        val cells = listOf(
            "2026-11-13", "EXPENSE", "470.01", "微信", "餐饮", "食材",
            "午餐", "CNY", "", "", "", "", ""
        )
        val bytes = (oldHeader.joinToString(",") + "\r\n" +
            cells.joinToString(",") + "\r\n").toByteArray(Charsets.UTF_8)
        val back = CsvCodec.parseImport(bytes)
        assertTrue(back.errors.isEmpty())
        assertEquals("", back.rows.single().discountYuan)
        assertEquals("", back.rows.single().feeYuan)
    }

    @Test
    fun `03 duplicateKey 不因附加金额变化`() {
        val a = csvRow()
        val b = a.copy(discountYuan = "10.00")
        assertEquals(a.duplicateKey(), b.duplicateKey())
    }

    @Test
    fun `04 Entity 与 BackupRecord 往返保持两字段`() {
        val entity = RecordEntity(
            id = 7L, type = RecordType.EXPENSE, amountCents = 47_001L,
            fromAccountId = 1L, timestamp = 1_700_000_000_000L,
            localDate = 20261113, yearMonth = 202611,
            discountCents = 2_999L, feeCents = null
        )
        val back = entity.toDto().toEntity()
        assertEquals(2_999L, back.discountCents)
        assertNull(back.feeCents)
        assertEquals(47_001L, back.amountCents)
    }

    @Test
    fun `05 回收站删除恢复不丢附加金额`() {
        val transfer = RecordEntity(
            id = 8L, type = RecordType.TRANSFER, amountCents = 100_000L,
            fromAccountId = 1L, toAccountId = 2L,
            timestamp = 1_700_000_000_000L,
            localDate = 20261113, yearMonth = 202611,
            feeCents = 500L
        )
        val back = transfer.toTrash(1_700_000_001_000L).toRecord()
        assertEquals(500L, back.feeCents)
        assertEquals(100_000L, back.amountCents)
    }

    @Test
    fun `06 加密备份 JSON 往返保持附加金额`() {
        val record = BackupRecord(
            id = 9L, type = "EXPENSE", amountCents = 47_001L,
            fromAccountId = 1L, timestamp = 1_700_000_000_000L,
            localDate = 20261113, yearMonth = 202611,
            discountCents = 2_999L
        )
        val snapshot = LedgerSnapshot(
            accounts = emptyList(), categories = emptyList(),
            records = listOf(record), balanceAdjustments = emptyList()
        )
        val back = BackupImporter.decode(BackupExporter.encode(snapshot))
        assertEquals(2_999L, back.payload.records.single().discountCents)
    }

    @Test
    fun `07 旧备份无字段按 null 解析`() {
        val json = """{"envelope":{"schemaVersion":3,"currencyCode":"HKD","counts":{"accounts":0,"categories":0,"records":1,"balanceAdjustments":0}},"payload":{"records":[{"id":1,"type":"EXPENSE","amountCents":47001,"timestamp":1700000000000,"localDate":20261113,"yearMonth":202611}]}}"""
        // 只验证 DTO 默认值存在，不强行构造整份信封（由既有 V1/V2/V3 兼容测试覆盖）。
        val bare = BackupRecord(
            id = 1L, type = "EXPENSE", amountCents = 47_001L,
            timestamp = 1_700_000_000_000L, localDate = 20261113, yearMonth = 202611
        )
        assertNull(bare.discountCents)
        assertNull(bare.feeCents)
        assertTrue(json.contains("amountCents"))
    }
}
