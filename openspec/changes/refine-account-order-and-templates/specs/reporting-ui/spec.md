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
每个候选 chip SHALL 以**10dp** 小角标单字（「储」/「信」）
显示 `cardKind`，MUST NOT 用文字后缀挤占账户名宽度。
**第 61 批**：候选与全账户选择弹层的 label SHALL 经 `accountBaseLabel()`
带上 `cardLast4`（如「工商银行 (1122)」），MUST NOT 只显示纯名；
「当前：」文案与弹层搜索键 SHALL 同样基于该组合名，
输入后四位 MUST 能搜到对应卡。多张同名银行卡 SHALL 可区分。
`AccountQuickCandidates.select` / `labelOf` / `promoteById`
的纯 JVM 契约 SHALL 零改动，只更换映射进 `AccountQuickItem.name` 的字符串。
记一笔分类快捷栏图标 SHALL 由 40/34dp 收紧为 **28/24dp**，
分类管理页 5 列网格仍 SHALL 用 40dp（两处密度各按各的场景）。
资产页分组内排序 MUST NOT 改变候选顺序。

#### Scenario: 跨类型常用置顶

- **GIVEN** 使用者在「全部账户排序」页把一张信用卡与一张储蓄卡排到前两位
- **WHEN** 打开记一笔
- **THEN** 常用候选前两项就是这两张卡，且带各自的小标志
- **AND** 资产页分组内顺序未因此改变

#### Scenario: 多张同名银行卡可区分

- **GIVEN** 两张账户名同为「工商银行」、卡号后四位分别为 1122 与 3344
- **WHEN** 打开记一笔查看快捷候选 /「当前：」/ 全账户选择弹层
- **THEN** 分别显示「工商银行 (1122)」与「工商银行 (3344)」
- **AND** 在弹层搜索框输入「3344」能搜到第二张卡
- **AND** 两张卡各带不占名称宽度的 10dp 卡种小标志

#### Scenario: 记一笔图标密度收紧

- **GIVEN** 打开记一笔
- **WHEN** 查看分类快捷栏与账户候选 chip
- **THEN** 一级分类圆图标为 28dp（选中）/ 24dp（未选中），账户小标志为 10dp
- **AND** 分类管理页网格仍为 40dp，MUST NOT 被连带改小

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


---

### Requirement: 账户卡种可编辑且导入账户有保守默认值

**第 61 批**：新增账户页与编辑账户页 SHALL 都提供
「不设 / 储蓄卡 / 信用卡」三态 `cardKind` 选择，
MUST NOT 只有从模板建卡时才带 `DEBIT` / `CREDIT`。
编辑页 SHALL 把**当前选中的** `cardKind` 传给 `onConfirm` 落库，
MUST NOT 写死传 `account.cardKind` 原值
（第 60 批正是这样导致「编辑选不了卡种」）。
导入 / CSV 等**非模板路径**建账户时 SHALL 一并写入 `quickOrder`
与 `cardKind`：保守默认 `LIABILITY → "CREDIT"`，
`ASSET` SHALL 不猜（微信零钱 / 支付宝 / 储蓄卡混在一起，猜了会误标），
使用者 SHALL 可在编辑页随时改。
账户图标位与记一笔 chip SHALL 以 `cardKind` 决定是否有「储 / 信」标志，
未设置时 SHALL 不显示标志、MUST NOT 崩。

#### Scenario: 导入账户补设卡种

- **GIVEN** 通过导入建了一个 `ASSET` 账户、一个 `LIABILITY` 账户
- **WHEN** 打开记一笔查看候选
- **THEN** 信用负债账户带「信」标志，资产账户不带标志（不误标为「储」）
- **WHEN** 编辑资产账户把卡种改成「储蓄卡」并保存
- **THEN** `cardKind = "DEBIT"` 落库，列表与记一笔出现「储」标志

#### Scenario: 编辑保存写回选中值

- **GIVEN** 账户 `cardKind = null`
- **WHEN** 编辑页选「信用卡」并保存
- **THEN** `cardKind = "CREDIT"`，MUST NOT 仍为 null

---

### Requirement: 筛选与统计的账户显示名与全站一致

**第 62 批**：筛选面板的账户选项 SHALL 经 `accountBaseLabel()` 带上
`cardLast4`，MUST NOT 只显示纯名；picker 的搜索按 label 匹配，
输入后四位 SHALL 能定位到对应卡。
明细页的筛选 chip（含 `accountDisplayName` 为空时的 fallback）SHALL
同样带卡号。
统计页「账户」维度排行 SHALL 经 `accountBaseLabel()` 带卡号；
`InsightData.RankItem.name` 同时是下钻 `filterByAccount()` 的只读展示名，
故从统计跳明细后的筛选 chip SHALL 自动带上卡号，
MUST NOT 在 UI 侧再拼一次。
明细列表副行与流水详情页 SHALL 经 `accountLabel()` 组合 DAO 投影的
`fromAccountName` / `fromAccountLast4`（转入同理），
MUST NOT 直接显示 `row.fromAccountName` 纯名。
资产页删除确认弹窗、账户详情标题、默认账户设置、对账 / 核对标题、
回收站流水行 SHALL 全部带卡号。
**第 63 批修订 CSV 往返语义**：CSV 导出的 `account` 列 SHALL 写
**展示名**（`accountLabel(name, cardLast4)`，如「工商银行 (1122)」），
并 SHALL 新增可选 `cardLast4` 列写机器匹配键；
导入 SHALL 按 `(name, cardLast4)` 复合匹配，
MUST NOT 再用 `firstOrNull { it.name == ... }` 静默命中同名第一张卡。
**加密备份 / `BackupAccount.name` 仍是纯名且 MUST NOT 被改动**（走 `id` 还原）。
第 62 批「CSV 导出 MUST NOT 改」的结论已被本批显式推翻，
理由是它只防住了「导入当新账户」，却没防住「同名两卡静默归错」。

#### Scenario: 筛选与统计不再撞名

- **GIVEN** 两张账户名同为「工商银行」、卡号后四位 1122 / 3344
- **WHEN** 打开筛选面板账户选项、统计页账户排行、从排行跳明细后的筛选 chip
- **THEN** 三处都分别显示「工商银行 (1122)」与「工商银行 (3344)」
- **AND** 筛选 picker 搜索框输入「3344」能定位到第二张卡

#### Scenario: 导出带卡号且复合匹配回原账户

- **GIVEN** 两张 `name = "工商银行"`、`cardLast4` 为 1122 / 3344 的账户各有流水
- **WHEN** 导出 CSV
- **THEN** `account` 列分别是「工商银行 (1122)」「工商银行 (3344)」，
  `cardLast4` 列分别是 `1122` / `3344`
- **WHEN** 用该文件重建 / 导入
- **THEN** 两笔流水分别回到 id 对应的原账户，MUST NOT 归到同一张卡
- **AND** 加密备份的 `BackupAccount.name` 仍是纯名「工商银行」

#### Scenario: 老 CSV 同名歧义显式报错

- **GIVEN** 库里有两张同名「工商银行」、卡号 1122 / 3344
- **WHEN** 导入一份没有 `cardLast4` 列、账户列写纯名「工商银行」的老 CSV
- **THEN** 导入 MUST 报「账户不唯一，请指定 cardLast4」并整批回滚
- **AND** MUST NOT 静默把流水挂到第一张卡

