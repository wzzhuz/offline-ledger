package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.dto.BackupAccount
import com.zwz.offlineledger.backup.dto.BackupRecord
import com.zwz.offlineledger.backup.dto.BackupSchema
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV3CompatTest {

    private val v1Json = """{"envelope":{"schemaVersion":1,"exportedAtEpochMilli":1,"currencyCode":"HKD","counts":{"accounts":1,"categories":0,"records":1,"balanceAdjustments":0}},"payload":{"accounts":[{"id":1,"name":"微信","type":"ASSET","initialBalanceCents":10000,"balanceCents":10000}],"categories":[],"records":[{"id":4,"type":"EXPENSE","amountCents":3850,"timestamp":1,"localDate":20261113,"yearMonth":202611}],"balanceAdjustments":[],"trash":[]}}"""

    private val v3Json = """{"envelope":{"schemaVersion":3,"exportedAtEpochMilli":3,"currencyCode":"HKD","counts":{"accounts":1,"categories":0,"records":1,"balanceAdjustments":0,"budgets":0}},"payload":{"accounts":[{"id":1,"name":"微信","type":"ASSET","initialBalanceCents":10000,"balanceCents":10000}],"categories":[],"records":[{"id":4,"type":"EXPENSE","amountCents":3850,"timestamp":1,"localDate":20261113,"yearMonth":202611,"linkedRecordId":null}],"balanceAdjustments":[],"trash":[],"budgets":[]}}"""

    @Test
    fun v1_backup_defaults_currency_to_HKD() {
        val f = BackupImporter.decode(v1Json)
        assertEquals(1, f.envelope.schemaVersion)
        assertEquals("HKD", f.payload.records.single().currencyCode)
        assertTrue(f.payload.budgets.isEmpty())
    }

    @Test
    fun v3_backup_defaults_currency_to_HKD() {
        val f = BackupImporter.decode(v3Json)
        assertEquals(3, f.envelope.schemaVersion)
        assertEquals("HKD", f.payload.records.single().currencyCode)
    }

    @Test
    fun v4_roundtrip_keeps_CNY() {
        val rec = BackupRecord(
            id = 9, type = "EXPENSE", amountCents = 2000L,
            timestamp = 1L, localDate = 20261113, yearMonth = 202611,
            currencyCode = "CNY"
        )
        val snapshot = LedgerSnapshot(
            accounts = listOf(BackupAccount(1, "支付宝", "ASSET", 5000L, 5000L)),
            categories = emptyList(),
            records = listOf(rec),
            balanceAdjustments = emptyList(),
            exportedAtEpochMilli = 4L
        )
        val decoded = BackupImporter.decode(BackupExporter.encode(snapshot))
        assertEquals(BackupSchema.CURRENT, decoded.envelope.schemaVersion)
        assertEquals("CNY", decoded.payload.records.single().currencyCode)
    }

    @Test
    fun min_supported_stays_v1() {
        assertEquals(1, BackupSchema.MIN_SUPPORTED)
        assertTrue(BackupSchema.CURRENT >= 4)
    }
}
