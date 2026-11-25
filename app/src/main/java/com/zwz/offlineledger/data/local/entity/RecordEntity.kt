package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.data.model.RecordType

@Entity(
    tableName = "records",
    indices = [
        Index(value = ["localDate", "id"]),
        Index(value = ["yearMonth", "type"]),
        Index(value = ["rootCategoryId", "categoryId", "yearMonth"]),
        Index(value = ["amountCents"]),
        Index(value = ["fromAccountId"]),
        Index(value = ["toAccountId"]),
        Index(value = ["categoryId"]),
        /** 第 23 批：导入来源的复合唯一键；两列均可空，手工记账为 NULL 不参与唯一约束。 */
        Index(value = ["importSource", "sourceId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"],
            childColumns = ["fromAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"],
            childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"],
            childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "type") val type: RecordType,
    /** 恒为正整数，单位分。 */
    @ColumnInfo(name = "amountCents") val amountCents: Long,
    @ColumnInfo(name = "categoryId") val categoryId: Long? = null,
    /** 冗余：一级分类指向自身，二级指向父级。 */
    @ColumnInfo(name = "rootCategoryId") val rootCategoryId: Long? = null,
    @ColumnInfo(name = "fromAccountId") val fromAccountId: Long? = null,
    @ColumnInfo(name = "toAccountId") val toAccountId: Long? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "localDate") val localDate: Int,
    @ColumnInfo(name = "yearMonth") val yearMonth: Int,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "excludeFromStats") val excludeFromStats: Boolean = false,
    /** 阶段 6b：SETTLEMENT 指向被冲减的原支出流水；普通流水为 null。 */
    @ColumnInfo(name = "linkedRecordId") val linkedRecordId: Long? = null,
    /** 阶段 6c-1：流水币种 = 关联账户币种，只是标签，不参与金额运算。 */
    @ColumnInfo(name = "currencyCode", defaultValue = "'CNY'")
    val currencyCode: String = LedgerCurrency.DEFAULT,
    /** 阶段 7：收款方自由文本。不是账户、不产生复式分录、不参与余额联动。 */
    @ColumnInfo(name = "counterparty", defaultValue = "''")
    val counterparty: String = "",
    /**
     * 第 43 批：Firefly III 的 description（必填、常写「这笔干嘛的」）。
     * MUST 独立成列，MUST NOT 拼进 note / 覆盖 counterparty。
     */
    @ColumnInfo(name = "description", defaultValue = "''")
    val description: String = "",
    /**
     * 第 67 批：附加金额（单位分，可空）。`amountCents` 恒为实际金额（实付），
     * 原价 = amountCents + discountCents，MUST NOT 再落一个原价列。
     * discountCents 仅 EXPENSE / INCOME，MUST 0 < d <= amountCents。
     */
    @ColumnInfo(name = "discountCents") val discountCents: Long? = null,
    /** 仅 TRANSFER；正数=手续费，**负数=转账优惠**（与一羽 CSV 手册一致）。 */
    @ColumnInfo(name = "feeCents") val feeCents: Long? = null,
    /** 导入来源标记；手工记账恒为 null，NULL 在 SQLite 唯一索引里互不冲突。 */
    @ColumnInfo(name = "importSource")
    val importSource: String? = null,
    /** 来源系统内的稳定流水号：FF3 用 journal_id，自有 CSV 用去重键。 */
    @ColumnInfo(name = "sourceId")
    val sourceId: String? = null
)
