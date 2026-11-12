# Delta for 账务联动与一致性

## ADDED Requirements

### Requirement: 账本表结构与索引

系统 SHALL 建立 `accounts` / `categories` / `records` / `balance_adjustments` /
`trash` 五张表，`records` SHALL 冗余 `localDate` / `yearMonth` / `rootCategoryId`。
系统 SHALL 为 `localDate DESC,id`、`yearMonth,type`、
`rootCategoryId,categoryId,yearMonth`、`amountCents` 与账户外键建立索引。

#### Scenario: 5 万条切月查询走索引

- **GIVEN** `records` 含 5 万条流水
- **WHEN** 按月范围查询明细
- **THEN** 查询命中 `(localDate, id)` 索引
- **AND** 不对 `timestamp` 做函数运算

**理由**：行数不是瓶颈，全表扫描与函数运算导致索引失效才是。

---

### Requirement: 删除走 trash 表且主表无软删除标记

删除流水 SHALL 在事务内把记录从 `records` 移入 `trash`，
恢复 SHALL 从 `trash` 移回 `records` 并重算余额。
`records` 表 MUST NOT 包含 `isDeleted` 字段。

#### Scenario: 删除后统计不含该笔

- **GIVEN** 一笔 30 元支出被删除
- **WHEN** 生成月报
- **THEN** 该笔不出现在支出合计与分类占比中
- **AND** 无需任何 `WHERE isDeleted = 0` 条件

#### Scenario: 恢复重新联动余额

- **GIVEN** trash 中有一笔 30 元支出
- **WHEN** 用户恢复该笔
- **THEN** 该流水回到 `records`
- **AND** 对应账户余额重新扣减 3000 分

**理由**：主表永远只含有效流水，恢复天然走与 insert 相同的重算路径，
杜绝「只翻转标记却忘了联动余额」以及「漏写过滤条件让删掉的账回到年报」。

---

### Requirement: 写操作统一用 withTransaction 且末尾重算

Repository 的每个写方法 SHALL 使用 `androidx.room.withTransaction`，
并 SHALL 在事务末尾调用 `rebuildAllBalances()`。
系统 MUST NOT 依赖标在 Repository 普通方法上的 `@Transaction` 注解
（Room 只在 DAO 方法上生成事务代码）。

#### Scenario: 事务中途失败

- **GIVEN** 插入流水过程中抛出异常
- **WHEN** 事务回滚
- **THEN** 流水未落库、所有账户余额保持原值
- **AND** 不出现「流水已记但余额未变」

**理由**：写入后全量重算让 update / delete / restore 共用一条路径，
不必分别实现「回滚旧影响 + 应用新影响」两套容易写反的分支。

---

### Requirement: 余额由 SQL 聚合重建

`rebuildAllBalances()` SHALL 由一条 SQL 把流水压成每账户一行的净变动，
再叠加未撤销的校准 delta 与期初余额后写回 `balanceCents`。
系统 MUST NOT 把全部流水读入内存逐条累加。

#### Scenario: 篡改后自检修复

- **GIVEN** `accounts.balanceCents` 被直接改错
- **WHEN** 冷启动执行 `checkAndRepair()`
- **THEN** 自动重算使余额与流水聚合一致
- **AND** 写入一条审计日志记录偏差

**理由**：事务只保证单次操作原子；可重建的缓存才能覆盖导入、迁移、崩溃等长期一致性场景。

---

### Requirement: 显式迁移且 seed 覆盖升级场景

系统 SHALL 声明 `Migrations.ALL` 作为后续版本的显式迁移落点，
MUST NOT 使用 `fallbackToDestructiveMigration`。
新增预置数据时 SHALL 同时在 `migrate()` 中补齐，
MUST NOT 依赖 `RoomDatabase.Callback.onCreate` 重跑。

#### Scenario: 从 version 1 升级

- **GIVEN** 已存在 version 1 的账本数据库
- **WHEN** 应用升级到 version 2 并新增预置分类
- **THEN** 通过显式 `Migration(1, 2)` 补数据
- **AND** 既有流水与余额不受影响

**理由**：破坏性迁移会丢真实账本数据；`onCreate` 的 seed 只对全新库生效。
