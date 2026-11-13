# 账务联动与一致性 Specification

## Purpose

定义流水与账户余额的联动、事务边界、余额可重建性、统计口径与删除语义。目标是「任何时刻账本内部都自洽」。

---

## Requirements

### Requirement: 写操作与余额变更同处一个事务

流水的新增、编辑、删除、恢复 SHALL 与账户余额变更包裹在同一个 Room `@Transaction` 内。系统 MUST NOT 在事务外单独修改 `balance`。

#### Scenario: 事务中途失败

- **GIVEN** 插入一笔支出的过程中发生异常
- **WHEN** 事务回滚
- **THEN** 该流水未落库且账户余额未变
- **AND** 不出现「流水已记但余额未变」

**理由**：原子性是账本的最低要求。事务只保证单次操作原子，长期一致性由下一条的重建机制保证。

---

### Requirement: 余额是派生缓存而非真相源

唯一真相源 SHALL 为 `accounts.initialBalance` + 全部有效流水 + 全部 `BalanceAdjustment`。`accounts.balance` SHALL 明确降级为可重建缓存。

#### Scenario: 余额字段被篡改

- **GIVEN** 直接修改数据库使某账户 `balance` 偏离流水聚合值
- **WHEN** 冷启动触发账本自检
- **THEN** 系统检测到不一致并自动重算修复
- **AND** 写一条审计日志

#### Scenario: 用户手动校验

- **GIVEN** 用户在设置页点「校验并修复余额」
- **WHEN** 执行全量重算
- **THEN** 所有账户余额与流水聚合一致
- **AND** 展示校验结果

**理由**：只靠事务无法覆盖导入、迁移、崩溃、预置数据等场景。把余额设为缓存，等于给长期一致性加了自愈能力。

---

### Requirement: 余额可全量重算

系统 SHALL 提供唯一权威函数 `rebuildAllBalances()`，在事务内按流水对账户的 `+/- amount` 聚合、叠加校准 delta 与期初后写回 `balance`。冷启动、导入完成、任何写操作后 SHALL 调用重算或受影响账户重算。

#### Scenario: 导入后重算

- **GIVEN** 从备份全量恢复账本
- **WHEN** 导入事务完成后
- **THEN** `rebuildAllBalances()` 被执行
- **AND** 净资产与流水聚合结果一致

**理由**：一条聚合 SQL 即可完成，个人账本 5 万行在十毫秒量级，成本远低于维护双写正确性的风险。

---

### Requirement: 编辑流水先回滚旧影响

编辑流水 SHALL 消除旧记录对余额的全部影响后再应用新记录。实现 MAY 采用「写入新记录 → 全量重算」的等价简化路径。

#### Scenario: 修改金额

- **GIVEN** 把一笔 30 元支出改为 50 元
- **WHEN** 保存
- **THEN** 账户余额只再减少 20 元
- **AND** 不是减少 50 元也不是不变

#### Scenario: 修改账户与类型

- **GIVEN** 把一笔支出改为收入并换账户
- **WHEN** 保存
- **THEN** 原账户余额回滚、新账户按收入增加
- **AND** 月报分类与金额同步更新

**理由**：v1 规定「回滚旧 → 写新 → 应用新」三步，逻辑正确但分支多；直接重算更不易错，二者等价即可。

---

### Requirement: 回收站恢复重新走插入事务

恢复已删除流水 SHALL 重新执行插入事务并重算余额，MUST NOT 只翻转一个删除标记而不联动余额。

#### Scenario: 恢复支出

- **GIVEN** 回收站中恢复一笔 30 元支出
- **WHEN** 确认恢复
- **THEN** 该流水回到明细列表
- **AND** 对应账户余额重新扣减 3000 分

**理由**：「只有入口没有出口」是已记录过的真实坑：归档/删除必须配套反向操作，且反向操作要走同一套联动逻辑。

---

### Requirement: 有流水的账户与分类只能归档

系统 SHALL 对存在关联流水的账户与分类使用 `isArchived` 归档，MUST NOT 物理删除。外键 SHALL 使用 `onDelete = RESTRICT`。

