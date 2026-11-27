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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zwz.offlineledger.core.intake.TextIntakeCommitDraft
import com.zwz.offlineledger.core.intake.TextIntakeCommitOutcome
import com.zwz.offlineledger.core.intake.TextIntakeInspector
import com.zwz.offlineledger.core.intake.TextIntakeRules
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.ui.components.AmountMask
import com.zwz.offlineledger.ui.lock.AppLockScreen
import com.zwz.offlineledger.ui.theme.LedgerColors
import com.zwz.offlineledger.ui.theme.LedgerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.launch

private data class UiStatus(val kind: String, val message: String, val recordId: Long? = null)

/**
 * 第 87 批：系统文本选择菜单 / 分享选择器 / 设置页共用的文本识别页。
 *
 * - 严格充分条件 + 自动开关开启时自动落库；否则只显示草稿，MUST NOT 猜值。
 * - 规则覆盖层与结果摘要走 LedgerPrefs，原文只留内存态，不进备份。
 * - 解锁前只渲染 AppLockScreen；excludeFromRecents 防最近任务快照泄露。
 */
@AndroidEntryPoint
class TextIntakeActivity : FragmentActivity() {

    @Inject lateinit var repository: LedgerRepository

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
                var rawText by remember { mutableStateOf(initialRaw) }
                var testInput by remember { mutableStateOf(initialRaw) }
                var manualMode by remember { mutableStateOf(source == ACTION_DEBUG) }
                var rulePatch by remember { mutableStateOf(prefs.textIntakeRulePatch.take(TextIntakeRules.MAX_PATCH_CHARS)) }
                var autoEnabled by remember { mutableStateOf(prefs.textIntakeAutoCommitEnabled) }
                var logText by remember { mutableStateOf(prefs.textIntakeLog) }
                var commitState by remember { mutableStateOf<UiStatus?>(null) }
                var lastDraft by remember { mutableStateOf<TextIntakeCommitDraft?>(null) }
                var autoAttempted by remember { mutableStateOf(false) }

