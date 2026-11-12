package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zwz.offlineledger.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Insert suspend fun insert(a: AccountEntity): Long
    @Update suspend fun update(a: AccountEntity)

    @Query("UPDATE accounts SET balanceCents = :balanceCents WHERE id = :id")
    suspend fun updateBalance(id: Long, balanceCents: Long)

    /** 净资产 = SUM(balanceCents WHERE includeInTotal = 1)，不走「总资产 − 总负债」。 */
    @Query("SELECT COALESCE(SUM(balanceCents), 0) FROM accounts WHERE includeInTotal = 1")
    fun observeNetWorth(): Flow<Long>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("DELETE FROM accounts")
    suspend fun clear()
}
