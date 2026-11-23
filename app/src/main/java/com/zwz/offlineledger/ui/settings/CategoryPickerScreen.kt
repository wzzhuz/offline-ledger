package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.components.CategoryHeaderBlue
import com.zwz.offlineledger.ui.components.CategoryIconGrid
import com.zwz.offlineledger.ui.components.CategoryTile
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 55 批：借一羽「添加二级分类」页的版式做分类选择页（方案 A：只借版式）。
 *
 * 顶部上下文（父级 + 收支类型）+ 名称输入 + 左侧分组导航 + 右侧等宽网格 +
 * 顶部搜索 +「继续添加」。从列表页某级的「添加子类」进入时父级与类型预填，
 * MUST NOT 要求用户重选层级与搜索父级。
 *
 * 同版式用于「停用项管理」批量启用停用扩展项；
 * MUST NOT 改成「从预设分类库挑一个分类加进来」，不碰清单 ID 规则与备份语义。
 */
@Composable
fun CategoryPickerScreen(
    parentId: Long?,
    type: RecordType,
    mode: String,
    onBack: () -> Unit
) {
    val vm = settingsVm()
    val state by vm.state.collectAsState()
    val disabledMode = mode == "disabled"

    var selectedParent by remember { mutableStateOf(parentId) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    val roots = state.categories.filter {
        it.level == 0 && !it.isArchived && it.type == type
    }.sortedBy { it.sortOrder }

    val parentName = roots.firstOrNull { it.id == selectedParent }?.name
    val title = if (disabledMode) {
        "停用项管理 › " + pickerTypeLabel(type)
    } else if (parentName != null) {
        "添加到 " + parentName + " › " + pickerTypeLabel(type)
    } else {
        "新增分类 › " + pickerTypeLabel(type)
    }

    val pool: List<CategoryEntity> = when {
        disabledMode -> state.categories.filter {
            !it.isArchived && it.type == type && !it.enabled &&
                (selectedParent == null || it.parentId == selectedParent || it.id == selectedParent)
        }
        selectedParent == null -> roots
        else -> state.categories.filter {
            it.level == 1 && !it.isArchived && it.type == type &&
                it.parentId == selectedParent
        }.sortedBy { it.sortOrder }
    }
    val visible = pool.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    val tiles = visible.map { c ->
        CategoryTile(
            id = c.id,
            name = c.name,
            iconName = c.iconName,
            colorHex = c.colorHex,
            enabled = c.enabled
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CategoryHeaderBlue)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!disabledMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; vm.clearPickerError() },
                        placeholder = {
                            Text("分类名称", style = MaterialTheme.typography.bodyMedium)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (vm.submitPicker(name, type, selectedParent)) name = ""
                        }
                    ) { Text("继续添加") }
                }
                if (state.pickerError.isNotEmpty()) {
                    Text(
                        text = state.pickerError,
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.ExpenseRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = if (selectedParent == null) {
                        "先在左侧选一个一级作为父级，或直接新增一级分类。"
                    } else {
                        "右侧为该一级已有的二级；点格子可带入名称，改一下即可新建。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    text = "点下方格子即可重新启用；历史流水归属不变。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索分类", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.width(96.dp).fillMaxSize()
            ) {
                item {
                    PickerNavItem(
                        text = if (disabledMode) "全部" else "＋ 新增一级",
                        selected = selectedParent == null,
                        onClick = { selectedParent = null }
                    )
                }
                items(roots, key = { it.id }) { r ->
                    PickerNavItem(
                        text = r.name,
                        selected = selectedParent == r.id,
                        onClick = { selectedParent = r.id }
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (tiles.isEmpty()) {
                    Text(
                        text = if (query.isBlank()) "暂无可显示的分类" else "没有匹配「$query」的分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    CategoryIconGrid(
                        tiles = tiles,
                        columns = 4,
                        onTile = { tile ->
                            if (disabledMode) {
                                vm.setCategoryEnabled(tile.id, true)
                            } else if (selectedParent == null) {
                                selectedParent = tile.id
                            } else {
                                name = tile.name
                                vm.clearPickerError()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerNavItem(text: String, selected: Boolean, onClick: () -> Unit) {
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

private fun pickerTypeLabel(type: RecordType): String =
    if (type == RecordType.EXPENSE) "支出" else "收入"
