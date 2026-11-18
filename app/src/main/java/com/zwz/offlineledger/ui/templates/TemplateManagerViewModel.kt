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
                categories = categoryDao.all().filter { !it.isArchived && it.enabled },
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

    /** 第三批：编辑模板（保留 id / createdAt / sortOrder）。 */
    fun updateTemplate(t: RecordTemplateEntity) {
        viewModelScope.launch {
            runCatching { repository.updateTemplate(t) }
                .onSuccess { message("已更新模板「" + t.name + "」"); refresh() }
                .onFailure { message(it.message ?: "更新模板失败") }
        }
    }

    /** 第三批：删除模板（草稿，不影响已生成流水）。 */
    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteTemplate(id) }
                .onSuccess { message("模板已删除"); refresh() }
                .onFailure { message(it.message ?: "删除模板失败") }
        }
    }

    fun saveRule(r: RecurringRuleEntity) {
        viewModelScope.launch {
            runCatching { repository.createRecurringRule(r) }
                .onSuccess { message("已保存周期规则「" + r.name + "」"); refresh() }
                .onFailure { message(it.message ?: "保存规则失败") }
        }
    }

    /** 第 6 批：模板顺序可调，复用 Repository 既有 reorderTemplates 单事务。 */
    fun moveTemplate(id: Long, delta: Int) {
        viewModelScope.launch {
            val all = _state.value.templates
            val active = all.filter { !it.isArchived }
            val i = active.indexOfFirst { it.id == id }
            val j = i + delta
            if (i < 0 || j < 0 || j >= active.size) return@launch
            val reordered = active.toMutableList().apply {
                val moved = removeAt(i)
                add(j, moved)
            }.map { it.id }
            val archived = all.filter { it.isArchived }.map { it.id }
            runCatching { repository.reorderTemplates(reordered + archived) }
                .onSuccess { refresh() }
                .onFailure { message(it.message ?: "调整顺序失败") }
        }
    }

    /** 第 16 批：拖拽落库，复用既有 reorderTemplates 单事务。 */
    fun commitTemplateOrder(orderedIds: List<Long>) {
        viewModelScope.launch {
            val all = _state.value.templates
            val byId = all.associateBy { it.id }
            val valid = orderedIds.mapNotNull { byId[it] }
                .filter { !it.isArchived }.map { it.id }
            val tail = all.filter { !it.isArchived && it.id !in valid }.map { it.id }
            val archived = all.filter { it.isArchived }.map { it.id }
            runCatching { repository.reorderTemplates(valid + tail + archived) }
                .onSuccess { refresh() }
                .onFailure { message(it.message ?: "调整顺序失败") }
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
