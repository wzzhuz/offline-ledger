# 任务清单：add-budgets-and-settlements

## 1. 前置：闸门与归档状态

- [x] 1.1 强制开场复述、五项对齐结论、分级判定并停下等确认
- [x] 1.2 使用者确认「按推荐来」（只做两项 / SETTLEMENT / 月度分类预算含总预算）
- [x] 1.3 阶段 6a 已归档（12 条 ADDED 合并，基线 149 Req / 237 Scenario）
- [x] 1.4 **闸门放行**：`project.md` 6b 两项改「本批实现」；
  小组件/通知栏磁贴移入 6c 并写明原因
- [x] 1.5 **真机实测推迟决定**已写入 6a 归档 `design.md`，
  本 change 重写累积清单并提醒装机前导出加密备份

## 2. change 四件套

- [x] 2.1 `proposal.md` —— 原始场景与完整「对齐结论」节
- [x] 2.2 `design.md` —— SETTLEMENT 联动 / 净支出减法 / 预算 /
  Migration(2→3) / 真机累积清单 / 否决方案表
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 6 份 delta，共 13 条 ADDED
- [x] 2.5 推送 change 文件到远端并用 API 校验

## 3. 数据层：budgets 表 + linkedRecordId + 迁移

- [x] 3.1 `RecordType` 加 SETTLEMENT；`RecordEntity` 加 `linkedRecordId`
- [x] 3.2 `BudgetEntity` / `BudgetScope` / `BudgetDao`
- [x] 3.3 `AppDatabase` entities +1、`version = 3`、`budgetDao()`
- [x] 3.4 `Migrations.kt`：`MIGRATION_2_3` CREATE TABLE + ALTER TABLE，
  `ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)`
- [x] 3.5 `DatabaseModule` 加 provideBudgetDao
- [x] 3.6 `LedgerDi.entryPoint` 加 budgetDao()
- [x] 3.7 RecordDao 加 3 条 SETTLEMENT 聚合 + `settlementTotalFor`

## 4. 备份 CURRENT=3 / MIN_SUPPORTED=1

- [x] 4.1 `BackupSchema` 升 3；`BackupCounts` / `BackupPayload` /
  `LedgerSnapshot` 加带默认值字段；`BackupRecord` 加 linkedRecordId
- [x] 4.2 `BackupBudget` DTO + 2 个 mapper；Record mapper 补 linkedRecordId
- [x] 4.3 `BackupExporter` 计入 budgets counts
- [x] 4.4 `exportSnapshot` / `importBackup` 处理 budgets

## 5. Repository：4 个新写方法

- [x] 5.1 `createBudget` / `updateBudget` / `archiveBudget`
- [x] 5.2 `recordSettlement`：校验链 + 走 withResolvedRoot + 同一事务 + 重算
- [x] 5.3 `WRITE_METHODS` 扩到 30；Migration 静态检查扩到 version=3 / 两段迁移

## 6. 引擎：净支出与预算（纯 JVM）

- [x] 6.1 `ChartData.netSlices(expense, settlementMap)`
- [x] 6.2 `BudgetEngine.progress()` + `BudgetProgress`
- [x] 6.3 `SettlementNetTest` / `BudgetEngineTest`

## 7. UI：三处落点

- [x] 7.1 `LedgerUiParts` 两个穷尽 when 补 SETTLEMENT 分支
- [x] 7.2 `RecordEditorViewModel.save` 的 when 补分支；
  `TemplateManagerDialog` 的 `RecordType.entries` 过滤 SETTLEMENT
- [x] 7.3 `DetailViewModel.settle()`；`DetailScreen` 加「报销」按钮与
  金额输入 `AlertDialog`（默认剩余可报）
- [x] 7.4 `StatsUiState` 加 settlementCents / netExpenseCents / budgetItems；
  `StatsViewModel.reload` 用 netSlices 与 BudgetEngine
- [x] 7.5 `StatsScreen` 概览加「已报销 / 净支出」，`BudgetSection` 进度条
- [x] 7.6 `BudgetManagerDialog` + `BudgetManagerViewModel`；
  `SettingsScreen` 加「预算管理」入口

## 8. 测试与文档

- [x] 8.1 `BackupV2CompatTest`（v1/v2 解析、v3 往返）
- [x] 8.2 `MigrationContractTest` 扩 2→3 契约
- [x] 8.3 `check_stat_rules.py` marker 加 3 条 SETTLEMENT 查询
- [x] 8.4 `README.md` / `project.md` 状态同步
- [x] 8.5 本地六个检查脚本全 PASS
- [x] 8.6 单次 commit 推送，查 Actions 确认确实触发并全绿
- [x] 8.7 推完用远端 API 逐文件比对 blob sha

## 9. 收尾

- [x] 9.1 同步 `design.md` / `tasks.md` 实测结果
- [x] 9.2 使用者确认后归档（13 条 ADDED 合并 + change 移入 archive）

## 明确留到后续（不预先打勾）

> **使用者决定：真机实测整体推迟到完成度更高后统一装机体验。**
> 以下各项至今**全部未验证**，不得在后续阶段被改写成「已通过」。
> 装机前建议先用旧版导出一份加密备份。

- **`Migration(1→2)` + `Migration(2→3)` 从 v1 覆盖升级**
- R8 包真机启动、SQLCipher 打开数据库、BiometricPrompt、SAF
- 5 万条切月 / 年报聚合耗时、滚动掉帧
- 模板快选、周期补记、桌面快捷方式
- 6b 新增：报销入口、预算进度条、净支出环形图
- 6c：多币种、CSV、账单导入、小组件与通知栏磁贴

> 归档已由使用者「接着开6c」按推荐 A 授权执行。
