package com.zwz.offlineledger.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.displayName
import com.zwz.offlineledger.engine.Reconciliation
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.components.ListDivider
import com.zwz.offlineledger.ui.components.rememberKeyedReorder
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors
import com.zwz.offlineledger.ui.reconcile.ReconcileDialog
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun AssetsScreen(refreshTick: Int = 0) {
    val context = LocalContext.current
    val vm: AssetsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            AssetsViewModel(ep.accountDao(), ep.ledgerRepository(), LedgerPrefs(context))
        }
    )
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var reconcileId by remember { mutableStateOf<Long?>(null) }
    var editAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<AccountEntity?>(null) }

    // 第 20 批：资产页 Column+verticalScroll 改 LazyColumn，接入 reorderable 3.1.0。
    // 删除自研「拖拽期间禁用父滚动 / 边缘 scrollBy / 实测行高」整套代码：
    // 库接管长按窗口、pointer pass 顺序、边缘按距离变速滚动与 animateItem 让位补间。
    val listState = rememberLazyListState()
    val visible = state.accounts.filter { state.showArchived || !it.isArchived }
    val activeIds = visible.filter { !it.isArchived }.map { it.id }
    val accountOrder = rememberKeyedReorder(
        ids = activeIds,
        prefix = "acc",
        onCommit = { ids -> vm.commitAccountOrderAll(ids) }
    )
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        accountOrder.move(from.key, to.key)
    }

    LaunchedEffect(refreshTick) { vm.reload() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item(key = "assets-header") {
            Column {
                Text(text = "资产", style = MaterialTheme.typography.headlineMedium)
                CurrencyGroupsCard(state)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "账户（${state.accounts.count { !it.isArchived }}）",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = state.showArchived,
                            onClick = { vm.toggleShowArchived() },
                            label = { Text("含已归档") }
                        )
                        TextButton(onClick = { addError = null; showAdd = true }) { Text("+ 新增") }
                    }
                }
            }
        }
        AccountType.entries.forEach { type ->
            item(key = "section-" + type.name) {
                Column {
                    SectionTitle(type.displayName())
                    if (visible.none { it.type == type }) {
                        EmptyHint("还没有" + type.displayName() + "账户")
                    }
                }
            }
            val group = visible.filter { it.type == type }
            items(
                accountOrder.ids.mapNotNull { id ->
                    group.firstOrNull { it.id == id && !it.isArchived }
                },
                key = { accountOrder.keyOf(it.id) }
            ) { account ->
                ReorderableItem(reorderState, key = accountOrder.keyOf(account.id)) { _ ->
                    val dragHandle = with(this) {
                        Modifier.longPressDraggableHandle(
                            onDragStopped = { accountOrder.commit() }
                        )
                    }
                    AccountCard(
                        account = account,
                        dragModifier = dragHandle,
                        onDetail = { detailId = account.id },
                        onReconcile = { reconcileId = account.id },
                        onEdit = { editError = null; editAccount = account },
                        onDelete = { deleting = account },
                        onMove = { d -> scope.launch { vm.moveAccount(account.id, d) } },
                        onArchived = { a -> scope.launch { vm.setArchived(account.id, a) } }
                    )
                }
            }
            // 已归档账户不参与拖拽，仍按列表展示。
            group.filter { it.isArchived }.forEach { account ->
                item(key = "archived-account-" + account.id) {
                    AccountCard(
                        account = account,
                        onDetail = { detailId = account.id },
                        onReconcile = { reconcileId = account.id },
                        onEdit = { editError = null; editAccount = account },
                        onDelete = { deleting = account },
                        onMove = { d -> scope.launch { vm.moveAccount(account.id, d) } },
                        onArchived = { a -> scope.launch { vm.setArchived(account.id, a) } }
                    )
                }
            }
        }
        item(key = "assets-footer") {
            Text(
                text = "总负债仅作展示；各币种小计 = SUM(计入统计账户余额)。",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }

    if (showAdd) {
        AddAccountDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, type, initial, limit ->
                scope.launch {
                    val err = vm.addAccount(name, type, initial, limit)
                    if (err == null) { showAdd = false; addError = null } else addError = err
                }
            },
            errorText = addError
        )
    }

    editAccount?.let { account ->
        EditAccountDialog(
            account = account,
            onDismiss = { editAccount = null },
            onConfirm = { name, type, initial, limit, cc, include ->
                scope.launch {
                    val err = vm.editAccount(account, name, type, initial, limit, cc, include)
                    if (err == null) { editAccount = null; editError = null } else editError = err
                }
            },
            errorText = editError
        )
    }

    deleting?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除账户") },
            text = { Text("确定删除「" + account.name + "」？有流水的账户不能删除，只能归档。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.deleteAccount(account.id) }
                    deleting = null
                }) { Text("删除", color = LedgerColors.ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }

    detailId?.let { id ->
        AccountDetailDialog(
            accountId = id,
            accountName = state.accounts.firstOrNull { it.id == id }?.name ?: "账户详情",
            onDismiss = { detailId = null },
            onChanged = { vm.reload() }
        )
    }

    reconcileId?.let { id ->
        ReconcileDialog(
            accountId = id,
            onDismiss = { reconcileId = null },
            onDone = { vm.reload() }
        )
    }
}

