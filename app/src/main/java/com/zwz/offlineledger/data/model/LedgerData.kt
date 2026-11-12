package com.zwz.offlineledger.data.model

/** 纯 JVM 数据模型：不含任何 Android / Room 依赖，供 LedgerEngine 与单测使用。 */
data class LedgerAccount(
    val id: Long,
    val initialBalanceCents: Long,
    val includeInTotal: Boolean = true
)

data class LedgerRecord(
    val id: Long,
    val type: RecordType,
    val amountCents: Long,
    val fromAccountId: Long?,
    val toAccountId: Long?,
    val categoryId: Long? = null,
    val rootCategoryId: Long? = null,
    val excludeFromStats: Boolean = false
)

data class LedgerAdjustment(
    val accountId: Long,
    val deltaCents: Long,
    val active: Boolean = true
)

data class LedgerCategory(
    val id: Long,
    val name: String,
    val type: RecordType,
    val parentId: Long?,
    val level: Int
)
