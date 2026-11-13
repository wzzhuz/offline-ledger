package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.dto.BackupAccount
import com.zwz.offlineledger.backup.dto.BackupBudget
import com.zwz.offlineledger.backup.dto.BackupRecord
import com.zwz.offlineledger.backup.dto.BackupSchema
import com.zwz.offlineledger.backup.dto.BackupTemplate
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV2CompatTest {

    /** 不含 budgets / linkedRecordId / templates 的 v1 JSON（单行原始字符串）。 */
    private val v1Json = """{"envelope":{"schemaVersion":1,"exportedAtEpochMilli":1,"currencyCode":"HKD","counts":{"accounts":1,"categories":0,"records":0,"balanceAdjustments":0}},"payload":{"accounts":[{"id":1,"name":"微信","type":"ASSET","initialBalanceCents":10000,"balanceCents":10000}],"categories":[],"records":[],"balanceAdjustments":[],"trash":[]}}"""

    /** 含 templates 但不含 budgets / linkedRecordId 的 v2 JSON。 */
    private val v2Json = """{"envelope":{"schemaVersion":2,"exportedAtEpochMilli":2,"currencyCode":"HKD","counts":{"accounts":1,"categories":0,"records":0,"balanceAdjustments":0,"templates":1,"recurringRules":0}},"payload":{"accounts":[{"id":1,"name":"微信","type":"ASSET","initialBalanceCents":10000,"balanceCents":10000}],"categories":[],"records":[],"balanceAdjustments":[],"trash":[],"templates":[{"id":7,"name":"午餐","type":"EXPENSE","amountCents":3500}],"recurringRules":[]}}"""

    @Test
    fun v1_backup_decodes_with_empty_new_fields() {
        val f = BackupImporter.decode(v1Json)
        assertEquals(1, f.envelope.schemaVersion)
        assertTrue(f.payload.budgets.isEmpty())
        assertTrue(f.payload.templates.isEmpty())
        assertTrue(f.payload.records.isEmpty())
    }

    @Test
    fun v2_backup_keeps_templates_and_defaults_new_fields() {
        val f = BackupImporter.decode(v2Json)
        assertEquals(2, f.envelope.schemaVersion)
        assertEquals(1, f.payload.templates.size)
        assertEquals("午餐", f.payload.templates.single().name)
        assertTrue(f.payload.budgets.isEmpty())
    }

    @Test
    fun v3_roundtrip_carries_budgets_and_linked_record() {
        val budget = BackupBudget(
            id = 3, name = "餐饮", scope = "ROOT", categoryId = 1L, limitCents = 100000L
        )
        val settlement = BackupRecord(
            id = 9, type = "SETTLEMENT", amountCents = 3850L,
            timestamp = 1L, localDate = 20261113, yearMonth = 202611,
            linkedRecordId = 4L
        )
        val snapshot = LedgerSnapshot(
            accounts = listOf(BackupAccount(1, "微信", "ASSET", 10000L, 10000L)),
            categories = emptyList(),
            records = listOf(settlement),
            balanceAdjustments = emptyList(),
            templates = listOf(BackupTemplate(7, "午餐", "EXPENSE", 3500L)),
            budgets = listOf(budget),
            exportedAtEpochMilli = 3L
        )
        val decoded = BackupImporter.decode(BackupExporter.encode(snapshot))
        assertEquals(BackupSchema.CURRENT, decoded.envelope.schemaVersion)
        assertEquals(1, decoded.envelope.counts.budgets)
        assertEquals(budget, decoded.payload.budgets.single())
        assertEquals(4L, decoded.payload.records.single().linkedRecordId)
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
        assertThrows(IllegalArgumentException::class.java) {
            BackupImporter.decode(tooNew)
        }
    }

    @Test
    fun min_supported_is_still_v1() {
        assertEquals(1, BackupSchema.MIN_SUPPORTED)
        assertTrue(BackupSchema.CURRENT >= 3)
    }
}
