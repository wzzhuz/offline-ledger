package com.zwz.offlineledger.ui.bulk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.bulk.BulkChange
import com.zwz.offlineledger.bulk.BulkTarget
import com.zwz.offlineledger.bulk.RuleActionKind
import com.zwz.offlineledger.bulk.RuleActionSpec
import com.zwz.offlineledger.bulk.RuleCondition
import com.zwz.offlineledger.bulk.RuleDefinition
import com.zwz.offlineledger.bulk.RuleEditorCatalog
import com.zwz.offlineledger.bulk.RuleEngine
import com.zwz.offlineledger.bulk.RuleField
import com.zwz.offlineledger.bulk.RuleGroupDef
import com.zwz.offlineledger.bulk.RuleOp
import com.zwz.offlineledger.bulk.RuleValueKind
import com.zwz.offlineledger.core.DataRefreshBus
import com.zwz.offlineledger.data.display.CategoryDisplay
import com.zwz.offlineledger.data.local.AppDatabase
import com.zwz.offlineledger.data.local.dao.CategoryDao
import com.zwz.offlineledger.data.local.dao.RecordDao
import com.zwz.offlineledger.data.local.entity.RuleEntity
import com.zwz.offlineledger.data.local.entity.RuleGroupEntity
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.ui.ledgerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RuleOption(val id: Long, val label: String)

