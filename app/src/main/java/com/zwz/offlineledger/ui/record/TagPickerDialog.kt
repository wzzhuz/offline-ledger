package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.TagNameRules
import com.zwz.offlineledger.core.addPickedTag
import com.zwz.offlineledger.core.visibleTagNames
import com.zwz.offlineledger.data.local.entity.TagEntity
import com.zwz.offlineledger.ui.LedgerDi

/**
 * 第 75 批：记一笔的多选标签弹层。
 * 刻意不复用 CategorySelectDialog —— 那个按收支类型过滤并按分类 id 映射，
 * 标签是全账本级、按名匹配、一笔可多个，契约不同。
 */
@Composable
fun TagPickerDialog(
    selected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tagDao = remember { LedgerDi.entryPoint(context).tagDao() }
    val tags by produceState(initialValue = emptyList<TagEntity>(), tagDao) {
        value = runCatching { tagDao.active() }.getOrDefault(emptyList())
    }
    var picked by remember { mutableStateOf(selected.distinct().take(TagNameRules.MAX_PER_RECORD)) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择标签") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "分类一笔一个，标签一笔可多个；最多 ${TagNameRules.MAX_PER_RECORD} 个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // T12：不用 FlowRow / 实验性 API，改 Column + chunked(3) 纯 Row。
                // 第 92 批：chip 名单 MUST 合并「数据库已有名 + picked」，
                // 否则刚新建的标签在弹层里完全不可见、也无从反选。
                Text(
                    text = "已选 ${picked.size} / ${TagNameRules.MAX_PER_RECORD}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    visibleTagNames(tags.map { it.name }, picked).chunked(3).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.forEach { name ->
                                FilterChip(
                                    selected = name in picked,
                                    onClick = {
                                        picked = if (name in picked) {
                                            picked - name
                                        } else if (picked.size < TagNameRules.MAX_PER_RECORD) {
                                            picked + name
                                        } else picked
                                    },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; error = null },
                        placeholder = { Text("新建标签") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = {
                        // 第 92 批：走唯一纯函数出口，新建项立刻出现在上方 chip 区并可反选。
                        val (next, bad) = addPickedTag(
                            picked, tags.map { it.name }, newName
                        )
                        if (bad != null) {
                            error = bad
                        } else {
                            picked = next
                            newName = ""
                            error = null
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "新建标签")
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(picked) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
