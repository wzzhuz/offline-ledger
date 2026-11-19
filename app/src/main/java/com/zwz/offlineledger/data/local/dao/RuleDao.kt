package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zwz.offlineledger.data.local.entity.RuleEntity
import com.zwz.offlineledger.data.local.entity.RuleGroupEntity

/** 第 36 批：规则定义读写。写入为单行 JSON，天然原子，不引入跨表事务。 */
@Dao
interface RuleDao {
    @Query("SELECT * FROM rule_groups ORDER BY sortOrder, id")
    suspend fun groups(): List<RuleGroupEntity>

    @Query("SELECT * FROM rule_definitions ORDER BY sortOrder, id")
    suspend fun definitions(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(g: RuleGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDefinition(d: RuleEntity): Long

    @Query("DELETE FROM rule_definitions WHERE id = :id")
    suspend fun deleteDefinition(id: Long)

    @Query("DELETE FROM rule_groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("DELETE FROM rule_definitions")
    suspend fun clearDefinitions()

    @Query("DELETE FROM rule_groups")
    suspend fun clearGroups()
}
