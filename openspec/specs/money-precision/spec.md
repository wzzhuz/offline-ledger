# 金额精度与净资产 Specification

## Purpose

定义金额表示、账户余额符号与净资产计算。这是全应用最容易算错钱的部分，也是「资产状况可见」这一核心诉求的数值基础。

---

## Requirements

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

### Requirement: Cents 基础类型

系统 SHALL 提供 `@JvmInline value class Cents(val value: Long)` 作为所有金额的
基础类型，并 SHALL 允许负值（用于账户余额与校准差额）。
输入解析 SHALL 使用 `BigDecimal.setScale(2, RoundingMode.HALF_UP)` 与
`longValueExact()`，MUST NOT 使用 `toLong()` 静默截断。

#### Scenario: 输入分币金额

- **GIVEN** 用户输入字符串 `"0.01"`
- **WHEN** 调用 `Cents.fromYuan("0.01")`
- **THEN** 返回 `Cents(1)`
- **AND** `toYuanString()` 返回 `"0.01"`

#### Scenario: 超出 Long 范围的输入

- **GIVEN** 用户输入一个超出 `Long` 范围的巨额字符串
- **WHEN** 调用 `Cents.fromYuan(...)`
- **THEN** 抛出明确的异常
- **AND** 不静默截断为一个错误的小金额

**理由**：`Float` / `Double` 参与金额会产生 0.30000000000000004 这类尾差，
而整数分从根上消灭舍入类「1 分钱差额」。用 `longValueExact()` 是为了
让溢出显式失败，而不是把错误藏进一个看起来正常的数字。

---

### Requirement: 本地日期与年月冗余列

系统 SHALL 提供由 `timestamp` 派生 `localDate`(YYYYMMDD) 与 `yearMonth`(YYYYMM)
的统一函数，并 SHALL 固定使用 `ZoneId.of("Asia/Hong_Kong")`。
系统 MUST NOT 在 SQL 中对 `timestamp` 做函数运算来分组。

#### Scenario: UTC 与本地跨月

- **GIVEN** 时间戳 `2025-12-31T16:00:00Z`（即香港时间 2026-01-01 00:00）
- **WHEN** 调用派生函数
- **THEN** 返回 `localDate = 20260101`、`yearMonth = 202601`
- **AND** 该流水归入 2026 年 1 月而非 2025 年 12 月

**理由**：`reporting-ui` spec 要求冗余本地日期列以避免索引失效与时区串月。
把时区与派生逻辑收敛到一个函数，各页面才不会各写一套。

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

### Requirement: 净资产看板落地且不靠减法倒推

资产页 SHALL 以 `SUM(balanceCents WHERE includeInTotal = 1)` 展示净资产，
SHALL 分别展示总资产 `SUM(balance WHERE includeInTotal AND type = ASSET)` 与
总负债 `ABS(SUM(balance WHERE includeInTotal AND type = LIABILITY))`。
总负债 SHALL 明确标注「仅展示，不参与计算」。
系统 MUST NOT 用「总资产 − 总负债」反推净资产。

#### Scenario: 资产与负债并存

- **GIVEN** 资产账户 10000 分、信用卡 −500 分
- **WHEN** 打开资产页
- **THEN** 净资产显示 95.00 元
- **AND** 不得显示 105.00 元

#### Scenario: 排除账户不污染展示值

- **GIVEN** 另有一个 `includeInTotal = false` 的账户 20000 分
- **WHEN** 打开资产页
- **THEN** 净资产仍为 95.00 元
- **AND** 总资产 / 总负债也不含该账户

**理由**：「净资产 = SUM(balance)」与「总资产 − 总负债」在带符号余额下
只差一个负号，却是最容易算错钱的一处；三个数各自独立由 SQL 聚合，
并在单测里用排除账户证明净资产不是减法倒推出来的。

---

### Requirement: 信用账户可用额度按额加负余额计算

信用负债账户 SHALL 展示 `可用额度 = creditLimitCents + balanceCents`
（`balanceCents` 欠款为负），MUST NOT 写成 `creditLimitCents − balanceCents`。
无额度的账户 SHALL 不展示该项。

#### Scenario: 欠款 500 元、额度 20000 元

- **GIVEN** 信用卡 `creditLimitCents = 2000000`、`balanceCents = −50000`
- **WHEN** 查看账户卡
- **THEN** 可用额度显示 195.00 元
- **AND** 不显示 205.00 元

#### Scenario: 无额度

- **GIVEN** 一个 `creditLimitCents = null` 的负债账户
- **WHEN** 查看账户卡
- **THEN** 不显示可用额度行

**理由**：余额本就带符号，再减一次等于把欠款当成可用余额，凭空虚高。

### Requirement: 图表展示金额仍由整数分换算且浮点不回写

统计页所有金额的**存储、求和、比较、差额** SHALL 保持 `Long` 分，
`BigDecimal` MAY 仅用于展示格式化。系统 MAY 用 `Float` 做
「金额比例 → Canvas 绘制角度 / 归一化坐标」的几何换算，
但该结果 SHALL 只进 `DrawScope`，MUST NOT 回写任何金额、
MUST NOT 参与统计或余额计算。合计差额 SHALL 以分为单位展示。

#### Scenario: 扇区角度换算

- **GIVEN** 三个分类支出分别为 1000 / 2000 / 3000 分
- **WHEN** 计算扇区角度
- **THEN** 三个 sweepAngle 之和为 360°
- **AND** 三个金额的和仍由 `Long` 累加、不经过浮点

#### Scenario: 合计差额展示

- **GIVEN** 一级合计与总支出相差 100 分
- **WHEN** 显示警告条
- **THEN** 文案为「差额 100 分」
- **AND** 不用浮点元值做比较

**理由**：把「绝对禁止浮点数」的边界写清楚，避免后来者
误以为金额铁律被 Canvas 破了，或反过来用 Float 去累加金额。
