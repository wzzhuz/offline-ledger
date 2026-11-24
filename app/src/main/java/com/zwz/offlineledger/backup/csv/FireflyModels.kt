package com.zwz.offlineledger.backup.csv

/** Firefly III 6.6.x transactions.csv 的单行原始值，只保留迁移所需字段。 */
data class FireflyRow(
    val lineNo: Int,
    val journalId: String,
    val groupId: String,
    val groupTitle: String,
    val type: String,
    val amountRaw: String,
    val currencyCode: String,
    val foreignCurrencyCode: String,
    val foreignAmount: String,
    val description: String,
    val dateRaw: String,
    val sourceName: String,
    val sourceType: String,
    val destinationName: String,
    val destinationType: String,
    val categoryRaw: String,
    val tags: String,
    val notes: String,
    val budget: String
)

/** 全量解析结果；errors 非空时调用方 MUST NOT 生成导入计划。 */
data class FireflyParseResult(
    val rows: List<FireflyRow>,
    val errors: List<String>,
    val warnings: List<String>
) {
    val ok: Boolean get() = errors.isEmpty()
}

enum class FireflyKind { EXPENSE, INCOME, TRANSFER, OPENING, SKIPPED }

/**
 * 一行 FF3 流水在本项目里的落法。
 * amountYuan 对流水恒为 abs() 后的正数；OPENING 保留原符号（负债期初可为负）。
 */
data class FireflyEntry(
    val lineNo: Int,
    val journalId: String,
    val kind: FireflyKind,
    val accountName: String,
    val toAccountName: String?,
    val counterparty: String,
    val categoryKey: String,
    val categoryRaw: String,
    val amountYuan: String,
    val currencyCode: String,
    val epochMilli: Long,
    val localDate: Int,
    val yearMonth: Int,
    val note: String,
    /** 第 43 批：FF3 description 独立带出，MUST NOT 并入 note。 */
    val description: String = "",
    val skipReason: String = ""
)

/** FF3 的「一级-二级」分类键，按收支类型分开建树，避免同名跨类型冲突。 */
data class FireflyCategoryKey(
    val type: String,
    val root: String,
    val child: String
) {
    fun key(): String = "$type|$root|$child"
}

data class FireflyPlan(
    val entries: List<FireflyEntry>,
    val accountNames: List<String>,
    val categoryKeys: List<FireflyCategoryKey>,
    val warnings: List<String>
) {
    val recordCount: Int get() = entries.count {
        it.kind == FireflyKind.EXPENSE || it.kind == FireflyKind.INCOME ||
            it.kind == FireflyKind.TRANSFER
    }
    val openingCount: Int get() = entries.count { it.kind == FireflyKind.OPENING }
    val skippedCount: Int get() = entries.count { it.kind == FireflyKind.SKIPPED }
}

/** 账户映射：targetName 为空表示新建，type 为新建时的建议账户类型。 */
data class FireflyAccountChoice(
    val sourceName: String,
    val targetName: String,
    val isNew: Boolean,
    val type: String,
    /** 第 63 批：目标账户的卡号后四位，同名两卡靠它精确映射。 */
    val targetCardLast4: String? = null
)

/** 分类映射：targetRoot 为空表示按 FF3 原名自动新建；skip 表示不设分类。 */
data class FireflyCategoryChoice(
    val key: String,
    val root: String,
    val child: String,
    val type: String,
    val targetRoot: String,
    val skip: Boolean = false
)

data class FireflyMapping(
    val accounts: Map<String, FireflyAccountChoice>,
    val categories: Map<String, FireflyCategoryChoice>,
    val overwriteOpeningForExisting: Boolean = false
)

data class FireflyImportOutcome(
    val written: Int,
    val skipped: Int,
    val newAccounts: Int,
    val newCategories: Int,
    val openingsApplied: Int,
    val openingsSkipped: Int,
    /** 第 25 批：结果视图单一出口所需状态；repository 用位置参数构造时走默认值。 */
    val success: Boolean = true,
    val errorMessage: String = "",
    val failRows: List<Int> = emptyList(),
    val durationMs: Long = 0L,
    val totalRows: Int = 0
)
