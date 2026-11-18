package com.zwz.offlineledger.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.engine.InsightData
import com.zwz.offlineledger.ui.charts.ChartFullscreenActivity
import com.zwz.offlineledger.ui.charts.DonutChart
import com.zwz.offlineledger.ui.charts.LineChart
import com.zwz.offlineledger.ui.charts.LineSeries
import com.zwz.offlineledger.ui.components.AmountMask
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors

private enum class RankDim { CATEGORY, COUNTERPARTY, ACCOUNT }

/**
 * 第 14 批：统计页改为「一卡一结论 + 允许纵向滚动的浏览型长报表」。
 * 顺序：固定顶部 →（警告置顶）总结卡 → 趋势卡 → 分类占比卡（环形独立）→
 * 分类排行卡（Top5 独立）→（年度）资产卡 → 口径入口。
 * 环形图与排行各自常驻，MUST NOT 再用切换 chip 把两张卡合并——第 13 批为挤一屏
 * 的取巧做法已被使用者否掉：查看统计页允许滚动，不该硬塞。
 * 既有三级下钻、双折线、资产累计推算、横屏放大、点数值全部保留；
 * 合计一致性警告 MUST 置顶显眼。
 */
@Composable
fun StatsScreen(
    refreshTick: Int = 0,
    onOpenCategories: () -> Unit = {},
    onOpenCategoryInDetail: (Long, Boolean, Int, String, String) -> Unit = { _, _, _, _, _ -> },
    onOpenCounterpartyInDetail: (String, Int, String) -> Unit = { _, _, _ -> },
    onOpenAccountInDetail: (Long, String, Int, String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val vm: StatsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            StatsViewModel(ep.recordDao(), ep.categoryDao(), ep.budgetDao(), ep.accountDao())
        }
    )
    val state by vm.state.collectAsState()
    // 第 23 批：导入 / 恢复后由 LedgerApp 转发的 refreshTick 触发立即重查。
    LaunchedEffect(refreshTick) { if (refreshTick > 0) vm.reload() }
    var pointIndex by remember { mutableStateOf<Int?>(null) }
    var pointMode by remember { mutableStateOf(StatsMode.MONTH) }
    var showAverage by remember { mutableStateOf(false) }
    var showScopeInfo by remember { mutableStateOf(false) }
    var rankDim by remember { mutableStateOf(RankDim.CATEGORY) }

    // 组合期间读可观察状态：隐藏金额开关一变，竖轴刻度也跟着重组。
    val hidden = AmountMask.hidden
    fun yLabels(max: Long): List<String> =
        if (hidden) List(5) { "****" } else ChartData.yAxisLabels(max)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "统计", style = MaterialTheme.typography.headlineMedium)
        PeriodSwitcher(state, vm)
        CurrencySwitcher(state, vm)

        val check = state.totalsCheck
        if (check != null && !check.consistent) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = "⚠ 合计不一致：一级 " + formatCents(check.rootCents) +
                        " / 二级 " + formatCents(check.childCents) +
                        " / 总净支出 " + formatCents(check.totalCents) +
                        "，差额 " + check.driftCents + " 分",
                    color = LedgerColors.ExpenseRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        val empty = state.recordCount == 0 &&
            state.totalExpenseCents == 0L && state.totalIncomeCents == 0L
        if (empty) {
            EmptyInsight(state, vm)
        } else {
            InsightSummaryCard(state, onAverage = { showAverage = true })
            TrendCard(
                state = state,
                vm = vm,
                pointIndex = pointIndex,
                pointMode = pointMode,
                onPoint = { idx, mode ->
                    pointIndex = if (idx < 0) null else idx
                    pointMode = mode
                },
                yLabels = { max -> yLabels(max) }
            )
            DonutInsightCard(
                state = state,
                vm = vm,
                onOpenCategories = onOpenCategories,
                onOpenCategoryInDetail = onOpenCategoryInDetail
            )
            RankInsightCard(
                state = state,
                vm = vm,
                rankDim = rankDim,
                onRankDim = { rankDim = it },
                onOpenCategoryInDetail = onOpenCategoryInDetail,
                onOpenCounterpartyInDetail = onOpenCounterpartyInDetail,
                onOpenAccountInDetail = onOpenAccountInDetail
            )
            if (state.mode == StatsMode.YEAR) {
                AssetCard(
                    state = state,
                    pointIndex = pointIndex,
                    pointMode = pointMode,
                    onPoint = { idx -> pointIndex = idx; pointMode = StatsMode.YEAR },
                    yLabels = { max -> yLabels(max) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showScopeInfo = true }) {
                Text("ⓘ 统计口径与资产推算说明", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (showAverage) {
        AlertDialog(
            onDismissRequest = { showAverage = false },
            title = { Text(state.averageLabel + "怎么算") },
            text = {
                Column {
                    Text(
                        formatCents(state.totalExpenseCents) + " 元 ÷ " +
                            state.averageUnits + averageUnitLabel(state.mode) +
                            " = " + formatCents(state.averageCents) + " 元"
                    )
                    Text(
                        "分母是" + averageDenominatorText(state.mode) +
                            "，不按有流水的天数折算；仅统计当前币种。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAverage = false }) { Text("知道了") }
            }
        )
    }

    if (showScopeInfo) {
        AlertDialog(
            onDismissRequest = { showScopeInfo = false },
            title = { Text("统计口径") },
            text = {
                Column {
                    Text("仅 EXPENSE / INCOME 且 excludeFromStats = 0；校准记录与转账不进任何图。")
                    Text(
                        "资产线按当前币种累计推算，不跨币种混算；点击任意图可查看具体金额。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showScopeInfo = false }) { Text("关闭") }
            }
        )
    }

    if (state.drillRootId != null || state.drillCategoryId != null) {
        DrillDownDialog(
            state = state,
            vm = vm,
            onOpenCategoryInDetail = { id ->
                vm.closeDrill()
                onOpenCategoryInDetail(
                    id, false, state.yearMonth, state.currencyCode,
                    state.drillCategoryName.ifBlank { "分类" }
                )
            }
        )
    }



}

/* ------------------------------------------------------------------ */
/* ① 总结卡：大数字 + 笔数 + 可解释均值 + 预算进度                        */
/* ------------------------------------------------------------------ */
@Composable
private fun InsightSummaryCard(state: StatsUiState, onAverage: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val period = when (state.mode) {
                StatsMode.MONTH -> MonthRange.label(state.yearMonth)
                StatsMode.YEAR -> state.year.toString() + " 年"
                StatsMode.MULTI_YEAR -> "近 5 年（至 " + state.year + "）"
            }
            Text(
                text = period + " · " + state.currencyCode,
                style = MaterialTheme.typography.labelMedium,
                color = LedgerColors.Neutral
            )
            Text(
                text = formatCents(state.totalExpenseCents) + " 元",
                style = MaterialTheme.typography.headlineLarge,
                color = LedgerColors.ExpenseRed
            )
            Text(
                text = "总支出 · " + state.recordCount + " 笔",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "收入 " + formatCents(state.totalIncomeCents),
                    color = LedgerColors.IncomeGreen,
                    style = MaterialTheme.typography.bodyMedium
                )
                val balance = state.totalIncomeCents - state.totalExpenseCents
                Text(
                    text = "结余 " + formatCents(balance),
                    color = if (balance < 0) LedgerColors.ExpenseRed else LedgerColors.IncomeGreen,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.averageLabel + " " + formatCents(state.averageCents) + " 元",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onAverage) {
                    Text("怎么算？", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                text = "已报销 / 退款 " + formatCents(state.settlementCents) +
                    " · 净支出 " + formatCents(state.netExpenseCents),
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            if (state.budgetItems.isNotEmpty()) {
                Text(
                    text = "本月预算（按净支出）",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 10.dp)
                )
                state.budgetItems.take(2).forEach { item ->
                    val p = item.progress
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = if (p.isOver)
                                "超支 " + formatCents(p.overCents)
                            else "剩余 " + formatCents(p.remainingCents),
                            color = if (p.isOver) LedgerColors.ExpenseRed else LedgerColors.IncomeGreen,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    MiniBar(p.usedRatio, p.isOver)
                }
            }
        }
    }
}

@Composable
private fun MiniBar(ratio: Float, isOver: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(LedgerColors.Neutral.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (isOver) LedgerColors.ExpenseRed else LedgerColors.IncomeGreen
                )
        )
    }
}

/* ------------------------------------------------------------------ */
/* ② 分类占比卡：环形图独立常驻（一级 / 二级切换 + 三级下钻）              */
/* ------------------------------------------------------------------ */
@Composable
private fun DonutInsightCard(
    state: StatsUiState,
    vm: StatsViewModel,
    onOpenCategories: () -> Unit,
    onOpenCategoryInDetail: (Long, Boolean, Int, String, String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("分类占比", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onOpenCategories) {
                    Text("分类管理 ›", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.sliceLevel == SliceLevel.ROOT,
                    onClick = { vm.setSliceLevel(SliceLevel.ROOT) },
                    label = { Text("按大类") }
                )
                FilterChip(
                    selected = state.sliceLevel == SliceLevel.CHILD,
                    onClick = { vm.setSliceLevel(SliceLevel.CHILD) },
                    label = { Text("按小类") }
                )
            }
            val slices = if (state.sliceLevel == SliceLevel.ROOT)
                state.rootSlices else state.childSlices
            DonutChart(
                sectors = ChartData.sectors(slices),
                totalCents = state.netExpenseCents,
                centerLabel = if (state.sliceLevel == SliceLevel.ROOT)
                    "一级净支出" else "二级净支出",
                onSectorClick = { i ->
                    val slice = slices.getOrNull(i)
                    val id = slice?.id
                    if (id != null) {
                        if (state.sliceLevel == SliceLevel.ROOT) vm.openRoot(id)
                        else onOpenCategoryInDetail(
                            id, false, state.yearMonth, state.currencyCode,
                            slice?.name ?: "分类"
                        )
                    }
                }
            )
            Text(
                text = "点击扇区或图例" + if (state.sliceLevel == SliceLevel.ROOT)
                    "展开该大类下的二级构成" else "跳到明细页查看该分类流水",
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* ③ 分类排行卡：Top5 独立常驻，三维切换只在卡内切换维度                   */
/* ------------------------------------------------------------------ */
@Composable
private fun RankInsightCard(
    state: StatsUiState,
    vm: StatsViewModel,
    rankDim: RankDim,
    onRankDim: (RankDim) -> Unit,
    onOpenCategoryInDetail: (Long, Boolean, Int, String, String) -> Unit,
    onOpenCounterpartyInDetail: (String, Int, String) -> Unit,
    onOpenAccountInDetail: (Long, String, Int, String) -> Unit
) {
    // 第 27 批：二级展开态跨重组 / 切 Tab 保留（与分类管理折叠态同口径）。
    var expandedRootId by rememberSaveable { mutableStateOf<Long?>(null) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("分类排行", style = MaterialTheme.typography.titleSmall)
                Text(
                    "按金额排序",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RankDim.values().forEach { d ->
                    FilterChip(
                        selected = rankDim == d,
                        onClick = { onRankDim(d) },
                        label = { Text(rankDimLabel(d)) }
                    )
                }
            }
            val rows = when (rankDim) {
                RankDim.CATEGORY -> state.topCategories
                RankDim.COUNTERPARTY -> state.topCounterparties
                RankDim.ACCOUNT -> state.topAccounts
            }
            if (rows.isEmpty()) {
                Text(
                    "本周期暂无该维度数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                rows.forEach { row ->
                    val kids = if (rankDim == RankDim.CATEGORY) {
                        row.id?.let { state.rankChildren[it] } ?: emptyList()
                    } else emptyList()
                    RankRowView(
                        row = row,
                        expandable = kids.isNotEmpty(),
                        expanded = expandedRootId == row.id,
                        onToggle = {
                            expandedRootId = if (expandedRootId == row.id) null else row.id
                        },
                        onClick = {
                            when (rankDim) {
                                RankDim.CATEGORY -> row.id?.let {
                                    onOpenCategoryInDetail(
                                        it, true, state.yearMonth,
                                        state.currencyCode, row.name
                                    )
                                }
                                RankDim.COUNTERPARTY ->
                                    onOpenCounterpartyInDetail(
                                        row.name, state.yearMonth, state.currencyCode
                                    )
                                RankDim.ACCOUNT -> row.id?.let {
                                    onOpenAccountInDetail(
                                        it, row.name, state.yearMonth, state.currencyCode
                                    )
                                }
                            }
                        }
                    )
                    if (rankDim == RankDim.CATEGORY && expandedRootId == row.id) {
                        kids.forEach { kid ->
                            RankRowView(
                                row = kid,
                                indent = true,
                                onClick = {
                                    kid.id?.let {
                                        onOpenCategoryInDetail(
                                            it, false, state.yearMonth,
                                            state.currencyCode, kid.name
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                Text(
                    text = when (rankDim) {
                        RankDim.CATEGORY ->
                            "点一级看该分类流水，点 ⌄ 展开二级；二级可直接跳明细。"
                        RankDim.COUNTERPARTY ->
                            "点收款方跳明细（包含匹配）；按当前币种统计，不跨币种混算。"
                        RankDim.ACCOUNT ->
                            "点账户跳明细；按当前币种统计，不跨币种混算。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RankRowView(
    row: InsightData.RankRow,
    onClick: () -> Unit,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggle: () -> Unit = {},
    indent: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                Text(
                    (if (indent) "  └ " else "") + row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
            Text(
                text = formatCents(row.cents),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = " " + (row.ratio * 100).toInt() + "%",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            if (expandable) {
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "⌃" else "⌄")
                }
            }
        }
        MiniBar(row.ratio, false)
    }
}

private fun rankDimLabel(d: RankDim): String = when (d) {
    RankDim.CATEGORY -> "分类"
    RankDim.COUNTERPARTY -> "收款方"
    RankDim.ACCOUNT -> "账户"
}

/* ------------------------------------------------------------------ */
/* ④ 趋势卡：原有折线 / 单线切换 / 点数值 / 横屏放大，一字不改地保留      */
/* ------------------------------------------------------------------ */
@Composable
private fun TrendCard(
    state: StatsUiState,
    vm: StatsViewModel,
    pointIndex: Int?,
    pointMode: StatsMode,
    onPoint: (Int, StatsMode) -> Unit,
    yLabels: (Long) -> List<String>
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (state.mode) {
                    StatsMode.MONTH -> "每日收支趋势"
                    StatsMode.YEAR -> "年度收支趋势"
                    StatsMode.MULTI_YEAR -> "近 5 年收支趋势"
                },
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TrendMetric.values().forEach { m ->
                    FilterChip(
                        selected = state.trendMetric == m,
                        onClick = { vm.setTrendMetric(m) },
                        label = { Text(metricLabel(m)) }
                    )
                }
            }

            val points: List<TrendPoint> = when (state.mode) {
                StatsMode.MONTH -> state.daily.map {
                    TrendPoint(it.day.toString() + "日", it.expenseCents, it.incomeCents)
                }
                StatsMode.YEAR -> state.monthly.map {
                    TrendPoint(it.month.toString() + "月", it.expenseCents, it.incomeCents)
                }
                StatsMode.MULTI_YEAR -> state.yearly.map {
                    TrendPoint(it.year.toString() + "年", it.expenseCents, it.incomeCents)
                }
            }
            val labels = points.map { it.label }
            val expenseValues = points.map { it.expenseCents }
            val incomeValues = points.map { it.incomeCents }
            val netValues = points.map { it.incomeCents - it.expenseCents }
            val metric = state.trendMetric
            val signed = metric == TrendMetric.NET
            val shownValues: List<Long> = when (metric) {
                TrendMetric.EXPENSE -> expenseValues
                TrendMetric.INCOME -> incomeValues
                TrendMetric.BOTH -> expenseValues
                TrendMetric.NET -> netValues
            }
            val max = if (signed) ChartData.seriesMaxAbsSigned(netValues)
                else ChartData.seriesMaxSingle(
                    if (metric == TrendMetric.BOTH) expenseValues + incomeValues
                    else shownValues
                )
            val axis = if (signed) ChartData.yAxisLabelsSigned(max) else yLabels(max)
            val series: List<LineSeries> = when (metric) {
                TrendMetric.EXPENSE -> listOf(
                    LineSeries(
                        "支出", LedgerColors.ExpenseRed,
                        ChartData.normalize(expenseValues, max)
                    )
                )
                TrendMetric.INCOME -> listOf(
                    LineSeries(
                        "收入", LedgerColors.IncomeGreen,
                        ChartData.normalize(incomeValues, max)
                    )
                )
                TrendMetric.BOTH -> listOf(
                    LineSeries(
                        "支出", LedgerColors.ExpenseRed,
                        ChartData.normalize(expenseValues, max)
                    ),
                    LineSeries(
                        "收入", LedgerColors.IncomeGreen,
                        ChartData.normalize(incomeValues, max)
                    )
                )
                TrendMetric.NET -> listOf(
                    LineSeries(
                        "收入 − 支出", MaterialTheme.colorScheme.primary,
                        ChartData.normalizeSigned(netValues, max)
                    )
                )
            }
            Text(
                text = if (signed)
                    "净结余＝收入 − 支出，可为负；0 线在中间，仅当前币种、不混算"
                else "仅当前币种，不跨币种混算；点击数据点看具体金额",
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(top = 4.dp)
            )
            LineChart(
                series = series,
                xLabels = labels,
                yLabels = axis,
                valueLabels = shownValues.map { compactCents(it) },
                selectedIndex = if (pointMode == state.mode) pointIndex else null,
                staggerLabels = state.mode != StatsMode.MONTH,
                guideIndex = if (pointMode == state.mode) pointIndex else null,
                zeroLine = signed,
                onPointTap = { idx -> onPoint(idx, state.mode) }
            )
            if (pointIndex != null && pointMode == state.mode) {
                val i = pointIndex ?: 0
                val inc = incomeValues.getOrElse(i) { 0L }
                val exp = expenseValues.getOrElse(i) { 0L }
                Text(
                    text = "已选 " + labels.getOrElse(i) { "" } +
                        " · 收入 " + formatCents(inc) +
                        " · 支出 " + formatCents(exp) +
                        " · 净结余 " + formatCents(inc - exp) + " 元",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row {
                    if (state.mode == StatsMode.MULTI_YEAR) {
                        TextButton(onClick = {
                            state.yearly.getOrNull(i)?.let { vm.openYear(it.year) }
                            onPoint(-1, state.mode)
                        }) { Text("查看该年") }
                    }
                    TextButton(onClick = { onPoint(-1, state.mode) }) {
                        Text("清除选中")
                    }
                }
            }
            if (state.mode == StatsMode.MULTI_YEAR) {
                Text(
                    text = "点某年的数据点可回到该年的年度视图",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
            ChartZoomButton(
                title = when (state.mode) {
                    StatsMode.MONTH -> "每日" + metricLabel(metric) + "趋势"
                    StatsMode.YEAR -> "年度收支趋势（" + state.year + "）"
                    StatsMode.MULTI_YEAR -> "近 5 年" + metricLabel(metric) + "趋势"
                },
                values = shownValues.toLongArray(),
                labels = ArrayList(labels),
                colorName = if (metric == TrendMetric.INCOME) "INCOME" else "EXPENSE",
                signed = signed
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* ⑤ 年度资产卡                                                         */
/* ------------------------------------------------------------------ */
@Composable
private fun AssetCard(
    state: StatsUiState,
    pointIndex: Int?,
    pointMode: StatsMode,
    onPoint: (Int) -> Unit,
    yLabels: (Long) -> List<String>
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "资产余额趋势（" + state.currencyCode + "）",
                style = MaterialTheme.typography.titleSmall
            )
            val assetMax = ChartData.seriesMax(
                listOf(state.assetTrend.map { kotlin.math.abs(it) })
            )
            LineChart(
                series = listOf(
                    LineSeries(
                        "资产余额", LedgerColors.IncomeGreen,
                        ChartData.normalize(
                            state.assetTrend.map { it.coerceAtLeast(0L) }, assetMax
                        )
                    )
                ),
                xLabels = state.monthly.map { it.month.toString() + "月" },
                yLabels = yLabels(assetMax),
                valueLabels = state.assetTrend.map { formatCents(it.coerceAtLeast(0L)) },
                selectedIndex = if (pointMode == StatsMode.YEAR) pointIndex else null,
                onPointTap = onPoint
            )
            ChartZoomButton(
                title = "资产余额趋势（" + state.currencyCode + " · " + state.year + "）",
                values = state.assetTrend.map { it.coerceAtLeast(0L) }.toLongArray(),
                labels = ArrayList(state.monthly.map { it.month.toString() + "月" }),
                colorName = "ASSET"
            )
            Text(
                text = "按当前币种累计推算，不跨币种混算；点击任意图可查看具体金额。",
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* 空状态与顶部切换                                                     */
/* ------------------------------------------------------------------ */
@Composable
private fun EmptyInsight(state: StatsUiState, vm: StatsViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本周期还没有流水", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "当前币种 " + state.currencyCode + "，换个月份或切到年度看看。",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { vm.shift(-1) }) { Text("上个月") }
                TextButton(onClick = { vm.setMode(StatsMode.YEAR) }) { Text("看年度") }
            }
            Text(
                text = "引导只改周期，不会清空已选币种与视图。",
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
        }
    }
}

@Composable
private fun CurrencySwitcher(state: StatsUiState, vm: StatsViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.currencies.forEach { cc ->
            FilterChip(
                selected = cc == state.currencyCode,
                onClick = { vm.setCurrency(cc) },
                label = { Text(cc) }
            )
        }
    }
    Text(
        text = "当前币种：" + state.currencyCode + " · 不跨币种汇总",
        style = MaterialTheme.typography.bodySmall,
        color = LedgerColors.Neutral
    )
}

@Composable
private fun PeriodSwitcher(state: StatsUiState, vm: StatsViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = state.mode == StatsMode.MONTH,
                onClick = { vm.setMode(StatsMode.MONTH) },
                label = { Text("月度") }
            )
            FilterChip(
                selected = state.mode == StatsMode.YEAR,
                onClick = { vm.setMode(StatsMode.YEAR) },
                label = { Text("年度") }
            )
            FilterChip(
                selected = state.mode == StatsMode.MULTI_YEAR,
                onClick = { vm.setMode(StatsMode.MULTI_YEAR) },
                label = { Text("多年") }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.shift(-1) }) { Text("◀") }
            Text(
                text = when (state.mode) {
                    StatsMode.MONTH -> MonthRange.label(state.yearMonth)
                    StatsMode.YEAR -> state.year.toString() + " 年"
                    StatsMode.MULTI_YEAR -> "近 5 年 · 至 " + state.year
                },
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { vm.shift(1) }) { Text("▶") }
        }
    }
}

private fun StatsViewModel.shift(delta: Int) {
    when (state.value.mode) {
        StatsMode.MONTH -> shiftMonth(delta)
        StatsMode.YEAR, StatsMode.MULTI_YEAR -> shiftYear(delta)
    }
}

private fun metricLabel(m: TrendMetric): String = when (m) {
    TrendMetric.EXPENSE -> "支出"
    TrendMetric.INCOME -> "收入"
    TrendMetric.BOTH -> "两者"
    TrendMetric.NET -> "净结余"
}

private fun averageUnitLabel(mode: StatsMode): String = when (mode) {
    StatsMode.MONTH -> " 天"
    StatsMode.YEAR -> " 个月"
    StatsMode.MULTI_YEAR -> " 年"
}

private fun averageDenominatorText(mode: StatsMode): String = when (mode) {
    StatsMode.MONTH -> "自然月天数"
    StatsMode.YEAR -> "年 12 个月"
    StatsMode.MULTI_YEAR -> "近 5 个自然年"
}

/** 趋势卡的统一点位模型，避免月度 / 年度 / 多年写三套分支。 */
private data class TrendPoint(
    val label: String, val expenseCents: Long, val incomeCents: Long
)

/** 第 26 批：竖屏常驻标签用简化值，避免 12 点 27dp 间距下重叠。 */
private fun compactCents(c: Long): String {
    val abs = kotlin.math.abs(c)
    val sign = if (c < 0L) "-" else ""
    val yuan = abs / 100.0
    return when {
        yuan >= 10000.0 -> sign + (kotlin.math.round(yuan / 1000.0) / 10.0).toString() + "万"
        yuan >= 1000.0 -> sign + (kotlin.math.round(yuan / 100.0) / 10.0).toString() + "k"
        else -> sign + kotlin.math.round(yuan).toLong().toString()
    }
}

@Composable
private fun ChartZoomButton(
    title: String,
    values: LongArray,
    labels: ArrayList<String>,
    colorName: String,
    signed: Boolean = false
) {
    val context = LocalContext.current
    TextButton(onClick = {
        context.startActivity(
            ChartFullscreenActivity.intentFor(
                context, title, values, labels, colorName, signed
            )
        )
    }) { Text("⛶ 横屏放大", style = MaterialTheme.typography.labelSmall) }
}
