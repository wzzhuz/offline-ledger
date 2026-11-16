package com.zwz.offlineledger.data.model

/** 资产账户余额通常非负；信用负债账户欠款为负（欠款 500 元 = -50000 分）。 */
enum class AccountType { ASSET, LIABILITY, PREPAID, INVESTMENT }

/**
 * 第 6 批：充值 / 储值卡与投资理财账户。
 * 净资产只按 includeInTotal 汇总余额，MUST NOT 按 type 分支；
 * 只有 LIABILITY 触发信用额度与负债展示。枚举以名称字符串落库，加值不改列定义。
 */
fun AccountType.displayName(): String = when (this) {
    AccountType.ASSET -> "资产账户"
    AccountType.LIABILITY -> "信用负债"
    AccountType.PREPAID -> "充值 / 储值卡"
    AccountType.INVESTMENT -> "投资理财"
}

/** 校准不入流水表，用独立 balance_adjustments 记录。 */
/**
 * SETTLEMENT = 退款 / 报销收回款。
 * 金额恒正；生成时 fromAccountId = null、toAccountId = 原支出账户，
 * 因此 aggregateAccountDeltas 与 LedgerEngine 一行不改就能把钱加回账户。
 * StatRules 只认 EXPENSE/INCOME，故它天然不进收入概览与趋势双线。
 */
enum class RecordType { EXPENSE, INCOME, TRANSFER, SETTLEMENT }
