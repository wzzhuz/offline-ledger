package com.zwz.offlineledger.ui.bulk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.bulk.BulkChange
import com.zwz.offlineledger.bulk.BulkTarget
import com.zwz.offlineledger.bulk.CategoryMigration
import com.zwz.offlineledger.bulk.RuleActionKind
import com.zwz.offlineledger.bulk.RuleActionSpec
import com.zwz.offlineledger.bulk.RuleCondition
import com.zwz.offlineledger.bulk.RuleDefinition
import com.zwz.offlineledger.bulk.RuleEngine
import com.zwz.offlineledger.bulk.RuleField
import com.zwz.offlineledger.bulk.RuleGroupDef
import com.zwz.offlineledger.bulk.RuleOp
import com.zwz.offlineledger.core.DataRefreshBus
import com.zwz.offlineledger.data.local.AppDatabase
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.entity.RuleEntity
import com.zwz.offlineledger.data.local.entity.RuleGroupEntity
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.ui.ledgerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class RuleUiState(
    val busy: Boolean = false,
    val message: String = "",
    val error: String = "",
    val groups: List<RuleGroupDef> = emptyList(),
    val rules: List<RuleDefinition> = emptyList(),
    val preview: List<BulkChange> = emptyList(),
    val applied: List<BulkChange> = emptyList(),
    val report: String = ""
)

