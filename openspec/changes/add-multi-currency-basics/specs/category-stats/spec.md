# Delta for 分类与统计

## ADDED Requirements

### Requirement: 按币种统计走独立查询且既有聚合 SQL 不改

系统 SHALL 提供带 `currencyCode = :currencyCode` 的
`...ForCurrency` 聚合查询覆盖收支概览、一级 / 二级占比、
月度趋势、年度趋势、下钻与 SETTLEMENT 合计。
这些查询 SHALL 全部带 `excludeFromStats = 0`，
MUST 纳入 `check_stat_rules.py` marker 清单。
既有 12 条聚合 SQL 与 `StatRules.SQL_STAT_RELEVANT` MUST NOT 改动。

#### Scenario: HKD 占比不含 CNY

- **GIVEN** 餐饮分类下同时有 HKD 与 CNY 支出
- **WHEN** 统计页选 HKD 查看一级占比
- **THEN** 餐饮金额只含 HKD 流水

#### Scenario: 新查询漏口径

- **GIVEN** 某条 `...ForCurrency` 查询未带 `excludeFromStats = 0`
- **WHEN** 执行 `check_stat_rules.py`
- **THEN** 检查失败并指出该查询

**理由**：沿用 6b 验证过的「既有聚合 SQL 零改动 + 新增独立查询」思路；
`aggregateAccountDeltas` 若被误加币种条件会直接算错账户余额，
隔离是最稳的做法。
