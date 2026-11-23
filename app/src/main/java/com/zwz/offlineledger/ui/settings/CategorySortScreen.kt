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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.components.CategoryGlyph
import com.zwz.offlineledger.ui.components.CategoryHeaderBlue
import com.zwz.offlineledger.ui.theme.LedgerColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * 第 56 批：独立「分类排序」页，按一羽图 3 做**两态互斥**。
 *
 * - 一级态：只渲染 5 列一级 `LazyVerticalGrid`，二级区折叠 / 不渲染；
 *   容器内无 span / header，`from.index` 直接对应 `draftOrder` 下标。
 * - 二级态：外层一级区改非惰性 `Column + Row(weight)` 静态展示并冻结；
 *   圆角白卡插在选中一级所在行之后，卡内用**第二套**
 *   `rememberReorderableLazyGridState` 拖 5 列等宽二级图标格。
 *
 * 两套 grid state 各管各段 `draftOrder`，同一时刻只有一个可拖容器，互不干涉。
 * `onMove` 在返回前由 ViewModel 同步 `_state.update`（库 FAQ：异步会闪烁）；
 * 保存 / 放弃语义与第 55 批一致：`draftOrder` 与已落库 `sortOrder` 完全分离。
 */
@Composable
fun CategorySortScreen(type: RecordType, onBack: () -> Unit) {
    val vm = settingsVm()
    val state by vm.state.collectAsState()
    // null = 一级态；非 null = 二级态（即使用者观察到的「拖一级时二级折叠」）。
    var expandedRootId by remember { mutableStateOf<Long?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // 本页 ViewModel 是独立实例，init 里的 loadCategories 是异步的；
    // 等 categories 真正到位后再初始化 draft，避免首帧空白。
    LaunchedEffect(type, state.categories.size) {
        if (state.categories.isNotEmpty() &&
            state.sortDraftRootIds.isEmpty() &&
            !state.sortDirty
        ) {
            vm.beginSort(type)
        }
    }

    val roots = state.sortDraftRootIds.mapNotNull { id ->
        state.categories.firstOrNull {
            it.id == id && !it.isArchived && it.level == 0 && it.type == type
        }
    }
    val activeRootId = expandedRootId?.takeIf { id -> roots.any { it.id == id } }
    val selectedRoot = roots.firstOrNull { it.id == activeRootId }
    val children = activeRootId?.let { pid ->
        state.sortDraftChildIds[pid].orEmpty().mapNotNull { id ->
            state.categories.firstOrNull { it.id == id && !it.isArchived && it.level == 1 }
        }
    }.orEmpty()

    val rootGridState = rememberLazyGridState()
    val reorderRoots = rememberReorderableLazyGridState(rootGridState) { from, to ->
        vm.moveDraftRootByIndex(from.index, to.index)
    }

    val childGridState = rememberLazyGridState()
    val currentParent = rememberUpdatedState(activeRootId)
    val reorderChildren = rememberReorderableLazyGridState(childGridState) { from, to ->
        currentParent.value?.let { parent ->
            vm.moveDraftChildByIndex(parent, from.index, to.index)
        }
    }

    fun save() {
        vm.saveSort(type) { onBack() }
    }

    fun back() {
        if (state.sortDirty) confirmDiscard = true
        else {
            vm.discardSort()
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CategoryHeaderBlue)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                // 二级态先收起回到一级态，不直接退出页面。
                if (expandedRootId != null) expandedRootId = null else back()
            }) {
                Icon(
                    Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Text(
                text = if (expandedRootId == null) "分类排序 · 一级" else "分类排序 · 二级",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { save() }) { Text("保存", color = Color.White) }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (expandedRootId == null) {
                Text(
                    text = "一级分类 · 长按手柄拖动排序",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral
                )
                Text(
                    text = "点一级名称可展开该组二级排序；一级拖动时二级折叠。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
                Spacer(Modifier.height(4.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = rootGridState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    roots.forEachIndexed { idx, root ->
                        val key = "sort-root-" + root.id
                        item(key = key) {
                            ReorderableItem(reorderRoots, key = key) { _ ->
                                val dragHandle = with(this) {
                                    Modifier.longPressDraggableHandle()
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Color(0xFFF2F3F7),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .semantics {
                                            customActions = buildList {
                                                if (idx > 0) {
                                                    add(
                                                        CustomAccessibilityAction("上移") {
                                                            vm.moveDraftRootByIndex(idx, idx - 1)
                                                            true
                                                        }
                                                    )
                                                }
                                                if (idx < roots.lastIndex) {
                                                    add(
                                                        CustomAccessibilityAction("下移") {
                                                            vm.moveDraftRootByIndex(idx, idx + 1)
                                                            true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = dragHandle.size(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "≡",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = LedgerColors.Neutral
                                        )
                                    }
                                    CategoryGlyph(
                                        name = root.name,
                                        iconName = root.iconName,
                                        colorHex = root.colorHex,
                                        grayscale = true,
                                        size = 36,
                                        categoryId = root.id
                                    )
                                    Text(
                                        text = root.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LedgerColors.Neutral,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable {
                                            expandedRootId = root.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 二级态：一级区冻结为静态网格，白卡插在选中行之后。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "「" + (selectedRoot?.name ?: "—") + "」的二级 · 点按手柄拖动",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { expandedRootId = null }) {
                        Text("收起，排一级")
                    }
                }
                Text(
                    text = "一级区已冻结；点其他一级可切换分组，两态顺序互不影响。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
                Spacer(Modifier.height(4.dp))

                // 外层非惰性 Column：MUST NOT 滚动，MUST NOT 嵌进另一个惰性网格。
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    roots.chunked(5).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { root ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    StaticRootCell(
                                        root = root,
                                        chosen = root.id == activeRootId,
                                        onClick = { expandedRootId = root.id }
                                    )
                                }
                            }
                        }
                        val parentId = activeRootId
                        if (parentId != null && row.any { it.id == parentId }) {
                            Spacer(Modifier.height(6.dp))
                            val pid: Long = parentId
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FC), RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "二级分类 · 点按小手柄拖动排序",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LedgerColors.Neutral
                                )
                                if (children.isEmpty()) {
                                    Text(
                                        "该一级暂无二级分类",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LedgerColors.Neutral
                                    )
                                } else {
                                    // 第二套 grid state：与一级态互斥，互不干涉。
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(5),
                                        state = childGridState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 180.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        children.forEachIndexed { idx, child ->
                                            val key = "sort-child-" + child.id
                                            item(key = key) {
                                                ReorderableItem(reorderChildren, key = key) { _ ->
                                                    val dragHandle = with(this) {
                                                        Modifier.draggableHandle()
                                                    }
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                Color.White,
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .semantics {
                                                                customActions = buildList {
                                                                    if (idx > 0) {
                                                                        add(
                                                                            CustomAccessibilityAction("上移") {
                                                                                vm.moveDraftChildByIndex(pid, idx, idx - 1)
                                                                                true
                                                                            }
                                                                        )
                                                                    }
                                                                    if (idx < children.lastIndex) {
                                                                        add(
                                                                            CustomAccessibilityAction("下移") {
                                                                                vm.moveDraftChildByIndex(pid, idx, idx + 1)
                                                                                true
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            .padding(vertical = 4.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Box(
                                                            modifier = dragHandle.size(26.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "≡",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = LedgerColors.Neutral
                                                            )
                                                        }
                                                        CategoryGlyph(
                                                            name = child.name,
                                                            iconName = child.iconName,
                                                            colorHex = child.colorHex,
                                                            grayscale = true,
                                                            size = 30,
                                                            categoryId = child.id
                                                        )
                                                        Text(
                                                            text = child.name,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "长按拖动一级，点按拖动二级",
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { save() }, modifier = Modifier.weight(1f)) { Text("保存") }
                TextButton(onClick = { back() }, modifier = Modifier.weight(1f)) { Text("放弃") }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃修改") },
            text = { Text("排序调整尚未保存，返回后顺序不会写入账本。确定放弃吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.discardSort()
                    onBack()
                }) { Text("确定放弃", color = LedgerColors.ExpenseRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续调整") }
            }
        )
    }
}

/** 二级态的静态一级格：不挂拖柄、不可拖，点击只切换当前分组。 */
@Composable
private fun StaticRootCell(
    root: CategoryEntity,
    chosen: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (chosen) CategoryHeaderBlue else Color(0xFFF2F3F7),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CategoryGlyph(
            name = root.name,
            iconName = root.iconName,
            colorHex = root.colorHex,
            grayscale = true,
            size = 32,
            categoryId = root.id
        )
        Text(
            text = root.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (chosen) Color.White else LedgerColors.Neutral,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
