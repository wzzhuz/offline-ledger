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
    /** 第 30 批：卡号后四位，仅用于区分同名卡片，不参与任何金额运算。
     *  第 60 批：改为**单一真相源**——name SHALL 只存纯名，展示层组合一次。 */
    @ColumnInfo(name = "cardLast4") val cardLast4: String? = null,
    /** 第 60 批：记一笔优先顺序，全账户不分类型；与分组内 sortOrder 互不干涉。
     *  使用者明确「测试数据、不考虑升级迁移」，故 version=6 但不注册 Migration。 */
    @ColumnInfo(name = "quickOrder") val quickOrder: Int = 0,
    /** 第 60 批：卡种 DEBIT / CREDIT / null。正规银行全称下同名两卡靠它区分，
     *  UI 用 12dp 小角标表达，MUST NOT 把卡种拼回账户名。 */
    @ColumnInfo(name = "cardKind") val cardKind: String? = null,
    /** 第 82 批：账户图标关联。`preset:<iconKey>` / `custom:<uuid>` / null=按名自动推断。 */
    @ColumnInfo(name = "iconRef") val iconRef: String? = null
)
