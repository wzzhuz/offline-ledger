package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.zwz.offlineledger.ui.components.ListDivider
import com.zwz.offlineledger.ui.components.WrapRow
import com.zwz.offlineledger.ui.components.rememberReorderDrag
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 12 批：分类管理由「列表 + 新增表单同 Column(verticalScroll)」改为
 * 「TopBar 固定 + 入口 + LazyColumn 纯列表 + 编辑独立视图」。
 * 第 13 批：补齐信息价值——支出 / 收入分段、每类笔数（一级含二级）、
 * 点分类行看近期流水、编辑视图字段分组。骨架一字未回退。
 * 第 14 批：一级默认折叠、点一级展开二级 chips、点二级 chip 弹操作
 * （改名 / 删除 / 上移 / 下移 / 看流水）；展开态用 rememberSaveable 保存。
 * 第 12/13 批的 TopBar 固定 + 惰性列表 + 编辑独立视图骨架一字未回退。
 * 第 15 批：一级之间统一 ListDivider、一级标题加粗、二级 chip 按文本自适应、
 * 一级行长按拖手柄重排（零新增依赖，松手一次性落库）。
 * 第 16 批：改用 ReorderSupport 共享拖拽（拖动中实时重排让位，非松手跳变）；
 * 二级 chips 改 WrapRow 自适应换行并同样支持长按拖拽；上移/下移保留为无障碍兜底。
 */
private data class CategoryEditorState(
    val mode: String,                 // "add" | "rename"
    val targetId: Long? = null,
    val initialName: String = ""
)

@Composable
fun CategoryManagerScreen(onBack: () -> Unit) {
    val vm = settingsVm()
    val state by vm.state.collectAsState()
    var editor by remember { mutableStateOf<CategoryEditorState?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var catType by remember { mutableStateOf(RecordType.EXPENSE) }
    // 第 14 批：展开态用 String 键经 rememberSaveable 保存（autoSaver 对 String 零风险），
    // 再派生 Set<Long>，避免保存 MutableState<Set<>> 包装对象带来的 saver 版本依赖。
    var expandedKey by rememberSaveable { mutableStateOf("") }
    val expandedRoots: Set<Long> = expandedKey.split(',')
        .filter { it.isNotEmpty() }
        .mapNotNull { it.toLongOrNull() }
        .toSet()

    val categories = state.categories
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsPageTopBar(
            title = if (editor == null) "分类管理" else
                (if (editor!!.mode == "rename") "改名" else "新增分类"),
            onBack = {
                if (editor != null) editor = null else onBack()
            },
            onAdd = if (editor == null) {
                { editor = CategoryEditorState("add") }
            } else null
        )
        if (editor != null) {
            CategoryEditor(
                editor = editor!!,
                categories = categories,
                catType = catType,
                onSaveRoot = { name, type -> vm.createCategory(name, type, null); editor = null },
                onSaveChild = { name, parentId ->
                    val parent = categories.firstOrNull { it.id == parentId }
                    if (parent != null) vm.createCategory(name, parent.type, parentId)
                    editor = null
                },
                onSaveRename = { id, name -> vm.renameCategory(id, name); editor = null },
                modifier = Modifier.weight(1f)
            )
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                listOf(RecordType.EXPENSE, RecordType.INCOME).forEach { t ->
                    FilterChip(
                        selected = catType == t,
                        onClick = { catType = t },
                        label = { Text(typeLabel(t)) }
                    )
                }
            }
            CategoryList(
                categories = categories,
                catType = catType,
                counts = state.categoryCounts,
                showArchived = showArchived,
                expandedRoots = expandedRoots,
                onToggleExpand = { id ->
                    val next = if (id in expandedRoots)
                        expandedRoots - id else expandedRoots + id
                    expandedKey = next.sorted().joinToString(",")
                },
                onToggleArchived = { showArchived = !showArchived },
                onOpen = { id, name -> vm.openCategoryDetail(id, name) },
                onRename = { id, name -> editor = CategoryEditorState("rename", id, name) },
                onDelete = { deletingId = it },
                onMove = { id, delta -> vm.moveCategory(id, delta) },
                onRestore = { vm.restoreCategory(it) },
                onCommitOrder = { ids -> vm.commitRootOrder(ids, catType) },
                onCommitChildOrder = { parentId, ids -> vm.commitChildOrder(parentId, ids) },
                modifier = Modifier.weight(1f)
            )
        }
    }

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