                val lifecycleOwner = LocalLifecycleOwner.current
                val scope = rememberCoroutineScope()
                val patchResult = remember(rulePatch) { TextIntakeRules.withPatch(rulePatch) }
                val rules = patchResult.rules
                val external = source == "process_text" || source == "send"
                val previewSource = if (manualMode) ACTION_DEBUG else source
                val previewText = if (manualMode) testInput else rawText
                val preview = TextIntakeInspector.from(previewSource, previewText, rules)

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_PAUSE && prefs.appLockEnabled) {
                            unlocked = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                fun appendLog(kind: String, draft: TextIntakeCommitDraft?, reason: String) {
                    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    val amount = draft?.amountCents?.let { centsToYuan(it) } ?: "--"
                    val sourceTail = draft?.sourceId?.takeLast(6) ?: "------"
                    val line = "$ts | $kind | ${amount}元 | 来源尾号$sourceTail | $reason"
                    val next = (logText + "\n" + line)
                        .lines().filter { it.isNotBlank() }.takeLast(20).joinToString("\n")
                    prefs.textIntakeLog = next
                    logText = next
                }

                fun applyOutcome(outcome: TextIntakeCommitOutcome, mode: String, draft: TextIntakeCommitDraft) {
                    lastDraft = draft
                    when (outcome) {
                        is TextIntakeCommitOutcome.Inserted -> {
                            commitState = UiStatus("INSERTED", outcome.message, outcome.recordId)
                            appendLog(mode, draft, "已记账")
                        }
                        is TextIntakeCommitOutcome.Duplicate -> {
                            commitState = UiStatus("DUPLICATE", outcome.message)
                            appendLog(mode, draft, "已存在")
                        }
                        is TextIntakeCommitOutcome.Rejected -> {
                            commitState = UiStatus("REJECTED", outcome.reason)
                            appendLog(mode, draft, "降级草稿")
                        }
                    }
                }

                suspend fun doCommit(draft: TextIntakeCommitDraft): TextIntakeCommitOutcome = try {
                    repository.commitTextIntake(draft)
                } catch (e: Exception) {
                    TextIntakeCommitOutcome.Rejected("写入失败：" + (e.message ?: e.javaClass.simpleName))
                }

                LaunchedEffect(unlocked, external, manualMode, autoEnabled, autoAttempted, rulePatch) {
                    if (!unlocked || !external || manualMode || !autoEnabled || autoAttempted || initialRaw.isBlank()) {
                        return@LaunchedEffect
                    }
                    autoAttempted = true
                    val p = TextIntakeInspector.from(source, initialRaw, rules)
                    if (p.readyForAutoCommit && p.commitDraft != null) {
                        applyOutcome(doCommit(p.commitDraft!!), "自动", p.commitDraft!!)
                    } else {
                        lastDraft = p.commitDraft
                        commitState = UiStatus(
                            "DRAFT",
                            "未达严格充分条件，已降级草稿：" + p.missing.joinToString("；"),
                        )
                        appendLog("草稿", p.commitDraft, "未达严格门槛")
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        if (!unlocked) {
                            AppLockScreen(
                                prefs = prefs,
                                onUnlocked = { unlocked = true },
                                onPrefsChanged = { applyFlagSecure(prefs.flagSecureEnabled) },
                            )
                        } else {
                            val scroll = rememberScrollState()
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scroll),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("文本诊断 / 自动记账", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "原文只在内存，退出或一键清空即释放。规则与结果摘要不含原文，且已排除系统备份。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("自动记账", style = MaterialTheme.typography.titleSmall)
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Text("严格条件达标后自动落库", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                            Switch(
                                                checked = autoEnabled,
                                                onCheckedChange = {
                                                    autoEnabled = it
                                                    prefs.textIntakeAutoCommitEnabled = it
                                                },
                                            )
                                        }
                                        Text(
                                            "关闭后所有文本只形成草稿，不会自动写库；未知格式也只会降级草稿。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("规则增补（可粘贴 AI 给你的一行词）", style = MaterialTheme.typography.titleSmall)
                                        OutlinedTextField(
                                            value = rulePatch,
                                            onValueChange = {
                                                val next = it.take(TextIntakeRules.MAX_PATCH_CHARS)
                                                rulePatch = next
                                                prefs.textIntakeRulePatch = next
                                                manualMode = true
                                                commitState = null
                                            },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                                            label = { Text("状态词 / 优惠词 / 噪声 / 分类映射") },
                                        )
                                        Text(
                                            "示例：状态词 += 付款完成　优惠词 += 立减金　噪声 += 查看购物详情　分类 文化休闲 = 学习娱乐",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (patchResult.invalidLines.isNotEmpty()) {
                                            Text("已忽略 ${patchResult.invalidLines.size} 行无效指令", style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = {
                                            rulePatch = ""
                                            prefs.textIntakeRulePatch = ""
                                            manualMode = true
                                            commitState = null
                                        }) { Text("恢复默认规则") }
                                    }
                                }

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("粘贴原文试解析（不自动落库）", style = MaterialTheme.typography.titleSmall)
                                        OutlinedTextField(
                                            value = testInput,
                                            onValueChange = { testInput = it },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                                            label = { Text("把失败那一笔原文贴进来") },
                                        )
                                        Button(onClick = {
                                            manualMode = true
                                            commitState = null
                                            lastDraft = null
                                        }) { Text("试解析") }
                                        if (manualMode) {
                                            Text(
                                                "当前为试解析模式：不会自动写库；可手工保存下方草稿。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }

                                if (commitState != null) {
                                    val st = commitState!!
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("处理结果：${st.kind}", style = MaterialTheme.typography.titleSmall)
                                            Text(st.message, style = MaterialTheme.typography.bodySmall)
                                            if (st.kind == "INSERTED" && st.recordId != null) {
                                                Button(onClick = {
                                                    val id = st.recordId!!
                                                    scope.launch {
                                                        try {
                                                            repository.deleteRecord(id)
                                                            commitState = UiStatus("UNDONE", "已撤销，余额已重算")
                                                            appendLog("撤销", lastDraft, "已撤销")
                                                        } catch (e: Exception) {
                                                            commitState = UiStatus("REJECTED", "撤销失败：" + (e.message ?: "未知错误"))
                                                        }
                                                    }
                                                }) { Text("撤销这笔") }
                                            }
                                        }
                                    }
                                }

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("只读预演", style = MaterialTheme.typography.titleSmall)
                                        InfoRow("来源", sourceText(preview.source))
                                        InfoRow("字符数", "${preview.rawText.length} / ${preview.originalCharCount}" + if (preview.truncated) "（已截断）" else "")
                                        InfoRow("页面特征数", "${preview.commitDraft?.pageMarkers ?: 0}")
                                        InfoRow("主金额", preview.commitDraft?.amountYuan.orEmpty().ifEmpty { "未找到" })
                                        InfoRow("全部金额候选", preview.amountCandidates.ifEmpty { listOf("未找到") }.joinToString("、"))
                                        InfoRow("方向", yesNo(preview.hasDirection))
                                        InfoRow("时间", yesNo(preview.hasTime))
                                        InfoRow("收款方", yesNo(preview.hasCounterparty))
                                        InfoRow("严格门槛", yesNo(preview.readyForAutoCommit))
                                        if (preview.missing.isNotEmpty()) {
                                            Text("未达标：" + preview.missing.joinToString("；"), style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (preview.warnings.isNotEmpty()) {
                                            Text("提示：" + preview.warnings.joinToString("；"), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }

                                val showDraft = preview.commitDraft != null &&
                                    (commitState == null || commitState!!.kind == "DRAFT" || commitState!!.kind == "REJECTED")
                                if (showDraft) {
                                    DraftForm(preview.commitDraft!!) { d ->
                                        scope.launch { applyOutcome(doCommit(d), "手工", d) }
                                    }
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

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("最近结果摘要（不含原文）", style = MaterialTheme.typography.titleSmall)
                                        Text(logText.ifBlank { "（暂无）" }, style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = {
                                            logText = ""
                                            prefs.textIntakeLog = ""
                                        }) { Text("清除留痕") }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { copyRaw(this@TextIntakeActivity, preview.rawText) }) { Text("复制全文") }
                                    OutlinedButton(onClick = {
                                        if (manualMode) testInput = "" else rawText = ""
                                        commitState = null
                                        lastDraft = null
                                    }) { Text("一键清空") }
                                    TextButton(onClick = { finish() }) { Text("关闭") }
                                }
                            }
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
        const val ACTION_TEXT_INTAKE_DEBUG = "com.zwz.offlineledger.ACTION_TEXT_INTAKE_DEBUG"
        private const val ACTION_DEBUG = "debug"

        fun extract(intent: Intent?): Pair<String, String> {
            if (intent == null) return ACTION_DEBUG to ""
            if (intent.action == ACTION_TEXT_INTAKE_DEBUG) return ACTION_DEBUG to ""
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

@Composable
private fun DraftForm(initial: TextIntakeCommitDraft, onSave: (TextIntakeCommitDraft) -> Unit) {
    var amount by remember { mutableStateOf(initial.amountYuan) }
    var sourceId by remember { mutableStateOf(initial.sourceId) }
    var counterparty by remember { mutableStateOf(initial.counterparty) }
    var accountName by remember { mutableStateOf(initial.accountName) }
    var cardLast4 by remember { mutableStateOf(initial.cardLast4) }
    var categoryRoot by remember { mutableStateOf(initial.categoryRoot.orEmpty()) }
    var note by remember { mutableStateOf(initial.note) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(initial.sourceId, initial.amountYuan, initial.counterparty, initial.accountName, initial.cardLast4) {
        amount = initial.amountYuan
        sourceId = initial.sourceId
        counterparty = initial.counterparty
        accountName = initial.accountName
        cardLast4 = initial.cardLast4
        categoryRoot = initial.categoryRoot.orEmpty()
        note = initial.note
        error = ""
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("预填草稿（未自动写库）", style = MaterialTheme.typography.titleSmall)
            InfoRow("日期", initial.localDate.toString())
            InfoRow("状态", initial.statusWord.ifEmpty { "未找到" })
            InfoRow("账户", "${initial.accountName}(${initial.cardLast4})")
            if (initial.discountCents != null) {
                InfoRow("原价 / 优惠", "${initial.originalYuan} / ${initial.discountYuan}")
            }
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("实付金额") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = sourceId, onValueChange = { sourceId = it }, label = { Text("来源号 / 订单号") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = counterparty, onValueChange = { counterparty = it }, label = { Text("收款方") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = accountName, onValueChange = { accountName = it }, label = { Text("账户纯名") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = cardLast4, onValueChange = { cardLast4 = it }, label = { Text("账户尾号") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = categoryRoot, onValueChange = { categoryRoot = it }, label = { Text("一级分类（可留空）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可留空）") }, modifier = Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                val cents = TextIntakeInspector.yuanToCentsAbs(amount.trim())
                if (cents == null || cents <= 0L) {
                    error = "金额必须严格为两位小数"
                } else if (sourceId.isBlank()) {
                    error = "来源号不能为空"
                } else {
                    val amountEdited = amount.trim().removePrefix("-") != initial.amountYuan
                    val edited = initial.copy(
                        sourceId = sourceId.trim(),
                        amountCents = cents,
                        amountYuan = amount.trim().removePrefix("-"),
                        discountCents = if (amountEdited) null else initial.discountCents,
                        discountYuan = if (amountEdited) "" else initial.discountYuan,
                        originalYuan = if (amountEdited) "" else initial.originalYuan,
                        counterparty = counterparty.trim(),
                        accountName = accountName.trim(),
                        cardLast4 = cardLast4.trim(),
                        categoryRoot = categoryRoot.trim().takeIf { it.isNotBlank() },
                        note = note,
                        warnings = initial.warnings + if (amountEdited) listOf("手工改金额后已清除优惠") else emptyList(),
                    )
                    error = ""
                    onSave(edited)
                }
            }) { Text("手工保存这笔") }
        }
    }
}

@Composable
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

private fun sourceText(source: String): String = when (source) {
    "process_text" -> "文本选择菜单（PROCESS_TEXT）"
    "send" -> "系统分享（ACTION_SEND）"
    "debug" -> "内部试解析"
    "empty" -> "入口已打开，但未收到文本"
    else -> "无 Intent"
}

private fun centsToYuan(cents: Long): String {
    val v = abs(cents)
    return (v / 100L).toString() + "." + (v % 100L).toString().padStart(2, '0')
}
