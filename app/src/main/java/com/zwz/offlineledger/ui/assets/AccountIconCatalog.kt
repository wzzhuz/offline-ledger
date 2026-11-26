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
import com.zwz.offlineledger.R
import com.zwz.offlineledger.data.local.AccountTemplates
import com.zwz.offlineledger.ui.components.CategoryIcon
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 60 批 Q7 落地机制；**第 64 批补齐实际资源**。
 *
 * 使用者已明确「用 Logo 指示账户归属、记账类场景」。第 64 批从
 * `icongo/bank-logos`（MIT，21 家中国银行 SVG）与
 * `Simple Icons`（CC0-1.0，微信 / 支付宝 / 美团 / 抖音）取回图形，
 * 统一裁切 + 白圆底转成 128×128 PNG 放进 `drawable-nodpi/`；
 * 京东白条无 CC0/明确授权图形，用本作品原创红色「白」字占位。
 * 逐项来源、授权类型、商标口径 MUST 见 `assets/NOTICE` 第 64 批段落。
 *
 * IconFont 未采用：UGC 图标由不同作者上传、授权逐个不同，部分图库
 * 需打赏才商用，且需登录态导出；平台聚合页不等于「可随包分发」。
 * 若后续改用 IconFont，MUST 按每个图标详情页授权逐项替换资源与 NOTICE。
 *
 * 仍是**编译期显式常量表**：MUST NOT 用运行时字符串反射 / 查表映射
 * （T14 编译坑）。补齐或替换资源只需：① 替换同名 PNG
 * ② 需要新增键位时在此加 `when` 分支 ③ 同步 `assets/NOTICE` 条目，
 * UI 代码零改动。
 */
object AccountIconCatalog {

    /** 账户名 → 模板 iconKey 的纯数据反查；不匹配时返回 null 走回退。 */
    fun iconKeyFor(accountName: String): String? =
        AccountTemplates.ALL.firstOrNull { it.name == accountName }?.iconKey

    fun drawableFor(iconKey: String?): Int? = when (iconKey) {
        // 第 64 批：21 家银行，来自 icongo/bank-logos（MIT）。
        "icbc" -> R.drawable.ic_acct_icbc
        "ccb" -> R.drawable.ic_acct_ccb
        "abc" -> R.drawable.ic_acct_abc
        "boc" -> R.drawable.ic_acct_boc
        "bocom" -> R.drawable.ic_acct_bocom
        "psbc" -> R.drawable.ic_acct_psbc
        "cmb" -> R.drawable.ic_acct_cmb
        "spdb" -> R.drawable.ic_acct_spdb
        "citic" -> R.drawable.ic_acct_citic
        "ceb" -> R.drawable.ic_acct_ceb
        "hxb" -> R.drawable.ic_acct_hxb
        "cmbc" -> R.drawable.ic_acct_cmbc
        "cgb" -> R.drawable.ic_acct_cgb
        "cib" -> R.drawable.ic_acct_cib
        "pab" -> R.drawable.ic_acct_pab
        "czb" -> R.drawable.ic_acct_czb
        "njcb" -> R.drawable.ic_acct_njcb
        "bob" -> R.drawable.ic_acct_bob
        "bosc" -> R.drawable.ic_acct_bosc
        "jsb" -> R.drawable.ic_acct_jsb
        "nbcb" -> R.drawable.ic_acct_nbcb
        // 支付 / 网络信用，来自 Simple Icons（CC0-1.0）或本作品原创。
        "wechat" -> R.drawable.ic_acct_wechat
        "alipay" -> R.drawable.ic_acct_alipay
        // 花呗是支付宝旗下，用母品牌图形指示归属（非花呗官方 logo）。
        "huabei" -> R.drawable.ic_acct_alipay
        "meituan" -> R.drawable.ic_acct_meituan
        // 抖音月付：以同品牌 TikTok 图形指示字节跳动 / 抖音归属。
        "douyin" -> R.drawable.ic_acct_douyin
        // 京东白条：无 CC0 / 明确授权图形，用原创红色「白」字占位，
        // MUST NOT 临摹京东商标；后续拿到授权可直接替换同名 PNG。
        "jdbaitiao" -> R.drawable.ic_acct_jdbaitiao
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
        val bg = if (cardKind == "CREDIT") LedgerColors.Primary.copy(alpha = 0.12f)
            else LedgerColors.IncomeGreen.copy(alpha = 0.12f)
        val fg = if (cardKind == "CREDIT") LedgerColors.Primary else LedgerColors.IncomeGreen
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
