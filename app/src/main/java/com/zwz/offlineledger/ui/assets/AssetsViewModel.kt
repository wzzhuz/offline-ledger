package com.zwz.offlineledger.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.engine.Reconciliation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 阶段 6c-1：单一币种的展示小计。严格不混算，不显示跨币种净资产总数。 */
data class CurrencyGroup(
    val currencyCode: String,
    val assetsCents: Long,
    val liabilitiesCents: Long
) {
    val netCents: Long get() = assetsCents - liabilitiesCents
}

data class AssetsUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val groups: List<CurrencyGroup> = emptyList()
)

/** 本阶段「每阶段跑一遍导出备份」由设置页沿用，资产页只负责刷新聚合值。 */
class AssetsViewModel(
    private val accountDao: AccountDao,
    private val repository: LedgerRepository,
    private val prefs: LedgerPrefs
) : ViewModel() {

    private val _state = MutableStateFlow(AssetsUiState())
    val state: StateFlow<AssetsUiState> = _state.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            val accounts = accountDao.all()
            val active = accounts.filter { !it.isArchived }
            // 按币种分组，每组单独算展示值；绝不把不同币种的 Long 分相加。
            val groups = active.groupBy { it.currencyCode }
                .toSortedMap()
                .map { (cc, list) ->
                    val totals = Reconciliation.displayTotals(list.map {
                        Reconciliation.AccountTotalsInput(
                            balanceCents = it.balanceCents,
                            isLiability = it.type == AccountType.LIABILITY,
                            includeInTotal = it.includeInTotal
                        )
                    })
                    CurrencyGroup(cc, totals.totalAssetsCents, totals.totalLiabilitiesCents)
                }
            _state.value = AssetsUiState(accounts = accounts, groups = groups)
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
                    sortOrder = accountDao.count(),
                    currencyCode = prefs.defaultCurrency
                )
            )
            reload()
            null
        } catch (e: Exception) {
            e.message ?: "新增账户失败"
        }
    }

    /** 阶段 7：完整账户编辑，含期初余额与币种。改真相源后由 Repository 重算。 */
    suspend fun editAccount(
        account: AccountEntity,
        name: String,
        initialYuan: String,
        limitYuan: String,
        currencyCode: String,
        includeInTotal: Boolean
    ): String? {
        if (name.isBlank()) return "账户名不能为空"
        val initialCents = try {
            Cents.fromYuan(if (initialYuan.isBlank()) "0" else initialYuan).value
        } catch (e: IllegalArgumentException) {
            return e.message ?: "期初余额格式非法"
        }
        val limitCents = if (limitYuan.isBlank()) null else try {
            Cents.fromYuan(limitYuan).value
        } catch (e: IllegalArgumentException) {
            return e.message ?: "信用额度格式非法"
        }
        return try {
            repository.updateAccountMeta(
                id = account.id,
                name = name.trim(),
                includeInTotal = includeInTotal,
                creditLimitCents = limitCents,
                sortOrder = account.sortOrder,
                initialBalanceCents = initialCents,
                currencyCode = currencyCode
            )
            reload()
            null
        } catch (e: Exception) {
            e.message ?: "编辑账户失败"
        }
    }
}
