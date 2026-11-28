package com.zwz.offlineledger.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 91 批：虚拟账本定义的纯 JVM 契约，不依赖 Android framework。 */
class VirtualBookCodecTest {

    @Test
    fun emptyRawDecodesToEmptyList() {
        assertTrue(VirtualBookCodec.decode("").isEmpty())
        assertTrue(VirtualBookCodec.decode("  ").isEmpty())
    }

    @Test
    fun roundTripKeepsTagStableAcrossRename() {
        val a = VirtualBook("vb-1", "结婚礼金", VirtualBookCodec.tagFor("结婚礼金"))
        val b = VirtualBook("vb-2", "装修", VirtualBookCodec.tagFor("装修"), archived = true)
        val restored = VirtualBookCodec.decode(VirtualBookCodec.encode(listOf(a, b)))
        assertEquals(2, restored.size)
        assertEquals(VirtualBookCodec.tagFor("结婚礼金"), restored[0].tagName)
        // 改名不改 tagName：历史流水仍归入同一视图
        val renamed = VirtualBookCodec.normalize(listOf(restored[0].copy(name = "婚礼往来"), restored[1]))
        assertEquals(VirtualBookCodec.tagFor("结婚礼金"), renamed[0].tagName)
        assertEquals("婚礼往来", renamed[0].name)
        assertTrue(renamed[1].archived)
    }

    @Test
    fun normalizeRejectsDuplicatesAndBlankNamesAndIllegalTags() {
        val raw = listOf(
            VirtualBook("vb-1", "  礼金  ", ""),
            VirtualBook("vb-1", "重复", "台账:重复"),
            VirtualBook("vb-2", "   ", "台账:空"),
            VirtualBook("vb-3", "装修", "普通标签")
        )
        val n = VirtualBookCodec.normalize(raw)
        assertEquals(2, n.size)
        assertEquals("礼金", n[0].name)
        assertEquals("台账:礼金", n[0].tagName)
        assertEquals("台账:装修", n[1].tagName)
        assertEquals(0, n[0].sortOrder)
        assertEquals(1, n[1].sortOrder)
    }

    @Test
    fun decodeSkipsCorruptedSegments() {
        val good = VirtualBook("vb-1", "礼金", VirtualBookCodec.tagFor("礼金"))
        val raw = VirtualBookCodec.encode(listOf(good)) + "\u0002" + "broken-segment"
        assertEquals(1, VirtualBookCodec.decode(raw).size)
    }
}
