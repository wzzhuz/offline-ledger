package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账实不符的校准记录。不在 records 表，因此：
 * 不进支出明细、不进分类统计、不进年度收支，重算时也不会被当成真实流水再扣一次。
 */
@Entity(
    tableName = "balance_adjustments",
    indices = [Index(value = ["accountId"])],
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class BalanceAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "accountId") val accountId: Long,
    /** 可正可负，单位分。 */
    @ColumnInfo(name = "deltaCents") val deltaCents: Long,
    @ColumnInfo(name = "memo") val memo: String = "",
    @ColumnInfo(name = "source") val source: String = "TAIL_DIFFERENCE",
    @ColumnInfo(name = "createdAt") val createdAt: Long = 0L,
    @ColumnInfo(name = "isRevoked") val isRevoked: Boolean = false
)
