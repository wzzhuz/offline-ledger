package com.zwz.offlineledger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 第 78 批：设计 token 单一真相源。
 *
 * 只收敛「新增 / 已有显式写法」的常量，不改动明细页、记一笔、资产列表的
 * 纯行网格布局（reporting-ui 基线仍禁止这些页面卡片化与阴影）。
 */
object LedgerShapes {
    /** 卡片统一大圆角。 */
    val Card = RoundedCornerShape(16.dp)

    /** 分类 / 账户图标容器圆角（容器尺寸仍由各页自定，避免牵动首屏密度）。 */
    val Icon = RoundedCornerShape(12.dp)
}

object LedgerSpacing {
    /** 卡片之间的间距。第 79 批真机反馈：12dp 在浅色下边界仍偏糊，
     * 回到第 70 批已验证的 16dp（StatsCard 用 vertical 8.dp 上下各半）。 */
    val CardGap = 16.dp

    /** 卡片内边距统一值；记一笔表单行仍用 16×8，不套用本值。 */
    val CardPadding = 16.dp
}

/**
 * 金额排版层级。所有金额样式统一开启 `tnum` 等宽数字特性：
 * `+` / `-` / 小数点 / 千分位不再随字形宽度跳动，列表上下对齐。
 *
 * 注意：`tnum` 依赖字体 OpenType 特性，部分 OEM 中文字体可能不生效，
 * 真机待验；必要时保留等宽数字字体兜底，MUST NOT 写成「所有机型必然生效」。
 */
object LedgerType {
    /** 大额总览数字：统计页总支出 / 交易详情页主金额。 */
    val AmountDisplay = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    )

    /** 单笔金额：明细行、统计页收入 / 结余行。 */
    val AmountRow = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum"
    )

    /** 主标题层级。 */
    val Title = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )

    /** 次要备注层级。 */
    val Caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    )
}
