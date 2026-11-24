package com.zwz.offlineledger.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.local.entity.TrashEntryEntity
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.ui.assets.accountBaseLabel
import com.zwz.offlineledger.ui.components.formatCents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 第 5 批：回收站行改成可读的完整信息，不再只有日期、类型、金额、备注。 */
data class TrashUiRow(
    val originalId: Long,
    val dateTime: String,
    val typeLabel: String,
    val amountText: String,
    val counterparty: String,
    val categoryText: String,
    val accountText: String,
    val note: String
)

class TrashViewModel(
    private val trashDao: TrashDao,
    private val repository: LedgerRepository,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao
) : ViewModel() {

    private val _items = MutableStateFlow<List<TrashUiRow>>(emptyList())
    val items: StateFlow<List<TrashUiRow>> = _items.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val categories = categoryDao.all().associateBy { it.id }
            val accounts = accountDao.all().associateBy { it.id }
            _items.value = trashDao.all().map { e -> e.toUiRow(categories, accounts) }
        }
    }

    /** 恢复走 Repository 的事务：插回 records + 重算余额，与 insert 同路径。 */
    fun restore(originalId: Long) {
        viewModelScope.launch {
            repository.restoreRecord(originalId)
            reload()
        }
    }

    fun purge(originalId: Long) {
        viewModelScope.launch {
            trashDao.deleteByOriginalId(originalId)
            reload()
        }
    }

    private fun TrashEntryEntity.toUiRow(
        categories: Map<Long, com.zwz.offlineledger.data.local.entity.CategoryEntity>,
        accounts: Map<Long, com.zwz.offlineledger.data.local.entity.AccountEntity>
    ): TrashUiRow {
        val z = java.time.Instant.ofEpochMilli(timestamp).atZone(LedgerClock.zoneId)
        val hhmm = "%02d:%02d".format(z.hour, z.minute)
        val root = rootCategoryId?.let { categories[it]?.name }
        val leaf = categoryId?.let { categories[it]?.name }
        val categoryText = when {
            root != null && leaf != null && root != leaf -> root + " / " + leaf
            else -> leaf ?: root ?: "转账"
        }
        // 第 62 批：回收站行的转出 / 转入账户同样带卡号。
        val from = fromAccountId?.let { accounts[it]?.let { a -> accountBaseLabel(a) } }
        val to = toAccountId?.let { accounts[it]?.let { a -> accountBaseLabel(a) } }
        val accountText = when {
            from != null && to != null -> from + " → " + to
            else -> from ?: to ?: "-"
        }
        return TrashUiRow(
            originalId = originalId,
            dateTime = com.zwz.offlineledger.core.time.MonthRange.dateLabel(localDate) +
                " " + hhmm,
            typeLabel = when (type) {
                com.zwz.offlineledger.data.model.RecordType.EXPENSE -> "支出"
                com.zwz.offlineledger.data.model.RecordType.INCOME -> "收入"
                com.zwz.offlineledger.data.model.RecordType.TRANSFER -> "转账"
                com.zwz.offlineledger.data.model.RecordType.SETTLEMENT -> "报销 / 退款"
            },
            amountText = formatCents(amountCents) + " 元",
            counterparty = counterparty,
            categoryText = categoryText,
            accountText = accountText,
            note = note
        )
    }
}
