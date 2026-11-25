package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zwz.offlineledger.core.currency.LedgerCurrency
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
    /** 与 records.linkedRecordId 同步，保证 SETTLEMENT 删除后恢复不丢关联。 */
    @ColumnInfo(name = "linkedRecordId") val linkedRecordId: Long? = null,
    /** 与 records.currencyCode 同步，删除->恢复不丢币种。 */
    @ColumnInfo(name = "currencyCode", defaultValue = "'CNY'")
    val currencyCode: String = LedgerCurrency.DEFAULT,
    /** 与 records.counterparty 同步，删除->恢复不丢收款方。 */
    @ColumnInfo(name = "counterparty", defaultValue = "''")
    val counterparty: String = "",
    /** 第 43 批：与 records.description 同步，删除->恢复不丢描述。 */
    @ColumnInfo(name = "description", defaultValue = "''")
    val description: String = "",
    /** 第 67 批：与 records 同步，删除→恢复 MUST NOT 丢附加金额。 */
    @ColumnInfo(name = "discountCents") val discountCents: Long? = null,
    @ColumnInfo(name = "feeCents") val feeCents: Long? = null,
    /** 第 23 批：与 records.importSource 同步。 */
    @ColumnInfo(name = "importSource") val importSource: String? = null,
    @ColumnInfo(name = "sourceId") val sourceId: String? = null,
    /** 第 75 批：删除瞬间的标签名快照（逗号分隔）；恢复时按名重新绑定。 */
    @ColumnInfo(name = "tagNames", defaultValue = "''") val tagNames: String = "",
    @ColumnInfo(name = "deletedAt") val deletedAt: Long = 0L
)
