package com.zwz.offlineledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 第 75 批：标签定义表，全账本级、不按收支类型隔离。
 * name 唯一；管理页默认用归档而非物理删除，避免误删后历史关联全部丢失。
 */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
    @ColumnInfo(name = "isArchived") val isArchived: Boolean = false,
    @ColumnInfo(name = "createdAt") val createdAt: Long = 0L
)

/**
 * 流水 ↔ 标签多对多关联。两个外键都 CASCADE：
 * 删流水自动清关联；标签若被物理删也自动清关联（正常路径走归档）。
 */
@Entity(
    tableName = "record_tags",
    primaryKeys = ["recordId", "tagId"],
    indices = [Index(value = ["tagId"])],
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"], childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"], childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecordTagCrossRef(
    @ColumnInfo(name = "recordId") val recordId: Long,
    @ColumnInfo(name = "tagId") val tagId: Long
)
