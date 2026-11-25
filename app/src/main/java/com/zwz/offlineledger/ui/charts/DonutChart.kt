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
            val lead = with(density) { 14.dp.toPx() }
            val edge = with(density) { 6.dp.toPx() }
            val list = sectors.mapIndexed { i, s ->
                val mid = ((s.startAngle + s.sweepAngle / 2f) * PI / 180f).toFloat()
                val c = cos(mid)
                val sn = sin(mid)
                val p1 = Offset(cx + outer * c, cy + outer * sn)
                val p2 = Offset(cx + (outer + lead) * c, cy + (outer + lead) * sn)
                val left = c < 0f
                GuideLine(
                    index = i,
                    color = Color(ChartData.chartArgb(s.slice.id, i).toInt()),
                    label = s.slice.name + " " +
                        StatsLegend.percentText(s.slice.cents, totalCents),
                    p1 = p1,
                    p2 = p2,
                    endX = if (left) edge else w - edge,
                    y = p2.y,
                    left = left
                )
            }
            // 同侧按 y 排开，避免几条线叠在一起（一羽截图也是上下错开）。
            val gap = with(density) { 22.dp.toPx() }
            listOf(true, false).forEach { side ->
                var last = with(density) { 10.dp.toPx() }
                list.filter { it.left == side }.sortedBy { it.y }.forEach { g ->
                    if (g.y < last + gap) g.y = last + gap
                    last = g.y
                }
            }
            list
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
                    // 第 70 批：环外指示线 = 斜引线 + 水平折线 + 端点小圆点。
                    val lineWidth = 1.5f
                    val dot = 2.5f
                    guides.forEach { g ->
                        drawLine(
                            color = g.color, start = g.p1, end = g.p2,
                            strokeWidth = lineWidth
                        )
                        drawLine(
                            color = g.color, start = g.p2,
                            end = Offset(g.endX, g.y), strokeWidth = lineWidth
                        )
                        drawCircle(color = g.color, radius = dot, center = g.p2)
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

/** 一条引导线的数据，Canvas 画线 / Text 定位共用。y 可被去重叠逻辑下推。 */
private class GuideLine(
    val index: Int,
    val color: Color,
    val label: String,
    val p1: Offset,
    val p2: Offset,
    val endX: Float,
    var y: Float,
    val left: Boolean
)
