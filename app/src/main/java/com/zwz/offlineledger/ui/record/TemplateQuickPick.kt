package com.zwz.offlineledger.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.core.money.Cents
import com.zwz.offlineledger.data.local.entity.RecordTemplateEntity
import com.zwz.offlineledger.ui.LedgerDi
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 80 批：模板从「更多面板里的模板 › 按钮 + 居中 AlertDialog」改为
 * 键盘上方 chip 直达的底部 sheet，一羽式一行一条（名称 / 备注 / 预填金额）。
 *
 * 语义不变：模板仍是**预填草稿、不直接生成流水**（applyTemplate()），
 * 点一项 SHALL 预填并关闭 sheet，MUST NOT 自动落库。
 * 复用同包 BottomPickerSheet（Dialog + 底部对齐 Surface），
 * MUST NOT 引入 ExperimentalMaterial3Api 的 ModalBottomSheet。
 */
@Composable
fun TemplatePickerSheet(
    onTemplate: (RecordTemplateEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ep = remember { LedgerDi.entryPoint(context) }
    var templates by remember { mutableStateOf(emptyList<RecordTemplateEntity>()) }

    LaunchedEffect(Unit) {
        templates = runCatching { ep.templateDao().active() }.getOrDefault(emptyList())
    }

    BottomPickerSheet(title = "选择模板", onDismiss = onDismiss) {
        if (templates.isEmpty()) {
            Text(
                "还没有模板，可在设置里新建",
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerColors.Neutral,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            templates.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTemplate(t) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent
                    ) {
                        Text(
                            t.name.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            color = LedgerColors.Primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        if (t.note.isNotBlank()) {
                            Text(
                                t.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = LedgerColors.Neutral,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        "-" + Cents(t.amountCents).toYuanString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = LedgerColors.ExpenseRed
                    )
                }
                FormDivider()
            }
        }
    }
}
