package com.zwz.offlineledger.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.engine.Reconciliation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AssetsUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val netWorthCents: Long = 0L,
    val totalAssetsCents: Long = 0L,
    val totalLiabilitiesCents: Long = 0L
)

/** 本阶段「每阶段跑一遍导出备份」由设置页沿用，资产页只负责刷新聚合值。 */
class AssetsViewModel(
    private val accountDao: AccountDao,
    private val repository: LedgerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AssetsUiState())
    val state: StateFlow<AssetsUiState> = _state.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            val accounts = accountDao.all()
            val totals = Reconciliation.displayTotals(accounts.map {
                Reconciliation.AccountTotalsInput(
                    balanceCents = it.balanceCents,
                    isLiability = it.type == AccountType.LIABILITY,
                    includeInTotal = it.includeInTotal
                )
            })
            _state.value = AssetsUiState(
                accounts = accounts,
                netWorthCents = totals.netWorthCents,
                totalAssetsCents = totals.totalAssetsCents,
                totalLiabilitiesCents = totals.totalLiabilitiesCents
            )
        }
    }

    /** 返回 null 表示成功，否则是可展示的错误文案。 */
    suspend fun addAccount(
        name: String,
        type: AccountType,
        initialYuan: String,
        creditLimitYuan: String
    ): String? {
        if (name.isBlank()) return "账户名不能为空"
        val initialCents = try {
            Cents.fromYuan(if (initialYuan.isBlank()) "0" else initialYuan).value
        } catch (e: IllegalArgumentException) {
            return e.message ?: "期初余额格式非法"
        }
        val limitCents = if (creditLimitYuan.isBlank()) null else try {
            Cents.fromYuan(creditLimitYuan).value
        } catch (e: IllegalArgumentException) {
            return e.message ?: "信用额度格式非法"
        }
        return try {
            repository.createAccount(
                AccountEntity(
                    name = name.trim(),
                    type = type,
                    initialBalanceCents = initialCents,
                    balanceCents = initialCents,
                    creditLimitCents = limitCents,
                    sortOrder = accountDao.count()
                )
            )
            reload()
            null
        } catch (e: Exception) {
            e.message ?: "新增账户失败"
        }
    }
}
