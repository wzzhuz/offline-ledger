package com.zwz.offlineledger.data.di

import android.content.Context
import androidx.room.Room
import com.zwz.offlineledger.core.StartupTrace
import com.zwz.offlineledger.data.local.AppDatabase
import com.zwz.offlineledger.data.local.Migrations
import com.zwz.offlineledger.data.local.SeedCallback
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.TagDao
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.BudgetDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.CounterpartyAliasDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.RecurringRuleDao
import com.zwz.offlineledger.data.local.dao.TemplateDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.local.dao.VirtualBookDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 第 50 批：Room 用平台默认 SQLiteOpenHelper（明文库）。
 * 不再接 SupportOpenHelperFactory、不再取 Keystore 口令、不再派生密钥。
 *
 * 库名仍是 `offline_ledger.db`、version 仍是 5：
 * 备份排除规则写死了这三个文件名，改库名会让账本逃出排除范围。
 * 因此旧加密库打不开 → 覆盖安装会崩，MUST 先导出加密备份 → 卸载 → 安装 → 导入。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        StartupTrace.mark(2)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addCallback(SeedCallback(context))
            .addMigrations(*Migrations.ALL)
            .build()
            .also { StartupTrace.mark(3) }
    }

    @Provides fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideRecordDao(db: AppDatabase): RecordDao = db.recordDao()
    @Provides fun provideAdjustmentDao(db: AppDatabase): BalanceAdjustmentDao = db.balanceAdjustmentDao()
    @Provides fun provideTrashDao(db: AppDatabase): TrashDao = db.trashDao()
    @Provides fun provideTemplateDao(db: AppDatabase): TemplateDao = db.templateDao()
    @Provides fun provideRecurringRuleDao(db: AppDatabase): RecurringRuleDao = db.recurringRuleDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideCounterpartyAliasDao(db: AppDatabase): CounterpartyAliasDao = db.counterpartyAliasDao()
    /** 第 75 批：LedgerEntryPoint.tagDao() 与标签管理页共用此绑定。 */
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
    /** 第 91 批：LedgerEntryPoint.virtualBookDao() 与台账页共用此绑定。 */
    @Provides fun provideVirtualBookDao(db: AppDatabase): VirtualBookDao = db.virtualBookDao()
}
