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
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    tags.chunked(3).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.forEach { t ->
                                FilterChip(
                                    selected = t.name in picked,
                                    onClick = {
                                        picked = if (t.name in picked) {
                                            picked - t.name
                                        } else if (picked.size < TagNameRules.MAX_PER_RECORD) {
                                            picked + t.name
                                        } else picked
                                    },
                                    label = { Text(t.name) }
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
                        val n = newName.trim()
                        val bad = TagNameRules.validate(n)
                        when {
                            bad != null -> error = bad
                            n in tags.map { it.name } -> error = "标签已存在"
                            picked.size >= TagNameRules.MAX_PER_RECORD ->
                                error = "最多 ${TagNameRules.MAX_PER_RECORD} 个标签"
                            else -> {
                                picked = picked + n
                                newName = ""
                                error = null
                            }
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
