package com.zwz.offlineledger.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.data.local.dao.TrashDao
import com.zwz.offlineledger.data.local.entity.TrashEntryEntity
import com.zwz.offlineledger.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrashViewModel(
    private val trashDao: TrashDao,
    private val repository: LedgerRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<TrashEntryEntity>>(emptyList())
    val items: StateFlow<List<TrashEntryEntity>> = _items.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch { _items.value = trashDao.all() }
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
}
