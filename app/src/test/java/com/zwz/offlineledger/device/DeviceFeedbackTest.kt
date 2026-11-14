package com.zwz.offlineledger.device

import com.zwz.offlineledger.backup.BackupExporter
import com.zwz.offlineledger.backup.BackupImporter
import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvRow
import com.zwz.offlineledger.backup.dto.BackupCategory
import com.zwz.offlineledger.backup.dto.BackupEnvelope
import com.zwz.offlineledger.backup.dto.BackupRecord
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.core.time.LedgerClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 阶段 7：真机反馈批次的纯 JVM 契约，不依赖模拟器。 */
class DeviceFeedbackTest {

    @Test
    fun default_currency_is_CNY() {
        assertEquals("CNY", LedgerCurrency.DEFAULT)
        assertTrue(LedgerCurrency.SUPPORTED.contains("CNY"))
        assertTrue(LedgerCurrency.SUPPORTED.contains("HKD"))
    }

    @Test
    fun default_zone_is_Shanghai() {
        assertEquals(ZoneId.of("Asia/Shanghai"), LedgerClock.zoneId)
    }

    @Test
    fun shanghai_and_hong_kong_still_group_same_date() {
        // 两地同为 UTC+8、均无夏令时，改时区不动任何已录日期。
        val ts = 1767196800000L // 2025-12-31T16:00:00Z
        val d = LedgerClock.fromEpochMilli(ts)
        assertEquals(20260101, d.localDate)
        assertEquals(202601, d.yearMonth)
    }

    @Test
    fun csv_counterparty_round_trip() {
        val row = CsvRow(
            date = "2026-11-13", type = "EXPENSE", amountYuan = "38.50",
            accountName = "微信", categoryRoot = "餐饮", category = "食材",
            note = "午餐", currencyCode = "CNY", counterparty = "盒马"
        )
        val back = CsvCodec.parseImport(CsvCodec.export(listOf(row)))
        assertTrue(back.errors.toString(), back.ok)
        assertEquals("盒马", back.rows.single().counterparty)
        assertEquals("38.50", back.rows.single().amountYuan)
    }

    @Test
    fun legacy_8_column_csv_still_imports_with_empty_counterparty() {
        val text = "date,type,amount,account,categoryRoot,category,note,currencyCode\r\n" +
            "2026-11-13,EXPENSE,38.50,微信,餐饮,食材,午餐,CNY\r\n"
        val result = CsvCodec.parseImport(text.toByteArray(Charsets.UTF_8))
        assertTrue(result.errors.toString(), result.ok)
        assertEquals("", result.rows.single().counterparty)
    }

    @Test
    fun encrypted_backup_keeps_counterparty() {
        val record = BackupRecord(
            id = 4L, type = "EXPENSE", amountCents = 3850L,
            timestamp = 1L, localDate = 20261113, yearMonth = 202611,
            note = "午餐", counterparty = "盒马", currencyCode = "CNY"
        )
        val snapshot = LedgerSnapshot(
            accounts = emptyList(),
            categories = listOf(BackupCategory(1L, "餐饮", "EXPENSE")),
            records = listOf(record),
            balanceAdjustments = emptyList(),
            trash = emptyList(),
            templates = emptyList(),
            recurringRules = emptyList(),
            budgets = emptyList(),
            exportedAtEpochMilli = 123L,
            currencyCode = "CNY"
        )
        val text = BackupExporter.encode(snapshot)
        val back = BackupImporter.decode(text)
        assertEquals("盒马", back.payload.records.single().counterparty)
        assertEquals("CNY", back.envelope.currencyCode)
    }

    @Test
    fun duplicate_key_ignores_counterparty() {
        val a = CsvRow("2026-11-13", "EXPENSE", "10.00", "微信",
            "餐饮", "食材", "第1笔", "CNY", "盒马")
        val b = a.copy(counterparty = "别的商户")
        assertEquals(a.duplicateKey(), b.duplicateKey())
    }
}
