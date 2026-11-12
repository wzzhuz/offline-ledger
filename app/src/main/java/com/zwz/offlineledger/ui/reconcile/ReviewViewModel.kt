package com.zwz.offlineledger.ui.reconcile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.dao.ReviewRow
import com.zwz.offlineledger.engine.Reconciliation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewUiRow(
    val row: ReviewRow,
    val flags: Int,
    val labels: List<String>
)

data class ReviewState(
    val accountId: Long = 0L,
    val accountName: String = "",
    /** null = 锚点后；7 / 30 / 90 = 近 N 天。 */
    val rangeDays: Int? = null,
    val anchorLabel: String = "未对过账（显示全部）",
    val lookupMode: Reconciliation.LookupMode = Reconciliation.LookupMode.EXACT,
    val lookupText: String = "",
    val rangeMinText: String = "",
    val rangeMaxText: String = "",
    val onlySuspect: Boolean = false,
    val rows: List<ReviewUiRow> = emptyList(),
    val totalLoaded: Int = 0,
    val noMatchHint: String? = null,
    val loaded: Boolean = false
)

class ReviewViewModel(
    private val accountDao: AccountDao,
    private val recordDao: RecordDao
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    fun load(accountId: Long, differenceCents: Long) {
        viewModelScope.launch {
            val acc = accountDao.byId(accountId)
            val label = acc?.lastReconciledAt?.let {
                "上次对账 " + com.zwz.offlineledger.core.time.MonthRange.dateLabel(
                    com.zwz.offlineledger.core.time.LedgerClock
                        .fromEpochMilli(it).localDate
                )
            } ?: "未对过账（显示全部）"
            // 差额可能为负（实际比账面少），漏记支出仍应按其绝对金额反查。
            val diffYuan = Cents(kotlin.math.abs(differenceCents))
                .toYuanString().trimEnd('0').trimEnd('.')
            _state.value = ReviewState(
                accountId = accountId,
                accountName = acc?.name ?: "账户",
                anchorLabel = label,
                lookupText = if (differenceCents > 0) diffYuan else "",
                loaded = true
            )
            reload()
        }
    }

    fun setRange(days: Int?) {
        _state.value = _state.value.copy(rangeDays = days)
        viewModelScope.launch { reload() }
    }

    fun setMode(mode: Reconciliation.LookupMode) {
        _state.value = _state.value.copy(lookupMode = mode)
        viewModelScope.launch { reload() }
    }

    fun setLookupText(text: String) {
        _state.value = _state.value.copy(lookupText = text)
        viewModelScope.launch { reload() }
    }

    fun setRangeBounds(min: String, max: String) {
        _state.value = _state.value.copy(rangeMinText = min, rangeMaxText = max)
        viewModelScope.launch { reload() }
    }

    fun clearLookup() {
        _state.value = _state.value.copy(
            lookupText = "", rangeMinText = "", rangeMaxText = "",
            lookupMode = Reconciliation.LookupMode.EXACT
        )
        viewModelScope.launch { reload() }
    }

    fun setOnlySuspect(v: Boolean) {
        _state.value = _state.value.copy(onlySuspect = v)
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val s = _state.value
        val acc = accountDao.byId(s.accountId)
        val fromTs = when (val d = s.rangeDays) {
            null -> acc?.lastReconciledAt ?: 0L
            else -> System.currentTimeMillis() - d * 86_400_000L
        }

        val bounds = if (s.lookupMode == Reconciliation.LookupMode.RANGE) {
            Reconciliation.amountLookupBounds(
                0L, Reconciliation.LookupMode.RANGE,
                rangeMinCents = parseCents(s.rangeMinText),
                rangeMaxCents = parseCents(s.rangeMaxText)
            )
        } else {
            val amount = parseCents(s.lookupText)
            if (amount == null) null to null
            else Reconciliation.amountLookupBounds(amount, s.lookupMode)
        }

        val raw = recordDao.reviewRows(
            accountId = s.accountId,
            fromTimestamp = fromTs,
            minAmount = bounds.first,
            maxAmount = bounds.second,
            largeThreshold = Reconciliation.LARGE_AMOUNT_THRESHOLD_CENTS,
            limit = 500
        )

        // 第 4 个疑似标记在已由 SQL 限定的结果集上补算（非全表加载）。
        val duplicates = Reconciliation.markNearDuplicates(raw.map {
            Reconciliation.ReviewItem(it.id, it.amountCents, it.timestamp)
        })
        val all = raw.map { r ->
            val flags = r.suspectFlags or
                if (duplicates[r.id] == true) Reconciliation.SuspectFlags.NEAR_DUPLICATE else 0
            ReviewUiRow(r, flags, Reconciliation.labels(flags))
        }
        val filtered = if (s.onlySuspect) all.filter { it.flags != 0 } else all

        val hasLookup = bounds.first != null || bounds.second != null
        _state.value = s.copy(
            rows = filtered,
            totalLoaded = all.size,
            noMatchHint = if (hasLookup && all.isEmpty())
                "未找到 " + (s.lookupText.ifBlank { "该金额" }) + " 的记录" else null
        )
    }

    private fun parseCents(text: String): Long? {
        if (text.isBlank()) return null
        return try {
            Cents.fromYuan(text).value
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
