package com.zwz.offlineledger.backup.dto

import kotlinx.serialization.Serializable

/** 与 Room Entity 分离的备份 DTO；新增字段一律带默认值，保证 v1/v2 备份可解析。 */
@Serializable
data class BackupBudget(
    val id: Long,
    val name: String,
    val scope: String,
    val categoryId: Long? = null,
    val limitCents: Long,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L
)
