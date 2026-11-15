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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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

@Composable
internal fun settingsVm(): SettingsViewModel {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            SettingsViewModel(ep.ledgerRepository(), ep.categoryDao(), LedgerPrefs(context))
        }
    )
    return vm
}

@Composable
internal fun SettingsPageTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回")
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
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
fun BackupSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = settingsVm()
    val state by vm.state.collectAsState()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var passwordMode by remember { mutableStateOf<PasswordMode?>(null) }

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
            ) { Text("导出加密备份") }
            Button(
                onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("从备份恢复") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { csvExportLauncher.launch("offline-ledger.csv") },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("导出 CSV") }
            Button(
                onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/plain", "*/*")) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("导入账单") }
        }
        Text(
            text = "CSV 为明文文件，由你自行保管；应用不会上传任何数据。" +
                "导入使用 CSV/TSV 模板：date, type, amount, account, " +
                "categoryRoot, category, note, currencyCode, counterparty" +
                "（币种列导入时按账户继承；counterparty 列可选，缺列按空处理）。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )
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
        AlertDialog(
            onDismissRequest = { vm.cancelCsvImport() },
            title = { Text("导入账单") },
            text = {
                Text(
                    "将写入 ${plan.rows.size} 条流水（重复项自动跳过）。" +
                        "确认后一次性写入；失败会整体回滚，账本不变。"
                )
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
            onDismiss = { passwordMode = null; pendingUri = null },
            onConfirm = { pass ->
                val uri = pendingUri
                if (uri != null) {
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
            label = { Text("对账尾差阈值（元，默认 1.00）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        SettingSwitchRow("支出 / 收入配色反转", state.reverseColors) {
            vm.setReverseColors(it)
        }
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
