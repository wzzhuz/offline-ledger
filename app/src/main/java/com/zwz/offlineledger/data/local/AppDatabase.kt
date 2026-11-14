package com.zwz.offlineledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.BudgetDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.RecurringRuleDao
import com.zwz.offlineledger.data.local.dao.TemplateDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity
import com.zwz.offlineledger.data.local.entity.BudgetEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.data.local.entity.RecurringRuleEntity
import com.zwz.offlineledger.data.local.entity.TrashEntryEntity

/**
 * 稳定版前按使用者授权重置为 version 1、不注册任何 Migration。
 * 使用者只会用测试数据且在满意前不会长期使用，不需要保留升级路径；
 * 全新安装走 onCreate，按 Entity 直接建含 counterparty 列的最新表结构。
 * 三段迁移 SQL 常量保留在 Migrations.kt 供历史追溯与测试，但 ALL 为空。
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
        BudgetEntity::class
    ],
    version = 1,
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

    companion object {
        const val DATABASE_NAME = "offline_ledger.db"
    }
}
