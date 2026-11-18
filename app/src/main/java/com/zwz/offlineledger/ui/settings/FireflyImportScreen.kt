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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.zwz.offlineledger.backup.csv.FireflyCategoryChoice
import com.zwz.offlineledger.backup.csv.FireflyCsvCodec
import com.zwz.offlineledger.backup.csv.FireflyImportOutcome
import com.zwz.offlineledger.data.local.entity.AccountEntity
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

    /** 第 24 批：结果态出现时给一次可见反馈，避免只靠「按钮置灰」猜完成。 */
    LaunchedEffect(result) {
        if (result != null) {
            snackbarHostState.showSnackbar(
                "迁移完成：写入 " + result.written + " 条、跳过 " +
                    result.skipped + " 条重复"
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "覆盖已有账户的期初余额",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = st.overwriteOpening,
                            onCheckedChange = { vm.setFireflyOverwriteOpening(it) }
                        )
                    }
                    Text(
                        text = "默认关闭：映射到已有账户时不动它现在的期初，" +
                            "对应期初行会跳过并计入报告；新建账户则用导出的期初值。",
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
                            FireflyAccountRow(choice, state.accounts) {
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
                            FireflyCategoryRow(choice) {
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

    if (showConfirm && st != null) {
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
        Text(text = "迁移完成", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "写入 " + result.written + " 条流水 · 跳过 " +
                result.skipped + " 条重复",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
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
                    text = "余额已重算；任一行失败会整体回滚，不会留下半套数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.Neutral,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Row {
            Button(onClick = onGoDetail) { Text("去看明细") }
            TextButton(onClick = onClose) { Text("完成") }
        }
        Text(
            text = "再次导入同一份文件会全部跳过，不会写入重复流水。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun FireflyAccountRow(
    choice: FireflyAccountChoice,
    existing: List<AccountEntity>,
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
                } else "映射到 · " + choice.targetName,
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
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
                                type = FireflyCsvCodec.inferAccountType(choice.sourceName)
                            )
                        )
                        expanded = false
                    }
                )
                existing.filter { !it.isArchived }.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.name) },
                        onClick = {
                            onChange(
                                choice.copy(
                                    targetName = a.name, isNew = false,
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
    onChange: (FireflyCategoryChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
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
                    choice.targetRoot.isBlank() -> "按原名自动新建"
                    else -> "复用一级 · " + choice.targetRoot
                },
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text("选择") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("按原名自动新建") },
                    onClick = {
                        onChange(choice.copy(targetRoot = "", skip = false))
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
}
