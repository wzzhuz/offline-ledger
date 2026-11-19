package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.csv.CsvCodec
import com.zwz.offlineledger.backup.csv.CsvRow
import com.zwz.offlineledger.backup.csv.FireflyCsvCodec
import com.zwz.offlineledger.backup.csv.FireflyParseResult
import com.zwz.offlineledger.backup.csv.FireflyRow
import com.zwz.offlineledger.backup.dto.BackupRecord
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import com.zwz.offlineledger.bulk.BulkTarget
import com.zwz.offlineledger.bulk.RuleActionKind
import com.zwz.offlineledger.bulk.RuleActionSpec
import com.zwz.offlineledger.bulk.RuleCondition
import com.zwz.offlineledger.bulk.RuleDefinition
import com.zwz.offlineledger.bulk.RuleEngine
import com.zwz.offlineledger.bulk.RuleField
import com.zwz.offlineledger.bulk.RuleOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 43 批：FF3 description 独立列的三条契约（纯 JVM）。 */
class DescriptionRoundTripTest {

    @Test
    fun fireflyDescriptionAndNotesAreSeparate() {
        val row = FireflyRow(
            lineNo = 2, journalId = "j1", groupId = "", groupTitle = "",
            type = "Withdrawal", amountRaw = "-12.00", currencyCode = "CNY",
            foreignCurrencyCode = "", foreignAmount = "", description = "牛奶鸡蛋",
            dateRaw = "2026-11-19T10:00:00+08:00", sourceName = "招商银行",
            sourceType = "", destinationName = "虚拟-不记录", destinationType = "",
            categoryRaw = "", tags = "", notes = "另有信息", budget = ""
        )
        val plan = FireflyCsvCodec.buildPlan(
            FireflyParseResult(listOf(row), emptyList(), emptyList())
        )
        assertEquals("牛奶鸡蛋", plan.entries[0].description)
        assertEquals("另有信息", plan.entries[0].note)
        assertEquals("虚拟-不记录", plan.entries[0].counterparty)
    }

    @Test
    fun csvExportThenImportKeepsDescription() {
        val row = CsvRow(
            date = "2026-11-19", type = "EXPENSE", amountYuan = "12.00",
            accountName = "招商银行", categoryRoot = "三餐", category = "外卖",
            note = "另有信息", currencyCode = "CNY", counterparty = "盒马",
            importSource = "FIREFLY_III", sourceId = "j1", description = "牛奶鸡蛋"
        )
        val parsed = CsvCodec.parseImport(CsvCodec.export(listOf(row)))
        assertTrue(parsed.errors.toString(), parsed.ok)
        assertEquals("牛奶鸡蛋", parsed.rows[0].description)
        assertEquals("另有信息", parsed.rows[0].note)
        assertEquals("盒马", parsed.rows[0].counterparty)
    }

    @Test
    fun oldCsvWithoutDescriptionDefaultsToEmpty() {
        val legacy = "date,type,amount,account,categoryRoot,category,note,currencyCode,counterparty,importSource,sourceId\r\n" +
                "2026-11-19,EXPENSE,12.00,招商银行,三餐,外卖,另有信息,CNY,盒马,,\r\n"
        val parsed = CsvCodec.parseImport(legacy.toByteArray())
        assertTrue(parsed.errors.toString(), parsed.ok)
        assertEquals("", parsed.rows[0].description)
    }

    @Test
    fun backupRoundTripKeepsDescription() {
        val rec = BackupRecord(
            id = 1, type = "EXPENSE", amountCents = 1200, timestamp = 0L,
            localDate = 20261119, yearMonth = 202611, description = "牛奶鸡蛋"
        )
        val snap = LedgerSnapshot(
            accounts = emptyList(), categories = emptyList(),
            records = listOf(rec), balanceAdjustments = emptyList()
        )
        val decoded = BackupImporter.decode(BackupExporter.encode(snap))
        assertEquals("牛奶鸡蛋", decoded.payload.records[0].description)
    }

    @Test
    fun legacyBackupWithoutDescriptionKeyIsCompatible() {
        val legacy = """{"envelope":{"schemaVersion":4,"exportedAtEpochMilli":0,"currencyCode":"CNY"},""" +
                """"payload":{"records":[{"id":1,"type":"EXPENSE","amountCents":1200,""" +
                """"timestamp":0,"localDate":20261119,"yearMonth":202611,"note":"旧备注"}]}}"""
        val decoded = BackupImporter.decode(legacy)
        assertEquals("", decoded.payload.records[0].description)
        assertEquals("旧备注", decoded.payload.records[0].note)
    }

    @Test
    fun ruleCanMatchAndSetDescription() {
        val t = BulkTarget(
            id = 1, counterparty = "虚拟-不记录", note = "旧备注", categoryId = null,
            amountCents = 1200, bookedAtEpochMilli = 0L, excludedFromTotals = false,
            description = "超市购物"
        )
        val cond = RuleCondition(field = RuleField.DESCRIPTION, op = RuleOp.CONTAINS, value = "超市")
        assertTrue(RuleEngine.matches(t, cond))
        val emptyCond = RuleCondition(field = RuleField.DESCRIPTION, op = RuleOp.EMPTY)
        assertTrue(!RuleEngine.matches(t, emptyCond))
        val rule = RuleDefinition(
            conditions = listOf(cond),
            actions = listOf(RuleActionSpec(kind = RuleActionKind.SET_DESCRIPTION, text = "牛奶鸡蛋"))
        )
        val after = RuleEngine.applyRule(t, rule)
        assertEquals("牛奶鸡蛋", after.description)
        assertEquals("虚拟-不记录", after.counterparty)
        assertEquals("旧备注", after.note)
    }
}
