package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.splitTagNames

/**
 * 第 75 批：流水标签 chips。输入是逗号分隔的快照字符串，
 * 明细行 / 详情页共用同一份渲染。
 * 项目 T12 禁用 FlowRow + ExperimentalLayoutApi（历史真机坑），
 * 这里用 Column + chunked(4) 的纯 Row，零实验性 API。
 */
@Composable
fun TagChips(raw: String?) {
    val names = splitTagNames(raw.orEmpty())
    if (names.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        names.chunked(4).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.forEach { n ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("#$n", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}
