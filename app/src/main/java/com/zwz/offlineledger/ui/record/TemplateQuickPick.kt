package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.ui.LedgerDi

/**
 * 第 77 批（Q2A）：模板从「6 个 chips 两行铺开」收敛成**一枚按钮**。
 * 全量弹层沿用既有实现，零新数据路径；模板仍是预填草稿、不直接生成流水。
 * 按钮放在「更多」面板里，不再占记一笔首屏。
 */
@Composable
fun TemplateQuickPickBar(onTemplate: (RecordTemplateEntity) -> Unit) {
    val context = LocalContext.current
    val ep = remember { LedgerDi.entryPoint(context) }
    var templates by remember { mutableStateOf(emptyList<RecordTemplateEntity>()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        templates = runCatching { ep.templateDao().active() }.getOrDefault(emptyList())
    }

    if (templates.isEmpty()) return

    fun pick(t: RecordTemplateEntity) {
        selectedId = t.id
        onTemplate(t)
    }

    FormRow("模板", onRowClick = { showAll = true }, centerLabel = true) {
        TextButton(onClick = { showAll = true }) {
            Text(
                text = "模板 ›",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }

    if (showAll) {
        AlertDialog(
            onDismissRequest = { showAll = false },
            title = { Text("全部模板") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    templates.forEach { t ->
                        TextButton(
                            onClick = { pick(t); showAll = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                t.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAll = false }) { Text("关闭") } }
        )
    }
}
