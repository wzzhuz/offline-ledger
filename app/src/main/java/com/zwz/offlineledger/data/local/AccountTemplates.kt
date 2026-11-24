package com.zwz.offlineledger.data.local

import com.zwz.offlineledger.data.model.AccountType

/**
 * 第 60 批：模板清单扩到 21 家银行 × 2 卡种 + 支付余额 + 网络信用 = 48 条。
 *
 * - Q5A：六大行 / 邮储 / 交行 / 主要股份行 / 南京银行等城商行齐备，
 *   每家 SHALL 各有储蓄卡与信用卡一条。
 * - Q6A：label / name 一律用**正规全称**（「工商银行」），
 *   MUST NOT 再写「工行储蓄卡」这种把卡种拼进名称的写法；
 *   卡种由 cardKind 表达，UI 用 12dp 小角标，不挤占名称宽度。
 * - Q7：iconKey 为编译期图标键位预留；资源目录本批为空，
 *   消费侧一律回退中性圆 + 中文首字。
 *
 * group 按清单顺序分四组，groupBy 保序：
 * 支付余额 → 银行储蓄卡 → 银行信用卡 → 网络信用。
 * 资产组与信用负债组 MUST NOT 混在同一组（基线要求）。
 */
data class AccountTemplate(
    val label: String,
    val name: String,
    val type: AccountType,
    val group: String,
    val needsLast4: Boolean,
    val cardKind: String? = null,
    val iconKey: String? = null
)

object AccountTemplates {
    val ALL: List<AccountTemplate> = listOf(
        AccountTemplate("微信零钱", "微信零钱", AccountType.ASSET, "支付余额", false, null, "wechat"),
        AccountTemplate("支付宝", "支付宝", AccountType.ASSET, "支付余额", false, null, "alipay"),
        AccountTemplate("工商银行", "工商银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "icbc"),
        AccountTemplate("建设银行", "建设银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "ccb"),
        AccountTemplate("农业银行", "农业银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "abc"),
        AccountTemplate("中国银行", "中国银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "boc"),
        AccountTemplate("交通银行", "交通银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "bocom"),
        AccountTemplate("邮储银行", "邮储银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "psbc"),
        AccountTemplate("招商银行", "招商银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "cmb"),
        AccountTemplate("浦发银行", "浦发银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "spdb"),
        AccountTemplate("中信银行", "中信银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "citic"),
        AccountTemplate("光大银行", "光大银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "ceb"),
        AccountTemplate("华夏银行", "华夏银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "hxb"),
        AccountTemplate("民生银行", "民生银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "cmbc"),
        AccountTemplate("广发银行", "广发银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "cgb"),
        AccountTemplate("兴业银行", "兴业银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "cib"),
        AccountTemplate("平安银行", "平安银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "pab"),
        AccountTemplate("浙商银行", "浙商银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "czb"),
        AccountTemplate("南京银行", "南京银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "njcb"),
        AccountTemplate("北京银行", "北京银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "bob"),
        AccountTemplate("上海银行", "上海银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "bosc"),
        AccountTemplate("江苏银行", "江苏银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "jsb"),
        AccountTemplate("宁波银行", "宁波银行", AccountType.ASSET, "银行储蓄卡", true, "DEBIT", "nbcb"),
        AccountTemplate("工商银行", "工商银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "icbc"),
        AccountTemplate("建设银行", "建设银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "ccb"),
        AccountTemplate("农业银行", "农业银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "abc"),
        AccountTemplate("中国银行", "中国银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "boc"),
        AccountTemplate("交通银行", "交通银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "bocom"),
        AccountTemplate("邮储银行", "邮储银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "psbc"),
        AccountTemplate("招商银行", "招商银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "cmb"),
        AccountTemplate("浦发银行", "浦发银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "spdb"),
        AccountTemplate("中信银行", "中信银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "citic"),
        AccountTemplate("光大银行", "光大银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "ceb"),
        AccountTemplate("华夏银行", "华夏银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "hxb"),
        AccountTemplate("民生银行", "民生银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "cmbc"),
        AccountTemplate("广发银行", "广发银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "cgb"),
        AccountTemplate("兴业银行", "兴业银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "cib"),
        AccountTemplate("平安银行", "平安银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "pab"),
        AccountTemplate("浙商银行", "浙商银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "czb"),
        AccountTemplate("南京银行", "南京银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "njcb"),
        AccountTemplate("北京银行", "北京银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "bob"),
        AccountTemplate("上海银行", "上海银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "bosc"),
        AccountTemplate("江苏银行", "江苏银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "jsb"),
        AccountTemplate("宁波银行", "宁波银行", AccountType.LIABILITY, "银行信用卡", true, "CREDIT", "nbcb"),
        AccountTemplate("花呗", "花呗", AccountType.LIABILITY, "网络信用", false, null, "huabei"),
        AccountTemplate("京东白条", "京东白条", AccountType.LIABILITY, "网络信用", false, null, "jdbaitiao"),
        AccountTemplate("美团月付", "美团月付", AccountType.LIABILITY, "网络信用", false, null, "meituan"),
        AccountTemplate("抖音月付", "抖音月付", AccountType.LIABILITY, "网络信用", false, null, "douyin")
    )
}
