package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zwz.offlineledger.data.model.RecordType

/**
 * 可直接记一笔的模板。
 *
 * 刻意不声明 @Index / @ForeignKey：模板最多几十行，全表扫描无性能问题，
 * 而少一个索引就少一处「Migration SQL 与 Room schema 不一致」的运行时风险。
 * 字段语义与 RecordEntity 完全一致，生成流水时走 withResolvedRoot + 同一事务。
 */
@Entity(tableName = "record_templates")
data class RecordTemplateEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: RecordType,
    @ColumnInfo(name = "amountCents") val amountCents: Long,
    @ColumnInfo(name = "categoryId") val categoryId: Long? = null,
    @ColumnInfo(name = "rootCategoryId") val rootCategoryId: Long? = null,
    @ColumnInfo(name = "fromAccountId") val fromAccountId: Long? = null,
    @ColumnInfo(name = "toAccountId") val toAccountId: Long? = null,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "excludeFromStats") val excludeFromStats: Boolean = false,
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
    @ColumnInfo(name = "isArchived") val isArchived: Boolean = false,
    @ColumnInfo(name = "createdAt") val createdAt: Long = 0L
)
