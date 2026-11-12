package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.backup.BackupExporter
import com.zwz.offlineledger.backup.BackupImporter
import com.zwz.offlineledger.ui.LedgerDi
import com.zwz.offlineledger.ui.theme.LedgerColors
import com.zwz.offlineledger.ui.trash.TrashScreen
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val entryPoint = remember { LedgerDi.entryPoint(context) }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var showTrash by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(text = "设置", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "⚠️ 明文，勿外传：本阶段导出为未加密 JSON，只写入应用私有目录。" +
                "阶段 5 会替换为 SAF 选择 + 口令加密导出。",
            color = LedgerColors.ExpenseRed,
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            onClick = {
                scope.launch {
                    val json = BackupExporter.encode(
                        entryPoint.ledgerRepository().exportSnapshot()
                    )
                    val file = File(context.filesDir, "offline-ledger-backup.json")
                    file.writeText(json)
                    val back = BackupImporter.decode(json)
                    message = "已导出 ${file.name}（${json.length} 字符）；" +
                        "自校验通过，${back.payload.records.size} 条流水。" +
                        "文件位于应用私有目录，未申请任何存储权限。"
                }
            },
            modifier = Modifier.padding(top = 12.dp)
        ) { Text("导出明文 JSON（开发期）") }

        Button(
            onClick = {
                scope.launch {
                    val h = entryPoint.ledgerRepository().checkAndRepair()
                    message = "账本自检：检查 ${h.checkedAccounts} 个账户，" +
                        "修复 ${h.repairedAccounts} 个，偏差合计 ${h.driftCents} 分。"
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text("账本健康自检") }

        Button(
            onClick = { showTrash = true },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text("回收站") }

        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Text(
            text = "隐私基线：不声明 INTERNET 权限 · 关闭系统自动备份并排除数据库 · " +
                "SQLCipher 加密数据库 · 口令由 Android Keystore 保管。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(top = 24.dp)
        )
    }

    if (showTrash) {
        TrashScreen(
            onDismiss = { showTrash = false },
            onChanged = { message = "回收站已变更；如恢复了流水，返回明细下拉即可刷新。" }
        )
    }
}
