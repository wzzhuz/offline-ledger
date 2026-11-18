package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvRow
import com.zwz.offlineledger.engine.ChartData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 23 批：来源列兼容、稳定色板、有符号归一化与多年补全，全部纯 JVM。 */
class ImportIdentityTest {

    @Test
    fun oldNineColumnCsvStillParses() {
        val csv = "date,type,amount,account,categoryRoot,category,note,currencyCode,counterparty\r\n" +
            "2026-11-15,EXPENSE,38.50,A,R,C,lunch,CNY,Shop\r\n"
        val r = CsvCodec.parseImport(csv.toByteArray(Charsets.UTF_8))
        assertTrue(r.ok)
        assertEquals(1, r.rows.size)
        assertEquals("", r.rows[0].importSource)
        assertEquals("", r.rows[0].sourceId)
    }

    @Test
    fun newElevenColumnsRoundTrip() {
        val row = CsvRow(
            "2026-11-15", "EXPENSE", "38.50", "A", "R", "C",
            "lunch", "CNY", "Shop", "FIREFLY_III", "1937"
        )
        val bytes = CsvCodec.export(listOf(row))
        val r = CsvCodec.parseImport(bytes)
        assertTrue(r.ok)
        assertEquals(1, r.rows.size)
        assertEquals("FIREFLY_III", r.rows[0].importSource)
        assertEquals("1937", r.rows[0].sourceId)
    }

    @Test
    fun chartPaletteIsStableByIdAndDistinct() {
        val a = ChartData.chartArgb(12L, 0)
        val b = ChartData.chartArgb(12L, 7)
        assertEquals(a, b)
        assertNotEquals(a, ChartData.chartArgb(13L, 0))
    }

    @Test
    fun signedNormalizationPutsZeroInTheMiddle() {
        val v = ChartData.normalizeSigned(listOf(-100L, 0L, 100L), 100L)
        assertEquals(3, v.size)
        assertEquals(0f, v[0], 0.0001f)
        assertEquals(0.5f, v[1], 0.0001f)
        assertEquals(1f, v[2], 0.0001f)
        assertEquals(5, ChartData.yAxisLabelsSigned(10000L).size)
    }

    @Test
    fun yearlySeriesFillsMissingYears() {
        val ys = ChartData.fillYearlySeries(
            2026, 5, listOf(ChartData.YearPoint(2026, 10L, 5L))
        )
        assertEquals(5, ys.size)
        assertEquals(2022, ys[0].year)
        assertEquals(0L, ys[0].incomeCents)
        assertEquals(10L, ys[4].incomeCents)
    }
}
