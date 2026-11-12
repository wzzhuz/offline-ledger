package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
        Index(value = ["categoryId"])
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
    @ColumnInfo(name = "excludeFromStats") val excludeFromStats: Boolean = false
)
