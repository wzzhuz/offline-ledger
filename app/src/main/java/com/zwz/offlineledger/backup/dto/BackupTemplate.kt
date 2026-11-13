package com.zwz.offlineledger.backup.dto

import kotlinx.serialization.Serializable

/** 与 Room Entity 分离的备份 DTO；新增字段一律带默认值，保证 v1 备份可解析。 */
@Serializable
data class BackupTemplate(
    val id: Long,
    val name: String,
    val type: String,
    val amountCents: Long,
    val categoryId: Long? = null,
    val rootCategoryId: Long? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val note: String = "",
    val excludeFromStats: Boolean = false,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L
)

@Serializable
data class BackupRecurringRule(
    val id: Long,
    val name: String,
    val type: String,
    val amountCents: Long,
    val categoryId: Long? = null,
    val rootCategoryId: Long? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val note: String = "",
    val excludeFromStats: Boolean = false,
    val cadence: String = "MONTHLY",
    val dayOfMonth: Int = 1,
    val dayOfWeek: Int = 1,
    val anchorLocalDate: Int,
    val lastGeneratedLocalDate: Int? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = 0L
)
