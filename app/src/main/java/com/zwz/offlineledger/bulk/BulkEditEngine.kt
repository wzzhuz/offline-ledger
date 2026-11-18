package com.zwz.offlineledger.bulk

/**
 * 批量规整的纯 JVM 内核。落库事务、预览 UI 与末尾重算由上层负责；
 * 本类只做条件匹配、动作应用与逐字段撤销，便于单测锁死契约。
 */
data class BulkTarget(
    val id: Long,
    val counterparty: String,
    val note: String,
    val categoryId: Long?,
    val amountCents: Long,
    val bookedAtEpochMilli: Long,
    val excludedFromTotals: Boolean
)

data class BulkConditions(
    val counterpartyContains: String? = null,
    val counterpartyEquals: String? = null,
    val noteContains: String? = null,
    val categoryId: Long? = null,
    val minAmountCents: Long? = null,
    val maxAmountCents: Long? = null,
    val fromEpochMilli: Long? = null,
    val toEpochMilli: Long? = null
) {
    fun matches(t: BulkTarget): Boolean {
        if (counterpartyContains != null && !t.counterparty.contains(counterpartyContains, true)) return false
        if (counterpartyEquals != null && !t.counterparty.equals(counterpartyEquals, true)) return false
        if (noteContains != null && !t.note.contains(noteContains, true)) return false
        if (categoryId != null && t.categoryId != categoryId) return false
        if (minAmountCents != null && t.amountCents < minAmountCents) return false
        if (maxAmountCents != null && t.amountCents > maxAmountCents) return false
        if (fromEpochMilli != null && t.bookedAtEpochMilli < fromEpochMilli) return false
        if (toEpochMilli != null && t.bookedAtEpochMilli > toEpochMilli) return false
        return true
    }
}

sealed interface BulkAction {
    data class SetCategory(val categoryId: Long?) : BulkAction
    data class RenameCounterparty(val value: String) : BulkAction
    data class ReplaceInNote(val from: String, val to: String) : BulkAction
    data class SetExcluded(val value: Boolean) : BulkAction
}

data class BulkChange(val before: BulkTarget, val after: BulkTarget)

object BulkEditEngine {

    fun apply(
        targets: List<BulkTarget>,
        conditions: BulkConditions,
        actions: List<BulkAction>
    ): List<BulkChange> = targets.filter { conditions.matches(it) }.map { before ->
        var cur = before
        for (a in actions) {
            cur = when (a) {
                is BulkAction.SetCategory -> cur.copy(categoryId = a.categoryId)
                is BulkAction.RenameCounterparty -> cur.copy(counterparty = a.value)
                is BulkAction.ReplaceInNote -> cur.copy(
                    note = if (a.from.isEmpty()) cur.note else cur.note.replace(a.from, a.to)
                )
                is BulkAction.SetExcluded -> cur.copy(excludedFromTotals = a.value)
            }
        }
        BulkChange(before, cur)
    }

    /** 撤销只还原真正被改动的记录，逐字段还原，不依赖当前库状态。 */
    fun revert(changes: List<BulkChange>): List<BulkTarget> = changes.map { it.before }
}
