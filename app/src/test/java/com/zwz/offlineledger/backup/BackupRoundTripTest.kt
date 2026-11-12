package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.dto.BackupAccount
import com.zwz.offlineledger.backup.dto.BackupAdjustment
import com.zwz.offlineledger.backup.dto.BackupCategory
import com.zwz.offlineledger.backup.dto.BackupRecord
import com.zwz.offlineledger.backup.dto.BackupSchema
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import com.zwz.offlineledger.data.model.LedgerAccount
import com.zwz.offlineledger.engine.LedgerEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRoundTripTest {

    @Test
    fun `export then import keeps balances identical`() {
        val snapshot = LedgerSnapshot(
            accounts = listOf(
                BackupAccount(1, "微信零钱", "ASSET", 100_000L, 100_000L),
                BackupAccount(2, "招行信用卡", "LIABILITY", 0L, -3_000L, creditLimitCents = 2_000_000L)
            ),
            categories = listOf(
                BackupCategory(1, "餐饮", "EXPENSE", null, 0),
                BackupCategory(101, "食材", "EXPENSE", 1L, 1)
            ),
            records = listOf(
                BackupRecord(1, "EXPENSE", 3_000L, 101L, 1L, 2L, null, 1_700_000_000_000L, 20260101, 202601)
            ),
            balanceAdjustments = listOf(BackupAdjustment(1, 1L, -1L)),
            exportedAtEpochMilli = 1_700_000_000_000L
        )

        val json = BackupExporter.encode(snapshot)
        val back = BackupImporter.decode(json)

        assertEquals(BackupSchema.CURRENT, back.envelope.schemaVersion)
        assertEquals("HKD", back.envelope.currencyCode)
        assertEquals(2, back.envelope.counts.accounts)
        assertEquals(1, back.envelope.counts.records)
        assertEquals(snapshot.accounts, back.payload.accounts)
        assertEquals(snapshot.records, back.payload.records)

        // 解码后的流水 + 校准 → 余额与原快照完全一致
        val accounts = back.payload.accounts.map {
            LedgerAccount(it.id, it.initialBalanceCents, it.includeInTotal)
        }
        val records = back.payload.records.map {
            com.zwz.offlineledger.data.model.LedgerRecord(
                it.id, com.zwz.offlineledger.data.model.RecordType.valueOf(it.type),
                it.amountCents, it.fromAccountId, it.toAccountId,
                it.categoryId, it.rootCategoryId, it.excludeFromStats
            )
        }
        val adjustments = back.payload.balanceAdjustments.map {
            com.zwz.offlineledger.data.model.LedgerAdjustment(it.accountId, it.deltaCents, !it.isRevoked)
        }
        val balances = LedgerEngine.computeBalances(accounts, records, adjustments)
        assertEquals(99_999L, balances[1])
        assertEquals(-3_000L, balances[2])
    }

    @Test
    fun `stage0 header fields are unchanged and still parseable`() {
        // 阶段 0 的 BackupEnvelope 是扁平结构；四项头部一个字没变，
        // 阶段 1 只是新增了顶层 BackupFile(envelope, payload)。
        val legacy = """{"schemaVersion":1,"exportedAtEpochMilli":1,"currencyCode":"HKD","counts":{"accounts":0,"categories":0,"records":0,"balanceAdjustments":0}}"""
        val envelope = BackupExporter.json.decodeFromString<com.zwz.offlineledger.backup.dto.BackupEnvelope>(legacy)
        assertEquals(1, envelope.schemaVersion)
        assertEquals(1L, envelope.exportedAtEpochMilli)
        assertEquals("HKD", envelope.currencyCode)
    }

    @Test
    fun `backup file with empty payload is parseable`() {
        val empty = """{"envelope":{"schemaVersion":1,"exportedAtEpochMilli":1,"currencyCode":"HKD"},"payload":{}}"""
        val back = BackupImporter.decode(empty)
        assertEquals(1, back.envelope.schemaVersion)
        assertEquals(0, back.payload.records.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `future schema version is rejected`() {
        val future = """{"envelope":{"schemaVersion":999,"exportedAtEpochMilli":1,"currencyCode":"HKD"},"payload":{}}"""
        BackupImporter.decode(future)
    }
}
