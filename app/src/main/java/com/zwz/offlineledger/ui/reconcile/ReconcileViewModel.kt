package com.zwz.offlineledger.ui.reconcile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.engine.Reconciliation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReconcileState(
    val accountId: Long = 0L,
    val accountName: String = "",
    val bookCents: Long = 0L,
    val actualInput: String = "",
    val memo: String = "",
    val parsedActualCents: Long? = null,
    val deltaCents: Long? = null,
    val isTail: Boolean = false,
    val anchorAfterCount: Int = 0,
    val loaded: Boolean = false,
    val errorText: String? = null,
    val done: Boolean = false
)

class ReconcileViewModel(
    private val accountDao: AccountDao,
    private val recordDao: RecordDao,
    private val repository: LedgerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReconcileState())
    val state: StateFlow<ReconcileState> = _state.asStateFlow()

    fun load(accountId: Long) {
        viewModelScope.launch {
            val acc = accountDao.byId(accountId) ?: return@launch
            val fromTs = acc.lastReconciledAt ?: 0L
            val count = recordDao.reviewRows(
                accountId = accountId,
                fromTimestamp = fromTs,
                minAmount = null,
                maxAmount = null,
                largeThreshold = Reconciliation.LARGE_AMOUNT_THRESHOLD_CENTS,
                limit = 500
            ).size
            _state.value = ReconcileState(
                accountId = accountId,
                accountName = acc.name,
                bookCents = acc.balanceCents,
                anchorAfterCount = count,
                loaded = true
            )
        }
    }

    fun setActual(text: String) {
        val parsed = parse(text)
        val s = _state.value
        val delta = if (parsed == null) null
        else Reconciliation.difference(s.bookCents, parsed)
        _state.value = s.copy(
            actualInput = text,
            parsedActualCents = parsed,
            deltaCents = delta,
            isTail = delta != null && Reconciliation.isTailDifference(delta),
            errorText = if (text.isNotBlank() && parsed == null) "金额格式非法" else null
        )
    }

    fun setMemo(text: String) {
        _state.value = _state.value.copy(memo = text)
    }

    /** force=true 表示大额差额下用户仍选择直接校准（备注必填已在 UI 与 Repository 双保险）。 */
    fun reconcile(source: String) {
        val s = _state.value
        val actual = s.parsedActualCents
        if (actual == null) {
            _state.value = s.copy(errorText = "请先输入有效的实际余额")
            return
        }
        viewModelScope.launch {
            try {
                repository.reconcile(s.accountId, actual, s.memo.trim(), source)
                _state.value = s.copy(done = true, errorText = null)
            } catch (e: IllegalArgumentException) {
                _state.value = s.copy(errorText = e.message ?: "对账失败")
            }
        }
    }

    private fun parse(text: String): Long? {
        if (text.isBlank()) return null
        return try {
            Cents.fromYuan(text).value
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
