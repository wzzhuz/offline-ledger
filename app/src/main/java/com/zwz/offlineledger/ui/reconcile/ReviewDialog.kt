package com.zwz.offlineledger.ui.reconcile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.engine.Reconciliation
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors

@Composable
fun ReviewDialog(
    accountId: Long,
    differenceCents: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val vm: ReviewViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            ReviewViewModel(ep.accountDao(), ep.recordDao())
        }
    )
    LaunchedEffect(accountId) { vm.load(accountId, differenceCents) }
    val state by vm.state.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "核对 · " + state.accountName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Text(
                    text = state.anchorLabel + " · 已加载 " + state.totalLoaded + " 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )

                SectionLabel("核对范围")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf<Pair<String, Int?>>(
                        "锚点后" to null, "近 7 天" to 7,
                        "近 30 天" to 30, "近 90 天" to 90
                    ).forEach { (label, days) ->
                        TextButton(onClick = { vm.setRange(days) }) {
                            Text(
                                text = if (state.rangeDays == days) "● $label" else label,
                                color = if (state.rangeDays == days) LedgerColors.IncomeGreen
                                else LedgerColors.Neutral
                            )
                        }
                    }
                }

                SectionLabel("金额反查（元）")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Reconciliation.LookupMode.values().forEach { mode ->
                        TextButton(onClick = { vm.setMode(mode) }) {
                            val label = when (mode) {
                                Reconciliation.LookupMode.EXACT -> "等于"
                                Reconciliation.LookupMode.RANGE -> "区间"
                                Reconciliation.LookupMode.APPROX -> "近似 ±1"
                            }
                            Text(
                                text = if (state.lookupMode == mode) "● $label" else label,
                                color = if (state.lookupMode == mode) LedgerColors.IncomeGreen
                                else LedgerColors.Neutral
                            )
                        }
                    }
                }
                if (state.lookupMode == Reconciliation.LookupMode.RANGE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.rangeMinText,
                            onValueChange = { vm.setRangeBounds(it, state.rangeMaxText) },
                            label = { Text("最小") }, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.rangeMaxText,
                            onValueChange = { vm.setRangeBounds(state.rangeMinText, it) },
                            label = { Text("最大") }, modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = state.lookupText,
                        onValueChange = { vm.setLookupText(it) },
                        label = { Text("输入差额金额") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(onClick = { vm.clearLookup() }) { Text("清空金额条件") }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "只看疑似项", modifier = Modifier.weight(1f))
                    Switch(checked = state.onlySuspect, onCheckedChange = { vm.setOnlySuspect(it) })
                }

                state.noMatchHint?.let {
                    Text(
                        text = it,
                        color = LedgerColors.ExpenseRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(state.rows, key = { it.row.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = MonthRange.dateLabel(item.row.localDate) + " · " +
                                        (item.row.categoryName ?: "转账"),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = formatCents(item.row.amountCents) + " 元",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (item.labels.isNotEmpty()) {
                                Text(
                                    text = "⚠ " + item.labels.joinToString(" / "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LedgerColors.ExpenseRed
                                )
                            }
                            item.row.note.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LedgerColors.Neutral
                                )
                            }
                        }
                    }
                }
                if (state.rows.isEmpty() && state.noMatchHint == null) {
                    Text(
                        text = "该范围内没有流水",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 10.dp)
    )
}
