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
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 60 批：只读「选择账户模板」底部 sheet。
 *
 * 48 条模板按 group 分四组（支付余额 / 银行储蓄卡 / 银行信用卡 / 网络信用），
 * 资产组与信用负债组 MUST NOT 混排（基线要求）。
 * Q6A：label 用银行正规全称，卡种用 12dp 小角标贴在图标右下角，
 * 不占名称宽度。Q7：iconKey 走 AccountIconCatalog，资源待补时回退中性圆。
 *
 * 组件本身**无任何写库路径**：onPick 只回填 UI 状态（名称 / 类型 / 卡种）。
 * 遮罩只压暗、MUST NOT 挂 clickable——无 pointerInput 的 Column 不消费点击，
 * 点 sheet 内空白会穿透到遮罩导致误关（§7.41）。
 */
@Composable
fun AccountTemplateSheet(
    onDismiss: () -> Unit,
    onPick: (AccountTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(LedgerColors.CardSurface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
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
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = items.size.toString() + " 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral
                        )
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
                            // 末行不足 2 个补空占位，保持列对齐（§7.39）。
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
            .background(LedgerColors.SurfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountIconBox(
            name = template.label,
            cardKind = template.cardKind,
            iconKey = template.iconKey,
            size = 32
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = template.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
