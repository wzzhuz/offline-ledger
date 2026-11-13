package com.zwz.offlineledger.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.engine.Reconciliation
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors
import com.zwz.offlineledger.ui.reconcile.ReconcileDialog
import kotlinx.coroutines.launch

@Composable
fun AssetsScreen() {
    val context = LocalContext.current
    val vm: AssetsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            AssetsViewModel(ep.accountDao(), ep.ledgerRepository())
        }
    )
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var reconcileId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { vm.reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "资产", style = MaterialTheme.typography.headlineMedium)

        CurrencyGroupsCard(state)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "账户（${state.accounts.count { !it.isArchived }}）",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { addError = null; showAdd = true }) { Text("+ 新增账户") }
        }

        SectionTitle("资产账户")
        val assets = state.accounts.filter {
            !it.isArchived && it.type == AccountType.ASSET
        }
        if (assets.isEmpty()) EmptyHint("还没有资产账户")
        assets.forEach { account ->
            AccountCard(
                account = account,
                onDetail = { detailId = account.id },
                onReconcile = { reconcileId = account.id }
            )
        }

        SectionTitle("信用负债账户")
        val liabilities = state.accounts.filter {
            !it.isArchived && it.type == AccountType.LIABILITY
        }
        if (liabilities.isEmpty()) EmptyHint("还没有信用负债账户")
        liabilities.forEach { account ->
            AccountCard(
                account = account,
                onDetail = { detailId = account.id },
                onReconcile = { reconcileId = account.id }
            )
        }

        Text(
            text = "总负债仅作展示，不参与净资产计算；净资产 = SUM(计入统计账户余额)。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(top = 16.dp)
        )
    }

    if (showAdd) {
        AddAccountDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, type, initial, limit ->
                scope.launch {
                    val err = vm.addAccount(name, type, initial, limit)
                    if (err == null) {
                        showAdd = false
                        addError = null
                    } else {
                        addError = err
                    }
                }
            },
            errorText = addError
        )
    }

    detailId?.let { id ->
        AccountDetailDialog(
            accountId = id,
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
/**
 * 阶段 6c-1：严格不混算。按币种分组列小计，
 * MUST NOT 显示把所有币种加在一起的单一「净资产」总数。
 */
private fun CurrencyGroupsCard(state: AssetsUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            state.groups.forEach { g ->
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = g.currencyCode + " 小计",
                            style = MaterialTheme.typography.titleMedium
                        )
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
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LedgerColors.Neutral,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AccountCard(
    account: AccountEntity,
    onDetail: () -> Unit,
    onReconcile: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.name + " · " + account.currencyCode,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatCents(account.balanceCents) + " 元",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (account.balanceCents < 0) LedgerColors.ExpenseRed
                    else LedgerColors.IncomeGreen
                )
            }
            val credit = Reconciliation.availableCredit(
                account.creditLimitCents, account.balanceCents
            )
            if (credit != null) {
                Text(
                    text = "可用额度 " + formatCents(credit) + " 元",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )
            }
            if (!account.includeInTotal) {
                Text(
                    text = "不计入净资产",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDetail) { Text("详情") }
                TextButton(onClick = onReconcile) { Text("对账") }
            }
        }
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
                    value = name, onValueChange = { name = it },
                    label = { Text("账户名称") }, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { type = AccountType.ASSET }) {
                        Text(
                            if (type == AccountType.ASSET) "● 资产账户" else "资产账户",
                            color = if (type == AccountType.ASSET) LedgerColors.IncomeGreen
                            else LedgerColors.Neutral
                        )
                    }
                    TextButton(onClick = { type = AccountType.LIABILITY }) {
                        Text(
                            if (type == AccountType.LIABILITY) "● 信用负债" else "信用负债",
                            color = if (type == AccountType.LIABILITY) LedgerColors.ExpenseRed
                            else LedgerColors.Neutral
                        )
                    }
                }
                OutlinedTextField(
                    value = initial, onValueChange = { initial = it },
                    label = { Text("期初余额（元，默认 0）") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == AccountType.LIABILITY) {
                    OutlinedTextField(
                        value = limit, onValueChange = { limit = it },
                        label = { Text("信用额度（元，可空）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                errorText?.let {
                    Text(text = it, color = LedgerColors.ExpenseRed)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, type, initial, limit) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
