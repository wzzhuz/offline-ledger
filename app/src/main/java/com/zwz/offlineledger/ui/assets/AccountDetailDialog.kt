package com.zwz.offlineledger.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors

@Composable
fun AccountDetailDialog(
    accountId: Long,
    accountName: String,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val vm: AccountDetailViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            AccountDetailViewModel(
                ep.accountDao(), ep.recordDao(),
                ep.balanceAdjustmentDao(), ep.ledgerRepository()
            )
        }
    )
    LaunchedEffect(accountId) { vm.load(accountId) }
    val state by vm.state.collectAsState()

    var showAdjustments by remember { mutableStateOf(false) }

    // 第三批：标题直接用资产页列表传入的实时名称，去掉详情里重复的名称输入框，
    // 避免「详情名称栏」与列表改名后不同步。改账户统一走资产页的编辑弹窗。
    val acc = state.account

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = accountName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }

                if (acc == null) {
                    Text(text = "账户不存在或已归档")
                    return@Column
                }

                Text(
                    text = "当前余额 " + formatCents(acc.balanceCents) + " 元",
                    style = MaterialTheme.typography.titleMedium
                )
                acc.lastReconciledAt?.let {
                    Text(
                        text = "上次对账：" + MonthRange.dateLabel(
                            com.zwz.offlineledger.core.time.LedgerClock
                                .fromEpochMilli(it).localDate
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral
                    )
                }

                Text(
                    text = "改名 / 类型 / 期初余额 / 币种请回到资产页点「编辑」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        vm.archive(accountId)
                        onChanged()
                        onDismiss()
                    }) { Text("归档账户", color = LedgerColors.ExpenseRed) }
                }
                Text(
                    text = "关联 ${state.linkedRecordCount} 笔流水；归档只隐藏账户，历史明细仍可查。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )

                TextButton(onClick = { showAdjustments = !showAdjustments }) {
                    Text(
                        (if (showAdjustments) "收起" else "展开") +
                            "余额调整记录（${state.adjustments.size}）"
                    )
                }
                if (showAdjustments) {
                    state.adjustments.forEach { a ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = (if (a.deltaCents >= 0) "+" else "") +
                                        formatCents(a.deltaCents) + " 元 · " + a.source,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = listOfNotNull(
                                        a.memo.takeIf { it.isNotBlank() },
                                        if (a.isRevoked) "已撤销" else null
                                    ).joinToString(" · ").ifEmpty { "无备注" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LedgerColors.Neutral
                                )
                            }
                            if (!a.isRevoked) {
                                TextButton(onClick = { vm.revoke(a.id, accountId) }) {
                                    Text("撤销", color = LedgerColors.ExpenseRed)
                                }
                            }
                        }
                    }
                    if (state.adjustments.isEmpty()) {
                        Text(text = "暂无校准记录", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(
                    text = "账户专属流水（最多 200 条）",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(state.records, key = { it.id }) { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIcon(r.categoryName, r.categoryColorHex, size = 32)
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(
                                    text = r.categoryName ?: "转账",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = MonthRange.dateLabel(r.localDate) +
                                        (r.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LedgerColors.Neutral
                                )
                            }
                            Text(
                                text = formatCents(r.amountCents),
                                color = if (r.type == "EXPENSE") LedgerColors.ExpenseRed
                                else LedgerColors.IncomeGreen
                            )
                        }
                    }
                }
                if (state.records.isEmpty()) {
                    Text(text = "暂无流水", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
