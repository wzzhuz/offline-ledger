# 导入 Firefly III 导出的 CSV

## Why

使用者此前用 Firefly III 6.6.6 记账，现要把历史账本一次性搬进本项目。
这是**一次性历史迁移**，不是持续同步。Firefly III 是复式记账（source / destination
两个账户，其中一方常是外部 Expense / Revenue account），本项目是单式流水
（账户只装自己的资产 / 负债 / 充值 / 投资，`counterparty` 是流水级自由文本）。
两者的语义差必须在导入层收敛，不能把复式模型带进本项目。

## What Changes

- 设置 → 数据与备份新增「导入 Firefly III 导出（CSV）」入口，进入独立二级页
- 纯 JVM 解析 6.6.x `transactions.csv`：表头按列名别名映射、引号状态机复用 `CsvCodec`
- 生成映射预览：账户逐项对齐（同名建议 + 可改 + 可一键新建）、分类逐项对齐、报告各类行数
- 二次确认后单事务写入：按需新建账户 / 两级分类 → 批量写流水 → 末尾余额重算
- 零表结构变更、零新增依赖、零新增权限；不引入网络行为

## Capabilities

- `backup-restore`（MODIFIED 1 条 + ADDED 1 条）：两种导入入口并存，互不冲突
- `ledger-transactions`（ADDED 1 条）：复式 → 单式的字段映射与期初余额语义
- `reporting-ui`（ADDED 1 条）：迁移二级页与映射预览

## Impact

- 代码：`backup/csv/` 新增 2 个纯 JVM 文件、`ui/settings/` 新增 1 页、
  `LedgerRepository` 新增 1 个写方法、`SettingsViewModel` 新增状态机、
  `LedgerApp` 新增 1 个路由
- 静态检查：`check_transaction_annotations.py` 的写方法清单扩到 40
- 版本 `0.2.5 → 0.2.6`；覆盖安装即可，不用卸载
- 不碰：DAO 查询、Entity、`AppDatabase.version`、备份 DTO、统计口径、Manifest、release.yml
