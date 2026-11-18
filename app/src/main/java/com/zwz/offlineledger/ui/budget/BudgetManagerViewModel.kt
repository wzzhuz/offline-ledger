package com.zwz.offlineledger.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.data.local.dao.BudgetDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.entity.BudgetEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BudgetManagerState(
    val budgets: List<BudgetEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val message: String = ""
)

/** 与项目其它页面一致：ledgerFactory 手动工厂，不引入 hilt-navigation-compose。 */
class BudgetManagerViewModel(
    private val repository: LedgerRepository,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetManagerState())
    val state: StateFlow<BudgetManagerState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                budgets = budgetDao.all(),
                categories = categoryDao.all().filter { !it.isArchived && it.enabled }
            )
        }
    }

    fun save(b: BudgetEntity) {
        viewModelScope.launch {
            runCatching { repository.createBudget(b) }
                .onSuccess { _state.value = _state.value.copy(message = "已保存预算「" + b.name + "」"); refresh() }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "保存预算失败") }
        }
    }

    fun archive(id: Long) {
        viewModelScope.launch {
            runCatching { repository.archiveBudget(id) }
                .onSuccess { _state.value = _state.value.copy(message = "预算已归档"); refresh() }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "归档失败") }
        }
    }
}
