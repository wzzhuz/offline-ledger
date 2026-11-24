# Delta for reporting-ui

## ADDED Requirements

### Requirement: 资产页逐币种净资产标签与负债红字负数

资产页蓝底身份区 SHALL 按 `currencyCode` **逐组**渲染，
每组 SHALL 把原「X 小计」标为「**净资产**」，
SHALL 以常规色显示「资产 +X」、以**红色系高对比浅红**（蓝底取 `0xFFFFCDD2`，与 `ExpenseRed` 同系）显示「负债 -X」
（负号为纯展示文本，`CurrencyGroup.liabilitiesCents` 仍为绝对值、
MUST NOT 参与任何运算）。
账户行负余额 SHALL 继续以红字负数显示，MUST NOT 被本批改掉。
MUST NOT 把各组 `netCents` 汇总成一个跨币种的单一「净资产」总数，
MUST NOT 违反基线「资产页按币种分组且移除单一净资产总数」。

#### Scenario: 两币种各自净资产

- **GIVEN** 一个 HKD 账户与一个 CNY 账户，其中 CNY 有一笔信用负债欠款
- **WHEN** 打开资产页查看蓝底区
- **THEN** 出现 HKD / CNY 两组各自的「净资产」，CNY 组负债为红字负号
- **AND** 页面不出现把两币种加在一起的单一净资产数额

#### Scenario: 账户行负数语义不变

- **GIVEN** 信用卡账户 `balanceCents = -50000`
- **WHEN** 查看账户行
- **THEN** 金额显示为负数且为红字，MUST NOT 变成绝对值或白字

---

### Requirement: 记一笔常用候选按独立优先级排序且带卡种标志

记一笔常用账户候选 SHALL 按 `quickOrder` 排序取前 4，
SHALL 允许**不同 `AccountType` 的账户一起参与排序**（常用置顶），
MUST NOT 再取 `sortOrder` 的全局前 4。
`AccountQuickCandidates.select` 的「常用前 N + 当前选中项必可见」
语义 SHALL 保持不变，只更换传入列表的数据源顺序。
每个候选 chip SHALL 以 12dp 小角标单字（「储」/「信」）
显示 `cardKind`，MUST NOT 用文字后缀挤占账户名宽度。
资产页分组内排序 MUST NOT 改变候选顺序。

#### Scenario: 跨类型常用置顶

- **GIVEN** 使用者在「全部账户排序」页把一张信用卡与一张储蓄卡排到前两位
- **WHEN** 打开记一笔
- **THEN** 常用候选前两项就是这两张卡，且带各自的小标志
- **AND** 资产页分组内顺序未因此改变

---

### Requirement: 全部账户排序独立页

系统 SHALL 提供独立「全部账户排序」页（NavHost 路由 `account-quick-sort`），
入口 SHALL 在资产页蓝底身份区固定可见，MUST NOT 新增第 5 个底部 Tab。
该页 SHALL 以单一 `LazyColumn` 展示**全部未归档账户、不分类型**，
SHALL 复用 `sh.calvin.reorderable:reorderable:3.1.0` 与
`rememberKeyedReorder`，MUST NOT 自写 `Modifier.pointerInput`；
SHALL 提供「保存 / 放弃」与一行「这里排的是记一笔里常用账户的先后顺序」提示。
`draftOrder` SHALL 与已落库 `quickOrder` 完全分离：
放弃 MUST 零写库，保存 SHALL 一次性写入并在完成后刷新。
每行 SHALL 显示小标志、账户名与类型标签，
上移 / 下移 SHALL 以 `semantics.customActions` 保留作 TalkBack 兜底。

#### Scenario: 保存与放弃

- **GIVEN** 在独立页拖动若干账户后
- **WHEN** 点「放弃」
- **THEN** 页面关闭、`quickOrder` 零变化、记一笔候选不变
- **WHEN** 重新进入拖动后点「保存」
- **THEN** `quickOrder` 一次性写回、记一笔候选按新顺序、`sortOrder` 零变化

#### Scenario: 导航格数不变

- **GIVEN** 从资产页进入全部账户排序页
- **WHEN** 查看底部导航
- **THEN** 仍是明细 / 资产 / 统计 / 设置 4 个 Tab
