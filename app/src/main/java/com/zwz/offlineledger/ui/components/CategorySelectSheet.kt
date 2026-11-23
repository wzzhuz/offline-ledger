package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zwz.offlineledger.data.display.CategoryDisplay
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 56 批：记一笔「分类 ›」与明细筛选「分类」共用的**只读**分类选择弹层。
 *
 * 版式借一羽「添加二级分类」页：顶部搜索 + 左侧一级分组导航 + 右侧 5 列
 * 等宽彩色图标格。与 CategoryPickerScreen 的区别是：
 * MUST NOT 有名称输入框、MUST NOT 有「继续添加」/ 保存按钮、
 * MUST NOT 暴露任何写库路径 —— 点任一项立即回填并关闭。
 *
 * 既有口径零回归：数据源不按 enabled 过滤（停用项可选、靠小角标表达）、
 * 归档项不进选择器、按收支类型过滤（type 为 null 时不过滤，供筛选沿用）、
 * 搜索跨组平铺、二级显示名经 CategoryDisplay 拼成「一级 / 二级」。
 *
 * 彩色 Tabler 图标只进本组件；记一笔快捷栏、明细列表行、账户详情、统计页
 * 仍用中性圆 + 中文首字，colorHex 一字不动。
 */
data class CategorySelectChoice(
    val categoryId: Long?,
    val rootId: Long?,
    val label: String,
    val level: Int
)

@Composable
fun CategorySelectDialog(
    categories: List<CategoryEntity>,
    type: RecordType?,
    selectedId: Long?,
    allowAll: Boolean,
    title: String,
    onSelect: (CategorySelectChoice) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var navRootId by remember { mutableStateOf<Long?>(null) }

    // 只读投影：不写库、不新建分类；归档项一律不进选择器。
    val scoped = remember(categories, type) {
        categories.filter { !it.isArchived && (type == null || it.type == type) }
    }
    val roots = remember(scoped) {
        scoped.filter { it.level == 0 }.sortedBy { it.sortOrder }
    }
    val childrenOf = { parentId: Long ->
        scoped.filter { it.level == 1 && it.parentId == parentId }
            .sortedBy { it.sortOrder }
    }

    // 搜索命中：一级名或其下二级名；二级 label 一律带父级，避免同名混淆。
    val hits: List<Pair<CategoryEntity, String>> = if (query.isBlank()) {
        emptyList()
    } else {
        val q = query.trim()
        roots.flatMap { r ->
            val rootMatch = r.name.contains(q, ignoreCase = true)
            val list = mutableListOf<Pair<CategoryEntity, String>>()
            if (rootMatch) list += r to r.name
            childrenOf(r.id).forEach { c ->
                if (rootMatch || c.name.contains(q, ignoreCase = true)) {
                    list += c to CategoryDisplay.resolve(r.name, c.name)
                }
            }
            list
        }
    }

    val currentLabel = if (selectedId == null) {
        "全部分类"
    } else {
        val c = scoped.firstOrNull { it.id == selectedId }
        if (c == null) "全部分类" else {
            val parent = c.parentId?.let { pid ->
                scoped.firstOrNull { it.id == pid }?.name
            }
            CategoryDisplay.resolve(parent, c.name)
        }
    }

    fun choose(c: CategoryEntity, label: String) {
        val rootId = if (c.level == 0) c.id else c.parentId
        onSelect(CategorySelectChoice(c.id, rootId, label, c.level))
        onDismiss()
    }

    val tiles: List<Pair<CategoryTile, () -> Unit>> = if (query.isNotBlank()) {
        hits.map { (c, label) ->
            Pair(tileOf(c, label), { choose(c, label) })
        }
    } else if (navRootId == null) {
        roots.map { r -> Pair(tileOf(r, r.name), { choose(r, r.name) }) }
    } else {
        val r = roots.firstOrNull { it.id == navRootId }
        if (r == null) {
            emptyList()
        } else {
            buildList {
                add(Pair(tileOf(r, r.name), { choose(r, r.name) }))
                childrenOf(r.id).forEach { c ->
                    val label = CategoryDisplay.resolve(r.name, c.name)
                    add(Pair(tileOf(c, label), { choose(c, label) }))
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
                Text(
                    text = "当前：" + currentLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text("搜索一级或二级分类名", style = MaterialTheme.typography.bodyMedium)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                )
                Spacer(Modifier.height(8.dp))

                // 固定高度：弹层不随分类数量增高（基线「统一网格与紧凑输入框」）。
                Row(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    LazyColumn(modifier = Modifier.width(96.dp).fillMaxHeight()) {
                        item {
                            SelectNavItem(
                                text = if (allowAll) "全部分类" else "全部一级",
                                selected = navRootId == null && query.isBlank(),
                                onClick = {
                                    if (allowAll) {
                                        onSelect(CategorySelectChoice(null, null, "全部分类", -1))
                                        onDismiss()
                                    } else {
                                        navRootId = null
                                        query = ""
                                    }
                                }
                            )
                        }
                        items(roots, key = { it.id }) { r ->
                            SelectNavItem(
                                text = r.name,
                                selected = navRootId == r.id && query.isBlank(),
                                onClick = { navRootId = r.id; query = "" }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (tiles.isEmpty()) {
                            Text(
                                text = if (query.isBlank()) "暂无可显示的分类" else "没有匹配的分类",
                                style = MaterialTheme.typography.bodySmall,
                                color = LedgerColors.Neutral,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            CategoryIconGrid(
                                tiles = tiles.map { it.first },
                                columns = 5,
                                grayscale = false,
                                onTile = { tile ->
                                    tiles.firstOrNull { it.first.id == tile.id }?.second?.invoke()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tileOf(c: CategoryEntity, label: String): CategoryTile = CategoryTile(
    id = c.id,
    name = label,
    iconName = c.iconName,
    colorHex = c.colorHex,
    enabled = c.enabled
)

@Composable
private fun SelectNavItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (selected) CategoryHeaderBlue else LedgerColors.Neutral,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) Color(0xFFE8EDFF) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    )
}
