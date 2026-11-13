package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zwz.offlineledger.data.model.BudgetScope

/**
 * 预算规则：长期对每个自然月生效，不存 yearMonth，
 * 省掉每月重建；切月时消耗按当前 range 实时 SQL 聚合。
 *
 * 沿用 6a 教训：不声明 @Index / @ForeignKey，
 * Migration(2→3) 只需一条 CREATE TABLE，把 Room schema 校验风险压到最低。
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "scope") val scope: BudgetScope,
    /** TOTAL 为 null；ROOT 指向一级 id；CATEGORY 指向二级 id。 */
    @ColumnInfo(name = "categoryId") val categoryId: Long? = null,
    /** 每月限额，单位分，恒正。 */
    @ColumnInfo(name = "limitCents") val limitCents: Long,
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
    @ColumnInfo(name = "isArchived") val isArchived: Boolean = false,
    @ColumnInfo(name = "createdAt") val createdAt: Long = 0L
)
