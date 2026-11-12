# Delta for 金额精度与净资产

## ADDED Requirements

### Requirement: 净变动统一为 from 减 to 加

流水对账户的净变动 SHALL 统一计算为：
`fromAccountId → -amountCents`、`toAccountId → +amountCents`。
系统 MUST NOT 按 `EXPENSE` / `INCOME` / `TRANSFER` 或
`ASSET` / `LIABILITY` 分支反向处理。

#### Scenario: 信用卡消费与还款

- **GIVEN** 信用卡 `balanceCents = -50000`，储蓄卡 `balanceCents = 100000`
- **WHEN** 记一笔 30 元信用卡支出，再从储蓄卡向信用卡转账 500 元
- **THEN** 消费后信用卡为 `-53000`
- **AND** 还款后信用卡归零、储蓄卡为 `50000`、净资产前后只减少 3000 分

**理由**：统一 `+delta` 语义让消费、还款、转账共用一套逻辑，分支越少越不容易漏。

---

### Requirement: 净资产取 includeInTotal 余额求和

`LedgerEngine.netWorth()` SHALL 返回
`SUM(balanceCents WHERE includeInTotal = 1)`，
MUST NOT 使用「总资产 − 总负债」或按账户类型分别求和再相减。

#### Scenario: 排除不计入统计的账户

- **GIVEN** 资产账户 1000000 分、信用卡 -50000 分、另有一个
  `includeInTotal = false` 的账户 200000 分
- **WHEN** 计算净资产
- **THEN** 返回 950000 分
- **AND** 被排除账户不影响结果

**理由**：负债余额本身已是负数，再减一次等于取负，会凭空虚高净资产。
