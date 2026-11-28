package com.zwz.offlineledger.ui.virtual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.core.prefs.VirtualBookStore
import com.zwz.offlineledger.data.local.dao.VirtualBookPartyRow
import com.zwz.offlineledger.data.local.dao.VirtualBookRecordRow
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.LedgerDi
import com.zwz.offlineledger.ui.components.formatCents

/**
 * 第 91 批：虚拟台账页。
 *
 * 不是账本、不是第二个数据库：只是按「台账:<名称>」标签聚合 records 的只读视图 +
 * 两个带预填的录入入口。收礼不记成收入虚高，靠「不计入收支」摘出主账本报表。
 */
@Composable
fun VirtualBookScreen(
    bookId: String,
    refreshTick: Int,
    onBack: () -> Unit,
    onRecord: (RecordType) -> Unit
) {
    val context = LocalContext.current
    val store = remember { VirtualBookStore(context) }
    val dao = remember { LedgerDi.entryPoint(context).virtualBookDao() }
    val currency = remember { LedgerPrefs(context).defaultCurrency }
    val book = store.byId(bookId)

    var parties by remember { mutableStateOf(emptyList<VirtualBookPartyRow>()) }
    var records by remember { mutableStateOf(emptyList<VirtualBookRecordRow>()) }

    LaunchedEffect(bookId, refreshTick) {
        val b = store.byId(bookId) ?: return@LaunchedEffect
        parties = runCatching { dao.partyTotals(b.tagName, currency) }.getOrDefault(emptyList())
        records = runCatching { dao.bookRecords(b.tagName, currency, 200) }.getOrDefault(emptyList())
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回主账本")
            }
            Text(
                text = book?.name ?: "虚拟账本",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        Text(
            text = "收礼 / 还礼按人汇总；这些流水不计入主账本收支，钱仍如实进入账户余额。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (book == null) {
            Text("该虚拟账本已不存在，请返回设置页重建。", color = MaterialTheme.colorScheme.error)
        } else {

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Button(onClick = { onRecord(RecordType.INCOME) }, modifier = Modifier.weight(1f)) {
                Text("记收礼")
            }
            Button(onClick = { onRecord(RecordType.EXPENSE) }, modifier = Modifier.weight(1f)) {
                Text("记还礼")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "按人往来",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                if (parties.isEmpty()) {
                    Text(
                        "还没有记录。点上方按钮记一笔，记得填交易对方。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            parties.forEach { p ->
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(p.party, style = MaterialTheme.typography.titleSmall)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text("收 ${formatCents(p.receivedCents)}", style = MaterialTheme.typography.bodySmall)
                                Text("还 ${formatCents(p.returnedCents)}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "待还 ${formatCents(p.pendingCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "${p.recordCount} 笔",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "本台账流水",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            records.forEach { r ->
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        // 流水行
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = r.counterparty.ifBlank { "（未填对方）" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = (if (r.type == "INCOME") "+" else "-") + formatCents(r.amountCents),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = dateLabel(r.localDate) +
                                (if (r.note.isBlank()) "" else " · ${r.note}") +
                                (if (r.excludeFromStats) " · 不计入收支" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        }
    }
}

private fun dateLabel(d: Int): String {
    val s = d.toString().padStart(8, '0')
    return "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
}
