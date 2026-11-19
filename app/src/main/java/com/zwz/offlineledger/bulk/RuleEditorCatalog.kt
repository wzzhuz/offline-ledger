package com.zwz.offlineledger.bulk

/**
 * 第 37 批：规则编辑器的目录。
 *
 * 引擎（RuleModels / RuleEngine）本来就支持「字段 + 操作符 + 值」的通用条件与多动作，
 * v0.2.18 的 UI 却只暴露了几个写死的槽位。本对象把「哪些字段可选、每个字段能用哪些
 * 操作符、值控件长什么样」收敛成单一来源，UI 与单测都读它，避免表单枚举与引擎枚举分叉。
 *
 * 纯 JVM、无 Android / Room 依赖。枚举追加值一律放在末尾，旧 JSON 按名反序列化不破。
 */
enum class RuleValueKind { TEXT, CATEGORY, ACCOUNT, AMOUNT_RANGE, DATE_RANGE, CHOICE, FLAG }

object RuleEditorCatalog {

    /** 一期常用字段，不引 Firefly III 的 ID / SEPA / 对账 / 创建更新时间等本项目没有的概念。 */
    fun fields(): List<RuleField> = listOf(
        RuleField.COUNTERPARTY,
        RuleField.CATEGORY,
        RuleField.NOTE,
        RuleField.AMOUNT,
        RuleField.ACCOUNT,
        RuleField.DATE,
        RuleField.CURRENCY,
        RuleField.IMPORT_SOURCE,
        RuleField.EXCLUDED
    )

    fun fieldLabel(f: RuleField): String = when (f) {
        RuleField.COUNTERPARTY -> "交易对方"
        RuleField.NOTE -> "备注"
        RuleField.CATEGORY -> "分类"
        RuleField.AMOUNT -> "金额"
        RuleField.DATE -> "日期"
        RuleField.ACCOUNT -> "账户"
        RuleField.CURRENCY -> "币种"
        RuleField.IMPORT_SOURCE -> "导入来源"
        RuleField.EXCLUDED -> "不计入收支"
    }

    /** 操作符按字段动态给出，不照抄 FF3 那个把「字段+操作符」合成一个超长单选的做法。 */
    fun opsFor(f: RuleField): List<RuleOp> = when (f) {
        RuleField.CATEGORY, RuleField.ACCOUNT ->
            listOf(RuleOp.EQUALS, RuleOp.NOT_EQUALS, RuleOp.EMPTY)
        RuleField.AMOUNT, RuleField.DATE ->
            listOf(RuleOp.EQUALS, RuleOp.EMPTY)
        RuleField.EXCLUDED -> listOf(RuleOp.EQUALS)
        else -> listOf(
            RuleOp.CONTAINS,
            RuleOp.EQUALS,
            RuleOp.STARTS_WITH,
            RuleOp.ENDS_WITH,
            RuleOp.NOT_CONTAINS,
            RuleOp.NOT_EQUALS,
            RuleOp.EMPTY,
            RuleOp.REGEX
        )
    }

    fun opLabel(op: RuleOp): String = when (op) {
        RuleOp.CONTAINS -> "包含"
        RuleOp.EQUALS -> "等于"
        RuleOp.STARTS_WITH -> "开头是"
        RuleOp.ENDS_WITH -> "结尾是"
        RuleOp.NOT_CONTAINS -> "不包含"
        RuleOp.NOT_EQUALS -> "不等于"
        RuleOp.EMPTY -> "为空"
        RuleOp.REGEX -> "正则"
    }

    fun valueKind(f: RuleField): RuleValueKind = when (f) {
        RuleField.CATEGORY -> RuleValueKind.CATEGORY
        RuleField.ACCOUNT -> RuleValueKind.ACCOUNT
        RuleField.AMOUNT -> RuleValueKind.AMOUNT_RANGE
        RuleField.DATE -> RuleValueKind.DATE_RANGE
        RuleField.CURRENCY -> RuleValueKind.CHOICE
        RuleField.EXCLUDED -> RuleValueKind.FLAG
        else -> RuleValueKind.TEXT
    }

