package com.zwz.offlineledger.ui.detail

import com.zwz.offlineledger.data.display.CategoryDisplay
import com.zwz.offlineledger.ui.assets.accountBaseLabel
import com.zwz.offlineledger.ui.assets.accountLabel
import com.zwz.offlineledger.data.paging.DetailGranularity

import androidx.activity.compose.BackHandler
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.ui.components.ListDivider
import com.zwz.offlineledger.ui.components.YearMonthPickerDialog
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
    /** 第 41 批：月份条点开 YearMonthPicker，一次点到任意年月。 */
    var showMonthPicker by remember { mutableStateOf(false) }
    val monthCounts by vm.monthCounts.collectAsState()
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

    /**
     * 第 46 批：切月已由 vm.locateMonth 写回 _filter 触发 Pager 重查，
     * 锚点只在分页首屏 / 后续加载到达后滚到该月首条。
     * MUST NOT 再要求用户「继续下滑后再点一次」：目标月无记录时列表本身为空即是反馈，
     * 空月在 YearMonthPicker 里已灰显不可点。
     */
    val anchorMonth by vm.anchorMonth.collectAsState()
    LaunchedEffect(anchorMonth, pagingItems.itemCount, pagingItems.loadState.refresh) {
        anchorMonth?.let { ym ->
            if (pagingItems.loadState.refresh is LoadState.NotLoading) {
                val idx = (0 until pagingItems.itemCount).firstOrNull { i ->
                    pagingItems.peek(i)?.let { it.localDate / 100 == ym } == true
                }
                if (idx != null) {
                    listState.animateScrollToItem(idx)
                    vm.consumeAnchor()
                }
            }
        }
    }

    // 第 45 批：actionRow != null 时整页切到只读详情页，明细列表整体让位，
    // 不新增 NavHost 路由、不动 4 Tab 骨架。
    if (actionRow == null) {
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
                        "已刷新 · " + pagingItems.itemCount + " 条记录"
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                MonthOverviewCard(
                    overview = overview,
                    activeFilterCount = filter.activeCount(),
                    timeSummary = filter.timeSummary(pagingItems.itemCount),
                    showOverview = filter.showMonthOverview(),
                    // 第 48 批：表头读单一派生周期，不再读 _overview 那份独立月份。
                    periodTitle = filter.displayPeriodLabel(),
                    granularity = filter.granularity,
                    onGranularity = { vm.setGranularity(it) },
                    onPrev = { vm.switchMonth(-1) },
                    onNext = { vm.switchMonth(1) },
                    onPickMonth = { showMonthPicker = true },
                    onFilter = { showFilter = true }
                )
                // 第 28 批：三条只读展示名按维度独立读取，MUST NOT 共用一个字段，
                // 否则按收款方跳转时「分类」行会串味显示收款方名。
                val drillLabel = when {
                    filter.categoryId != null || filter.rootCategoryId != null ->
                        "分类筛选：" + (filter.categoryDisplayName ?: run {
                            val cid = filter.categoryId ?: filter.rootCategoryId
                            val c = cid?.let { id -> categories.firstOrNull { it.id == id } }
                            if (c == null) "分类" else {
                                val parent = c.parentId?.let { pid ->
                                    categories.firstOrNull { it.id == pid }?.name
                                }
                                CategoryDisplay.resolve(parent, c.name)
                            }
                        })
                    filter.counterpartyQuery != null ->
                        "交易对方包含：" +
                            (filter.counterpartyDisplayName ?: filter.counterpartyQuery)
                    filter.accountId != null ->
                        "账户：" + (filter.accountDisplayName ?: (
                            accounts.firstOrNull { it.id == filter.accountId }
                                ?.let { accountBaseLabel(it) } ?: "账户"
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
                        val previous = if (index > 0) pagingItems.peek(index - 1) else null
                        val ym = row.localDate / 100
                        // 第 42 批：跨月连续列表，月分组头只在 UI 层插入，
                        // 复用已有 monthCounts 角标，MUST NOT 另跑聚合查询。
                        if (previous == null || previous.localDate / 100 != ym) {
                            MonthGroupHeader(ym, monthCounts[ym])
                        }
                        if (previous == null || previous.localDate != row.localDate) {
                            // 第 47 批：跨范围态 overview 只按当前查看月聚合，
                            // 日头不显示错月 / 0 值合计，只显示日期；不新增聚合查询。
                            DayHeader(
                                row.localDate,
                                if (filter.showMonthOverview()) overview.dailyTotals[row.localDate]
                                else null
                            )
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
    }

    // 第 45 批：点击记录直接进详情页；删除 / 报销仍复用下方既有二次确认。
    // 第 46 批：只读 / 编辑统一返回出口，并消费全面屏左滑返回，
    // 否则内部二级页没挂 BackHandler，系统 back 会直接收掉 Activity 退出 App。
    val closeDetail = { actionRow = null }
    actionRow?.let { target ->
        val canSettle = try {
            RecordType.valueOf(target.type) == RecordType.EXPENSE
        } catch (e: IllegalArgumentException) {
            false
        }
        // 只消费事件，不接管返回动画、不拦 predictive back 预览；
        // 编辑表单是外部 Dialog，其自身 onDismiss 处理未保存改动，无需在此重复拦截。
        BackHandler(enabled = true, onBack = closeDetail)
        RecordDetailPage(
            row = target,
            onClose = closeDetail,
            onEdit = { id -> actionRow = null; onEdit(id) },
            onDelete = { actionRow = null; pendingDelete = target },
            onSettle = if (canSettle) {
                {
                    actionRow = null
                    scope.launch {
                        settleAmount = Cents(vm.remainingSettlement(target.id)).toYuanString()
                        settleError = null
                        settleRow = target
                    }
                }
            } else null
        )
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

    if (showMonthPicker) {
        YearMonthPickerDialog(
            initial = filter.displayYearMonth(),
            monthCounts = monthCounts,
            onYearShown = { vm.loadMonthCounts(it) },
            onDismiss = { showMonthPicker = false },
            onConfirm = { vm.selectMonth(it); showMonthPicker = false },
            disableEmptyMonths = true
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
    timeSummary: String,
    showOverview: Boolean,
    periodTitle: String,
    granularity: DetailGranularity,
    onGranularity: (DetailGranularity) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickMonth: () -> Unit,
    onFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 第 46 批：月 / 年粒度切换收进标题行，不再独立占一行；
        // 标题 weight(1f) + 单行省略，chip 紧凑排列，触摸区仍保留 Material 默认值。
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = periodTitle + " ▾",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onPickMonth)
            )
            listOf(
                DetailGranularity.MONTH to "月",
                DetailGranularity.YEAR to "年"
            ).forEach { (g, label) ->
                FilterChip(
                    selected = granularity == g,
                    onClick = { onGranularity(g) },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
        }
        // 第 47 批：顶部月概览只在「无筛选 · 月粒度 · 单月窄视图」显示。
        // 统计下钻 / 显式范围 / 全部时间 / 年粒度 / 任意筛选条件下，
        // overview 与列表不同口径，MUST NOT 显示错月数字。
        if (showOverview) {
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
            Text(
                text = timeSummary,
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        } else {
            // 隐藏态：只留一行范围说明（复用 timeSummary 单一出口）+ 筛选入口，
            // 不新增一整块、不留空白占位、不把统计页数字并列搬来。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onFilter) {
                    Text(if (activeFilterCount > 0) "筛选 ($activeFilterCount)" else "筛选")
                }
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

/** 第 42 批：跨月分组头。不 sticky（Paging items 无法直接用 stickyHeader），
 *  不显示月合计（不新增聚合查询），只显示月份与已有角标命中数。 */
@Composable
private fun MonthGroupHeader(yearMonth: Int, count: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = MonthRange.label(yearMonth),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count?.let { "共 " + it + " 条" } ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
    }
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
        // 第 47 批：totals == null 表示跨范围态，只显示日期，
        // 不把 0 伪装成「本日无收支」，也不新增按范围的日聚合查询。
        if (totals != null) {
            Text(
                text = "支出 " + formatCents(totals.first) +
                    " · 收入 " + formatCents(totals.second),
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
        }
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
    // 第 62 批：明细列表副行也带卡号。DAO 投影只给原值，组合走 accountLabel。
    val account = accountLabel(row.fromAccountName, row.fromAccountLast4)
        ?: accountLabel(row.toAccountName, row.toAccountLast4) ?: "-"
    val category = row.categoryName ?: "转账"
    val party = row.counterparty.takeIf { it.isNotBlank() }
    val desc = row.description.takeIf { it.isNotBlank() }
    // 第 43 批 2-A'：描述有值用描述做主标题，空着回退交易对方。
    // 超市可写「牛奶鸡蛋」，餐饮只看「肯德基」零额外输入，FF3 占位对方退到副行。
    val primary = when {
        type == RecordType.TRANSFER -> "转账"
        desc != null -> desc
        party != null -> party
        else -> category
    }
    val partyTag = when {
        type == RecordType.TRANSFER -> null
        desc != null -> null
        party == null -> null
        type == RecordType.INCOME -> "来源"
        else -> "收款方"
    }
    val secondary = listOfNotNull(
        category.takeIf { primary != category },
        party.takeIf { desc != null && it != primary },
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


