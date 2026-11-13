# Delta for 流水与账务联动

## ADDED Requirements

### Requirement: 模板与周期规则作为独立表落库

系统 SHALL 提供 `record_templates` 与 `recurring_rules` 两张独立表，
分别存「可直接记一笔的模板」与「周期规则」。两张表 SHALL NOT 声明
`@Index` 或 `@ForeignKey`，使 `Migration(1→2)` 只需两条 `CREATE TABLE`。
模板与规则 MUST 复用 `RecordEntity` 的字段语义（金额 Long 分、
`RecordType`、`rootCategoryId` 冗余、`excludeFromStats`），
MUST NOT 新增三级分类或新的 `RecordType`。

#### Scenario: 两张表结构落地

- **GIVEN** 全新安装或从 v1 升级
- **WHEN** 打开数据库
- **THEN** `record_templates` 与 `recurring_rules` 存在
- **AND** 原有 5 张表与全部索引保持不变

#### Scenario: 字段语义一致

- **GIVEN** 一条「餐饮·正餐 3500 分」模板
- **WHEN** 用它生成流水
- **THEN** `amountCents = 3500`、`rootCategoryId` 指向「餐饮」一级
- **AND** 与手工记一笔产生的 `RecordEntity` 结构一致

**理由**：两张表数据量最多几十行，不需要索引；刻意不声明索引与外键
是为了把 Room 运行时 schema 校验的不一致风险压到最低（见 design.md 第 2 节）。

---

### Requirement: 模板记一笔复用既有事务路径

`recordFromTemplate` SHALL 走 `withResolvedRoot` 解析分类，
SHALL 与 `insertRecord` 一样包在 `db.withTransaction` 内，
并在事务末尾调用 `rebuildAllBalancesInternal()`。
系统 MUST NOT 为模板另开一条写账户余额的路径。

#### Scenario: 从模板记一笔

- **GIVEN** 一条「午餐 35 元 / 餐饮·正餐 / 微信」模板
- **WHEN** 在记一笔点该模板
- **THEN** 生成一笔 `amountCents = 3500` 的支出流水
- **AND** 对应账户余额正确扣减、净资产与流水聚合一致

#### Scenario: 分类被归档

- **GIVEN** 模板引用的二级分类已被归档
- **WHEN** 从模板记账
- **THEN** 仍按原有 `rootCategoryId` 解析成功
- **AND** 不因归档产生孤儿流水

**理由**：账要算得准的唯一保证是「所有写余额走同一条路径」；
多开一条就会像早期方案那样出现流水已记但余额未变。

---

### Requirement: 周期补记幂等且不阻塞冷启动

`applyDueRules` SHALL 在单个 `db.withTransaction` 内批量插入到期流水、
末尾只做一次余额重算，SHALL 通过 `lastGeneratedLocalDate` 保证幂等，
并 SHALL 设 60 天 catch-up 上限。系统 MUST NOT 使用精确闹钟，
MUST NOT 引入 WorkManager 或任何含网络行为的依赖。

#### Scenario: 隔几天冷启动

- **GIVEN** 每月 1 日房租规则，上次生成 3 天前
- **WHEN** 冷启动
- **THEN** 补记到期的一笔，不重复、不漏
- **AND** `localDate` / `yearMonth` 按 Asia/Hong_Kong 正确

#### Scenario: 长期不打开

- **GIVEN** 用户三年没打开 App
- **WHEN** 冷启动
- **THEN** 最多补记 60 天范围内的流水
- **AND** 不生成上千条把启动卡死

#### Scenario: 整体回滚

- **GIVEN** 补记过程中某一笔插入失败
- **WHEN** 事务结束
- **THEN** 本次所有补记流水全部回滚
- **AND** 账户余额保持补记前的值

**理由**：冷启动补记对个人账本已足够；精确闹钟要 `SCHEDULE_EXACT_ALARM`
与零新增权限冲突，WorkManager 的后台行为沙盒验证不了。

---

### Requirement: Migration(1→2) 显式且无破坏性迁移

`AppDatabase` 版本 SHALL 升为 2，`Migrations.ALL` SHALL 含
`Migration(1, 2)`，`DatabaseModule` SHALL 调用 `addMigrations(*Migrations.ALL)`。
系统 MUST NOT 使用 `fallbackToDestructiveMigration`。
`SeedCallback.onCreate` 仍只对全新库生效；6a 不新增预置分类 / 账户，
故 `migrate()` SHALL NOT 补 seed 数据。

#### Scenario: 从 v1 覆盖升级

- **GIVEN** 设备上是 v1 的加密数据库
- **WHEN** 安装 v1.1 并打开
- **THEN** 执行 `Migration(1→2)` 建两张新表
- **AND** 原有账户 / 分类 / 流水 / 校准 / 回收站全部保留

#### Scenario: 全新安装

- **GIVEN** 全新设备
- **WHEN** 首次打开
- **THEN** `SeedCallback.onCreate` 仍预置原有分类与账户
- **AND** 两张新表为空、不预置模板

**理由**：真实账本绝不能破坏性迁移；`exportSchema` 保持 false，
因为 CI 无模拟器跑不了 MigrationTestHelper，导出 JSON 不产生验证价值。
