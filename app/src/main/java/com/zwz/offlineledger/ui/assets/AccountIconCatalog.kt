package com.zwz.offlineledger.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwz.offlineledger.data.local.AccountTemplates
import com.zwz.offlineledger.ui.components.CategoryIcon

/**
 * 第 60 批 Q7：账户品牌图标的**编译期显式常量表**。
 *
 * 使用者已明确「用 Logo 指示账户归属、记账类、可以从 IconFont 获取」，
 * 本批按此方向落地可插拔机制，但**资源目录为空**：沙盒拿不到 IconFont
 * 的登录态导出文件，且每份品牌图形必须在 `assets/NOTICE` 逐项登记
 * 作者 / 来源 / 授权类型 / 传递依赖（平台图标由不同作者上传，授权逐个不同）。
 *
 * 因此 `drawableFor` 本批只有 `else -> null`，三页全部回退
 * `CategoryIcon` 的中性圆 + 中文首字。补齐资源时只需：
 *   ① 放入本地 VectorDrawable ② 在此加 `when` 分支 ③ 补 `assets/NOTICE` 条目
 * UI 代码零改动。MUST NOT 用运行时字符串反射 / 查表映射（T14 编译坑）。
 */
object AccountIconCatalog {

    /** 账户名 → 模板 iconKey 的纯数据反查；不匹配时返回 null 走回退。 */
    fun iconKeyFor(accountName: String): String? =
        AccountTemplates.ALL.firstOrNull { it.name == accountName }?.iconKey

    fun drawableFor(iconKey: String?): Int? = when (iconKey) {
        // 第 60 批：Q7 资源待使用者提供导出文件 + 逐项授权登记后在此补分支。
        // 示例："icbc" -> R.drawable.ic_bank_icbc
        else -> null
    }
}

/**
 * 账户图标位：有已登记资源则显示，否则回退中性圆 + 中文首字；
 * 卡种以 12dp 小角标叠在右下角，**不占名称宽度**（Q6A 要求）。
 */
@Composable
fun AccountIconBox(
    name: String,
    cardKind: String?,
    iconKey: String?,
    size: Int = 40,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        val res = AccountIconCatalog.drawableFor(iconKey)
        if (res != null) {
            Icon(
                painter = painterResource(res),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size((size - 6).dp)
            )
        } else {
            CategoryIcon(name = name, colorHex = null, size = size)
        }
        AccountKindBadge(cardKind, Modifier.align(Alignment.BottomEnd))
    }
}

/**
 * 12dp 单字小标志：「储」= 储蓄卡 / 「信」= 信用卡。
 * 叠在图标圆右下角，MUST NOT 用「· 储蓄卡」这类文字后缀挤占账户名。
 */
@Composable
fun AccountKindBadge(
    cardKind: String?,
    modifier: Modifier = Modifier,
    size: Int = 12
) {
    // 不用 early return：Composable 重启函数里条件包裹最稳。
    val label = when (cardKind) {
        "DEBIT" -> "储"
        "CREDIT" -> "信"
        else -> null
    }
    if (label != null) {
        val bg = if (cardKind == "CREDIT") Color(0xFFDCE7FF) else Color(0xFFDFF0E4)
        val fg = if (cardKind == "CREDIT") Color(0xFF2A4FA0) else Color(0xFF236B36)
        Box(
            modifier = modifier
                .size(size.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = fg,
                fontSize = if (size <= 10) 6.sp else 7.sp,
                lineHeight = if (size <= 10) 6.sp else 7.sp,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
