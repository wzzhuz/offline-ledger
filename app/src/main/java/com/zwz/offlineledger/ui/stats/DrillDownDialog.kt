package com.zwz.offlineledger.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.theme.LedgerColors

/** 一级 → 二级构成 → 分类明细；明细走带 LIMIT 的 SQL，不全量加载。 */
@Composable
fun DrillDownDialog(
    state: StatsUiState,
    vm: StatsViewModel,
    onOpenCategoryInDetail: (Long) -> Unit = {},
    onOpenRootInDetail: (Long) -> Unit = {}
) {
    Dialog(
        onDismissRequest = { vm.closeDrill() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.drillCategoryId != null)
                            state.drillCategoryName + " · 明细"
                        else state.drillRootName + " · 二级构成",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.drillCategoryId != null) {
                        TextButton(onClick = { vm.backToChildren() }) { Text("返回") }
                    }
                    TextButton(onClick = { vm.closeDrill() }) { Text("关闭") }
                }

                if (state.drillCategoryId == null) {
                    val total = state.drillChildren.sumOf { it.cents }
                    Text(
                        text = "合计 " + formatCents(total) + " 元（应与一级扇区相等）",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral
                    )
                    // 第 28 批：弹窗不能再「只能跳二级」，一级本身也要能下钻。
                    if (state.drillRootId != null) {
                        TextButton(onClick = {
                            val rootId = state.drillRootId
                            vm.closeDrill()
                            rootId?.let(onOpenRootInDetail)
                        }) {
                            Text(
                                "查看该大类全部流水 ›",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(state.drillChildren, key = { it.id ?: 0L }) { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = c.name, modifier = Modifier.weight(1f))
                                Text(text = formatCents(c.cents) + " 元")
                                TextButton(onClick = {
                                    c.id?.let {
                                        vm.closeDrill()
                                        onOpenCategoryInDetail(it)
                                    }
                                }) {
                                    Text("明细")
                                }
                            }
                        }
                    }
                    if (state.drillChildren.isEmpty()) {
                        Text(
                            text = "该大类下没有二级分类流水（可能都是一级直记）",
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.Neutral
                        )
                    }
                } else {
                    Text(
                        text = "最多显示 100 条，按日期倒序",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral
                    )
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(state.drillRows, key = { it.id }) { r ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = MonthRange.dateLabel(r.localDate) +
                                            " · " + (r.categoryName ?: "转账"),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    r.note.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = LedgerColors.Neutral
                                        )
                                    }
                                }
                                Text(
                                    text = formatCents(r.amountCents),
                                    color = if (r.type == "EXPENSE") LedgerColors.ExpenseRed
                                    else LedgerColors.IncomeGreen
                                )
                            }
                        }
                    }
                    if (state.drillRows.isEmpty()) {
                        Text(
                            text = "该范围内没有符合条件的流水",
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.Neutral
                        )
                    }
                }
            }
        }
    }
}
