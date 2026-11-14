package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第三批：两级分类管理。新增 / 改名 / 排序 / 删除 / 恢复。
 * 删除有流水的分类自动降级为归档，保护历史流水归属。
 * 二级父级用「搜索 + 可滚动全量列表」，不再被 DropdownMenu 高度截断。
 */
@Composable
fun CategoryManagerDialog(
    categories: List<CategoryEntity>,
    vm: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var newRootName by remember { mutableStateOf("") }
    var newRootType by remember { mutableStateOf(RecordType.EXPENSE) }
    var newChildName by remember { mutableStateOf("") }
    var childParentId by remember { mutableStateOf<Long?>(null) }
    var parentQuery by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val roots = categories.filter { it.level == 0 && !it.isArchived }
    val children = categories.filter { it.level == 1 && !it.isArchived }
    val archived = categories.filter { it.isArchived }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类管理（最多两级）") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(roots, key = { it.id }) { root ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = root.name + "（" + typeLabel(root.type) + "）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.moveCategory(root.id, -1) }) { Text("↑") }
                                TextButton(onClick = { vm.moveCategory(root.id, 1) }) { Text("↓") }
                                TextButton(onClick = {
                                    renamingId = root.id; renameText = root.name
                                }) { Text("改名") }
                                TextButton(onClick = { deletingId = root.id }) {
                                    Text("删除", color = LedgerColors.ExpenseRed)
                                }
                            }
                            children.filter { it.parentId == root.id }.forEach { child ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = "└ " + child.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { vm.moveCategory(child.id, -1) }) { Text("↑") }
                                    TextButton(onClick = { vm.moveCategory(child.id, 1) }) { Text("↓") }
                                    TextButton(onClick = {
                                        renamingId = child.id; renameText = child.name
                                    }) { Text("改名") }
                                    TextButton(onClick = { deletingId = child.id }) {
                                        Text("删除", color = LedgerColors.ExpenseRed)
                                    }
                                }
                            }
                        }
                    }
                }

                if (renamingId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("新名称") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            vm.renameCategory(renamingId!!, renameText)
                            renamingId = null
                        }) { Text("保存") }
                    }
                }

                Text(
                    text = "新增一级分类",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newRootName,
                        onValueChange = { newRootName = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        newRootType = if (newRootType == RecordType.EXPENSE)
                            RecordType.INCOME else RecordType.EXPENSE
                    }) { Text(typeLabel(newRootType)) }
                    TextButton(onClick = {
                        if (newRootName.isNotBlank()) {
                            vm.createCategory(newRootName, newRootType, null)
                            newRootName = ""
                        }
                    }) { Text("新增") }
                }

                Text(
                    text = "新增二级分类（类型继承父级）",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                val selectedParent = roots.firstOrNull { it.id == childParentId }
                Text(
                    text = "父级：" + (selectedParent?.let { it.name + "（" + typeLabel(it.type) + "）" }
                        ?: "未选，点下面列表选择"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedParent == null) LedgerColors.ExpenseRed
                    else LedgerColors.IncomeGreen
                )
                OutlinedTextField(
                    value = parentQuery,
                    onValueChange = { parentQuery = it },
                    label = { Text("搜索一级分类（可留空看全部）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 全量一级分类，可滚动；不再被 DropdownMenu 的默认高度截断。
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp)) {
                    items(roots.filter {
                        it.name.contains(parentQuery, ignoreCase = true)
                    }, key = { it.id }) { r ->
                        TextButton(
                            onClick = { childParentId = r.id },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = (if (childParentId == r.id) "● " else "") +
                                    r.name + "（" + typeLabel(r.type) + "）",
                                color = if (childParentId == r.id) LedgerColors.IncomeGreen
                                else LedgerColors.Neutral
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newChildName,
                        onValueChange = { newChildName = it },
                        label = { Text("子类名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val parent = childParentId?.let { id -> roots.firstOrNull { it.id == id } }
                        if (parent != null && newChildName.isNotBlank()) {
                            vm.createCategory(newChildName, parent.type, parent.id)
                            newChildName = ""
                        }
                    }) { Text("新增") }
                }

                if (archived.isNotEmpty()) {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Text((if (showArchived) "收起" else "展开") +
                            "已归档分类（${archived.size}）")
                    }
                    if (showArchived) {
                        archived.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = c.name + "（" + typeLabel(c.type) + "）",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.restoreCategory(c.id) }) { Text("恢复") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )

    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("删除分类") },
            text = { Text("无流水的分类会直接删除；已有流水的分类自动转为归档，历史流水归属不变。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCategory(id); deletingId = null }) {
                    Text("确定", color = LedgerColors.ExpenseRed)
                }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } }
        )
    }
}

private fun typeLabel(type: RecordType): String =
    if (type == RecordType.EXPENSE) "支出" else "收入"
