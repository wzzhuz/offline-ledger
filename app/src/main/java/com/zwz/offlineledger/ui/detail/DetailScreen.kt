package com.zwz.offlineledger.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.local.dao.RecordRow
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.components.amountColor
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.components.signedAmount
import com.zwz.offlineledger.ui.theme.LedgerColors
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    refreshTick: Int = 0,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val vm: DetailViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            DetailViewModel(
                ep.recordDao(), ep.accountDao(), ep.categoryDao(), ep.ledgerRepository()
            )
        }
    )
    val pagingItems = vm.pagingData.collectAsLazyPagingItems()
    val overview by vm.overview.collectAsState()
    val filter by vm.filter.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()

    var showFilter by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RecordRow?>(null) }
    var settleRow by remember { mutableStateOf<RecordRow?>(null) }
    var settleAmount by remember { mutableStateOf("") }
    var settleError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshTick) {
        if (refreshTick > 0) vm.bumpRevision()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthOverviewCard(
            overview = overview,
            activeFilterCount = filter.activeCount(),
            onPrev = { vm.switchMonth(-1) },
            onNext = { vm.switchMonth(1) },
            onFilter = { showFilter = true }
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.id }
            ) { index ->
                val row = pagingItems[index] ?: return@items
                val previous = if (index > 0) pagingItems[index - 1] else null
                if (previous == null || previous.localDate != row.localDate) {
                    DayHeader(row.localDate, overview.dailyTotals[row.localDate])
                }
                RecordRowCard(
                    row = row,
                    onClick = { onEdit(row.id) },
                    onDelete = { pendingDelete = row },
                    onSettle = {
                        scope.launch {
                            settleAmount = Cents(vm.remainingSettlement(row.id)).toYuanString()
                            settleError = null
                            settleRow = row
                        }
                    }
                )
            }
        }
    }

    settleRow?.let { target ->
        AlertDialog(
            onDismissRequest = { settleRow = null },
            title = { Text("报销 / 退款") },
            text = {
                Column {
                    Text(
                        text = "原支出 " + formatCents(target.amountCents) +
                            " 元；输入框已填剩余可报金额。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = settleAmount,
                        onValueChange = { settleAmount = it },
                        label = { Text("金额（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    settleError?.let {
                        Text(
                            text = it,
                            color = LedgerColors.ExpenseRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cents = try {
                        Cents.fromYuan(settleAmount).value
                    } catch (e: IllegalArgumentException) {
                        settleError = e.message ?: "金额格式非法"
                        return@TextButton
                    }
                    if (cents <= 0L) {
                        settleError = "金额必须大于 0"
                        return@TextButton
                    }
                    scope.launch {
                        val ok = vm.settle(target.id, cents)
                        if (ok) {
                            settleRow = null
                        } else {
                            settleError = "报销失败：累计已报可能超过原支出金额"
                        }
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { settleRow = null }) { Text("取消") }
            }
        )
    }

    if (showFilter) {
        FilterSheet(
            initial = filter,
            accounts = accounts,
            categories = categories,
            onApply = { vm.applyFilter(it) },
            onClear = { vm.clearFilter() },
            onDismiss = { showFilter = false }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔流水？") },
            text = {
                val label = target.categoryName ?: "转账"
                Text(
                    "分类：$label，金额 " + formatCents(target.amountCents) +
                        " 元。删除后会移入回收站，可在设置页恢复，账户余额将同步回滚。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        vm.delete(target.id)
                        pendingDelete = null
                    }
                }) { Text("删除", color = LedgerColors.ExpenseRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MonthOverviewCard(
    overview: MonthOverview,
    activeFilterCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFilter: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Text(
                    text = MonthRange.label(overview.yearMonth),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下个月")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("支出 " + formatCents(overview.expenseCents), color = LedgerColors.ExpenseRed)
                Text("收入 " + formatCents(overview.incomeCents), color = LedgerColors.IncomeGreen)
            }
            // 6c-2：口径与统计页一致，展示当前筛选币种。
            Text(
                text = overview.currencyCode?.let { "当前币种：$it · 不跨币种汇总" }
                    ?: "全部币种（可在筛选中按币种查看）",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "结余 " + formatCents(overview.balanceCents),
                    color = if (overview.balanceCents < 0) LedgerColors.ExpenseRed
                    else LedgerColors.IncomeGreen
                )
                TextButton(onClick = onFilter) {
                    Text(if (activeFilterCount > 0) "筛选 ($activeFilterCount)" else "筛选")
                }
            }
        }
    }
}

@Composable
private fun DayHeader(localDate: Int, totals: Pair<Long, Long>?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = MonthRange.dateLabel(localDate),
            style = MaterialTheme.typography.labelLarge
        )
        val e = totals?.first ?: 0L
        val i = totals?.second ?: 0L
        Text(
            text = "支出 " + formatCents(e) + " · 收入 " + formatCents(i),
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
    }
}

@Composable
private fun RecordRowCard(
    row: RecordRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onSettle: () -> Unit
) {
    val type = try {
        RecordType.valueOf(row.type)
    } catch (e: IllegalArgumentException) {
        RecordType.TRANSFER
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(row.categoryName, row.categoryColorHex)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(text = row.categoryName ?: "转账", style = MaterialTheme.typography.bodyLarge)
                val account = row.fromAccountName ?: row.toAccountName ?: "-"
                Text(
                    text = listOfNotNull(
                        account,
                        row.payee.takeIf { it.isNotBlank() },
                        row.note.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )
            }
            Text(
                text = signedAmount(type, row.amountCents),
                color = amountColor(type),
                style = MaterialTheme.typography.titleMedium
            )
            if (type == RecordType.EXPENSE) {
                TextButton(onClick = onSettle) {
                    Text("报销", style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}
