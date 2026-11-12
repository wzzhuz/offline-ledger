package com.zwz.offlineledger.ui.trash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.theme.LedgerColors

@Composable
fun TrashScreen(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val vm: TrashViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            TrashViewModel(ep.trashDao(), ep.ledgerRepository())
        }
    )
    val items by vm.items.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "回收站（${items.size}）",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Text(
                    text = "恢复会重新走插入事务并联动余额；彻底删除只移除回收站记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )
                LazyColumn {
                    items(items, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = MonthRange.dateLabel(item.localDate) +
                                            " · " + item.type.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = formatCents(item.amountCents) + " 元 · " +
                                            item.note.takeIf { it.isNotBlank() }.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall
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
                        }
                    }
                }
            }
        }
    }
}
