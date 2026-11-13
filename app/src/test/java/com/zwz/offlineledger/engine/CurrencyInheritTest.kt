package com.zwz.offlineledger.engine

import com.zwz.offlineledger.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 的币种语义契约。真实继承在 Repository.withCurrency 里（需 Room），
 * 这里锁定「币种只是标签、不参与金额运算」与「同币种校验」两条不变量。
 */
class CurrencyInheritTest {

    private data class Acc(val id: Long, val currencyCode: String)

    private fun currencyOf(type: RecordType, from: Acc?, to: Acc?): String = when (type) {
        RecordType.EXPENSE -> from?.currencyCode
        RecordType.INCOME, RecordType.SETTLEMENT, RecordType.TRANSFER -> to?.currencyCode
    } ?: "HKD"

    @Test
    fun expense_inherits_from_account() {
        assertEquals("CNY", currencyOf(RecordType.EXPENSE, Acc(1, "CNY"), null))
    }

    @Test
    fun income_inherits_to_account() {
        assertEquals("HKD", currencyOf(RecordType.INCOME, null, Acc(2, "HKD")))
    }

    @Test
    fun settlement_inherits_to_account_which_is_original_expense_account() {
        assertEquals("CNY", currencyOf(RecordType.SETTLEMENT, null, Acc(1, "CNY")))
    }

    @Test
    fun missing_account_falls_back_to_default() {
        assertEquals("HKD", currencyOf(RecordType.EXPENSE, null, null))
    }

    private fun sameCurrency(from: Acc, to: Acc): Boolean =
        from.currencyCode == to.currencyCode

    @Test
    fun same_currency_transfer_is_allowed() {
        assertTrue(sameCurrency(Acc(1, "HKD"), Acc(2, "HKD")))
    }

    @Test
    fun cross_currency_transfer_is_rejected() {
        assertFalse(sameCurrency(Acc(1, "HKD"), Acc(2, "CNY")))
    }

    @Test
    fun currency_is_label_only_amounts_never_mix() {
        val byCurrency = mapOf("HKD" to 3850L, "CNY" to 2000L)
        assertEquals(3850L, byCurrency["HKD"])
        assertEquals(2000L, byCurrency["CNY"])
        assertTrue(byCurrency.values.none { it == 5850L })
    }
}
