package com.zwz.offlineledger.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.core.time.MonthRange
import com.zwz.offlineledger.data.local.dao.DrillRow
import com.zwz.offlineledger.core.accountLabel
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.components.signedAmount
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 69 批（Q4B + Q6A）：分类下钻的**唯一**落点，路径沿用旧 `DrillDownDialog.kt`。
 *
 * 从居中 Dialog +「去明细」按钮改为 `ModalBottomSheet`，
 * 每行逐项对齐明细页 `RecordRowCard`：方向箭头圆、描述/收款方/分类主标题、
 * 「分类 · 收款方 · 备注」副行、带卡号账户名、红/绿金额、
 * 划线原价与「优惠 / 手续费」标签、日期。
 *
 * MUST NOT 跳明细 Tab、MUST NOT 向 DetailViewModel 注入状态、
 * MUST NOT 出现「查看该大类全部流水 ›」或「明细」按钮。
 * 数据来自扩字段后的 `DrillRow`（一次 LEFT JOIN + LIMIT 100），不逐行查库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDrillSheet(
    state: StatsUiState,
    vm: StatsViewModel,
    onDismiss: () -> Unit = { vm.closeDrill() }
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.drillCategoryName + " · 流水",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            Text(
                text = "最多显示 100 条，按日期倒序",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(state.drillRows, key = { it.id }) { r ->
                    DrillRecordRow(r)
                }
            }
            if (state.drillRows.isEmpty()) {
                Text(
                    text = "该范围内没有符合条件的流水",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

/** 与 `DetailScreen.RecordRowCard` 同构的明细行，供统计页内复用。 */
@Composable
private fun DrillRecordRow(r: DrillRow) {
    val type = try {
        RecordType.valueOf(r.type)
    } catch (e: IllegalArgumentException) {
        RecordType.TRANSFER
    }
    val account = accountLabel(r.fromAccountName, r.fromAccountLast4)
        ?: accountLabel(r.toAccountName, r.toAccountLast4) ?: "-"
    val category = r.categoryName ?: "转账"
    val party = r.counterparty.takeIf { it.isNotBlank() }
    val desc = r.description.takeIf { it.isNotBlank() }
    // 第 43 批契约：描述有值用描述做主标题，空着回退交易对方 / 分类。
    val primary = when {
        type == RecordType.TRANSFER -> "转账"
        desc != null -> desc
        party != null -> party
        else -> category
    }
    val secondary = listOfNotNull(
        category.takeIf { primary != category },
        party.takeIf { desc != null && it != primary },
        r.note.takeIf { it.isNotBlank() && it != primary }
    ).joinToString(" · ")
    val accent = when (type) {
        RecordType.EXPENSE -> LedgerColors.ExpenseRed
        RecordType.INCOME -> LedgerColors.IncomeGreen
        RecordType.TRANSFER -> MaterialTheme.colorScheme.primary
        RecordType.SETTLEMENT -> LedgerColors.IncomeGreen
    }
    val arrow = when (type) {
        RecordType.EXPENSE -> "←"
        RecordType.INCOME -> "→"
        RecordType.TRANSFER -> "⇄"
        RecordType.SETTLEMENT -> "↩"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = arrow,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = primary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = MonthRange.dateLabel(r.localDate) + " · " + account,
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val discount = r.discountCents ?: 0L
            val fee = r.feeCents ?: 0L
            if (discount > 0L || fee != 0L) {
                // 第 67 批：划线原价 + 附加金额标签，与明细页同款。
                Text(
                    text = formatCents(r.amountCents + discount + kotlin.math.abs(fee)),
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral,
                    textDecoration = TextDecoration.LineThrough
                )
            }
            Text(
                text = signedAmount(type, r.amountCents),
                style = MaterialTheme.typography.bodyMedium,
                color = accent
            )
            if (discount > 0L) {
                Text(
                    text = "优惠 " + formatCents(discount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (fee != 0L) {
                Text(
                    text = (if (fee < 0L) "转账优惠 " else "手续费 ") +
                        formatCents(kotlin.math.abs(fee)),
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 4.dp)
            .background(LedgerColors.Neutral.copy(alpha = 0.10f))
    )
}
