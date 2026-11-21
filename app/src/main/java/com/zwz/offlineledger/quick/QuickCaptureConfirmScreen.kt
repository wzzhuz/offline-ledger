package com.zwz.offlineledger.quick

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zwz.offlineledger.data.repository.QuickCaptureDefaults
import kotlinx.coroutines.launch

/**
 * 第 50 批：磁贴点击与识屏解耦后的状态机。
 * 任何一步失败都 MUST 给明确提示 + 一键手动，MUST NOT 静默 return。
 * 零无障碍权限时磁贴仍可用（手动记账 + 权限引导）。
 * 识别详情只在内存展示，不落盘、不上传、不加列改表。
 */
private sealed interface UiState {
    object Preparing : UiState
    data class PermissionNeeded(val reason: String) : UiState
    data class Manual(val reason: String) : UiState
    data class Review(
        val bill: ParsedBill,
        val source: String,
        val blocks: List<TextBlockCandidate>,
        val trace: ParseTrace,
        val options: List<AmountCandidate>
    ) : UiState
}

@Composable
fun QuickCaptureConfirmScreen(
    writer: QuickCaptureWriter,
    onClose: () -> Unit,
    onManual: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UiState>(UiState.Preparing) }
    var defaults by remember { mutableStateOf<QuickCaptureDefaults?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        defaults = writer.defaults()
        if (!QuickCaptureBridge.isAttached()) {
            ui = UiState.PermissionNeeded(
                "无障碍服务未开启（升级 / 重启后会被系统复位）。识屏需要它，但磁贴仍可直接手动记账。"
            )
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val executor = ContextCompat.getMainExecutor(context)
        val node = QuickCaptureBridge.freshSnapshot(now)
        val fromNode = QuickCaptureResolver.resolve(node, null, null, now)
        if (fromNode is CaptureOutcome.Success) {
            val blocks = node?.lines?.mapIndexed { i, t -> TextBlockCandidate(i, t) }
                ?: emptyList()
            ui = reviewOf(fromNode, blocks, now)
            return@LaunchedEffect
        }

        var retried = false
        val handler = Handler(Looper.getMainLooper())

        fun attemptCapture() {
            QuickCaptureBridge.capture(
                executor,
                { bitmap ->
                    QuickOcr.recognizeBlocks(
                        bitmap,
                        { blocks ->
                            val outcome = QuickCaptureResolver.resolve(
                                node, blocks.map { it.text }, null, now
                            )
                            ui = reviewOf(outcome, blocks, now)
                            bitmap.recycle()
                        },
                        {
                            ui = UiState.Manual(
                                "OCR 识别失败，无法判断金额。请手动记账或换一屏再试。"
                            )
                            bitmap.recycle()
                        }
                    )
                },
                { code ->
                    // AOSP 节流错误码 1：等一拍重试一次，不靠用户连点碰运气。
                    if (code == 1 && !retried) {
                        retried = true
                        ui = UiState.Preparing
                        handler.postDelayed({ attemptCapture() }, 900L)
                    } else {
                        ui = UiState.Manual(
                            "截图失败（错误码 " + code + "）：" +
                                QuickCaptureResolver.screenshotReason(code)
                        )
                    }
                }
            )
        }
        attemptCapture()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { }
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("识屏记账", style = MaterialTheme.typography.titleMedium)

                when (val s = ui) {
                    is UiState.Preparing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text("  正在识别当前屏幕…", modifier = Modifier.padding(start = 8.dp))
                    }
                    is UiState.PermissionNeeded -> {
                        Text(s.reason, style = MaterialTheme.typography.bodyMedium)
                        Text("不会写入任何账目。", style = MaterialTheme.typography.bodySmall)
                    }
                    is UiState.Manual -> {
                        Text(s.reason, style = MaterialTheme.typography.bodyMedium)
                        Text("不会写入任何账目。", style = MaterialTheme.typography.bodySmall)
                    }
                    is UiState.Review -> {
                        val b = s.bill
                        Text(
                            "金额：¥%.2f".format(b.amountCents / 100.0) +
                                if (b.isIncome) "（收入）" else "（支出）"
                        )
                        Text("交易对方：" + b.counterparty.ifBlank { "（空）" })
                        Text("描述：" + b.description.ifBlank { "（空）" })
                        Text("来源：" + s.source, style = MaterialTheme.typography.bodySmall)
                        defaults?.let { d ->
                            Text(
                                "将记入：" + d.accountName + " / " + d.categoryName,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "收起识别详情" else "展开识别详情（定位识别问题）")
                        }
                        if (expanded) {
                            val r = s as UiState.Review
                            RecognitionDetail(r) { changed ->
                                ui = r.copy(bill = changed)
                            }
                        }
                    }
                }

                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                val review = ui as? UiState.Review
                val def = defaults
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = review != null && def != null && !saving && message == null,
                        onClick = {
                            val b = review?.bill ?: return@Button
                            val d = def ?: return@Button
                            saving = true
                            scope.launch {
                                val ok = writer.save(b, d.accountId, d.categoryId)
                                message = if (ok) "已保存" else "已存在，未重复入账"
                                saving = false
                            }
                        }
                    ) { Text(if (saving) "保存中…" else "核对并保存") }
                    TextButton(onClick = onManual) { Text("手动记账") }
                    TextButton(onClick = onSettings) { Text("设置 / 权限") }
                }
                TextButton(onClick = onClose) { Text("关闭") }
            }
        }
    }
}

