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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.ui.LedgerDi
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第三批：模板 = 预填草稿，点一下把内容填进「记一笔」表单，不直接生成流水。
 * 金额留空由用户填，时间默认现在，所有字段仍可自由改；选中模板高亮。
 */
@Composable
fun TemplateQuickPickBar(onTemplate: (RecordTemplateEntity) -> Unit) {
    val context = LocalContext.current
    val ep = remember { LedgerDi.entryPoint(context) }
    var templates by remember { mutableStateOf(emptyList<RecordTemplateEntity>()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        templates = runCatching { ep.templateDao().active() }.getOrDefault(emptyList())
    }

    if (templates.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "模板 · 点一下自动填充，金额与时间仍可改",
            style = MaterialTheme.typography.labelMedium,
            color = LedgerColors.Neutral
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            templates.take(8).forEach { t ->
                val chosen = selectedId == t.id
                AssistChip(
                    onClick = { selectedId = t.id; onTemplate(t) },
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
        }
    }
}
