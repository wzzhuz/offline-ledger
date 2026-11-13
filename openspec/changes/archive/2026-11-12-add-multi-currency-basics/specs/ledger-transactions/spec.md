# Delta for 流水与账务联动

## ADDED Requirements

### Requirement: 流水币种从账户继承且转账须同币种

`records` 与 `trash` SHALL 带 `currencyCode`。
EXPENSE SHALL 取 `fromAccountId` 账户币种，
INCOME SHALL 取 `toAccountId` 账户币种，
SETTLEMENT SHALL 复制原支出流水币种，
模板与周期补记 SHALL 走同一继承路径。
TRANSFER 的两个账户币种不一致时 MUST 被拒绝并提示，
且 MUST NOT 写入任何流水。

#### Scenario: 支出继承账户币种

- **GIVEN** 一个 CNY 微信账户与一笔该账户的支出
- **WHEN** 保存流水
- **THEN** 流水的 `currencyCode` 为 CNY
- **AND** 账户余额按 Long 分正常联动

#### Scenario: 跨币种转账被拒

- **GIVEN** HKD 账户向 CNY 账户转账
- **WHEN** 保存
- **THEN** 返回「转账双方币种必须一致」错误
- **AND** 数据库不新增流水、两账户余额不变

#### Scenario: 余额重算不按币种过滤

- **GIVEN** 账本含 HKD 与 CNY 两类流水
- **WHEN** 执行 `rebuildAllBalances()`
- **THEN** `aggregateAccountDeltas()` 汇总全部流水
- **AND** 每个账户余额与该账户全部流水一致

**理由**：账户余额本就是账户自身币种，按币种过滤重算会算错余额；
币种只在「展示与统计」层起隔离作用，不进余额算法。

---

### Requirement: 多币种字段通过显式迁移与备份兼容落地

`AppDatabase` SHALL 为 `version = 4`，
`Migrations.ALL` SHALL 同时含 `MIGRATION_1_2` / `MIGRATION_2_3` / `MIGRATION_3_4`。
`Migration(3,4)` SHALL 给 `records` 与 `trash` 各加一列
`currencyCode TEXT NOT NULL DEFAULT 'HKD'`。
系统 SHALL 保持 `BackupSchema.MIN_SUPPORTED = 1`，
v1 / v2 / v3 备份导入时旧流水 SHALL 默认 HKD。

#### Scenario: 导入 v2 备份

- **GIVEN** 一份不含 `currencyCode` 键的 v2 加密备份
- **WHEN** 用 6c-1 包导入
- **THEN** 解析成功、所有旧流水币种为 HKD
- **AND** 余额重算后各账户余额一致

#### Scenario: 漏登记第三段迁移

- **GIVEN** 有人把 `ALL` 改回只含两段
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并使 job 失败

**理由**：SQLite 给 `NOT NULL` 列做 `ADD COLUMN` 必须给默认值；
逐段保留迁移是为了任何中间版本装机都有路径，不是因为使用者有历史数据。
