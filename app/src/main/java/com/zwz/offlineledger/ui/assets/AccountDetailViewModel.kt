package com.zwz.offlineledger.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.AccountRecordRow
import com.zwz.offlineledger.data.local.dao.BalanceAdjustmentDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.BalanceAdjustmentEntity
import com.zwz.offlineledger.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountDetailState(
    val account: AccountEntity? = null,
    val records: List<AccountRecordRow> = emptyList(),
    val adjustments: List<BalanceAdjustmentEntity> = emptyList(),
    val linkedRecordCount: Int = 0,
    val loaded: Boolean = false
)

class AccountDetailViewModel(
    private val accountDao: AccountDao,
    private val recordDao: RecordDao,
    private val adjustmentDao: BalanceAdjustmentDao,
    private val repository: LedgerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccountDetailState())
    val state: StateFlow<AccountDetailState> = _state.asStateFlow()

    fun load(accountId: Long) {
        viewModelScope.launch { reload(accountId) }
    }

    private suspend fun reload(accountId: Long) {
        _state.value = AccountDetailState(
            account = accountDao.byId(accountId),
            records = recordDao.accountRecords(accountId, 200),
            adjustments = adjustmentDao.byAccountId(accountId),
            linkedRecordCount = accountDao.linkedRecordCount(accountId),
            loaded = true
        )
    }

    fun updateMeta(
        accountId: Long, name: String, includeInTotal: Boolean,
        creditLimitCents: Long?, sortOrder: Int
    ) {
        viewModelScope.launch {
            repository.updateAccountMeta(
                accountId, name, includeInTotal, creditLimitCents, sortOrder
            )
            reload(accountId)
        }
    }

    fun archive(accountId: Long) {
        viewModelScope.launch {
            repository.archiveAccount(accountId)
            reload(accountId)
        }
    }

    /** 撤销走 Repository 事务：标记撤销 + 重算余额。 */
    fun revoke(adjustmentId: Long, accountId: Long) {
        viewModelScope.launch {
            repository.revokeAdjustment(adjustmentId)
            reload(accountId)
        }
    }
}
