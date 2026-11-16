package com.zwz.offlineledger.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 4 批升为独立页；第 5 批补齐时间、对方、分类、账户与备注。
 * 第 12 批：右侧两个竖排 TextButton（96dp）把行高硬撑到 ~120dp，
 * 改为「主行 + 副行」两行文字 + 一个「⋯」菜单 + padding 10→6dp，
 * 信息一条不删，一屏可见条数从约 5 条提升到 10 条以上。
 */
@Composable
fun TrashScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val vm: TrashViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            TrashViewModel(
                ep.trashDao(), ep.ledgerRepository(), ep.categoryDao(), ep.accountDao()
            )
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
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items, key = { it.originalId }) { item ->
                var menu by remember { mutableStateOf(false) }
                // 主行：时间 · 类型 · 金额 · 对方（有才带）
                val main = item.dateTime + " · " + item.typeLabel + " · " + item.amountText +
                    (if (item.counterparty.isNotBlank()) " · " + item.counterparty else "")
                // 副行：分类 · 账户 · 备注（有才带），不再拆成 3~4 行
                val sub = item.categoryText + " · " + item.accountText +
                    (if (item.note.isNotBlank()) " · " + item.note else "")
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = main,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("恢复", color = LedgerColors.IncomeGreen) },
                            onClick = {
                                menu = false; vm.restore(item.originalId); onChanged()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("彻底删除", color = LedgerColors.ExpenseRed) },
                            onClick = {
                                menu = false; vm.purge(item.originalId); onChanged()
                            }
                        )
                    }
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
