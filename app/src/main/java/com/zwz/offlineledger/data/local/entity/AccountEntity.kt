package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.data.model.AccountType

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["type"]), Index(value = ["includeInTotal"])]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: AccountType,
    /** 真相源之一：期初余额。balanceCents 只是可重建缓存。 */
    @ColumnInfo(name = "initialBalanceCents") val initialBalanceCents: Long = 0L,
    @ColumnInfo(name = "balanceCents") val balanceCents: Long = 0L,
    @ColumnInfo(name = "creditLimitCents") val creditLimitCents: Long? = null,
    @ColumnInfo(name = "includeInTotal") val includeInTotal: Boolean = true,
    @ColumnInfo(name = "isArchived") val isArchived: Boolean = false,
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
    @ColumnInfo(name = "currencyCode") val currencyCode: String = LedgerCurrency.DEFAULT,
    /** 阶段 3 对账锚点，字段先预留。 */
    @ColumnInfo(name = "lastReconciledAt") val lastReconciledAt: Long? = null,
    @ColumnInfo(name = "lastReconciledBalanceCents") val lastReconciledBalanceCents: Long? = null,
    /** 第 30 批：卡号后四位，仅用于区分同名卡片，不参与任何金额运算。 */
    @ColumnInfo(name = "cardLast4") val cardLast4: String? = null
)
