package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zwz.offlineledger.data.local.entity.BudgetEntity

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun active(): List<BudgetEntity>

    @Query("SELECT * FROM budgets ORDER BY sortOrder ASC, id ASC")
    suspend fun all(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun byId(id: Long): BudgetEntity?

    @Insert suspend fun insert(b: BudgetEntity): Long
    @Update suspend fun update(b: BudgetEntity)

    @Query("UPDATE budgets SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE budgets SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("DELETE FROM budgets")
    suspend fun clear()
}
