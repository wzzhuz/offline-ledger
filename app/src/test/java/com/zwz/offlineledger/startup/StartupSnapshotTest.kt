package com.zwz.offlineledger.startup

import com.zwz.offlineledger.data.startup.StartupPhase
import com.zwz.offlineledger.data.startup.SnapshotRow
import com.zwz.offlineledger.data.startup.StartupPhaseMachine
import com.zwz.offlineledger.data.startup.StartupSnapshot
import com.zwz.offlineledger.data.startup.StartupSnapshotBus
import com.zwz.offlineledger.data.startup.StartupSnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSnapshotTest {

    private val ym = 202611
    private val dbVersion = 5

    private fun snapshot(
        schema: Int = StartupSnapshotCodec.SCHEMA_VERSION,
        db: Int = dbVersion,
        month: Int = ym,
        rows: List<SnapshotRow> = listOf(SnapshotRow(id = 1L, localDate = 20261122))
    ) = StartupSnapshot(
        schemaVersion = schema,
        dbVersion = db,
        yearMonth = month,
        granularity = "MONTH",
        savedAtEpochMilli = 1_763_000_000_000L,
        rows = rows
    )

    private fun usable(
        s: StartupSnapshot,
        unlocked: Boolean = true,
        defaultFilter: Boolean = true,
        defaultGranularity: Boolean = true
    ) = StartupSnapshotCodec.isUsable(
        snapshot = s,
        currentYearMonth = ym,
        expectedDbVersion = dbVersion,
        unlocked = unlocked,
        filterIsDefault = defaultFilter,
        isDefaultGranularity = defaultGranularity
    )

    @Test
    fun `round trip keeps rows`() {
        val s = snapshot(rows = (1..3).map {
            SnapshotRow(id = it.toLong(), type = "EXPENSE", amountCents = it * 100L,
                localDate = 20261122, description = "第 $it 笔")
        })
        val decoded = StartupSnapshotCodec.decode(StartupSnapshotCodec.encode(s))
        assertEquals(s, decoded)
        assertEquals("第 2 笔", decoded!!.rows[1].description)
    }

    @Test
    fun `broken json is discarded instead of crashing`() {
        assertNull(StartupSnapshotCodec.decode("{not json"))
    }

    @Test
    fun `trim keeps at most 60 rows`() {
        val rows = (1..200).map { SnapshotRow(id = it.toLong()) }
        assertEquals(60, StartupSnapshotCodec.trim(rows).size)
    }

    @Test
    fun `full match is usable`() {
        assertTrue(usable(snapshot()))
    }

    @Test
    fun `reject when locked`() {
        assertFalse(usable(snapshot(), unlocked = false))
    }

    @Test
    fun `reject when filter is not default`() {
        assertFalse(usable(snapshot(), defaultFilter = false))
    }

    @Test
    fun `reject when granularity is not default`() {
        assertFalse(usable(snapshot(), defaultGranularity = false))
    }

    @Test
    fun `reject stale month`() {
        assertFalse(usable(snapshot(month = 202610)))
    }

    @Test
    fun `reject db version mismatch`() {
        assertFalse(usable(snapshot(db = 4)))
    }

    @Test
    fun `reject snapshot format version mismatch`() {
        assertFalse(usable(snapshot(schema = 999)))
    }

    @Test
    fun `reject empty or oversized rows`() {
        assertFalse(usable(snapshot(rows = emptyList())))
        assertFalse(usable(snapshot(rows = (1..61).map { SnapshotRow(id = it.toLong()) })))
    }

    @Test
    fun `switch only after a real loading has been observed`() {
        // 刚 collect 时可能短暂 NotLoading，不能立刻换源
        assertFalse(StartupPhaseMachine.shouldSwitch(previouslySaw = false, currentIsLoading = false))
        assertFalse(StartupPhaseMachine.shouldSwitch(previouslySaw = false, currentIsLoading = true))
        // 加载中不得切
        assertFalse(StartupPhaseMachine.shouldSwitch(previouslySaw = true, currentIsLoading = true))
        // 真正加载过且 refresh 结束才切
        assertTrue(StartupPhaseMachine.shouldSwitch(previouslySaw = true, currentIsLoading = false))
    }

    @Test
    fun `sawLoading sticks once loading was seen`() {
        assertFalse(StartupPhaseMachine.sawLoading(false, false))
        assertTrue(StartupPhaseMachine.sawLoading(false, true))
        assertTrue(StartupPhaseMachine.sawLoading(true, false))
    }

    @Test
    fun `write transaction invalidates the disk snapshot`() {
        var deleted = 0
        StartupSnapshotBus.register { deleted += 1 }
        StartupSnapshotBus.invalidate()
        assertEquals(1, deleted)
        StartupSnapshotBus.invalidate()
        assertEquals(2, deleted)
    }

    @Test
    fun `phase enum has exactly three branches`() {
        assertEquals(3, StartupPhase.values().size)
    }
}
