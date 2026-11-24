package com.zwz.offlineledger.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.data.local.AccountTemplate
import com.zwz.offlineledger.data.local.AccountTemplates
import com.zwz.offlineledger.ui.components.CategoryHeaderBlue
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 59 批：只读「选择账户模板」底部 sheet。
 *
 * 借鉴一羽记账「选择银行」弹窗的组织方式（底部圆角卡 + 分组 + 2 列图标格 +
 * 底部自定义入口），但 MUST NOT 复制其视觉资产，也 MUST NOT 引入
 * 「IconFont 下载 PNG」等在线能力——Manifest 当前不声明 `INTERNET`，联网需 `project.md` 显式授权，本批不申请。
 *
 * 承载的是基线 `ledger-transactions`「快捷账户模板替代预制账户行」已定下的语义：
 * 按资产组 / 信用负债组展示、点选自动填名称与类型、卡号后四位仍由主表单输入。
 * 组件本身**无任何写库路径**：`onPick` 只回填两个 UI 状态。
 *
 * 没有字母索引：一羽有上百个银行，本 App 只有 12 个模板，按 `group` 分组已足够。
 */
@Composable
fun AccountTemplateSheet(
    onDismiss: () -> Unit,
    onPick: (AccountTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    // 遮罩只做视觉压暗：MUST NOT 挂 clickable——无 pointerInput 的 Column 不会消费点击，
    // 点 sheet 内空白会穿透到遮罩导致误关。关闭只走「关闭」/「自定义名称」两个显式入口。
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .background(Color.White, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "选择账户模板",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            Text(
                text = "点选后自动填入名称与类型，可再改卡号后四位",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerColors.Neutral
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                AccountTemplates.ALL.groupBy { it.group }.forEach { (group, items) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(14.dp)
                                .background(CategoryHeaderBlue, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = group, style = MaterialTheme.typography.titleSmall)
                    }
                    items.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { t ->
                                TemplateTile(
                                    template = t,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onPick(t) }
                                )
                            }
                            // 末行不足 2 个补空占位，MUST NOT 让单格被拉宽（§7.39）。
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("自定义名称", color = CategoryHeaderBlue)
            }
        }
    }
}

@Composable
private fun TemplateTile(
    template: AccountTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .background(Color(0xFFF5F6FA), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // CategoryIcon 忽略 colorHex，天然中性圆 + 首字；零新增图标资源。
        CategoryIcon(name = template.label, colorHex = null, size = 32)
        Spacer(Modifier.width(8.dp))
        Text(
            text = template.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
