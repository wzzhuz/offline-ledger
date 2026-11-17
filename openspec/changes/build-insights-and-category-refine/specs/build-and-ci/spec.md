# build-and-ci · Delta

## ADDED Requirements

### Requirement: 洞察只读聚合 MUST 纳入统计口径静态检查

本批新增的笔数、交易对方 Top、支出账户 Top、分类笔数与分类详情等只读查询
MUST 全部带全局唯一统计口径 `type IN ('EXPENSE','INCOME') AND excludeFromStats = 0`，
MUST 按当前选中币种过滤，MUST 用 `GROUP BY` 返回聚合行且带 `LIMIT` 兜底，
MUST NOT 加载流水实体到内存后分组。
`scripts/check_stat_rules.py` 的 marker 清单 SHALL 登记这些新查询名，
漏登记或缺口径 MUST 使静态检查 FAIL，MUST NOT 以「只读查询」为由豁免。

#### Scenario: 漏口径被拦截

- **GIVEN** 有人把交易对方排行查询的 `excludeFromStats = 0` 删掉
- **WHEN** 跑六个静态检查
- **THEN** `check_stat_rules.py` 报 FAIL 并指出该查询名
- **AND** CI 不进入编译步骤

#### Scenario: 年度只返回聚合行

- **GIVEN** 账本含 5 万条流水
- **WHEN** 切到年度洞察视图
- **THEN** 新增查询返回 ≤ 若干行聚合结果，MUST NOT 返回流水实体列表

**理由**：第 4 批起 `check_stat_rules.py` 就是「统计口径唯一出口」的机械防线；
第 13 批新增 4 条聚合必须一并登记，否则防线出现缺口，与指南 §7.1
「新 DAO 方法 MUST 继续复用唯一口径」一致。
