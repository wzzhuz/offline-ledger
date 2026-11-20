package com.zwz.offlineledger.quick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * 第 49 批：只读识别 -> 用户核对 -> 点保存才落库。
 * 节点命中跳过 OCR；安全窗口 / 无金额一律给「手动记账」，MUST NOT 静默写库。
 */
@Composable
fun QuickCaptureConfirmScreen(
    writer: QuickCaptureWriter,
    onClose: () -> Unit,
    onManual: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<CaptureOutcome?>(null) }
    var defaults by remember { mutableStateOf<QuickCaptureDefaults?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        defaults = writer.defaults()
        val now = System.currentTimeMillis()
        val node = QuickCaptureBridge.freshSnapshot(now)
        val fromNode = QuickCaptureResolver.resolve(node, null, null, now)
        if (fromNode is CaptureOutcome.Success) {
            outcome = fromNode
        } else {
            QuickCaptureBridge.capture(
                ContextCompat.getMainExecutor(context),
                { bitmap ->
                    QuickOcr.recognize(
                        bitmap,
                        { lines ->
                            outcome = QuickCaptureResolver.resolve(node, lines, null, now)
                            bitmap.recycle()
                        },
                        {
                            outcome = QuickCaptureResolver.resolve(node, null, 9, now)
                            bitmap.recycle()
                        }
                    )
                },
                { code -> outcome = QuickCaptureResolver.resolve(node, null, code, now) }
            )
        }
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
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("识屏记账", style = MaterialTheme.typography.titleMedium)
                when (val o = outcome) {
                    null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text("  正在识别当前屏幕…", modifier = Modifier.padding(start = 8.dp))
                    }
                    is CaptureOutcome.NeedManual -> {
                        Text(o.reason, style = MaterialTheme.typography.bodyMedium)
                        Text("不会写入任何账目。", style = MaterialTheme.typography.bodySmall)
                    }
                    is CaptureOutcome.Success -> {
                        val b = o.bill
                        val def = defaults
                        Text(
                            "金额：" + "¥%.2f".format(b.amountCents / 100.0) +
                                if (b.isIncome) "（收入）" else "（支出）"
                        )
                        Text("交易对方：" + b.counterparty.ifBlank { "（空）" })
                        Text("描述：" + b.description.ifBlank { "（空）" })
                        Text("来源：" + o.source, style = MaterialTheme.typography.bodySmall)
                        if (def != null) {
                            Text(
                                "将记入：" + def.accountName + " / " + def.categoryName,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (message != null) {
                            Text(message!!, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                val success = outcome as? CaptureOutcome.Success
                val def = defaults
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = success != null && def != null && !saving && message == null,
                        onClick = {
                            val b = success?.bill ?: return@Button
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
                    TextButton(onClick = onSettings) { Text("设置") }
                }
                TextButton(onClick = onClose) { Text("关闭") }
            }
        }
    }
}
