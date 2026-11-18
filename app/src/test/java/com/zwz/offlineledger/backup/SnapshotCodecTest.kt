package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.snapshot.SnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotCodecTest {

    @Test
    fun `checksum is stable and detects tampering`() {
        val blob = "hello".toByteArray()
        val sum = SnapshotCodec.checksum(blob)
        assertEquals(64, sum.length)
        assertTrue(SnapshotCodec.verify(blob, sum))
        assertFalse(SnapshotCodec.verify("hello!".toByteArray(), sum))
    }

    @Test
    fun `temp files are pruned first and keep newest finals`() {
        val names = listOf(
            "offline-ledger-20261118-120000-abc.ledgerbak",
            "offline-ledger-20261118-130000-abc.ledgerbak",
            "offline-ledger-20261118-140000-abc.ledgerbak",
            "offline-ledger-20261118-150000-abc.ledgerbak.tmp"
        )
        val drop = SnapshotCodec.pruneCandidates(names, keep = 2)
        assertTrue(drop.contains(names[3]))
        assertTrue(drop.contains(names[0]))
        assertFalse(drop.contains(names[2]))
    }

    @Test
    fun `file name carries stamp and short sha`() {
        val n = SnapshotCodec.fileName(1_763_400_000_000L, "d7bafd66e6e7")
        assertTrue(n.endsWith(SnapshotCodec.EXTENSION))
        assertTrue(n.contains("d7bafd66e6e7"))
    }
}
