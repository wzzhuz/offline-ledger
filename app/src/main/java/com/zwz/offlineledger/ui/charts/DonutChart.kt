package com.zwz.offlineledger.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.engine.ChartData.ChartSector
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 原生 Canvas 环形图。刻意不用 DrawScope.drawText / TextMeasurer，
 * 也不用 FlowRow（ExperimentalLayoutApi）：中心总额与图例全用
 * 普通 Compose Text / Column / Row 叠加，零版本相关 API。
 */
@Composable
fun DonutChart(
    sectors: List<ChartSector>,
    totalCents: Long,
    centerLabel: String,
    onSectorClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val chartHeight = 220.dp
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
                    text = formatCents(totalCents) + " 元",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // 图例：每行两项，普通 Column + Row，不依赖任何实验性布局 API。
        // 第 27 批起每项整行可点（点击等价于点对应扇区）。
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            sectors.chunked(2).forEachIndexed { rowIndex, group ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    group.forEachIndexed { colIndex, s ->
                        val i = rowIndex * 2 + colIndex
                        // 第 27 批：图例与扇区共用同一个 onSectorClick，
                        // 整行可点、色块加大、触摸区 >=40dp，不再只有圈能点。
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clickable { onSectorClick(i) }
                                .padding(horizontal = 4.dp)
                        ) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawCircle(
                                    color = Color(ChartData.chartArgb(s.slice.id, i).toInt())
                                )
                            }
                            Text(
                                text = " " + s.slice.name + " " + formatCents(s.slice.cents),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    if (group.size == 1) {
                        androidx.compose.foundation.layout.Spacer(
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        if (sectors.isEmpty()) {
            Text(
                text = "本期没有支出记录",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
        }
    }
}
