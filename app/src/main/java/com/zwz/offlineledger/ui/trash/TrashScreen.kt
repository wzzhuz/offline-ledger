package com.zwz.offlineledger.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 4 批：回收站由小弹窗升为独立页，列表铺满全屏、可回滚事务不变。
 */
@Composable
fun TrashScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val vm: TrashViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            TrashViewModel(ep.trashDao(), ep.ledgerRepository())
        }
    )
    val items by vm.items.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "回收站（${items.size}）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "恢复会重新走插入事务并联动余额；彻底删除只移除回收站记录。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = MonthRange.dateLabel(item.localDate) + " · " + item.type.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatCents(item.amountCents) + " 元 · " +
                                item.note.takeIf { it.isNotBlank() }.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral
                        )
                    }
                    TextButton(onClick = {
                        vm.restore(item.originalId)
                        onChanged()
                    }) { Text("恢复", color = LedgerColors.IncomeGreen) }
                    TextButton(onClick = {
                        vm.purge(item.originalId)
                        onChanged()
                    }) { Text("彻底删除", color = LedgerColors.ExpenseRed) }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LedgerColors.Neutral.copy(alpha = 0.10f))
                )
            }
        }
    }
}
