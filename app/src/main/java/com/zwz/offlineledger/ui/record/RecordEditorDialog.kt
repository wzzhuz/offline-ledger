package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.launch

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
                ep.recordDao(), ep.accountDao(), ep.categoryDao(), ep.ledgerRepository()
            )
        }
    )

    LaunchedEffect(recordId) { vm.load(recordId) }

    val state by vm.state.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    // 只在新建时显示：编辑已有流水不该被模板 chips 干扰。
                    if (recordId == null && templateQuickPick) {
                        TemplateQuickPickBar(onPicked = onSaved)
                    }
                    TypeTabs(state.type) { vm.setType(it) }

                    Text(
                        text = if (state.amountExpr.isBlank()) "0.00"
                        else state.amountExpr,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    if (state.type != RecordType.TRANSFER) {
                        CategoryGrid(
                            categories = categories,
                            type = state.type,
                            selectedId = state.categoryId,
                            expandedRootId = state.expandedRootId,
                            onSelect = { id, root -> vm.selectCategory(id, root) },
                            onKeepRoot = { vm.setExpandedRoot(null) }
                        )
                    }

                    AccountSection(
                        type = state.type,
                        accounts = accounts,
                        fromId = state.fromAccountId,
                        toId = state.toAccountId,
                        onFrom = { vm.setFromAccount(it) },
                        onTo = { vm.setToAccount(it) }
                    )

                    DateRow(
                        localDate = LedgerClock.fromEpochMilli(state.timestamp).localDate,
                        onPrev = { vm.shiftDay(-1) },
                        onNext = { vm.shiftDay(1) },
                        onToday = { vm.setToday() }
                    )

                    OutlinedTextField(
                        value = state.note,
                        onValueChange = { vm.setNote(it) },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "不计入收支", modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.excludeFromStats,
                            onCheckedChange = { vm.setExclude(it) }
                        )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun TypeTabs(current: RecordType, onSelect: (RecordType) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "支出" to RecordType.EXPENSE,
            "收入" to RecordType.INCOME,
            "转账" to RecordType.TRANSFER
        ).forEach { (label, type) ->
            Button(
                onClick = { onSelect(type) },
                modifier = Modifier.weight(1f),
                enabled = current != type
            ) { Text(label) }
        }
    }
}

/** 一级彩色圆网格；点一级即可直接记，展开的二级 chips 只是可选细分。 */
@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    type: RecordType,
    selectedId: Long?,
    expandedRootId: Long?,
    onSelect: (Long, Long) -> Unit,
    onKeepRoot: () -> Unit
) {
    val roots = categories.filter {
        it.level == 0 && it.type == type && !it.isArchived
    }
    roots.chunked(4).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            row.forEach { root ->
                val chosen = selectedId == root.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .clickable { onSelect(root.id, root.id) }
                ) {
                    CategoryIcon(root.name, root.colorHex, size = if (chosen) 46 else 40)
                    Text(
                        text = root.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chosen) LedgerColors.IncomeGreen else LedgerColors.Neutral
                    )
                }
            }
            repeat(4 - row.size) { Text(text = "", modifier = Modifier.weight(1f)) }
        }
    }

    val expanded = categories.firstOrNull { it.id == expandedRootId }
    if (expanded != null) {
        val children = categories.filter { it.level == 1 && it.parentId == expanded.id }
        Text(
            text = "「${expanded.name}」下细分（可不选，直接保存按一级记）",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            children.chunked(3).forEach { group ->
                Column(modifier = Modifier.weight(1f)) {
                    group.forEach { child ->
                        TextButton(onClick = { onSelect(child.id, expanded.id) }) {
                            Text(
                                text = child.name,
                                color = if (selectedId == child.id) LedgerColors.IncomeGreen
                                else LedgerColors.Neutral
                            )
                        }
                    }
                }
            }
        }
        TextButton(onClick = onKeepRoot) { Text("就按「${expanded.name}」记") }
    }
}

@Composable
private fun AccountSection(
    type: RecordType,
    accounts: List<AccountEntity>,
    fromId: Long?,
    toId: Long?,
    onFrom: (Long) -> Unit,
    onTo: (Long) -> Unit
) {
    val visible = accounts.filter { !it.isArchived }
    if (type == RecordType.TRANSFER) {
        Text(text = "转出账户", style = MaterialTheme.typography.labelLarge)
        AccountRow(visible, fromId, onFrom)
        Text(text = "转入账户", style = MaterialTheme.typography.labelLarge)
        AccountRow(visible, toId, onTo)
    } else {
        Text(
            text = if (type == RecordType.EXPENSE) "支出账户" else "收入账户",
            style = MaterialTheme.typography.labelLarge
        )
        if (type == RecordType.EXPENSE) AccountRow(visible, fromId, onFrom)
        else AccountRow(visible, toId, onTo)
    }
}

@Composable
private fun AccountRow(
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        accounts.chunked(3).forEach { group ->
            Column(modifier = Modifier.weight(1f)) {
                group.forEach { a ->
                    TextButton(onClick = { onSelect(a.id) }) {
                        Text(
                            text = if (a.id == selectedId) "● ${a.name}" else a.name,
                            color = if (a.id == selectedId) LedgerColors.IncomeGreen
                            else LedgerColors.Neutral
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRow(
    localDate: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "前一天")
        }
        Text(text = MonthRange.dateLabel(localDate), modifier = Modifier.weight(1f))
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "后一天")
        }
        TextButton(onClick = onToday) { Text("今天") }
    }
}

@Composable
private fun CalculatorKeypad(onKey: (String) -> Unit) {
    val keys = listOf(
        "7", "8", "9", "÷",
        "4", "5", "6", "×",
        "1", "2", "3", "-",
        "C", "0", ".", "+",
        "⌫", "(", ")", "="
    )
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        keys.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { key ->
                    TextButton(
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f)
                    ) { Text(text = key, style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}
