package com.zwz.offlineledger.ui.bulk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.bulk.BulkAction
import com.zwz.offlineledger.bulk.BulkChange
import com.zwz.offlineledger.bulk.BulkConditions
import com.zwz.offlineledger.bulk.BulkEditEngine
import com.zwz.offlineledger.bulk.BulkTarget
import com.zwz.offlineledger.core.DataRefreshBus
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.settings.SettingsPageTopBar
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BulkUiState(
    val busy: Boolean = false,
    val message: String = "",
    val error: String = "",
    val categories: List<CategoryEntity> = emptyList(),
    val preview: List<BulkChange> = emptyList(),
    val applied: List<BulkChange> = emptyList()
)

class BulkEditViewModel(
    private val repository: LedgerRepository,
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(BulkUiState())
    val state: StateFlow<BulkUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { st -> st.copy(categories = categoryDao.all()) }
        }
    }

    fun preview(conditions: BulkConditions, actions: List<BulkAction>) {
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "") }
            try {
                val changes = withContext(Dispatchers.IO) {
                    val targets = recordDao.all().map { e ->
                        BulkTarget(
                            id = e.id,
                            counterparty = e.counterparty,
                            note = e.note,
                            categoryId = e.categoryId,
                            amountCents = e.amountCents,
                            bookedAtEpochMilli = e.timestamp,
                            excludedFromTotals = e.excludeFromStats,
                            description = e.description
                        )
                    }
                    BulkEditEngine.apply(targets, conditions, actions)
                }
                _state.update { st -> st.copy(
                    busy = false,
                    preview = changes,
                    applied = emptyList(),
                    message = "命中 " + changes.size + " 条；确认后才会写入。"
                ) }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "预览失败：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }

    fun commit() {
        val changes = _state.value.preview
        if (changes.isEmpty()) return
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "") }
            try {
                withContext(Dispatchers.IO) {
                    repository.applyBulkTargets(changes.map { it.after })
                }
                DataRefreshBus.notifyChanged()
                _state.update { st -> st.copy(
                    busy = false,
                    applied = changes,
                    preview = emptyList(),
                    message = "已调整 " + changes.size + " 条；下方按钮可一键撤销。"
                ) }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "写入失败，账本未改动：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }

    /** changeset 保存在本次页面会话内，逐字段还原，不依赖当前库状态。 */
    fun revert() {
        val changes = _state.value.applied
        if (changes.isEmpty()) return
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "") }
            try {
                withContext(Dispatchers.IO) {
                    repository.applyBulkTargets(BulkEditEngine.revert(changes))
                }
                DataRefreshBus.notifyChanged()
                _state.update { st -> st.copy(
                    busy = false,
                    applied = emptyList(),
                    message = "已逐字段撤销 " + changes.size + " 条变更。"
                ) }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "撤销失败：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }
}

private fun yuanToCents(s: String): Long? =
    s.toBigDecimalOrNull()?.movePointRight(2)?.toLong()

@Composable
fun BulkEditScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: BulkEditViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            BulkEditViewModel(ep.ledgerRepository(), ep.recordDao(), ep.categoryDao())
        }
    )
    val state by vm.state.collectAsState()
    var showRules by remember { mutableStateOf(false) }

    // 第 37 批：规则编辑器改为独立全屏页，不再塞进滚动列末尾的弹层。
    if (showRules) {
        RuleManagerScreen(onBack = { showRules = false })
        return
    }

    var cpContains by remember { mutableStateOf("") }
    var noteContains by remember { mutableStateOf("") }
    var minYuan by remember { mutableStateOf("") }
    var maxYuan by remember { mutableStateOf("") }
    var newCounterparty by remember { mutableStateOf("") }
    var noteFrom by remember { mutableStateOf("") }
    var noteTo by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Long?>(null) }
    var categoryChanged by remember { mutableStateOf(false) }
    var setExcluded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    fun conditions() = BulkConditions(
        counterpartyContains = cpContains.takeIf { it.isNotBlank() },
        noteContains = noteContains.takeIf { it.isNotBlank() },
        minAmountCents = yuanToCents(minYuan),
        maxAmountCents = yuanToCents(maxYuan)
    )

    fun actions(): List<BulkAction> = buildList {
        if (categoryChanged) add(BulkAction.SetCategory(selectedCategory))
        if (newCounterparty.isNotBlank()) add(BulkAction.RenameCounterparty(newCounterparty))
        if (noteFrom.isNotBlank()) add(BulkAction.ReplaceInNote(noteFrom, noteTo))
        if (setExcluded) add(BulkAction.SetExcluded(true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SettingsPageTopBar("批量规整", onBack)
        Text(
            text = "对历史流水做一次性调整：先预览命中与前后对比，确认后单事务写入，可一键撤销。" +
                "规则定义不持久化，不会在导入后自动触发。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral
        )
        Text(text = "筛选条件（全部满足才命中）", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = cpContains, onValueChange = { cpContains = it },
            label = { Text("交易对方包含") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = noteContains, onValueChange = { noteContains = it },
            label = { Text("备注包含") }, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minYuan, onValueChange = { minYuan = it },
                label = { Text("金额下限（元）") }, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = maxYuan, onValueChange = { maxYuan = it },
                label = { Text("金额上限（元）") }, modifier = Modifier.weight(1f)
            )
        }
        Text(text = "调整动作（留空即不改）", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "修改分类", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { categoryChanged = !categoryChanged }) {
                Text(if (categoryChanged) "已启用" else "不修改")
            }
        }
        if (categoryChanged) {
            Box {
                TextButton(onClick = { categoryExpanded = true }) {
                    val label = state.categories.firstOrNull { it.id == selectedCategory }?.name
                        ?: "不设分类"
                    Text(text = "改为：" + label)
                }
                DropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("不设分类") },
                        onClick = {
                            selectedCategory = null
                            categoryExpanded = false
                        }
                    )
                    state.categories.filter { it.enabled }.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name) },
                            onClick = {
                                selectedCategory = c.id
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = newCounterparty, onValueChange = { newCounterparty = it },
            label = { Text("交易对方改为") }, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = noteFrom, onValueChange = { noteFrom = it },
                label = { Text("备注替换：查找") }, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = noteTo, onValueChange = { noteTo = it },
                label = { Text("替换为") }, modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "设为不计入收支",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = setExcluded, onCheckedChange = { setExcluded = it })
        }
        Button(
            onClick = { vm.preview(conditions(), actions()) },
            enabled = !state.busy && actions().isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("预览命中") }

        if (state.message.isNotBlank()) {
            Text(text = state.message, style = MaterialTheme.typography.bodySmall)
        }
        if (state.error.isNotBlank()) {
            Text(
                text = state.error,
                color = LedgerColors.ExpenseRed,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.preview.isNotEmpty()) {
            Text(
                text = "前后对比（前 20 条，共 " + state.preview.size + " 条）",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                items(state.preview.take(20)) { change ->
                    Text(
                        text = "#" + change.before.id + "  " +
                            change.before.counterparty.take(10) + " → " +
                            change.after.counterparty.take(10) + " · " +
                            change.before.note.take(10) + " → " +
                            change.after.note.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            Button(
                onClick = { vm.commit() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("确认写入 " + state.preview.size + " 条") }
        }
        if (state.applied.isNotEmpty()) {
            Button(
                onClick = { vm.revert() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("一键撤销 " + state.applied.size + " 条") }
        }
        Button(
            onClick = { showRules = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("规则编辑器", maxLines = 1) }
    }
}
