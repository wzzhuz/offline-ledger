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
    // 第 71 批修「连接线交错」：
    //  旧算法把标签 y 下推后**没同步更新折点 y**，所谓「水平折线」其实是斜线，
    //  多条必然交叉；且 last 从 10dp 起算会无谓下推顶部标签、又没有上界。
    //  新算法：① 每侧按环上锚点 y 排序**保序**；② 分配目标 y（最小间距 +
    //  整体平移进 [pad, h-pad]，绝不越界）；③ 折点 x 固定、y = 目标 y，
    //  水平段严格水平且彼此平行；④ 斜线段只从环外缘到折点，短且不跨线。
    //  极端到单侧放不下时才按使用者给的兜底只标金额 Top3。
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
            val lead = with(density) { 12.dp.toPx() }
            val edge = with(density) { 4.dp.toPx() }
            val pad = with(density) { 14.dp.toPx() }
            val minGap = with(density) { 20.dp.toPx() }
            val usable = h - 2f * pad
            // 单侧真正放得下的标签数；超出才降级（不是按分类数拍脑袋）。
            val capacity = (usable / minGap).toInt() + 1

            // 保留全局 index，保证指示线颜色与 drawArc 完全同源。
            val raw = sectors.mapIndexed { i, s ->
                val mid = ((s.startAngle + s.sweepAngle / 2f) * PI / 180f).toFloat()
                val c = cos(mid)
                val sn = sin(mid)
                RawGuide(
                    index = i,
                    sector = s,
                    anchor = Offset(cx + outer * c, cy + outer * sn),
                    left = c < 0f
                )
            }

            val out = mutableListOf<GuideLine>()
            listOf(true, false).forEach { side ->
                // 降级只在「真的放不下」时发生，且按原 index（金额降序）取 Top3。
                var group0 = raw.filter { it.left == side }
                if (group0.size > capacity) {
                    group0 = group0.sortedBy { it.index }.take(3)
                }
                // 保序：按环上锚点 y 排，标签顺序与扇区顺序一致 → 不交错。
                val group = group0.sortedBy { it.anchor.y }
                val n = group.size
                if (n == 0) return@forEach

                // ① 以锚点 y 为基础做最小间距下推（保序）
                val ys = FloatArray(n)
                var last = Float.NEGATIVE_INFINITY
                for (k in 0 until n) {
                    val desired = group[k].anchor.y
                    ys[k] = if (last == Float.NEGATIVE_INFINITY) desired
                    else maxOf(desired, last + minGap)
                    last = ys[k]
                }
                // ② 整体平移进可用范围（跨度 <= usable，不会挤到边界）
                val shift = when {
                    ys[0] < pad -> pad - ys[0]
                    ys[n - 1] > h - pad -> (h - pad) - ys[n - 1]
                    else -> 0f
                }
                for (k in 0 until n) ys[k] = (ys[k] + shift).coerceIn(pad, h - pad)

                val kneeX = if (side) cx - (outer + lead) else cx + (outer + lead)
                val endX = if (side) edge else w - edge
                for (k in 0 until n) {
                    val g = group[k]
                    val targetY = ys[k]
                    out.add(
                        GuideLine(
                            index = g.index,
                            color = Color(ChartData.chartArgb(g.sector.slice.id, g.index).toInt()),
                            label = g.sector.slice.name + " " +
                                StatsLegend.percentText(g.sector.slice.cents, totalCents),
                            anchor = g.anchor,
                            knee = Offset(kneeX, targetY),
                            endX = endX,
                            y = targetY,
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
                    // 第 71 批：环外指示线 = 短斜引线 + **严格水平**折线 + 折点小圆点。
                    // knee.y == g.y，水平段彼此平行，MUST NOT 再出现斜的「水平段」。
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
                                    (g.y - 8.dp.toPx()).toInt()
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
                                Color(0xFFEDEFF5),
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

/** 布局中间量：环外缘锚点 + 全局 index（颜色与 drawArc 同源）。 */
private class RawGuide(
    val index: Int,
    val sector: ChartSector,
    val anchor: Offset,
    val left: Boolean
)

/** 一条引导线的数据，Canvas 画线 / Text 定位共用。knee.y == y，水平段严格水平。 */
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
