package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.dto.BackupAccount
import com.zwz.offlineledger.backup.dto.BackupSchema
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupAnchorTest {

    @Test
    fun `reconciliation anchors survive export and import`() {
        val at = 1_762_000_000_000L
        val snapshot = LedgerSnapshot(
            accounts = listOf(
                BackupAccount(
                    id = 1, name = "微信零钱", type = "ASSET",
                    initialBalanceCents = 0L, balanceCents = 99_999L,
                    lastReconciledAt = at,
                    lastReconciledBalanceCents = 99_999L
                )
            ),
            exportedAtEpochMilli = at
        )
        val json = BackupExporter.encode(snapshot)
        val back = BackupImporter.decode(json)

        assertEquals(BackupSchema.CURRENT, back.envelope.schemaVersion)
        assertEquals("HKD", back.envelope.currencyCode)
        assertEquals(at, back.payload.accounts[0].lastReconciledAt)
        assertEquals(99_999L, back.payload.accounts[0].lastReconciledBalanceCents)
        assertEquals(snapshot.accounts, back.payload.accounts)
    }

    @Test
    fun `legacy backup without anchors parses as null`() {
        val legacy = """{"envelope":{"schemaVersion":1,"exportedAtEpochMilli":1,"currencyCode":"HKD"},"payload":{"accounts":[{"id":1,"name":"旧账户","type":"ASSET","initialBalanceCents":0,"balanceCents":100}]}}"""
        val back = BackupImporter.decode(legacy)
        assertNull(back.payload.accounts[0].lastReconciledAt)
        assertNull(back.payload.accounts[0].lastReconciledBalanceCents)
    }
}