data class RuleUiState(
    val busy: Boolean = false,
    val message: String = "",
    val error: String = "",
    val groups: List<RuleGroupDef> = emptyList(),
    val rules: List<RuleDefinition> = emptyList(),
    val preview: List<BulkChange> = emptyList(),
    val applied: List<BulkChange> = emptyList(),
    val report: String = "",
    val draft: RuleDefinition = RuleDefinition(),
    val editingId: Long? = null,
    val selectedGroupId: Long? = null,
    /** 含停用与归档，否则导入的旧分类圈不出来；label 已带「停用 / 归档」标记。 */
    val categories: List<RuleOption> = emptyList(),
    val accounts: List<RuleOption> = emptyList(),
    val suggestions: List<String> = emptyList(),
    /** 第 38 批：最近一次应用到历史的结果，仅内存态，列表页展示「上次应用」。 */
    val lastApplied: String = ""
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
    private var suggestJob: Job? = null

    private fun stamp(): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())

    init {
        reload()
        loadOptions()
    }

    private suspend fun targetsFromDb(): List<BulkTarget> = withContext(Dispatchers.IO) {
        recordDao.all().map { e ->
            BulkTarget(
                id = e.id,
                counterparty = e.counterparty,
                note = e.note,
                categoryId = e.categoryId,
                amountCents = e.amountCents,
                bookedAtEpochMilli = e.timestamp,
                excludedFromTotals = e.excludeFromStats,
                accountId = e.fromAccountId ?: e.toAccountId,
                currencyCode = e.currencyCode,
                importSource = e.importSource ?: ""
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
            val gid = _state.value.selectedGroupId ?: gs.firstOrNull()?.id
            val draft = _state.value.draft
            _state.value = _state.value.copy(
                groups = gs,
                rules = rs,
                selectedGroupId = gid,
                draft = if (draft.groupId == 0L && gid != null) draft.copy(groupId = gid) else draft
            )
        }
    }

    private fun loadOptions() {
        viewModelScope.launch {
            val cats = withContext(Dispatchers.IO) { categoryDao.all() }
            val byId = cats.associateBy { it.id }
            val catOpts = cats.map { c ->
                val parent = c.parentId?.let { byId[it] }
                val base = CategoryDisplay.resolve(parent?.name, c.name)
                val suffix = (if (!c.enabled) "（停用）" else "") + (if (c.isArchived) "（归档）" else "")
                RuleOption(c.id, base + suffix)
            }
            val accOpts = withContext(Dispatchers.IO) { db.accountDao().all() }.map { a ->
                RuleOption(a.id, a.name + (if (a.isArchived) "（归档）" else ""))
            }
            _state.value = _state.value.copy(categories = catOpts, accounts = accOpts)
        }
    }

    fun selectGroup(id: Long) {
        _state.value = _state.value.copy(
            selectedGroupId = id,
            draft = _state.value.draft.copy(groupId = id)
        )
    }

    fun addGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.ruleDao().insertGroup(
                    RuleGroupEntity(name = name.trim(), sortOrder = _state.value.groups.size)
                )
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
            _state.value = _state.value.copy(selectedGroupId = null)
            reload()
        }
    }

    fun newDraft() {
        val gid = _state.value.selectedGroupId ?: 0L
        _state.value = _state.value.copy(
            editingId = null,
            draft = RuleDefinition(
                groupId = gid,
                sortOrder = _state.value.rules.count { it.groupId == gid },
                conditions = listOf(RuleCondition()),
                actions = listOf(RuleActionSpec())
            ),
            message = "",
            error = ""
        )
    }

    fun editRule(r: RuleDefinition) {
        _state.value = _state.value.copy(
            editingId = r.id,
            selectedGroupId = r.groupId,
            draft = r,
            message = "",
            error = ""
        )
    }

    fun duplicateRule(r: RuleDefinition) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.ruleDao().insertDefinition(
                    RuleEntity(
                        groupId = r.groupId,
                        sortOrder = _state.value.rules.size,
                        definitionJson = json.encodeToString(
                            RuleDefinition.serializer(),
                            r.copy(id = 0, name = r.name + " 副本")
                        )
                    )
                )
            }
            reload()
        }
    }

    fun updateDraft(d: RuleDefinition) {
        _state.value = _state.value.copy(draft = d)
    }

    fun deleteRule(r: RuleDefinition) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.ruleDao().deleteDefinition(r.id) }
            if (_state.value.editingId == r.id) newDraft()
            reload()
        }
    }

    /** 联想：DAO 限量 10 条 + 200ms 防抖，MUST NOT 把全历史灌进内存。 */
    fun suggest(q: String, field: RuleField) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(200)
            val list = withContext(Dispatchers.IO) {
                when (field) {
                    RuleField.NOTE -> recordDao.searchNotes(q)
                    RuleField.COUNTERPARTY -> recordDao.searchCounterparties(q)
                    else -> emptyList()
                }
            }
            _state.value = _state.value.copy(suggestions = list)
        }
    }

    fun saveDraft(applyNow: Boolean) {
        val d = _state.value.draft
        if (d.conditions.isEmpty() || d.actions.isEmpty()) {
            _state.value = _state.value.copy(error = "至少要有 1 条触发条件和 1 个动作")
            return
        }
        fun condReady(c: RuleCondition): Boolean = when (RuleEditorCatalog.valueKind(c.field)) {
            RuleValueKind.CATEGORY -> c.categoryId != null
            RuleValueKind.ACCOUNT -> c.accountId != null
            RuleValueKind.AMOUNT_RANGE -> c.minAmountCents != null || c.maxAmountCents != null
            RuleValueKind.DATE_RANGE -> c.fromEpochMilli != null || c.toEpochMilli != null
            RuleValueKind.FLAG -> c.value.isNotBlank()
            else -> c.op == RuleOp.EMPTY || c.value.isNotBlank()
        }
        if (d.conditions.none { condReady(it) }) {
            _state.value = _state.value.copy(error = "触发条件还没填值；空关键词不会命中任何记录")
            return
        }
        if (d.actions.any { it.kind == RuleActionKind.SET_CATEGORY && it.categoryId == null }) {
            _state.value = _state.value.copy(error = "「设置分类为」还没选分类")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = "")
            try {
                val eid = _state.value.editingId ?: 0L
                withContext(Dispatchers.IO) {
                    db.ruleDao().insertDefinition(
                        RuleEntity(
                            id = eid,
                            groupId = d.groupId,
                            sortOrder = d.sortOrder,
                            definitionJson = json.encodeToString(RuleDefinition.serializer(), d)
                        )
                    )
                }
                reload()
                if (applyNow) {
                    val changes = withContext(Dispatchers.IO) {
                        RuleEngine.applyGroup(targetsFromDb(), listOf(d), true)
                    }
                    withContext(Dispatchers.IO) {
                        repository.applyBulkTargets(changes.map { it.after })
                    }
                    DataRefreshBus.notifyChanged()
                    _state.value = _state.value.copy(
                        busy = false,
                        preview = emptyList(),
                        applied = changes,
                        message = "规则已保存并立即应用 " + changes.size + " 条；可一键撤销。",
                        lastApplied = "命中 " + changes.size + " 条 · " + stamp()
                    )
                } else {
                    _state.value = _state.value.copy(busy = false, message = "规则已保存。")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = "保存失败：" + (e.message ?: "unknown")
                )
            }
        }
    }

    /** 批量录入生成的是普通规则，共用同一 RuleEngine 与撤销链路，不另开写入口。 */
    fun generateBatch(text: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = "", report = "")
            try {
                val gid = _state.value.selectedGroupId ?: _state.value.groups.firstOrNull()?.id
                if (gid == null) {
                    _state.value = _state.value.copy(busy = false, error = "请先新建一个规则组")
                    return@launch
                }
                val cats = withContext(Dispatchers.IO) { categoryDao.all() }
                val byId = cats.associateBy { it.id }
                fun resolve(n: String): Long? {
                    val q = n.trim()
                    return cats.firstOrNull { c ->
                        val parent = c.parentId?.let { byId[it] }
                        c.name == q || CategoryDisplay.resolve(parent?.name, c.name) == q
                    }?.id
                }
                val start = _state.value.rules.count { it.groupId == gid }
                val issues = mutableListOf<String>()
                val rules = RuleEditorCatalog.batchRules(text, gid, start, issues) { resolve(it) }
                if (rules.isEmpty()) {
                    _state.value = _state.value.copy(
                        busy = false,
                        error = "没有生成规则。" +
                            (if (issues.isEmpty()) "每行：旧分类名或对方关键词 | 新分类名"
                             else "\n" + issues.joinToString("\n"))
                    )
                    return@launch
                }
                val ts = withContext(Dispatchers.IO) { targetsFromDb() }
                val hits = RuleEditorCatalog.batchReport(ts, rules)
                val sb = StringBuilder()
                for (msg in issues) sb.append("⚠ ").append(msg).append('\n')
                for (h in hits) {
                    sb.append("第 ").append(h.index + 1).append(" 组 ").append(h.label)
                        .append("：命中 ").append(h.hits).append(" 条")
                    if (h.conflicts.isNotEmpty()) {
                        sb.append("，与第 ")
                            .append(h.conflicts.joinToString { (it + 1).toString() })
                            .append(" 组冲突")
                    }
                    if (h.hits == 0) sb.append("（未命中）")
                    sb.append('\n')
                }
                withContext(Dispatchers.IO) {
                    for (r in rules) {
                        db.ruleDao().insertDefinition(
                            RuleEntity(
                                groupId = r.groupId,
                                sortOrder = r.sortOrder,
                                definitionJson = json.encodeToString(RuleDefinition.serializer(), r)
                            )
                        )
                    }
                }
                reload()
                _state.value = _state.value.copy(
                    busy = false,
                    report = sb.toString(),
                    message = "已生成 " + rules.size + " 条规则；冲突按组内顺序首次命中优先。"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = "批量生成失败：" + (e.message ?: "unknown")
                )
            }
        }
    }

    fun previewRule(r: RuleDefinition) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = "")
            try {
                val changes = withContext(Dispatchers.IO) {
                    RuleEngine.applyGroup(targetsFromDb(), listOf(r), true)
                }
                _state.value = _state.value.copy(
                    busy = false,
                    preview = changes,
                    applied = emptyList(),
                    message = "规则「" + r.name + "」命中 " + changes.size + " 条；确认后才写入。"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = "预览失败：" + (e.message ?: "unknown"))
            }
        }
    }

    fun previewGroup(groupId: Long) {
        val g = _state.value.groups.firstOrNull { it.id == groupId } ?: return
        val rules = _state.value.rules.filter { it.groupId == groupId }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = "")
            try {
                val changes = withContext(Dispatchers.IO) {
                    RuleEngine.applyGroup(targetsFromDb(), rules, g.enabled)
                }
                _state.value = _state.value.copy(
                    busy = false,
                    preview = changes,
                    applied = emptyList(),
                    message = "规则组「" + g.name + "」命中 " + changes.size + " 条；确认后才写入。"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = "预览失败：" + (e.message ?: "unknown"))
            }
        }
    }

    fun commit() {
        val changes = _state.value.preview
        if (changes.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = "")
            try {
                withContext(Dispatchers.IO) {
                    repository.applyBulkTargets(changes.map { it.after })
                }
                DataRefreshBus.notifyChanged()
                _state.value = _state.value.copy(
                    busy = false,
                    applied = changes,
                    preview = emptyList(),
                    message = "已调整 " + changes.size + " 条；可一键撤销。",
                    lastApplied = "命中 " + changes.size + " 条 · " + stamp()
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
                    busy = false,
                    applied = emptyList(),
                    message = "已逐字段撤销 " + changes.size + " 条变更。"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = "撤销失败：" + (e.message ?: "unknown"))
            }
        }
    }
}

