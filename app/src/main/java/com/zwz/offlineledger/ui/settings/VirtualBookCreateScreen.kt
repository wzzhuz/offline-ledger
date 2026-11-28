package com.zwz.offlineledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.prefs.VirtualBookStore
import com.zwz.offlineledger.core.prefs.VirtualBookTemplate

/**
 * 第 95 批：新建虚拟账本独立页。
 *
 * 从虚拟账本列表页的右上角「+」进入，和列表彻底分离，避免「新建表单与已建
 * 视图卡片混在一屏」。模板说明与「配置」弹窗复用同一份 VirtualBookTemplate.hint，
 * 不复制文案。创建成功后直接回列表，列表按 tick 重读 prefs。
 */
@Composable
fun VirtualBookCreateScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { VirtualBookStore(context) }
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(VirtualBookTemplate.GIFT) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text("新建虚拟账本", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = "虚拟账本只是按标签聚合的视图，不搬动任何流水。" +
                "建好后从明细页标题行的账本图标进入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; errorText = null },
            label = { Text("名称，如「结婚礼金」或「装修」") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        )

        Text(
            "场景模板",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VirtualBookTemplate.entries.forEach { t ->
                FilterChip(
                    selected = template == t,
                    onClick = { template = t },
                    label = { Text(t.label) }
                )
            }
        }
        Text(
            text = template.hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = "模板决定录入按钮、汇总措辞与预填分类；" +
                "专项支出 / 自定义模板建好后可在列表的「配置」里设一次默认分类，" +
                "之后每笔自动带入，不用逐笔手选。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        errorText?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Button(
            onClick = {
                val b = store.add(name, template)
                if (b == null) errorText = "名称为空或已存在同名视图"
                else onCreated()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) { Text("创建") }
    }
}
