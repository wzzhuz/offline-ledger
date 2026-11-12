package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zwz.offlineledger.data.model.RecordType

/** 删除 = 从 records 移入本表；主表因此永远只含有效流水，无需 isDeleted 列。 */
@Entity(tableName = "trash", indices = [Index(value = ["deletedAt"])])
data class TrashEntryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "originalId") val originalId: Long,
    @ColumnInfo(name = "type") val type: RecordType,
    @ColumnInfo(name = "amountCents") val amountCents: Long,
    @ColumnInfo(name = "categoryId") val categoryId: Long? = null,
    @ColumnInfo(name = "rootCategoryId") val rootCategoryId: Long? = null,
    @ColumnInfo(name = "fromAccountId") val fromAccountId: Long? = null,
    @ColumnInfo(name = "toAccountId") val toAccountId: Long? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "localDate") val localDate: Int,
    @ColumnInfo(name = "yearMonth") val yearMonth: Int,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "excludeFromStats") val excludeFromStats: Boolean = false,
    @ColumnInfo(name = "deletedAt") val deletedAt: Long = 0L
)
