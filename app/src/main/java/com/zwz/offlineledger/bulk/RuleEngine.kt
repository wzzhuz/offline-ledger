package com.zwz.offlineledger.bulk

/** 第 36 批：规则 2.0 纯 JVM 求值内核。落库事务、预览 UI 与末尾重算由上层负责。 */
object RuleEngine {

    private fun valueOf(t: BulkTarget, f: RuleField): String = when (f) {
        RuleField.COUNTERPARTY -> t.counterparty
        RuleField.NOTE -> t.note
        RuleField.CATEGORY -> t.categoryId?.toString() ?: ""
        RuleField.AMOUNT -> t.amountCents.toString()
        RuleField.DATE -> t.bookedAtEpochMilli.toString()
        RuleField.ACCOUNT -> t.accountId?.toString() ?: ""
        RuleField.CURRENCY -> t.currencyCode
        RuleField.IMPORT_SOURCE -> t.importSource
        RuleField.EXCLUDED -> t.excludedFromTotals.toString()
    }

    private fun rawMatch(t: BulkTarget, c: RuleCondition): Boolean {
        // 第 37 批：分类 / 账户是 ID 型字段，值控件给的是「一级 / 二级」显示名，
        // MUST 按 id 判定，不能拿显示名去比 categoryId.toString()，否则永远不匹配。
        if (c.field == RuleField.CATEGORY && c.categoryId != null) {
            return when (c.op) {
                RuleOp.EQUALS -> t.categoryId == c.categoryId
                RuleOp.NOT_EQUALS -> t.categoryId != c.categoryId
                RuleOp.EMPTY -> t.categoryId == null
                else -> false
            }
        }
        if (c.field == RuleField.ACCOUNT && c.accountId != null) {
            return when (c.op) {
                RuleOp.EQUALS -> t.accountId == c.accountId
                RuleOp.NOT_EQUALS -> t.accountId != c.accountId
                RuleOp.EMPTY -> t.accountId == null
                else -> false
            }
        }
        // 空关键词不得命中全库；EMPTY 与 ID / 区间型条件由各自分支处理。
        if (c.op != RuleOp.EMPTY && c.value.isBlank() &&
            c.categoryId == null && c.accountId == null &&
            c.minAmountCents == null && c.maxAmountCents == null &&
            c.fromEpochMilli == null && c.toEpochMilli == null
        ) return false
        val min = c.minAmountCents
        val max = c.maxAmountCents
        val from = c.fromEpochMilli
        val to = c.toEpochMilli
        if (min != null && t.amountCents < min) return false
        if (max != null && t.amountCents > max) return false
        if (from != null && t.bookedAtEpochMilli < from) return false
        if (to != null && t.bookedAtEpochMilli > to) return false
        val s = valueOf(t, c.field)
        // 金额 / 日期用上下限表达；EQUALS 且填了区间时，区间已通过即命中。
        if (c.field == RuleField.AMOUNT && c.op == RuleOp.EQUALS &&
            (min != null || max != null)
        ) return true
        if (c.field == RuleField.DATE && c.op == RuleOp.EQUALS &&
            (from != null || to != null)
        ) return true
        return when (c.op) {
            RuleOp.CONTAINS -> s.contains(c.value, ignoreCase = true)
            RuleOp.EQUALS -> s.equals(c.value, ignoreCase = true)
            RuleOp.NOT_EQUALS -> !s.equals(c.value, ignoreCase = true)
            RuleOp.STARTS_WITH -> s.startsWith(c.value, ignoreCase = true)
            RuleOp.ENDS_WITH -> s.endsWith(c.value, ignoreCase = true)
            RuleOp.NOT_CONTAINS -> !s.contains(c.value, ignoreCase = true)
            RuleOp.EMPTY -> s.isBlank()
            RuleOp.REGEX -> try {
                Regex(c.value).containsMatchIn(s)
            } catch (e: Exception) {
                false
            }
        }
    }

    fun matches(t: BulkTarget, c: RuleCondition): Boolean {
        val r = rawMatch(t, c)
        return if (c.negate) !r else r
    }

    /** 无条件的规则刻意不命中任何记录，避免误改全库。 */
    fun evaluate(t: BulkTarget, rule: RuleDefinition): Boolean {
        if (!rule.enabled || rule.conditions.isEmpty()) return false
        val rs = rule.conditions.map { matches(t, it) }
        return if (rule.matchMode == "ANY") rs.any { it } else rs.all { it }
    }

    fun applyRule(t: BulkTarget, rule: RuleDefinition): BulkTarget {
        var cur = t
        for (a in rule.actions) {
            cur = when (a.kind) {
                RuleActionKind.SET_CATEGORY -> cur.copy(categoryId = a.categoryId)
                RuleActionKind.RENAME_COUNTERPARTY -> cur.copy(counterparty = a.text)
                RuleActionKind.REPLACE_NOTE -> cur.copy(
                    note = if (a.from.isEmpty()) cur.note else cur.note.replace(a.from, a.to)
                )
                RuleActionKind.SET_EXCLUDED -> cur.copy(excludedFromTotals = a.excluded)
                RuleActionKind.APPEND_NOTE -> cur.copy(note = cur.note + a.text)
            }
        }
        return cur
    }

    /** 规则组按 sortOrder 有序执行；后一条看到的是前一条已改过的值。 */
    fun applyGroup(
        targets: List<BulkTarget>,
        rules: List<RuleDefinition>,
        groupEnabled: Boolean = true
    ): List<BulkChange> {
        if (!groupEnabled) return emptyList()
        val ordered = rules.filter { it.enabled }.sortedBy { it.sortOrder }
        if (ordered.isEmpty()) return emptyList()
        return targets.map { before ->
            var cur = before
            for (r in ordered) {
                if (evaluate(cur, r)) cur = applyRule(cur, r)
            }
            BulkChange(before, cur)
        }.filter { it.before != it.after }
    }

    data class MigrationHit(val index: Int, val hits: Int, val conflicts: List<Int>)

    private fun migrationMatches(t: BulkTarget, m: CategoryMigration): Boolean {
        if (m.toCategoryId == null) return false
        val byCat = m.fromCategoryId?.let { t.categoryId == it } ?: false
        val byCp = m.counterpartyContains.isNotBlank() &&
            t.counterparty.contains(m.counterpartyContains, ignoreCase = true)
        return byCat || byCp
    }

    /** 每组命中数 + 与其他组重叠的冲突下标；hits==0 即未命中。 */
    fun migrationReport(
        targets: List<BulkTarget>,
        mappings: List<CategoryMigration>
    ): List<MigrationHit> {
        val hitIds = mappings.map { m ->
            targets.filter { migrationMatches(it, m) }.map { it.id }.toSet()
        }
        return mappings.indices.map { i ->
            val conflicts = mappings.indices.filter { j ->
                j != i && hitIds[i].intersect(hitIds[j]).isNotEmpty()
            }
            MigrationHit(i, hitIds[i].size, conflicts)
        }
    }

    /** 冲突组按映射顺序首次命中优先，一条记录只改一次。 */
    fun migrate(
        targets: List<BulkTarget>,
        mappings: List<CategoryMigration>
    ): List<BulkChange> {
        val valid = mappings.filter { it.toCategoryId != null }
        return targets.map { before ->
            val m = valid.firstOrNull { migrationMatches(before, it) }
            val after = if (m == null) before else before.copy(categoryId = m.toCategoryId)
            BulkChange(before, after)
        }.filter { it.before != it.after }
    }

    fun revert(changes: List<BulkChange>): List<BulkTarget> = changes.map { it.before }
}