#### Scenario: 删除已用账户

- **GIVEN** 用户尝试删除有 200 笔流水的微信零钱账户
- **WHEN** 提交删除
- **THEN** 系统只允许归档，账户与历史明细保持可查
- **AND** 不产生孤儿流水

**理由**：物理删除会留下孤儿流水，历史统计随之失真。归档的成本几乎为零。

---

### Requirement: 全局唯一统计口径

系统 SHALL 定义唯一统计口径 `isStatRelevant = type IN ('EXPENSE', 'INCOME') AND excludeFromStats = 0`，报表、导出、明细概览 MUST 共用该出口（实现为 `StatRules.SQL_STAT_RELEVANT`）。删除流水 SHALL 通过移入 `trash` 表生效，主表 MUST NOT 含 `isDeleted` 字段，因此口径 MUST NOT 包含 `isDeleted = 0`。`TRANSFER` 因 `type` 不符被排除；余额校准记录存放于独立的 `balance_adjustments` 表，根本不在 `records` 中，天然不计入收支统计。

#### Scenario: 年报不混入转账、校准与已删除流水

- **GIVEN** 账本含 1 笔转账、1 笔余额校准、1 笔不计入收支的垫付，另有 1 笔已移入 trash 的支出
- **WHEN** 生成年度报表
- **THEN** 四者均不出现在收入 / 支出 / 分类占比中
- **AND** 明细页与导出结果口径一致

#### Scenario: 主表无软删除列

- **GIVEN** 查看 `records` 表结构
- **WHEN** 检查字段列表
- **THEN** 不含 `isDeleted`
- **AND** 任何聚合查询都不需要 `WHERE isDeleted = 0`

**理由**：阶段 1 把软删除改为 trash 表后，旧的 `isDeleted = 0` 口径已无处可指；
同时校准改为独立表，`BALANCE_ADJUST` 也不再是 records 的一种 type。
若只追加新 Requirement 而不 MODIFIED 这一条，specs 会出现两条互相矛盾的要求，
正是 AGENTS.md「改完代码必须同步 spec」要防的规格腐烂。

---

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

### Requirement: 组合筛选走单条参数化查询

明细与核对视图 SHALL 由一条带可选参数的 `@Query` 覆盖时间 / 账户 / 类型 /
一级 / 二级分类 / 金额区间 / 备注全文的全部组合，
所有值 SHALL 通过参数绑定传入，MUST NOT 用字符串拼接 WHERE 值。
查询 SHALL 通过 `LEFT JOIN` 一次取回分类名、主题色与账户名。

#### Scenario: 组合筛选

- **GIVEN** 用户设置上月 + 微信零钱 + 一级「餐饮」+ 金额 20–100 元 + 备注含「午饭」
- **WHEN** 执行查询
- **THEN** 返回同时满足全部条件的流水
- **AND** 每行已带分类名与账户名，渲染时不再逐行查库

#### Scenario: 注入面

- **GIVEN** 备注搜索框输入 `' OR 1=1 --`
- **WHEN** 执行查询
- **THEN** 它被当作字面备注子串匹配
- **AND** 不返回全部流水

**理由**：动态组合筛选最容易写成拼 SQL；参数化绑定既零注入面，
又让一条 SQL 覆盖所有组合，避免 `@RawQuery` 或多份 `@Query` 的组合爆炸。

---

### Requirement: 分页查询不加载全表

明细列表 SHALL 由 `PagingSource<Int, RecordRow>` 以 `LIMIT :limit OFFSET :offset`
按 `localDate DESC, id DESC` 取页，MUST NOT 调用 `getAll()` 后在 Kotlin 里分组。
`nextKey` SHALL 按 `offset + rows.size` 推进，`getRefreshKey` SHALL 以
`anchorPosition` 为中心避免刷新跳回顶部。

#### Scenario: 5 万条首屏

- **GIVEN** `records` 含 5 万条流水
- **WHEN** 打开明细首页
- **THEN** 只查询首屏 60 条并命中 `(localDate, id)` 索引
- **AND** 内存占用不随总记录数线性增长

