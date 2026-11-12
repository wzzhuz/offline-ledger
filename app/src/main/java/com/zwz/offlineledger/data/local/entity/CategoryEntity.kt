package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zwz.offlineledger.data.model.RecordType

@Entity(
    tableName = "categories",
    indices = [Index(value = ["parentId"]), Index(value = ["type", "level"])],
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["parentId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: RecordType,
    @ColumnInfo(name = "parentId") val parentId: Long? = null,
    /** 0 = 一级，1 = 二级；v1 不允许 >= 2。 */
    @ColumnInfo(name = "level") val level: Int = 0,
    @ColumnInfo(name = "iconName") val iconName: String = "",
    @ColumnInfo(name = "colorHex") val colorHex: String = "#888888",
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
    @ColumnInfo(name = "isArchived") val isArchived: Boolean = false
)
