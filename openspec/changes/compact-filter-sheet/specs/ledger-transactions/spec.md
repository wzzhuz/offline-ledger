# ledger-transactions · Delta

## MODIFIED Requirements

### Requirement: 组合筛选走单条参数化查询

明细与核对视图 SHALL 由一条带可选参数的 `@Query` 覆盖时间 / 账户 / 类型 /
一级 / 二级分类 / 金额区间 / 备注全文 / 交易对方的全部组合，
所有值 SHALL 通过参数绑定传入，MUST NOT 用字符串拼接 WHERE 值。
查询 SHALL 通过 `LEFT JOIN` 一次取回分类名、主题色与账户名。
交易对方 SHALL 以
`(:counterpartyQuery IS NULL OR COALESCE(r.counterparty, '') LIKE '%' || :counterpartyQuery || '%')`
短路实现，null 时行为 MUST NOT 改变。

#### Scenario: 组合筛选

- **GIVEN** 用户设置上月 + 微信零钱 + 一级「餐饮」+ 金额 20–100 元 + 备注含「午饭」
- **WHEN** 执行查询
- **THEN** 返回同时满足全部条件的流水
- **AND** 每行已带分类名与账户名，渲染时不再逐行查库

#### Scenario: 交易对方组合筛选

- **GIVEN** 用户输入交易对方「便利」并同时设置账户与金额区间
- **WHEN** 执行查询
- **THEN** 返回同时满足三项条件的流水
- **AND** 清空交易对方后行为与未设置时完全一致

#### Scenario: 注入面

- **GIVEN** 备注搜索框或交易对方输入框输入 `' OR 1=1 --`
- **WHEN** 执行查询
- **THEN** 它被当作字面子串匹配
- **AND** 不返回全部流水

**理由**：动态组合筛选最容易写成拼 SQL；参数化绑定既零注入面，
又让一条 SQL 覆盖所有组合，避免 `@RawQuery` 或多份 `@Query` 的组合爆炸。
第 11 批新增交易对方时，MUST 沿用同一条查询而不是另开一条。
