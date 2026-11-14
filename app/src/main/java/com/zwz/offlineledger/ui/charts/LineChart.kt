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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.ui.theme.LedgerColors

data class LineSeries(val label: String, val color: Color, val values: List<Float>)

/**
 * 原生 Canvas 折线图：网格 + 折线 + 数据点。
 * x 轴刻度用普通 Compose Text（不依赖 TextMeasurer / drawText）。
 * values 已是 ChartData.normalize 算好的 0..1 比例，UI 不碰金额换算。
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    onPointTap: ((Int) -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
                    Text(
                        text = " " + s.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier.fillMaxWidth().height(170.dp)
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
            val padTop = 10f
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

            val n = series.firstOrNull()?.values?.size ?: 0
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
                    // 点太多时只画端点，避免 31 个点挤成一团
                    if (points.size <= 15) {
                        points.forEach { p -> drawCircle(color = s.color, radius = 3f, center = p) }
                    } else {
                        drawCircle(color = s.color, radius = 3f, center = points.first())
                        drawCircle(color = s.color, radius = 3f, center = points.last())
                    }
                }
            }
        }

        if (xLabels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val shown = when {
                    xLabels.size <= 3 -> xLabels
                    else -> listOf(xLabels.first(), xLabels[xLabels.size / 2], xLabels.last())
                }
                shown.forEach {
                    Text(text = it, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
