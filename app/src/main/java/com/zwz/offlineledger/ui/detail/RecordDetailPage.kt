package com.zwz.offlineledger.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.time.LedgerClock
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.core.accountLabel
import com.zwz.offlineledger.data.local.dao.RecordRow
import com.zwz.offlineledger.ui.components.TagChips
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.components.amountColor
import com.zwz.offlineledger.ui.components.signedAmount
import com.zwz.offlineledger.ui.theme.LedgerColors
import java.time.Instant
import com.zwz.offlineledger.ui.theme.LedgerType

/**
 * 第 45 批：点击记录直接进只读详情页。
 *
 * 锁图标 = 解锁进入编辑（复用既有全屏 RecordEditorDialog 表单，不重写）；
 * 删除 / 报销 / 退款一步到位，二次确认与事务路径仍由 DetailScreen 负责。
 * 不新增 NavHost 路由：actionRow 非空时明细列表整体让位，等价于二级页。
 */
@Composable
fun RecordDetailPage(
    row: RecordRow,
    onClose: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit,
    onSettle: (() -> Unit)?
) {
    val type = try {
        RecordType.valueOf(row.type)
    } catch (e: IllegalArgumentException) {
        RecordType.TRANSFER
    }
    val z = Instant.ofEpochMilli(row.timestamp).atZone(LedgerClock.zoneId)
    val timeText = "%04d-%02d-%02d %02d:%02d".format(
        z.year, z.monthValue, z.dayOfMonth, z.hour, z.minute
    )
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "返回")
                }
                Text(
                    text = "交易详情",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onEdit(row.id) }) {
                    Icon(Icons.Filled.Lock, contentDescription = "解锁进入编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = signedAmount(type, row.amountCents) + " 元",
                    style = LedgerType.AmountDisplay,
                    color = amountColor(type)
                )
                // 第 67 批：附加金额三行只读展示；原价由 amount + discount 推导，
                // MUST NOT 从数据库读一个不存在的原价列。
                val d = row.discountCents
                val f = row.feeCents
                if (d != null && d > 0L) {
                    DetailLine("原始金额", Cents(row.amountCents + d).toYuanString() + " 元")
                    DetailLine("优惠", "-" + Cents(d).toYuanString() + " 元")
                    DetailLine("实际金额", Cents(row.amountCents).toYuanString() + " 元")
                } else if (f != null && f > 0L) {
                    DetailLine("手续费", Cents(f).toYuanString() + " 元")
                    DetailLine("转出实际扣款", Cents(row.amountCents + f).toYuanString() + " 元")
                } else if (f != null && f < 0L) {
                    DetailLine("转账优惠", Cents(-f).toYuanString() + " 元")
                    DetailLine("转出实际扣款", Cents(row.amountCents + f).toYuanString() + " 元")
                }
                Spacer(Modifier.height(12.dp))
                DetailLine("类型", when (type) {
                    RecordType.EXPENSE -> "支出"
                    RecordType.INCOME -> "收入"
                    RecordType.TRANSFER -> "转账"
                    RecordType.SETTLEMENT -> "报销 / 退款"
                })
                DetailLine("时间", timeText)
                DetailLine("分类", row.categoryName?.takeIf { it.isNotBlank() } ?: "转账（无分类）")
                // 第 62 批：流水详情页与列表、筛选、统计用同一套账户显示名。
                DetailLine(
                    "支出 / 转出账户",
                    accountLabel(row.fromAccountName, row.fromAccountLast4) ?: "-"
                )
                DetailLine(
                    "收入 / 转入账户",
                    accountLabel(row.toAccountName, row.toAccountLast4) ?: "-"
                )
                DetailLine("交易对方", row.counterparty.takeIf { it.isNotBlank() } ?: "—")
                DetailLine("描述", row.description.takeIf { it.isNotBlank() } ?: "—")
                DetailLine("备注", row.note.takeIf { it.isNotBlank() } ?: "—")
                if (row.tagNames.orEmpty().isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    TagChips(row.tagNames, limit = Int.MAX_VALUE)
                }
                DetailLine("不计入收支", if (row.excludeFromStats) "是" else "否")
                if (row.importSource.isNotBlank()) {
                    DetailLine(
                        "来源",
                        row.importSource + if (row.sourceId.isNotBlank()) " · " + row.sourceId else ""
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (onSettle != null) {
                    TextButton(onClick = { onSettle?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                        Text("报销 / 退款")
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("返回列表")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LedgerColors.Neutral
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
