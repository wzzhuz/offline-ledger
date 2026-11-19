package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvRebuildTest {

    private val own = (
        "date,type,amount,account,categoryRoot,category,note,currencyCode," +
            "counterparty,importSource,sourceId\r\n" +
            "2026-11-15,EXPENSE,38.50,招行储蓄,餐饮,外卖,午餐,CNY,盒马,CSV,k1\r\n" +
            "2026-11-16,TRANSFER,100.00,招行储蓄,,,转出,CNY,,CSV,k2\r\n" +
            "2026-11-17,INCOME,500.00,微信,工资,,月薪,CNY,,CSV,k3\r\n"
        ).toByteArray()

    private val thirdParty = (
        "交易时间,交易分类,交易对方,金额\r\n" +
            "2026-11-15 12:00,餐饮美食,盒马,-38.50\r\n"
        ).toByteArray()

    @Test
    fun `own export header is detected by columns not filename`() {
        assertEquals(CsvFormat.OWN, CsvCodec.detectFormat(own))
        assertEquals(CsvFormat.OTHER, CsvCodec.detectFormat(thirdParty))
    }

    @Test
    fun `rebuild keeps source identity and reports skipped transfer rows`() {
        val result = CsvCodec.parseRebuild(own)
        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.rows.size)
        assertEquals("k1", result.rows[0].sourceId)
        assertEquals("CSV", result.rows[0].importSource)
        assertEquals(1, result.notices.size)
        assertTrue(result.notices[0].contains("TRANSFER"))
    }

    @Test
    fun `import parser rejects the own export as expected`() {
        val result = CsvCodec.parseImport(own)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `rebuild refuses third party bill`() {
        val result = CsvCodec.parseRebuild(thirdParty)
        assertTrue(result.errors.isNotEmpty())
    }
}
