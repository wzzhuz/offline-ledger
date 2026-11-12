package com.zwz.offlineledger.data.di

import android.content.Context
import androidx.room.Room
import com.zwz.offlineledger.data.local.AppDatabase
import com.zwz.offlineledger.data.local.DatabaseKeyManager
import com.zwz.offlineledger.data.local.SeedCallback
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideOpenHelperFactory(
        @ApplicationContext context: Context
    ): SupportOpenHelperFactory = SupportOpenHelperFactory(
        DatabaseKeyManager.getOrCreatePassphrase(context)
    )

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        factory: SupportOpenHelperFactory
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
        .openHelperFactory(factory)
        .addCallback(SeedCallback())
        .build()

    @Provides fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideRecordDao(db: AppDatabase): RecordDao = db.recordDao()
    @Provides fun provideAdjustmentDao(db: AppDatabase): BalanceAdjustmentDao = db.balanceAdjustmentDao()
    @Provides fun provideTrashDao(db: AppDatabase): TrashDao = db.trashDao()
}
