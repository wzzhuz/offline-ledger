package com.zwz.offlineledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.BudgetDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.CounterpartyAliasDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.RecurringRuleDao
import com.zwz.offlineledger.data.local.dao.TemplateDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity
import com.zwz.offlineledger.data.local.entity.BudgetEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.CounterpartyAliasEntity
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.data.local.entity.RecurringRuleEntity
import com.zwz.offlineledger.data.local.entity.TrashEntryEntity

/**
 * 第 30 批：version 升到 3（categories.enabled / accounts.cardLast4 /
 * counterparty_aliases）。仍不注册 Migration，测试期覆盖安装会开库失败，
 * MUST 卸载重装；装之前先导出。第 23 批：version 升到 2，
 * records / trash 增加 importSource + sourceId 与复合唯一索引。
 * 仍不注册 Migration（ALL 为空），因此**必须卸载重装**，覆盖安装会开库失败。
 * 卸载前 MUST 先导出 CSV 或加密备份带走历史数据；重装后用「导入账单」重建。
 * 三段迁移 SQL 常量保留在 Migrations.kt 供历史追溯与测试。
 * exportSchema = false：CI 无模拟器跑不了 MigrationTestHelper。
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        RecordEntity::class,
        BalanceAdjustmentEntity::class,
        TrashEntryEntity::class,
        RecordTemplateEntity::class,
        RecurringRuleEntity::class,
        BudgetEntity::class,
        CounterpartyAliasEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recordDao(): RecordDao
    abstract fun balanceAdjustmentDao(): BalanceAdjustmentDao
    abstract fun trashDao(): TrashDao
    abstract fun templateDao(): TemplateDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun counterpartyAliasDao(): CounterpartyAliasDao

    companion object {
        const val DATABASE_NAME = "offline_ledger.db"
    }
}
