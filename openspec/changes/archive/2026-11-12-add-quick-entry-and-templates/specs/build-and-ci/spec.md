# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: 写方法清单与迁移静态检查扩展

`check_transaction_annotations.py` 的 `WRITE_METHODS` SHALL 扩到 26 个，
覆盖阶段 6a 新增的 9 个写方法；并 SHALL 精确检查
`AppDatabase version = 2`、`Migrations.ALL` 含 `Migration(1, 2)`、
两条 CREATE TABLE SQL 常量含全部必要列名。
检查 MUST 先剥注释再匹配，MUST NOT 用宽泛子串。
六个静态检查 SHALL 在每次 push 时全部执行。

#### Scenario: 漏登记写方法

- **GIVEN** 有人新增一个不包在事务内的写方法
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并使 job 失败
- **AND** 不产出一个「看着能编译」的包

#### Scenario: 注释里提到破坏性迁移

- **GIVEN** `Migrations.kt` 注释解释「禁止破坏性迁移」
- **WHEN** 执行静态检查
- **THEN** 不因注释自误报
- **AND** 六个静态检查仍全部执行

**理由**：阶段 6a 首次引入 Migration，静态检查是沙盒里唯一能
拦住「忘登记 ALL / 忘加 addMigrations / SQL 列写漏」的手段；
本仓库已有 7 次「检查器自误报」教训，精确匹配是硬要求。

---

### Requirement: 阶段 6a 纯 JVM 单测覆盖迁移与排期

系统 SHALL 提供 `RecurringScheduleTest`、`BackupV1CompatTest`、
`MigrationContractTest` 三个纯 JVM 单测类，
分别覆盖月度 / 周度 / 日度排期与幂等与 catch-up 上限、
v1 备份解析成空列表与 v2 往返与过高版本拒绝、
以及两条 CREATE TABLE 常量列齐全。
`testDebugUnitTest` MUST 在 CI 中单独执行。

#### Scenario: 月末规则

- **GIVEN** 每月 31 日的规则
- **WHEN** 计算 2 月到期日
- **THEN** 返回 2 月最后一天（平年 28 / 闰年 29）
- **AND** 不抛 `DateTimeException`

#### Scenario: v1 备份兼容

- **GIVEN** 一段不含 `templates` / `recurringRules` 键的 v1 JSON
- **WHEN** 用 `BackupImporter.decode` 解析
- **THEN** 成功且两个列表为空
- **AND** 不要求调用方传额外参数

**理由**：CI 没有模拟器，仪器测试跑不了；纯 JVM 层是唯一能自动验证
「排期算对、旧备份能读、SQL 列齐全」的地方。
