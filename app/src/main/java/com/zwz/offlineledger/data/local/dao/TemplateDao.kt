package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity

@Dao
interface TemplateDao {
    @Query("SELECT * FROM record_templates WHERE isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun active(): List<RecordTemplateEntity>

    @Query("SELECT * FROM record_templates ORDER BY sortOrder ASC, id ASC")
    suspend fun all(): List<RecordTemplateEntity>

    @Query("SELECT * FROM record_templates WHERE id = :id")
    suspend fun byId(id: Long): RecordTemplateEntity?

    @Insert suspend fun insert(t: RecordTemplateEntity): Long
    @Update suspend fun update(t: RecordTemplateEntity)

    @Query("UPDATE record_templates SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE record_templates SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("DELETE FROM record_templates")
    suspend fun clear()
}
