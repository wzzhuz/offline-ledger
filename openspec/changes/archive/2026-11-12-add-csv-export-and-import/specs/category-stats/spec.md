# Delta for 分类与统计

## ADDED Requirements

### Requirement: 明细筛选复用唯一统计口径且不改既有聚合 SQL

明细页的 `pagedFiltered` 与 `dailyTotals` SHALL 继续带
`excludeFromStats = 0` 且 `type IN ('EXPENSE','INCOME')`，
加币种条件后 MUST 保持这两条口径不变。
6c-1 的 13 条 `...ForCurrency` 统计查询与 `StatRules.SQL_STAT_RELEVANT`
MUST NOT 改动。
明细页与统计页在相同月份、相同币种下的支出合计 SHALL 一致。

#### Scenario: 两页合计一致

- **GIVEN** 2026 年 11 月 HKD 支出合计 123456 分
- **WHEN** 明细页与统计页都切到该月 HKD
- **THEN** 两处支出合计均为 123456 分

#### Scenario: 不计入统计的流水

- **GIVEN** 一条 `excludeFromStats = true` 的流水
- **WHEN** 明细页按币种筛选
- **THEN** 该流水不出现在日合计中
- **AND** 统计页也不计入

**理由**：口径一致性靠「复用同一条常量 + 不改既有 SQL」保证，
比在 Kotlin 层做减法或另写一份聚合可靠；
这也是 6b / 6c-1 连续验证过的隔离思路。
