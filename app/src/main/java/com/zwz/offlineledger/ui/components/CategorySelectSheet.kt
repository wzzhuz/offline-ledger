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
 * 第 57 批（Q3A）：网格与搜索结果的二级**显示名一律用裸名**——左侧分组导航
 * 已表明父级，拼成「餐饮 / 外卖」会把 5 列格单行省略成「餐饮 / …」。
 * 但选中后回填筛选侧的 `displayName` MUST 仍经 `CategoryDisplay` 拼父级，
 * 第 29 批由统计占比图例、扇区下钻、筛选面板、明细筛选 chip 四处共用的
 * 显示名契约一字不动。
 *
 * 版式借一羽「添加二级分类」页：顶部搜索 + 左侧一级分组导航 + 右侧 5 列
 * 等宽彩色图标格（第 58 批改 4 列）。MUST NOT 含名称输入框、MUST NOT 含「继续添加」/ 保存按钮、
 * MUST NOT 暴露任何写库路径 —— 点任一项立即回填并关闭。
 *
 * 既有口径零回归：数据源不按 enabled 过滤（停用项可选、靠小角标表达）、
 * 归档项不进选择器、按收支类型过滤（type 为 null 时不过滤，供筛选沿用）、
 * 搜索跨组平铺、点选无二次确认。
 *
 * 彩色 Tabler 图标只进本组件；记一笔快捷栏、明细列表行、账户详情、统计页
 * 仍用中性圆 + 中文首字，colorHex 一字不动。
 */
data class CategorySelectChoice(
    val categoryId: Long?,
    val rootId: Long?,
    /** 网格 tile 与搜索结果的显示名：裸名，MUST NOT 拼父级前缀。 */
    val tileName: String,
    /** 回填筛选 chip 的只读展示名：仍为「一级 / 二级」。 */
    val displayLabel: String,
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

    // 搜索命中：一级名或其下二级名；结果只保留 entity，
    // 显示名（裸名）与回填名（拼父级）在 tileOf / choose 里统一算，不再带 label 字符串。
    val hits: List<CategoryEntity> = if (query.isBlank()) {
        emptyList()
    } else {
        val q = query.trim()
        roots.flatMap { r ->
            val rootMatch = r.name.contains(q, ignoreCase = true)
            buildList {
                if (rootMatch) add(r)
                childrenOf(r.id).forEach { c ->
                    if (rootMatch || c.name.contains(q, ignoreCase = true)) add(c)
                }
            }
        }
    }

    // 当前值仍是筛选 chip 口径：拼父级，MUST NOT 跟着改成裸名。
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

    fun choose(c: CategoryEntity) {
        val rootId = if (c.level == 0) c.id else c.parentId
        val parentName = c.parentId?.let { pid ->
            scoped.firstOrNull { it.id == pid }?.name
        }
        val displayLabel = if (c.level == 1) {
            CategoryDisplay.resolve(parentName, c.name)
        } else {
            c.name
        }
        onSelect(CategorySelectChoice(c.id, rootId, c.name, displayLabel, c.level))
        onDismiss()
    }

    val tiles: List<Pair<CategoryTile, () -> Unit>> = if (query.isNotBlank()) {
        hits.map { c -> Pair(tileOf(c), { choose(c) }) }
    } else if (navRootId == null) {
        roots.map { r -> Pair(tileOf(r), { choose(r) }) }
    } else {
        val r = roots.firstOrNull { it.id == navRootId }
        if (r == null) {
            emptyList()
        } else {
            buildList {
                add(Pair(tileOf(r), { choose(r) }))
                childrenOf(r.id).forEach { c ->
                    add(Pair(tileOf(c), { choose(c) }))
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 第 58 批（2B）：双层 padding 各收 12→8，把宽度还给右侧网格。
        Surface(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {
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
                    // 第 58 批（2B）：左区 96→80dp，减少左右分区的疏离感。
                    LazyColumn(modifier = Modifier.width(80.dp).fillMaxHeight()) {
                        item {
                            SelectNavItem(
                                text = if (allowAll) "全部分类" else "全部一级",
                                selected = navRootId == null && query.isBlank(),
                                onClick = {
                                    if (allowAll) {
                                        onSelect(
                                            CategorySelectChoice(
                                                null, null, "全部分类", "全部分类", -1
                                            )
                                        )
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
                    Spacer(Modifier.width(4.dp))
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
                                // 第 58 批（1A）：4 列。5 列时右侧实得 208dp、
                                // 每格文字区仅 37.6dp，4 字名被省略成 2 字；
                                // 配合 2B 收窄后右侧实得 244dp、每格约 57dp。
                                columns = 4,
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

/** 网格显示名一律用裸名；MUST NOT 拼父级前缀。 */
private fun tileOf(c: CategoryEntity): CategoryTile = CategoryTile(
    id = c.id,
    name = c.name,
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
