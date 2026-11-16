package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/**
 * 阶段 7 紧凑版记一笔：备注与收款方首屏可见、分类横向单行、
 * 纯数字键盘、日历可选任意日期。「更多」折叠低频开关。
 */
@Composable
fun RecordEditorDialog(
    recordId: Long?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    templateQuickPick: Boolean = true
) {
    val context = LocalContext.current
    val vm: RecordEditorViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            RecordEditorViewModel(
                ep.recordDao(), ep.accountDao(), ep.categoryDao(),
                ep.ledgerRepository(), LedgerPrefs(context)
            )
        }
    )

    LaunchedEffect(recordId) { vm.load(recordId) }

    val state by vm.state.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val counterparties by vm.counterparties.collectAsState()
    val suggestions by vm.counterpartySuggestions.collectAsState()
    var errorText by remember { mutableStateOf<String?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val localDate = LedgerClock.fromEpochMilli(state.timestamp).localDate
    val zonedTime = java.time.Instant.ofEpochMilli(state.timestamp)
        .atZone(LedgerClock.zoneId)
    val timeLabel = "%02d:%02d".format(zonedTime.hour, zonedTime.minute)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                    Text(
                        text = if (recordId == null) "记一笔" else "编辑流水",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    if (recordId == null && templateQuickPick) {
                        TemplateQuickPickBar(onTemplate = { vm.applyTemplate(it) })
                    }
                    TypeTabs(state.type) { vm.setType(it) }

                    Text(
                        text = if (state.amountExpr.isBlank()) "0.00" else state.amountExpr,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    FormDivider()

                    if (state.type != RecordType.TRANSFER) {
                        FormRow("分类") {
                            CategoryStrip(
                                categories = categories,
                                type = state.type,
                                selectedId = state.categoryId,
                                expandedRootId = state.expandedRootId,
                                onSelect = { id, root -> vm.selectCategory(id, root) },
                                onKeepRoot = { vm.setExpandedRoot(null) }
                            )
                        }
                    }

                    FormRow("账户") {
                        AccountStrip(
                            type = state.type,
                            accounts = accounts,
                            fromId = state.fromAccountId,
                            toId = state.toAccountId,
                            onFrom = { vm.setFromAccount(it) },
                            onTo = { vm.setToAccount(it) }
                        )
                    }

                    FormRow("日期") {
                        DateRow(
                            localDate = localDate,
                            timeLabel = timeLabel,
                            onPick = { showDatePicker = true },
                            onPickTime = { showTimePicker = true },
                            onPrev = { vm.shiftDay(-1) },
                            onNext = { vm.shiftDay(1) },
                            onToday = { vm.setToday() }
                        )
                    }

                    // 阶段 8：转账不显示交易对方；收入显示「付款方 / 来源」。
                    if (state.type != RecordType.TRANSFER) {
                        val partyLabel = if (state.type == RecordType.INCOME)
                            "付款方 / 来源" else "收款方"
                        FormRow("备注") {
                            OutlinedTextField(
                                value = state.note,
                                onValueChange = { vm.setNote(it) },
                                label = { Text("可选") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        FormRow("对方") {
                            OutlinedTextField(
                                value = state.counterparty,
                                onValueChange = { vm.setCounterparty(it) },
                                label = { Text(partyLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            // 第 6 批：空输入列最近常用，输入后改联想；快捷区上限可数。
                            val quick: List<String> = if (state.counterparty.isBlank())
                                counterparties.take(QUICK_COUNT) else suggestions.take(QUICK_COUNT)
                            if (quick.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    quick.forEach { p ->
                                        TextButton(onClick = { vm.setCounterparty(p) }) {
                                            Text(p, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TextButton(onClick = { showMore = !showMore }) {
                        Text(if (showMore) "收起" else "更多")
                    }
                    if (showMore) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "不计入收支", modifier = Modifier.weight(1f))
                            Switch(
                                checked = state.excludeFromStats,
                                onCheckedChange = { vm.setExclude(it) }
                            )
                        }
                    }

                    errorText?.let {
                        Text(text = it, color = LedgerColors.ExpenseRed)
                    }
                }

                CalculatorKeypad { key -> vm.onKey(key) }

                Button(
                    onClick = {
                        scope.launch {
                            val err = vm.save()
                            if (err == null) onSaved() else errorText = err
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("保存") }
            }
        }
    }

    if (showDatePicker) {
        LedgerDatePicker(
            timestamp = state.timestamp,
            onDismiss = { showDatePicker = false },
            onConfirm = { vm.setDate(it); showDatePicker = false }
        )
    }
    if (showTimePicker) {
        showTimePicker = false
        android.app.TimePickerDialog(
            context,
            { _, hour, minute -> vm.setTime(hour, minute) },
            zonedTime.hour, zonedTime.minute, true
        ).show()
    }
}

/** 第 5 批：三个凸起 Button 换成平铺 TabRow，少一层视觉重量。 */
@Composable
private fun TypeTabs(current: RecordType, onSelect: (RecordType) -> Unit) {
    val items = listOf(
        "支出" to RecordType.EXPENSE,
        "收入" to RecordType.INCOME,
        "转账" to RecordType.TRANSFER
    )
    TabRow(selectedTabIndex = items.indexOfFirst { it.second == current }.coerceAtLeast(0)) {
        items.forEach { (label, type) ->
            Tab(
                selected = current == type,
                onClick = { onSelect(type) },
                text = { Text(label) }
            )
        }
    }
}

/** 一级横向单行；点一级即可直接记，二级 chips 只是可选细分。 */
@Composable
private fun CategoryStrip(
    categories: List<CategoryEntity>,
    type: RecordType,
    selectedId: Long?,
    expandedRootId: Long?,
    onSelect: (Long, Long) -> Unit,
    onKeepRoot: () -> Unit
) {
    val roots = categories.filter { it.level == 0 && it.type == type && !it.isArchived }
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        roots.forEach { root ->
            val chosen = selectedId == root.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable { onSelect(root.id, root.id) }
            ) {
                CategoryIcon(root.name, root.colorHex, size = if (chosen) 40 else 34)
                Text(
                    text = root.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chosen) LedgerColors.IncomeGreen else LedgerColors.Neutral
                )
            }
        }
    }

    val expanded = categories.firstOrNull { it.id == expandedRootId }
    if (expanded != null) {
        val children = categories.filter { it.level == 1 && it.parentId == expanded.id }
        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            children.forEach { child ->
                TextButton(onClick = { onSelect(child.id, expanded.id) }) {
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedId == child.id) LedgerColors.IncomeGreen
                        else LedgerColors.Neutral
                    )
                }
            }
            TextButton(onClick = onKeepRoot) {
                Text("就按「${expanded.name}」记", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AccountStrip(
    type: RecordType,
    accounts: List<AccountEntity>,
    fromId: Long?,
    toId: Long?,
    onFrom: (Long) -> Unit,
    onTo: (Long) -> Unit
) {
    val visible = accounts.filter { !it.isArchived }
    if (type == RecordType.TRANSFER) {
        AccountQuickRow("转出", visible, fromId, onFrom)
        AccountQuickRow("转入", visible, toId, onTo)
    } else {
        val chosenId = if (type == RecordType.EXPENSE) fromId else toId
        AccountQuickRow("", visible, chosenId,
            if (type == RecordType.EXPENSE) onFrom else onTo)
    }
}

/**
 * 第 6 批：常用账户（ViewModel 已按最近 30 天频次排序）平铺 QUICK_COUNT 个，
 * 其余进「更多」弹层按名称搜索。标签不再写「支出账户 · CNY」这类长文本，
 * 币种只在弹层里显示，避免占用表单宽度。
 */
@Composable
private fun AccountQuickRow(
    tag: String,
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var showAll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    if (tag.isNotEmpty()) {
        Text(text = tag, style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral)
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        accounts.take(QUICK_COUNT).forEach { a ->
            val chosen = a.id == selectedId
            AssistChip(
                onClick = { onSelect(a.id) },
                label = {
                    Text(
                        (if (chosen) "● " else "") + a.name,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        AssistChip(
            onClick = { showAll = true },
            label = { Text("更多 ▾", style = MaterialTheme.typography.labelSmall) }
        )
    }
    if (showAll) {
        AlertDialog(
            onDismissRequest = { showAll = false },
            title = { Text("全部账户") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索账户名（含卡号后缀）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        accounts.filter { it.name.contains(query, ignoreCase = true) }
                            .forEach { a ->
                                TextButton(
                                    onClick = { onSelect(a.id); showAll = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(a.name + " · " + a.currencyCode) }
                            }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAll = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun DateRow(
    localDate: Int,
    timeLabel: String,
    onPick: () -> Unit,
    onPickTime: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPick) { Text(MonthRange.dateLabel(localDate)) }
        TextButton(onClick = onPickTime) { Text(timeLabel) }
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "前一天")
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "后一天")
        }
        TextButton(onClick = onToday) { Text("今天") }
    }
}

/** 日历回传 UTC 午夜毫秒，ViewModel 再换算回本地时区，避免差一天。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerDatePicker(
    timestamp: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val localDate = Instant.ofEpochMilli(timestamp)
        .atZone(LedgerClock.zoneId).toLocalDate()
    val utcMillis = localDate.atStartOfDay(ZoneOffset.UTC)
        .toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = utcMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onConfirm) }) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) { DatePicker(state = pickerState) }
}

/** 阶段 7：纯数字键盘，无加减乘除与括号。空字符串占位只做 Spacer。 */
@Composable
private fun CalculatorKeypad(onKey: (String) -> Unit) {
    val keys = listOf(
        "7", "8", "9", "⌫",
        "4", "5", "6", "C",
        "1", "2", "3", ".",
        "", "0", "", ""
    )
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        keys.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        TextButton(
                            onClick = { onKey(key) },
                            modifier = Modifier.weight(1f)
                        ) { Text(text = key, style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }
}


/**
 * 第 6 批：统一表单网格。固定 52dp 标签列 + 一条细分割线，
 * 左侧边界完全一致；同区域只用 OutlinedTextField 一种输入容器，
 * 不再混用带框输入框 / 无边界 TextButton / AssistChip 直接铺在根 Column。
 */
private const val QUICK_COUNT = 4

@Composable
private fun FormRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LedgerColors.Neutral,
            modifier = Modifier.width(52.dp).padding(top = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) { content() }
    }
    FormDivider()
}

@Composable
private fun FormDivider() {
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}
