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

/** 两级分类管理：新增 / 改名 / 排序 / 归档。MUST NOT 物理删除。 */
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
    var renamingId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }

    val roots = categories.filter { it.level == 0 && !it.isArchived }
    val children = categories.filter { it.level == 1 && !it.isArchived }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类管理（最多两级）") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(roots, key = { it.id }) { root ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = root.name + "（" + typeLabel(root.type) + "）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    renamingId = root.id; renameText = root.name
                                }) { Text("改名") }
                                TextButton(onClick = { vm.archiveCategory(root.id) }) {
                                    Text("归档", color = LedgerColors.ExpenseRed)
                                }
                            }
                            children.filter { it.parentId == root.id }.forEach { child ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    Text(
                                        text = "└ " + child.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        renamingId = child.id; renameText = child.name
                                    }) { Text("改名") }
                                    TextButton(onClick = { vm.archiveCategory(child.id) }) {
                                        Text("归档", color = LedgerColors.ExpenseRed)
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
                    modifier = Modifier.padding(top = 10.dp)
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
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    roots.forEach { r ->
                        TextButton(onClick = { childParentId = r.id }) {
                            Text(
                                if (childParentId == r.id) "●" + r.name else r.name,
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

                val archived = categories.count { it.isArchived }
                if (archived > 0) {
                    Text(
                        text = "已归档 $archived 个分类（仍保留在库中，历史流水归属不变）",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

private fun typeLabel(type: RecordType): String =
    if (type == RecordType.EXPENSE) "支出" else "收入"
