package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.display.CategoryDisplay
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.components.CategoryGlyph
import com.zwz.offlineledger.ui.components.CategoryHeaderBlue
import com.zwz.offlineledger.ui.components.CategoryIconGrid
import com.zwz.offlineledger.ui.components.CategoryTile
import com.zwz.offlineledger.ui.components.ListDivider
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 12 批：分类管理由「列表 + 新增表单同 Column」改为
 * 「TopBar 固定 + 惰性列表 + 编辑独立视图」。
 * 第 13–20 批：收支分段、笔数、默认折叠、二级 chips、reorderable 拖拽。
 *
 * 第 55 批：按一羽记账对照图重排三页。本页改动：
 * - 蓝底 TopBar + 支出/收入 Tab 坐进标题栏，右上独立「排序」入口；
 * - 顶部说明灰字收进 ⋮「使用说明」，不再常驻首屏；
 * - 一级行改「图标 + 名称 + 二级数角标 + 单 ⋮ 菜单」，≡ / › 收进菜单；
 * - 二级 WrapRow 自适应 chips 改 5 列等宽图标网格，末格「添加子类」，
 *   停用改小角标，长名单行省略；
 * - 底部胶囊「已归档 / 停用项管理」+ 蓝色大 + FAB；
 * - 就地拖拽移出列表页，统一交给独立「分类排序」页（保存 / 放弃）。
 *
 * 图标：Tabler Icons outline v3.34.0（MIT），按预置 id / iconName 查编译期目录，
 * 目录缺项才回退中性圆 + 首字；记一笔 / 明细 / 统计页零回归。
 * 骨架一字未回退：顶部固定入口 + LazyColumn + 编辑独立视图 + ListDivider 单一来源。
 */
private data class CategoryEditorState(
    val mode: String,                 // "add" | "rename"
    val targetId: Long? = null,
    val initialName: String = ""
)

