package com.zwz.offlineledger.quick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCaptureResolverTest {

    private val now = 1732000000000L

    @Test
    fun nodeSnapshotWinsOverOcr() {
        val node = ScreenCaptureNodeSnapshot(
            "com.eg.android.AlipayGphone", listOf("永辉超市", "¥38.50"), now
        )
        val out = QuickCaptureResolver.resolve(node, listOf("别的页面", "¥99.00"), null, now)
        assertTrue(out is CaptureOutcome.Success)
        assertEquals("节点文本", (out as CaptureOutcome.Success).source)
        assertEquals(3850L, out.bill.amountCents)
    }

    @Test
    fun expiredNodeFallsBackToOcr() {
        val node = ScreenCaptureNodeSnapshot(
            "com.eg.android.AlipayGphone", listOf("旧页面", "¥1.00"), now - 120_000L
        )
        val out = QuickCaptureResolver.resolve(node, listOf("新页面", "¥9.00"), null, now)
        assertTrue(out is CaptureOutcome.Success)
        assertEquals("OCR", (out as CaptureOutcome.Success).source)
        assertEquals(900L, out.bill.amountCents)
    }

    @Test
    fun secureWindowNeverSilentlyWrites() {
        val out = QuickCaptureResolver.resolve(null, null, 3, now)
        assertTrue(out is CaptureOutcome.NeedManual)
        assertTrue((out as CaptureOutcome.NeedManual).reason.contains("手动记账"))
    }

    @Test
    fun throttleErrorIsReadable() {
        val out = QuickCaptureResolver.resolve(null, null, 1, now)
        assertTrue((out as CaptureOutcome.NeedManual).reason.contains("间隔"))
    }

    @Test
    fun blankScreenNeedsManual() {
        val out = QuickCaptureResolver.resolve(null, listOf("交易成功"), null, now)
        assertTrue(out is CaptureOutcome.NeedManual)
    }
}
