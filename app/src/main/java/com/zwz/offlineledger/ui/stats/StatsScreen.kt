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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.zwz.offlineledger.ui.charts.ChartFullscreenActivity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.ui.charts.DonutChart
import com.zwz.offlineledger.ui.charts.LineChart
import com.zwz.offlineledger.ui.charts.LineSeries
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val vm: StatsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            StatsViewModel(ep.recordDao(), ep.categoryDao(), ep.budgetDao(), ep.accountDao())
        }
    )
    val state by vm.state.collectAsState()
    var pointIndex by remember { mutableStateOf<Int?>(null) }
    var pointMode by remember { mutableStateOf(StatsMode.MONTH) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "统计", style = MaterialTheme.typography.headlineMedium)

        PeriodSwitcher(state, vm)
        CurrencySwitcher(state, vm)
        OverviewCard(state)
        BudgetSection(state)

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

        SectionLabel("支出分类占比")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.setSliceLevel(SliceLevel.ROOT) }) {
                Text(
                    if (state.sliceLevel == SliceLevel.ROOT) "● 按大类" else "按大类",
                    color = state.sliceLevelColor(SliceLevel.ROOT)
                )
            }
            TextButton(onClick = { vm.setSliceLevel(SliceLevel.CHILD) }) {
                Text(
                    if (state.sliceLevel == SliceLevel.CHILD) "● 按小类" else "按小类",
                    color = state.sliceLevelColor(SliceLevel.CHILD)
                )
            }
        }

        val slices = if (state.sliceLevel == SliceLevel.ROOT)
            state.rootSlices else state.childSlices
        DonutChart(
            sectors = ChartData.sectors(slices),
            totalCents = state.netExpenseCents,
            centerLabel = if (state.sliceLevel == SliceLevel.ROOT) "一级净支出" else "二级净支出",
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

        SectionLabel(if (state.mode == StatsMode.MONTH) "每日支出趋势" else "年度收支趋势")
        if (state.mode == StatsMode.MONTH) {
            // 阶段 8：默认只画一条线，想对比时再切「两者」。
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TrendMetric.values().forEach { m ->
                    TextButton(onClick = { vm.setTrendMetric(m) }) {
                        Text(
                            if (state.trendMetric == m) "● " + metricLabel(m)
                            else metricLabel(m),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            // 第三批：单线模式用该系列自己的最大值，不再与收入共用而贴底。
            val max = ChartData.seriesMaxSingle(
                when (state.trendMetric) {
                    TrendMetric.EXPENSE -> state.daily.map { it.expenseCents }
                    TrendMetric.INCOME -> state.daily.map { it.incomeCents }
                    TrendMetric.BOTH -> state.daily.map { it.expenseCents } +
                        state.daily.map { it.incomeCents }
                }
            )
            val expenseSeries = LineSeries(
                "支出", LedgerColors.ExpenseRed,
                ChartData.normalize(state.daily.map { it.expenseCents }, max)
            )
            val incomeSeries = LineSeries(
                "收入", LedgerColors.IncomeGreen,
                ChartData.normalize(state.daily.map { it.incomeCents }, max)
            )
            val shown = when (state.trendMetric) {
                TrendMetric.EXPENSE -> listOf(expenseSeries)
                TrendMetric.INCOME -> listOf(incomeSeries)
                TrendMetric.BOTH -> listOf(expenseSeries, incomeSeries)
            }
            LineChart(
                series = shown,
                xLabels = state.daily.map { it.day.toString() + "日" },
                yLabels = ChartData.yAxisLabels(max),
                onPointTap = { idx -> pointIndex = idx; pointMode = StatsMode.MONTH }
            )
            ChartZoomButton(
                title = "每日" + metricLabel(state.trendMetric) + "趋势",
                values = (when (state.trendMetric) {
                    TrendMetric.EXPENSE -> state.daily.map { it.expenseCents }
                    TrendMetric.INCOME -> state.daily.map { it.incomeCents }
                    TrendMetric.BOTH -> state.daily.map { it.expenseCents }
                }).toLongArray(),
                labels = ArrayList(state.daily.map { it.day.toString() + "日" }),
                colorName = if (state.trendMetric == TrendMetric.INCOME) "INCOME" else "EXPENSE"
            )
        } else {
            // 第三批：年度也能切单线，单线按自身最大值缩放。
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TrendMetric.values().forEach { m ->
                    TextButton(onClick = { vm.setTrendMetric(m) }) {
                        Text(
                            if (state.trendMetric == m) "● " + metricLabel(m)
                            else metricLabel(m),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            val max = ChartData.seriesMaxSingle(
                when (state.trendMetric) {
                    TrendMetric.EXPENSE -> state.monthly.map { it.expenseCents }
                    TrendMetric.INCOME -> state.monthly.map { it.incomeCents }
                    TrendMetric.BOTH -> state.monthly.map { it.expenseCents } +
                        state.monthly.map { it.incomeCents }
                }
            )
            val yearExpenseSeries = LineSeries(
                "支出", LedgerColors.ExpenseRed,
                ChartData.normalize(state.monthly.map { it.expenseCents }, max)
            )
            val yearIncomeSeries = LineSeries(
                "收入", LedgerColors.IncomeGreen,
                ChartData.normalize(state.monthly.map { it.incomeCents }, max)
            )
            val shownYear = when (state.trendMetric) {
                TrendMetric.EXPENSE -> listOf(yearExpenseSeries)
                TrendMetric.INCOME -> listOf(yearIncomeSeries)
                TrendMetric.BOTH -> listOf(yearExpenseSeries, yearIncomeSeries)
            }
            LineChart(
                series = shownYear,
                xLabels = state.monthly.map { it.month.toString() + "月" },
                yLabels = ChartData.yAxisLabels(max),
                onPointTap = { idx -> pointIndex = idx; pointMode = StatsMode.YEAR }
            )
            ChartZoomButton(
                title = "年度收支趋势（" + state.year + "）",
                values = (when (state.trendMetric) {
                    TrendMetric.INCOME -> state.monthly.map { it.incomeCents }
                    else -> state.monthly.map { it.expenseCents }
                }).toLongArray(),
                labels = ArrayList(state.monthly.map { it.month.toString() + "月" }),
                colorName = if (state.trendMetric == TrendMetric.INCOME) "INCOME" else "EXPENSE"
            )
            // 资产线单独一张图，避免与月度收支量级差大时三线糊在一起。
            SectionLabel("资产余额趋势（" + state.currencyCode + "）")
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
                yLabels = ChartData.yAxisLabels(assetMax),
                onPointTap = { idx -> pointIndex = idx; pointMode = StatsMode.YEAR }
            )
            ChartZoomButton(
                title = "资产余额趋势（" + state.currencyCode + " · " + state.year + "）",
                values = state.assetTrend.map { it.coerceAtLeast(0L) }.toLongArray(),
                labels = ArrayList(state.monthly.map { it.month.toString() + "月" }),
                colorName = "ASSET"
            )
            Text(
                text = "资产线按当前币种累计推算，不跨币种混算；点击任意图可查看具体金额。",
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
        }

        Text(
            text = "统计口径：仅 EXPENSE / INCOME 且 excludeFromStats = 0；" +
                "校准记录与转账不进任何图。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(top = 12.dp)
        )
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

    if (state.drillRootId != null || state.drillCategoryId != null) {
        DrillDownDialog(state = state, vm = vm)
    }
}

/** 阶段 6c-1：币种切换，默认必须选中一个具体币种，不提供「全部」混算选项。 */
@Composable
private fun CurrencySwitcher(state: StatsUiState, vm: StatsViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.currencies.forEach { cc ->
            TextButton(onClick = { vm.setCurrency(cc) }) {
                Text(
                    if (cc == state.currencyCode) "● $cc" else cc,
                    color = if (cc == state.currencyCode) LedgerColors.IncomeGreen
                    else LedgerColors.Neutral
                )
            }
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
            TextButton(onClick = { vm.setMode(StatsMode.MONTH) }) {
                Text(
                    if (state.mode == StatsMode.MONTH) "● 月度" else "月度",
                    color = if (state.mode == StatsMode.MONTH) LedgerColors.IncomeGreen
                    else LedgerColors.Neutral
                )
            }
            TextButton(onClick = { vm.setMode(StatsMode.YEAR) }) {
                Text(
                    if (state.mode == StatsMode.YEAR) "● 年度" else "年度",
                    color = if (state.mode == StatsMode.YEAR) LedgerColors.IncomeGreen
                    else LedgerColors.Neutral
                )
            }
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

private fun StatsUiState.sliceLevelColor(level: SliceLevel) =
    if (sliceLevel == level) LedgerColors.IncomeGreen else LedgerColors.Neutral

private fun metricLabel(m: TrendMetric): String = when (m) {
    TrendMetric.EXPENSE -> "支出"
    TrendMetric.INCOME -> "收入"
    TrendMetric.BOTH -> "两者"
}

@Composable
private fun OverviewCard(state: StatsUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "收入 " + formatCents(state.totalIncomeCents),
                    color = LedgerColors.IncomeGreen,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "支出 " + formatCents(state.totalExpenseCents),
                    color = LedgerColors.ExpenseRed,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val balance = state.totalIncomeCents - state.totalExpenseCents
            Text(
                text = "结余 " + formatCents(balance) + " 元",
                style = MaterialTheme.typography.titleLarge,
                color = if (balance < 0) LedgerColors.ExpenseRed else LedgerColors.IncomeGreen
            )
            // 阶段 6b：毛支出 / 已报销 / 净支出三者并列，避免口径混淆。
            Text(
                text = "已报销 / 退款 " + formatCents(state.settlementCents) +
                    " 元  ·  净支出 " + formatCents(state.netExpenseCents) + " 元",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            Text(
                text = "分类占比与预算进度均按净支出计算",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
        }
    }
}

/** 预算进度区：消耗来自 SQL 聚合，超支只在应用内标红，不发通知。 */
@Composable
private fun BudgetSection(state: StatsUiState) {
    if (state.budgetItems.isEmpty()) return
    SectionLabel("本月预算（按净支出）")
    state.budgetItems.forEach { item ->
        val p = item.progress
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = item.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (p.isOver)
                            "超支 " + formatCents(p.overCents) + " 元"
                        else
                            "剩余 " + formatCents(p.remainingCents) + " 元",
                        color = if (p.isOver) LedgerColors.ExpenseRed else LedgerColors.IncomeGreen,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "已用 " + formatCents(p.spentCents) +
                        " / 限额 " + formatCents(item.limitCents) + " 元",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )
                // 自绘进度条：不依赖 LinearProgressIndicator 的跨版本 API 签名。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(LedgerColors.Neutral.copy(alpha = 0.25f))
                        .padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(p.usedRatio.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(
                                if (p.isOver) LedgerColors.ExpenseRed else LedgerColors.IncomeGreen
                            )
                    )
                }
            }
        }
    }
}

/** 第三批：横屏全屏放大入口；只传展示数据，不带任何数据库对象。 */
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}
