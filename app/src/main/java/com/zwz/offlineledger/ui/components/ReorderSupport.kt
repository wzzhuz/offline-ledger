package com.zwz.offlineledger.ui.components

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 第 17 批：二级分类 chips 的自适应换行布局。
 *
 * 第 20 批说明：第 15-17 批的自研拖拽手势层（ReorderDragState /
 * rememberReorderDrag / ReorderHandle / ReorderBounds）已整体删除，改由
 * sh.calvin.reorderable:reorderable:3.1.0 接管一维列表拖拽；本文件只保留
 * WrapRow —— 二级是 chips 网格，不属于库的一维列表模型，仍按实测宽度换行，
 * 排序沿用操作弹层里的上移 / 下移（同时是 TalkBack 无障碍兜底）。
 *
 * 显式 Alignment.Start、layout() 占满父宽、行尾留白固定留在右侧。
 */
@androidx.compose.runtime.Composable
fun WrapRow(
    spacing: Dp = 6.dp,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val avail = if (constraints.hasBoundedWidth) constraints.maxWidth else 1_000_000
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val positions = arrayOfNulls<Pair<Int, Int>>(placeables.size)
        val rows = ArrayList<ArrayList<Int>>()
        var cur = ArrayList<Int>()
        var x = 0
        placeables.forEachIndexed { i, p ->
            if (cur.isNotEmpty() && x + p.width > avail) {
                rows.add(cur)
                cur = ArrayList()
                x = 0
            }
            cur.add(i)
            x += p.width + gap
        }
        if (cur.isNotEmpty()) rows.add(cur)
        var y = 0
        var widest = 0
        rows.forEach { row ->
            var rowW = 0
            var rowH = 0
            row.forEach { i ->
                rowW += placeables[i].width + gap
                rowH = max(rowH, placeables[i].height)
            }
            rowW -= gap
            widest = max(widest, rowW)
            val free = (avail - rowW).coerceAtLeast(0)
            val startX = when {
                horizontalAlignment == Alignment.CenterHorizontally -> free / 2
                horizontalAlignment == Alignment.End -> free
                else -> 0
            }
            var cx = startX
            row.forEach { i ->
                positions[i] = cx to y
                cx += placeables[i].width + gap
            }
            y += rowH + gap
        }
        val totalHeight = (y - gap).coerceAtLeast(0)
        val outWidth = if (constraints.hasBoundedWidth) constraints.maxWidth
        else widest.coerceAtLeast(0)
        layout(outWidth, totalHeight) {
            placeables.forEachIndexed { i, p ->
                val pos = positions[i] ?: (0 to 0)
                p.placeRelative(pos.first, pos.second)
            }
        }
    }
}
