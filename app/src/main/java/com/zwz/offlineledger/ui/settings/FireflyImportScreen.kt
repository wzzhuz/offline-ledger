package com.zwz.offlineledger.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.backup.csv.FireflyAccountChoice
import com.zwz.offlineledger.backup.csv.FireflyOpeningPolicy
import com.zwz.offlineledger.backup.csv.FireflyCategoryChoice
import com.zwz.offlineledger.backup.csv.FireflyCsvCodec
import com.zwz.offlineledger.core.accountLabel
import com.zwz.offlineledger.ui.components.CategorySelectDialog
import com.zwz.offlineledger.ui.components.formatCents
import com.zwz.offlineledger.ui.assets.accountBaseLabel
import com.zwz.offlineledger.backup.csv.FireflyImportOutcome
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.local.entity.CategoryEntity
import com.zwz.offlineledger.data.model.RecordType
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 22 批：Firefly III 迁移二级页。
 * 选文件 -> 全量解析 -> 账户/分类映射预览 -> 二次确认 -> 单事务写入。
 * 不新增 NavHost 之外的依赖，不申请任何权限；
 * 第 23 批起库结构为 v2（导入来源唯一索引），安装需卸载重装。
 */
@Composable
fun FireflyImportScreen(
    onBack: () -> Unit,
    onGoDetail: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm = settingsVm()
    val state by vm.state.collectAsState()
    val st = state.firefly
    var showConfirm by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.readFireflyPlan(context, uri)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val result = state.fireflyResult

    /** 第 25 批：结果态出现时清掉确认弹窗并给一次可见反馈；失败也报原因。 */
    LaunchedEffect(result) {
        if (result != null) {
            showConfirm = false
            snackbarHostState.showSnackbar(
                if (result.success) {
                    "迁移完成：写入 " + result.written + " 条、跳过 " +
                        result.skipped + " 条重复"
                } else {
                    "迁移未写入：" + result.errorMessage
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            SettingsPageTopBar("导入 Firefly III", onBack)
            when {
                // 结果态是整页视图，不再只是顶部一张容易被滚过去的卡片。
                result != null -> FireflyResultView(
                    result = result,
                    onGoDetail = onGoDetail,
                    onClose = { vm.clearFireflyResult() }
                )
                st == null -> {
                    Button(
                        onClick = {
                            launcher.launch(arrayOf("text/csv", "text/plain", "*/*"))
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("选择 transactions.csv") }
                    Text(
                        text = "在 Firefly III 导出页面生成的 transactions.csv，" +
                            "由你通过系统文件选择器选中；应用不联网、不申请权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = "支出取来源账户、收款方取对方；收入取目标账户、" +
                            "付款方取对方；转账双方都建成账户；期初余额转成账户期初。" +
                            "拆分交易按原行逐条导入，备注保留分组名与 ff3 流水号用于追溯。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "不会导入：规则、订阅、附件、存钱罐、标签系统与预算；" +
                            "外币按主币种落账不折算；Reconciliation 行单独报告不计入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                else -> {
                    Text(
                        text = "识别到 " + st.plan.recordCount + " 条流水、" +
                            st.plan.openingCount + " 条期初余额、" +
                            st.plan.skippedCount + " 条跳过；需映射 " +
                            st.accounts.size + " 个账户、" + st.categories.size + " 个分类。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (st.plan.warnings.isNotEmpty()) {
                        Text(
                            text = "提示：" + st.plan.warnings.take(3).joinToString("；"),
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral
                        )
                    }
                    // 第 74 批（Q2A）：布尔开关 → 三态策略。
                    Text(
                        text = "已有账户的期初余额",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FireflyOpeningPolicy.values().forEach { p ->
                            FilterChip(
                                selected = st.openingPolicy == p,
                                onClick = { vm.setFireflyOpeningPolicy(p) },
                                label = {
                                    Text(
                                        when (p) {
                                            FireflyOpeningPolicy.KEEP -> "保持"
                                            FireflyOpeningPolicy.OVERWRITE -> "覆盖"
                                            FireflyOpeningPolicy.ADJUST -> "差额调整"
                                        }
                                    )
                                },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                    Text(
                        text = when (st.openingPolicy) {
                            FireflyOpeningPolicy.KEEP ->
                                "保持（默认）：完全不动已有账户的期初，FF3 期初行计入跳过报告；" +
                                    "新建账户仍用导出值。"
                            FireflyOpeningPolicy.OVERWRITE ->
                                "覆盖：把 FF3 期初写入 initialBalanceCents。已有流水的账户会" +
                                    "整体平移历史余额、已对账余额也会变；改名映射同样生效。"
                            FireflyOpeningPolicy.ADJUST ->
                                "差额调整：不改写历史起点，按「FF3 期初 − 当前期初」走既有" +
                                    "调整机制；最终余额与覆盖一致，但已对账余额不变。" +
                                    "第二次导入不会重复累加。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral
                    )
                    // 运行态：明确显示「正在写入 N 条」与进度，而不是只有按钮置灰。
                    if (state.busy) {
                        Text(
                            text = "正在写入 " + st.plan.recordCount +
                                " 条流水，请稍候（单事务，完成前不会留下半套数据）",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Button(
                                onClick = { showConfirm = true },
                                modifier = Modifier.weight(1f)
                            ) { Text("确认写入") }
                            TextButton(onClick = { vm.cancelFireflyImport() }) { Text("取消") }
                        }
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Text(
                                text = "账户映射（" + st.accounts.size + "）",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(st.accounts) { choice ->
                            FireflyAccountRow(choice, state.accounts, st.openingBySource) {
                                vm.updateFireflyAccount(it)
                            }
                        }
                        item {
                            Text(
                                text = "分类映射（" + st.categories.size + "）",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(st.categories) { choice ->
                            FireflyCategoryRow(choice, state.categories) {
                                vm.updateFireflyCategory(it)
                            }
                        }
                    }
                }
            }
            if (result == null && state.message.isNotBlank()) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (result == null && state.error.isNotBlank()) {
                Text(
                    text = state.error,
                    color = LedgerColors.ExpenseRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showConfirm && st != null && result == null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认迁移") },
            text = {
                Text(
                    "将写入 " + st.plan.recordCount + " 条流水，并按需新建账户与分类。" +
                        "确认后在单个事务内完成；任一行失败整体回滚，账本不变。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    vm.confirmFireflyImport()
                }) { Text("确认写入") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("再看看") }
            }
        )
    }
}

@Composable
private fun FireflyResultView(
    result: FireflyImportOutcome,
    onGoDetail: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text(
            text = if (result.success) "迁移完成" else "迁移未写入",
            style = MaterialTheme.typography.headlineSmall,
            color = if (result.success) MaterialTheme.colorScheme.onSurface
            else LedgerColors.ExpenseRed
        )
        Text(
            text = "写入 " + result.written + " 条流水 · 跳过 " +
                result.skipped + " 条重复",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "耗时 " + result.durationMs + " ms · 共 " + result.totalRows + " 行",
            style = MaterialTheme.typography.bodyMedium,
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (!result.success) {
            Text(
                text = "失败原因：" + result.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerColors.ExpenseRed,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (result.failRows.isNotEmpty()) {
            Text(
                text = "失败行号：" + result.failRows.joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.ExpenseRed,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "新建账户 " + result.newAccounts + " 个 · 新建分类 " +
                        result.newCategories + " 个",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "期初余额应用 " + result.openingsApplied + " 条 · 跳过 " +
                        result.openingsSkipped + " 条",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = if (result.success) {
                        "余额已重算；任一行失败会整体回滚，不会留下半套数据。"
                    } else {
                        "账本未改动；可返回重试同一份文件。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Row {
            if (result.success) {
                Button(onClick = onGoDetail) { Text("去看明细") }
            }
            TextButton(onClick = onClose) { Text("完成") }
        }
        if (result.success) {
            Text(
                text = "再次导入同一份文件会全部跳过，不会写入重复流水。",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun FireflyAccountRow(
    choice: FireflyAccountChoice,
    existing: List<AccountEntity>,
    openingBySource: Map<String, Long>,
    onChange: (FireflyAccountChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = choice.sourceName, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (choice.isNew) {
                    "新建 · " + choice.type
                } else "映射到 · " +
                    (accountLabel(choice.targetName, choice.targetCardLast4)
                        ?: choice.targetName),
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
            // 第 74 批：把「空记录 / 有记录」判断所需的两值直接摆出来，
            // 免得使用者在覆盖期初前还要去资产页翻当前期初。
            if (!choice.isNew) {
                val mapped = existing.firstOrNull {
                    !it.isArchived && it.name == choice.targetName &&
                        (choice.targetCardLast4 == null ||
                            it.cardLast4 == choice.targetCardLast4)
                }
                if (mapped != null) {
                    val ff3 = openingBySource[choice.sourceName]
                    Text(
                        text = "当前期初 ¥" + formatCents(mapped.initialBalanceCents) +
                            " · FF3 期初 " +
                            (ff3?.let { "¥" + formatCents(it) } ?: "无（不动）"),
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral
                    )
                }
            }
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text("选择") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("新建：" + choice.sourceName) },
                    onClick = {
                        onChange(
                            choice.copy(
                                targetName = "", isNew = true,
                                targetCardLast4 = null,
                                type = FireflyCsvCodec.inferAccountType(choice.sourceName)
                            )
                        )
                        expanded = false
                    }
                )
                existing.filter { !it.isArchived }.forEach { a ->
                    // 第 63 批：同名两卡在下拉里靠展示名区分，
                    // targetName 仍是纯名，卡号单独带在 targetCardLast4。
                    DropdownMenuItem(
                        text = { Text(accountBaseLabel(a)) },
                        onClick = {
                            onChange(
                                choice.copy(
                                    targetName = a.name, isNew = false,
                                    targetCardLast4 = a.cardLast4,
                                    type = a.type.name
                                )
                            )
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FireflyCategoryRow(
    choice: FireflyCategoryChoice,
    categories: List<CategoryEntity>,
    onChange: (FireflyCategoryChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val label = choice.root + if (choice.child.isBlank()) "" else " / " + choice.child
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label + " · " + choice.type,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = when {
                    choice.skip -> "不设分类"
                    choice.targetCategoryId != null ->
                        "映射到 · " + choice.targetDisplayName
                    else -> "按原名自动新建"
                },
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
        }
        // 第 74 批（Q1A）：分类本身一律走全量只读选择器
        //（搜索 + 左分组导航 + 右彩色图标网格），MUST NOT 用下拉框逐个列分类。
        TextButton(onClick = { showPicker = true }) { Text("选择分类") }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多映射方式")
            }
            // 这个下拉里**只有两个动作**，不含任何分类项。
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("按原名自动新建") },
                    onClick = {
                        onChange(
                            choice.copy(
                                targetCategoryId = null,
                                targetDisplayName = "",
                                targetRoot = "",
                                skip = false
                            )
                        )
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("不设分类") },
                    onClick = {
                        onChange(choice.copy(skip = true))
                        expanded = false
                    }
                )
            }
        }
    }

    if (showPicker) {
        CategorySelectDialog(
            categories = categories,
            // FF3 的 EXPENSE / INCOME 各自建树，选择器 MUST 按同类型过滤，
            // 免得把支出流水映射到收入分类。
            type = try {
                RecordType.valueOf(choice.type)
            } catch (e: IllegalArgumentException) {
                null
            },
            selectedId = choice.targetCategoryId,
            allowAll = false,
            title = "映射「" + label + "」到分类",
            onSelect = { c ->
                onChange(
                    choice.copy(
                        targetCategoryId = c.categoryId,
                        targetDisplayName = c.displayLabel,
                        targetRoot = "",
                        skip = false
                    )
                )
            },
            onDismiss = { showPicker = false }
        )
    }
}
