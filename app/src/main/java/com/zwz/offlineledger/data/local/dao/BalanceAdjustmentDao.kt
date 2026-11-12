package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity

@Dao
interface BalanceAdjustmentDao {
    @Insert suspend fun insert(a: BalanceAdjustmentEntity): Long

    @Query("SELECT * FROM balance_adjustments WHERE isRevoked = 0")
    suspend fun allActive(): List<BalanceAdjustmentEntity>

    @Query("SELECT * FROM balance_adjustments ORDER BY createdAt DESC")
    suspend fun all(): List<BalanceAdjustmentEntity>

    @Query("UPDATE balance_adjustments SET isRevoked = 1 WHERE id = :id")
    suspend fun revoke(id: Long)

    @Query("DELETE FROM balance_adjustments")
    suspend fun clear()

    @Query("SELECT * FROM balance_adjustments WHERE accountId = :accountId ORDER BY createdAt DESC")
    suspend fun byAccountId(accountId: Long): List<BalanceAdjustmentEntity>

    @Query("SELECT * FROM balance_adjustments WHERE id = :id")
    suspend fun byId(id: Long): BalanceAdjustmentEntity?
}
