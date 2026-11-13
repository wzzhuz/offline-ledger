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

    /** 阶段 3：资产页观察账户列表（账户数最多几十个，不是万级）。 */
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT COALESCE(SUM(balanceCents), 0) FROM accounts WHERE includeInTotal = 1 AND type = 'ASSET'")
    suspend fun totalAssets(): Long

    /** 原始负值（欠款为负）；展示取绝对值，MUST NOT 参与净资产计算。 */
    @Query("SELECT COALESCE(SUM(balanceCents), 0) FROM accounts WHERE includeInTotal = 1 AND type = 'LIABILITY'")
    suspend fun totalLiabilitiesRaw(): Long

    @Query("UPDATE accounts SET lastReconciledAt = :at, lastReconciledBalanceCents = :balance WHERE id = :id")
    suspend fun updateReconciliationAnchor(id: Long, at: Long, balance: Long)

    /** 阶段 7：账户编辑支持期初余额与币种；调用方 MUST 在事务末尾重算。 */
    @Query("UPDATE accounts SET name = :name, includeInTotal = :includeInTotal, creditLimitCents = :creditLimitCents, sortOrder = :sortOrder, initialBalanceCents = :initialBalanceCents, currencyCode = :currencyCode WHERE id = :id")
    suspend fun updateAccountMeta(
        id: Long, name: String, includeInTotal: Boolean,
        creditLimitCents: Long?, sortOrder: Int,
        initialBalanceCents: Long, currencyCode: String
    )

    /** 显式批量统一币种：只改标签，不改金额。 */
    @Query("UPDATE accounts SET currencyCode = :code")
    suspend fun updateAllCurrency(code: String)

    /** 只归档不物理删：有流水的账户 MUST NOT 删除。 */
    @Query("UPDATE accounts SET isArchived = 1 WHERE id = :id")
    suspend fun archiveAccount(id: Long)

    @Query("SELECT COUNT(*) FROM records WHERE fromAccountId = :id OR toAccountId = :id")
    suspend fun linkedRecordCount(id: Long): Int
}
