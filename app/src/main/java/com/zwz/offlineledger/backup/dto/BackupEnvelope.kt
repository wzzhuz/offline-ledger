package com.zwz.offlineledger.backup.dto

import kotlinx.serialization.Serializable

/**
 * 备份格式版本。
 * CURRENT 随结构变化递增；MIN_SUPPORTED 表示仍能导入的最旧版本。
 */
object BackupSchema {
    // 阶段 6a 升 2；阶段 6b 升 3；阶段 6c-1 BackupRecord 增加
    // currencyCode，故 CURRENT 升 4。MIN_SUPPORTED 一路保持 1：
    // v1/v2/v3 备份缺键时靠默认值解析成空列表 / null / HKD。
    const val CURRENT: Int = 4
    const val MIN_SUPPORTED: Int = 1
}

/**
 * 备份信封头部。
 *
 * 这四项的字段名与含义一旦定下不再变更：阶段 1 往 payload 里加
 * BackupAccount / BackupRecord 等数据 DTO 时，旧备份仍能被解析。
 * 本类与 Room Entity 分离，Entity 加字段不破坏备份格式。
 */
@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = BackupSchema.CURRENT,
    val exportedAtEpochMilli: Long,
    val currencyCode: String,
    val counts: BackupCounts = BackupCounts()
)

@Serializable
data class BackupCounts(
    val accounts: Int = 0,
    val categories: Int = 0,
    val records: Int = 0,
    val balanceAdjustments: Int = 0,
    val templates: Int = 0,
    val recurringRules: Int = 0,
    val budgets: Int = 0
)
