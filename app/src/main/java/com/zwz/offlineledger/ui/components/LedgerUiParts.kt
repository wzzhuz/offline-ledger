package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 4 批：分类标识统一为「中性单色圆 + 中文首字」。
 * colorHex 字段仍保留在 Entity / 备份 / 环形图里（扇区需要颜色区分），
 * 但明细与记一笔的分类标识不再按分类上色：6~7 种颜色跳，列表一眼看过去不整齐。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun CategoryIcon(name: String?, colorHex: String?, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(LedgerColors.Neutral.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name?.take(1) ?: "?",
            color = LedgerColors.Neutral,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

fun amountColor(type: RecordType): Color = when (type) {
    RecordType.EXPENSE -> LedgerColors.ExpenseRed
    RecordType.INCOME -> LedgerColors.IncomeGreen
    RecordType.TRANSFER -> LedgerColors.Neutral
    RecordType.SETTLEMENT -> LedgerColors.IncomeGreen
}

fun formatCents(cents: Long): String = Cents(cents).toYuanString()

fun signedAmount(type: RecordType, cents: Long): String = when (type) {
    RecordType.EXPENSE -> "-" + formatCents(cents)
    RecordType.INCOME -> "+" + formatCents(cents)
    RecordType.TRANSFER -> formatCents(cents)
    RecordType.SETTLEMENT -> "+" + formatCents(cents)
}
