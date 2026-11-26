package com.zwz.offlineledger.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.engine.ChartData.ChartSector
import com.zwz.offlineledger.engine.StatsLegend
import com.zwz.offlineledger.ui.components.CategoryGlyph
import com.zwz.offlineledger.ui.components.CategoryHeaderBlue
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 原生 Canvas 环形图 + 第 70 批一羽式**环外指示线** + 图例列表。
 *
 * 第 68/69 批两次把「环外悬浮百分比」列为明确未做，理由是禁用
 * `DrawScope.drawText` / `TextMeasurer`。第 70 批按使用者真机截图补上：
 * 引导折线用 Canvas `drawLine` / `drawCircle` 画，
 * 末端文字用**普通 Compose Text** 按 `onSizeChanged` 量到的尺寸叠加，
 * 两者都不碰 `drawText` / `TextMeasurer` / `FlowRow` / 实验性 API。
 *
 * 图例列表每行：彩色图标 + 名称 + 百分比 + ¥金额 + 灰色长条（宽度 = 占比）。
 * 整行可点且触摸区 >=40dp，与扇区共用同一个 onSectorClick（第 27 批契约）。
 */
@Composable
fun DonutChart(
    sectors: List<ChartSector>,
    totalCents: Long,
    centerLabel: String,
    onSectorClick: (Int) -> Unit,
    categoryIds: List<Long?> = emptyList(),
    modifier: Modifier = Modifier
) {
    val chartHeight = 250.dp
    val legendRows = StatsLegend.rows(sectors.map { it.slice }, totalCents)
    val density = LocalDensity.current
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // 指示线数据：纯 Compose 侧按量到的尺寸算，Canvas 与 Text 共用同一份，
    // MUST NOT 在 DrawScope 里回写 state（会引发重组循环）。
    //
    // 第 72 批按一羽真机图重做（第 70/71 批两次都没抓到真因）：
    //  第 70 批：标签 y 下推后折点 y 没同步 → 水平段变斜线。
    //  第 71 批：修了斜线，但**折点 x 固定在同一条竖线上** → 多个扇区外缘点
    //     被一起拉到同一竖线，形成扇形汇聚，真机图三/四仍交错。
    //  一羽的做法：折点是**每个扇区沿自身中点角径向延伸**得到的点，
    //     x 各不相同、沿环外缘弧分布；anchor→knee 是严格径向短线，
    //     从圆心发散、数学上永不相交；knee→(endX, knee.y) 严格水平，
    //     标签 y = knee.y（贴在线上方），顺序天然随角度有序。
    //  只剩「相邻折点 y 间隔 < minGap」时才按金额降序降级到 Top3
    //  （使用者给的退路），判据用**实际折点 y 的最小相邻间隔**，
    //  MUST NOT 再拿整个图表高度算容量（小扇区挤在窄角度里会误判）。
    val guides = remember(sectors, boxSize, totalCents) {
        val w = boxSize.width.toFloat()
        val h = boxSize.height.toFloat()
        if (w <= 0f || h <= 0f || sectors.isEmpty()) {
            emptyList()
        } else {
            val cx = w / 2f
            val cy = h / 2f
            val radius = min(cx, cy) * 0.60f
            val thickness = radius * 0.42f
            val outer = radius + thickness / 2f
            val lead = with(density) { 10.dp.toPx() }
            val radialR = outer + lead
            val edge = with(density) { 4.dp.toPx() }
            val minGap = with(density) { 18.dp.toPx() }

            // 保留全局 index，保证指示线颜色与 drawArc 完全同源。
            val raw = sectors.mapIndexed { i, sec ->
                val mid = ((sec.startAngle + sec.sweepAngle / 2f) * PI / 180f).toFloat()
                val c = cos(mid)
                val sn = sin(mid)
                RawGuide(
                    index = i,
                    sector = sec,
                    anchor = Offset(cx + outer * c, cy + outer * sn),
                    knee = Offset(cx + radialR * c, cy + radialR * sn),
                    left = c < 0f
                )
            }

            // 一组里相邻折点 y 的最小间隔；< minGap 就会挤 / 交错。
            fun minAdjacent(list: List<RawGuide>): Float {
                if (list.size < 2) return Float.POSITIVE_INFINITY
                val sorted = list.sortedBy { it.knee.y }
                return sorted.zipWithNext { a, b -> b.knee.y - a.knee.y }
                    .minOrNull() ?: Float.POSITIVE_INFINITY
            }

            val out = mutableListOf<GuideLine>()
            listOf(true, false).forEach { side ->
                var group = raw.filter { it.left == side }.sortedBy { it.knee.y }
                if (group.isEmpty()) return@forEach
                // 只在「真的挤」时降级到金额 Top3；极端到 Top3 仍挤则继续
                // 按金额从大到小保留，直到不交错或只剩 1 条（不交错优先）。
                if (minAdjacent(group) < minGap) {
                    group = group.sortedBy { it.index }.take(3).sortedBy { it.knee.y }
                    while (group.size > 1 && minAdjacent(group) < minGap) {
                        group = group.sortedBy { it.index }.dropLast(1).sortedBy { it.knee.y }
                    }
                }
                val endX = if (side) edge else w - edge
                group.forEach { g ->
                    out.add(
                        GuideLine(
                            index = g.index,
                            color = Color(ChartData.chartArgb(g.sector.slice.id, g.index).toInt()),
                            label = g.sector.slice.name + " " +
                                StatsLegend.percentText(g.sector.slice.cents, totalCents),
                            anchor = g.anchor,
                            knee = g.knee,
                            endX = endX,
                            y = g.knee.y,
                            left = side
                        )
                    )
                }
            }
            out
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .onSizeChanged { boxSize = it },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sectors) {
                        detectTapGestures { tap ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = tap.x - cx
                            val dy = tap.y - cy
                            val radius = min(cx, cy) * 0.60f
                            val thickness = radius * 0.42f
                            val dist = sqrt(dx * dx + dy * dy)
                            // 只有点落在环带内才算命中扇区
                            if (dist >= radius - thickness && dist <= radius + thickness) {
                                val deg = (atan2(
                                    dy.toDouble(), dx.toDouble()
                                ) * 180.0 / PI).toFloat()
                                ChartData.sectorHit(sectors, deg)?.let(onSectorClick)
                            }
                        }
                    }
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(cx, cy) * 0.60f
                val stroke = Stroke(width = radius * 0.42f)
                val topLeft = Offset(cx - radius, cy - radius)
                val arc = Size(radius * 2f, radius * 2f)
                if (sectors.isEmpty()) {
                    drawArc(
                        color = LedgerColors.Neutral.copy(alpha = 0.18f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arc, style = stroke
                    )
                } else {
                    sectors.forEachIndexed { i, s ->
                        drawArc(
                            color = Color(ChartData.chartArgb(s.slice.id, i).toInt()),
                            startAngle = s.startAngle,
                            sweepAngle = s.sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arc,
                            style = stroke
                        )
                    }
                    // 第 72 批：环外指示线 = **径向短线** + 严格水平折线 + 折点小圆点。
                    // anchor→knee 沿同一角度从圆心发散，永不相交；knee.y == g.y。
                    val lineWidth = 1.5f
                    val dot = 2.5f
                    guides.forEach { g ->
                        drawLine(
                            color = g.color, start = g.anchor, end = g.knee,
                            strokeWidth = lineWidth
                        )
                        drawLine(
                            color = g.color, start = g.knee,
                            end = Offset(g.endX, g.y), strokeWidth = lineWidth
                        )
                        drawCircle(color = g.color, radius = dot, center = g.knee)
                    }
                }
            }

            // 末端文字用普通 Compose Text 叠加，不用 DrawScope.drawText。
            guides.forEach { g ->
                Text(
                    text = g.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = g.color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .align(if (g.left) Alignment.TopStart else Alignment.TopEnd)
                        .absoluteOffset {
                            with(density) {
                                IntOffset(
                                    (if (g.left) 4.dp.toPx() else (-4).dp.toPx()).toInt(),
                                    // 文字贴在自己那条水平线上方，不压线、不盖环。
                                    (g.y - 16.dp.toPx()).toInt()
                                )
                            }
                        }
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = centerLabel, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "¥" + formatCents(totalCents),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // 一羽式图例列表（每行一项），与环同屏可见、允许纵向滚动。
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            legendRows.forEach { row ->
                val cid = categoryIds.getOrNull(row.index)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable { onSectorClick(row.index) }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cid != null) {
                            CategoryGlyph(
                                name = row.name,
                                iconName = "",
                                colorHex = "",
                                grayscale = false,
                                size = 32,
                                categoryId = cid
                            )
                        } else {
                            CategoryIcon(name = row.name, colorHex = null, size = 32)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = row.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = row.percentText,
                                style = MaterialTheme.typography.labelSmall,
                                color = LedgerColors.Neutral,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "¥" + formatCents(row.cents),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LedgerColors.ExpenseRed,
                            maxLines = 1
                        )
                    }
                    // 灰色长条：浅灰轨道 + 低饱和蓝填充，宽度 = 占比。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 42.dp)
                            .height(5.dp)
                            .background(
                                LedgerColors.SurfaceVariant,
                                androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(row.percent)
                                .height(5.dp)
                                .background(
                                    CategoryHeaderBlue.copy(alpha = 0.75f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 4.dp)
                        .background(LedgerColors.Neutral.copy(alpha = 0.10f))
                )
            }
        }
        if (sectors.isEmpty()) {
            Text(
                text = "本期没有支出记录",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** 布局中间量：环外缘锚点 + 径向折点 + 全局 index（颜色与 drawArc 同源）。 */
private class RawGuide(
    val index: Int,
    val sector: ChartSector,
    val anchor: Offset,
    val knee: Offset,
    val left: Boolean
)

/** 一条引导线的数据。knee 是径向折点，knee.y == y，水平段严格水平。 */
private class GuideLine(
    val index: Int,
    val color: Color,
    val label: String,
    val anchor: Offset,
    val knee: Offset,
    val endX: Float,
    val y: Float,
    val left: Boolean
)
