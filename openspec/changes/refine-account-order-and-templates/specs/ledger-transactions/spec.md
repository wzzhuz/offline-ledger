# Delta for ledger-transactions

## MODIFIED Requirements

### Requirement: 快捷账户模板替代预制账户行

> 标题沿用基线原名以命中整块替换；**现行语义以本节正文为准**。

首次启动 SHALL 为零账户；新增账户页 SHALL 提供只读快捷模板，
由 `AccountTemplateSheet` 底部 sheet 承载，按 `group` 分为
「支付余额 / 银行储蓄卡 / 银行信用卡 / 网络信用」四组，
**资产组与信用负债组 MUST NOT 混在同一组**。
模板清单 SHALL 覆盖六大行、邮储 / 交行、主要股份行与南京银行等城商行，
共 21 家银行，每家 SHALL 各有储蓄卡与信用卡一条，
连同支付余额与网络信用合计 48 条。
模板 `label` / `name` SHALL 用银行**正规全称**（如「工商银行」），
MUST NOT 再用「工行储蓄卡」这类把卡种拼进名称的写法；
卡种 SHALL 由 `cardKind`（`DEBIT` / `CREDIT` / null）表达，
UI SHALL 以**12dp 小角标单字**（「储」/「信」）贴在账户图标右下角，
MUST NOT 用文字后缀挤占名称宽度。
花呗 / 白条 / 美团月付 / 抖音月付 SHALL 归「网络信用」组，
MUST NOT 与支付宝余额、微信零钱并列。
sheet SHALL 为 2 列等宽图标格、末行不足 2 个补 `weight(1f)` 空占位，
4~5 字名 SHALL 单行完整可见。
点选模板 SHALL 只回填账户名称、账户类型与 `cardKind`，
SHALL 立即关闭 sheet，MUST NOT 写库、MUST NOT 清空已填卡号后四位；
sheet 底部 SHALL 保留「自定义名称」入口。
主表单 SHALL 保留「卡号后四位（可选）」输入，`needsLast4` 模板仍 SHALL 可落库。
模板 `iconKey` SHALL 为 Q7 图标资源预留键位，
MUST NOT 在本批预置任何未经逐项授权登记的品牌图形。

#### Scenario: 三击建卡不再冗余拼接

- **WHEN** 使用者选「招商银行」储蓄卡模板、输入 1234 后保存
- **THEN** `name` 为纯名「招商银行」、`cardLast4 = "1234"`、`cardKind = "DEBIT"` 落库
- **AND** 展示名为「招商银行 (1234)」且**只出现一次**后缀
- **AND** 不产生任何预制账户行

#### Scenario: 同名银行的储蓄卡与信用卡可区分

- **GIVEN** 已存在「工商银行」储蓄卡账户
- **WHEN** 再从「银行信用卡」组选「工商银行」信用卡模板并保存
- **THEN** 两个账户 `name` 同为正规全称，但 `cardKind` 分别为 `DEBIT` / `CREDIT`
- **AND** 列表与记一笔 chip 上各有一个不占名称宽度的小标志

#### Scenario: 四组不混排

- **GIVEN** 打开模板 sheet
- **WHEN** 查看分组
- **THEN** 支付余额 / 银行储蓄卡 / 银行信用卡 / 网络信用四组各自独立
- **AND** 花呗 / 京东白条 / 美团月付 / 抖音月付均在网络信用组

**理由**：Q5A 扩清单 + Q6A 正规命名 + Q3A 卡号单一真相源，三项合并整块
替换基线原文，MUST NOT 追加 ADDED 让「显示名带 `(1234)`」与
「`name` 不冗余拼接」两条相反要求并存。

---

### Requirement: 账户类型可改与自定义排序

> 标题沿用基线原名以命中整块替换。

系统 SHALL 允许修正账户类型（资产 ↔ 信用负债）并重算余额；
SHALL 允许无流水账户物理删除、有流水账户 MUST 改为归档；
SHALL 提供已归档账户查看与恢复入口。
**第 60 批：排序 SHALL 拆为两套互不干涉的顺序**：

- `sortOrder` SHALL 只作用于资产页**同 `AccountType` 分组内**排序，
  SHALL 与分类一级 / 模板共用 `sh.calvin.reorderable:reorderable:3.1.0`
  （`ReorderableItem` + `longPressDraggableHandle` + `rememberKeyedReorder`），
  资产页容器 SHALL 为 `LazyColumn`；松手 SHALL 经 `commitAccountOrderAll`
  一次性写入；上移 / 下移 SHALL 保留在「⋯」菜单内作无障碍兜底。
  MUST NOT 改动 `moveAccount` 的分组内交换语义。
