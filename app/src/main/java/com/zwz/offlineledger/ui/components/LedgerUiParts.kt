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
 * 分类图标用 colorHex 画圆 + 中文首字。
 * 刻意不把 iconName 反射映射到 ImageVector：预置数据有 30 个图标名，
 * 任一在当前 Compose BOM 的图标集里不存在就会让整份映射表编译失败。
 */
@Composable
fun CategoryIcon(name: String?, colorHex: String?, size: Int = 40) {
    val bg = parseColor(colorHex)
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name?.take(1) ?: "?",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun parseColor(hex: String?): Color = try {
    if (hex == null) LedgerColors.Neutral
    else Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    LedgerColors.Neutral
}

fun amountColor(type: RecordType): Color = when (type) {
    RecordType.EXPENSE -> LedgerColors.ExpenseRed
    RecordType.INCOME -> LedgerColors.IncomeGreen
    RecordType.TRANSFER -> LedgerColors.Neutral
    // 报销 / 退款是收回款，按正向色显示
    RecordType.SETTLEMENT -> LedgerColors.IncomeGreen
}

/** 金额展示：整数分 -> 元的两位小数。 */
fun formatCents(cents: Long): String = Cents(cents).toYuanString()

fun signedAmount(type: RecordType, cents: Long): String = when (type) {
    RecordType.EXPENSE -> "-" + formatCents(cents)
    RecordType.INCOME -> "+" + formatCents(cents)
    RecordType.TRANSFER -> formatCents(cents)
    RecordType.SETTLEMENT -> "+" + formatCents(cents)
}
