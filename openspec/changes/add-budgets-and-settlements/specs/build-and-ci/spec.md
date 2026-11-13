# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: 迁移静态检查覆盖两段 Migration 与 ALTER TABLE

`check_transaction_annotations.py` 的 `WRITE_METHODS` SHALL 扩到 30 个，
并 SHALL 精确检查 `AppDatabase version = 3`、
`Migrations.ALL` 同时含 `MIGRATION_1_2` 与 `MIGRATION_2_3`、
budgets 的 CREATE TABLE 常量列齐全、
ALTER TABLE 语句含 `linkedRecordId`。
`check_stat_rules.py` SHALL 把 3 条 SETTLEMENT 聚合查询纳入 marker 清单，
并要求其 `@Query` 带 `excludeFromStats = 0`。
检查 MUST 先剥注释再匹配，MUST NOT 用宽泛子串。

#### Scenario: 漏登记第二段迁移

- **GIVEN** 有人把 `ALL` 改回只含 `MIGRATION_1_2`
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并使 job 失败
- **AND** 不产出「看着能编译」的包

#### Scenario: 新统计查询漏口径

- **GIVEN** `settlementByRootInRange` 的 `@Query` 未带 `excludeFromStats = 0`
- **WHEN** 执行 `check_stat_rules.py`
- **THEN** 检查失败并指出该查询

**理由**：6b 首次 ALTER TABLE，静态检查是沙盒里唯一能拦住
「忘登记 ALL / 列名写漏 / 新查询漏口径」的手段；
本仓库已有 7 次「检查器自误报」教训，精确匹配是硬要求。

---

### Requirement: 阶段 6b 纯 JVM 单测覆盖净支出、预算与备份兼容

系统 SHALL 提供 `SettlementNetTest`、`BudgetEngineTest`、
`BackupV2CompatTest`，并 SHALL 扩展 `MigrationContractTest` 覆盖
`Migration(2→3)`。`testDebugUnitTest` MUST 在 CI 中单独执行。

#### Scenario: 净支出按 id 相减

- **GIVEN** 餐饮毛支出 5000 分、报销 2000 分
- **WHEN** 计算净切片
- **THEN** 餐饮净支出 3000 分
- **AND** 无分类出现负数

#### Scenario: 预算超支判定

- **GIVEN** 限额 100000 分、消耗 120000 分
- **WHEN** 计算 BudgetProgress
- **THEN** `isOver = true`、`overCents = 20000`、剩余为 0

#### Scenario: v2 备份兼容

- **GIVEN** 一段不含 `budgets` / `linkedRecordId` 键的 v2 JSON
- **WHEN** 用 `BackupImporter.decode` 解析
- **THEN** 成功、budgets 为空、linkedRecordId 为 null

**理由**：CI 没有模拟器且使用者已推迟真机验证，
纯 JVM 层是唯一能自动验证「净支出算对、预算判定对、
旧备份能读、ALTER TABLE 列齐全」的地方。
