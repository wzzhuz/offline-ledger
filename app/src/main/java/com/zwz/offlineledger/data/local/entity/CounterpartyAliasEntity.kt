package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 第 30 批一期：商户全称 -> 简称的归一规则，只在导入落库前查表，
 * records.counterparty 仍是自由文本，不建外键、不引入对方管理页。
 */
@Entity(
    tableName = "counterparty_aliases",
    indices = [Index(value = ["pattern"], unique = true)]
)
data class CounterpartyAliasEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "pattern") val pattern: String,
    @ColumnInfo(name = "shortName") val shortName: String
)
