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
 * 阶段 6b：第二次结构升级（2 -> 3），首次含 ALTER TABLE。
 * 阶段 6a：首次真正的结构升级（1 -> 2）。
 * 两张新表刻意不声明索引 / 外键，让 Migration 只需两条 CREATE TABLE。
 * exportSchema 仍为 false：CI 无模拟器跑不了 MigrationTestHelper，
 * 导出 JSON 不产生验证价值，取舍见 changes/.../design.md。
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
    version = 4,
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
