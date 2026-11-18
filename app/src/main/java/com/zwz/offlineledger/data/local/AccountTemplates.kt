package com.zwz.offlineledger.data.local

import com.zwz.offlineledger.data.model.AccountType

/**
 * 第 30 批：替代预制账户行的只读快捷模板。
 * 月付/白条属信用负债，MUST NOT 与支付宝余额、微信零钱并列为同一组。
 */
data class AccountTemplate(
    val label: String,
    val name: String,
    val type: AccountType,
    val group: String,
    val needsLast4: Boolean
)

object AccountTemplates {
    val ALL: List<AccountTemplate> = listOf(
        AccountTemplate("微信零钱", "微信零钱", AccountType.ASSET, "支付余额", false),
        AccountTemplate("支付宝", "支付宝", AccountType.ASSET, "支付余额", false),
        AccountTemplate("工行储蓄卡", "工行储蓄卡", AccountType.ASSET, "银行储蓄卡", true),
        AccountTemplate("建行储蓄卡", "建行储蓄卡", AccountType.ASSET, "银行储蓄卡", true),
        AccountTemplate("农行储蓄卡", "农行储蓄卡", AccountType.ASSET, "银行储蓄卡", true),
        AccountTemplate("中行储蓄卡", "中行储蓄卡", AccountType.ASSET, "银行储蓄卡", true),
        AccountTemplate("招行储蓄卡", "招行储蓄卡", AccountType.ASSET, "银行储蓄卡", true),
        AccountTemplate("信用卡", "信用卡", AccountType.LIABILITY, "信用负债", true),
        AccountTemplate("花呗", "花呗", AccountType.LIABILITY, "信用负债", false),
        AccountTemplate("京东白条", "京东白条", AccountType.LIABILITY, "信用负债", false),
        AccountTemplate("美团月付", "美团月付", AccountType.LIABILITY, "信用负债", false),
        AccountTemplate("抖音月付", "抖音月付", AccountType.LIABILITY, "信用负债", false)
    )
}
