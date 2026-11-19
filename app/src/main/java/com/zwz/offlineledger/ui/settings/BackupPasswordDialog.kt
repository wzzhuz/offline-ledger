package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.ui.theme.LedgerColors

enum class PasswordMode { EXPORT, IMPORT }

/**
 * 第 34 批：口令始终必填，但可勾选「在本机记住」，由 SnapshotPrefs
 * 经 AndroidKeystore 加密存本机，卸载即失效；文件本身永远是加密的。
 * 导入额外要求勾选「我明白将全量替换」。取消 MUST NOT 触发任何写操作。
 */
@Composable
fun BackupPasswordDialog(
    mode: PasswordMode,
    initialPassphrase: CharArray? = null,
    onDismiss: () -> Unit,
    onConfirm: (CharArray, Boolean) -> Unit
) {
    var pass by remember {
        mutableStateOf(initialPassphrase?.let { String(it) } ?: "")
    }
    var confirmed by remember { mutableStateOf(false) }
    var rememberOnDevice by remember { mutableStateOf(initialPassphrase != null) }
    var error by remember { mutableStateOf("") }
    val isImport = mode == PasswordMode.IMPORT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isImport) "从加密备份恢复" else "导出加密备份") },
        text = {
            Column {
                if (isImport) {
                    Text(
                        text = "将全量替换现有全部数据。当前账本会被覆盖，此操作不可撤销。",
                        color = LedgerColors.ExpenseRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "备份采用口令加密（PBKDF2 + AES-256/GCM）。" +
                            "文件始终加密；不勾选记住时口令不落盘，遗忘后无法恢复。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it; error = "" },
                    label = { Text(if (isImport) "备份口令" else "设置口令（至少 6 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Checkbox(
                        checked = rememberOnDevice,
                        onCheckedChange = { rememberOnDevice = it }
                    )
                    Text(
                        text = "在本机记住口令（Keystore 加密，卸载即失效）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isImport) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text(
                            text = "我明白将全量替换现有全部数据",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = LedgerColors.ExpenseRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    !isImport && pass.length < 6 -> error = "口令至少 6 位"
                    isImport && pass.isBlank() -> error = "请输入备份口令"
                    isImport && !confirmed -> error = "请先勾选覆盖确认"
                    else -> onConfirm(pass.toCharArray(), rememberOnDevice)
                }
            }) { Text(if (isImport) "确认恢复" else "确认导出") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
