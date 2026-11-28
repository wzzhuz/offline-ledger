package com.zwz.offlineledger.ui.virtual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.core.prefs.VirtualBookStore
import com.zwz.offlineledger.core.prefs.VirtualBookTemplate
import com.zwz.offlineledger.data.local.dao.VirtualBookPartyRow
import com.zwz.offlineledger.data.local.dao.VirtualBookRecordRow
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.LedgerDi
import com.zwz.offlineledger.ui.components.formatCents
import kotlinx.coroutines.launch

/** 第 93 批：录入区与列表 MUST 分开；往来汇总与流水 MUST 分段，不再混在一条列表里。 */
private enum class BookSegment { PARTIES, RECORDS }

/**
 * 第 93 批重做：虚拟台账页。
 *
 * 不是账本、不是第二个数据库：只是按「台账:<名称>」标签聚合 records 的视图。
 * 收礼不记成收入虚高，靠「不计入收支」摘出主账本报表；流水仍在主账本明细可见、
 * 可编辑、可删除，MUST NOT 再从明细 SQL 里排除（第 91 批的过度隔离）。
 *
 * 页结构：标题行 → 总览卡 → 分段 chip → 内容（往来汇总 / 按日分组流水）
 * → 底部固定录入区。录入区不随列表滚动，避免「新增和列表混在一起」。
 */
@Composable
fun VirtualBookScreen(
    bookId: String,
    refreshTick: Int,
    onBack: () -> Unit,
    /** 第 94 批：回传该笔应预填的分类 id，由模板决定，避免每笔手选分类。 */
    onRecord: (RecordType, Long?) -> Unit,
    onEditRecord: (Long) -> Unit
) {
    val context = LocalContext.current
    val store = remember { VirtualBookStore(context) }
    val dao = remember { LedgerDi.entryPoint(context).virtualBookDao() }
    val repository = remember { LedgerDi.entryPoint(context).ledgerRepository() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currency = remember { LedgerPrefs(context).defaultCurrency }
    val book = store.byId(bookId)
    // 第 94 批：所有文案 / 按钮 / 预填分类都由场景模板决定，MUST NOT 写死「记收礼」。
    val spec = book?.spec ?: VirtualBookTemplate.GIFT

    var parties by remember { mutableStateOf(emptyList<VirtualBookPartyRow>()) }
    var records by remember { mutableStateOf(emptyList<VirtualBookRecordRow>()) }
    var reload by remember { mutableStateOf(0) }
    var segment by remember { mutableStateOf(BookSegment.PARTIES) }
    var pendingDelete by remember { mutableStateOf<VirtualBookRecordRow?>(null) }

    LaunchedEffect(bookId, refreshTick, reload) {
        val b = store.byId(bookId) ?: return@LaunchedEffect
        parties = runCatching { dao.partyTotals(b.tagName, currency) }.getOrDefault(emptyList())
        records = runCatching { dao.bookRecords(b.tagName, currency, 200) }.getOrDefault(emptyList())
    }

    val totalReceived = parties.sumOf { it.receivedCents }
    val totalReturned = parties.sumOf { it.returnedCents }
    val totalCount = parties.sumOf { it.recordCount }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔记录？") },
            text = { Text("将移入回收站，账户余额自动重算。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = pendingDelete!!.recordId
                    pendingDelete = null
                    // 第 93 批：写事务 MUST 用 LifecycleOwner 级 scope，
                    // MUST NOT 放进 LaunchedEffect / 弹层局部 rememberCoroutineScope
                    // （第 89 批「The coroutine scope left the composition」坑）。
                    lifecycleOwner.lifecycleScope.launch {
                        runCatching { repository.deleteRecord(id) }
                        reload += 1
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 二级页 topBar 不渲染，标题行自己避让状态栏（第 77 批 edge-to-edge 口径）。
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回主账本")
            }
            Text(
                text = book?.name ?: "虚拟账本",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        if (book == null) {
            Text(
                "该虚拟账本已不存在，请返回设置页重建。",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            // 页内总览：页内汇总回答「总共收/还/还欠多少」，不给统计模块加 scope。
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("本台账总览", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("${spec.incomeWord} ${formatCents(totalReceived)}", style = MaterialTheme.typography.bodySmall)
                        Text("${spec.expenseWord} ${formatCents(totalReturned)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            "${spec.netWord} ${formatCents(totalReceived - totalReturned)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "$totalCount 笔",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        spec.hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                FilterChip(
                    selected = segment == BookSegment.PARTIES,
                    onClick = { segment = BookSegment.PARTIES },
                    label = { Text(spec.segmentLabel) }
                )
                FilterChip(
                    selected = segment == BookSegment.RECORDS,
                    onClick = { segment = BookSegment.RECORDS },
                    label = { Text("流水") }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                when (segment) {
                    BookSegment.PARTIES -> {
                        if (parties.isEmpty()) {
                            item { EmptyHint("还没有记录。点下方按钮记一笔，记得填交易对方。") }
                        }
                        parties.forEach { p ->
                            item { PartyCard(p, spec) }
                        }
                    }
                    BookSegment.RECORDS -> {
                        if (records.isEmpty()) {
                            item { EmptyHint("还没有流水。点下方按钮记一笔。") }
                        }
                        records.groupBy { it.localDate }
                            .entries.sortedByDescending { it.key }
                            .forEach { (date, dayRecords) ->
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(top = 12.dp, bottom = 4.dp)
                                    ) {
                                        Text(
                                            dateLabel(date),
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val dayIn = dayRecords.filter { it.type == "INCOME" }
                                            .sumOf { it.amountCents }
                                        val dayOut = dayRecords.filter { it.type == "EXPENSE" }
                                            .sumOf { it.amountCents }
                                        Text(
                                            "${spec.incomeWord} ${formatCents(dayIn)} · ${spec.expenseWord} ${formatCents(dayOut)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                dayRecords.forEach { r ->
                                    item {
                                        RecordLine(
                                            r = r,
                                            onClick = { onEditRecord(r.recordId) },
                                            onDelete = { pendingDelete = r }
                                        )
                                    }
                                }
                            }
                    }
                }
            }

            // 第 94 批：底部固定录入区按场景模板渲染，礼金专用文案不再写死。
            // 专项支出只给一个按钮；分类 id 由模板（或视图默认分类）一次性给出。
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                spec.incomeAction?.let { label ->
                    Button(
                        onClick = {
                            onRecord(
                                RecordType.INCOME,
                                spec.categoryFor(true, book.defaultCategoryId)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                }
                spec.expenseAction?.let { label ->
                    Button(
                        onClick = {
                            onRecord(
                                RecordType.EXPENSE,
                                spec.categoryFor(false, book.defaultCategoryId)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun PartyCard(p: VirtualBookPartyRow, spec: VirtualBookTemplate) {
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
                    "${spec.netWord} ${formatCents(p.pendingCents)}",
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

/** 第 93 批：流水行可点进全屏编辑态，行尾给删除（二次确认 + 既有 deleteRecord 事务）。 */
@Composable
private fun RecordLine(
    r: VirtualBookRecordRow,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = r.counterparty.ifBlank { "（未填对方）" },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = (if (r.note.isBlank()) "" else "${r.note} · ") +
                    (if (r.excludeFromStats) "不计入收支" else "计入收支"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = (if (r.type == "INCOME") "+" else "-") + formatCents(r.amountCents),
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除这笔",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun dateLabel(d: Int): String {
    val s = d.toString().padStart(8, '0')
    return "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
}
