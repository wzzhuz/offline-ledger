package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import com.zwz.offlineledger.ui.components.ListDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.TagNameRules
import com.zwz.offlineledger.data.local.entity.TagEntity
import com.zwz.offlineledger.ui.LedgerDi
import kotlinx.coroutines.launch

/** 第 75 批：独立标签管理页。默认归档而非物理删除，保护历史关联。 */
@Composable
fun TagManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { LedgerDi.entryPoint(context).tagDao() }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    val tags by produceState(initialValue = emptyList<TagEntity>(), dao, revision) {
        value = runCatching { dao.all() }.getOrDefault(emptyList())
    }
    var addText by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TagEntity?>(null) }
    var editText by remember { mutableStateOf("") }

    fun refresh() { revision += 1 }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(text = "标签管理", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "分类一笔一个、标签一笔多个；标签可把散在不同分类里的账打包回顾。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = addText,
                onValueChange = { addText = it; addError = null },
                placeholder = { Text("新建标签名") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = {
                val n = addText.trim()
                val bad = TagNameRules.validate(n)
                if (bad != null) { addError = bad; return@TextButton }
                scope.launch {
                    if (dao.idByName(n) != null) { addError = "标签已存在"; return@launch }
                    dao.insertTag(TagEntity(name = n, sortOrder = tags.size + 1))
                    addText = ""; refresh()
                }
            }) { Text("添加") }
        }
        addError?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
        ListDivider(Modifier.padding(vertical = 8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(tags, key = { it.id }) { t ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = t.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (t.isArchived) "已归档" else "启用中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { editing = t; editText = t.name }) { Text("改名") }
                    TextButton(onClick = {
                        scope.launch { dao.setArchived(t.id, !t.isArchived); refresh() }
                    }) { Text(if (t.isArchived) "启用" else "归档") }
                }
                ListDivider()
            }
        }
    }

    editing?.let { t ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("改名：${t.name}") },
            text = {
                OutlinedTextField(value = editText, onValueChange = { editText = it },
                    singleLine = true, label = { Text("新名称") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = editText.trim()
                    val bad = TagNameRules.validate(n)
                    if (bad != null) { addError = bad; editing = null; return@TextButton }
                    scope.launch {
                        if (dao.idByName(n) == null) dao.rename(t.id, n)
                        editing = null; refresh()
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }
        )
    }
}
