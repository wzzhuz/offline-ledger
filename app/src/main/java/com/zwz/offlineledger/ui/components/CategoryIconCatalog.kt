package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.engine.CategoryManagementRules
import com.zwz.offlineledger.ui.theme.LedgerColors

/** 分类管理三页的蓝底顶栏 / 主按钮共用色，避免三处各写一个蓝。 */
val CategoryHeaderBlue: Color = Color(0xFF5B7CFA)

/**
 * 第 55 批（方案 A）：分类管理三页图标的唯一出口。
 *
 * 图标资源尚未由使用者确认 / 提供，本批目录为空，三页一律回退
 * `CategoryIcon` 的中性圆 + 中文首字。MUST NOT 用 `iconName` 字符串
 * 在运行时反射或查表映射到 ImageVector（活文档 T14：任一图标名不存在
 * 会让整份映射表编译失败）。
 *
 * 将来补齐资源时只改本文件的 `hasVector` / `tintOrNull` 为编译期显式常量表，
 * 三个页面零改动；MUST NOT 复制第三方记账 App 的视觉资产。
 * 图标配色单独一套（tintOrNull），MUST NOT 改写参与统计扇区取色的 colorHex。
 */
object CategoryIconCatalog {

    /** 编译期显式常量表（方案 A 为空目录）。补齐资源后改成 when(iconName) 分支。 */
    @Suppress("UNUSED_PARAMETER")
    fun hasVector(iconName: String): Boolean = false

    /** 方案 A 无彩色可去饱和；保留出口供资源补齐后按 grayscale 降饱和。 */
    @Suppress("UNUSED_PARAMETER")
    fun tintOrNull(iconName: String, grayscale: Boolean): Color? = null
}

data class CategoryTile(
    val id: Long,
    val name: String,
    val iconName: String = "",
    val colorHex: String = "#888888",
    val enabled: Boolean = true,
    val isAdd: Boolean = false
)

/**
 * 分类管理三页共用的图标格。方案 A 全部回退中性圆 + 首字；
 * grayscale 参数保留给排序页（资源补齐后一羽式灰阶降噪在这里生效）。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun CategoryGlyph(
    name: String,
    iconName: String,
    colorHex: String,
    grayscale: Boolean,
    size: Int = 40
) {
    // 方案 A：目录为空，一律回退。补齐资源后在此按 iconName 取显式常量并上色。
    CategoryIcon(name = name, colorHex = colorHex, size = size)
}

/**
 * 固定列数等宽图标网格：每格 weight(1f)、固定格高、gutter 均匀、文字单行省略。
 * MUST NOT 用 LazyVerticalGrid 嵌进 LazyColumn.item {}（同方向嵌套滚动 + 高度不确定），
 * MUST NOT 用 FlowRow + ExperimentalLayoutApi（活文档 T12）。
 */
@Composable
fun CategoryIconGrid(
    tiles: List<CategoryTile>,
    onTile: (CategoryTile) -> Unit,
    columns: Int = 5,
    grayscale: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rows = CategoryManagementRules.gridCells(tiles.size, columns).chunked(columns)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val tile = index?.let { tiles[it] }
                        if (tile != null) {
                            CategoryTileCell(tile, grayscale) { onTile(tile) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTileCell(
    tile: CategoryTile,
    grayscale: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (tile.isAdd) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(LedgerColors.Neutral.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "＋",
                        style = MaterialTheme.typography.titleMedium,
                        color = LedgerColors.Neutral
                    )
                }
            } else {
                CategoryGlyph(
                    name = tile.name,
                    iconName = tile.iconName,
                    colorHex = tile.colorHex,
                    grayscale = grayscale,
                    size = 40
                )
            }
            // 停用改小角标，MUST NOT 再拼进名称把格子撑破。
            if (!tile.enabled && !tile.isAdd) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(LedgerColors.ExpenseRed, CircleShape)
                )
            }
        }
        Text(
            text = tile.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
    }
}
