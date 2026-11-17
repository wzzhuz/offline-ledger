package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlin.math.max

/**
 * 第 16 批：全站统一排序交互的单一来源。
 *
 * 零新增依赖、零表结构变更。与 Flutter `ReorderableListView` 的差别如实记录：
 * 这里是「拖动跨过一行的中点即实时重排列表」（其余项立即让位），
 * 不是连续的像素级让位动画；被拖项带阴影与 zIndex 浮起。
 *
 * `WrapRow` 用稳定的 [Layout] API 按实测宽度换行，MUST NOT 用 `chunked(n)`
 * 固定每行个数（短 chip 右侧留白），也刻意不引入实验性 FlowRow 注解。
 */
@Composable
fun WrapRow(
    spacing: Dp = 6.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val positions = ArrayList<Pair<Int, Int>>(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var widest = 0
        placeables.forEach { p ->
            if (x > 0 && x + p.width > constraints.maxWidth) {
                x = 0
                y += rowHeight + gap
                rowHeight = 0
            }
            positions.add(x to y)
            x += p.width + gap
            rowHeight = max(rowHeight, p.height)
            widest = max(widest, x - gap)
        }
        val totalHeight = y + rowHeight
        layout(
            widest.coerceAtMost(constraints.maxWidth).coerceAtLeast(0),
            totalHeight.coerceAtLeast(0)
        ) {
            placeables.forEachIndexed { i, p ->
                val pos = positions[i]
                p.placeRelative(pos.first, pos.second)
            }
        }
    }
}

/** 一次拖拽会话的可观察状态。松手才调用 onCommit，拖动中只改本地顺序。 */
class ReorderDragState internal constructor(
    ids: List<Long>,
    private val rowPx: Float,
    private val onCommit: (List<Long>) -> Unit
) {
    var orderedIds by mutableStateOf(ids)
        private set
    var draggingId by mutableStateOf<Long?>(null)
        private set
    var remainder by mutableStateOf(0f)
        private set
    private var accumulated = 0f
    private var snapshot = ids

    fun start(id: Long) {
        snapshot = orderedIds
        draggingId = id
        accumulated = 0f
        remainder = 0f
    }

    /** 每跨过一行就立即重排：调用方列表随之刷新，其余项实时让位。 */
    fun drag(dy: Float) {
        if (draggingId == null || rowPx <= 0f) return
        accumulated += dy
        val steps = (accumulated / rowPx).toInt()
        if (steps != 0) {
            val id = draggingId
            val from = orderedIds.indexOf(id)
            if (from >= 0) {
                val to = (from + steps).coerceIn(0, orderedIds.lastIndex)
                if (to != from) {
                    orderedIds = orderedIds.toMutableList().also {
                        it.add(to, it.removeAt(from))
                    }
                }
            }
            accumulated -= steps * rowPx
        }
        remainder = accumulated
    }

    fun end(commit: Boolean) {
        if (draggingId == null) return
        draggingId = null
        accumulated = 0f
        remainder = 0f
        if (commit) onCommit(orderedIds) else orderedIds = snapshot
    }

    /** 挂在拖手柄（或 chip）上；长按才接管，短按仍可触发点击。 */
    fun handleModifier(id: Long): Modifier = Modifier.pointerInput(id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { start(id) },
            onDrag = { change, delta -> change.consume(); drag(delta.y) },
            onDragEnd = { end(true) },
            onDragCancel = { end(false) }
        )
    }

    /** 挂在被重排的 item 根上；配合 LazyColumn 的 animateItem() 让位。 */
    fun itemModifier(id: Long): Modifier = Modifier
        .zIndex(if (draggingId == id) 1f else 0f)
        .graphicsLayer {
            if (draggingId == id) {
                shadowElevation = 6.dp.toPx()
                alpha = 0.96f
                translationY = remainder
            }
        }
}

@Composable
fun rememberReorderDrag(
    ids: List<Long>,
    rowHeightDp: Int = 56,
    onCommit: (List<Long>) -> Unit
): ReorderDragState {
    val rowPx = with(LocalDensity.current) { rowHeightDp.dp.toPx() }
    val key = ids.joinToString(",")
    val currentCommit = rememberUpdatedState(onCommit)
    return remember(key) { ReorderDragState(ids, rowPx) { currentCommit.value(it) } }
}

@Composable
fun ReorderHandle(state: ReorderDragState, id: Long) {
    Box(
        modifier = state.handleModifier(id).size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "≡",
            style = MaterialTheme.typography.titleMedium,
            color = LedgerColors.Neutral
        )
    }
}
