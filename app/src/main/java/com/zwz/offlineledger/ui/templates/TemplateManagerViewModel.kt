package com.zwz.offlineledger.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.offlineledger.data.local.dao.AccountDao
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecurringRuleDao
import com.zwz.offlineledger.data.local.dao.TemplateDao
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.data.local.entity.RecurringRuleEntity
import com.zwz.offlineledger.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TemplateManagerState(
    val templates: List<RecordTemplateEntity> = emptyList(),
    val rules: List<RecurringRuleEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val message: String = ""
)

/** 与项目其它页面一致，用 ledgerFactory 手动工厂，不引入 hilt-navigation-compose。 */
class TemplateManagerViewModel(
    private val repository: LedgerRepository,
    private val templateDao: TemplateDao,
    private val ruleDao: RecurringRuleDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao
) : ViewModel() {

    private val _state = MutableStateFlow(TemplateManagerState())
    val state: StateFlow<TemplateManagerState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                templates = templateDao.all(),
                rules = ruleDao.all(),
                categories = categoryDao.all().filter { !it.isArchived },
                accounts = accountDao.all().filter { !it.isArchived }
            )
        }
    }

    private fun message(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    fun saveTemplate(t: RecordTemplateEntity) {
        viewModelScope.launch {
            runCatching { repository.createTemplate(t) }
                .onSuccess { message("已保存模板「" + t.name + "」"); refresh() }
                .onFailure { message(it.message ?: "保存模板失败") }
        }
    }

    fun archiveTemplate(id: Long) {
        viewModelScope.launch {
            runCatching { repository.archiveTemplate(id) }
                .onSuccess { message("模板已归档，历史流水不受影响"); refresh() }
                .onFailure { message(it.message ?: "归档失败") }
        }
    }

    fun saveRule(r: RecurringRuleEntity) {
        viewModelScope.launch {
            runCatching { repository.createRecurringRule(r) }
                .onSuccess { message("已保存周期规则「" + r.name + "」"); refresh() }
                .onFailure { message(it.message ?: "保存规则失败") }
        }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setRuleEnabled(id, enabled) }
                .onSuccess { refresh() }
                .onFailure { message(it.message ?: "切换启用状态失败") }
        }
    }
}
