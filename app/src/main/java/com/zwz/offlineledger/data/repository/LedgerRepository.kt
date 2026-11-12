package com.zwz.offlineledger.data.repository

import androidx.room.withTransaction
import com.zwz.offlineledger.backup.dto.BackupFile
import com.zwz.offlineledger.backup.dto.LedgerSnapshot
import com.zwz.offlineledger.data.local.AppDatabase
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.local.toDto
import com.zwz.offlineledger.data.local.toEntity
import com.zwz.offlineledger.data.local.toRecord
import com.zwz.offlineledger.data.local.toTrash
import com.zwz.offlineledger.data.model.LedgerAdjustment
import com.zwz.offlineledger.data.model.LedgerCategory
import com.zwz.offlineledger.engine.CategoryTree
import com.zwz.offlineledger.engine.LedgerEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class LedgerHealth(val checkedAccounts: Int, val repairedAccounts: Int, val driftCents: Long)

/**
 * 账本唯一写入口。
 *
 * 所有写操作统一 db.withTransaction { 写入 -> rebuildAllBalancesInternal() }。
 * Room 的 @Transaction 只在 DAO 方法上生成事务代码，
 * 标在 Repository 普通方法上完全不生效，所以这里用 room-ktx 的 withTransaction。
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val db: AppDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val recordDao: RecordDao,
    private val adjustmentDao: BalanceAdjustmentDao,
    private val trashDao: TrashDao
) {
    fun observeNetWorth(): Flow<Long> = accountDao.observeNetWorth()

    suspend fun insertRecord(record: RecordEntity): Long = db.withTransaction {
        val id = recordDao.insert(withResolvedRoot(record))
        rebuildAllBalancesInternal()
        id
    }

    suspend fun updateRecord(record: RecordEntity) = db.withTransaction {
        recordDao.update(withResolvedRoot(record))
        rebuildAllBalancesInternal()
    }

    /** 删除 = 从 records 移入 trash，然后重算。 */
    suspend fun deleteRecord(id: Long) = db.withTransaction {
        val record = recordDao.byId(id) ?: return@withTransaction
        trashDao.insert(record.toTrash(System.currentTimeMillis()))
        recordDao.deleteById(id)
        rebuildAllBalancesInternal()
    }

    /** 恢复 = 插回 records + 重算，与 insert 走同一条联动路径。 */
    suspend fun restoreRecord(originalId: Long) = db.withTransaction {
        val trash = trashDao.byOriginalId(originalId) ?: return@withTransaction
        recordDao.insert(trash.toRecord())
        trashDao.deleteByOriginalId(originalId)
        rebuildAllBalancesInternal()
    }

    suspend fun addAdjustment(a: BalanceAdjustmentEntity): Long = db.withTransaction {
        val id = adjustmentDao.insert(a)
        rebuildAllBalancesInternal()
        id
    }

    suspend fun revokeAdjustment(id: Long) = db.withTransaction {
        adjustmentDao.revoke(id)
        rebuildAllBalancesInternal()
    }

    /** 事务外也可显式调用；冷启动与导入后必跑。 */
    suspend fun rebuildAllBalances() = db.withTransaction {
        rebuildAllBalancesInternal()
    }

    /** 账内不一致属于缺陷，自动修复并写审计日志（阶段 5 落库）。 */
    suspend fun checkAndRepair(): LedgerHealth = db.withTransaction {
        val accounts = accountDao.all()
        val expected = expectedBalances()
        val drift = accounts.filter {
            it.balanceCents != (expected[it.id] ?: it.initialBalanceCents)
        }
        val driftCents = drift.sumOf {
            (expected[it.id] ?: it.initialBalanceCents) - it.balanceCents
        }
        if (drift.isNotEmpty()) {
            android.util.Log.w(
                "LedgerHealth",
                "检测到 ${drift.size} 个账户余额偏差，合计 $driftCents 分，已自动重算修复"
            )
            rebuildAllBalancesInternal()
        }
        LedgerHealth(accounts.size, drift.size, driftCents)
    }

    suspend fun exportSnapshot(): LedgerSnapshot = LedgerSnapshot(
        accounts = accountDao.all().map { it.toDto() },
        categories = categoryDao.all().map { it.toDto() },
        records = recordDao.all().map { it.toDto() },
        balanceAdjustments = adjustmentDao.all().map { it.toDto() },
        trash = trashDao.all().map {
            com.zwz.offlineledger.backup.dto.BackupRecord(
                id = it.originalId, type = it.type.name, amountCents = it.amountCents,
                categoryId = it.categoryId, rootCategoryId = it.rootCategoryId,
                fromAccountId = it.fromAccountId, toAccountId = it.toAccountId,
                timestamp = it.timestamp, localDate = it.localDate,
                yearMonth = it.yearMonth, note = it.note,
                excludeFromStats = it.excludeFromStats
            )
        },
        exportedAtEpochMilli = System.currentTimeMillis()
    )

    /** 全量替换的覆盖导入：先清子表再清父表，事务末尾强制重算。 */
    suspend fun importBackup(file: BackupFile) = db.withTransaction {
        recordDao.clear()
        trashDao.clear()
        adjustmentDao.clear()
        categoryDao.clear()
        accountDao.clear()

        file.payload.accounts.forEach { accountDao.insert(it.toEntity()) }
        file.payload.categories.forEach { categoryDao.insert(it.toEntity()) }
        file.payload.records.forEach { recordDao.insert(it.toEntity()) }
        file.payload.balanceAdjustments.forEach { adjustmentDao.insert(it.toEntity()) }
        file.payload.trash.forEach {
            trashDao.insert(it.toEntity().toTrash(System.currentTimeMillis()))
        }
        rebuildAllBalancesInternal()
    }

    // ---------------------------------------------------------------
    private suspend fun expectedBalances(): Map<Long, Long> {
        val deltas = recordDao.aggregateAccountDeltas()
            .associate { it.accountId to it.deltaCents }
        val adjustments = adjustmentDao.allActive().map {
            LedgerAdjustment(it.accountId, it.deltaCents, !it.isRevoked)
        }
        return LedgerEngine.apply(
            accountDao.all().associate { it.id to it.initialBalanceCents },
            deltas,
            adjustments
        )
    }

    /** 唯一余额写入路径；调用方必须已处在 withTransaction 内。 */
    private suspend fun rebuildAllBalancesInternal() {
        val balances = expectedBalances()
        accountDao.all().forEach {
            accountDao.updateBalance(it.id, balances[it.id] ?: it.initialBalanceCents)
        }
    }

    /** 两级分类解析：一级指向自身，二级指向父级；允许只选一级直接记账。 */
    private suspend fun withResolvedRoot(record: RecordEntity): RecordEntity {
        val id = record.categoryId ?: return record
        val map: Map<Long, LedgerCategory> = categoryDao.all().associate {
            it.id to LedgerCategory(it.id, it.name, it.type, it.parentId, it.level)
        }
        val rootId = CategoryTree.resolveRootId(id, map)
        return record.copy(rootCategoryId = rootId)
    }
}
