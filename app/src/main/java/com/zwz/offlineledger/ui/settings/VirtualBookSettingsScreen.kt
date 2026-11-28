package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.prefs.VirtualBook
import com.zwz.offlineledger.core.prefs.VirtualBookStore
import com.zwz.offlineledger.core.prefs.VirtualBookTemplate
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.ui.LedgerDi
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 91 批：虚拟账本管理。定义只存 SharedPreferences，不进 Room / 备份 DTO。
 * 新建同名视图即可把带「台账:」标签的历史流水重新聚合出来，不需要迁移数据。
 *
 * 第 94 批：新建时选场景模板（人情往来 / 专项支出 / 自定义），非礼金模板可设
 * 默认分类，之后每笔台账录入自动带入，不用逐笔手选。
 *
 * 第 95 批：
 * - 新建表单拆到独立页，本页只留总开关 + 列表 + 右上角「+」；
 * - 每张卡片的六项操作收进右侧「⋮」，两行压成一行，与账户列表同款；
 * - 补真实删除：只删视图定义，MUST NOT 删流水，确认弹窗写明可同名重建找回。
 */
@Composable
fun VirtualBookSettingsScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenBook: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember { VirtualBookStore(context) }
    var tick by remember { mutableStateOf(0) }
    var renameTarget by remember { mutableStateOf<VirtualBook?>(null) }
    var renameText by remember { mutableStateOf("") }
    var configTarget by remember { mutableStateOf<VirtualBook?>(null) }
    var categoryTarget by remember { mutableStateOf<VirtualBook?>(null) }
    var deleteTarget by remember { mutableStateOf<VirtualBook?>(null) }
    // tick 作重组信号：改动后重读 SharedPreferences 的最新定义。
    val books = remember(tick) { store.all() }
    var exclude by remember { mutableStateOf(store.excludeByDefault) }
    val categories by produceState(initialValue = emptyList<CategoryEntity>()) {
        value = runCatching { LedgerDi.entryPoint(context).categoryDao().all() }
            .getOrDefault(emptyList())
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text("虚拟账本", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            // 第 95 批：新建不再和列表混在一屏。
            IconButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = "新建虚拟账本")
            }
        }
        Text(
            text = "虚拟账本只是按标签聚合的视图：流水仍在主账本明细可见，切换入口只切浏览范围，" +
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

        Text(
            "已建视图",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
        )
        if (books.isEmpty()) {
            Text(
                "还没有虚拟账本。点右上角「+」新建，之后从明细页标题行的账本图标进入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        books.forEach { b ->
            VirtualBookCard(
                book = b,
                categories = categories,
                onOpen = { onOpenBook(b.id) },
                onConfig = { configTarget = b },
                onRename = { renameTarget = b; renameText = b.name },
                onArchived = { store.setArchived(b.id, !b.archived); tick += 1 },
                onMove = { d -> store.move(b.id, d); tick += 1 },
                onDelete = { deleteTarget = b }
            )
        }
        Text(
            "提示：删除只移除这个视图的入口，已记流水仍在主账本明细；归档只从切换列表隐藏。" +
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

    // 第 94 批：改场景模板；切到非礼金模板时可直接去设默认分类。
    configTarget?.let { target ->
        var picked by remember(target.id) { mutableStateOf(target.spec) }
        AlertDialog(
            onDismissRequest = { configTarget = null },
            title = { Text("配置「${target.name}」") },
            text = {
                Column {
                    Text("场景模板", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VirtualBookTemplate.entries.forEach { t ->
                            FilterChip(
                                selected = picked == t,
                                onClick = { picked = t },
                                label = { Text(t.label) }
                            )
                        }
                    }
                    Text(
                        picked.hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (picked != VirtualBookTemplate.GIFT) {
                        TextButton(onClick = {
                            store.setTemplate(target.id, picked)
                            categoryTarget = store.byId(target.id)
                        }) { Text("设置默认分类…") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.setTemplate(target.id, picked)
                    tick += 1
                    configTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { configTarget = null }) { Text("取消") } }
        )
    }

    // 第 94 批：默认分类一次配置，之后每笔台账录入自动预填，不必逐笔手选。
    categoryTarget?.let { target ->
        val filter = target.spec.categoryTypeFilter
        val options = categories.filter { c ->
            !c.isArchived && (filter == null || c.type.name == filter)
        }
        AlertDialog(
            onDismissRequest = { categoryTarget = null },
            title = { Text("默认分类") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    item {
                        TextButton(onClick = {
                            store.setDefaultCategory(target.id, null)
                            tick += 1
                            categoryTarget = null
                        }) { Text("（不预设，记一笔里选）") }
                    }
                    options.forEach { c ->
                        item {
                            TextButton(onClick = {
                                store.setDefaultCategory(target.id, c.id)
                                tick += 1
                                categoryTarget = null
                            }) { Text(categoryLabel(categories, c.id)) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { categoryTarget = null }) { Text("取消") } }
        )
    }

    // 第 95 批：真删除。弹窗 MUST 明说「不删流水 / 可同名重建找回」。
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除虚拟账本") },
            text = {
                Column {
                    Text("视图：「${target.name}」", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "标签：${target.tagName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "只删除这个视图的入口，不会删除任何已记流水。" +
                            "那些流水仍带此标签、仍在主账本明细可见；" +
                            "以后新建一个同名视图即可重新聚合查看。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.ExpenseRed,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(target.id)
                    tick += 1
                    deleteTarget = null
                }) { Text("确认删除", color = LedgerColors.ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

/**
 * 第 95 批：六项操作收进右侧「⋮」，与账户列表同款，一行放下。
 *
 * 删除触发用 pendingDelete + LaunchedEffect(menu) 解耦：第 60 批 Q2A 真机反馈
 * 「点删除没弹窗」——DropdownMenu 的 Popup dismiss 与 AlertDialog 新窗口同帧竞争。
 */
@Composable
private fun VirtualBookCard(
    book: VirtualBook,
    categories: List<CategoryEntity>,
    onOpen: () -> Unit,
    onConfig: () -> Unit,
    onRename: () -> Unit,
    onArchived: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    LaunchedEffect(menu) {
        if (!menu && pendingDelete) {
            pendingDelete = false
            onDelete()
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name + if (book.archived) "（已归档）" else "",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "标签 ${book.tagName} · ${book.spec.label} · 不计入主账本收支",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (book.spec != VirtualBookTemplate.GIFT) {
                    Text(
                        "默认分类：" + categoryLabel(categories, book.defaultCategoryId),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("查看") }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("配置") }, onClick = { menu = false; onConfig() })
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(
                        text = { Text(if (book.archived) "恢复" else "归档") },
                        onClick = { menu = false; onArchived() }
                    )
                    DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(
                        text = { Text("删除", color = LedgerColors.ExpenseRed) },
                        onClick = { menu = false; pendingDelete = true }
                    )
                }
            }
        }
    }
}

/** 二级显示成「一级 > 二级」，一级只显示自己；找不到时回退成不预设。 */
private fun categoryLabel(categories: List<CategoryEntity>, id: Long?): String {
    if (id == null) return "未设置"
    val c = categories.firstOrNull { it.id == id } ?: return "已失效，请重设"
    val parent = c.parentId?.let { pid -> categories.firstOrNull { it.id == pid } }
    return if (parent != null) "${parent.name} > ${c.name}" else c.name
}
