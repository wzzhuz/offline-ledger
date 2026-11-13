# 任务清单：add-quick-entry-and-templates

## 1. 前置：归档与闸门

- [x] 1.1 强制开场复述、五项对齐结论、分级判定并停下等确认
- [x] 1.2 使用者确认「按推荐来」
- [x] 1.3 **归档出包阶段**：回填两次 run 实测（R8 -24.9%、
  坚果云 skipped=false）→ 9 条 ADDED 合并 → change 移入
  `archive/2026-11-12-build-release-pipeline/` → 校验 PASS
  （8 模块现为 **137 条 Requirement / 212 个 Scenario**）
- [x] 1.4 **闸门改写**：`project.md`「v1 明确不做」→「v1.1 分批计划」表，
  6a 本批实现 / 6b / 6c 待决策 / 三级分类移入永久不做；清单不删除

## 2. change 四件套

- [x] 2.1 `proposal.md` —— 原始场景与完整「对齐结论」节
- [x] 2.2 `design.md` —— 闸门 / 两张无索引表 / Migration /
  备份兼容 / 排期引擎 / UI 三入口 / 回退顺序 / 否决方案表
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 5 份 delta，共 12 条 ADDED
- [x] 2.5 推送 change 文件到远端并用 API 校验

## 3. 数据层：两张表与首次迁移

- [x] 3.1 `RecordTemplateEntity` / `RecurringRuleEntity`（无索引、无外键）
- [x] 3.2 `TemplateDao` / `RecurringRuleDao`
- [x] 3.3 `AppDatabase`：entities +2、`version = 2`、两个 DAO 方法
- [x] 3.4 `Migrations.kt`：`MIGRATION_1_2` 两条 CREATE TABLE + `ALL`
- [x] 3.5 `DatabaseModule`：`.addMigrations(*Migrations.ALL)` + 两个 provide
- [x] 3.6 `Converters` 无需改（枚举仍按 name 存 TEXT）

## 4. 备份：CURRENT=2 / MIN_SUPPORTED=1

- [x] 4.1 `BackupSchema` 升 2、`BackupCounts` / `BackupPayload` /
  `LedgerSnapshot` 加带默认值字段；**信封四项头部一字不改**
- [x] 4.2 `BackupTemplate` / `BackupRecurringRule` DTO + 4 个 mapper
- [x] 4.3 `BackupExporter` 传新列表与 counts
- [x] 4.4 `exportSnapshot` / `importBackup` 处理两张新表，末尾重算

## 5. Repository：9 个新写方法

- [x] 5.1 `createTemplate` / `updateTemplate` / `archiveTemplate` /
  `reorderTemplates`
- [x] 5.2 `createRecurringRule` / `updateRecurringRule` / `setRuleEnabled`
- [x] 5.3 `recordFromTemplate`：走 `withResolvedRoot` + 同一事务 + 重算
- [x] 5.4 `applyDueRules`：单事务批量插入 + 末尾一次重算
- [x] 5.5 `check_transaction_annotations.py` 的 `WRITE_METHODS` 扩到 26 个，
  并加 `version = 2` / `Migration(1, 2)` / SQL 列名对齐检查

## 6. 引擎与 UI

- [x] 6.1 `engine/RecurringSchedule.kt` 纯 JVM 排期 + `Cadence` 枚举
- [x] 6.2 `ui/record/TemplateQuickPick.kt`：记一笔顶部 chips，
  点一下直接生成流水
- [x] 6.3 `RecordEditorDialog` 加可选参数，不改既有表单状态
- [x] 6.4 `ui/templates/TemplateManagerDialog.kt` +
  `TemplateManagerViewModel.kt`：两 Tab、列表、新增、归档、启用开关
- [x] 6.5 `SettingsScreen` 加「模板与周期记账」入口
- [x] 6.6 `LedgerEntryPoint` 加两个 DAO 方法
- [x] 6.7 `LedgerApp` 冷启动 `LaunchedEffect` 触发 `applyDueRules`

## 7. 桌面快捷方式

- [x] 7.1 `res/xml/shortcuts.xml` + `strings.xml` 两个标签
- [x] 7.2 `AndroidManifest.xml` 在 MainActivity 内加 `android.app.shortcuts` meta-data
- [x] 7.3 `MainActivity` 读 action → `startWithRecordEntry`，
  **只在 unlocked 分支传给 LedgerApp**
- [x] 7.4 `LedgerApp(startWithRecordEntry: Boolean = false)`

## 8. 测试与文档

- [x] 8.1 `RecurringScheduleTest`（月度 / 周度 / 日度 / 月末 / 幂等 / catch-up）
- [x] 8.2 `BackupV1CompatTest`（v1 解析成空列表、v2 往返、过高版本拒绝）
- [x] 8.3 `MigrationContractTest`（两条 CREATE TABLE 常量列齐全）
- [x] 8.4 `README.md` 状态与 6a 说明、`project.md` 分阶段表已改
- [x] 8.5 本地六个检查脚本全 PASS
- [ ] 8.6 单次 commit 推送，查 Actions 确认**确实触发**并全绿
- [ ] 8.7 推完用远端 API 逐文件比对 blob sha

## 9. 收尾

- [ ] 9.1 同步 `design.md` / `tasks.md` 实测结果（含 CI 首轮若失败的原因）
- [ ] 9.2 使用者确认后归档（12 条 ADDED 合并 + change 移入 archive）

## 明确留到后续（不预先打勾）

- **真机从 v1 覆盖升级验证 `Migration(1→2)`**
- 6b：预算超支提醒、退款 / 报销成对模型
- 6c：多币种、CSV 导出（需单独过隐私评审）
- 小组件 / 通知栏磁贴
- R8 包真机启动、SQLCipher 打开数据库、BiometricPrompt 弹窗、
  SAF 选择器、5 万条切月 / 年报聚合耗时

> 归档需使用者明确指令，本轮不擅自归档。
