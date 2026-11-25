package com.zwz.offlineledger.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 原生 Canvas 环形图 + 第 68/69 批一羽式图例列表。
 *
 * 刻意不用 DrawScope.drawText / TextMeasurer，也不用 FlowRow /
 * BoxWithConstraints：环外悬浮百分比需要 Canvas 文本测量或复杂 offset 定位，
 * 本批由图例列表每行承载百分比（见 design 2.2）。
 *
 * 第 69 批：图例每行新增**灰色长条**（浅灰轨道 + 低饱和蓝填充，
 * 宽度 = 占比），承担 TopN 的大小对比；第 68 批的一级行内联展开参数
 * 全部删除——要看小类 MUST 直接切左上角「按大类 ⇄ / 按小类 ⇄」标签。
 *
 * 图例整行可点且触摸区 >=40dp，与扇区共用同一个 onSectorClick（第 27 批契约）。
 * 分类维度传 categoryIds 显示彩色 Tabler 图标，缺项回退中性圆 + 首字。
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

        // 一羽式图例列表（每行一项），与环同屏可见、允许纵向滚动。
        // 第 27 批契约保留：整行可点、触摸区 >=40dp、与扇区共用 onSectorClick。
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
                            // 统计占比图例放行彩色 Tabler；iconName 为空按预置 id 回查，
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
                    // 第 69 批 Q2A：灰色长条体现不同大小。
                    // 浅灰轨道 + 低饱和蓝填充，宽度 = 该行占比（纯显示换算）。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 42.dp)
                            .height(5.dp)
                            .background(Color(0xFFEDEFF5), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(row.percent)
                                .height(5.dp)
                                .background(CategoryHeaderBlue.copy(alpha = 0.75f), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        )
                    }
                }
                // 细分割线：纯行 + 分割线，不叠卡片 / 阴影（基线要求）。
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
