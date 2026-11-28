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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class UiStatus(val kind: String, val message: String, val recordId: Long? = null)

/**
 * 第 89 批：系统文本选择菜单 / 分享选择器 / 设置页共用的自动记账页。
 *
 * 三条硬约束：
 * - 写事务 MUST 跑在 Activity 的 lifecycleScope，MUST NOT 跑在 LaunchedEffect /
 *   rememberCoroutineScope 里（composition 离开会 cancel，第 88 批「写入失败
 *   The coroutine scope left the composition」即此坑）。
 * - CancellationException MUST 原样抛出，MUST NOT 被包装成「写入失败」。
 * - 页面分三区：本次结果 / 规则与试解析 / 记录。外部入口默认落在「本次结果」，
 *   结果条置顶且常驻，失败原因第一屏可见。
 */
@AndroidEntryPoint
class TextIntakeActivity : FragmentActivity() {

    @Inject lateinit var repository: LedgerRepository

    private lateinit var prefs: LedgerPrefs
    private var source: String = ACTION_DEBUG
    private var initialRaw: String = ""
    private var autoCommitStarted = false

    /** Activity 级 snapshot state：由 lifecycleScope 写，Compose 只读订阅。 */
    private val intakeStatus = mutableStateOf<UiStatus?>(null)
    private val intakeLog = mutableStateOf("")
    private val activeDraft = mutableStateOf<TextIntakeCommitDraft?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LedgerPrefs(this)
        val extracted = extract(intent)
        source = extracted.first
        initialRaw = extracted.second
        intakeLog.value = prefs.textIntakeLog

        LedgerColors.applyReverseColors(prefs.reverseColors)
        AmountMask.hidden = prefs.hideAmounts
        applyFlagSecure(prefs.flagSecureEnabled)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // 未开应用锁时立刻触发；开锁时由 AppLockScreen 的 onUnlocked 触发。
        // 两者都只调一次，且完全不依赖 composition coroutine。
        if (!prefs.appLockEnabled) triggerAutoCommit()

