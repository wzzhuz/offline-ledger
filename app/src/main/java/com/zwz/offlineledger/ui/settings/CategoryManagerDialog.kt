package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 12 批：分类管理由「列表 + 新增表单同 Column(verticalScroll)」改为
 * 「TopBar 固定 + 入口 + LazyColumn 纯列表 + 编辑独立视图」。
 * 新增入口不再随分类增多往下移动；顶层 LazyColumn 无无限高度问题
 * （旧注释「用普通 Column 避免嵌套 LazyColumn 冲突」只对嵌套成立）。
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
            CategoryList(
                categories = categories,
                showArchived = showArchived,
                onToggleArchived = { showArchived = !showArchived },
                onRename = { id, name -> editor = CategoryEditorState("rename", id, name) },
                onDelete = { deletingId = it },
                onMove = { id, delta -> vm.moveCategory(id, delta) },
                onRestore = { vm.restoreCategory(it) },
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
}

/* ------------------------------------------------------------------ */
/* 纯列表：LazyColumn + 每行一个「⋯」菜单，行高不再被 4 个 48dp 按钮撑高   */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryList(
    categories: List<CategoryEntity>,
    showArchived: Boolean,
    onToggleArchived: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onRestore: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val roots = categories.filter { it.level == 0 && !it.isArchived }
    val children = categories.filter { it.level == 1 && !it.isArchived }
    val archived = categories.filter { it.isArchived }

    LazyColumn(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        item {
            Text(
                text = "最多两级；删除有流水的分类自动转归档，历史流水归属不变。",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        roots.forEach { root ->
            item(key = "root-" + root.id) {
                CategoryRow(
                    label = root.name + "（" + typeLabel(root.type) + "）",
                    indent = 0,
                    onMoveUp = { onMove(root.id, -1) },
                    onMoveDown = { onMove(root.id, 1) },
                    onRename = { onRename(root.id, root.name) },
                    onDelete = { onDelete(root.id) }
                )
            }
            children.filter { it.parentId == root.id }.forEach { child ->
                item(key = "child-" + child.id) {
                    CategoryRow(
                        label = "└ " + child.name,
                        indent = 1,
                        onMoveUp = { onMove(child.id, -1) },
                        onMoveDown = { onMove(child.id, 1) },
                        onRename = { onRename(child.id, child.name) },
                        onDelete = { onDelete(child.id) }
                    )
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
                            text = c.name + "（" + typeLabel(c.type) + "）",
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
}

@Composable
private fun CategoryRow(
    label: String,
    indent: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(start = if (indent == 1) 12.dp else 0.dp)
            .heightIn(min = 40.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
/* 编辑独立视图：新增（一级 / 二级切换）与改名；父级搜索 + 限高全量列表      */
/* ------------------------------------------------------------------ */
@Composable
private fun CategoryEditor(
    editor: CategoryEditorState,
    categories: List<CategoryEntity>,
    onSaveRoot: (String, RecordType) -> Unit,
    onSaveChild: (String, Long) -> Unit,
    onSaveRename: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(editor.initialName) }
    var level by remember { mutableStateOf(0) }                 // 0 一级 / 1 二级
    var type by remember { mutableStateOf(RecordType.EXPENSE) }
    var parentId by remember { mutableStateOf<Long?>(null) }
    var parentQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val roots = categories.filter { it.level == 0 && !it.isArchived }
    val matched = roots.filter { it.name.contains(parentQuery, ignoreCase = true) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (editor.mode == "add") {
            Row {
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

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            placeholder = { Text(if (editor.mode == "rename") "新名称" else "分类名称") },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
        )

        if (editor.mode == "add" && level == 0) {
            Row(modifier = Modifier.padding(top = 6.dp)) {
                listOf(RecordType.EXPENSE, RecordType.INCOME).forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(typeLabel(t)) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }

        if (editor.mode == "add" && level == 1) {
            val selected = roots.firstOrNull { it.id == parentId }
            Text(
                text = "父级：" + (selected?.let { it.name + "（" + typeLabel(it.type) + "）" }
                    ?: "未选，点下面列表选择"),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected == null) LedgerColors.ExpenseRed else LedgerColors.IncomeGreen,
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedTextField(
                value = parentQuery,
                onValueChange = { parentQuery = it },
                placeholder = { Text("搜索一级分类（可留空看全部）") },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
            )
            // 第 12 批：限高 + 惰性列表，10 个一级不再全量平铺 480dp。
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                items(matched, key = { it.id }) { r ->
                    FilterChip(
                        selected = parentId == r.id,
                        onClick = { parentId = r.id; error = null },
                        label = {
                            Text(
                                r.name + "（" + typeLabel(r.type) + "）",
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
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
