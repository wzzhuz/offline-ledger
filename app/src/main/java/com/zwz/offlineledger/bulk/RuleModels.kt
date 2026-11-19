package com.zwz.offlineledger.bulk

import kotlinx.serialization.Serializable

/**
 * 第 36 批：规则 2.0 的可序列化定义。
 * 条件与动作以 JSON 存规则表，避免为每种操作符建一张表；Room 侧零外键。
 * 第 37 批：新增枚举值一律追加在末尾，旧规则 JSON 按名反序列化不破；新增字段带默认值。
 */
@Serializable
enum class RuleField {
    COUNTERPARTY, NOTE, CATEGORY, AMOUNT, DATE,
    ACCOUNT, CURRENCY, IMPORT_SOURCE, EXCLUDED
}

@Serializable
enum class RuleOp {
    CONTAINS, EQUALS, STARTS_WITH, ENDS_WITH, NOT_CONTAINS, EMPTY, REGEX,
    NOT_EQUALS
}

@Serializable
data class RuleCondition(
    val field: RuleField = RuleField.COUNTERPARTY,
    val op: RuleOp = RuleOp.CONTAINS,
    val value: String = "",
    val negate: Boolean = false,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val minAmountCents: Long? = null,
    val maxAmountCents: Long? = null,
    val fromEpochMilli: Long? = null,
    val toEpochMilli: Long? = null
)

@Serializable
enum class RuleActionKind {
    SET_CATEGORY, RENAME_COUNTERPARTY, REPLACE_NOTE, SET_EXCLUDED,
    APPEND_NOTE
}

@Serializable
data class RuleActionSpec(
    val kind: RuleActionKind = RuleActionKind.SET_CATEGORY,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val text: String = "",
    val from: String = "",
    val to: String = "",
    val excluded: Boolean = false
)

@Serializable
data class RuleDefinition(
    val id: Long = 0,
    val groupId: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    /** ALL = strict 全部条件 AND；ANY = 任意一条 OR。 */
    val matchMode: String = "ALL",
    val conditions: List<RuleCondition> = emptyList(),
    val actions: List<RuleActionSpec> = emptyList()
)

@Serializable
data class RuleGroupDef(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

/** 分类迁移映射表：旧分类 id 或对方关键词 → 新分类 id。 */
@Serializable
data class CategoryMigration(
    val fromCategoryId: Long? = null,
    val counterpartyContains: String = "",
    val toCategoryId: Long? = null,
    val label: String = ""
)
