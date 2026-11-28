package com.zwz.offlineledger.ui.settings

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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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

/**
 * 第 91 批：虚拟账本管理。定义只存 SharedPreferences，不进 Room / 备份 DTO。
 * 新建同名视图即可把带「台账:」标签的历史流水重新聚合出来，不需要迁移数据。
 *
 * 第 94 批：新建时选场景模板（人情往来 / 专项支出 / 自定义），非礼金模板可设
 * 默认分类，之后每笔台账录入自动带入，不用逐笔手选。顺带修掉第 93 批漏收口的
 * 三处过期表述（明细排除 / 旧入口位置）。
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
    var newTemplate by remember { mutableStateOf(VirtualBookTemplate.GIFT) }
    var renameTarget by remember { mutableStateOf<VirtualBook?>(null) }
    var renameText by remember { mutableStateOf("") }
    var configTarget by remember { mutableStateOf<VirtualBook?>(null) }
    var categoryTarget by remember { mutableStateOf<VirtualBook?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
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
            Text("虚拟账本", style = MaterialTheme.typography.headlineSmall)
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
            "场景模板",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
        )
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            VirtualBookTemplate.entries.forEach { t ->
                FilterChip(
                    selected = newTemplate == t,
                    onClick = { newTemplate = t },
                    label = { Text(t.label) }
                )
            }
        }
        Text(
            text = newTemplate.hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it; errorText = null },
                label = { Text("新建，如「结婚礼金」或「装修」") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val b = store.add(newName, newTemplate)
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
                // 第 94 批：第 93 批把切换器从常驻条改成明细页标题行图标，文案同步。
                "还没有虚拟账本。新建后从明细页标题行的账本图标进入。",
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
                        // 第 94 批：第 93 批已撤回明细排除 SQL，原「默认不显示」是错的。
                        "标签 ${b.tagName} · ${b.spec.label} · 不计入主账本收支",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (b.spec != VirtualBookTemplate.GIFT) {
                        Text(
                            "默认分类：" + categoryLabel(categories, b.defaultCategoryId),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = { configTarget = b }) { Text("配置") }
                        TextButton(onClick = { renameTarget = b; renameText = b.name }) { Text("重命名") }
                        TextButton(onClick = { store.setArchived(b.id, !b.archived); tick += 1 }) {
                            Text(if (b.archived) "恢复" else "归档")
                        }
                    }
                    Row {
                        TextButton(onClick = { store.move(b.id, -1); tick += 1 }) { Text("上移") }
                        TextButton(onClick = { store.move(b.id, 1); tick += 1 }) { Text("下移") }
                    }
                }
            }
        }
        Text(
            "提示：归档只从切换列表隐藏，历史流水与标签不受影响；" +
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
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
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
}

/** 二级显示成「一级 > 二级」，一级只显示自己；找不到时回退成不预设。 */
private fun categoryLabel(categories: List<CategoryEntity>, id: Long?): String {
    if (id == null) return "未设置"
    val c = categories.firstOrNull { it.id == id } ?: return "已失效，请重设"
    val parent = c.parentId?.let { pid -> categories.firstOrNull { it.id == pid } }
    return if (parent != null) "${parent.name} > ${c.name}" else c.name
}