/**
 * 第 38 批：列表与编辑器拆成两个独立全屏页。
 * 本壳保留给旧调用点（BulkEditScreen），实际入口已改为 RuleListScreen。
 */
@Composable
fun RuleManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: RuleViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            RuleViewModel(ep.appDatabase(), ep.ledgerRepository(), ep.recordDao(), ep.categoryDao())
        }
    )
    RuleEditorContent(vm = vm, onBack = onBack)
}

/** 兼容旧调用点：全屏 Dialog，视觉与独立页一致。 */
@Composable
fun RuleManagerDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val vm: RuleViewModel = viewModel(
                factory = ledgerFactory(context) { ep ->
                    RuleViewModel(ep.appDatabase(), ep.ledgerRepository(), ep.recordDao(), ep.categoryDao())
                }
            )
            RuleEditorContent(vm = vm, onBack = onDismiss)
        }
    }
}

/**
 * 规则编辑器（独立页面）：只负责编辑一条规则，不承载已保存规则列表。
 * 列表、再次应用与「查看匹配记录」都在 RuleListScreen，靠设置内二级页状态栈切换，
 * 不新增 NavHost 路由、不新增表、不动 RuleEngine 求值。
 */
@Composable
fun RuleEditorContent(vm: RuleViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    var batchText by rememberSaveable { mutableStateOf("") }
    val groupName = state.groups.firstOrNull { it.id == state.draft.groupId }?.name ?: "未分组"

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回", maxLines = 1) }
            Text(
                if (state.editingId == null) "新建规则" else "编辑规则",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text("组：" + groupName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = state.draft.name,
                onValueChange = { vm.updateDraft(state.draft.copy(name = it)) },
                placeholder = { Text("规则标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("匹配：", maxLines = 1)
                TextButton(onClick = { vm.updateDraft(state.draft.copy(matchMode = "ALL")) }) {
                    Text(if (state.draft.matchMode == "ANY") "全部满足" else "● 全部满足", maxLines = 1)
                }
                TextButton(onClick = { vm.updateDraft(state.draft.copy(matchMode = "ANY")) }) {
                    Text(if (state.draft.matchMode == "ANY") "● 任一满足" else "任一满足", maxLines = 1)
                }
                Checkbox(
                    checked = state.draft.enabled,
                    onCheckedChange = { vm.updateDraft(state.draft.copy(enabled = it)) }
                )
                Text("启用", maxLines = 1)
            }

            SectionTitle("规则触发于")
            state.draft.conditions.forEachIndexed { i, c ->
                ConditionCard(
                    cond = c,
                    state = state,
                    vm = vm,
                    onChange = { nc ->
                        vm.updateDraft(
                            state.draft.copy(
                                conditions = state.draft.conditions.toMutableList().also { it[i] = nc }
                            )
                        )
                    },
                    onRemove = {
                        vm.updateDraft(
                            state.draft.copy(
                                conditions = state.draft.conditions.toMutableList().also { it.removeAt(i) }
                            )
                        )
                    }
                )
            }
            TextButton(
                onClick = {
                    vm.updateDraft(
                        state.draft.copy(conditions = state.draft.conditions + RuleCondition())
                    )
                }
            ) { Text("+ 添加触发条件", maxLines = 1) }

            SectionTitle("规则将执行")
            state.draft.actions.forEachIndexed { i, a ->
                ActionCard(
                    spec = a,
                    state = state,
                    vm = vm,
                    onChange = { na ->
                        vm.updateDraft(
                            state.draft.copy(
                                actions = state.draft.actions.toMutableList().also { it[i] = na }
                            )
                        )
                    },
                    onRemove = {
                        vm.updateDraft(
                            state.draft.copy(
                                actions = state.draft.actions.toMutableList().also { it.removeAt(i) }
                            )
                        )
                    }
                )
            }
            TextButton(
                onClick = {
                    vm.updateDraft(
                        state.draft.copy(actions = state.draft.actions + RuleActionSpec())
                    )
                }
            ) { Text("+ 添加动作", maxLines = 1) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { vm.saveDraft(true) }, enabled = !state.busy) {
                    Text("保存并立即应用", maxLines = 1)
                }
                Button(onClick = { vm.saveDraft(false) }, enabled = !state.busy) {
                    Text("保存规则", maxLines = 1)
                }
            }

            SectionTitle("批量录入（分类迁移）")
            Text(
                "每行一组：旧分类名或对方关键词 | 新分类名；支持全角｜、→、Tab 与分隔前后空格，空行与 # 注释跳过。" +
                    "一次生成 N 条普通规则，与单独配置的规则共用同一预览、写入与撤销链路。",
                style = MaterialTheme.typography.labelSmall
            )
            OutlinedTextField(
                value = batchText,
                onValueChange = { batchText = it },
                placeholder = { Text("每行一组映射，如：盒马 | 三餐 / 买菜做饭") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    batchText = "# 每行一组：旧值 | 新分类\n盒马|三餐 / 买菜做饭\n南京盒马网络科技有限公司|三餐 / 买菜做饭"
                }) { Text("填入示例", maxLines = 1) }
                PickerField(
                    current = "选新分类插入",
                    options = state.categories,
                    onPick = { o ->
                        batchText = (batchText.trimEnd() + "\n旧分类名或对方关键词|" + o.label)
                    }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.generateBatch(batchText) }, enabled = !state.busy) {
                    Text("生成规则并看命中报告", maxLines = 1)
                }
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
            Text(
                "保存后回「规则列表」可对历史记录再次应用；本页只改规则定义。",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        maxLines = 1
    )
}

@Composable
internal fun EnumDropdown(hint: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                (if (selected in labels.indices) labels[selected] else hint) + " ▾",
                maxLines = 1
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { i, l ->
                DropdownMenuItem(
                    text = { Text(l, maxLines = 1) },
                    onClick = { onPick(i); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun PickerField(current: String, options: List<RuleOption>, onPick: (RuleOption) -> Unit) {
    var show by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    TextButton(onClick = { show = true }) { Text(current + " ▾", maxLines = 1) }
    if (show) {
        Dialog(onDismissRequest = { show = false }) {
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("选择（含停用与归档）", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = q,
                        onValueChange = { q = it },
                        placeholder = { Text("搜索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        options.filter { it.label.contains(q, ignoreCase = true) }.forEach { o ->
                            TextButton(
                                onClick = { onPick(o); show = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(o.label, maxLines = 1) }
                        }
                    }
                    TextButton(onClick = { show = false }) { Text("关闭", maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun TextFieldWithSuggestions(
    value: String,
    label: String,
    suggestions: List<String>,
    onValue: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            placeholder = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (suggestions.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                suggestions.take(4).forEach { s ->
                    TextButton(onClick = { onValue(s) }) { Text(s, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun ConditionCard(
    cond: RuleCondition,
    state: RuleUiState,
    vm: RuleViewModel,
    onChange: (RuleCondition) -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val fields = RuleEditorCatalog.fields()
            EnumDropdown(
                "字段",
                fields.map { RuleEditorCatalog.fieldLabel(it) },
                fields.indexOf(cond.field)
            ) { idx ->
                val f = fields[idx]
                onChange(
                    cond.copy(
                        field = f,
                        op = RuleEditorCatalog.opsFor(f).first(),
                        value = "",
                        categoryId = null,
                        accountId = null,
                        minAmountCents = null,
                        maxAmountCents = null,
                        fromEpochMilli = null,
                        toEpochMilli = null
                    )
                )
            }
            val ops = RuleEditorCatalog.opsFor(cond.field)
            EnumDropdown(
                "操作符",
                ops.map { RuleEditorCatalog.opLabel(it) },
                ops.indexOf(cond.op).coerceAtLeast(0)
            ) { idx -> onChange(cond.copy(op = ops[idx])) }
            Checkbox(checked = cond.negate, onCheckedChange = { onChange(cond.copy(negate = it)) })
            Text("反", maxLines = 1)
            TextButton(onClick = onRemove) { Text("删", maxLines = 1) }
        }
        when (RuleEditorCatalog.valueKind(cond.field)) {
            RuleValueKind.CATEGORY -> PickerField(
                current = state.categories.firstOrNull { it.id == cond.categoryId }?.label ?: "选择分类",
                options = state.categories,
                onPick = { onChange(cond.copy(categoryId = it.id, value = it.label)) }
            )
            RuleValueKind.ACCOUNT -> PickerField(
                current = state.accounts.firstOrNull { it.id == cond.accountId }?.label ?: "选择账户",
                options = state.accounts,
                onPick = { onChange(cond.copy(accountId = it.id, value = it.label)) }
            )
            RuleValueKind.AMOUNT_RANGE -> Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = cond.minAmountCents?.toString() ?: "",
                    onValueChange = { onChange(cond.copy(minAmountCents = it.toLongOrNull())) },
                    placeholder = { Text("最小金额(分)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                OutlinedTextField(
                    value = cond.maxAmountCents?.toString() ?: "",
                    onValueChange = { onChange(cond.copy(maxAmountCents = it.toLongOrNull())) },
                    placeholder = { Text("最大金额(分)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
            RuleValueKind.DATE_RANGE -> Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = cond.fromEpochMilli?.toString() ?: "",
                    onValueChange = { onChange(cond.copy(fromEpochMilli = it.toLongOrNull())) },
                    placeholder = { Text("起始毫秒") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                OutlinedTextField(
                    value = cond.toEpochMilli?.toString() ?: "",
                    onValueChange = { onChange(cond.copy(toEpochMilli = it.toLongOrNull())) },
                    placeholder = { Text("结束毫秒") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }
            RuleValueKind.FLAG -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = cond.value == "true",
                    onCheckedChange = { onChange(cond.copy(value = it.toString())) }
                )
                Text("不计入收支 = 真", maxLines = 1)
            }
            else -> TextFieldWithSuggestions(
                value = cond.value,
                label = "触发值（可联想）",
                suggestions = state.suggestions,
                onValue = {
                    onChange(cond.copy(value = it))
                    vm.suggest(it, cond.field)
                }
            )
        }
    }
}

@Composable
private fun ActionCard(
    spec: RuleActionSpec,
    state: RuleUiState,
    vm: RuleViewModel,
    onChange: (RuleActionSpec) -> Unit,
    onRemove: () -> Unit
) {
    val kinds = RuleEditorCatalog.actions()
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EnumDropdown(
                "动作",
                kinds.map { RuleEditorCatalog.actionLabel(it) },
                kinds.indexOf(spec.kind).coerceAtLeast(0)
            ) { idx -> onChange(spec.copy(kind = kinds[idx])) }
            TextButton(onClick = onRemove) { Text("删", maxLines = 1) }
        }
        when (RuleEditorCatalog.actionValueKind(spec.kind)) {
            RuleValueKind.CATEGORY -> PickerField(
                current = state.categories.firstOrNull { it.id == spec.categoryId }?.label ?: "选择分类",
                options = state.categories,
                onPick = { onChange(spec.copy(categoryId = it.id)) }
            )
            RuleValueKind.FLAG -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = spec.excluded,
                    onCheckedChange = { onChange(spec.copy(excluded = it)) }
                )
                Text("标记为不计入收支", maxLines = 1)
            }
            else -> when (spec.kind) {
                RuleActionKind.RENAME_COUNTERPARTY -> TextFieldWithSuggestions(
                    value = spec.text,
                    label = "新交易对方（可联想）",
                    suggestions = state.suggestions,
                    onValue = {
                        onChange(spec.copy(text = it))
                        vm.suggest(it, RuleField.COUNTERPARTY)
                    }
                )
                RuleActionKind.REPLACE_NOTE -> Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = spec.from,
                        onValueChange = { onChange(spec.copy(from = it)) },
                        placeholder = { Text("把") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    )
                    OutlinedTextField(
                        value = spec.to,
                        onValueChange = { onChange(spec.copy(to = it)) },
                        placeholder = { Text("换成") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }
                RuleActionKind.APPEND_NOTE -> OutlinedTextField(
                    value = spec.text,
                    onValueChange = { onChange(spec.copy(text = it)) },
                    placeholder = { Text("追加到备注末尾") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                else -> Unit
            }
        }
    }
}
