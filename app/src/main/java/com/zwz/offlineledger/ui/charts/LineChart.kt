package com.zwz.offlineledger.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlin.math.roundToInt

data class LineSeries(val label: String, val color: Color, val values: List<Float>)

/**
 * 原生 Canvas 折线图：竖轴刻度 + 折线 + 数据点 + 横轴标签 + 常驻点数值。
 * values 是 ChartData.normalize 算好的 0..1 比例，UI 不碰金额换算；
 * valueLabels 由调用方按原始金额传入（元，两位小数）。
 * 第 4 批：竖屏常驻标峰值 / 末点 / 选中点，横屏 annotateAll 全量标。
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    yLabels: List<String> = emptyList(),
    chartHeight: Dp = 170.dp,
    labelEvery: Int = 0,
    valueLabels: List<String> = emptyList(),
    annotateAll: Boolean = false,
    selectedIndex: Int? = null,
    zeroLine: Boolean = false,
    onPointTap: ((Int) -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    Column(modifier = modifier.fillMaxWidth()) {
        val n = series.firstOrNull()?.values?.size ?: 0
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawLine(
                            color = s.color,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 4f
                        )
                    }
                    Text(text = " " + s.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            if (yLabels.isNotEmpty()) {
                Column(
                    modifier = Modifier.height(chartHeight).padding(end = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    yLabels.forEach {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral
                        )
                    }
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight)
                    .pointerInput(n) {
                        detectTapGestures { offset ->
                            val cb = onPointTap ?: return@detectTapGestures
                            if (n <= 0) return@detectTapGestures
                            val step = if (n > 1) (size.width - 16f) / (n - 1) else 0f
                            val idx = if (step > 0f)
                                ((offset.x - 8f) / step).roundToInt().coerceIn(0, n - 1)
                            else 0
                            cb(idx)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val padTop = 22f
                val padBottom = 10f
                val padX = 8f
                val plotH = h - padTop - padBottom

                for (i in 0..4) {
                    val y = padTop + plotH * i / 4f
                    drawLine(
                        color = LedgerColors.Neutral.copy(alpha = 0.15f),
                        start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f
                    )
                }
                // 第 23 批：净结余线允许负值，0 落在绘图区正中，画一条醒目的零线。
                if (zeroLine) {
                    val y0 = padTop + plotH / 2f
                    drawLine(
                        color = LedgerColors.Neutral.copy(alpha = 0.45f),
                        start = Offset(0f, y0), end = Offset(w, y0),
                        strokeWidth = 1.5f
                    )
                }

                if (n > 0) {
                    val step = if (n > 1) (w - padX * 2f) / (n - 1) else 0f
                    series.forEach { s ->
                        val points = s.values.mapIndexed { idx, v ->
                            Offset(
                                padX + step * idx,
                                padTop + plotH * (1f - v.coerceIn(0f, 1f))
                            )
                        }
                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = s.color,
                                start = points[i], end = points[i + 1],
                                strokeWidth = 3f
                            )
                        }
                        if (points.size <= 15) {
                            points.forEach { p -> drawCircle(color = s.color, radius = 3f, center = p) }
                        } else {
                            drawCircle(color = s.color, radius = 3f, center = points.first())
                            drawCircle(color = s.color, radius = 3f, center = points.last())
                        }
                    }

                    if (valueLabels.isNotEmpty()) {
                        val marked = ChartData.annotationIndices(
                            series.first().values, selectedIndex, annotateAll
                        )
                        val labelStyle = TextStyle(
                            color = LedgerColors.Neutral,
                            fontSize = 10.sp
                        )
                        val pts = series.first().values.mapIndexed { idx, v ->
                            Offset(padX + step * idx, padTop + plotH * (1f - v.coerceIn(0f, 1f)))
                        }
                        marked.forEach { idx ->
                            val label = valueLabels.getOrNull(idx) ?: return@forEach
                            val p = pts.getOrNull(idx) ?: return@forEach
                            val layout = textMeasurer.measure(label, labelStyle)
                            val maxX = (w - layout.size.width.toFloat()).coerceAtLeast(0f)
                            val tx = (p.x - layout.size.width / 2f).coerceIn(0f, maxX)
                            val ty = if (p.y < 30f) {
                                (p.y + 8f).coerceAtLeast(0f)
                            } else {
                                (p.y - layout.size.height.toFloat() - 6f).coerceAtLeast(0f)
                            }
                            drawText(layout, topLeft = Offset(tx, ty))
                        }
                    }
                }
            }
        }

        if (xLabels.isNotEmpty()) {
            val shown = when {
                labelEvery > 0 -> xLabels.filterIndexed { i, _ ->
                    i % labelEvery == 0 || i == xLabels.lastIndex
                }
                xLabels.size <= 3 -> xLabels
                else -> listOf(xLabels.first(), xLabels[xLabels.size / 2], xLabels.last())
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                shown.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