class RuleViewModel(
    private val db: AppDatabase,
    private val repository: LedgerRepository,
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(RuleUiState())
    val state: StateFlow<RuleUiState> = _state.asStateFlow()

    init { reload() }

    private suspend fun targetsFromDb(): List<BulkTarget> = withContext(Dispatchers.IO) {
        recordDao.all().map { e ->
            BulkTarget(
                id = e.id,
                counterparty = e.counterparty,
                note = e.note,
                categoryId = e.categoryId,
                amountCents = e.amountCents,
                bookedAtEpochMilli = e.timestamp,
                excludedFromTotals = e.excludeFromStats
            )
        }
    }

    fun reload() {
        viewModelScope.launch {
            val gs = withContext(Dispatchers.IO) {
                db.ruleDao().groups().map { RuleGroupDef(it.id, it.name, it.enabled, it.sortOrder) }
            }
            val rs = withContext(Dispatchers.IO) {
                db.ruleDao().definitions().mapNotNull { d ->
                    try {
                        json.decodeFromString(RuleDefinition.serializer(), d.definitionJson)
                            .copy(id = d.id, groupId = d.groupId, sortOrder = d.sortOrder)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            _state.value = _state.value.copy(groups = gs, rules = rs)
        }
    }

    fun addGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.ruleDao().insertGroup(RuleGroupEntity(name = name.trim(), sortOrder = _state.value.groups.size))
            }
            reload()
        }
    }

    fun toggleGroup(g: RuleGroupDef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.ruleDao().insertGroup(
                    RuleGroupEntity(id = g.id, name = g.name, enabled = !g.enabled, sortOrder = g.sortOrder)
                )
            }
            reload()
        }
    }

    fun deleteGroup(g: RuleGroupDef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _state.value.rules.filter { it.groupId == g.id }.forEach {
                    db.ruleDao().deleteDefinition(it.id)
                }
                db.ruleDao().deleteGroup(g.id)
            }
            reload()
        }
    }

    /** 一条规则 = 一个「对方包含」条件 + 一个「改分类」动作；AND/OR 与取反可在 def 里扩。 */
    fun addRule(groupId: Long, name: String, keyword: String, anyMode: Boolean, targetCategoryId: Long?) {
        if (keyword.isBlank() || targetCategoryId == null) return
        val def = RuleDefinition(
            groupId = groupId,
            name = name.ifBlank { keyword },
            sortOrder = _state.value.rules.count { it.groupId == groupId },
            matchMode = if (anyMode) "ANY" else "ALL",
            conditions = listOf(RuleCondition(field = RuleField.COUNTERPARTY, op = RuleOp.CONTAINS, value = keyword.trim())),
            actions = listOf(RuleActionSpec(kind = RuleActionKind.SET_CATEGORY, categoryId = targetCategoryId))
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.ruleDao().insertDefinition(
                    RuleEntity(
                        groupId = groupId,
                        sortOrder = def.sortOrder,
                        definitionJson = json.encodeToString(RuleDefinition.serializer(), def)
                    )
                )
            }
            reload()
        }
    }

    fun addRuleByName(
        groupId: Long, name: String, keyword: String,
        anyMode: Boolean, targetCategoryName: String
    ) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                categoryDao.all().firstOrNull { it.name == targetCategoryName.trim() }?.id
            }
            if (id == null) {
                _state.value = _state.value.copy(error = "目标分类名不存在：" + targetCategoryName)
                return@launch
            }
            addRule(groupId, name, keyword, anyMode, id)
        }
    }

    fun deleteRule(r: RuleDefinition) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.ruleDao().deleteDefinition(r.id) }
            reload()
        }
    }

    fun previewGroup(groupId: Long) {
        val g = _state.value.groups.firstOrNull { it.id == groupId } ?: return
        val rules = _state.value.rules.filter { it.groupId == groupId }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = "", error = "")
            try {
                val changes = withContext(Dispatchers.IO) {
                    RuleEngine.applyGroup(targetsFromDb(), rules, g.enabled)
                }
                _state.value = _state.value.copy(
                    busy = false, preview = changes, applied = emptyList(),
                    message = "规则组「" + g.name + "」命中 " + changes.size + " 条；确认后才写入。"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = "预览失败：" + (e.message ?: "unknown"))
            }
        }
    }

    /** 每行：旧分类名或对方关键词|新分类名；两段都可写分类名，第二段必填。 */
    fun previewMigration(text: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = "", error = "", report = "")
            try {
                val (ts, cats) = withContext(Dispatchers.IO) {
                    Pair(targetsFromDb(), categoryDao.all())
                }
                fun idOf(s: String): Long? = cats.firstOrNull { it.name == s.trim() }?.id
                val mappings = text.lines().map { it.trim() }.filter { it.isNotBlank() }.mapNotNull { line ->
                    val parts = line.split("|").map { it.trim() }
                    if (parts.size < 2) return@mapNotNull null
                    val toId = idOf(parts[1]) ?: return@mapNotNull null
                    val fromId = idOf(parts[0])
                    CategoryMigration(
                        fromCategoryId = fromId,
                        counterpartyContains = if (fromId == null) parts[0] else "",
                        toCategoryId = toId,
                        label = parts[0] + " -> " + parts[1]
                    )
                }
                val report = RuleEngine.migrationReport(ts, mappings)
                val sb = StringBuilder()
                mappings.forEachIndexed { i, m ->
                    val h = report[i]
                    sb.append(m.label).append("：命中 ").append(h.hits).append(" 条")
                    if (h.conflicts.isNotEmpty()) sb.append("，与第 ").append(h.conflicts.joinToString { (it + 1).toString() }).append(" 组冲突")
                    sb.append('\n')
                }
                if (mappings.isEmpty()) sb.append("未解析出有效映射，请检查分类名是否与库内一致。\n")
                val changes = RuleEngine.migrate(ts, mappings)
                _state.value = _state.value.copy(
                    busy = false, preview = changes, applied = emptyList(),
                    report = sb.toString(),
                    message = "分类迁移将写入 " + changes.size + " 条；冲突组按首次命中优先。"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = "迁移预览失败：" + (e.message ?: "unknown"))
            }
        }
    }

    fun commit() {
        val changes = _state.value.preview
        if (changes.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = "")
            try {
                withContext(Dispatchers.IO) { repository.applyBulkTargets(changes.map { it.after }) }
                DataRefreshBus.notifyChanged()
                _state.value = _state.value.copy(
                    busy = false, applied = changes, preview = emptyList(),
                    message = "已调整 " + changes.size + " 条；可一键撤销。"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = "写入失败，账本未改动：" + (e.message ?: "unknown"))
            }
        }
    }

    fun revert() {
        val changes = _state.value.applied
        if (changes.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = "")
            try {
                withContext(Dispatchers.IO) {
                    repository.applyBulkTargets(RuleEngine.revert(changes))
                }
                DataRefreshBus.notifyChanged()
                _state.value = _state.value.copy(
                    busy = false, applied = emptyList(),
                    message = "已逐字段撤销 " + changes.size + " 条变更。"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = "撤销失败：" + (e.message ?: "unknown"))
            }
        }
    }
}

