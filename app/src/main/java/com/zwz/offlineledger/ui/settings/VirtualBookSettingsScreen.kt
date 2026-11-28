package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.prefs.VirtualBook
import com.zwz.offlineledger.core.prefs.VirtualBookStore

/**
 * 第 91 批：虚拟账本管理。定义只存 SharedPreferences，不进 Room / 备份 DTO。
 * 新建同名视图即可把带「台账:」标签的历史流水重新聚合出来，不需要迁移数据。
 */
@Composable
fun VirtualBookSettingsScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember { VirtualBookStore(context) }
    var tick by remember { mutableStateOf(0) }
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<VirtualBook?>(null) }
    var renameText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    // tick 作重组信号：改动后重读 SharedPreferences 的最新定义。
    val books = remember(tick) { store.all() }
    var exclude by remember { mutableStateOf(store.excludeByDefault) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text("虚拟账本", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = "虚拟账本只是按标签聚合的视图：流水仍在主账本，切换器只切浏览范围，" +
                "不会改变记一笔与自动记账的落库位置。定义存本机偏好，不随加密备份恢复。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            Switch(
                checked = exclude,
                onCheckedChange = {
                    exclude = it
                    store.excludeByDefault = it
                }
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text("台账记录不计入收支", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (exclude) "主账本月度收入/支出零污染（推荐）" else "关闭后收礼会让月度收入虚高",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it; errorText = null },
                label = { Text("新建，如「结婚礼金」") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val b = store.add(newName)
                    if (b == null) errorText = "名称为空或已存在" else {
                        newName = ""
                        tick += 1
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("新建") }
        }
        errorText?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }

        Text(
            "已建视图",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
        )
        if (books.isEmpty()) {
            Text(
                "还没有虚拟账本。新建后从左上角切换器进入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        books.forEach { b ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = b.name + if (b.archived) "（已归档）" else "",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onOpenBook(b.id) }) { Text("查看") }
                    }
                    Text(
                        "标签 ${b.tagName} · 主账本明细默认不显示",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = { renameTarget = b; renameText = b.name }) { Text("重命名") }
                        TextButton(onClick = { store.setArchived(b.id, !b.archived); tick += 1 }) {
                            Text(if (b.archived) "恢复" else "归档")
                        }
                        TextButton(onClick = { store.move(b.id, -1); tick += 1 }) { Text("上移") }
                        TextButton(onClick = { store.move(b.id, 1); tick += 1 }) { Text("下移") }
                    }
                }
            }
        }
        Text(
            "提示：归档只从切换器隐藏，历史流水与标签不受影响；" +
                "CSV 导出不含「不计入」列，台账数据请以加密备份为准。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名虚拟账本") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("名称") }
                    )
                    Text(
                        "改名不改动绑定标签，历史流水仍归入此视图。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.trim().isNotEmpty()) {
                        store.rename(target.id, renameText)
                        tick += 1
                    }
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }
}
