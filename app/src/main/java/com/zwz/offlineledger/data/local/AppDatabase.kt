package com.zwz.offlineledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.RecordEntity
import com.zwz.offlineledger.data.local.entity.TrashEntryEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        RecordEntity::class,
        BalanceAdjustmentEntity::class,
        TrashEntryEntity::class
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

    companion object {
        const val DATABASE_NAME = "offline_ledger.db"
    }
}
