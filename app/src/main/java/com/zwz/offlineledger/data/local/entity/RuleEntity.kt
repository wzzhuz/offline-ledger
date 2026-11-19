package com.zwz.offlineledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 第 36 批：单条规则。条件与动作序列化成 JSON，避免四张表与外键链。 */
@Entity(tableName = "rule_definitions")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long = 0,
    val sortOrder: Int = 0,
    val definitionJson: String = ""
)
