package com.zwz.offlineledger.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zwz.offlineledger.data.local.AppDatabase
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.BudgetDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.RecurringRuleDao
import com.zwz.offlineledger.data.local.dao.TemplateDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.repository.LedgerRepository
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 用 @EntryPoint 从 Hilt 取依赖，避免引入 hilt-navigation-compose 再配一个版本。
 * 所有对象仍由阶段 1 的 DatabaseModule 提供，构造关系没变。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LedgerEntryPoint {
    fun ledgerRepository(): LedgerRepository
    fun appDatabase(): AppDatabase
    fun accountDao(): AccountDao
    fun categoryDao(): CategoryDao
    fun recordDao(): RecordDao
    fun trashDao(): TrashDao
    fun balanceAdjustmentDao(): BalanceAdjustmentDao
    fun templateDao(): TemplateDao
    fun recurringRuleDao(): RecurringRuleDao
    fun budgetDao(): BudgetDao
}

object LedgerDi {
    fun entryPoint(context: Context): LedgerEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LedgerEntryPoint::class.java
        )
}

/** 标准 ViewModelProvider.Factory，零额外依赖、零版本风险。 */
fun ledgerFactory(
    context: Context,
    create: (LedgerEntryPoint) -> ViewModel
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        create(LedgerDi.entryPoint(context)) as T
}
