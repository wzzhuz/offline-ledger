package com.zwz.offlineledger.quick

import com.zwz.offlineledger.bulk.BulkTarget
import com.zwz.offlineledger.bulk.RuleDefinition
import com.zwz.offlineledger.bulk.RuleEngine

/**
 * 第 49 批：识屏结果过已有规则引擎自动分类。
 * 复用 v0.2.18 的 RuleEngine，不新开写入口、不改求值逻辑。
 */
object QuickEntryEngine {

    fun targetOf(bill: ParsedBill, accountId: Long?, currencyCode: String): BulkTarget =
        BulkTarget(
            id = 0L,
            counterparty = bill.counterparty,
            note = bill.note,
            categoryId = null,
            amountCents = bill.amountCents,
            bookedAtEpochMilli = bill.timestamp,
            excludedFromTotals = false,
            accountId = accountId,
            currencyCode = currencyCode,
            importSource = "screen",
            description = bill.description
        )

    /** 规则组按 sortOrder 有序执行，后一条看到前一条改过的值。 */
    fun applyRules(target: BulkTarget, rules: List<RuleDefinition>): BulkTarget {
        var cur = target
        for (rule in rules.filter { it.enabled }.sortedBy { it.sortOrder }) {
            if (RuleEngine.evaluate(cur, rule)) {
                cur = RuleEngine.applyRule(cur, rule)
            }
        }
        return cur
    }

    const val IMPORT_SOURCE = "screen"
}
