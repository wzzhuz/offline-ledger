package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.ui.LedgerDi
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 6 批：模板 = 预填草稿。常用 4 个平铺（DAO 已按 sortOrder 排），
 * 其余进「更多模板」弹层全量选择，避免横向无限滚动。
 * 点一下把内容填进「记一笔」表单，不直接生成流水；金额与时间仍可改。
 */
private const val QUICK_TEMPLATES = 4

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

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "常用模板 · 点一下自动填充，金额与时间仍可改",
            style = MaterialTheme.typography.labelMedium,
            color = LedgerColors.Neutral
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            templates.take(QUICK_TEMPLATES).forEach { t ->
                val chosen = selectedId == t.id
                AssistChip(
                    onClick = { pick(t) },
                    label = {
                        Text(
                            text = (if (chosen) "● " else "") + t.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chosen) LedgerColors.IncomeGreen
                            else LedgerColors.Neutral
                        )
                    },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            if (templates.size > QUICK_TEMPLATES) {
                AssistChip(
                    onClick = { showAll = true },
                    label = {
                        Text("更多模板 ▾", style = MaterialTheme.typography.labelSmall)
                    }
                )
            }
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
