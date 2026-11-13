# Delta for 账务联动与一致性

## ADDED Requirements

### Requirement: 新增统计查询复用唯一口径

阶段 4 新增的只读统计查询 SHALL 带
`type = 'EXPENSE' AND excludeFromStats = 0`（趋势查询为
`type IN ('EXPENSE','INCOME') AND excludeFromStats = 0`），
并 SHALL 被 `scripts/check_stat_rules.py` 覆盖。
系统 MUST NOT 在 UI 层手写裸 SQL 各自过滤。
新查询的函数名 MUST NOT 以已有统计查询名为前缀，
以免静态检查回溯 `@Query` 时指向错误的方法。

#### Scenario: 口径被改歪

- **GIVEN** 有人把 `subcategoryTotalsUnderRoot` 的 `excludeFromStats = 0` 删掉
- **WHEN** CI 运行静态检查
- **THEN** `check_stat_rules` 失败并使 job 失败
- **AND** 不产出一个「不计入收支的流水混进下钻图」的版本

#### Scenario: 下钻金额与一级扇区相等

- **GIVEN** 「餐饮」一级扇区金额 5000 分
- **WHEN** 展开该大类下二级构成
- **THEN** 二级各项金额合计为 5000 分
- **AND** 二者使用完全相同的过滤口径

**理由**：下钻是新增的唯一 SQL 出口，最容易顺手漏掉
`excludeFromStats`；命名前缀约束来自阶段 1 归档两次
「宽泛子串搜索自误报」换来的经验。
