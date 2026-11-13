package com.zwz.offlineledger.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            StatsViewModel(ep.recordDao(), ep.categoryDao())
        }
    )
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "统计", style = MaterialTheme.typography.headlineMedium)

        PeriodSwitcher(state, vm)
        OverviewCard(state)

        val check = state.totalsCheck
        if (check != null && !check.consistent) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = "⚠ 合计不一致：一级 " + formatCents(check.rootCents) +
                        " / 二级 " + formatCents(check.childCents) +
                        " / 总支出 " + formatCents(check.totalCents) +
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
            totalCents = state.totalExpenseCents,
            centerLabel = if (state.sliceLevel == SliceLevel.ROOT) "一级支出" else "二级支出",
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
            val max = ChartData.seriesMax(
                listOf(
                    state.daily.map { it.expenseCents },
                    state.daily.map { it.incomeCents }
                )
            )
            LineChart(
                series = listOf(
                    LineSeries(
                        "支出", LedgerColors.ExpenseRed,
                        ChartData.normalize(state.daily.map { it.expenseCents }, max)
                    ),
                    LineSeries(
                        "收入", LedgerColors.IncomeGreen,
                        ChartData.normalize(state.daily.map { it.incomeCents }, max)
                    )
                ),
                xLabels = state.daily.map { it.day.toString() }
            )
        } else {
            val max = ChartData.seriesMax(
                listOf(
                    state.monthly.map { it.expenseCents },
                    state.monthly.map { it.incomeCents }
                )
            )
            LineChart(
                series = listOf(
                    LineSeries(
                        "支出", LedgerColors.ExpenseRed,
                        ChartData.normalize(state.monthly.map { it.expenseCents }, max)
                    ),
                    LineSeries(
                        "收入", LedgerColors.IncomeGreen,
                        ChartData.normalize(state.monthly.map { it.incomeCents }, max)
                    )
                ),
                xLabels = state.monthly.map { it.month.toString() + "月" }
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

    if (state.drillRootId != null || state.drillCategoryId != null) {
        DrillDownDialog(state = state, vm = vm)
    }
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
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}
