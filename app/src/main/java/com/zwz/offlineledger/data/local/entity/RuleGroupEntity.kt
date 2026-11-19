package com.zwz.offlineledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 第 36 批：规则组。不建外键，恢复时按 groups → definitions 顺序插入即可。 */
@Entity(tableName = "rule_groups")
data class RuleGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)