        setContent {
            LedgerTheme {
                var unlocked by remember { mutableStateOf(!prefs.appLockEnabled) }
                var rawText by remember { mutableStateOf(initialRaw) }
                var testInput by remember { mutableStateOf(initialRaw) }
                var manualMode by remember { mutableStateOf(source == ACTION_DEBUG) }
                var rulePatch by remember { mutableStateOf(prefs.textIntakeRulePatch.take(TextIntakeRules.MAX_PATCH_CHARS)) }
                var autoEnabled by remember { mutableStateOf(prefs.textIntakeAutoCommitEnabled) }
                var tab by remember { mutableStateOf(if (source == ACTION_DEBUG) 1 else 0) }

                val lifecycleOwner = LocalLifecycleOwner.current
                val patchResult = remember(rulePatch) { TextIntakeRules.withPatch(rulePatch) }
                val rules = patchResult.rules
                val external = initialRaw.isNotBlank() && source != ACTION_DEBUG
                val previewSource = if (manualMode) ACTION_DEBUG else source
                val previewText = if (manualMode) testInput else rawText
                val preview = TextIntakeInspector.from(previewSource, previewText, rules)
                val status = intakeStatus.value
                val logText = intakeLog.value
                val draft = activeDraft.value ?: preview.commitDraft

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
                            AppLockScreen(
                                prefs = prefs,
                                onUnlocked = {
                                    unlocked = true
                                    triggerAutoCommit()
                                },
                                onPrefsChanged = { applyFlagSecure(prefs.flagSecureEnabled) },
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text("自动记账", style = MaterialTheme.typography.headlineSmall)
                                    Text(
                                        "原文只在内存；规则与结果摘要不含原文，且已排除系统备份。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TabRow(selectedTabIndex = tab) {
                                    listOf("本次结果", "规则与试解析", "记录").forEachIndexed { i, title ->
                                        Tab(
                                            selected = tab == i,
                                            onClick = { tab = i },
                                            text = { Text(title, style = MaterialTheme.typography.labelMedium) },
                                        )
                                    }
                                }
                                val scroll = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                        .verticalScroll(scroll),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    when (tab) {
                                        // ── 分区 1：本次结果（外部入口默认落这里） ──
                                        0 -> {
                                            ResultBanner(
                                                status = status,
                                                rawCount = rawText.length,
                                                sourceLabel = sourceText(source),
                                                external = external,
                                                autoEnabled = autoEnabled,
                                                manualMode = manualMode,
                                            )
                                            if (status?.kind == "INSERTED" && status.recordId != null) {
                                                Button(onClick = { undo(status.recordId!!) }) {
                                                    Text("撤销这笔")
                                                }
                                            }
                                            if (draft != null && draft.amountYuan.isNotBlank() &&
                                                (status == null || status.kind == "DRAFT" ||
                                                    status.kind == "REJECTED" || status.kind == "DUPLICATE")
                                            ) {
                                                InfoCard("本次识别摘要", listOf(
                                                    "金额" to "${draft.amountYuan} 元",
                                                    "状态" to draft.statusWord.ifEmpty { "未找到" },
                                                    "时间" to draft.localDate.toString(),
                                                    "账户" to "${draft.accountName}(${draft.cardLast4})",
                                                    "收款方" to draft.counterparty.ifEmpty { "未找到" },
                                                    "分类" to (draft.categoryRoot ?: "未映射，留空"),
                                                    "来源号尾号" to draft.sourceId.takeLast(6),
                                                ).let { rows ->
                                                    if (draft.discountCents != null) rows +
                                                        listOf("原价 / 优惠" to "${draft.originalYuan} / ${draft.discountYuan}")
                                                    else rows
                                                })
                                                DraftForm(draft) { edited -> saveManual(edited) }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = { copyRaw(this@TextIntakeActivity, initialRaw) }) {
                                                    Text("复制本次原文")
                                                }
                                                OutlinedButton(onClick = {
                                                    rawText = ""
                                                    activeDraft.value = null
                                                    intakeStatus.value = null
                                                }) { Text("一键清空") }
                                                TextButton(onClick = { finish() }) { Text("关闭") }
                                            }
                                            Text(
                                                "原文全文与结果留痕在「记录」页；规则和试解析在第二个页签。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        // ── 分区 2：规则与试解析（设置页内部入口默认落这里） ──
                                        1 -> {
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("自动 / 仅草稿", style = MaterialTheme.typography.titleSmall)
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        Text(
                                                            "严格条件达标后自动落库",
                                                            modifier = Modifier.weight(1f),
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                        Switch(
                                                            checked = autoEnabled,
                                                            onCheckedChange = {
                                                                autoEnabled = it
                                                                prefs.textIntakeAutoCommitEnabled = it
                                                            },
                                                        )
                                                    }
                                                    Text(
                                                        "关闭后所有文本只形成草稿；未知格式也只会降级草稿，不会误记。",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("规则增补（可粘贴 AI 生成的一行词）", style = MaterialTheme.typography.titleSmall)
                                                    OutlinedTextField(
                                                        value = rulePatch,
                                                        onValueChange = {
                                                            val next = it.take(TextIntakeRules.MAX_PATCH_CHARS)
                                                            rulePatch = next
                                                            prefs.textIntakeRulePatch = next
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
                                                        Text(
                                                            "已忽略 ${patchResult.invalidLines.size} 行无效指令",
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        TextButton(onClick = {
                                                            copyRaw(this@TextIntakeActivity, RULE_PROMPT)
                                                        }) { Text("复制规则生成提示词") }
                                                        TextButton(onClick = {
                                                            rulePatch = ""
                                                            prefs.textIntakeRulePatch = ""
                                                        }) { Text("恢复默认规则") }
                                                    }
                                                }
                                            }
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("粘贴原文试解析（不写库）", style = MaterialTheme.typography.titleSmall)
                                                    OutlinedTextField(
                                                        value = testInput,
                                                        onValueChange = { testInput = it; manualMode = true },
                                                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                                                        label = { Text("把失败那一笔原文贴进来") },
                                                    )
                                                    Button(onClick = { manualMode = true }) { Text("试解析") }
                                                    InfoCard("试解析结果", listOf(
                                                        "来源" to sourceText(preview.source),
                                                        "字符数" to "${preview.rawText.length} / ${preview.originalCharCount}" +
                                                            if (preview.truncated) "（已截断）" else "",
                                                        "页面特征数" to "${preview.commitDraft?.pageMarkers ?: 0}",
                                                        "主金额" to preview.commitDraft?.amountYuan.orEmpty().ifEmpty { "未找到" },
                                                        "全部金额候选" to preview.amountCandidates
                                                            .ifEmpty { listOf("未找到") }.joinToString("、"),
                                                        "方向 / 时间 / 收款方" to
                                                            "${yesNo(preview.hasDirection)} / ${yesNo(preview.hasTime)} / ${yesNo(preview.hasCounterparty)}",
                                                        "严格门槛" to yesNo(preview.readyForAutoCommit),
                                                    ))
                                                    if (preview.missing.isNotEmpty()) {
                                                        Text(
                                                            "未达标：" + preview.missing.joinToString("；"),
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                                    if (preview.warnings.isNotEmpty()) {
                                                        Text(
                                                            "提示：" + preview.warnings.joinToString("；"),
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // ── 分区 3：记录 ──
                                        else -> {
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("最近结果摘要（不含原文）", style = MaterialTheme.typography.titleSmall)
                                                    Text(logText.ifBlank { "（暂无）" }, style = MaterialTheme.typography.bodySmall)
                                                    TextButton(onClick = {
                                                        intakeLog.value = ""
                                                        prefs.textIntakeLog = ""
                                                    }) { Text("清除留痕") }
                                                }
                                            }
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text("本次收到的原文", style = MaterialTheme.typography.titleSmall)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    SelectionContainer {
                                                        Text(
                                                            text = rawText.ifEmpty { "（已清空）" },
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 只在 Activity 级调用一次；写事务在 lifecycleScope，不受 composition 取消影响。 */
    private fun triggerAutoCommit() {
        if (autoCommitStarted || initialRaw.isBlank() || source == ACTION_DEBUG) return
        if (!prefs.textIntakeAutoCommitEnabled) {
            // 仅草稿模式：不写状态，Tab 0 的结果条会说明，草稿由 inspector 直接给出。
            return
        }
        autoCommitStarted = true
        lifecycleScope.launch {
            val rules = TextIntakeRules.withPatch(prefs.textIntakeRulePatch).rules
            val p = TextIntakeInspector.from(source, initialRaw, rules)
            activeDraft.value = p.commitDraft
            if (p.readyForAutoCommit && p.commitDraft != null) {
                applyOutcome(commitSafely(p.commitDraft!!), "自动", p.commitDraft!!)
            } else {
                intakeStatus.value = UiStatus(
                    "DRAFT",
                    "未达严格充分条件，已降级草稿：" + p.missing.joinToString("；"),
                )
                appendLog("草稿", p.commitDraft, "未达严格门槛")
            }
        }
    }

    /** CancellationException MUST 原样抛出，绝不伪装成业务失败。 */
    private suspend fun commitSafely(draft: TextIntakeCommitDraft): TextIntakeCommitOutcome = try {
        repository.commitTextIntake(draft)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TextIntakeCommitOutcome.Rejected("写入失败：" + (e.message ?: e.javaClass.simpleName))
    }

    private fun saveManual(draft: TextIntakeCommitDraft) {
        lifecycleScope.launch { applyOutcome(commitSafely(draft), "手工", draft) }
    }

    private fun undo(id: Long) {
        lifecycleScope.launch {
            try {
                repository.deleteRecord(id)
                intakeStatus.value = UiStatus("UNDONE", "已撤销，余额已重算")
                appendLog("撤销", activeDraft.value, "已撤销")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                intakeStatus.value = UiStatus("REJECTED", "撤销失败：" + (e.message ?: "未知错误"))
            }
        }
    }

    private fun applyOutcome(
        outcome: TextIntakeCommitOutcome,
        mode: String,
        draft: TextIntakeCommitDraft,
    ) {
        activeDraft.value = draft
        when (outcome) {
            is TextIntakeCommitOutcome.Inserted -> {
                intakeStatus.value = UiStatus("INSERTED", outcome.message, outcome.recordId)
                appendLog(mode, draft, "已记账")
            }
            is TextIntakeCommitOutcome.Duplicate -> {
                intakeStatus.value = UiStatus("DUPLICATE", outcome.message)
                appendLog(mode, draft, "已存在")
            }
            is TextIntakeCommitOutcome.Rejected -> {
                intakeStatus.value = UiStatus("REJECTED", outcome.reason)
                appendLog(mode, draft, "降级草稿")
            }
        }
    }

    private fun appendLog(kind: String, draft: TextIntakeCommitDraft?, reason: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val amount = draft?.amountCents?.let { centsToYuan(it) } ?: "--"
        val sourceTail = draft?.sourceId?.takeLast(6) ?: "------"
        val line = "$ts | $kind | ${amount}元 | 来源尾号$sourceTail | $reason"
        val next = (intakeLog.value + "\n" + line)
            .lines().filter { it.isNotBlank() }.takeLast(20).joinToString("\n")
        prefs.textIntakeLog = next
        intakeLog.value = next
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
            // 第 88 批：HyperOS 等 ROM 的「全选 → 分享」可能只把文本放进 clipData，
            // EXTRA_TEXT 为空。不兜底会落到 "empty"，页面显示「未收到文本」，
            // 于是既不自动落库也不形成预填草稿。
            val clip = try {
                intent.clipData?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.text?.toString()
            } catch (_: Exception) {
                null
            }
            if (!clip.isNullOrEmpty()) return "send" to clip
            return "empty" to ""
        }

        fun copyRaw(context: Context, text: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("账单原文", text))
        }

        /** 离开本会话后，复制这段给任意 AI，即可生成可粘贴的规则增补行。 */
        const val RULE_PROMPT = """你是「离线账本」Android App 的自动记账规则助手。

我会贴一段从手机系统「全选 → 分享」得到的账单原文（我会先手动删掉订单号、卡号尾号等敏感信息），以及 App 里显示的「未达标」提示。

当前默认规则基线：
- 状态词：交易成功 / 自动扣款成功 / 支付成功 / 付款成功
- 优惠词：红包 / 立减 / 券 / 满减 / 折扣 / 优惠 / 减免
- 噪声：账单管理 / 支付奖励 / 收单机构 / 清算机构 / 标签 / 请选择 / 为您推荐 / 计入收支 / 备注 / 添加 / AA收款 / 查看往来记录 / 对此订单有疑问 / logo / arrow / extend-icon 等
- 分类映射：餐饮美食 = 餐饮
- 本项目一级分类只有：餐饮 / 交通 / 养车 / 居住 / 日常 / 购物 / 医疗 / 学习娱乐 / 宠物 / 人情社交 / 其他

任务：判断这段原文为什么没有达到自动落库门槛，只输出需要增补的规则行。每行一条，不要解释、不要输出正则 / JSON / 代码 / 其他文字。

只允许这四种格式：
状态词 += 新状态词
优惠词 += 新优惠关键词
噪声 += 新噪声行
分类 支付宝账单分类 = 本项目一级分类名

约束：
1. 只增补词表与分类映射，不要改金额规则、主金额位置、优惠算术校验或自动落库门槛。
2. 没有对应的一级分类就不要输出分类映射，留空让我自己配；不要硬猜。
3. 若失败原因是字段位置 / 区块结构变了，而不是某个词没覆盖，只输出一行：# 结构变化，需要改代码并发版
4. 最多 3 行；多个词可用中文逗号写在同一行。

示例输出：
状态词 += 自动扣款成功
优惠词 += 立减金
"""
    }
}

/** 置顶常驻的结果条：失败原因第一屏可见，不用在长页面里找。 */
@Composable
private fun ResultBanner(
    status: UiStatus?,
    rawCount: Int,
    sourceLabel: String,
    external: Boolean,
    autoEnabled: Boolean,
    manualMode: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("本次入口状态 / 处理结果", style = MaterialTheme.typography.titleSmall)
            val (kind, message) = when {
                status != null -> status.kind to status.message
                manualMode -> "试解析" to "试解析模式：不会自动写库。"
                !external || rawCount == 0 ->
                    "未收到文本" to "未收到文本。请确认从系统「全选 → 分享 → 离线账本 · 自动记账」进入；" +
                        "若仍为空，把原文贴进「规则与试解析」页，或复制给我排查。"
                !autoEnabled -> "仅草稿" to "当前仅草稿模式：不会自动写库，可在下方改好后手工保存。"
                else -> "处理中" to "正在处理…"
            }
            Text(
                text = "处理结果：$kind",
                style = MaterialTheme.typography.titleMedium,
                color = when (kind) {
                    "INSERTED" -> MaterialTheme.colorScheme.primary
                    "REJECTED", "未收到文本" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(message, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "已收到 $rawCount 字符 · 来源：$sourceLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, rows: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            rows.forEach { (label, value) -> InfoRow(label, value) }
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

private fun yesNo(hit: Boolean): String = if (hit) "✓" else "✗"

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