@Composable
fun CategoryManagerScreen(
    onBack: () -> Unit,
    onOpenSort: (RecordType) -> Unit,
    onOpenPicker: (Long?, RecordType, String) -> Unit
) {
    val vm = settingsVm()
    val state by vm.state.collectAsState()
    // 排序页 / 选择页是独立路由、各自持有 ViewModel 实例；
    // 返回本页时重新查库，保证刚保存的 sortOrder 立即可见。
    LaunchedEffect(Unit) { vm.loadCategories() }

    var editor by remember { mutableStateOf<CategoryEditorState?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var showArchivedDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var catType by remember { mutableStateOf(RecordType.EXPENSE) }
    // 第 14 批：展开态用 String 键经 rememberSaveable 保存，避免 Set 的 saver 版本依赖。
    var expandedKey by rememberSaveable { mutableStateOf("") }
    val expandedRoots: Set<Long> = expandedKey.split(',')
        .filter { it.isNotEmpty() }
        .mapNotNull { it.toLongOrNull() }
        .toSet()

    val categories = state.categories
    val archived = categories.filter { it.isArchived && it.type == catType }

    Column(modifier = Modifier.fillMaxSize()) {
        CategoryManagerTopBar(
            title = if (editor == null) "分类管理" else
                (if (editor!!.mode == "rename") "改名" else "新增分类"),
            onBack = { if (editor != null) editor = null else onBack() },
            showTabs = editor == null,
            catType = catType,
            onType = { catType = it },
            onSort = { onOpenSort(catType) },
            onAdd = if (editor == null) {
                { editor = CategoryEditorState("add") }
            } else null,
            onHelp = { showHelp = true }
        )
        if (editor != null) {
            CategoryEditor(
                editor = editor!!,
                catType = catType,
                onSaveRoot = { name, type -> vm.createCategory(name, type, null); editor = null },
                onSaveRename = { id, name -> vm.renameCategory(id, name); editor = null },
                modifier = Modifier.weight(1f)
            )
        } else {
            CategoryList(
                categories = categories,
                catType = catType,
                counts = state.categoryCounts,
                expandedRoots = expandedRoots,
                onToggleExpand = { id ->
                    val next = if (id in expandedRoots)
                        expandedRoots - id else expandedRoots + id
                    expandedKey = next.sorted().joinToString(",")
                },
                onOpen = { id, name -> vm.openCategoryDetail(id, name) },
                onRename = { id, name -> editor = CategoryEditorState("rename", id, name) },
                onDelete = { deletingId = it },
                onMove = { id, delta -> vm.moveCategory(id, delta) },
                onToggleEnabled = { id, en -> vm.setCategoryEnabled(id, en) },
                onAddChild = { parentId, type -> onOpenPicker(parentId, type, "add") },
                modifier = Modifier.weight(1f)
            )
            CategoryBottomBar(
                archivedCount = archived.size,
                onArchived = { showArchivedDialog = true },
                onDisabled = { onOpenPicker(null, catType, "disabled") },
                onAdd = { editor = CategoryEditorState("add") }
            )
        }
    }

    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("删除分类") },
            text = { Text("已有流水或预制分类会转为归档，可在底部「已归档」区恢复，历史流水归属不变；仅手工新建且无流水的分类会直接删除。预制分类删除后不会自动补回，恢复标准清单需全新安装且不导入旧备份。最多两级；笔数含二级；排序请到「分类排序」页调整并点保存。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCategory(id); deletingId = null }) {
                    Text("确定", color = LedgerColors.ExpenseRed)
                }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } }
        )
    }

    if (showArchivedDialog) {
        AlertDialog(
            onDismissRequest = { showArchivedDialog = false },
            title = { Text("已归档分类（${archived.size}）") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (archived.isEmpty()) {
                        Text("暂无归档分类", style = MaterialTheme.typography.bodySmall)
                    } else {
                        archived.forEach { c ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            ) {
                                Text(
                                    text = CategoryDisplay.resolve(
                                        categories.firstOrNull { it.id == c.parentId }?.name, c.name
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.restoreCategory(c.id) }) { Text("恢复") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showArchivedDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("使用说明") },
            text = { Text("最多两级；点一级展开二级，点二级可编辑；笔数含二级。删除有流水的分类自动转归档，可在底部「已归档」恢复。排序请点右上「排序」，调整后需点「保存」才生效，返回可放弃。停用项可在底部「停用项管理」重新启用。") },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("知道了") }
            }
        )
    }

    if (state.categoryDetailName.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.closeCategoryDetail() },
            title = { Text(state.categoryDetailName + " · 近期流水") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (state.categoryDetail.isEmpty()) {
                        Text("该分类暂无流水", style = MaterialTheme.typography.bodySmall)
                    } else {
                        state.categoryDetail.forEach { r ->
                            Text(
                                text = dateLabel(r.localDate) + "  " +
                                    (if (r.type == "EXPENSE") "支出" else "收入") + "  " +
                                    formatCents(r.amountCents) + "  " +
                                    (r.note ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                        Text(
                            text = "仅显示最近 30 条，不全量加载。",
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.closeCategoryDetail() }) { Text("关闭") }
            }
        )
    }
}

/* ------------------------------------------------------------------ */
/* 蓝底 TopBar：一羽式页面身份，Tab 坐进标题栏下部                      */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryManagerTopBar(
    title: String,
    onBack: () -> Unit,
    showTabs: Boolean,
    catType: RecordType,
    onType: (RecordType) -> Unit,
    onSort: () -> Unit,
    onAdd: (() -> Unit)?,
    onHelp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LedgerColors.CardSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = LedgerColors.OnSurface
                )
            }
            Text(
                text = title,
                color = LedgerColors.OnSurface,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (showTabs) {
                TextButton(onClick = onSort) { Text("排序", color = LedgerColors.OnSurface) }
            }
            if (onAdd != null) {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "新增分类", tint = LedgerColors.OnSurface)
                }
            }
            IconButton(onClick = onHelp) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = LedgerColors.OnSurface)
            }
        }
        if (showTabs) {
            Row(modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                listOf(RecordType.EXPENSE, RecordType.INCOME).forEach { t ->
                    val selected = catType == t
                    Text(
                        text = typeLabel(t),
                        color = if (selected) LedgerColors.OnSurface else LedgerColors.Neutral,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { onType(t) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* 底部胶囊：借一羽的位，不借「系统分类 / 扩展包」的功能                 */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryBottomBar(
    archivedCount: Int,
    onArchived: () -> Unit,
    onDisabled: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .background(LedgerColors.SurfaceVariant, RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = onArchived) {
                Text(
                    "已归档（$archivedCount）",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
            TextButton(onClick = onDisabled) {
                Text("停用项管理", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        FloatingActionButton(
            onClick = onAdd,
            containerColor = CategoryHeaderBlue
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新增分类", tint = LedgerColors.OnPrimary)
        }
    }
}

/* ------------------------------------------------------------------ */
/* 一级默认折叠；展开区改 5 列等宽图标网格，末格「添加子类」            */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryList(
    categories: List<CategoryEntity>,
    catType: RecordType,
    counts: Map<Long, Int>,
    expandedRoots: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    onOpen: (Long, String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onAddChild: (Long, RecordType) -> Unit,
    modifier: Modifier = Modifier
) {
    val roots = categories.filter {
        it.level == 0 && !it.isArchived && it.type == catType
    }.sortedBy { it.sortOrder }
    val children = categories.filter {
        it.level == 1 && !it.isArchived && it.type == catType
    }
    // 第 14 批：点二级格弹操作 Dialog，五项操作一个不丢。
    var actionChild by remember { mutableStateOf<CategoryEntity?>(null) }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        roots.forEach { root ->
            val expanded = root.id in expandedRoots
            val childList = children.filter { it.parentId == root.id }.sortedBy { it.sortOrder }
            item(key = "root-" + root.id) {
                Column {
                    CategoryRootRow(
                        id = root.id,
                        name = root.name,
                        iconName = root.iconName,
                        colorHex = root.colorHex,
                        enabled = root.enabled,
                        childCount = childList.size,
                        countText = (counts[root.id] ?: 0).toString() + " 笔（含二级）",
                        expanded = expanded,
                        onToggle = { onToggleExpand(root.id) },
                        onOpen = { onOpen(root.id, root.name) },
                        onMoveUp = { onMove(root.id, -1) },
                        onMoveDown = { onMove(root.id, 1) },
                        onRename = { onRename(root.id, root.name) },
                        onDelete = { onDelete(root.id) },
                        onToggleEnabled = { onToggleEnabled(root.id, !root.enabled) },
                        onAddChild = { onAddChild(root.id, root.type) }
                    )
                    if (expanded) {
                        val tiles = childList.map { c ->
                            CategoryTile(
                                id = c.id,
                                name = c.name,
                                iconName = c.iconName,
                                colorHex = c.colorHex,
                                enabled = c.enabled
                            )
                        } + CategoryTile(id = -1L, name = "添加子类", isAdd = true)
                        CategoryIconGrid(
                            tiles = tiles,
                            columns = 5,
                            onTile = { tile ->
                                if (tile.isAdd) {
                                    onAddChild(root.id, root.type)
                                } else {
                                    actionChild = childList.firstOrNull { it.id == tile.id }
                                }
                            },
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 6.dp)
                        )
                    }
                    ListDivider(Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }

    actionChild?.let { c ->
        AlertDialog(
            onDismissRequest = { actionChild = null },
            title = { Text(c.name) },
            text = {
                Column {
                    Text(
                        "二级分类 · " + (counts[c.id] ?: 0) + " 笔流水",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral
                    )
                    TextButton(
                        onClick = { actionChild = null; onRename(c.id, c.name) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("改名") }
                    TextButton(
                        onClick = { actionChild = null; onOpen(c.id, c.name) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("看流水") }
                    TextButton(
                        onClick = { actionChild = null; onToggleEnabled(c.id, !c.enabled) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (c.enabled) "停用" else "启用") }
                    TextButton(
                        onClick = { actionChild = null; onMove(c.id, -1) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("上移") }
                    TextButton(
                        onClick = { actionChild = null; onMove(c.id, 1) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("下移") }
                    TextButton(
                        onClick = { actionChild = null; onDelete(c.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("删除", color = LedgerColors.ExpenseRed) }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionChild = null }) { Text("关闭") }
            }
        )
    }
}

/* ------------------------------------------------------------------ */
/* 一级行：图标 + 名称 + 二级数角标 + 单 ⋮ 菜单（≡ / › 收进菜单）       */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryRootRow(
    id: Long,
    name: String,
    iconName: String,
    colorHex: String,
    enabled: Boolean,
    childCount: Int,
    countText: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    onAddChild: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable { onToggle() },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) "▾ " else "▸ ",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )
                CategoryGlyph(
                    name = name,
                    iconName = iconName,
                    colorHex = colorHex,
                    grayscale = false,
                    size = 36,
                    categoryId = id
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    LedgerColors.Neutral.copy(alpha = 0.10f),
                                    CircleShape
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = childCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = LedgerColors.Neutral
                            )
                        }
                        if (!enabled) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "停用",
                                style = MaterialTheme.typography.labelSmall,
                                color = LedgerColors.ExpenseRed
                            )
                        }
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral
                    )
                }
            }
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("添加子类") }, onClick = { menu = false; onAddChild() })
            DropdownMenuItem(text = { Text("看流水") }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("改名") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMoveUp() })
            DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMoveDown() })
            DropdownMenuItem(
                text = { Text(if (enabled) "停用（录入隐藏）" else "启用（录入显示）") },
                onClick = { menu = false; onToggleEnabled() }
            )
            DropdownMenuItem(
                text = { Text("删除", color = LedgerColors.ExpenseRed) },
                onClick = { menu = false; onDelete() }
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* 编辑独立视图：新增一级 / 改名。新增二级统一交给分类选择页            */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryEditor(
    editor: CategoryEditorState,
    catType: RecordType,
    onSaveRoot: (String, RecordType) -> Unit,
    onSaveRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(editor.initialName) }
    var type by remember { mutableStateOf(catType) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "名称",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 10.dp)
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            placeholder = {
                Text(
                    if (editor.mode == "rename") "新名称" else "分类名称",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
        )

        if (editor.mode == "add") {
            Text(
                text = "收支类型",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                listOf(RecordType.EXPENSE, RecordType.INCOME).forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(typeLabel(t)) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Text(
                text = "新增二级分类：回到列表展开某一级，点网格末位「添加子类」，父级与收支类型会自动带入。",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        error?.let {
            Text(it, color = LedgerColors.ExpenseRed, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                if (name.isBlank()) { error = "请填名称"; return@Button }
                if (editor.mode == "rename") {
                    onSaveRename(editor.targetId!!, name.trim())
                } else {
                    onSaveRoot(name.trim(), type)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) { Text("保存") }
    }
}

private fun dateLabel(d: Int): String {
    val m = (d / 100) % 100
    val day = d % 100
    return m.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

private fun typeLabel(type: RecordType): String =
    if (type == RecordType.EXPENSE) "支出" else "收入"
