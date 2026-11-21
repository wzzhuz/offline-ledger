package com.zwz.offlineledger.quick

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zwz.offlineledger.ui.theme.LedgerTheme

/** 第 49 批：权限引导与白名单。入口在确认页「设置」，不碰设置页一级导航。 */
class QuickCaptureSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LedgerTheme { QuickCaptureSettingsScreen(onBack = { finish() }) } }
    }
}

@Composable
private fun QuickCaptureSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuickCapturePrefs(context) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var nodeCache by remember { mutableStateOf(prefs.nodeCacheEnabled) }
    var chosen by remember { mutableStateOf(prefs.packages()) }
    var hint by remember { mutableStateOf("") }
    val pkgs = listOf(
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.tencent.mm" to "微信",
        "com.unionpay" to "云闪付",
        "com.chinamworld.main" to "手机银行"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("识屏记账设置", style = MaterialTheme.typography.titleLarge)
        Text(
            if (QuickCaptureBridge.isAttached()) "当前：无障碍服务已连接"
            else "当前：无障碍服务未连接，磁贴仍可一键手动记账",
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            "仅在你点磁贴时读取当前屏幕；白名单之外不解析、不截图。数据只在本机内存里流转。",
            style = MaterialTheme.typography.bodySmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; prefs.enabled = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("节点文本优先（省一次截图）", modifier = Modifier.weight(1f))
            Switch(checked = nodeCache, onCheckedChange = { nodeCache = it; prefs.nodeCacheEnabled = it })
        }
        Button(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) { Text("1 · 开启无障碍服务") }
        Button(onClick = { addTile(context) { hint = it } }) { Text("2 · 添加下拉磁贴") }
        Button(onClick = {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(i)
            } catch (e: Exception) {
                hint = "请到系统设置里手动忽略电池优化"
            }
        }) { Text("3 · 忽略电池优化（可选）") }
        Text("白名单应用", style = MaterialTheme.typography.titleSmall)
        pkgs.forEach { (pkg, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f))
                Switch(
                    checked = pkg in chosen,
                    onCheckedChange = { on ->
                        val next = if (on) chosen + pkg else chosen - pkg
                        chosen = next
                        prefs.setPackages(next)
                    }
                )
            }
        }
        if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall)
        Text(
            "Android 13+ 侧载包若提示「受限制的设置」：设置 → 应用 → 本 App → ⋮ → 允许受限设置，再回无障碍页放行。升级或重装后需重开一次无障碍权限。",
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = onBack) { Text("返回") }
    }
}

private fun addTile(context: Context, onHint: (String) -> Unit) {
    if (Build.VERSION.SDK_INT < 33) {
        onHint("请下拉快捷设置 → 编辑 → 把「识屏记账」拖进磁贴")
        return
    }
    try {
        val sm = context.getSystemService(StatusBarManager::class.java)
        sm.requestAddTileService(
            ComponentName(context, QuickEntryTileService::class.java),
            "识屏记账",
            Icon.createWithResource(context, android.R.drawable.ic_menu_add),
            ContextCompat.getMainExecutor(context)
        ) { result -> onHint("系统返回码：" + result) }
    } catch (e: Throwable) {
        onHint("自动添加未生效，请在快捷设置里手动添加磁贴")
    }
}
