# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: 迁移与统计静态检查覆盖第三段迁移和新查询

`check_transaction_annotations.py` SHALL 精确检查
`AppDatabase version = 4`、`Migrations.ALL` 含三段迁移、
两条 `SQL_3_4_ALTER_*` 常量含 `currencyCode` 与 `DEFAULT 'HKD'`，
检查 MUST 先剥注释再匹配。
`check_stat_rules.py` marker SHALL 覆盖全部 `...ForCurrency` 查询
并要求带唯一统计口径。
`WRITE_METHODS` SHALL 保持 30 个（6c-1 不新增写方法）。

#### Scenario: 漏登记 MIGRATION_3_4

- **GIVEN** `ALL` 只含两段迁移
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并使 job 失败

#### Scenario: ALTER 缺默认值

- **GIVEN** `SQL_3_4_ALTER_RECORDS` 写成 `NOT NULL` 但无 `DEFAULT 'HKD'`
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并指出该常量

**理由**：SQLite 对 `NOT NULL` 的 `ADD COLUMN` 强制要求默认值；
静态检查是沙盒里唯一能拦住「忘登记 ALL / 漏默认值」的手段。

---

### Requirement: 阶段 6c-1 单测覆盖币种继承与旧备份兼容

系统 SHALL 提供 `CurrencyInheritTest` 与 `BackupV3CompatTest`，
并 SHALL 扩展 `MigrationContractTest` 覆盖 `Migration(3,4)`。
`testDebugUnitTest` MUST 在 CI 中单独执行。

#### Scenario: 报销复制原流水币种

- **GIVEN** 一笔 CNY 支出
- **WHEN** 对它发起报销
- **THEN** SETTLEMENT 流水币种也是 CNY

#### Scenario: v1 备份默认 HKD

- **GIVEN** 一段不含 `currencyCode` 键的 v1 JSON
- **WHEN** 用 `BackupImporter.decode` 解析
- **THEN** 成功且所有流水币种为 HKD

**理由**：CI 无模拟器且使用者已推迟真机验证，
纯 JVM 层是唯一能自动验证「币种算对、旧备份能读、ALTER 列齐全」的地方。
