# 任务清单：implement-room-ledger-core

## 1. 阶段 0 对齐与前置归档

- [x] 1.1 读取 `openspec/AGENTS.md` 与 `project.md`，复述状态并等确认
- [x] 1.2 产出五项对齐结论并停下等待确认
- [x] 1.3 判定「需要建 change」并告知，等确认
- [x] 1.4 使用者确认「按推荐来」：先归档阶段 0 + 阶段 1 接入 SQLCipher
- [x] 1.5 归档阶段 0：delta 合并进 `specs/`（8 模块）、change 移入
  `archive/2026-11-12-scaffold-android-project/`、逐模块校验 Scenario

## 2. 写 change 四件套

- [x] 2.1 `proposal.md` —— 含原始场景与完整「对齐结论」节
- [x] 2.2 `design.md` —— 表结构、事务、加密、测试策略、回退顺序、否决方案表
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 `specs/ledger-transactions/spec.md` —— Delta
- [x] 2.5 `specs/money-precision/spec.md` —— Delta
- [x] 2.6 `specs/category-stats/spec.md` —— Delta
- [x] 2.7 `specs/backup-restore/spec.md` —— Delta
- [x] 2.8 `specs/privacy-security/spec.md` —— Delta
- [x] 2.9 `specs/build-and-ci/spec.md` —— Delta
- [ ] 2.10 推送 change 文件到远端并用 API 校验

## 3. Entity / 枚举 / Converter

- [x] 3.1 `LedgerTypes.kt`（AccountType / RecordType）
- [x] 3.2 `AccountEntity.kt` / `CategoryEntity.kt` / `RecordEntity.kt`
- [x] 3.3 `BalanceAdjustmentEntity.kt` / `TrashEntryEntity.kt`
- [x] 3.4 `Converters.kt` 与全部 `@ColumnInfo(name=...)` 显式列名
- [x] 3.5 索引与 `onDelete = RESTRICT` 外键

## 4. DAO / Database / 预置

- [x] 4.1 `AccountDao` / `CategoryDao` / `RecordDao`
- [x] 4.2 `BalanceAdjustmentDao` / `TrashDao`
- [x] 4.3 `RecordDao.aggregateAccountDeltas()` 的 SQL 聚合
- [x] 4.4 `AppDatabase`（version 1，`exportSchema=false`）
- [x] 4.5 `SeedData` + `SeedCallback`（两级中文分类、默认账户）
- [x] 4.6 `Migrations.kt` 显式落点（禁止破坏性迁移）

## 5. 加密与 DI

- [x] 5.1 `DatabaseKeyManager`（Android Keystore AES-256/GCM + 口令封装）
- [x] 5.2 `DatabaseModule`（`SupportOpenHelperFactory` + 各 DAO provider）
- [x] 5.3 `OfflineLedgerApp.onCreate()` 加载 `sqlcipher` native 库

## 6. 算法 / Repository / 备份

- [x] 6.1 `LedgerEngine`（唯一余额算法、净资产、统计口径）
- [x] 6.2 `CategoryTree`（两级 rootCategoryId 解析）
- [x] 6.3 `StatRules`（唯一 `SQL_STAT_RELEVANT`）
- [x] 6.4 `LedgerRepository`：`withTransaction` 写操作 + 末尾重算 + 自检审计
- [x] 6.5 `BackupPayload` / `BackupFile` DTO（不改阶段 0 信封头部）
- [x] 6.6 `BackupExporter` / `BackupImporter`（纯 JVM 编解码 + 事务化全量导入）

## 7. 单元测试与静态检查

- [x] 7.1 `LedgerEngineTest`：9 条账务路径
- [x] 7.2 `CategoryTreeTest`：一级合计 = 二级合计 = 总支出
- [x] 7.3 `StatRulesTest`：口径常量
- [x] 7.4 `BackupRoundTripTest`：导出 → 导入 → 余额一致
- [x] 7.5 `SeedDataTest`：预置数据结构
- [x] 7.6 `check_transaction_annotations.py`：写方法事务包裹、禁止破坏性迁移
- [x] 7.7 `check_stat_rules.py`：统计 SQL 关键片段
- [x] 7.8 本地五个检查脚本全部 PASS
- [x] 7.9 单次 commit 推送，Actions 列表确认该次运行确实触发
- [x] 7.10 首轮单测失败：非算法错误，是 `LedgerEngineTest.01` 净资产期望值
  写成 147000（应为 247000）。已修正并同步源脚本；诊断日志已回传、临时步骤已移除
- [x] 7.11 重跑 run 43211076566 结论 **success**（commit 534957790f）

## 8. 同步与收尾

- [x] 8.1 更新 `README.md` 阶段状态、目录结构、数据层说明与测试边界
- [x] 8.2 每次推送后均用远端 Git Trees API 逐文件比对 blob sha，PASS
- [x] 8.3 清理本地凭据文件
- [ ] 8.4 使用者确认后归档本 change（合并 delta 到 `openspec/specs/`）

> 仅 8.4（归档）未完成：需使用者明确指令，本轮不擅自归档。
> 归档时须把 6 份 delta 合并进 `openspec/specs/`，并把 change 移入 `archive/`。
