package com.zwz.offlineledger.ui.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.data.local.entity.RecurringRuleEntity
import com.zwz.offlineledger.data.model.Cadence
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.components.ListDivider
import com.zwz.offlineledger.ui.components.rememberReorderDrag
import com.zwz.offlineledger.ui.components.ReorderBounds
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.settings.SettingsPageTopBar
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 5 批：管理类页面统一为独立全屏页，不再套「很大的 Dialog」。
 * 第 12 批：再改为「TopBar 固定 + 入口 + LazyColumn 纯列表 + 编辑独立视图」，
 * 解决「模板越多新增表单越往下移」与局部留白；并同步全仓已统一写法
 * （placeholder 输入框 / FilterChip 底色 / 下拉限高+搜索）。
 */
private data class TemplateEditorState(
    val isRule: Boolean,
    val editing: RecordTemplateEntity? = null
)

@Composable
fun TemplateManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: TemplateManagerViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            TemplateManagerViewModel(
                ep.ledgerRepository(), ep.templateDao(), ep.recurringRuleDao(),
                ep.categoryDao(), ep.accountDao()
            )
        }
    )
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var editor by remember { mutableStateOf<TemplateEditorState?>(null) }
    var deleting by remember { mutableStateOf<RecordTemplateEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsPageTopBar(
            title = when {
                editor == null -> "模板与周期记账"
                editor!!.editing != null -> "编辑模板"
                else -> "新增"
            },
            onBack = {
                if (editor != null) editor = null else onBack()
            },
            onAdd = if (editor == null) {
                { editor = TemplateEditorState(isRule = tab == 1) }
            } else null
        )
        if (editor == null) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("模板") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("周期规则") })
            }
            TemplateList(
                tab = tab,
                state = state,
                vm = vm,
                onEdit = { editor = TemplateEditorState(isRule = false, editing = it) },
                onDelete = { deleting = it },
                modifier = Modifier.weight(1f)
            )
        } else {
            val e = editor!!
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (e.isRule)
                        "周期规则会自动写入流水，金额与账户必填。"
                    else "模板是预填草稿：金额可留空，选模板后自动填充并可自由调整。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )
                QuickEntryForm(
                    isRule = e.isRule,
                    editing = if (e.isRule) null else e.editing,
                    categories = state.categories,
                    accountNames = state.accounts.map { it.id to it.name },
                    onSubmitTemplate = { t ->
                        if (t.id != 0L) vm.updateTemplate(t) else vm.saveTemplate(t)
                        editor = null
                    },
                    onSubmitRule = { r -> vm.saveRule(r); editor = null },
                    onCancelEdit = { editor = null }
                )
            }
        }
    }

    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除模板") },
            text = { Text("确定删除「" + t.name + "」？已由它生成的历史流水不受影响。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTemplate(t.id); deleting = null }) {
                    Text("删除", color = LedgerColors.ExpenseRed)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

/* ------------------------------------------------------------------ */
/* 纯列表：LazyColumn + 每行一个「⋯」菜单；周期规则行保留启用 Switch        */
/* ------------------------------------------------------------------ */
@Composable
private fun TemplateList(
    tab: Int,
    state: TemplateManagerState,
    vm: TemplateManagerViewModel,
    onEdit: (RecordTemplateEntity) -> Unit,
    onDelete: (RecordTemplateEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // 第 16 批：rememberReorderDrag 是 @Composable，MUST 放在函数体，
    // 不能放在 LazyColumn 的 LazyListScope（非 composable）里——首轮 CI 第 3 条错误。
    val activeTemplates = state.templates.filter { !it.isArchived }
    // 第 17 批：拖拽期间禁用父滚动 + 边缘自动滚动 + 实测行高。
    val listState = rememberLazyListState()
    val reorderBounds = remember { ReorderBounds() }
    var anyDragging by remember { mutableStateOf(false) }
    val tplDrag = rememberReorderDrag(
        ids = activeTemplates.map { it.id },
        rowHeightDp = 44,
        bounds = reorderBounds,
        onDraggingChange = { anyDragging = it },
        onEdgeScroll = { d -> listState.dispatchRawDelta(d); Unit },
        onCommit = { ids -> vm.commitTemplateOrder(ids) }
    )
    val orderedTemplates = tplDrag.orderedIds.mapNotNull { id ->
        activeTemplates.firstOrNull { it.id == id }
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = !anyDragging,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .onGloballyPositioned {
                reorderBounds.topPx = it.positionInRoot().y
                reorderBounds.bottomPx = reorderBounds.topPx + it.size.height
            }
    ) {
        if (state.message.isNotBlank()) {
            item {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
        if (tab == 0) {
            val active = activeTemplates
            if (active.isEmpty()) {
                item { Text("还没有模板。点右上角 + 新增一个。", modifier = Modifier.padding(vertical = 6.dp)) }
            }
            items(orderedTemplates, key = { it.id }) { t ->
                var menu by remember { mutableStateOf(false) }
                val amount = if (t.amountCents > 0L)
                    Cents(t.amountCents).toYuanString() + " 元" else "金额待填"
                val path = categoryPath(t.categoryId, state.categories)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .then(tplDrag.itemModifier(t.id))
                        .then(tplDrag.rowMeasureModifier())
                ) {
                    Text(
                        text = t.name + " · " + typeLabel(t.type) + " · " + amount +
                            (if (path.isBlank()) "" else " · " + path),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Box(
                        modifier = tplDrag.handleModifier(t.id).size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("≡", style = MaterialTheme.typography.titleMedium,
                            color = LedgerColors.Neutral)
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("上移") },
                            onClick = { menu = false; vm.moveTemplate(t.id, -1) })
                        DropdownMenuItem(text = { Text("下移") },
                            onClick = { menu = false; vm.moveTemplate(t.id, 1) })
                        DropdownMenuItem(text = { Text("编辑") },
                            onClick = { menu = false; onEdit(t) })
                        DropdownMenuItem(
                            text = { Text("删除", color = LedgerColors.ExpenseRed) },
                            onClick = { menu = false; onDelete(t) }
                        )
                    }
                }
                ListDivider(Modifier.padding(horizontal = 4.dp))
            }
            val archived = state.templates.count { it.isArchived }
            if (archived > 0) {
                item {
                    Text(
                        text = "已归档 $archived 个模板",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        } else {
            if (state.rules.isEmpty()) {
                item { Text("还没有周期规则。点右上角 + 新增。", modifier = Modifier.padding(vertical = 6.dp)) }
            }
            items(state.rules, key = { it.id }) { r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                ) {
                    Text(
                        text = r.name + " · " + Cents(r.amountCents).toYuanString() +
                            " 元 · " + cadenceLabel(r.cadence),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Switch(
                        checked = r.isEnabled,
                        onCheckedChange = { vm.setRuleEnabled(r.id, it) }
                    )
                }
            }
        }
    }
}

private fun cadenceLabel(c: Cadence): String = when (c) {
    Cadence.DAILY -> "每天"
    Cadence.WEEKLY -> "每周"
    Cadence.MONTHLY -> "每月"
}

/**
 * 模板与规则共用的表单。金额在提交时用 Cents.fromYuan 转 Long 分，
 * 禁止 Float / Double 参与（金额铁律）。模板金额可留空（草稿语义）。
 * 第 12 批：浮动 label → placeholder + bodyMedium + heightIn(min=40)；
 * 选中态由 AssistChip + 文字前缀改为 FilterChip 底色；下拉限高 + 搜索。
 */
@Composable
private fun QuickEntryForm(
    isRule: Boolean,
    editing: RecordTemplateEntity?,
    categories: List<CategoryEntity>,
    accountNames: List<Pair<Long, String>>,
    onSubmitTemplate: (RecordTemplateEntity) -> Unit,
    onSubmitRule: (RecurringRuleEntity) -> Unit,
    onCancelEdit: () -> Unit
) {
    val eid = editing?.id ?: 0L
    var name by remember(eid) { mutableStateOf(editing?.name ?: "") }
    var amountText by remember(eid) {
        mutableStateOf(if (editing != null && editing.amountCents > 0L)
            Cents(editing.amountCents).toYuanString() else "")
    }
    var type by remember(eid) { mutableStateOf(editing?.type ?: RecordType.EXPENSE) }
    var categoryId by remember(eid) { mutableStateOf(editing?.categoryId) }
    var fromAccountId by remember(eid) { mutableStateOf(editing?.fromAccountId) }
    var toAccountId by remember(eid) { mutableStateOf(editing?.toAccountId) }
    var note by remember(eid) { mutableStateOf(editing?.note ?: "") }
    var counterpartyText by remember(eid) { mutableStateOf(editing?.counterparty ?: "") }
    var excluded by remember(eid) { mutableStateOf(editing?.excludeFromStats ?: false) }
    var cadence by remember(eid) { mutableStateOf(Cadence.MONTHLY) }
    var dayText by remember(eid) { mutableStateOf("1") }
    var error by remember(eid) { mutableStateOf<String?>(null) }

    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val rootId = selectedCategory?.let { it.parentId ?: it.id }

    // 第三批：下拉只显示与当前收支类型一致、未归档的分类；
    // 一级在前、二级紧跟父级，标签写成「一级 / 二级」。
    val visibleCategories = categories.filter { it.type == type && !it.isArchived }
    fun rootKey(c: CategoryEntity): Long = if (c.level == 0) c.id else (c.parentId ?: 0L)
    val orderedCategories = visibleCategories.sortedWith(
        compareBy<CategoryEntity> { rootKey(it) }.thenBy { it.level }.thenBy { it.sortOrder }
    )
    val categoryOptions = orderedCategories.map { c ->
        val label = if (c.level == 1) {
            val parent = categories.firstOrNull { it.id == c.parentId }?.name
            (if (parent.isNullOrBlank()) "" else parent + " / ") + c.name
        } else c.name
        c.id to label
    }

    Column {
        ManagerTextField(
            value = name, onValueChange = { name = it },
            placeholder = "名称（如 午餐 / 房租）"
        )
        ManagerTextField(
            value = amountText, onValueChange = { amountText = it },
            placeholder = if (isRule) "金额（元，必填）" else "金额（元，可留空，记一笔时再填）",
            keyboardType = KeyboardType.Decimal
        )

        Text("类型", style = MaterialTheme.typography.labelMedium)
        Row {
            listOf(RecordType.EXPENSE, RecordType.INCOME, RecordType.TRANSFER)
                .filter { it != RecordType.TRANSFER || !isRule }.forEach { rt ->
                    FilterChip(
                        selected = type == rt,
                        onClick = { type = rt; categoryId = null },
                        label = { Text(typeLabel(rt)) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
        }

        if (type != RecordType.TRANSFER) {
            DropdownSelector(
                label = "分类：" + (selectedCategory?.name ?: "未选"),
                options = categoryOptions,
                onSelect = { categoryId = it }
            )
        }
        if (type != RecordType.INCOME) {
            DropdownSelector(
                label = "转出账户（可留空）：" + accountName(accountNames, fromAccountId),
                options = accountNames,
                onSelect = { fromAccountId = it }
            )
        }
        if (type != RecordType.EXPENSE) {
            DropdownSelector(
                label = "转入账户（可留空）：" + accountName(accountNames, toAccountId),
                options = accountNames,
                onSelect = { toAccountId = it }
            )
        }

        ManagerTextField(
            value = note, onValueChange = { note = it },
            placeholder = "备注（可选）"
        )
        // 第三批：转账不显示交易对方；收入显示「付款方 / 来源」。
        if (type != RecordType.TRANSFER) {
            ManagerTextField(
                value = counterpartyText, onValueChange = { counterpartyText = it },
                placeholder = if (type == RecordType.INCOME) "付款方 / 来源（可选）" else "收款方（可选）"
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("不计入收支统计", modifier = Modifier.weight(1f))
            Switch(checked = excluded, onCheckedChange = { excluded = it })
        }

        if (isRule) {
            Text("周期", style = MaterialTheme.typography.labelMedium)
            Row {
                Cadence.entries.forEach { c ->
                    FilterChip(
                        selected = cadence == c,
                        onClick = { cadence = c },
                        label = { Text(cadenceLabel(c)) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            ManagerTextField(
                value = dayText, onValueChange = { dayText = it },
                placeholder = if (cadence == Cadence.MONTHLY) "每月几号（1-31）"
                else "每周几（1=周一..7=周日）",
                keyboardType = KeyboardType.Number
            )
        }

        error?.let {
            Text(it, color = LedgerColors.ExpenseRed, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val amount = if (amountText.isBlank()) 0L else try {
                    Cents.fromYuan(amountText).value
                } catch (e: IllegalArgumentException) {
                    error = e.message ?: "金额格式非法"; return@Button
                }
                if (name.isBlank()) { error = "请填名称"; return@Button }
                if (amount < 0L) { error = "金额不能为负"; return@Button }
                if (isRule && amount <= 0L) { error = "周期规则金额必须大于 0"; return@Button }
                if (type != RecordType.TRANSFER && categoryId != null &&
                    visibleCategories.none { it.id == categoryId }
                ) { error = "分类与收支类型不匹配，请重新选择"; return@Button }
                // 第 6 批：模板是草稿，账户字段全部可选，生成时用默认账户兜底；
                // 周期规则会自动写入流水，仍 MUST 要求账户完整。
                if (isRule && type == RecordType.EXPENSE && fromAccountId == null) {
                    error = "请选择转出账户"; return@Button
                }
                if (isRule && type == RecordType.INCOME && toAccountId == null) {
                    error = "请选择转入账户"; return@Button
                }
                if (isRule && type == RecordType.TRANSFER &&
                    (fromAccountId == null || toAccountId == null || fromAccountId == toAccountId)
                ) {
                    error = "转账需选择两个不同的账户"; return@Button
                }
                val now = System.currentTimeMillis()
                val commonNote = note.trim()
                if (isRule) {
                    val d = dayText.toIntOrNull() ?: 1
                    val ld = LedgerClock.fromEpochMilli(now).localDate
                    onSubmitRule(
                        RecurringRuleEntity(
                            name = name.trim(), type = type, amountCents = amount,
                            categoryId = if (type == RecordType.TRANSFER) null else categoryId,
                            rootCategoryId = if (type == RecordType.TRANSFER) null else rootId,
                            fromAccountId = if (type == RecordType.INCOME) null else fromAccountId,
                            toAccountId = if (type == RecordType.EXPENSE) null else toAccountId,
                            note = commonNote, excludeFromStats = excluded,
                            cadence = cadence,
                            dayOfMonth = if (cadence == Cadence.MONTHLY) d.coerceIn(1, 31) else 1,
                            dayOfWeek = if (cadence == Cadence.WEEKLY) d.coerceIn(1, 7) else 1,
                            anchorLocalDate = ld, createdAt = now,
                            counterparty = counterpartyText.trim()
                        )
                    )
                } else {
                    onSubmitTemplate(
                        RecordTemplateEntity(
                            id = editing?.id ?: 0L,
                            name = name.trim(), type = type, amountCents = amount,
                            categoryId = if (type == RecordType.TRANSFER) null else categoryId,
                            rootCategoryId = if (type == RecordType.TRANSFER) null else rootId,
                            fromAccountId = if (type == RecordType.INCOME) null else fromAccountId,
                            toAccountId = if (type == RecordType.EXPENSE) null else toAccountId,
                            note = commonNote, excludeFromStats = excluded,
                            sortOrder = editing?.sortOrder ?: 0,
                            createdAt = editing?.createdAt ?: now,
                            counterparty = counterpartyText.trim()
                        )
                    )
                }
                error = null
                name = ""; amountText = ""; note = ""; counterpartyText = ""
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text(if (isRule) "保存周期规则" else (if (editing == null) "保存模板" else "保存修改")) }
        if (editing != null) {
            TextButton(onClick = onCancelEdit) { Text("取消编辑") }
        }
    }
}

/** 第 12 批：管理类表单统一 placeholder + bodyMedium + heightIn(min)，不裁占位文字。 */
@Composable
private fun ManagerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
    )
}

/** 模板列表行用：把分类 id 还原成「一级 / 二级」可读路径。 */
private fun categoryPath(id: Long?, categories: List<CategoryEntity>): String {
    val c = categories.firstOrNull { it.id == id } ?: return ""
    return if (c.level == 1) {
        val parent = categories.firstOrNull { it.id == c.parentId }?.name
        (if (parent.isNullOrBlank()) "" else parent + " / ") + c.name
    } else c.name
}

private fun typeLabel(t: RecordType): String = when (t) {
    RecordType.EXPENSE -> "支出"
    RecordType.INCOME -> "收入"
    RecordType.TRANSFER -> "转账"
    RecordType.SETTLEMENT -> "报销 / 退款"
}

private fun accountName(names: List<Pair<Long, String>>, id: Long?): String =
    names.firstOrNull { it.first == id }?.second ?: "未选"

/** 第 12 批：下拉限高 + 搜索，20+ 分类 / 10+ 账户不再无限下拉。 */
@Composable
private fun DropdownSelector(
    label: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val matched = options.filter { it.second.contains(query, ignoreCase = true) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索") },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 40.dp)
            )
            Column(
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())
            ) {
                matched.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text, maxLines = 1) },
                        onClick = { onSelect(id); expanded = false; query = "" }
                    )
                }
            }
        }
    }
}