@Composable
private fun CurrencyGroupsCard(state: AssetsUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = "按币种的资产小计", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "不同币种不汇总，避免虚假精确的总数",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            if (state.groups.isEmpty()) {
                Text(
                    text = "还没有账户",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerColors.Neutral,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            state.groups.forEach { g ->
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = g.currencyCode + " 小计", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = formatCents(g.netCents) + " " + g.currencyCode,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (g.netCents < 0) LedgerColors.ExpenseRed
                            else LedgerColors.IncomeGreen
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "资产 " + formatCents(g.assetsCents),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "负债 " + formatCents(g.liabilitiesCents),
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.ExpenseRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LedgerColors.Neutral,
        modifier = Modifier.padding(top = 6.dp, bottom = 1.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LedgerColors.Neutral,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/**
 * 第 16 批：去掉 Card 底色与阴影，改明细页同款纯行（16dp 内边距 + 56dp 行高）。
 * 副信息仍是非点击的 labelSmall；下面 5 个操作是可点击的，故字号与触摸区
 * MUST 提升到 bodyMedium / ≥40dp（指南 §2.1 第三次应用）。
 */
@Composable
private fun AccountCard(
    account: AccountEntity,
    onDetail: () -> Unit,
    onReconcile: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onArchived: (Boolean) -> Unit,
    dragModifier: Modifier? = null
) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.name + " · " + account.currencyCode +
                        if (account.isArchived) "（已归档）" else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatCents(account.balanceCents),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (account.balanceCents < 0) LedgerColors.ExpenseRed
                    else LedgerColors.IncomeGreen
                )
            }
            val credit = Reconciliation.availableCredit(
                account.creditLimitCents, account.balanceCents
            )
            val extra = listOfNotNull(
                if (account.type != AccountType.ASSET) account.type.displayName() else null,
                credit?.let { "可用 " + formatCents(it) },
                if (!account.includeInTotal) "不计入净资产" else null
            ).joinToString(" · ")
            if (extra.isNotBlank()) {
                Text(
                    text = extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniAction("详情", onDetail)
                MiniAction("对账", onReconcile)
                MiniAction("编辑", onEdit)
                MiniAction(if (account.isArchived) "恢复" else "归档",
                    { onArchived(!account.isArchived) })
                MiniAction("删除", onDelete, LedgerColors.ExpenseRed)
                // 第 20 批：库 draggableHandle；归档账户不传，手柄不显示。
                if (dragModifier != null) {
                    Box(
                        modifier = dragModifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("≡", style = MaterialTheme.typography.titleMedium,
                            color = LedgerColors.Neutral)
                    }
                }
                // 上移 / 下移降为无障碍兜底（TalkBack 无法执行拖动手势）。
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("上移") },
                        onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("下移") },
                        onClick = { menu = false; onMove(1) })
                }
            }
        }
        ListDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun MiniAction(
    text: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = LedgerColors.Neutral
) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 40.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, AccountType, String, String) -> Unit,
    errorText: String?
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.ASSET) }
    var initial by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增账户") },
        text = {
            Column {
                OutlinedTextField(
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("账户名称", style = MaterialTheme.typography.bodyMedium) }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                )
                Column {
                    AccountType.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { t ->
                                FilterChip(
                                    selected = type == t,
                                    onClick = { type = t },
                                    label = { Text(t.displayName()) }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = initial, onValueChange = { initial = it },
                    placeholder = { Text("期初余额（元，默认 0）", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                )
                if (type == AccountType.LIABILITY) {
                    OutlinedTextField(
                        textStyle = MaterialTheme.typography.bodyMedium,
                    value = limit, onValueChange = { limit = it },
                        placeholder = { Text("信用额度（元，可空）", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    )
                }
                errorText?.let { Text(text = it, color = LedgerColors.ExpenseRed) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, type, initial, limit) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 第三批：账户类型可改，解决「类型选错只能重建」。 */
@Composable
private fun EditAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, AccountType, String, String, String, Boolean) -> Unit,
    errorText: String?
) {
    var name by remember { mutableStateOf(account.name) }
    var type by remember { mutableStateOf(account.type) }
    var initial by remember { mutableStateOf(Cents(account.initialBalanceCents).toYuanString()) }
    var limit by remember { mutableStateOf(
        account.creditLimitCents?.let { Cents(it).toYuanString() } ?: ""
    ) }
    var currency by remember { mutableStateOf(account.currencyCode) }
    var include by remember { mutableStateOf(account.includeInTotal) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账户") },
        text = {
            Column {
                OutlinedTextField(
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("账户名称", style = MaterialTheme.typography.bodyMedium) }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), singleLine = true
                )
                Column {
                    AccountType.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { t ->
                                FilterChip(
                                    selected = type == t,
                                    onClick = { type = t },
                                    label = { Text(t.displayName()) }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    textStyle = MaterialTheme.typography.bodyMedium,
                    value = initial, onValueChange = { initial = it },
                    placeholder = { Text("期初余额（元）", style = MaterialTheme.typography.bodyMedium) }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), singleLine = true
                )
                if (type == AccountType.LIABILITY) {
                    OutlinedTextField(
                        textStyle = MaterialTheme.typography.bodyMedium,
                    value = limit, onValueChange = { limit = it },
                        placeholder = { Text("信用额度（元，可空）", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp), singleLine = true
                    )
                }
                Box {
                    TextButton(onClick = { expanded = true }) { Text("币种：$currency") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        LedgerCurrency.SUPPORTED.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(code) },
                                onClick = { currency = code; expanded = false }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("计入净资产", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = include, onCheckedChange = { include = it }
                    )
                }
                Text(
                    text = "改期初余额或类型会按流水重新计算余额；改币种只改标签。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
                errorText?.let { Text(it, color = LedgerColors.ExpenseRed) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, type, initial, limit, currency, include) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
