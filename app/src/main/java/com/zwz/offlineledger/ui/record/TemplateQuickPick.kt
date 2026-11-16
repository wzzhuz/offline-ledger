package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.ui.LedgerDi

/**
 * 第 9 批：模板栏收进与「账户 ›」完全同构的 FormRow 网格。
 * 6 个常用模板 chunked(3) 两行铺开、每行 3 个等宽，不再横向拖动；
 * 全量只由「模板 ›」整行点击触发，删掉占位置的「更多模板」chip。
 * 模板 = 预填草稿，点一下只填表单、不直接生成流水。
 */
private const val QUICK_TEMPLATES = 6
private const val TEMPLATES_PER_ROW = 3

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

    FormRow("模板", onRowClick = { showAll = true }) {
        val quick = templates.take(QUICK_TEMPLATES)
        quick.chunked(TEMPLATES_PER_ROW).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                rowItems.forEach { t ->
                    FilterChip(
                        selected = selectedId == t.id,
                        onClick = { pick(t) },
                        label = {
                            Text(
                                text = t.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {},
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    )
                }
                // 不足 3 个补空位，保证两行等宽对齐。
                repeat(TEMPLATES_PER_ROW - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f).padding(end = 4.dp))
                }
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
