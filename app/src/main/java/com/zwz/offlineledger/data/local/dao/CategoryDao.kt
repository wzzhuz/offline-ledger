package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zwz.offlineledger.data.local.entity.CategoryEntity

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Insert suspend fun insert(c: CategoryEntity): Long
    @Update suspend fun update(c: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("DELETE FROM categories")
    suspend fun clear()
}
