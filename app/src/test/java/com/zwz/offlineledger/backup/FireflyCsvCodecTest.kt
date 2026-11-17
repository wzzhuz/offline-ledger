package com.zwz.offlineledger.backup

import com.zwz.offlineledger.backup.csv.FireflyCsvCodec
import com.zwz.offlineledger.backup.csv.FireflyKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM：Firefly III 6.6.x 导出解析、复式转单式、带符号金额与幂等键。 */
class FireflyCsvCodecTest {

    private val header = listOf(
        "journal_id", "group_title", "type", "amount", "currency_code",
        "foreign_currency_code", "description", "date",
        "source_name", "source_type", "destination_name", "destination_type",
        "category", "tags", "notes", "budget"
    ).joinToString(",")

    private fun bytes(vararg lines: String): ByteArray =
        (header + "\r\n" + lines.joinToString("\r\n") + "\r\n")
            .toByteArray(Charsets.UTF_8)

    private fun withdrawal() = listOf(
        "1937", "", "Withdrawal", "-112.59", "CNY", "",
        "盒马", "2026-11-15T11:22:00+08:00",
        "交通银行信用卡2203", "Asset account",
        "南京盒马网络科技有限公司", "Expense account",
        "活-购物", "", "", ""
    ).joinToString(",")

    private fun deposit() = listOf(
        "1842", "", "Deposit", "18739.25", "CNY", "",
        "工资", "2026-10-15T11:14:00+08:00",
        "外-华为技术有限公司南京研究所", "Revenue account",
        "中国银行9565", "Asset account",
        "入-工资", "", "", ""
    ).joinToString(",")

    private fun transfer() = listOf(
        "1916", "", "Transfer", "31.72", "CNY", "",
        "转账", "2026-11-08T16:54:00+08:00",
        "中国银行7375", "Asset account",
        "美团月付", "Asset account",
        "内流-转账", "", "", ""
    ).joinToString(",")

    private fun opening() = listOf(
        "8", "", "Opening balance", "-115.90", "CNY", "",
        "初始余额", "2025-08-23T00:00:00+08:00",
        "支付宝花呗", "Asset account",
        "初始余额账户", "Initial balance account",
        "", "", "", ""
    ).joinToString(",")

    @Test
    fun parses_and_maps_four_kinds() {
        val parsed = FireflyCsvCodec.parse(bytes(withdrawal(), deposit(), transfer(), opening()))
        assertTrue(parsed.errors.toString(), parsed.ok)
        assertEquals(4, parsed.rows.size)
        val plan = FireflyCsvCodec.buildPlan(parsed)
        assertEquals(3, plan.recordCount)
        assertEquals(1, plan.openingCount)
        assertEquals(0, plan.skippedCount)
        assertTrue(plan.accountNames.contains("交通银行信用卡2203"))
        assertTrue(plan.accountNames.contains("美团月付"))
    }

    @Test
    fun withdrawal_amount_is_abs_and_counterparty_is_destination() {
        val plan = FireflyCsvCodec.buildPlan(FireflyCsvCodec.parse(bytes(withdrawal())))
        val e = plan.entries.single()
        assertEquals(FireflyKind.EXPENSE, e.kind)
        assertEquals("112.59", e.amountYuan)
        assertEquals("南京盒马网络科技有限公司", e.counterparty)
        assertEquals("EXPENSE|活|购物", e.categoryKey)
        assertTrue(e.localDate == 20261115)
        assertTrue(e.yearMonth == 202611)
    }

    @Test
    fun deposit_uses_destination_account_and_source_as_payer() {
        val plan = FireflyCsvCodec.buildPlan(FireflyCsvCodec.parse(bytes(deposit())))
        val e = plan.entries.single()
        assertEquals(FireflyKind.INCOME, e.kind)
        assertEquals("中国银行9565", e.accountName)
        assertEquals("外-华为技术有限公司南京研究所", e.counterparty)
        assertEquals("INCOME|入|工资", e.categoryKey)
    }

    @Test
    fun transfer_has_both_accounts_and_no_counterparty() {
        val plan = FireflyCsvCodec.buildPlan(FireflyCsvCodec.parse(bytes(transfer())))
        val e = plan.entries.single()
        assertEquals(FireflyKind.TRANSFER, e.kind)
        assertEquals("中国银行7375", e.accountName)
        assertEquals("美团月付", e.toAccountName)
        assertEquals("", e.counterparty)
        assertEquals("", e.categoryKey)
        assertTrue(e.note.contains("FF3分类"))
    }

    @Test
    fun opening_keeps_negative_sign() {
        val plan = FireflyCsvCodec.buildPlan(FireflyCsvCodec.parse(bytes(opening())))
        val e = plan.entries.single()
        assertEquals(FireflyKind.OPENING, e.kind)
        assertEquals("-115.90", e.amountYuan)
        assertEquals("支付宝花呗", e.accountName)
    }

    @Test
    fun journal_id_is_embedded_in_note_for_idempotent_import() {
        val plan = FireflyCsvCodec.buildPlan(FireflyCsvCodec.parse(bytes(withdrawal())))
        assertTrue(plan.entries.single().note.contains("ff3:1937"))
    }

    @Test
    fun one_bad_row_discards_whole_plan() {
        val bad = withdrawal().replace("-112.59", "oops")
        val parsed = FireflyCsvCodec.parse(bytes(withdrawal(), bad))
        assertFalse(parsed.ok)
        assertTrue(parsed.errors.any { it.contains("第 3 行") })
    }

    @Test
    fun rejects_non_firefly_csv() {
        val parsed = FireflyCsvCodec.parse("date,type,amount\r\n2026-11-15,EXPENSE,1.00\r\n".toByteArray())
        assertFalse(parsed.ok)
        assertTrue(parsed.errors.any { it.contains("不是 Firefly III") })
    }

    @Test
    fun infer_account_type() {
        assertEquals("LIABILITY", FireflyCsvCodec.inferAccountType("交通银行信用卡2203"))
        assertEquals("PREPAID", FireflyCsvCodec.inferAccountType("中国石化加油卡-5663"))
        assertEquals("INVESTMENT", FireflyCsvCodec.inferAccountType("东兴证券资金"))
        assertEquals("ASSET", FireflyCsvCodec.inferAccountType("中国银行7375"))
    }
}
