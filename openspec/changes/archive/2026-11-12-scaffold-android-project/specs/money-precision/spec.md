# Delta for 金额精度与净资产

## ADDED Requirements

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