private fun dateLabel(d: Int): String {
    val m = (d / 100) % 100
    val day = d % 100
    return m.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

/* ------------------------------------------------------------------ */
/* 一级默认折叠；点一级展开二级 chips，点 chip 弹操作（不回退第 12 批骨架）  */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryList(
    categories: List<CategoryEntity>,
    catType: RecordType,
    counts: Map<Long, Int>,
    showArchived: Boolean,
    expandedRoots: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    onToggleArchived: () -> Unit,
    onOpen: (Long, String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onRestore: (Long) -> Unit,
    onCommitOrder: (List<Long>) -> Unit,
    onCommitChildOrder: (Long, List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootsBase = categories.filter {
        it.level == 0 && !it.isArchived && it.type == catType
    }
    val children = categories.filter {
        it.level == 1 && !it.isArchived && it.type == catType
    }
    val archived = categories.filter { it.isArchived && it.type == catType }
    // 第 14 批：点二级 chip 弹一个操作 Dialog，五项操作一个不丢。
    var actionChild by remember { mutableStateOf<CategoryEntity?>(null) }

    // 第 15 批：一级拖拽排序。本地顺序是可观察状态，松手一次性提交落库；
    // item 用 id 稳定 key；零新增依赖，不用跨应用 dragAndDrop API。
    // 第 16 批：共享拖拽状态。拖动中每跨一行即重排，列表实时让位；
    // 松手才一次性落库；scope 仍为当前收支类型的一级组。
    val rootDrag = rememberReorderDrag(
        ids = rootsBase.map { it.id },
        rowHeightDp = 56,
        onCommit = { ids -> onCommitOrder(ids) }
    )
    val roots = rootDrag.orderedIds.mapNotNull { id -> rootsBase.firstOrNull { it.id == id } }

    LazyColumn(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        item {
            Text(
                text = "最多两级；点一级展开二级，点二级可编辑；笔数含二级。删除有流水的分类自动转归档。",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }
        roots.forEach { root ->
            val expanded = root.id in expandedRoots
            val childList = children.filter { it.parentId == root.id }
            item(key = "root-" + root.id) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(rootDrag.itemModifier(root.id))
                ) {
                    Column {
                        CategoryRootRow(
                            label = root.name,
                            childCount = childList.size,
                            countText = (counts[root.id] ?: 0).toString() + " 笔（含二级）",
                            expanded = expanded,
                            onToggle = { onToggleExpand(root.id) },
                            onOpen = { onOpen(root.id, root.name) },
                            onMoveUp = { onMove(root.id, -1) },
                            onMoveDown = { onMove(root.id, 1) },
                            onRename = { onRename(root.id, root.name) },
                            onDelete = { onDelete(root.id) },
                            dragModifier = rootDrag.handleModifier(root.id)
                        )
                        if (expanded) {
                            // 第 16 批：二级 chips 自适应换行 + 与一级同款实时让位拖拽。
                            // 短名不再留白；长名受 widthIn(max) 约束，不独占整行。
                            val childDrag = rememberReorderDrag(
                                ids = childList.map { it.id },
                                rowHeightDp = 40,
                                onCommit = { ids -> onCommitChildOrder(root.id, ids) }
                            )
                            val orderedChildren = childDrag.orderedIds.mapNotNull { cid ->
                                childList.firstOrNull { it.id == cid }
                            }
                            WrapRow(
                                spacing = 6.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 2.dp, bottom = 6.dp)
                            ) {
                                orderedChildren.forEach { child ->
                                    Box(modifier = childDrag.itemModifier(child.id)) {
                                        FilterChip(
                                            selected = false,
                                            onClick = { actionChild = child },
                                            label = {
                                                Text(
                                                    child.name,
                                                    maxLines = 1,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            modifier = Modifier
                                                .widthIn(max = 150.dp)
                                                .then(childDrag.handleModifier(child.id))
                                        )
                                    }
                                }
                            }
                        }
                        ListDivider(Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
        if (archived.isNotEmpty()) {
            item(key = "archived-toggle") {
                TextButton(onClick = onToggleArchived) {
                    Text((if (showArchived) "收起" else "展开") +
                        "已归档分类（${archived.size}）")
                }
            }
            if (showArchived) {
                items(archived, key = { "arch-" + it.id }) { c ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    ) {
                        Text(
                            text = c.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerColors.Neutral,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onRestore(c.id) }) { Text("恢复") }
                    }
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

@Composable
private fun CategoryRootRow(
    label: String,
    childCount: Int,
    countText: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
    ) {
        // 整行主点击区：展开 / 收起，保持 44dp 触摸目标。
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clickable { onToggle() },
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expanded) "▾ " else "▸ ",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = childCount.toString() + " 二级分类 · " + countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
        }
        // 第 15 批：长按拖手柄；只在手柄上接管拖拽，滚动不误触。
        // 用文本「≡」而非 Icons.Filled.Menu —— material-icons-core 无该图标，
        // 首轮 CI 的 Unresolved reference 'Menu' 即由此而来（诊断日志已证实）。
        Box(
            modifier = dragModifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "≡",
                style = MaterialTheme.typography.titleMedium,
                color = LedgerColors.Neutral
            )
        }
        IconButton(onClick = onOpen) {
            Text("›", style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("看流水") }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMoveUp() })
            DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMoveDown() })
            DropdownMenuItem(text = { Text("改名") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(
                text = { Text("删除", color = LedgerColors.ExpenseRed) },
                onClick = { menu = false; onDelete() }
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* 编辑独立视图：字段分组；父级列表按当前收支分段过滤                     */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryEditor(
    editor: CategoryEditorState,
    categories: List<CategoryEntity>,
    catType: RecordType,
    onSaveRoot: (String, RecordType) -> Unit,
    onSaveChild: (String, Long) -> Unit,
    onSaveRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(editor.initialName) }
    var level by remember { mutableStateOf(0) }
    var type by remember { mutableStateOf(catType) }
    var parentId by remember { mutableStateOf<Long?>(null) }
    var parentQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val roots = categories.filter {
        it.level == 0 && !it.isArchived && it.type == catType
    }
    val matched = roots.filter { it.name.contains(parentQuery, ignoreCase = true) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (editor.mode == "add") {
            Text("分类层级", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.padding(top = 4.dp)) {
                FilterChip(
                    selected = level == 0,
                    onClick = { level = 0; error = null },
                    label = { Text("一级分类") },
                    modifier = Modifier.padding(end = 6.dp)
                )
                FilterChip(
                    selected = level == 1,
                    onClick = { level = 1; error = null },
                    label = { Text("二级分类") }
                )
            }
        }

        Text(
            text = "名称",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 10.dp)
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            placeholder = { Text(if (editor.mode == "rename") "新名称" else "分类名称") },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
        )

        if (editor.mode == "add") {
            if (level == 0) {
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
            } else {
                Text(
                    text = "父级（" + typeLabel(catType) + "）",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 10.dp)
                )
                val selected = roots.firstOrNull { it.id == parentId }
                Text(
                    text = "当前：" + (selected?.name ?: "未选，点下面列表选择"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected == null) LedgerColors.ExpenseRed else LedgerColors.IncomeGreen,
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = parentQuery,
                    onValueChange = { parentQuery = it },
                    placeholder = { Text("搜索一级分类（可留空看全部）") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                )
                // 限高 + 惰性列表：10 个一级不再全量平铺 480dp。
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(matched, key = { it.id }) { r ->
                        FilterChip(
                            selected = parentId == r.id,
                            onClick = { parentId = r.id; error = null },
                            label = { Text(r.name, maxLines = 1) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        error?.let {
            Text(it, color = LedgerColors.ExpenseRed, style = MaterialTheme.typography.bodySmall)
        }
        Row(modifier = Modifier.padding(top = 10.dp)) {
            androidx.compose.material3.Button(
                onClick = {
                    if (name.isBlank()) { error = "请填名称"; return@Button }
                    when {
                        editor.mode == "rename" ->
                            onSaveRename(editor.targetId!!, name.trim())
                        level == 0 -> onSaveRoot(name.trim(), type)
                        else -> {
                            val p = parentId
                            if (p == null) { error = "请先选一个一级分类"; return@Button }
                            onSaveChild(name.trim(), p)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
        }
    }
}

private fun typeLabel(type: RecordType): String =
    if (type == RecordType.EXPENSE) "支出" else "收入"
