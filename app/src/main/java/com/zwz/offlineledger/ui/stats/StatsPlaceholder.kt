package com.zwz.offlineledger.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 阶段 4 实现一级 / 二级环形图、下钻、月度趋势与年度双线。 */
@Composable
fun StatsPlaceholder() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "统计", style = MaterialTheme.typography.headlineMedium)
        Text(text = "统计报表 · 阶段 4", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "按 rootCategoryId（一级）与 categoryId（二级）各一条 GROUP BY，两者合计必须相等。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
