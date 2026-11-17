package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * 第 15 批：全站列表分割线的**单一来源**。
 * 1dp / Neutral 0.10；水平内缩由调用方提供（明细页 16dp；
 * 外层已带 12dp 的列表页补 4dp，视觉基准统一为距屏幕 16dp）。
 * MUST NOT 在各页各画一遍 Box，避免颜色 / 厚度 / 内缩漂移。
 */
@Composable
fun ListDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LedgerColors.Neutral.copy(alpha = 0.10f))
    )
}

fun amountColor(type: RecordType): Color = when (type) {
    RecordType.EXPENSE -> LedgerColors.ExpenseRed
    RecordType.INCOME -> LedgerColors.IncomeGreen
    RecordType.TRANSFER -> LedgerColors.Neutral
    RecordType.SETTLEMENT -> LedgerColors.IncomeGreen
}

/**
 * 第 13 批：隐藏金额是**可观察的全局显示状态**。
 * 放在 formatCents 单入口里，让「金额 Text / 图表 valueLabels / 图例 / 中心总额」
 * 全部同步遮蔽，且不需要把每个调用点改成 composable 函数
 * （values.map { } 这类非 @Composable lambda 里不能调 composable）。
 * 组合期间读取 mutableStateOf 会被 snapshot 记录，开关一变即重组。
 */
object AmountMask {
    var hidden by mutableStateOf(false)
}

fun formatCents(cents: Long): String =
    if (AmountMask.hidden) "****" else Cents(cents).toYuanString()

fun signedAmount(type: RecordType, cents: Long): String = when (type) {
    RecordType.EXPENSE -> "-" + formatCents(cents)
    RecordType.INCOME -> "+" + formatCents(cents)
    RecordType.TRANSFER -> formatCents(cents)
    RecordType.SETTLEMENT -> "+" + formatCents(cents)
}
