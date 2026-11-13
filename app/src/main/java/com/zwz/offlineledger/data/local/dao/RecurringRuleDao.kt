package com.zwz.offlineledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zwz.offlineledger.data.local.entity.RecurringRuleEntity

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules WHERE isEnabled = 1 ORDER BY id ASC")
    suspend fun enabled(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules ORDER BY id ASC")
    suspend fun all(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun byId(id: Long): RecurringRuleEntity?

    @Insert suspend fun insert(r: RecurringRuleEntity): Long
    @Update suspend fun update(r: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** 规则删除不影响历史流水（流水是独立的 RecordEntity），故允许物理删。 */
    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recurring_rules")
    suspend fun clear()
}
