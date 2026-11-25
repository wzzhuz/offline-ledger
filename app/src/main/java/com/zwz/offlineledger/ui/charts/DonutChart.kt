package com.zwz.offlineledger.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.engine.ChartData.ChartSector
import com.zwz.offlineledger.engine.StatsLegend
import com.zwz.offlineledger.ui.components.CategoryGlyph
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 原生 Canvas 环形图 + 第 68 批一羽式图例列表。
 *
 * 刻意不用 DrawScope.drawText / TextMeasurer，也不用 FlowRow /
 * BoxWithConstraints（ExperimentalLayoutApi / 版本相关 API）：
 * 环外悬浮百分比需要 Canvas 文本测量或复杂 offset 定位，
 * 本批改由**图例列表每行**承载百分比（见 change design 2.2）。
 *
 * 图例整行可点且触摸区 >=40dp，与扇区共用同一个 onSectorClick（第 27 批契约）。
 * 分类维度传 categoryIds 显示彩色 Tabler 图标，缺项 / 其他维度回退中性圆 + 首字。
 * 二级展开由 expandableIds / expandedChildren 驱动，子行直接调 onChildClick。
 */
@Composable
fun DonutChart(
    sectors: List<ChartSector>,
    totalCents: Long,
    centerLabel: String,
    onSectorClick: (Int) -> Unit,
    categoryIds: List<Long?> = emptyList(),
    expandableIds: Set<Long> = emptySet(),
    expandedId: Long? = null,
    onExpandToggle: (Long) -> Unit = {},
    expandedChildren: List<Triple<Long, String, Long>> = emptyList(),
    onChildClick: (Long, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val chartHeight = 210.dp
    val legendRows = StatsLegend.rows(sectors.map { it.slice }, totalCents)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(chartHeight),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .pointerInput(sectors) {
                        detectTapGestures { tap ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = tap.x - cx
                            val dy = tap.y - cy
                            val radius = min(cx, cy) * 0.78f
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
                val radius = min(cx, cy) * 0.78f
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
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = centerLabel, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "¥" + formatCents(totalCents),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // 第 68 批：一羽式图例列表（每行一项），与环同屏可见、允许纵向滚动。
        // 第 27 批契约保留：整行可点、触摸区 >=40dp、与扇区共用 onSectorClick。
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            legendRows.forEach { row ->
                val cid = categoryIds.getOrNull(row.index)
                val expandable = cid != null && cid in expandableIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable { onSectorClick(row.index) }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    if (cid != null) {
                        // Q2A：统计占比图例放行彩色 Tabler；iconName 为空按预置 id 回查，
                        // 覆盖安装零写库；目录缺项自动回退中性圆 + 首字。
                        CategoryGlyph(
                            name = row.name,
                            iconName = "",
                            colorHex = "",
                            grayscale = false,
                            size = 32,
                            categoryId = cid
                        )
                    } else {
                        // 收款方 / 账户维度没有分类图标：中性圆 + 中文首字。
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
                    if (expandable) {
                        TextButton(onClick = { onExpandToggle(cid) }) {
                            Text(if (expandedId == cid) "⌃" else "⌄")
                        }
                    } else {
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                }
                if (cid != null && expandedId == cid) {
                    expandedChildren.forEach { (kidId, kidName, kidCents) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .clickable { onChildClick(kidId, kidName) }
                                .padding(start = 48.dp, end = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "└ " + kidName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = StatsLegend.percentText(kidCents, totalCents),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LedgerColors.Neutral
                                )
                            }
                            Text(
                                text = "¥" + formatCents(kidCents),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
                // 细分割线：纯行 + 分割线，不叠卡片 / 阴影（基线要求）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 4.dp)
                        .background(LedgerColors.Neutral.copy(alpha = 0.12f))
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
