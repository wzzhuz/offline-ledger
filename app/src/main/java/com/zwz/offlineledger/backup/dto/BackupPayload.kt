package com.zwz.offlineledger.backup.dto

import kotlinx.serialization.Serializable

/** 与 Room Entity 分离的备份 DTO；Entity 加字段不破坏备份格式。 */
@Serializable
data class BackupAccount(
    val id: Long, val name: String, val type: String,
    val initialBalanceCents: Long, val balanceCents: Long,
    val creditLimitCents: Long? = null,
    val includeInTotal: Boolean = true, val isArchived: Boolean = false,
    val sortOrder: Int = 0, val currencyCode: String = "HKD",
    val lastReconciledAt: Long? = null,
    val lastReconciledBalanceCents: Long? = null
)

@Serializable
data class BackupCategory(
    val id: Long, val name: String, val type: String,
    val parentId: Long? = null, val level: Int = 0,
    val iconName: String = "", val colorHex: String = "#888888",
    val sortOrder: Int = 0, val isArchived: Boolean = false
)

@Serializable
data class BackupRecord(
    val id: Long, val type: String, val amountCents: Long,
    val categoryId: Long? = null, val rootCategoryId: Long? = null,
    val fromAccountId: Long? = null, val toAccountId: Long? = null,
    val timestamp: Long, val localDate: Int, val yearMonth: Int,
    val note: String = "", val excludeFromStats: Boolean = false,
    val linkedRecordId: Long? = null,
    val currencyCode: String = "HKD"
)

@Serializable
data class BackupAdjustment(
    val id: Long, val accountId: Long, val deltaCents: Long,
    val memo: String = "", val source: String = "TAIL_DIFFERENCE",
    val createdAt: Long = 0L, val isRevoked: Boolean = false
)

@Serializable
data class BackupPayload(
    val accounts: List<BackupAccount> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val records: List<BackupRecord> = emptyList(),
    val balanceAdjustments: List<BackupAdjustment> = emptyList(),
    val trash: List<BackupRecord> = emptyList(),
    // 阶段 6a 新增。默认空列表是 v1 备份兼容的关键：
    // kotlinx.serialization 的 ignoreUnknownKeys + 默认值会把缺失键补成空列表。
    val templates: List<BackupTemplate> = emptyList(),
    val recurringRules: List<BackupRecurringRule> = emptyList(),
    val budgets: List<BackupBudget> = emptyList()
)

/** 顶层信封。BackupEnvelope 的四项头部字段一个字不改。 */
@Serializable
data class BackupFile(
    val envelope: BackupEnvelope,
    val payload: BackupPayload
)

/** 纯 JVM 快照，供 BackupExporter.encode 与单测使用。 */
data class LedgerSnapshot(
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val records: List<BackupRecord>,
    val balanceAdjustments: List<BackupAdjustment>,
    val trash: List<BackupRecord> = emptyList(),
    val templates: List<BackupTemplate> = emptyList(),
    val recurringRules: List<BackupRecurringRule> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val exportedAtEpochMilli: Long = 0L,
    val currencyCode: String = "HKD",
    val schemaVersion: Int = BackupSchema.CURRENT
)
