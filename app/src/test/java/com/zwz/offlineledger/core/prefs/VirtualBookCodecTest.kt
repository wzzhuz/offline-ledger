package com.zwz.offlineledger.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ---- 第 94 批：场景模板与默认分类 ----

    @Test
    fun legacyFiveSegmentDataFallsBackToGiftTemplate() {
        // 第 91～93 批落盘格式：id / name / tag / archived / order
        val legacy = "vb-old\u0001结婚礼金\u0001台账:结婚礼金\u00010\u00010"
        val restored = VirtualBookCodec.normalize(VirtualBookCodec.decode(legacy))
        assertEquals(1, restored.size)
        assertEquals(VirtualBookTemplate.GIFT, restored[0].spec)
        assertNull(restored[0].defaultCategoryId)
    }

    @Test
    fun templateAndDefaultCategoryRoundTrip() {
        val a = VirtualBook(
            "vb-1", "装修", VirtualBookCodec.tagFor("装修"),
            template = VirtualBookTemplate.PROJECT.id,
            defaultCategoryId = 5009L
        )
        val b = VirtualBook(
            "vb-2", "自定义台账", VirtualBookCodec.tagFor("自定义台账"),
            template = VirtualBookTemplate.CUSTOM.id
        )
        val restored = VirtualBookCodec.decode(VirtualBookCodec.encode(listOf(a, b)))
        assertEquals(VirtualBookTemplate.PROJECT, restored[0].spec)
        assertEquals(5009L, restored[0].defaultCategoryId)
        assertEquals(VirtualBookTemplate.CUSTOM, restored[1].spec)
        assertNull(restored[1].defaultCategoryId)
    }

    @Test
    fun illegalTemplateAndCategoryAreNormalized() {
        val raw = listOf(
            VirtualBook("vb-1", "脏数据", "台账:脏数据",
                template = "not-a-template", defaultCategoryId = -3L)
        )
        val n = VirtualBookCodec.normalize(raw)
        assertEquals(VirtualBookTemplate.GIFT, n[0].spec)
        assertNull(n[0].defaultCategoryId)
    }

    @Test
    fun giftTemplateCarriesBuiltinCategoryIdsWithoutUserSelection() {
        assertEquals(34_004L, VirtualBookTemplate.GIFT.categoryFor(true, null))
        assertEquals(9_001L, VirtualBookTemplate.GIFT.categoryFor(false, null))
        // 礼金模板忽略视图自定义默认分类，避免两套口径并存
        assertEquals(9_001L, VirtualBookTemplate.GIFT.categoryFor(false, 5009L))
    }

    @Test
    fun projectAndCustomUseViewsDefaultCategory() {
        assertEquals(5009L, VirtualBookTemplate.PROJECT.categoryFor(false, 5009L))
        assertNull(VirtualBookTemplate.PROJECT.categoryFor(false, null))
        assertEquals(1L, VirtualBookTemplate.CUSTOM.categoryFor(true, 1L))
    }

    @Test
    fun projectOnlyOffersExpenseAction() {
        assertNull(VirtualBookTemplate.PROJECT.incomeAction)
        assertEquals("记支出", VirtualBookTemplate.PROJECT.expenseAction)
        assertEquals("记收礼", VirtualBookTemplate.GIFT.incomeAction)
        assertEquals("记收入", VirtualBookTemplate.CUSTOM.incomeAction)
    }

    // ---- 第 95 批：删除只删定义，同名重建可重新聚合 ----

    @Test
    fun tagForIsDerivedFromNameSoRecreateReaggregates() {
        // 删除视图定义后流水仍带此标签；新建同名视图 tagFor(name) 必须得到同一串。
        val tag = VirtualBookCodec.tagFor("结婚礼金")
        val removed = VirtualBookCodec.normalize(emptyList<VirtualBook>())
        assertTrue(removed.isEmpty())
        val recreated = VirtualBook("vb-new", "结婚礼金", VirtualBookCodec.tagFor("结婚礼金"))
        assertEquals(tag, VirtualBookCodec.normalize(listOf(recreated))[0].tagName)
    }

    @Test
    fun removingDefinitionKeepsOtherBooksAndStableOrder() {
        val a = VirtualBook("vb-1", "礼金", VirtualBookCodec.tagFor("礼金"), sortOrder = 0)
        val b = VirtualBook("vb-2", "装修", VirtualBookCodec.tagFor("装修"), sortOrder = 1,
            template = VirtualBookTemplate.PROJECT.id)
        val c = VirtualBook("vb-3", "旅行", VirtualBookCodec.tagFor("旅行"), sortOrder = 2,
            template = VirtualBookTemplate.PROJECT.id)
        // 模拟 Store.remove：filterNot + normalize
        val kept = VirtualBookCodec.normalize(listOf(a, b, c).filterNot { it.id == "vb-2" })
        assertEquals(listOf("vb-1", "vb-3"), kept.map { it.id })
        assertEquals(listOf(0, 1), kept.map { it.sortOrder })
        assertEquals(VirtualBookTemplate.PROJECT, kept[1].spec)
    }
}
