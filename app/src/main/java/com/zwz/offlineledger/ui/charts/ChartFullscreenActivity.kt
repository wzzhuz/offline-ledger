package com.zwz.offlineledger.ui.charts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.core.view.WindowCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.engine.ChartData
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.theme.LedgerColors
import com.zwz.offlineledger.ui.theme.LedgerTheme

/**
 * 横屏全屏页：Manifest 固定 landscape，竖屏 360dp 塞 12 个月标签必然挤。
 * 第 4 批：横屏全量常驻点数值，竖屏只标关键三点。
 */
class ChartFullscreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 第 23 批：全屏横屏页显式接管 insets，标题行不再压到状态栏的时间 / 电量。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "趋势"
        val values = (intent.getLongArrayExtra(EXTRA_VALUES) ?: longArrayOf()).toList()
        val labels = intent.getStringArrayListExtra(EXTRA_LABELS) ?: arrayListOf()
        val signed = intent.getBooleanExtra(EXTRA_SIGNED, false)
        val color = when (intent.getStringExtra(EXTRA_COLOR)) {
            "INCOME", "ASSET" -> LedgerColors.IncomeGreen
            else -> LedgerColors.ExpenseRed
        }
        setContent {
            LedgerTheme {
                Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    FullscreenChart(title, values, labels, color, signed) { finish() }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "chart_title"
        private const val EXTRA_VALUES = "chart_values"
        private const val EXTRA_LABELS = "chart_labels"
        private const val EXTRA_COLOR = "chart_color"
        private const val EXTRA_SIGNED = "chart_signed"

        fun intentFor(
            context: Context,
            title: String,
            values: LongArray,
            labels: ArrayList<String>,
            colorName: String,
            signed: Boolean = false
        ): Intent = Intent(context, ChartFullscreenActivity::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_VALUES, values)
            .putStringArrayListExtra(EXTRA_LABELS, labels)
            .putExtra(EXTRA_COLOR, colorName)
            .putExtra(EXTRA_SIGNED, signed)
    }
}

@Composable
private fun FullscreenChart(
    title: String,
    values: List<Long>,
    labels: List<String>,
    color: androidx.compose.ui.graphics.Color,
    signed: Boolean = false,
    onClose: () -> Unit
) {
    var scaleX by remember { mutableStateOf(1f) }
    var tapped by remember { mutableStateOf<Int?>(null) }
    val max = if (signed) ChartData.seriesMaxAbsSigned(values)
        else ChartData.seriesMaxSingle(values)
    val normalized = if (signed) ChartData.normalizeSigned(values, max)
        else ChartData.normalize(values, max)
    val axis = if (signed) ChartData.yAxisLabelsSigned(max)
        else ChartData.yAxisLabels(max)

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Text("竖轴单位：元 · 双指缩放 $scaleX 倍", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onClose) { Text("关闭") }
        }
        LineChart(
            series = listOf(
                LineSeries(title, color, normalized)
            ),
            xLabels = labels,
            yLabels = axis,
            zeroLine = signed,
            chartHeight = 300.dp,
            labelEvery = 1,
            valueLabels = values.map { formatCents(it) },
            annotateAll = true,
            selectedIndex = tapped,
            onPointTap = { tapped = it },
            modifier = Modifier
                .fillMaxWidth()
                .scale(scaleX = scaleX, scaleY = 1f)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        scaleX = (scaleX * zoom).coerceIn(1f, 4f)
                    }
                }
        )
        Text(
            text = "每个点常驻显示金额；点击数据点也可查看明细。横轴标签全量铺开。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
    }

    tapped?.let { idx ->
        AlertDialog(
            onDismissRequest = { tapped = null },
            title = { Text(labels.getOrElse(idx) { "第 " + (idx + 1) + " 项" }) },
            text = {
                Text(
                    text = title + "：" +
                        formatCents(values.getOrElse(idx) { 0L }) + " 元"
                )
            },
            confirmButton = { TextButton(onClick = { tapped = null }) { Text("关闭") } }
        )
    }
}
