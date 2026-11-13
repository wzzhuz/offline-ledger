package com.zwz.offlineledger.ui.reconcile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors

@Composable
fun ReconcileDialog(
    accountId: Long,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val vm: ReconcileViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            ReconcileViewModel(
                ep.accountDao(), ep.recordDao(), ep.ledgerRepository(), LedgerPrefs(context)
            )
        }
    )
    LaunchedEffect(accountId) { vm.load(accountId) }
    val state by vm.state.collectAsState()

    var showReview by remember { mutableStateOf(false) }
    var confirmForce by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) {
        if (state.done) {
            onDone()
            onDismiss()
        }
    }

    if (showReview) {
        ReviewDialog(
            accountId = accountId,
            differenceCents = state.deltaCents ?: 0L,
            onDismiss = { showReview = false }
        )
    }

    if (confirmForce) {
        AlertDialog(
            onDismissRequest = { confirmForce = false },
            title = { Text("确认直接校准？") },
            text = {
                Text(
                    "差额 " + formatCents(state.deltaCents ?: 0L) +
                        " 元超过尾差阈值。直接校准会生成一条不进报表的余额调整记录，" +
                        "但不会帮你找到漏记的那笔交易。备注：" + state.memo
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmForce = false
                    vm.reconcile("MANUAL_RECONCILE")
                }) { Text("确认校准", color = LedgerColors.ExpenseRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForce = false }) { Text("再查查") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("对账 · " + state.accountName) },
        text = {
            Column {
                Text(
                    text = "账面余额 " + formatCents(state.bookCents) + " 元",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = state.actualInput,
                    onValueChange = { vm.setActual(it) },
                    label = { Text("实际余额（元）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = { vm.setMemo(it) },
                    label = { Text("备注（大额校准必填）") },
                    modifier = Modifier.fillMaxWidth()
                )
                state.deltaCents?.let { d ->
                    Text(
                        text = "差额 " + (if (d >= 0) "+" else "") + formatCents(d) + " 元",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (d < 0) LedgerColors.ExpenseRed else LedgerColors.IncomeGreen
                    )
                    if (state.isTail) {
                        Text(
                            text = "小额尾差：可一键忽略并校准，月报与年报都不会被污染。",
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.Neutral
                        )
                    } else {
                        Text(
                            text = "超过 1.00 元：建议先核对锚点后的 " +
                                state.anchorAfterCount + " 条流水。",
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.Neutral
                        )
                    }
                }
                state.errorText?.let {
                    Text(text = it, color = LedgerColors.ExpenseRed)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        state.deltaCents == 0L -> {
                            Button(onClick = { vm.reconcile("TAIL_DIFFERENCE") }) {
                                Text("账实相符，标记已对账")
                            }
                        }
                        state.isTail -> {
                            Button(onClick = { vm.reconcile("TAIL_DIFFERENCE") }) {
                                Text("忽略尾差并校准")
                            }
                        }
                        else -> {
                            Button(onClick = { showReview = true }) { Text("先核对") }
                            TextButton(
                                onClick = { confirmForce = true },
                                enabled = state.memo.isNotBlank()
                            ) { Text("仍要校准") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
