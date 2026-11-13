package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.ui.LedgerDi
import com.zwz.offlineledger.ui.theme.LedgerColors
import kotlinx.coroutines.launch

/**
 * 常用模板 chips：点一下直接生成流水并关闭记一笔弹窗，**不弹二次编辑**。
 *
 * 刻意做成独立 Composable、自带 LedgerDi 取依赖，
 * 不动 RecordEditorViewModel 的构造与既有表单状态（少一处回归面）。
 */
@Composable
fun TemplateQuickPickBar(onPicked: () -> Unit) {
    val context = LocalContext.current
    val ep = remember { LedgerDi.entryPoint(context) }
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf(emptyList<RecordTemplateEntity>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        templates = runCatching { ep.templateDao().active() }.getOrDefault(emptyList())
    }

    if (templates.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "常用模板 · 点一下直接记账",
            style = MaterialTheme.typography.labelMedium,
            color = LedgerColors.Neutral
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            templates.take(8).forEach { t ->
                AssistChip(
                    onClick = {
                        scope.launch {
                            val id = runCatching {
                                ep.ledgerRepository().recordFromTemplate(
                                    t.id, System.currentTimeMillis()
                                )
                            }.getOrNull()
                            if (id != null) {
                                onPicked()
                            } else {
                                error = "模板「" + t.name + "」暂不可用，请检查其分类与账户"
                            }
                        }
                    },
                    label = { Text(t.name) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.ExpenseRed
            )
        }
    }
}
