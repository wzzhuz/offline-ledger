package com.zwz.offlineledger.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.templates.TemplateManagerDialog
import com.zwz.offlineledger.ui.theme.LedgerColors
import com.zwz.offlineledger.ui.trash.TrashScreen

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            SettingsViewModel(ep.ledgerRepository(), ep.categoryDao(), LedgerPrefs(context))
        }
    )
    val state by vm.state.collectAsState()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var passwordMode by remember { mutableStateOf<PasswordMode?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var thresholdInput by remember { mutableStateOf(state.thresholdYuan) }

    // SAF：CreateDocument / OpenDocument 均不申请任何存储权限。
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(text = "设置", style = MaterialTheme.typography.headlineMedium)

        SectionTitle("数据与备份")
        Text(
            text = "备份由你选择保存位置，口令加密后写出；应用不申请任何存储或网络权限。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral
        )
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
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
        Text(
            text = "v1 不提供 CSV 导出（明文文件，列在 v1.1 清单）。",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerColors.Neutral
        )

        SectionTitle("安全")
        SettingSwitch("应用锁（冷启动与后台恢复）", state.appLockEnabled) {
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
                label = { Text(if (state.hasPin) "重设 PIN（4–12 位数字）" else "设置 PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                ),
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
        SettingSwitch("最近任务防泄露（FLAG_SECURE）", state.flagSecure) {
            vm.setFlagSecure(it)
        }

        SectionTitle("偏好")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = thresholdInput,
                onValueChange = { thresholdInput = it; vm.setThresholdYuan(it) },
                label = { Text("对账尾差阈值（元，默认 1.00）") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.weight(1f)
            )
        }
        SettingSwitch("支出 / 收入配色反转", state.reverseColors) {
            vm.setReverseColors(it)
        }

        SectionTitle("账本")
        Button(onClick = { showCategories = true }, modifier = Modifier.fillMaxWidth()) {
            Text("分类管理（两级 · 新增 / 改名 / 排序 / 归档）")
        }
        Button(
            onClick = { showTemplates = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("模板与周期记账（模板 / 规则 · 新增 / 归档 / 启用）") }
        Button(
            onClick = { vm.runHealthCheck() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("账本健康自检") }
        if (state.healthText.isNotBlank()) {
            Text(text = state.healthText, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { showTrash = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("回收站") }

        if (state.message.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (state.error.isNotBlank()) {
            Text(
                text = state.error,
                color = LedgerColors.ExpenseRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Text(
            text = "隐私基线：不声明 INTERNET 权限 · 关闭系统自动备份并排除数据库 · " +
                "SQLCipher 加密数据库 · 口令由 Android Keystore 保管 · " +
                "备份口令不落盘，与数据库口令完全独立。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(top = 24.dp)
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

    if (showCategories) {
        CategoryManagerDialog(
            categories = state.categories,
            vm = vm,
            onDismiss = { showCategories = false }
        )
    }

    if (showTrash) {
        TrashScreen(
            onDismiss = { showTrash = false },
            onChanged = { vm.message("回收站已变更；如恢复了流水，返回明细即可刷新。") }
        )
    }

    if (showTemplates) {
        TemplateManagerDialog(onDismiss = { showTemplates = false })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