**理由**：行数不是瓶颈，几万对象 inflate 才是；分页键的推进方式写错
会在滚动时重复或漏掉记录。

### Requirement: 对账是唯一的校准写入路径

系统 SHALL 通过 `LedgerRepository.reconcile(...)` 生成校准，
MUST NOT 让 UI 层直接插入 `BalanceAdjustmentEntity` 或直接改 `balanceCents`。
`reconcile` SHALL 返回差额并 SHALL 在事务内完成插入、重算与锚点推进。

#### Scenario: UI 绕过 Repository

- **GIVEN** 有人试图在 ViewModel 里直接调 `adjustmentDao.insert`
- **WHEN** 静态检查或评审
- **THEN** 被判定为违规
- **AND** 必须改走 `reconcile`

**理由**：唯一写入口才能保证「写后重算」不被漏掉；
阶段 1 已经把 9 个写方法收敛到 Repository，阶段 3 新增 4 个也照此办理。

---

### Requirement: 账户管理写操作走事务且只归档不物理删

`createAccount` / `updateAccountMeta` / `archiveAccount` SHALL 包在
`withTransaction` 内；新增账户后 SHALL 调用 `rebuildAllBalances()` 兜底。
存在关联流水的账户 SHALL 只用 `isArchived` 归档，MUST NOT 物理删除。

#### Scenario: 新增账户

- **GIVEN** 新增一个期初 100.00 元的资产账户
- **WHEN** 保存
- **THEN** `balanceCents = 10000` 且净资产同步增加
- **AND** 不产生负余额或无主流水

#### Scenario: 归档有流水的账户

- **GIVEN** 某账户有 200 笔流水
- **WHEN** 用户点归档
- **THEN** 账户 `isArchived = 1`、历史明细仍可查
- **AND** 不产生孤儿流水

**理由**：物理删除会留下孤儿流水、历史统计失真；归档成本几乎为零。

---

### Requirement: 锚点不参与余额真相源

`rebuildAllBalances()` 的真相源 SHALL 保持为
`initialBalance + 流水净变动 + 未撤销校准 delta`，
`lastReconciledAt` / `lastReconciledBalanceCents` SHALL NOT 出现在余额计算中。
修改或清空锚点 MUST NOT 改变任何账户余额。

#### Scenario: 清空锚点

- **GIVEN** 某账户锚点为 2026-10-01、余额 −50000 分
- **WHEN** 把两个锚点字段置空
- **THEN** 余额与净资产完全不变
- **AND** 只有核对视图的默认范围退回「全部流水」

**理由**：锚点是「上次对到哪」的展示字段，若当期初会破坏
「任何时刻可全量重建」这条账本最不能动的铁律。

### Requirement: 新增统计查询复用唯一口径

阶段 4 新增的只读统计查询 SHALL 带
`type = 'EXPENSE' AND excludeFromStats = 0`（趋势查询为
`type IN ('EXPENSE','INCOME') AND excludeFromStats = 0`），
并 SHALL 被 `scripts/check_stat_rules.py` 覆盖。
系统 MUST NOT 在 UI 层手写裸 SQL 各自过滤。
新查询的函数名 MUST NOT 以已有统计查询名为前缀，
以免静态检查回溯 `@Query` 时指向错误的方法。

#### Scenario: 口径被改歪

- **GIVEN** 有人把 `subcategoryTotalsUnderRoot` 的 `excludeFromStats = 0` 删掉
- **WHEN** CI 运行静态检查
- **THEN** `check_stat_rules` 失败并使 job 失败
- **AND** 不产出一个「不计入收支的流水混进下钻图」的版本

#### Scenario: 下钻金额与一级扇区相等

- **GIVEN** 「餐饮」一级扇区金额 5000 分
- **WHEN** 展开该大类下二级构成
- **THEN** 二级各项金额合计为 5000 分
- **AND** 二者使用完全相同的过滤口径

**理由**：下钻是新增的唯一 SQL 出口，最容易顺手漏掉
`excludeFromStats`；命名前缀约束来自阶段 1 归档两次
「宽泛子串搜索自误报」换来的经验。
