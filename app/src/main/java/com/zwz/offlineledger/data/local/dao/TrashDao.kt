package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zwz.offlineledger.data.local.entity.TrashEntryEntity

@Dao
interface TrashDao {
    @Insert suspend fun insert(t: TrashEntryEntity): Long

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    suspend fun all(): List<TrashEntryEntity>

    @Query("SELECT * FROM trash WHERE originalId = :id")
    suspend fun byOriginalId(id: Long): TrashEntryEntity?

    @Query("DELETE FROM trash WHERE originalId = :id")
    suspend fun deleteByOriginalId(id: Long)

    @Query("DELETE FROM trash")
    suspend fun clear()
}
