package com.zwz.offlineledger.ui.budget

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.data.local.entity.BudgetEntity
import com.zwz.offlineledger.data.model.BudgetScope
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors

/** 设置页入口。Dialog 内列表 + 新增 + 归档，不改 4 Tab 导航。 */
@Composable
fun BudgetManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vm: BudgetManagerViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            BudgetManagerViewModel(
                ep.ledgerRepository(), ep.budgetDao(), ep.categoryDao()
            )
        }
    )
    val state by vm.state.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "预算管理",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 16.dp)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
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
                            color = LedgerColors.Neutral
                        )
                    }
                    Text(
                        text = "规则对每个自然月生效；统计页按当月净支出显示进度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (state.budgets.isEmpty()) {
                        Text("还没有预算。在下面新增一条，例如「餐饮 1000 元」。")
                    }
                    state.budgets.forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = b.name + " · " + scopeLabel(b.scope) +
                                        if (b.isArchived) "（已归档）" else "",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "每月限额 " + formatCents(b.limitCents) + " 元",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LedgerColors.Neutral
                                )
                            }
                            if (!b.isArchived) {
                                TextButton(onClick = { vm.archive(b.id) }) { Text("归档") }
                            }
                        }
                    }
                    BudgetForm(categories = state.categories, onSubmit = { vm.save(it) })
                }
            }
        }
    }
}

private fun scopeLabel(s: BudgetScope): String = when (s) {
    BudgetScope.TOTAL -> "总预算"
    BudgetScope.ROOT -> "一级分类"
    BudgetScope.CATEGORY -> "二级分类"
}

@Composable
private fun BudgetForm(
    categories: List<com.zwz.offlineledger.data.local.entity.CategoryEntity>,
    onSubmit: (BudgetEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(BudgetScope.TOTAL) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Text(
        text = "新增预算",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
    OutlinedTextField(
        value = name, onValueChange = { name = it },
        label = { Text("名称（如 餐饮 / 月度总预算）") },
        modifier = Modifier.fillMaxWidth(), singleLine = true
    )
    OutlinedTextField(
        value = limitText, onValueChange = { limitText = it },
        label = { Text("每月限额（元）") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(), singleLine = true
    )
    Text("范围", style = MaterialTheme.typography.labelMedium)
    Row {
        BudgetScope.entries.forEach { s ->
            AssistChip(
                onClick = { scope = s },
                label = { Text(scopeLabel(s)) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
    if (scope != BudgetScope.TOTAL) {
        var expanded by remember { mutableStateOf(false) }
        val options = categories.filter {
            if (scope == BudgetScope.ROOT) it.level == 0 else it.level == 1
        }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text("分类：" + (options.firstOrNull { it.id == categoryId }?.name ?: "未选"))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.name) },
                        onClick = { categoryId = c.id; expanded = false }
                    )
                }
            }
        }
    }
    error?.let {
        Text(it, color = LedgerColors.ExpenseRed, style = MaterialTheme.typography.bodySmall)
    }
    Button(
        onClick = {
            val cents = try {
                Cents.fromYuan(limitText).value
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "金额格式非法"; return@Button
            }
            if (name.isBlank()) { error = "请填名称"; return@Button }
            if (cents <= 0L) { error = "限额必须大于 0"; return@Button }
            if (scope != BudgetScope.TOTAL && categoryId == null) {
                error = "分类预算必须选一个分类"; return@Button
            }
            onSubmit(
                BudgetEntity(
                    name = name.trim(),
                    scope = scope,
                    categoryId = if (scope == BudgetScope.TOTAL) null else categoryId,
                    limitCents = cents,
                    createdAt = System.currentTimeMillis()
                )
            )
            error = null; name = ""; limitText = ""; categoryId = null
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) { Text("保存预算") }
}
