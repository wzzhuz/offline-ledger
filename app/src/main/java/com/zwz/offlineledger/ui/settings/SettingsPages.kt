package com.zwz.offlineledger.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.currency.LedgerCurrency
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors
import com.zwz.offlineledger.ui.components.formatCents

@Composable
internal fun settingsVm(): SettingsViewModel {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            SettingsViewModel(
                ep.ledgerRepository(), ep.categoryDao(), ep.accountDao(),
                ep.recordDao(), LedgerPrefs(context)
            )
        }
    )
    return vm
}

/**
 * 第 12 批：可选 onAdd 让管理类二级页把「新增」固定在 TopBar，
 * 不随列表条目增多而下移。其余 6 个二级页不传该参数，零影响。
 */
@Composable
fun SettingsPageTopBar(
    title: String,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "新增")
            }
        }
    }
}

@Composable
fun SettingGroupCard(title: String, summary: String, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = LedgerColors.Neutral
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LedgerColors.Neutral.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// --------------------------------------------------------------- 数据与备份
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    onOpenFirefly: () -> Unit = {},
    onOpenSnapshots: () -> Unit = {},
    onOpenBulk: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm = settingsVm()
    val state by vm.state.collectAsState()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var passwordMode by remember { mutableStateOf<PasswordMode?>(null) }
    var showCsvHelp by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            passwordMode = PasswordMode.EXPORT
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            passwordMode = PasswordMode.IMPORT
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) vm.exportCsvTo(context, uri) }
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.readCsvPlan(context, uri) }
    val csvRebuildLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.readCsvPlan(context, uri, rebuild = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SettingsPageTopBar("数据与备份", onBack)
        Text(
            text = "备份由你选择保存位置，口令加密后写出；应用不申请任何存储或网络权限。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { exportLauncher.launch("offline-ledger-backup.olbk") },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("导出加密备份", maxLines = 1) }
            Button(
                onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("从备份恢复", maxLines = 1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { csvExportLauncher.launch("offline-ledger.csv") },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("导出 CSV（Excel 核对用）", maxLines = 1) }
            Button(
                onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/plain", "*/*")) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("导入账单（第三方账单）", maxLines = 1) }
        }
        Button(
            onClick = { csvRebuildLauncher.launch(arrayOf("text/csv", "text/plain", "*/*")) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("从 CSV 重建", maxLines = 1) }
        Button(
            onClick = onOpenFirefly,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("导入 Firefly III 导出（CSV）", maxLines = 1) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpenSnapshots,
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("自动快照与恢复", maxLines = 1) }
            Button(
                onClick = onOpenBulk,
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("批量规整", maxLines = 1) }
        }
        Text(
            text = "CSV 为明文文件，由你自行保管；应用不会上传任何数据。" +
                "「导入账单」吃支付宝 / 微信 / 银行 / Firefly III 等第三方账单；" +
                "「从 CSV 重建」吃本 App 自己导出的 11 列文件，仅支出 / 收入，" +
                "且需当前库已存在同名账户与分类，缺项会在结果里逐项列出并跳过；" +
                "清库 / 换机后的完整重建（含转账、账户、模板、别名）" +
                "请用加密备份或自动快照，不要用 CSV。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
        TextButton(onClick = { showCsvHelp = !showCsvHelp }) {
            Text(if (showCsvHelp) "收起导入字段说明" else "查看导入字段说明与示例")
        }
        if (showCsvHelp) {
            Text(
                text = "首行必须是表头，逗号或制表符分隔，列顺序固定：\n" +
                    "date 日期 YYYY-MM-DD（必填）\n" +
                    "type 类型：EXPENSE 支出 / INCOME 收入（必填）\n" +
                    "amount 金额：元，可带两位小数（必填）\n" +
                    "account 账户名：需与 App 内账户名完全一致（必填）\n" +
                    "categoryRoot 一级分类名（可选）\n" +
                    "category 二级分类名（可选）\n" +
                    "note 备注（可选）\n" +
                    "currencyCode 币种：CNY / HKD / USD，留空按账户继承（可选）\n" +
                    "counterparty 收款方或付款方（可选，缺列按空处理）\n" +
                    "importSource 导入来源（可选，导出必带，如 FIREFLY_III / CSV）\n" +
                    "sourceId 来源系统内的稳定流水号（可选，导出必带）\n" +
                    "示例：2026-11-15,EXPENSE,38.50,中国银行7375,餐饮,正餐,午餐,CNY,好优特生活超市,,\n" +
                    "导入前整体校验，任何一行不合法都不写入。" +
                    "导出文件带 importSource/sourceId；「从 CSV 重建」仅支出 / 收入、" +
                    "需已有同名账户与分类，缺项逐项列出，转账行明确跳过；" +
                    "清库 / 换机全量重建请用「从加密备份恢复」或「自动快照与恢复」。" +
                    "二次重建按复合唯一键幂等、不会双份。" +
                    "手工记录与第三方账单另按「金额 + 账户 + ±1 天」给疑似匹配，逐笔确认后才写。",
                style = MaterialTheme.typography.labelSmall,
                color = LedgerColors.Neutral
            )
        }
        if (state.message.isNotBlank()) {
            Text(text = state.message, style = MaterialTheme.typography.bodySmall)
        }
        if (state.error.isNotBlank()) {
            Text(
                text = state.error,
                color = LedgerColors.ExpenseRed,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    state.csvPlan?.let { plan ->
        val willWrite = plan.rows.size - plan.skipRowIndexes.size
        AlertDialog(
            onDismissRequest = { vm.cancelCsvImport() },
            title = { Text(if (plan.rebuild) "从 CSV 重建" else "导入账单") },
            text = {
                Column {
                    Text(
                        "共 " + plan.rows.size + " 条，确认写入 " + willWrite +
                            " 条；重复项自动跳过。"
                    )
                    if (plan.rebuild) {
                        Text(
                            text = "自有导出文件回灌：需当前库已有同名账户与分类；" +
                                "缺账户 / 分类的行会跳过并在结果里列出，不会整批失败。" +
                                "清库 / 换机请改用「从加密备份恢复」或「自动快照与恢复」。",
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    if (plan.candidates.isNotEmpty()) {
                        Text(
                            text = "疑似匹配 " + plan.candidates.size +
                                " 项（金额 + 账户 + ±1 天）。默认勾选＝跳过该行，" +
                                "取消勾选＝仍导入；不会自动合并或删除已有流水。",
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(plan.candidates) { c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = c.rowIndex in plan.skipRowIndexes,
                                        onCheckedChange = {
                                            vm.toggleCsvCandidate(c.rowIndex)
                                        }
                                    )
                                    Text(
                                        text = "第 " + (c.rowIndex + 2) + " 行 → 已有 " +
                                            formatCents(c.amountCents) + " 元 · " +
                                            c.localDate + " · " + c.existingNote.take(14),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "确认后一次性写入；失败会整体回滚，账本不变。",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmCsvImport() }) { Text("确认写入") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelCsvImport() }) { Text("取消") }
            }
        )
    }

    passwordMode?.let { mode ->
        BackupPasswordDialog(
            mode = mode,
            initialPassphrase = com.zwz.offlineledger.backup.snapshot
                .SnapshotPrefs.loadPassphrase(context),
            onDismiss = { passwordMode = null; pendingUri = null },
            onConfirm = { pass, rememberOnDevice ->
                val uri = pendingUri
                if (uri != null) {
                    if (rememberOnDevice) {
                        com.zwz.offlineledger.backup.snapshot
                            .SnapshotPrefs.savePassphrase(context, pass)
                    }
                    if (mode == PasswordMode.EXPORT) vm.exportTo(context, uri, pass)
                    else vm.importFrom(context, uri, pass)
                }
                passwordMode = null
                pendingUri = null
            }
        )
    }
}

// --------------------------------------------------------------- 安全
@Composable
fun SecuritySettingsScreen(onBack: () -> Unit) {
    val vm = settingsVm()
    val state by vm.state.collectAsState()
    var pinInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SettingsPageTopBar("安全", onBack)
        SettingSwitchRow("应用锁（冷启动与后台恢复）", state.appLockEnabled) {
            vm.setAppLock(it)
        }
        Text(
            text = "开启前必须先设置 PIN；生物识别不可用时用 PIN 解锁。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it.filter(Char::isDigit) },
                label = { Text(if (state.hasPin) "重设 PIN（4-12 位数字）" else "设置 PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                if (pinInput.isNotBlank()) {
                    vm.setPin(pinInput)
                    pinInput = ""
                }
            }) { Text("保存") }
        }
        if (state.hasPin) {
            TextButton(onClick = { vm.clearPin() }) {
                Text("清除 PIN（同时关闭应用锁）", color = LedgerColors.ExpenseRed)
            }
        }
        SettingSwitchRow("最近任务防泄露（FLAG_SECURE）", state.flagSecure) {
            vm.setFlagSecure(it)
        }
        Text(
            text = "PIN 为加盐摘要，用于防瞥视与丢机随手翻，不是强认证；" +
                "数据库口令由 Android Keystore 独立保管。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
    }
}

// --------------------------------------------------------------- 偏好
@Composable
fun PreferenceSettingsScreen(onBack: () -> Unit) {
    val vm = settingsVm()
    val state by vm.state.collectAsState()
    var thresholdInput by remember { mutableStateOf(state.thresholdYuan) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SettingsPageTopBar("偏好", onBack)
        OutlinedTextField(
            value = thresholdInput,
            onValueChange = { thresholdInput = it; vm.setThresholdYuan(it) },
            placeholder = { Text("对账尾差阈值（元，默认 1.00）") },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        SettingSwitchRow("支出 / 收入配色反转", state.reverseColors) {
            vm.setReverseColors(it)
        }
        SettingSwitchRow("隐藏金额（公共场合翻账，只遮显示）", state.hideAmounts) {
            vm.setHideAmounts(it)
        }
        Text(
            text = "开启后统计 / 明细 / 资产页的金额显示为 ****，数据本身不变，也不会写进备份。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
        Text(
            text = "默认币种（只影响之后新建的账户）",
            style = MaterialTheme.typography.labelMedium
        )
        Box {
            TextButton(onClick = { currencyExpanded = true }) {
                Text(state.defaultCurrency, style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(
                expanded = currencyExpanded,
                onDismissRequest = { currencyExpanded = false }
            ) {
                LedgerCurrency.SUPPORTED.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(code) },
                        onClick = { vm.setDefaultCurrency(code); currencyExpanded = false }
                    )
                }
            }
        }
        Button(onClick = { vm.applyCurrencyToAll() }, modifier = Modifier.fillMaxWidth()) {
            Text("把现有账户与流水统一为 ${state.defaultCurrency}")
        }
        Text(
            text = "统一币种只改标签、不改金额；不会自动执行，需你点上面的按钮。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
        Text(
            text = "默认账户（记一笔与模板未指定账户时使用）",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        val chosenAccount = state.accounts.firstOrNull { it.id == state.defaultAccountId }
        Box {
            TextButton(onClick = { accountExpanded = true }) {
                Text(
                    chosenAccount?.name ?: "未设置",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            DropdownMenu(
                expanded = accountExpanded,
                onDismissRequest = { accountExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("未设置（自动取第一个常用账户）") },
                    onClick = { vm.setDefaultAccountId(0L); accountExpanded = false }
                )
                state.accounts.filter { !it.isArchived }.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.name + " · " + a.currencyCode) },
                        onClick = { vm.setDefaultAccountId(a.id); accountExpanded = false }
                    )
                }
            }
        }
        Text(
            text = "模板里的账户可以留空；记账时会用这里选的默认账户补上。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
    }
}

// --------------------------------------------------------------- 账本
@Composable
fun LedgerSettingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm = settingsVm()
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SettingsPageTopBar("账本", onBack)
        SettingGroupCard("分类管理", "两级 · 新增 / 改名 / 排序 / 归档") {
            onOpen("categories")
        }
        SettingGroupCard("模板与周期记账", "模板 / 规则 · 新增 / 归档 / 启用") {
            onOpen("templates")
        }
        SettingGroupCard("预算管理", "总预算 / 一级 / 二级 · 新增 / 归档") {
            onOpen("budgets")
        }
        SettingGroupCard("回收站", "恢复或彻底删除已删除流水") {
            onOpen("trash")
        }
        Button(
            onClick = { vm.runHealthCheck() },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("账本健康自检") }
        if (state.healthText.isNotBlank()) {
            Text(
                text = state.healthText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (state.message.isNotBlank()) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (state.error.isNotBlank()) {
            Text(
                text = state.error,
                color = LedgerColors.ExpenseRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
