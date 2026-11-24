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
import com.zwz.offlineledger.data.local.dao.RuleDao
import com.zwz.offlineledger.data.local.dao.RecurringRuleDao
import com.zwz.offlineledger.data.local.dao.TemplateDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity
import com.zwz.offlineledger.data.local.entity.BudgetEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.CounterpartyAliasEntity
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.local.entity.RuleEntity
import com.zwz.offlineledger.data.local.entity.RuleGroupEntity
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.data.local.entity.RecurringRuleEntity
import com.zwz.offlineledger.data.local.entity.TrashEntryEntity

/**
 * 第 60 批：version 升到 6（accounts 增加 quickOrder / cardKind）。
 * 使用者明确「现在都是测试数据，不需要考虑升级迁移」：
 * 仍不注册 Migration，Migrations.ALL 保持 emptyArray()，
 * MigrationContractTest 的 ALL.isEmpty() 断言零改动。
 * 测试期 MUST 卸载重装、装之前先导出；升版本只为让 Room 明确拒绝旧库，
 * 避免 identity 校验在 TableInfo 不一致处隐晦崩溃。
 * 第 43 批：version 升到 5（records / trash 增加 description 独立列）。
 * 仍不注册 Migration，测试期 MUST 卸载重装、装之前先导出。
 * 第 36 批：version 升到 4（rule_groups / rule_definitions，零外键）。
 * 仍不注册 Migration，测试期 MUST 卸载重装、装之前先导出。
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
        CounterpartyAliasEntity::class,
        RuleGroupEntity::class,
        RuleEntity::class
    ],
    version = 6,
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
    abstract fun ruleDao(): RuleDao

    companion object {
        const val DATABASE_NAME = "offline_ledger.db"
    }
}
