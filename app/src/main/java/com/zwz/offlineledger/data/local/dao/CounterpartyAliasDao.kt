package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zwz.offlineledger.data.local.entity.CounterpartyAliasEntity

@Dao
interface CounterpartyAliasDao {
    @Query("SELECT * FROM counterparty_aliases")
    suspend fun all(): List<CounterpartyAliasEntity>

    @Insert suspend fun insert(alias: CounterpartyAliasEntity): Long

    @Query("DELETE FROM counterparty_aliases")
    suspend fun clear()
}