    /** 「设置目标账户」在 FF3 里改的是对面账户；本项目不建对方账户，故映射为「改交易对方」。 */
    fun actions(): List<RuleActionKind> = listOf(
        RuleActionKind.SET_CATEGORY,
        RuleActionKind.RENAME_COUNTERPARTY,
        RuleActionKind.REPLACE_NOTE,
        RuleActionKind.APPEND_NOTE,
        RuleActionKind.SET_EXCLUDED
    )

    fun actionLabel(k: RuleActionKind): String = when (k) {
        RuleActionKind.SET_CATEGORY -> "设置分类为"
        RuleActionKind.RENAME_COUNTERPARTY -> "交易对方改为"
        RuleActionKind.REPLACE_NOTE -> "备注替换"
        RuleActionKind.APPEND_NOTE -> "备注追加"
        RuleActionKind.SET_EXCLUDED -> "切换不计入收支"
    }

    fun actionValueKind(k: RuleActionKind): RuleValueKind = when (k) {
        RuleActionKind.SET_CATEGORY -> RuleValueKind.CATEGORY
        RuleActionKind.SET_EXCLUDED -> RuleValueKind.FLAG
        else -> RuleValueKind.TEXT
    }

    data class BatchHit(val index: Int, val label: String, val hits: Int, val conflicts: List<Int>)

    /**
     * 批量录入：每行「旧分类名或对方关键词|新分类名」。
     * 生成的是普通规则（条件 + SET_CATEGORY 动作），不是第二套执行引擎；
     * 已存规则照常保留，删掉的只是「独立映射表」这个 UI 入口。
     */
    /**
     * 第 39 批：分隔符容错（半角 | / 全角 ｜ / → / Tab，分隔前后允许空格），
     * 空行与 # 注释跳过；不能解析的行不是静默丢弃，而是逐行写进 issues。
     * 新分类名支持「一级 / 二级」全名（弹窗插入时回填的就是全名）。
     */
    fun batchRules(
        text: String,
        groupId: Long,
        startOrder: Int,
        issues: MutableList<String>? = null,
        resolveCategory: (String) -> Long?
    ): List<RuleDefinition> {
        val sep = Regex("\\s*[|｜]\\s*|\\s*→\\s*|\\t+")
        val out = ArrayList<RuleDefinition>()
        var row = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            row += 1
            val parts = line.split(sep, limit = 2).map { it.trim() }
            if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                issues?.add("第 " + row + " 行：缺分隔符或两侧为空（" + line + "）")
                continue
            }
            val toId = resolveCategory(parts[1])
            if (toId == null) {
                issues?.add("第 " + row + " 行：新分类「" + parts[1] + "」不存在，请用「选新分类」插入")
                continue
            }
            val fromId = resolveCategory(parts[0])
            val cond = if (fromId != null) {
                RuleCondition(
                    field = RuleField.CATEGORY,
                    op = RuleOp.EQUALS,
                    categoryId = fromId,
                    value = parts[0]
                )
            } else {
                RuleCondition(
                    field = RuleField.COUNTERPARTY,
                    op = RuleOp.CONTAINS,
                    value = parts[0]
                )
            }
            out.add(
                RuleDefinition(
                    groupId = groupId,
                    name = parts[0] + " → " + parts[1],
                    sortOrder = startOrder + out.size,
                    matchMode = "ALL",
                    conditions = listOf(cond),
                    actions = listOf(
                        RuleActionSpec(kind = RuleActionKind.SET_CATEGORY, categoryId = toId)
                    )
                )
            )
        }
        return out
    }

    /** 每组命中数 + 与其他组重叠的冲突下标；hits == 0 即未命中。 */
    fun batchReport(targets: List<BulkTarget>, rules: List<RuleDefinition>): List<BatchHit> {
        val hitIds = rules.map { r ->
            targets.filter { RuleEngine.evaluate(it, r) }.map { it.id }.toSet()
        }
        return rules.indices.map { i ->
            val conflicts = rules.indices.filter { j ->
                j != i && hitIds[i].intersect(hitIds[j]).isNotEmpty()
            }
            BatchHit(i, rules[i].name, hitIds[i].size, conflicts)
        }
    }
}
