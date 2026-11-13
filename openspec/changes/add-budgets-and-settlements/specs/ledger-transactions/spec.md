# Delta for 流水与账务联动

## ADDED Requirements

### Requirement: SETTLEMENT 作为独立流水类型且余额联动复用既有算法

`RecordType` SHALL 增加 `SETTLEMENT`，`RecordEntity` SHALL 增加
可空 `linkedRecordId`。SETTLEMENT 金额 MUST 恒为正整数分，
生成时 `fromAccountId = null`、`toAccountId = 原支出账户`、
`linkedRecordId = 原流水 id`。
系统 MUST NOT 修改 `aggregateAccountDeltas`、`LedgerEngine`
或 `rebuildAllBalancesInternal()`。

#### Scenario: 报销后净资产回到垫付前

- **GIVEN** 微信支付一笔 38.50 元餐饮支出
- **WHEN** 对该笔发起全额报销
- **THEN** 生成一条 SETTLEMENT 流水，钱回到微信账户
- **AND** 净资产与垫付前完全相同

#### Scenario: 统计口径不被污染

- **GIVEN** 账本含一条 SETTLEMENT
- **WHEN** 查看年度收入与收支趋势
- **THEN** 年度收入不增加、趋势双线不含该笔
- **AND** `StatRules.SQL_STAT_RELEVANT` 保持原样

**理由**：`toAccountId += amount` 已经能表达「钱回来了」，
改余额算法会引入新分支；新增 type 天然被既有统计口径排除。

---

### Requirement: 报销入口只针对支出且累计不超过原金额

系统 SHALL 只在 `EXPENSE` 流水上提供「报销 / 退款」入口，
SHALL 校验「累计已报销 + 本次金额 <= 原支出金额」，
超额 MUST 被拒绝且账本不变。SETTLEMENT 生成 MUST 走
`withResolvedRoot` 与既有 `db.withTransaction` + 末尾重算。

#### Scenario: 部分报销

- **GIVEN** 一笔 100 元支出
- **WHEN** 先报 30 元再报 70 元
- **THEN** 生成两条 SETTLEMENT，账户余额累计回补 100 元

#### Scenario: 超额被拒

- **GIVEN** 一笔 100 元支出且已全额报销
- **WHEN** 再次发起 1 元报销
- **THEN** 返回错误、不写入任何流水
- **AND** 账户余额不变

#### Scenario: 对收入或转账发起

- **GIVEN** 一条收入流水
- **WHEN** 查看明细行
- **THEN** 不显示报销入口

**理由**：负金额冲减违反金额铁律；累计上限是防止误操作
把账户余额做成虚高的最后一道闸。

---

### Requirement: linkedRecordId 支持明细展示成对关系

SETTLEMENT 流水 SHALL 保留对原支出的关联，
明细页 SHALL 能据此识别「已报销」关系，
且归档 / 删除原分类 MUST NOT 改写历史流水的 `rootCategoryId`。

#### Scenario: 查看关联

- **GIVEN** 一笔支出及其 SETTLEMENT
- **WHEN** 打开明细
- **THEN** 两条流水都可见，SETTLEMENT 显示为收回款
- **AND** 分类名仍能解析（即使分类已归档）

**理由**：报销不是凭空出现的一笔收入，
要能回答「这笔钱是冲哪笔垫付的」。
