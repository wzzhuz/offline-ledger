package com.zwz.offlineledger.ui.assets

import com.zwz.offlineledger.data.local.entity.AccountEntity

/**
 * 第 61 批：账户**显示名**的唯一组合点。
 *
 * 第 60 批 Q3A 定了「`name` 只存纯名、`cardLast4` 是单一真相源，
 * 展示层组合一次」，但只落在了资产页 `AccountCard` 的内联 `shownName` 里，
 * 记一笔候选 / 全账户选择弹层 / 独立排序页仍各自用 `account.name`，
 * 于是多张同名银行卡（如两张「工商银行」）在记一笔里完全分不出来。
 *
 * 本函数统一供四处复用：
 *   ① 记一笔快捷 chip ② 记一笔全账户选择弹层（含搜索键）
 *   ③ 独立「全部账户排序」页 ④ 资产页账户行
 *
 * 旧库兼容：v0.2.40 及更早把 ` (1122)` **拼进了 `name` 本身**，
 * 若 `name` 已以该后缀结尾则不再追加，MUST NOT 出现 `(1122)(1122)`。
 * 旧 `name` 不被改写（零数据迁移）。
 *
 * 只负责「纯名 + 卡号一次组合」；币种、已归档、卡种小标志仍由各页面
 * 按自己的密度需要追加，不塞进这个函数。
 */
fun accountBaseLabel(account: AccountEntity): String {
    val last4 = account.cardLast4
    if (last4.isNullOrBlank()) return account.name
    val suffix = " ($last4)"
    return if (account.name.endsWith(suffix)) account.name else account.name + suffix
}

/** 编辑框初始化用的反向操作：把旧库 name 里冗余的 ` (xxxx)` 后缀剥掉。 */
fun stripLegacyCardSuffix(name: String, last4: String?): String {
    if (last4.isNullOrBlank()) return name
    val suffix = " ($last4)"
    return if (name.endsWith(suffix)) name.removeSuffix(suffix) else name
}
