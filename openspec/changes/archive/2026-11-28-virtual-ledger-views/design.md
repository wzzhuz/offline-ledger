# 设计：虚拟账本 = 标签 + 视图

## 决策 1：虚拟账本不是账本维度

清单结果：`AppDatabase version = 9`，12 张 Entity 全部无 `bookId`；所有 DAO、统计、
预算、周期规则、模板、批量规则、别名、标签、加密备份 DTO 都是全库口径。加 scope
要改几乎所有 SQL 与写路径，还要首次注册 Migration、处理旧数据归账本、转账两侧
跨账本——成本不成比例，且会撞铁律 3 的低摩擦默认路径。

改用三件套：`record_tags` 标签定义归属 + `excludeFromStats` 摘出收支报表 +
独立台账页提供录入上下文与按人净额。零改表、零格式变更、可直接覆盖安装。

## 决策 2：切换器只切浏览范围

一羽 / 钱迹的切换器同时承担「查看范围」与「录入归属」。本项目自动记账是
0 次确认自动落库，一旦带上录入归属，停在礼金视图时分享一笔午餐消费会被打上
礼金标签并勾不计入，日常支出凭空消失。钱迹用户提 issue「希望记住上次选的账本」
正是这类隐式状态的代价。

因此：切换器无跨启动记忆、默认永远回主账本；台账录入只能从台账页内按钮进入；
记一笔 FAB 与 `TextIntakeActivity` 一律无虚拟账本上下文。静态脚本断言
「TextIntakeActivity 不得感知虚拟账本」。

## 决策 3：不计入收支，而不是负债账户

`excludeFromStats` 过滤的是日期头合计、`tagStats`、对方排行等统计聚合；
`pagedFiltered`（明细列表）**不过滤**该列，所以台账记录在明细与台账页都查得到。
上一轮曾误以为「勾了就查不到」，是拿统计 SQL 外推到明细，已更正。

备选方案是建「人情往来」虚拟负债账户并把收礼记成 TRANSFER，`LedgerEngine`
会让净资产净变动为 0，会计上更干净；但每笔要切转账态、选两个账户，默认路径
明显变绕，且按人对账仍要靠 `counterparty`。不采用，留作已知备选。

**如实登记**：钱真实进了微信 / 银行卡，净资产仍会如实增加。人情债不是法律负债，
这是事实不是虚高。

## 决策 4：台账聚合另写 SQL

`tagStats` 过滤 `excludeFromStats = 0` 且 `SUM(amountCents)` 不分方向，直接复用
会一条都查不到且方向错。`VirtualBookDao.partyTotals` 必须：不过滤不计入、
`SUM(CASE WHEN type='INCOME'...)` / `SUM(CASE WHEN type='EXPENSE'...)`
分方向、带 `currencyCode` 条件、按 `counterparty` 分组（空值归「（未填对方）」）。
`待还 = 收 − 还`，**不设「结清」状态**：还礼常加码，净额不会归零。

## 决策 5：定义存 SharedPreferences，标签按需创建

`VirtualBookStore` 用纯 JVM `VirtualBookCodec` 编解码（零新增依赖、可 JVM 单测），
与 `accountPickerLayout` 同口径。`tagName` 创建后保持稳定，**改名不改标签**，
否则历史流水会因旧标签失联；重建同名视图仍生成 `tagFor(name)`，与已落库标签一致。

标签由 `LedgerRepository.bindTags` 在保存时按需自动创建，本批不动 Repository。
定义不进加密备份，但流水上的 `BackupRecord.tagNames` 完整往返，换设备后重建
同名视图即可恢复查看。

## 决策 6：主账本明细排除口径

给 `RecordDao.pagedFiltered` 的 SQL 增加一条 `NOT EXISTS` 排除 `台账:%` 标签，
**不改方法签名、不改 `RecordFilter`、不动 Paging 调用链**。用户显式按标签筛选时
`:tagName IS NOT NULL` 让排除条件短路，作为「查回来」的入口。代价是入口不是
显式的「显示全部记录」开关，已在已知限制中登记。

## 考虑过的其他方案（被否决）

| 方案 | 否决理由 |
|---|---|
| 独立 app | 资金进主账本账户却不在主账本记账，资产账对不上；重复维护应用锁/备份/导出 |
| 完整多账本 | 12 表加 scope、撞铁律 3、切换记错账本、初始账本不可删 |
| 照搬一羽式录入归属跟随 | 与 0 次确认自动落库组合成静默记错账 |
| SETTLEMENT / `linkedRecordId` 配对 | 语义是冲减原 EXPENSE，礼金方向相反且会污染净支出 |
| 虚拟负债账户 + TRANSFER | 默认路径变绕，留作备选不实施 |
| 补 CSV `excludeFromStats` 列 | 使用者明确 CSV 不会是备份主力；改格式需另验往返 |
| 用云端/端侧模型识别礼金 | 与分类归属无关，且撞本地口径 |
