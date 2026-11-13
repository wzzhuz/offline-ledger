package com.zwz.offlineledger.data.model

/** 资产账户余额通常非负；信用负债账户欠款为负（欠款 500 元 = -50000 分）。 */
enum class AccountType { ASSET, LIABILITY }

/** 校准不入流水表，用独立 balance_adjustments 记录。 */
/**
 * SETTLEMENT = 退款 / 报销收回款。
 * 金额恒正；生成时 fromAccountId = null、toAccountId = 原支出账户，
 * 因此 aggregateAccountDeltas 与 LedgerEngine 一行不改就能把钱加回账户。
 * StatRules 只认 EXPENSE/INCOME，故它天然不进收入概览与趋势双线。
 */
enum class RecordType { EXPENSE, INCOME, TRANSFER, SETTLEMENT }
