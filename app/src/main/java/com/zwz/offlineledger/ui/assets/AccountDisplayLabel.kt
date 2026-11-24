package com.zwz.offlineledger.ui.assets

import com.zwz.offlineledger.core.accountLabel as coreAccountLabel
import com.zwz.offlineledger.core.stripAccountCardSuffix
import com.zwz.offlineledger.data.local.entity.AccountEntity

/**
 * 第 61 批：账户**显示名**的唯一组合点。
 *
 * 第 60 批 Q3A 定了「`name` 只存纯名、`cardLast4` 是单一真相源，
 * 展示层组合一次」，但只落在了资产页 `AccountCard` 的内联 `shownName` 里，
 * 记一笔候选 / 全账户选择弹层 / 独立排序页仍各自用 `account.name`，
 * 于是多张同名银行卡（如两张「工商银行」）在记一笔里完全分不出来。
 *
 * 第 61 批覆盖四处：① 记一笔快捷 chip ② 记一笔全账户选择弹层（含搜索键）
 * ③ 独立「全部账户排序」页 ④ 资产页账户行。
 * 第 62 批全量排查后再覆盖：筛选面板账户选项、明细筛选 chip、
 * 统计页账户排行（及从统计跳明细的下钻 chip）、明细列表行与流水详情页
 * （经 `accountLabel` 走 DAO 投影）、账户详情标题、删除确认弹窗、
 * 默认账户设置、对账 / 核对标题、回收站。
 *
 * 旧库兼容：v0.2.40 及更早把 ` (1122)` **拼进了 `name` 本身**，
 * 若 `name` 已以该后缀结尾则不再追加，MUST NOT 出现 `(1122)(1122)`。
 * 旧 `name` 不被改写（零数据迁移）。
 *
 * 只负责「纯名 + 卡号一次组合」；币种、已归档、卡种小标志仍由各页面
 * 按自己的密度需要追加，不塞进这个函数。
 */
/**
 * 第 63 批：唯一实现已下沉到 `core/AccountLabel.kt`，
 * 这里只做 Entity 重载与兼容转发，MUST NOT 再写一份组合逻辑。
 */
fun accountBaseLabel(account: AccountEntity): String =
    coreAccountLabel(account.name, account.cardLast4) ?: ""

/** 编辑框初始化用的反向操作：把旧库 name 里冗余的 ` (xxxx)` 后缀剥掉。 */
fun stripLegacyCardSuffix(name: String, last4: String?): String =
    stripAccountCardSuffix(name, last4)
