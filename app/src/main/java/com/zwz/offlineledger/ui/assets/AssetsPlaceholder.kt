package com.zwz.offlineledger.ui.assets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 阶段 3 实现净资产看板、账户详情、余额调整记录与对账流程。 */
@Composable
fun AssetsPlaceholder() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "资产", style = MaterialTheme.typography.headlineMedium)
        Text(text = "资产看板 · 阶段 3", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "净资产 = SUM(includeInTotal 账户余额)，校准使用独立的余额调整记录。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