- `quickOrder` SHALL 作用于**全部账户、不分类型**的「记一笔优先」顺序，
  SHALL 由独立「全部账户排序」页维护，该页 SHALL 用同一套 reorderable 库、
  SHALL 提供「保存 / 放弃」，`draftOrder` MUST 与已落库 `quickOrder` 完全分离：
  放弃 MUST 零写库，保存 SHALL 经 `commitQuickOrderAll` 一次性写入。
  记一笔常用候选 SHALL 按 `quickOrder` 取前 4，SHALL 允许跨类型混排把常用置顶。
- 两套顺序 MUST NOT 互相写入；独立排序页 MUST NOT 改动 `sortOrder`。
- `AccountEntity` SHALL 新增 `quickOrder` 列，Room SHALL 升 `version 6`。
  **使用者明确「现在都是测试数据，不需要考虑升级迁移」**：
  `Migrations.ALL` SHALL 保持 `emptyArray()`，MUST NOT 注册迁移常量，
  `MigrationContractTest` 的 ALL 断言 SHALL 零改动，
  `SeedData` 的 accounts INSERT 列清单 SHALL 补齐两字段以保证全新库可建。
  测试期 SHALL 卸载重装、装前导出，MUST NOT 用 `fallbackToDestructiveMigration`。

**基线「顺序 SHALL 同步到记一笔的常用账户平铺」由此作废**：
正是这一句让分组内排序污染记一笔，是使用者第 5 条反馈的根因。

#### Scenario: 分组内排序不污染记一笔

- **GIVEN** 资产页「资产账户」分组内有 A、B 两个账户
- **WHEN** 把 B 拖到 A 前面并松手
- **THEN** `sortOrder` 改变、资产页分组内顺序变化
- **AND** `quickOrder` 零变化，记一笔常用候选前 4 与拖动前完全一致

#### Scenario: 独立页保存与放弃

- **GIVEN** 打开「全部账户排序」页，把某个信用负债账户拖到首位
- **WHEN** 点「放弃」后重进
- **THEN** 顺序与进入前一致、`quickOrder` 零写库
- **WHEN** 改点「保存」后打开记一笔
- **THEN** 该账户出现在常用候选前 4，且资产页分组内顺序不变

---

## ADDED Requirements

### Requirement: 账户卡号后四位可编辑且名称不冗余拼接

`cardLast4` SHALL 是卡号后四位的**单一真相源**，`name` SHALL 只存纯账户名，
MUST NOT 再把 ` (1122)` 拼进 `name` 落库。
新增与编辑账户 SHALL 都提供「卡号后四位（可选）」输入（4 位数字过滤），
编辑 SHALL 经 `vm.editAccount` → `repository.updateAccountMeta` → DAO `UPDATE`
写回 `cardLast4`，MUST NOT 出现「编辑改不了卡号」。
全站展示 SHALL 统一由 `name + cardLast4` 组合一次；
对旧库中 `name` 已含 ` (xxxx)` 且 `cardLast4` 相同的账户，
展示 SHALL 去重，MUST NOT 出现「中行储蓄卡(1122)(1122)」；
旧 `name` SHALL 不被数据迁移改写（零数据迁移）。
编辑页初始化 SHALL 剥掉历史后缀再填入名称输入框，
MUST NOT 因一次编辑把后缀重新写回 `name`。

#### Scenario: 编辑改卡号

- **GIVEN** 一个 `name = "中国银行"`、`cardLast4 = "1122"` 的账户
- **WHEN** 编辑把卡号改为 3344 并保存
- **THEN** `name` 仍是「中国银行」、`cardLast4 = "3344"`
- **AND** 列表与记一笔显示「中国银行 (3344)」，只出现一次后缀

#### Scenario: 旧库双后缀被治愈

- **GIVEN** 旧库账户 `name = "中行储蓄卡 (1122)"`、`cardLast4 = "1122"`
- **WHEN** 覆盖安装后打开资产页
- **THEN** 只显示一次「(1122)」，MUST NOT 重复

---

### Requirement: 删除账户必须有可达的二次确认

资产页删除账户 SHALL 弹出二次确认 `AlertDialog`，
SHALL 显示账户名、当前余额与「有流水的账户不能删除，只能归档」提示，
SHALL 有红色警示文案，确认按钮 SHALL 为红色「确认删除」，
SHALL 有「取消」按钮。取消 MUST NOT 删除。
确认弹窗的触发 SHALL 与 `DropdownMenu` 的 dismiss **解耦**
（`pendingDelete + LaunchedEffect(menu)`），
MUST NOT 因 Popup 与新窗口同帧竞争而「点了删除没弹窗」。
`vm.deleteAccount` 的「有流水只能归档」事务语义 SHALL 零改动。

#### Scenario: 点删除一定弹确认

- **GIVEN** 资产页某账户行
- **WHEN** 点「⋮」→「删除」
- **THEN** 一定出现二次确认弹窗，含账户名 / 余额 / 红字警示 /「确认删除」/「取消」
- **AND** 点「取消」后账户仍在、零写库