private fun reviewOf(
    outcome: CaptureOutcome,
    blocks: List<TextBlockCandidate>,
    now: Long
): UiState {
    if (outcome !is CaptureOutcome.Success) {
        return UiState.Manual(
            (outcome as? CaptureOutcome.NeedManual)?.reason
                ?: "未识别到金额，请手动记账或换一屏再试"
        )
    }
    val trace = ScreenTextParser.parseWithTrace(blocks.map { it.text }, now)
        ?: return UiState.Manual("解析不出结构化字段，请手动记账")
    return UiState.Review(trace.bill, outcome.source, blocks, trace, trace.amountOptions)
}

/** 识别详情：原始块 + 字段来源 + 金额 / 对方 / 描述改选。全部只在内存。 */
@Composable
private fun RecognitionDetail(
    review: UiState.Review,
    onBillChanged: (ParsedBill) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("字段来源", style = MaterialTheme.typography.labelMedium)
        review.trace.fields.forEach { f ->
            Text(
                "· " + f.field + " ← 块#" + f.blockIndex + "「" + f.rawText + "」· " + f.rule,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            "原始文本块（共 " + review.blocks.size + " 块，仅内存，不保存）",
            style = MaterialTheme.typography.labelMedium
        )
        review.blocks.take(40).forEach { b ->
            Text(
                "#" + b.index + " (" + b.left + "," + b.top + ") " + b.text,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text("改选金额", style = MaterialTheme.typography.labelMedium)
        review.options.take(6).forEach { c ->
            TextButton(onClick = { onBillChanged(ScreenTextParser.withAmount(review.bill, c.amountCents)) }) {
                Text(
                    c.rawText + " → ¥%.2f".format(c.amountCents / 100.0) +
                        (c.rejected?.let { "（" + it + "）" } ?: "") + " · " + c.rule
                )
            }
        }
        val textOptions = review.blocks.map { it.text }
            .filter { it.length in 2..30 && !it.any { ch -> ch.isDigit() } }
            .distinct().take(5)
        Text("改选交易对方", style = MaterialTheme.typography.labelMedium)
        textOptions.forEach { t ->
            TextButton(onClick = { onBillChanged(ScreenTextParser.withCounterparty(review.bill, t)) }) {
                Text(t)
            }
        }
        Text("改选描述", style = MaterialTheme.typography.labelMedium)
        textOptions.forEach { t ->
            TextButton(onClick = { onBillChanged(ScreenTextParser.withDescription(review.bill, t)) }) {
                Text(t)
            }
        }
    }
}
