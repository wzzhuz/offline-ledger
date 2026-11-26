package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwz.offlineledger.core.splitTagNames

/**
 * 第 76 批：账单标签的**只读**轻量行内标记。
 * 第 75 批用 AssistChip（32dp 高 + 内边距 + 可换行），在明细行与主标题、金额
 * 同一视觉重量，反馈「太大、太抢戏」。这里改成 10sp 字 + 低对比圆角底，
 * 单行不换行，默认最多 2 个 + 「+N」；详情页传 limit = Int.MAX_VALUE 看全量。
 * 项目 T12 禁用 FlowRow / 实验性 API，纯 Row 也顺带满足。
 */
@Composable
fun TagChips(raw: String?, limit: Int = 2) {
    val names = splitTagNames(raw.orEmpty())
    if (names.isEmpty()) return
    val shown = names.take(limit.coerceAtLeast(0))
    val rest = names.size - shown.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        shown.forEach { n -> TagBadge("#$n") }
        if (rest > 0) TagBadge("+$rest")
    }
}

@Composable
private fun TagBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            maxLines = 1
        )
    }
}
