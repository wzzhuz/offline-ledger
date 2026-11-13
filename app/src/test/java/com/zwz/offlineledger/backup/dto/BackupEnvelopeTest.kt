package com.zwz.offlineledger.backup.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEnvelopeTest {

    @Test
    fun `round trip keeps header fields`() {
        val envelope = BackupEnvelope(
            exportedAtEpochMilli = 123456789L,
            currencyCode = "HKD",
            counts = BackupCounts(accounts = 3, records = 10)
        )
        val json = Json.encodeToString(envelope)
        val back = Json.decodeFromString<BackupEnvelope>(json)

        assertEquals(BackupSchema.CURRENT, back.schemaVersion)
        // 不手写 CURRENT 常数：阶段 6a 已升到 2，以后还会再升。
        // 真正该锁住的是「v1 备份仍可解析」这条长期约束。
        assertEquals(1, BackupSchema.MIN_SUPPORTED)
        assertTrue(BackupSchema.CURRENT > BackupSchema.MIN_SUPPORTED)
        assertEquals("HKD", back.currencyCode)
        assertEquals(123456789L, back.exportedAtEpochMilli)
        assertEquals(10, back.counts.records)
    }

    @Test
    fun `older schema version is parseable`() {
        val raw = """{"schemaVersion":1,"exportedAtEpochMilli":1,"currencyCode":"HKD"}"""
        val back = Json.decodeFromString<BackupEnvelope>(raw)
        assertEquals(1, back.schemaVersion)
        assertEquals(BackupCounts(), back.counts)
    }
}