@Composable
fun RuleManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vm: RuleViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            RuleViewModel(ep.appDatabase(), ep.ledgerRepository(), ep.recordDao(), ep.categoryDao())
        }
    )
    val state by vm.state.collectAsState()
    var groupName by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var ruleName by remember { mutableStateOf("") }
    var anyMode by remember { mutableStateOf(false) }
    var targetCategoryName by remember { mutableStateOf("") }
    var migrationText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("规则与分类迁移", style = MaterialTheme.typography.titleLarge)
                Text(
                    "规则可保存、按组内顺序执行；分类迁移一次填 N 组映射。" +
                        "所有写入都先预览、单事务、可撤销。",
                    style = MaterialTheme.typography.labelSmall
                )

                Text("规则组", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                state.groups.forEach { g ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(g.name + if (g.enabled) "" else "（停用）", modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { vm.toggleGroup(g) }) { Text(if (g.enabled) "停用" else "启用") }
                        TextButton(onClick = { vm.deleteGroup(g) }) { Text("删除") }
                    }
                    state.rules.filter { it.groupId == g.id }.forEach { r ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("  · " + r.name, modifier = Modifier.weight(1f), maxLines = 1)
                            TextButton(onClick = { vm.deleteRule(r) }) { Text("移除") }
                        }
                    }
                    TextButton(onClick = { vm.previewGroup(g.id) }) { Text("预览「" + g.name + "」并应用") }
                }
                OutlinedTextField(
                    value = groupName, onValueChange = { groupName = it },
                    label = { Text("新规则组名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { vm.addGroup(groupName); groupName = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("新建规则组", maxLines = 1)
                }

                Text("新增规则", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it },
                    label = { Text("对方包含关键词") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetCategoryName, onValueChange = { targetCategoryName = it },
                    label = { Text("改到分类（填库内分类名）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ruleName, onValueChange = { ruleName = it },
                    label = { Text("规则名（可留空）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("匹配：", modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { anyMode = false }) { Text(if (anyMode) "全部满足" else "● 全部满足") }
                    TextButton(onClick = { anyMode = true }) { Text(if (anyMode) "● 任一满足" else "任一满足") }
                }
                Button(
                    onClick = {
                        val gid = state.groups.firstOrNull()?.id
                        if (gid != null) {
                            vm.addRuleByName(gid, ruleName, keyword, anyMode, targetCategoryName)
                            keyword = ""; ruleName = ""; targetCategoryName = ""
                        } else {
                            vm.addGroup("默认规则组")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存到第一个规则组", maxLines = 1) }

                Text("分类迁移映射表", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "每行：旧分类名或对方关键词|新分类名；冲突按首次命中优先，未命中的组会列出。",
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = migrationText, onValueChange = { migrationText = it },
                    label = { Text("每行一组映射") }, minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { vm.previewMigration(migrationText) }, modifier = Modifier.fillMaxWidth()) {
                    Text("预览迁移", maxLines = 1)
                }
                if (state.report.isNotBlank()) {
                    Text(state.report, style = MaterialTheme.typography.labelSmall)
                }

                if (state.message.isNotBlank()) {
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
                if (state.error.isNotBlank()) {
                    Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.preview.isNotEmpty()) {
                    Text(
                        "前后对比（前 10 条，共 " + state.preview.size + " 条）",
                        style = MaterialTheme.typography.titleSmall
                    )
                    state.preview.take(10).forEach { c ->
                        Text(
                            "#" + c.before.id + " 分类 " + c.before.categoryId + " → " + c.after.categoryId,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1
                        )
                    }
                    Button(onClick = { vm.commit() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("确认写入 " + state.preview.size + " 条", maxLines = 1)
                    }
                }
                if (state.applied.isNotEmpty()) {
                    Button(onClick = { vm.revert() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("一键撤销 " + state.applied.size + " 条", maxLines = 1)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
        }
    }
}
