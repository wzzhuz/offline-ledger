package com.zwz.offlineledger.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.ui.components.ListDivider
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.local.dao.RecordRow
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.components.amountColor
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.components.signedAmount
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    refreshTick: Int,
    vm: DetailViewModel,
    listState: LazyListState,
    onEdit: (Long) -> Unit
) {
    val pagingItems = vm.pagingData.collectAsLazyPagingItems()
    val overview by vm.overview.collectAsState()
    val filter by vm.filter.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    /** 第 11 批：筛选面板交易对方联想的全量候选。 */
    val counterparties by vm.counterparties.collectAsState()

    var showFilter by remember { mutableStateOf(false) }
    var actionRow by remember { mutableStateOf<RecordRow?>(null) }
    var pendingDelete by remember { mutableStateOf<RecordRow?>(null) }
    var settleRow by remember { mutableStateOf<RecordRow?>(null) }
    var settleAmount by remember { mutableStateOf("") }
    var settleError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /** 第 24 批：官方 PullToRefreshBox，手势只在列表已到顶部时接管；
     *  刻意不加 TopAppBar 无障碍刷新按钮（使用者明确要求不多出业务按钮）。
     *  isRefreshing 与首次加载标志分离，避免两个转圈。 */
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(refreshTick) {
        if (refreshTick > 0) vm.bumpRevision()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                vm.bumpRevision()
                isRefreshing = true
                scope.launch {
                    delay(420)
                    isRefreshing = false
                    snackbarHostState.showSnackbar(
                        "已刷新当前月份 · " + pagingItems.itemCount + " 条记录"
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                MonthOverviewCard(
                    overview = overview,
                    activeFilterCount = filter.activeCount(),
                    onPrev = { vm.switchMonth(-1) },
                    onNext = { vm.switchMonth(1) },
                    onFilter = { showFilter = true }
                )
                // 第 28 批：三条只读展示名按维度独立读取，MUST NOT 共用一个字段，
                // 否则按收款方跳转时「分类」行会串味显示收款方名。
                val drillLabel = when {
                    filter.categoryId != null || filter.rootCategoryId != null ->
                        "分类筛选：" + (filter.categoryDisplayName ?: run {
                            val cid = filter.categoryId ?: filter.rootCategoryId
                            categories.firstOrNull { it.id == cid }?.name ?: "分类"
                        })
                    filter.counterpartyQuery != null ->
                        "交易对方包含：" +
                            (filter.counterpartyDisplayName ?: filter.counterpartyQuery)
                    filter.accountId != null ->
                        "账户：" + (filter.accountDisplayName ?: (
                            accounts.firstOrNull { it.id == filter.accountId }?.name ?: "账户"
                        ))
                    else -> null
                }
                if (drillLabel != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = { vm.clearFilter() },
                            label = { Text(drillLabel + "  ✕") }
                        )
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { it.id }
                    ) { index ->
                        val row = pagingItems[index] ?: return@items
                        val previous = if (index > 0) pagingItems[index - 1] else null
                        if (previous == null || previous.localDate != row.localDate) {
                            DayHeader(row.localDate, overview.dailyTotals[row.localDate])
                        }
                        RecordRowCard(row = row, onClick = { actionRow = row })
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 第 4 批：行内不再塞报销 / 删除按钮，改为点击行弹出动作菜单，
    // 让四列网格保持整齐；编辑 / 报销 / 删除仍各走原有事务路径。
    actionRow?.let { target ->
        val t = try {
            RecordType.valueOf(target.type)
        } catch (e: IllegalArgumentException) {
            RecordType.TRANSFER
        }
        Dialog(onDismissRequest = { actionRow = null }) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = target.categoryName ?: "转账",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatCents(target.amountCents) + " 元",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LedgerColors.Neutral
                    )
                    if (target.importSource.isNotBlank()) {
                        Text(
                            text = "来源：" + target.importSource +
                                if (target.sourceId.isNotBlank())
                                    " · " + target.sourceId else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral
                        )
                    }
                    TextButton(
                        onClick = { actionRow = null; onEdit(target.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("编辑") }
                    if (t == RecordType.EXPENSE) {
                        TextButton(
                            onClick = {
                                actionRow = null
                                scope.launch {
                                    settleAmount = Cents(vm.remainingSettlement(target.id)).toYuanString()
                                    settleError = null
                                    settleRow = target
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("报销 / 退款") }
                    }
                    TextButton(
                        onClick = { actionRow = null; pendingDelete = target },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("删除", color = LedgerColors.ExpenseRed) }
                    TextButton(
                        onClick = { actionRow = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("取消") }
                }
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
            counterparties = counterparties,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LedgerColors.Neutral.copy(alpha = 0.12f))
    )
}

@Composable
private fun DayHeader(localDate: Int, totals: Pair<Long, Long>?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
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

/**
 * 第 5 批：按真机对账习惯重排——收款方/来源为主行，分类降为小字；
 * 左侧方向箭头区分支出 / 收入 / 转账；账户名不再被 64dp 截断。
 */
@Composable
private fun RecordRowCard(row: RecordRow, onClick: () -> Unit) {
    val type = try {
        RecordType.valueOf(row.type)
    } catch (e: IllegalArgumentException) {
        RecordType.TRANSFER
    }
    val timeLabel = remember(row.timestamp) {
        val z = java.time.Instant.ofEpochMilli(row.timestamp).atZone(LedgerClock.zoneId)
        "%02d:%02d".format(z.hour, z.minute)
    }
    val account = row.fromAccountName ?: row.toAccountName ?: "-"
    val category = row.categoryName ?: "转账"
    val party = row.counterparty.takeIf { it.isNotBlank() }
    val primary = when {
        type == RecordType.TRANSFER -> "转账"
        party != null -> party
        else -> category
    }
    val partyTag = when {
        type == RecordType.TRANSFER -> null
        party == null -> null
        type == RecordType.INCOME -> "来源"
        else -> "收款方"
    }
    val secondary = listOfNotNull(
        category.takeIf { primary != category },
        row.note.takeIf { it.isNotBlank() && it != primary }
    ).joinToString(" · ")
    val accent = when (type) {
        RecordType.EXPENSE -> LedgerColors.ExpenseRed
        RecordType.INCOME -> LedgerColors.IncomeGreen
        RecordType.TRANSFER -> MaterialTheme.colorScheme.primary
        RecordType.SETTLEMENT -> LedgerColors.IncomeGreen
    }
    val arrow = when (type) {
        RecordType.EXPENSE -> "←"
        RecordType.INCOME -> "→"
        RecordType.TRANSFER -> "⇄"
        RecordType.SETTLEMENT -> "↩"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = arrow,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = primary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (partyTag != null) {
                    Text(
                        text = " " + partyTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral
                    )
                }
            }
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = timeLabel + " · " + account,
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral,
                maxLines = 2
            )
        }
        Text(
            text = signedAmount(type, row.amountCents),
            color = amountColor(type),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(92.dp)
        )
    }
    ListDivider(Modifier.padding(horizontal = 16.dp))
}


