package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.dto.BackupAccount
import com.zwz.offlineledger.backup.dto.BackupEnvelope
import com.zwz.offlineledger.backup.dto.BackupFile
import com.zwz.offlineledger.backup.dto.BackupPayload
import com.zwz.offlineledger.backup.dto.BackupRecurringRule
import com.zwz.offlineledger.backup.dto.BackupSchema
import com.zwz.offlineledger.backup.dto.BackupTemplate
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import com.zwz.offlineledger.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class BackupV1CompatTest {

    /** 刻意不含 templates / recurringRules 两个键，模拟 v1 导出的 JSON。 */
    private val v1Json = "{" +
            ""envelope":{"schemaVersion":1,"exportedAtEpochMilli":1000," +
            ""currencyCode":"HKD","counts":{"accounts":1,"categories":0," +
            ""records":0,"balanceAdjustments":0}}," +
            ""payload":{"accounts":[{"id":1,"name":"微信"," +
            ""type":"ASSET","initialBalanceCents":10000,"balanceCents":10000}]," +
            ""categories":[],"records":[],"balanceAdjustments":[],"trash":[]}}"

    @Test
    fun v1_backup_decodes_with_empty_new_lists() {
        val file = BackupImporter.decode(v1Json)
        assertEquals(1, file.envelope.schemaVersion)
        assertEquals(1, file.payload.accounts.size)
        assertTrue(file.payload.templates.isEmpty())
        assertTrue(file.payload.recurringRules.isEmpty())
    }

    @Test
    fun v2_roundtrip_carries_templates_and_rules() {
        val tpl = BackupTemplate(
            id = 7, name = "午餐", type = RecordType.EXPENSE.name,
            amountCents = 3500, categoryId = 101L, rootCategoryId = 1L
        )
        val rule = BackupRecurringRule(
            id = 9, name = "房租", type = RecordType.EXPENSE.name,
            amountCents = 800000, cadence = "MONTHLY", dayOfMonth = 1,
            anchorLocalDate = 20260101, lastGeneratedLocalDate = 20260401
        )
        val snapshot = LedgerSnapshot(
            accounts = listOf(
                BackupAccount(1, "微信", "ASSET", 10000, 10000)
            ),
            categories = emptyList(),
            records = emptyList(),
            balanceAdjustments = emptyList(),
            templates = listOf(tpl),
            recurringRules = listOf(rule),
            exportedAtEpochMilli = 1234L
        )
        val decoded = BackupImporter.decode(BackupExporter.encode(snapshot))
        assertEquals(BackupSchema.CURRENT, decoded.envelope.schemaVersion)
        assertEquals(1, decoded.envelope.counts.templates)
        assertEquals(1, decoded.envelope.counts.recurringRules)
        assertEquals(tpl, decoded.payload.templates.single())
        assertEquals(rule, decoded.payload.recurringRules.single())
    }

    @Test
    fun newer_schema_is_rejected() {
        val tooNew = BackupExporter.encode(
            LedgerSnapshot(
                accounts = emptyList(), categories = emptyList(),
                records = emptyList(), balanceAdjustments = emptyList(),
                schemaVersion = 99
            )
        )
        assertThrows(IllegalArgumentException::class.java) { BackupImporter.decode(tooNew) }
    }

    @Test
    fun envelope_header_fields_are_unchanged() {
        val e = BackupEnvelope(exportedAtEpochMilli = 1L, currencyCode = "HKD")
        val f = BackupFile(e, BackupPayload())
        assertEquals(1L, f.envelope.exportedAtEpochMilli)
        assertEquals("HKD", f.envelope.currencyCode)
        assertEquals(BackupSchema.CURRENT, f.envelope.schemaVersion)
        assertEquals(BackupSchema.MIN_SUPPORTED, 1)
    }
}
