package com.zwz.offlineledger.data.model

/** 资产账户余额通常非负；信用负债账户欠款为负（欠款 500 元 = -50000 分）。 */
enum class AccountType { ASSET, LIABILITY }

/** 校准不入流水表，用独立 balance_adjustments 记录。 */
enum class RecordType { EXPENSE, INCOME, TRANSFER }
