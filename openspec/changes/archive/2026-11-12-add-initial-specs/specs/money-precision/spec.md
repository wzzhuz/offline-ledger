# Delta for 金额精度与净资产

> 初始归档：本模块全部为新增要求（`ADDED`），无 `MODIFIED` / `REMOVED`。
> 归档时已原样合并至 `openspec/specs/money-precision/spec.md`。

## ADDED Requirements

### Requirement: 金额以整数分存储

系统 SHALL 以 `Long` 存储所有金额字段，单位为「分」（1 元 = 100 分）。Entity、Domain Model、备份 DTO 一律如此。`BigDecimal` MAY 仅用于 UI 展示与用户输入字符串的格式化转换。系统 MUST NOT 让 `Float` / `Double` 参与任何金额运算或存储。

#### Scenario: 输入分币金额

- **GIVEN** 用户在记一笔输入「0.01」
- **WHEN** 系统解析并落库
- **THEN** 该记录 `amount` 为 `1`
- **AND** 明细显示「0.01」

#### Scenario: 输入含小数的计算结果

- **GIVEN** 用户用内置计算器算「0.1 + 0.2」并记账
- **WHEN** 系统保存该笔
- **THEN** 落库金额为 `30`（0.30 元）
- **AND** 不出现 0.30000000000000004 之类浮点尾差

**理由**：整数分从根上消灭舍入类「1 分钱尾差」。剩余尾差只可能来自真实业务（手续费、漏记、汇率），交由 reconciliation 模块处理，不再混入数值精度问题。

---

### Requirement: 账户余额统一带符号

系统 SHALL 用带符号的 `balance` 表示所有账户：资产账户通常非负，负债账户欠款为负（欠款 500 元存为 `-50000`）。所有余额变动 MUST 统一写成 `balance += delta`，MUST NOT 按 `ASSET` / `LIABILITY` 分支反向处理。

#### Scenario: 信用卡消费

- **GIVEN** 信用卡账户 `balance = -50000`，用户记一笔 30 元支出
- **WHEN** 事务提交
- **THEN** `balance` 变为 `-53000`
- **AND** 净资产相应减少 3000 分

#### Scenario: 信用卡还款

- **GIVEN** 用户从储蓄卡向信用卡转账 500 元
- **WHEN** 事务提交
- **THEN** 储蓄卡 `-50000`、信用卡 `balance` 归零
- **AND** 净资产在转账前后不变

**理由**：统一带符号后，消费、还款、转账共用一套 `+delta` 逻辑，分支越少越不容易漏。v1 曾同时写「欠款存负数」和「净资产 = 总资产 − 总负债」，两者语义冲突，会凭空虚高净资产。

---

### Requirement: 净资产为余额求和

系统 SHALL 以 `净资产 = SUM(balance WHERE includeInTotal = 1)` 计算净资产。系统 MUST NOT 使用「总资产 − 总负债」作为计算式。

#### Scenario: 资产与负债并存

- **GIVEN** 资产账户合计 1000000 分，信用卡余额 -50000 分
- **WHEN** 打开资产页
- **THEN** 净资产显示 9500.00 元
- **AND** 不得显示 10500.00 元

**理由**：负债余额本身已是负数，再减一次等于取负。公式只保留一条，消除实现时的歧义。

---

### Requirement: 负债总额仅作展示

系统 MAY 以 `ABS(SUM(balance WHERE type = LIABILITY))` 展示「总负债」，但该展示值 MUST NOT 参与任何计算。可用额度 SHALL 按 `creditLimit + balance` 计算。

#### Scenario: 负债展示

- **GIVEN** 信用卡欠款 500 元、额度 20000 元
- **WHEN** 查看资产页与账户详情
- **THEN** 总负债显示 500.00 元、可用额度显示 195.00 元
- **AND** 净资产计算仍使用 -50000 分

**理由**：把「给人看的数」和「参与计算的数」分开，避免展示层的绝对值污染计算层。

---
