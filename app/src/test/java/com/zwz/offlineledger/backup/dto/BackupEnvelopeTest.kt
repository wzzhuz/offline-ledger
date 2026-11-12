package com.zwz.offlineledger.backup.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
        assertEquals(1, BackupSchema.CURRENT)
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
