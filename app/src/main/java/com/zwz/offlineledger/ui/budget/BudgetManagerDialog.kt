package com.zwz.offlineledger.ui.budget

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.data.local.entity.BudgetEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.BudgetScope
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.components.ListDivider
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.settings.SettingsPageTopBar
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 5 批：预算管理改为独立全屏页。
 * 第 12 批：全局扫描发现与分类 / 模板同病（列表 + 表单同 Column(verticalScroll)），
 * 一并改为「TopBar 固定 + 入口 + LazyColumn 纯列表 + 编辑独立视图」。
 */
@Composable
fun BudgetManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: BudgetManagerViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            BudgetManagerViewModel(ep.ledgerRepository(), ep.budgetDao(), ep.categoryDao())
        }
    )
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsPageTopBar(
            title = if (editing) "新增预算" else "预算管理",
            onBack = { if (editing) editing = false else onBack() },
            onAdd = if (!editing) { { editing = true } } else null
        )
        if (editing) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                BudgetForm(
                    categories = state.categories,
                    onSubmit = { vm.save(it); editing = false }
                )
            }
        } else {
            BudgetList(state, vm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BudgetList(
    state: BudgetManagerState,
    vm: BudgetManagerViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
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
        item {
            Text(
                text = "规则对每个自然月生效；统计页按当月净支出显示进度。",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        if (state.budgets.isEmpty()) {
            item { Text("还没有预算。点右上角 + 新增一条，例如「餐饮 1000 元」。") }
        }
        items(state.budgets, key = { it.id }) { b ->
            var menu by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
            ) {
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
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("归档") },
                            onClick = { menu = false; vm.archive(b.id) }
                        )
                    }
                }
            }
            ListDivider(Modifier.padding(horizontal = 4.dp))
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
    categories: List<CategoryEntity>,
    onSubmit: (BudgetEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(BudgetScope.TOTAL) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    FormField(
        value = name, onValueChange = { name = it },
        placeholder = "名称（如 餐饮 / 月度总预算）"
    )
    FormField(
        value = limitText, onValueChange = { limitText = it },
        placeholder = "每月限额（元）",
        keyboardType = KeyboardType.Decimal
    )
    Text("范围", style = MaterialTheme.typography.labelMedium)
    Row {
        BudgetScope.entries.forEach { s ->
            FilterChip(
                selected = scope == s,
                onClick = { scope = s; categoryId = null },
                label = { Text(scopeLabel(s)) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
    if (scope != BudgetScope.TOTAL) {
        var expanded by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        val options = categories.filter {
            if (scope == BudgetScope.ROOT) it.level == 0 else it.level == 1
        }
        val matched = options.filter { it.name.contains(query, ignoreCase = true) }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text("分类：" + (categories.firstOrNull { it.id == categoryId }?.name ?: "未选"))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("搜索分类") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        .heightIn(min = 40.dp)
                )
                Column(modifier = Modifier.heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())) {
                    matched.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name, maxLines = 1) },
                            onClick = { categoryId = c.id; expanded = false; query = "" }
                        )
                    }
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

/** 第 12 批：管理类表单统一 placeholder + bodyMedium + heightIn(min)。 */
@Composable
private fun FormField(
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
