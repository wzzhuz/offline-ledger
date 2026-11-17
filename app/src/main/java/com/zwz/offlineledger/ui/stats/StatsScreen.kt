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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** 第 13 批：分类洞察默认排行，可切回原有环形图。 */
private enum class InsightMode { RANK, DONUT }
private enum class RankDim { CATEGORY, COUNTERPARTY, ACCOUNT }

/**
 * 第 13 批：统计页由「所有区块顺次平铺」改为「一卡一结论」：
 * 固定顶部 → 总结卡 → 分类洞察卡 → 趋势卡 →（年度）资产卡 → 口径入口。
 * 既有的环形图、三级下钻、双折线、资产累计推算、横屏放大、点数值全部保留，
 * 只是信息层级重排；合计一致性警告 MUST 置顶显眼。
 */
@Composable
fun StatsScreen(onOpenCategories: () -> Unit = {}) {
    val context = LocalContext.current
    val vm: StatsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            StatsViewModel(ep.recordDao(), ep.categoryDao(), ep.budgetDao(), ep.accountDao())
        }
    )
    val state by vm.state.collectAsState()
    var pointIndex by remember { mutableStateOf<Int?>(null) }
    var pointMode by remember { mutableStateOf(StatsMode.MONTH) }
    var showAverage by remember { mutableStateOf(false) }
    var showScopeInfo by remember { mutableStateOf(false) }
    var insightMode by remember { mutableStateOf(InsightMode.RANK) }
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
            CategoryInsightCard(
                state = state,
                vm = vm,
                insightMode = insightMode,
                onInsightMode = { insightMode = it },
                rankDim = rankDim,
                onRankDim = { rankDim = it },
                onOpenCategories = onOpenCategories
            )
            TrendCard(
                state = state,
                vm = vm,
                pointIndex = pointIndex,
                pointMode = pointMode,
                onPoint = { idx, mode -> pointIndex = idx; pointMode = mode },
                yLabels = { max -> yLabels(max) }
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
                            state.averageUnits + (if (state.mode == StatsMode.MONTH) " 天" else " 个月") +
                            " = " + formatCents(state.averageCents) + " 元"
                    )
                    Text(
                        "分母是自然" + (if (state.mode == StatsMode.MONTH) "月天数" else "年 12 个月") +
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
        DrillDownDialog(state = state, vm = vm)
    }

    pointIndex?.let { idx ->
        val daily = state.daily.getOrNull(idx)
        val month = state.monthly.getOrNull(idx)
        val income = if (pointMode == StatsMode.MONTH)
            daily?.incomeCents ?: 0L else month?.incomeCents ?: 0L
        val expense = if (pointMode == StatsMode.MONTH)
            daily?.expenseCents ?: 0L else month?.expenseCents ?: 0L
        val title = if (pointMode == StatsMode.MONTH)
            MonthRange.label(state.yearMonth) + " · " + (daily?.day ?: (idx + 1)) + " 日"
        else state.year.toString() + " 年 " + (idx + 1) + " 月"
        AlertDialog(
            onDismissRequest = { pointIndex = null },
            title = { Text(title) },
            text = {
                Column {
                    Text("收入 " + formatCents(income) + " 元")
                    Text("支出 " + formatCents(expense) + " 元")
                    if (pointMode == StatsMode.YEAR) {
                        Text(
                            "月末资产 " +
                                formatCents(state.assetTrend.getOrElse(idx) { 0L }) + " 元"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pointIndex = null }) { Text("关闭") }
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
            val period = if (state.mode == StatsMode.MONTH)
                MonthRange.label(state.yearMonth) else state.year.toString() + " 年"
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
/* ② 分类洞察卡：Top5 排行 / 可切环形图 / 分类管理短跳转                   */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryInsightCard(
    state: StatsUiState,
    vm: StatsViewModel,
    insightMode: InsightMode,
    onInsightMode: (InsightMode) -> Unit,
    rankDim: RankDim,
    onRankDim: (RankDim) -> Unit,
    onOpenCategories: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("分类洞察", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onOpenCategories) {
                    Text("分类 ›", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = insightMode == InsightMode.RANK,
                    onClick = { onInsightMode(InsightMode.RANK) },
                    label = { Text("Top5 排行") }
                )
                FilterChip(
                    selected = insightMode == InsightMode.DONUT,
                    onClick = { onInsightMode(InsightMode.DONUT) },
                    label = { Text("环形图") }
                )
            }

            if (insightMode == InsightMode.RANK) {
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
                        RankRowView(
                            row = row,
                            onClick = {
                                if (rankDim == RankDim.CATEGORY) row.id?.let { vm.openRoot(it) }
                            }
                        )
                    }
                    Text(
                        text = if (rankDim == RankDim.CATEGORY)
                            "点分类可展开二级构成；排行与环形图共用同一份净支出切片。"
                        else "按当前币种统计，不跨币种混算；金额来自 SQL 聚合。",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
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
                        val id = slices.getOrNull(i)?.id
                        if (id != null) {
                            if (state.sliceLevel == SliceLevel.ROOT) vm.openRoot(id)
                            else vm.openCategory(id)
                        }
                    }
                )
                Text(
                    text = "点击扇区" + if (state.sliceLevel == SliceLevel.ROOT)
                        "展开该大类下的二级构成" else "查看该分类明细",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
        }
    }
}

@Composable
private fun RankRowView(row: InsightData.RankRow, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
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
/* ③ 趋势卡：原有折线 / 单线切换 / 点数值 / 横屏放大，一字不改地保留      */
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
                text = if (state.mode == StatsMode.MONTH) "每日支出趋势" else "年度收支趋势",
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
            val values = if (state.mode == StatsMode.MONTH) {
                when (state.trendMetric) {
                    TrendMetric.EXPENSE -> state.daily.map { it.expenseCents }
                    TrendMetric.INCOME -> state.daily.map { it.incomeCents }
                    TrendMetric.BOTH -> state.daily.map { it.expenseCents } +
                        state.daily.map { it.incomeCents }
                }
            } else {
                when (state.trendMetric) {
                    TrendMetric.EXPENSE -> state.monthly.map { it.expenseCents }
                    TrendMetric.INCOME -> state.monthly.map { it.incomeCents }
                    TrendMetric.BOTH -> state.monthly.map { it.expenseCents } +
                        state.monthly.map { it.incomeCents }
                }
            }
            val max = ChartData.seriesMaxSingle(values)
            val expenseSeries: List<LineSeries>
            val incomeSeries: List<LineSeries>
            val labels: List<String>
            if (state.mode == StatsMode.MONTH) {
                expenseSeries = listOf(
                    LineSeries("支出", LedgerColors.ExpenseRed,
                        ChartData.normalize(state.daily.map { it.expenseCents }, max))
                )
                incomeSeries = listOf(
                    LineSeries("收入", LedgerColors.IncomeGreen,
                        ChartData.normalize(state.daily.map { it.incomeCents }, max))
                )
                labels = state.daily.map { it.day.toString() + "日" }
            } else {
                expenseSeries = listOf(
                    LineSeries("支出", LedgerColors.ExpenseRed,
                        ChartData.normalize(state.monthly.map { it.expenseCents }, max))
                )
                incomeSeries = listOf(
                    LineSeries("收入", LedgerColors.IncomeGreen,
                        ChartData.normalize(state.monthly.map { it.incomeCents }, max))
                )
                labels = state.monthly.map { it.month.toString() + "月" }
            }
            val shown = when (state.trendMetric) {
                TrendMetric.EXPENSE -> expenseSeries
                TrendMetric.INCOME -> incomeSeries
                TrendMetric.BOTH -> expenseSeries + incomeSeries
            }
            val shownValues = if (state.mode == StatsMode.MONTH) {
                when (state.trendMetric) {
                    TrendMetric.EXPENSE -> state.daily.map { it.expenseCents }
                    TrendMetric.INCOME -> state.daily.map { it.incomeCents }
                    TrendMetric.BOTH -> state.daily.map { it.expenseCents }
                }
            } else {
                when (state.trendMetric) {
                    TrendMetric.INCOME -> state.monthly.map { it.incomeCents }
                    else -> state.monthly.map { it.expenseCents }
                }
            }
            LineChart(
                series = shown,
                xLabels = labels,
                yLabels = yLabels(max),
                valueLabels = shownValues.map { formatCents(it) },
                selectedIndex = if (pointMode == state.mode) pointIndex else null,
                onPointTap = { idx -> onPoint(idx, state.mode) }
            )
            ChartZoomButton(
                title = if (state.mode == StatsMode.MONTH)
                    "每日" + metricLabel(state.trendMetric) + "趋势"
                else "年度收支趋势（" + state.year + "）",
                values = shownValues.map { it }.toLongArray(),
                labels = ArrayList(labels),
                colorName = if (state.trendMetric == TrendMetric.INCOME) "INCOME" else "EXPENSE"
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* ④ 年度资产卡                                                         */
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
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.shift(-1) }) { Text("◀") }
            Text(
                text = if (state.mode == StatsMode.MONTH)
                    MonthRange.label(state.yearMonth) else state.year.toString() + " 年",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { vm.shift(1) }) { Text("▶") }
        }
    }
}

private fun StatsViewModel.shift(delta: Int) {
    if (state.value.mode == StatsMode.MONTH) shiftMonth(delta) else shiftYear(delta)
}

private fun metricLabel(m: TrendMetric): String = when (m) {
    TrendMetric.EXPENSE -> "支出"
    TrendMetric.INCOME -> "收入"
    TrendMetric.BOTH -> "两者"
}

@Composable
private fun ChartZoomButton(
    title: String,
    values: LongArray,
    labels: ArrayList<String>,
    colorName: String
) {
    val context = LocalContext.current
    TextButton(onClick = {
        context.startActivity(
            ChartFullscreenActivity.intentFor(context, title, values, labels, colorName)
        )
    }) { Text("⛶ 横屏放大", style = MaterialTheme.typography.labelSmall) }
}
