package com.zwz.offlineledger.ui.intake

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zwz.offlineledger.core.intake.TextIntakeInspector
import com.zwz.offlineledger.core.intake.TextIntakePreview
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.ui.components.AmountMask
import com.zwz.offlineledger.ui.lock.AppLockScreen
import com.zwz.offlineledger.ui.theme.LedgerColors
import com.zwz.offlineledger.ui.theme.LedgerTheme

/**
 * 第 86 批：系统文本选择菜单 / 分享选择器进来的**只读诊断页**。
 *
 * - 不落库、不写文件、不联网、不申请权限、不注册任何后台组件。
 * - 原文只存在 [remember] 内存态；刻意不用可保存状态容器，避免写进
 *   savedInstanceState / 系统保存状态。
 * - Manifest 的 excludeFromRecents=true 防最近任务快照泄露原文。
 * - 解锁前只渲染 AppLockScreen，MUST NOT 渲染任何原文。
 * - 入口标签是「离线账本 · 文本诊断」，不叫「记一笔」：本批还不能记账。
 */
class TextIntakeActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = LedgerPrefs(this)
        val (source, initialRaw) = extract(intent)

        LedgerColors.applyReverseColors(prefs.reverseColors)
        AmountMask.hidden = prefs.hideAmounts
        applyFlagSecure(prefs.flagSecureEnabled)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            LedgerTheme {
                var unlocked by remember { mutableStateOf(!prefs.appLockEnabled) }
                // 一键清空后字符串引用即释放；不用可保存状态容器是隐私选择。
                var rawText by remember { mutableStateOf(initialRaw) }
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_PAUSE && prefs.appLockEnabled) {
                            unlocked = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        if (!unlocked) {
                            AppLockScreen(prefs = prefs, onUnlocked = { unlocked = true })
                        } else {
                            TextIntakePreviewScreen(
                                preview = TextIntakeInspector.from(source, rawText),
                                onCopy = { copyRaw(this@TextIntakeActivity, rawText) },
                                onClear = { rawText = "" },
                                onClose = { finish() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyFlagSecure(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    companion object {
        /** @return (来源标记, 原文)；优先 PROCESS_TEXT，其次 ACTION_SEND。 */
        fun extract(intent: Intent?): Pair<String, String> {
            if (intent == null) return "none" to ""
            val process = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!process.isNullOrEmpty()) return "process_text" to process
            val send = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (!send.isNullOrEmpty()) return "send" to send
            return "empty" to ""
        }

        fun copyRaw(context: Context, text: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("账单原文", text))
        }
    }
}

@androidx.compose.runtime.Composable
private fun TextIntakePreviewScreen(
    preview: TextIntakePreview,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "文本诊断 · 只读",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "本页不记账、不落库、不写文件，退出即清。原文含商户全称与卡号信息，请勿在公共场合展开。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoRow("来源", when (preview.source) {
                    "process_text" -> "文本选择菜单（PROCESS_TEXT）"
                    "send" -> "系统分享（ACTION_SEND）"
                    "empty" -> "入口已打开，但未收到文本"
                    else -> "无 Intent"
                })
                InfoRow(
                    "原文字符数",
                    "${preview.rawText.length} / ${preview.originalCharCount}" +
                        if (preview.truncated) "（已截断，上限 20000）" else "",
                )
                InfoRow(
                    "全部疑似金额串",
                    if (preview.amountCandidates.isEmpty()) "未找到"
                    else preview.amountCandidates.joinToString("、"),
                )
                InfoRow("方向命中", yesNo(preview.hasDirection))
                InfoRow("时间命中", yesNo(preview.hasTime))
                InfoRow("商户命中", yesNo(preview.hasCounterparty))
                InfoRow(
                    "达到后续自动落库门槛（只读预演）",
                    yesNo(preview.readyForAutoCommit),
                )
            }
        }

        Text(
            text = "以上均为只读预演：本批不写库、不猜金额、不补缺项。请把原文贴回分析节点聚合 vs 系统 OCR、噪声与四项字段是否齐全。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCopy) { Text("复制全文") }
            OutlinedButton(onClick = onClear) { Text("一键清空") }
            TextButton(onClick = onClose) { Text("关闭") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("收到的原文", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = preview.rawText.ifEmpty { "（已清空）" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun yesNo(hit: Boolean): String = if (hit) "✓ 命中" else "✗ 未命中"
