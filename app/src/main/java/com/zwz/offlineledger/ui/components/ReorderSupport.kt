package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * 第 17 批：全站统一排序交互的单一来源（零新增依赖、零表结构变更）。
 *
 * - `WrapRow` 显式 `horizontalAlignment`（默认 [Alignment.Start]）、`layout()` 占满
 *   父宽、每行从 0 起排；行尾留白固定留在右侧，短名行不再被父级对齐策略推到中间。
 * - 拖拽期间通过 `onDraggingChange` 让页面禁用父滚动，解决「上下移动同时触发
 *   屏幕滚动 → 子拖拽收到 onDragCancel」；配套 `edgeDirection` + `onEdgeScroll`
 *   做边缘自动滚动，MUST NOT 只禁不滚（多账户会拖不到屏外）。
 * - `rowPx` 由 `rowMeasureModifier()` 的 `onSizeChanged` 实测写入，
 *   替掉 56/40/44 三个估算；MUST 挂在单行根上，MUST NOT 挂在含展开区的整块 item 上。
 * - 长按阶段仍靠子级 `pointerInput` 的 Main-pass `consume()` + 父滚动 `enabled=false`；
 *   MUST NOT 在 Initial pass 消费 down —— `awaitFirstDown(requireUnconsumed = true)`
 *   会因此收不到按下事件，拖拽直接失效（第 17 批 design 三级回退第 2 条）。
 *
 * 与 Flutter `ReorderableListView` 的差距如实记录：这里是「拖动跨过一行即实时
 * 重排」，不是连续像素级补间动画；本 BOM 无 `animateItem`。
 */
@Composable
fun WrapRow(
    spacing: Dp = 6.dp,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit
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
            // 显式左对齐：短行剩余空间全部留在右侧，不交给父级 alignment 猜。
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

/** 可滚动列表容器的可见边界（root 坐标 px），由页面在 onGloballyPositioned 里写入。 */
class ReorderBounds {
    var topPx: Float = 0f
    var bottomPx: Float = 0f
}

/** 一次拖拽会话的可观察状态。松手才调用 onCommit，拖动中只改本地顺序。 */
class ReorderDragState internal constructor(
    ids: List<Long>,
    initialRowPx: Float,
    private val bounds: ReorderBounds,
    private val edgeZonePx: Float,
    private val onDraggingChange: (Boolean) -> Unit,
    private val onCommit: (List<Long>) -> Unit
) {
    var orderedIds by mutableStateOf(ids)
        private set
    var draggingId by mutableStateOf<Long?>(null)
        private set
    var remainder by mutableStateOf(0f)
        private set
    /** -1 = 上边缘自动滚 / 0 = 不滚 / 1 = 下边缘自动滚。 */
    var edgeDirection by mutableStateOf(0)
        private set
    private var rowPx = initialRowPx
    private var accumulated = 0f
    private var snapshot = ids
    private val handleTops = HashMap<Long, Float>()

    /** 实测行高（px）。多处根写同一个 state，取最后一次有效值。 */
    fun reportRowHeight(px: Float) {
        if (px > 0f) rowPx = px
    }

    fun start(id: Long) {
        snapshot = orderedIds
        draggingId = id
        accumulated = 0f
        remainder = 0f
        edgeDirection = 0
        onDraggingChange(true)
    }

    /** 每跨过一行就立即重排：调用方列表随之刷新，其余项实时让位。 */
    fun drag(id: Long, dy: Float, fingerRootY: Float) {
        if (draggingId == null || rowPx <= 0f) return
        accumulated += dy
        val steps = (accumulated / rowPx).toInt()
        if (steps != 0) {
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
        // 手指进入容器上下边缘区 → 页面按帧 scrollBy；用 root 坐标近似，48dp 为粗判定。
        val ready = bounds.bottomPx > bounds.topPx
        edgeDirection = when {
            ready && fingerRootY < bounds.topPx + edgeZonePx -> -1
            ready && fingerRootY > bounds.bottomPx - edgeZonePx -> 1
            else -> 0
        }
    }

    fun end(commit: Boolean) {
        if (draggingId == null) return
        draggingId = null
        accumulated = 0f
        remainder = 0f
        edgeDirection = 0
        onDraggingChange(false)
        if (commit) onCommit(orderedIds) else orderedIds = snapshot
    }

    /** 挂在拖手柄（或 chip）上；长按才接管，短按仍可触发点击。 */
    fun handleModifier(id: Long): Modifier = Modifier
        .onGloballyPositioned { handleTops[id] = it.positionInRoot().y }
        .pointerInput(id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { start(id) },
                onDrag = { change, delta ->
                    change.consume()
                    val rootY = (handleTops[id] ?: 0f) + change.position.y
                    drag(id, delta.y, rootY)
                },
                onDragEnd = { end(true) },
                onDragCancel = { end(false) }
            )
        }

    /** 挂在被重排的 item 根上；被拖项浮起并跟手。 */
    fun itemModifier(id: Long): Modifier = Modifier
        .zIndex(if (draggingId == id) 1f else 0f)
        .graphicsLayer {
            if (draggingId == id) {
                shadowElevation = 6.dp.toPx()
                alpha = 0.96f
                translationY = remainder
            }
        }

    /** 挂在单行根上实测行高；MUST NOT 挂在含展开区 / 多行 chips 的整块 item 上。 */
    fun rowMeasureModifier(): Modifier = Modifier.onSizeChanged {
        reportRowHeight(it.height.toFloat())
    }
}

@Composable
fun rememberReorderDrag(
    ids: List<Long>,
    rowHeightDp: Int = 56,
    bounds: ReorderBounds = ReorderBounds(),
    onDraggingChange: (Boolean) -> Unit = {},
    onEdgeScroll: (Float) -> Unit = {},
    onCommit: (List<Long>) -> Unit
): ReorderDragState {
    val density = LocalDensity.current
    val rowPx = with(density) { rowHeightDp.dp.toPx() }
    val edgeZonePx = with(density) { 48.dp.toPx() }
    val stepPx = with(density) { 10.dp.toPx() }
    val key = ids.joinToString(",")
    val currentCommit = rememberUpdatedState(onCommit)
    val currentDragging = rememberUpdatedState(onDraggingChange)
    val currentEdge = rememberUpdatedState(onEdgeScroll)
    val state = remember(key) {
        ReorderDragState(
            ids, rowPx, bounds, edgeZonePx,
            onDraggingChange = { currentDragging.value(it) },
            onCommit = { currentCommit.value(it) }
        )
    }
    // 边缘自动滚动：只有 edgeDirection != 0 的那个 state 会跑；手指停在边缘也持续滚。
    LaunchedEffect(state.edgeDirection) {
        val dir = state.edgeDirection
        if (dir == 0) return@LaunchedEffect
        while (true) {
            currentEdge.value(dir * stepPx)
            delay(16)
        }
    }
    return state
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
