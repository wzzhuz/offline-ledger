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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.components.CategoryGlyph
import com.zwz.offlineledger.ui.components.CategoryHeaderBlue
import com.zwz.offlineledger.ui.theme.LedgerColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 第 55 批：独立「分类排序」页（一羽图 3 的组织方式）。
 *
 * - 一级横排 5 列，长按小手柄拖动（rememberReorderableLazyGridState 已在
 *   v3.1.0 README 查证存在，非凭记忆写 API）；
 * - 选中一级的二级收进圆角白卡，点按小手柄拖动（一维 LazyColumn）；
 * - draftOrder 与已落库 sortOrder 完全分离，拖动只改内存，
 *   点「保存」才由 ViewModel 一次性按三元组写回；返回 / 放弃零写库。
 *
 * 两个 Lazy 容器是 Column 的**兄弟**，不是嵌套：一级网格限高 240dp，
 * 二级列表 weight(1f) 占剩余空间，避免同方向嵌套滚动。
 */
@Composable
fun CategorySortScreen(type: RecordType, onBack: () -> Unit) {
    val vm = settingsVm()
    val state by vm.state.collectAsState()
    var selectedRootId by remember { mutableStateOf<Long?>(null) }
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
    val selected = selectedRootId?.takeIf { id -> roots.any { it.id == id } }
        ?: roots.firstOrNull()?.id
    val selectedRoot = roots.firstOrNull { it.id == selected }
    val children = state.sortDraftChildIds[selected].orEmpty().mapNotNull { id ->
        state.categories.firstOrNull { it.id == id && !it.isArchived && it.level == 1 }
    }

    val gridState = rememberLazyGridState()
    val reorderGrid = rememberReorderableLazyGridState(gridState) { from, to ->
        vm.moveDraftRootByIndex(from.index, to.index)
    }

    val listState = rememberLazyListState()
    val currentSelected = rememberUpdatedState(selected)
    val reorderList = rememberReorderableLazyListState(listState) { from, to ->
        val parent = currentSelected.value
        if (parent != null) vm.moveDraftChildByIndex(parent, from.index, to.index)
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
            IconButton(onClick = { back() }) {
                Icon(
                    Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Text(
                text = "分类排序",
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
            Text(
                text = "一级分类 · 长按手柄拖动",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            Spacer(Modifier.height(4.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                roots.forEach { root ->
                    val key = "sort-root-" + root.id
                    item(key = key) {
                        ReorderableItem(reorderGrid, key = key) { _ ->
                            val dragHandle = with(this) {
                                Modifier.longPressDraggableHandle()
                            }
                            val chosen = root.id == selected
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (chosen) CategoryHeaderBlue else Color(0xFFF2F3F7),
                                        RoundedCornerShape(12.dp)
                                    )
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
                                        color = if (chosen) Color.White else LedgerColors.Neutral
                                    )
                                }
                                CategoryGlyph(
                                    name = root.name,
                                    iconName = root.iconName,
                                    colorHex = root.colorHex,
                                    grayscale = true,
                                    size = 36
                                )
                                Text(
                                    text = root.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (chosen) Color.White else LedgerColors.Neutral,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { selectedRootId = root.id }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "「" + (selectedRoot?.name ?: "—") + "」的二级 · 点按手柄拖动",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            Spacer(Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFF7F8FC), RoundedCornerShape(16.dp))
                    .padding(8.dp)
            ) {
                if (children.isEmpty()) {
                    Text(
                        "该一级暂无二级分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        children.forEach { child ->
                            val key = "sort-child-" + child.id
                            item(key = key) {
                                ReorderableItem(reorderList, key = key) { _ ->
                                    val dragHandle = with(this) {
                                        Modifier.draggableHandle()
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = dragHandle.size(40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "≡",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = LedgerColors.Neutral
                                            )
                                        }
                                        CategoryGlyph(
                                            name = child.name,
                                            iconName = child.iconName,
                                            colorHex = child.colorHex,
                                            grayscale = true,
                                            size = 32
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = child.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
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
