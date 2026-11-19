package com.zwz.offlineledger.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.backup.BackupImporter
import com.zwz.offlineledger.backup.crypto.SecureBackupCodec
import com.zwz.offlineledger.backup.snapshot.SnapshotPrefs
import com.zwz.offlineledger.backup.snapshot.SnapshotScheduler
import com.zwz.offlineledger.backup.snapshot.SnapshotWriter
import com.zwz.offlineledger.core.DataRefreshBus
import com.zwz.offlineledger.data.repository.LedgerRepository
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SnapshotUiState(
    val busy: Boolean = false,
    val message: String = "",
    val error: String = "",
    val treeReady: Boolean = false,
    val auto: Boolean = false,
    val hasPass: Boolean = false,
    val snapshots: List<Pair<String, Uri>> = emptyList()
)

class SnapshotViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(SnapshotUiState())
    val state: StateFlow<SnapshotUiState> = _state.asStateFlow()

    fun refresh(context: Context) {
        _state.update { st -> st.copy(
            treeReady = SnapshotPrefs.treeUri(context) != null,
            auto = SnapshotPrefs.autoEnabled(context),
            hasPass = SnapshotPrefs.hasPassphrase(context),
            snapshots = runCatching { SnapshotWriter.list(context) }.getOrDefault(emptyList())
        ) }
    }

    fun setTree(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        SnapshotPrefs.setTreeUri(context, uri.toString())
        refresh(context)
        _state.update { st -> st.copy(
            message = "快照目录已设置；卸载应用或清除数据不会删除该目录里的文件。",
            error = ""
        ) }
    }

    fun savePass(context: Context, pass: CharArray) {
        if (pass.isEmpty()) {
            _state.update { st -> st.copy(error = "口令不能为空") }
            return
        }
        runCatching { SnapshotPrefs.savePassphrase(context, pass) }
            .onSuccess {
                refresh(context)
                _state.update { st -> st.copy(
                    message = "自动快照口令已保存（经系统密钥库加密，明文不落盘）。",
                    error = ""
                ) }
            }
            .onFailure { e -> _state.update { st -> st.copy(
                error = "口令保存失败：" + (e.message ?: e.javaClass.simpleName)
            ) } }
    }

    fun setAuto(context: Context, on: Boolean) {
        if (on && (!SnapshotPrefs.hasPassphrase(context) || SnapshotPrefs.treeUri(context) == null)) {
            _state.update { st -> st.copy(error = "请先选择目录并设置口令") }
            return
        }
        SnapshotPrefs.setAutoEnabled(context, on)
        if (on) SnapshotScheduler.enable(context) else SnapshotScheduler.disable(context)
        refresh(context)
    }

    /** 导入/批量规整完成后也可由调用方触发；此处是手动一键。 */
    fun writeNow(context: Context) {
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "") }
            try {
                val result = withContext(Dispatchers.IO) {
                    val pass = SnapshotPrefs.loadPassphrase(context)
                        ?: error("请先设置自动快照口令")
                    SnapshotWriter.write(context, repository, pass)
                }
                refresh(context)
                _state.update { st -> st.copy(
                    busy = false,
                    message = "快照已生成：" + result.fileName + "（" + result.bytes +
                        " 字节，" + result.recordCount + " 条流水，清理旧文件 " +
                        result.pruned + " 个）。"
                ) }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "快照生成失败：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }

    /** 全量替换式恢复：解密与版本校验通过之前不写账本。 */
    fun restore(context: Context, uri: Uri, typed: CharArray) {
        viewModelScope.launch {
            _state.update { st -> st.copy(busy = true, message = "", error = "") }
            try {
                val text = withContext(Dispatchers.IO) {
                    val pass = if (typed.isNotEmpty()) typed
                    else (SnapshotPrefs.loadPassphrase(context) ?: error("请输入备份口令"))
                    val blob = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取快照文件")
                    val json = SecureBackupCodec.decode(blob, pass)
                    val file = BackupImporter.decode(json)
                    repository.importBackup(file)
                    val health = repository.checkAndRepair()
                    "恢复完成：" + file.payload.accounts.size + " 个账户、" +
                        file.payload.categories.size + " 个分类、" +
                        file.payload.records.size + " 条流水；自检 " +
                        health.checkedAccounts + " 个账户。"
                }
                DataRefreshBus.notifyChanged()
                _state.update { st -> st.copy(busy = false, message = text) }
            } catch (e: Exception) {
                _state.update { st -> st.copy(
                    busy = false,
                    error = "恢复失败，原账本未改动：" + (e.message ?: e.javaClass.simpleName)
                ) }
            }
        }
    }
}

@Composable
fun SnapshotSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SnapshotViewModel = viewModel(
        factory = ledgerFactory(context) { ep -> SnapshotViewModel(ep.ledgerRepository()) }
    )
    val state by vm.state.collectAsState()
    var pass by remember { mutableStateOf("") }
    var restoreTarget by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) vm.setTree(context, uri) }

    LaunchedEffect(Unit) { vm.refresh(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SettingsPageTopBar("自动快照与恢复", onBack)
        Text(
            text = "快照写入你通过系统选择器授权的目录（可指向 NAS 同步文件夹）。" +
                "卸载应用或清除数据不会删除该目录里的文件；重装后重新授权同一目录即可恢复。" +
                "快照是最近一次，不是卸载瞬间的零丢失。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral
        )
        Button(
            onClick = { treeLauncher.launch(null) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.treeReady) "更换快照目录" else "选择快照目录") }

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("备份口令（自动快照 / 加密备份 / 恢复共用）") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.savePass(context, pass.toCharArray()) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text("记住口令") }
            Button(
                onClick = { vm.writeNow(context) },
                enabled = !state.busy && state.treeReady,
                modifier = Modifier.weight(1f)
            ) { Text("立即生成快照") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "每日自动快照",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.auto,
                onCheckedChange = { vm.setAuto(context, it) },
                enabled = state.hasPass && state.treeReady
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
        Text(
            text = "目录里的快照（" + state.snapshots.size + "）",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp)
        )
        state.snapshots.forEach { (name, uri) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { restoreTarget = name to uri },
                    enabled = !state.busy
                ) { Text("恢复") }
            }
        }
    }

    restoreTarget?.let { (name, uri) ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("从快照恢复") },
            text = {
                Column {
                    Text(
                        text = "将用 " + name +
                            " 全量替换当前账本。解密与校验通过之前不会改动原账本。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("备份口令") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.restore(context, uri, pass.toCharArray())
                    restoreTarget = null
                }) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("取消") }
            }
        )
    }
}
