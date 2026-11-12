package com.zwz.offlineledger.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LedgerClockTest {

    @Test
    fun `hong kong midnight belongs to local new year`() {
        // 2025-12-31T16:00:00Z == 2026-01-01 00:00 Asia/Hong_Kong
        val millis = Instant.parse("2025-12-31T16:00:00Z").toEpochMilli()
        val date = LedgerClock.fromEpochMilli(millis)
        assertEquals(20260101, date.localDate)
        assertEquals(202601, date.yearMonth)
        assertEquals(millis, date.timestamp)
    }

    @Test
    fun `utc month end stays in previous local month`() {
        // 2026-03-31T16:00:00Z == 2026-04-01 00:00 HKT
        val millis = Instant.parse("2026-03-31T16:00:00Z").toEpochMilli()
        val date = LedgerClock.fromEpochMilli(millis)
        assertEquals(20260401, date.localDate)
        assertEquals(202604, date.yearMonth)
    }
}
