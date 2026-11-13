package com.zwz.offlineledger.ui.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.data.local.entity.RecurringRuleEntity
import com.zwz.offlineledger.data.model.Cadence
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors

/** 设置页入口。Dialog 内两个 Tab，不改 4 Tab 底部导航。 */
@Composable
fun TemplateManagerDialog(onDismiss: () -> Unit) {
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "模板与周期记账",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 16.dp)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("模板") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("周期规则") })
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    if (state.message.isNotBlank()) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.Neutral,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    if (tab == 0) {
                        if (state.templates.isEmpty()) {
                            Text("还没有模板。在下面新增一个，之后记一笔点一下即可完成。")
                        }
                        state.templates.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = t.name + " · " + Cents(t.amountCents).toYuanString() +
                                        " 元" + if (t.isArchived) "（已归档）" else "",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (!t.isArchived) {
                                    TextButton(onClick = { vm.archiveTemplate(t.id) }) {
                                        Text("归档")
                                    }
                                }
                            }
                        }
                    } else {
                        if (state.rules.isEmpty()) {
                            Text("还没有周期规则。房租、工资、话费、订阅可用它自动补记。")
                        }
                        state.rules.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = r.name + " · " + Cents(r.amountCents).toYuanString() +
                                        " 元 · " + cadenceLabel(r.cadence),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = r.isEnabled,
                                    onCheckedChange = { vm.setRuleEnabled(r.id, it) }
                                )
                            }
                        }
                    }

                    Text(
                        text = if (tab == 0) "新增模板" else "新增周期规则",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    QuickEntryForm(
                        isRule = tab == 1,
                        categories = state.categories,
                        accountNames = state.accounts.map { it.id to it.name },
                        onSubmitTemplate = { vm.saveTemplate(it) },
                        onSubmitRule = { vm.saveRule(it) }
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
 * 禁止 Float / Double 参与（金额铁律）。
 */
@Composable
private fun QuickEntryForm(
    isRule: Boolean,
    categories: List<CategoryEntity>,
    accountNames: List<Pair<Long, String>>,
    onSubmitTemplate: (RecordTemplateEntity) -> Unit,
    onSubmitRule: (RecurringRuleEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RecordType.EXPENSE) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var fromAccountId by remember { mutableStateOf<Long?>(null) }
    var toAccountId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var excluded by remember { mutableStateOf(false) }
    var cadence by remember { mutableStateOf(Cadence.MONTHLY) }
    var dayText by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val rootId = selectedCategory?.let { it.parentId ?: it.id }

    Column {
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("名称（如 午餐 / 房租）") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = amountText, onValueChange = { amountText = it },
            label = { Text("金额（元）") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        Text("类型", style = MaterialTheme.typography.labelMedium)
        Row {
            RecordType.entries.filter { it != RecordType.TRANSFER || !isRule }.forEach { rt ->
                AssistChip(
                    onClick = { type = rt },
                    label = { Text(typeLabel(rt)) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        if (type != RecordType.TRANSFER) {
            DropdownSelector(
                label = "分类：" + (selectedCategory?.name ?: "未选"),
                options = categories.map { it.id to (if (it.level == 1) "└ " else "") + it.name },
                onSelect = { categoryId = it }
            )
        }
        if (type != RecordType.INCOME) {
            DropdownSelector(
                label = "转出账户：" + accountName(accountNames, fromAccountId),
                options = accountNames,
                onSelect = { fromAccountId = it }
            )
        }
        if (type != RecordType.EXPENSE) {
            DropdownSelector(
                label = "转入账户：" + accountName(accountNames, toAccountId),
                options = accountNames,
                onSelect = { toAccountId = it }
            )
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text("备注（可选）") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("不计入收支统计", modifier = Modifier.weight(1f))
            Switch(checked = excluded, onCheckedChange = { excluded = it })
        }

        if (isRule) {
            Text("周期", style = MaterialTheme.typography.labelMedium)
            Row {
                Cadence.entries.forEach { c ->
                    AssistChip(
                        onClick = { cadence = c },
                        label = { Text(cadenceLabel(c)) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            OutlinedTextField(
                value = dayText, onValueChange = { dayText = it },
                label = { Text(if (cadence == Cadence.MONTHLY) "每月几号（1-31）" else "每周几（1=周一..7=周日）") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }

        error?.let {
            Text(it, color = LedgerColors.ExpenseRed, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val amount = try {
                    Cents.fromYuan(amountText).value
                } catch (e: IllegalArgumentException) {
                    error = e.message ?: "金额格式非法"; return@Button
                }
                if (name.isBlank()) { error = "请填名称"; return@Button }
                if (amount <= 0L) { error = "金额必须大于 0"; return@Button }
                if (type == RecordType.EXPENSE && fromAccountId == null) {
                    error = "请选择转出账户"; return@Button
                }
                if (type == RecordType.INCOME && toAccountId == null) {
                    error = "请选择转入账户"; return@Button
                }
                if (type == RecordType.TRANSFER &&
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
                            anchorLocalDate = ld, createdAt = now
                        )
                    )
                } else {
                    onSubmitTemplate(
                        RecordTemplateEntity(
                            name = name.trim(), type = type, amountCents = amount,
                            categoryId = if (type == RecordType.TRANSFER) null else categoryId,
                            rootCategoryId = if (type == RecordType.TRANSFER) null else rootId,
                            fromAccountId = if (type == RecordType.INCOME) null else fromAccountId,
                            toAccountId = if (type == RecordType.EXPENSE) null else toAccountId,
                            note = commonNote, excludeFromStats = excluded,
                            createdAt = now
                        )
                    )
                }
                error = null
                name = ""; amountText = ""; note = ""
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text(if (isRule) "保存周期规则" else "保存模板") }
    }
}

private fun typeLabel(t: RecordType): String = when (t) {
    RecordType.EXPENSE -> "支出"
    RecordType.INCOME -> "收入"
    RecordType.TRANSFER -> "转账"
}

private fun accountName(names: List<Pair<Long, String>>, id: Long?): String =
    names.firstOrNull { it.first == id }?.second ?: "未选"

@Composable
private fun DropdownSelector(
    label: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(id); expanded = false }
                )
            }
        }
    }
}
