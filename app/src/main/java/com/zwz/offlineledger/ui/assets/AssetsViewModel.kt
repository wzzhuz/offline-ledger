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

/** 严格不混算。按币种分组列小计，不显示跨币种净资产总数。 */
data class CurrencyGroup(
    val currencyCode: String,
    val assetsCents: Long,
    val liabilitiesCents: Long
) {
    val netCents: Long get() = assetsCents - liabilitiesCents
}

data class AssetsUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val groups: List<CurrencyGroup> = emptyList(),
    /** 第三批：归档账户不再消失，可一键显示 / 恢复。 */
    val showArchived: Boolean = false
)

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
            _state.value = _state.value.copy(accounts = accounts, groups = groups)
        }
    }

    fun toggleShowArchived() {
        _state.value = _state.value.copy(showArchived = !_state.value.showArchived)
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

    /** 第三批：完整账户编辑，含类型修正、期初余额与币种。改真相源后由 Repository 重算。 */
    suspend fun editAccount(
        account: AccountEntity,
        name: String,
        type: AccountType,
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
            if (type != account.type) repository.updateAccountType(account.id, type)
            reload()
            null
        } catch (e: Exception) {
            e.message ?: "编辑账户失败"
        }
    }

    /**
     * 第 15 批：排序作用域改为「账户类型」，与资产页 4 组分组一致。
     * 原实现把全部未归档账户当一个数组，跨组交换后 UI 看起来「点了没反应」，
     * 与分类管理是同一个全局数组 bug。
     */
    suspend fun moveAccount(id: Long, delta: Int): String? {
        val all = _state.value.accounts
        val cur = all.firstOrNull { it.id == id } ?: return null
        if (cur.isArchived) return null
        val group = all.filter { !it.isArchived && it.type == cur.type }
            .sortedBy { it.sortOrder }
        val idx = group.indexOfFirst { it.id == id }
        val target = idx + delta
        if (idx < 0 || target !in group.indices) return null
        val swapped = group.toMutableList().apply {
            val a = this[idx]; this[idx] = this[target]; this[target] = a
        }
        val active = all.filter { !it.isArchived }.sortedBy { it.sortOrder }
        val rebuilt = mutableListOf<Long>()
        val seen = mutableSetOf<AccountType>()
        active.forEach { a ->
            if (a.type !in seen) {
                seen += a.type
                rebuilt.addAll(
                    if (a.type == cur.type) swapped.map { it.id }
                    else active.filter { it.type == a.type }.map { it.id }
                )
            }
        }
        return try {
            repository.reorderAccounts(rebuilt + all.filter { it.isArchived }.map { it.id })
            reload()
            null
        } catch (e: Exception) {
            e.message ?: "排序失败"
        }
    }

    /** 第 16 批：组内拖拽落库。只替换该 AccountType 组，其余组相对次序不变。 */
    fun commitAccountOrder(type: AccountType, orderedIds: List<Long>) {
        viewModelScope.launch {
            val all = _state.value.accounts
            val byId = all.associateBy { it.id }
            val valid = orderedIds.mapNotNull { byId[it] }
                .filter { !it.isArchived && it.type == type }
                .map { it.id }
            val active = all.filter { !it.isArchived }.sortedBy { it.sortOrder }
            val rebuilt = mutableListOf<Long>()
            val seen = mutableSetOf<AccountType>()
            active.forEach { a ->
                if (a.type !in seen) {
                    seen += a.type
                    if (a.type == type) {
                        rebuilt.addAll(valid)
                        rebuilt.addAll(active.filter { it.type == type && it.id !in valid }
                            .map { it.id })
                    } else {
                        rebuilt.addAll(active.filter { it.type == a.type }.map { it.id })
                    }
                }
            }
            repository.reorderAccounts(rebuilt + all.filter { it.isArchived }.map { it.id })
            reload()
        }
    }

    /**
     * 第 20 批：库拖动后的全局顺序落库。
     * onMove 在内存里交换全局 ids（各 AccountType 组的分段结构由 UI 分组渲染保证），
     * 松手一次性写回；未在列表中的（新增/归档）账户按 sortOrder 追加，避免丢项。
     */
    fun commitAccountOrderAll(orderedActiveIds: List<Long>) {
        viewModelScope.launch {
            val all = _state.value.accounts
            val byId = all.associateBy { it.id }
            val valid = orderedActiveIds.mapNotNull { byId[it] }
                .filter { !it.isArchived }
                .map { it.id }
            val rest = all.filter { !it.isArchived && it.id !in valid }
                .sortedBy { it.sortOrder }.map { it.id }
            val archived = all.filter { it.isArchived }.map { it.id }
            repository.reorderAccounts(valid + rest + archived)
            reload()
        }
    }

    /** 第三批：无流水可物理删除（重建）；有流水 MUST 归档。 */
    suspend fun deleteAccount(id: Long): String? {
        if (accountDao.linkedRecordCount(id) > 0) {
            return "该账户已有流水，不能删除；请用「归档」隐藏，或先清空流水"
        }
        return try {
            repository.deleteAccountIfUnused(id)
            reload()
            null
        } catch (e: Exception) {
            e.message ?: "删除账户失败"
        }
    }

    suspend fun setArchived(id: Long, archived: Boolean): String? = try {
        repository.setAccountArchived(id, archived)
        reload()
        null
    } catch (e: Exception) {
        e.message ?: "归档状态修改失败"
    }
}
