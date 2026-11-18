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

**稳定版前的版本策略（条件性暂缓）**：
使用者至今未装机、稳定版前只会使用测试数据，不存在需要保留的旧库，
覆盖升级路径 MUST NOT 作为当前验收范围。
数据结构 SHALL 可按功能需要自由调整：
允许重置 `AppDatabase version`、不再逐段追加 `Migration`、
不保证旧备份向后兼容、`BackupSchema.CURRENT` / `MIN_SUPPORTED` 可随实现调整。
已落地的三段 Migration 与备份兼容代码 SHALL 保留但冻结，
MUST NOT 作为后续阶段的验收条件，
`check_transaction_annotations.py` 的迁移/版本检查 SHALL 降级为记录性提示。
`fallbackToDestructiveMigration` 禁令在稳定版前暂缓，
但源码中出现仍 SHALL 被静态检查判失败（视为误用）。
**任何阶段 MUST NOT 以「会破坏升级 / 要写 Migration / 旧备份读不了」
为由否决数据结构调整。**
**恢复条件**：使用者明确说明「需要考虑升级迁移」时，
MUST 重新启用显式 `Migration`、备份向后兼容、
`fallbackToDestructiveMigration` 禁令，
并把「从旧版覆盖升级数据完好」加回真机验证清单。

#### Scenario: 稳定版前调整数据结构

- **GIVEN** 某阶段需要给 Entity 加字段
- **WHEN** 评审以「要写 Migration / 旧备份读不了」为由否决
- **THEN** 评审依据 `project.md`「稳定版前的版本策略」驳回该否决
- **AND** 允许直接改 Entity 并重置 version

#### Scenario: 迁移门禁降级

- **GIVEN** 有人删掉 `MIGRATION_3_4`
- **WHEN** 跑 `check_transaction_annotations.py`
- **THEN** 只打印记录性提示，job 不因此失败
- **AND** 删掉某个写方法的事务包裹仍使 job 失败

#### Scenario: 使用者要求恢复升级支持

- **GIVEN** 使用者明确说明「需要考虑升级迁移」
- **WHEN** 下一阶段开工
- **THEN** 重新启用显式 Migration 与备份兼容
- **AND** 真机清单加回覆盖升级验证

**理由**：使用者在 6c-2 前明确「暂时不用考虑升级场景……
除非我说明需要考虑升级迁移」。原 Requirement 要求逐段显式迁移、
seed 覆盖升级场景，与当前阶段的实际约束相反；
若只追加新要求不 MODIFY 旧要求，specs 会同时存在两条矛盾约束。
本 MODIFIED 写清「条件性暂缓 + 恢复条件」，
MUST NOT 被写成「永远不做升级」；其余十几条历史 Requirement 保留不删，
由 `project.md` 暂缓执行清单统一声明优先级。

### Requirement: 组合筛选走单条参数化查询

明细与核对视图 SHALL 由一条带可选参数的 `@Query` 覆盖时间 / 账户 / 类型 /
一级 / 二级分类 / 金额区间 / 备注全文 / 交易对方的全部组合，
所有值 SHALL 通过参数绑定传入，MUST NOT 用字符串拼接 WHERE 值。
查询 SHALL 通过 `LEFT JOIN` 一次取回分类名、主题色与账户名。
交易对方 SHALL 以
`(:counterpartyQuery IS NULL OR COALESCE(r.counterparty, '') LIKE '%' || :counterpartyQuery || '%')`
短路实现，null 时行为 MUST NOT 改变。

从统计页下钻到明细 Tab 时，系统 SHALL 同时带入可执行的条件值（`rootCategoryId` /
`categoryId` / `counterpartyQuery` / `accountId`）与**按维度各自独立的三条只读展示名**
（`categoryDisplayName` / `counterpartyDisplayName` / `accountDisplayName`）；
三条展示名 MUST NOT 共用同一个字段（否则按收款方跳转时「分类」行会串味显示
收款方名），MUST NOT 参与 SQL、MUST NOT 计入 `activeCount()`（它们不是独立条件）。
三个下钻入口 SHALL 各自从全新条件对象起手，MUST NOT 沿用上一份条件快照。
二级分类下钻 SHALL 同时带入父级 `rootCategoryId`，否则筛选面板无法还原该条件；
筛选面板 SHALL 以一行「分类」显示具体名称（一级或「一级 / 二级」），并提供带
搜索的全量单选弹层，让已设的每一项都可见、可改、可清；一级项 SHALL 可选。
收款方下钻的筛选 chip 文案 SHALL 标明「包含」，MUST NOT 写成「等于」；
账户下钻 SHALL 带账户 id。明细页筛选 chip SHALL 始终显示「维度 + 具体值」，
MUST NOT 用维度名兜底成「分类筛选：分类」。
系统 SHALL 允许从一级分类扇区 / 图例直接下钻到明细 Tab 并带 `rootCategoryId`，
MUST NOT 把一级下钻堵在「只能跳二级」的弹窗里；下钻弹窗若出现，SHALL 同时提供
「查看该大类全部流水」入口。

#### Scenario: 组合筛选

- **GIVEN** 用户设置上月 + 微信零钱 + 一级「餐饮」+ 金额 20–100 元 + 备注含「午饭」
- **WHEN** 执行查询
- **THEN** 返回同时满足全部条件的流水
- **AND** 每行已带分类名与账户名，渲染时不再逐行查库

#### Scenario: 交易对方组合筛选

- **GIVEN** 用户输入交易对方「便利」并同时设置账户与金额区间
- **WHEN** 执行查询
- **THEN** 返回同时满足三项条件的流水
- **AND** 清空交易对方后行为与未设置时完全一致

#### Scenario: 注入面

- **GIVEN** 备注搜索框或交易对方输入框输入 `' OR 1=1 --`
- **WHEN** 执行查询
- **THEN** 它被当作字面子串匹配
- **AND** 不返回全部流水

#### Scenario: 统计页二级分类下钻后面板可还原

- **GIVEN** 统计页点二级分类「餐饮 / 外卖」跳到明细 Tab
- **WHEN** 打开组合筛选面板
- **THEN** 「分类」一行显示「餐饮 / 外卖」，已设项计数与 chip 一致
- **AND** 点开该行可在带搜索的全量列表里改选或清除，应用后列表按新条件重查

#### Scenario: 收款方 / 账户下钻

- **GIVEN** 排行卡切到「收款方」或「账户」后点某一行
- **WHEN** 跳到明细 Tab
- **THEN** 分别按包含匹配 / 账户 id 过滤，chip 显示具体名称，收款方标「包含」

#### Scenario: 收款方下钻不污染分类行（第 28 批）

- **GIVEN** 排行卡切到「收款方」点「盒马」跳到明细 Tab
- **WHEN** 打开组合筛选面板
- **THEN** 「分类」一行显示「全部分类」、不出现「盒马」
- **AND** 「交易对方」输入框为「盒马」，已设项计数与 chip 一致

#### Scenario: 饼图一级扇区下钻

- **GIVEN** 分类占比卡切到「按大类」并点某个一级扇区或对应图例
- **WHEN** 跳到明细 Tab
- **THEN** 按 `rootCategoryId` 过滤，命中该一级下全部分类（含未分二级）流水
- **AND** chip 显示该一级名称，筛选面板可在带搜索列表里改选或清除

**理由**：动态组合筛选最容易写成拼 SQL；参数化绑定既零注入面，
又让一条 SQL 覆盖所有组合，避免 `@RawQuery` 或多份 `@Query` 的组合爆炸。
第 11 批新增交易对方时，MUST 沿用同一条查询而不是另开一条。
第 26 批把「查看分类流水」从弹窗改到明细 Tab，但只带条件 id、没带展示名与
父级 id，导致 chip 显示「分类筛选：分类」、筛选面板看不到已选分类——
第 27 批按「下钻契约四层（条件值 / 展示名 / 面板维度 / 跳转入口）」一次补齐，
MUST NOT 只追加 ADDED 让 specs 出现两套条件清单。
第 28 批把单条展示名拆成三条独立字段并补一级扇区直接下钻；核查确认
`pagedFiltered` 早已接 `rootCategoryId`、筛选面板一级项早已可选、图例早已可点，
所以本批 MUST NOT 新增聚合 SQL 出口或复制分页列表。

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

### Requirement: SETTLEMENT 作为独立流水类型且余额联动复用既有算法

`RecordType` SHALL 增加 `SETTLEMENT`，`RecordEntity` SHALL 增加
可空 `linkedRecordId`。SETTLEMENT 金额 MUST 恒为正整数分，
生成时 `fromAccountId = null`、`toAccountId = 原支出账户`、
`linkedRecordId = 原流水 id`。
系统 MUST NOT 修改 `aggregateAccountDeltas`、`LedgerEngine`
或 `rebuildAllBalancesInternal()`。

#### Scenario: 报销后净资产回到垫付前

- **GIVEN** 微信支付一笔 38.50 元餐饮支出
- **WHEN** 对该笔发起全额报销
- **THEN** 生成一条 SETTLEMENT 流水，钱回到微信账户
- **AND** 净资产与垫付前完全相同

#### Scenario: 统计口径不被污染

- **GIVEN** 账本含一条 SETTLEMENT
- **WHEN** 查看年度收入与收支趋势
- **THEN** 年度收入不增加、趋势双线不含该笔
- **AND** `StatRules.SQL_STAT_RELEVANT` 保持原样

**理由**：`toAccountId += amount` 已经能表达「钱回来了」，
改余额算法会引入新分支；新增 type 天然被既有统计口径排除。

---

### Requirement: 报销入口只针对支出且累计不超过原金额

系统 SHALL 只在 `EXPENSE` 流水上提供「报销 / 退款」入口，
SHALL 校验「累计已报销 + 本次金额 <= 原支出金额」，
超额 MUST 被拒绝且账本不变。SETTLEMENT 生成 MUST 走
`withResolvedRoot` 与既有 `db.withTransaction` + 末尾重算。

#### Scenario: 部分报销

- **GIVEN** 一笔 100 元支出
- **WHEN** 先报 30 元再报 70 元
- **THEN** 生成两条 SETTLEMENT，账户余额累计回补 100 元

#### Scenario: 超额被拒

- **GIVEN** 一笔 100 元支出且已全额报销
- **WHEN** 再次发起 1 元报销
- **THEN** 返回错误、不写入任何流水
- **AND** 账户余额不变

#### Scenario: 对收入或转账发起

- **GIVEN** 一条收入流水
- **WHEN** 查看明细行
- **THEN** 不显示报销入口

**理由**：负金额冲减违反金额铁律；累计上限是防止误操作
把账户余额做成虚高的最后一道闸。

---

### Requirement: linkedRecordId 支持明细展示成对关系

SETTLEMENT 流水 SHALL 保留对原支出的关联，
明细页 SHALL 能据此识别「已报销」关系，
且归档 / 删除原分类 MUST NOT 改写历史流水的 `rootCategoryId`。

#### Scenario: 查看关联

- **GIVEN** 一笔支出及其 SETTLEMENT
- **WHEN** 打开明细
- **THEN** 两条流水都可见，SETTLEMENT 显示为收回款
- **AND** 分类名仍能解析（即使分类已归档）

**理由**：报销不是凭空出现的一笔收入，
要能回答「这笔钱是冲哪笔垫付的」。

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

### Requirement: 导入流水复用既有写路径且不新增去重字段

`LedgerRepository.importRecords` SHALL 登记进 `WRITE_METHODS`
并 SHALL 包裹在 `db.withTransaction` 内。
每行 SHALL 经 `withResolvedRoot`（含一级分类解析与 `withCurrency` 币种继承）后
由 `recordDao.insert` 写入，事务末尾 MUST 调用 `rebuildAllBalancesInternal()`。
系统 MUST NOT 为导入新开一条写余额的分支。
去重 SHALL 按「账户 + `amountCents` + `localDate` + `note`」
走参数化 `SELECT COUNT(*)` 查询，MUST NOT 为此新增字段或表。

#### Scenario: 导入 8 条合法流水

- **GIVEN** 8 行通过全量校验的 CSV
- **WHEN** 确认导入
- **THEN** 8 条流水走同一事务、末尾一次重算
- **AND** 每条币种等于其账户币种、一级分类正确

#### Scenario: 事务中途抛异常

- **GIVEN** 批量写入第 5 行时解析失败
- **WHEN** 事务结束
- **THEN** 前 4 行也不落库
- **AND** 账户余额与导入前完全一致

**理由**：复用 `withResolvedRoot` 是避免「导入流水币种错 / 一级分类空 /
余额没重算」三件套的最稳做法；去重不加字段，
正好验证稳定版前不再为升级背负担。

### Requirement: 收款方是流水级自由文本标签

`records` 与 `trash` SHALL 各有 `counterparty TEXT NOT NULL DEFAULT ''` 列，
模板与周期规则 SHALL 能把收款方带入生成的流水。收款方 MUST NOT 建成账户、
MUST NOT 有外键、MUST NOT 产生任何复式分录或余额联动。
删除后从回收站恢复 SHALL 保留收款方。

#### Scenario: 删除恢复不丢

- **GIVEN** 一条带收款方「盒马」的支出
- **WHEN** 删除再从回收站恢复
- **THEN** 恢复后的流水收款方仍为「盒马」
- **AND** 余额重算结果与删除前一致

#### Scenario: 模板带入

- **GIVEN** 模板设了收款方「房租中介」
- **WHEN** 从模板记一笔
- **THEN** 生成的流水带同一收款方

**理由**：使用者明确「只是不用那么严格，也不需要搞成账户」；
一个带默认值的文本列即可满足核对，不引入第二套账务模型。

---

### Requirement: 账户编辑改期初余额后必须重算

`updateAccountMeta` SHALL 在同一 `db.withTransaction` 内更新账户元数据，
改动 `initialBalanceCents` 时 SHALL 在事务末尾调用 `rebuildAllBalancesInternal()`。
系统 MUST NOT 让 `balanceCents` 缓存与「期初 + 流水 + 校准」真相源漂移。

#### Scenario: 期初余额生效

- **GIVEN** 账户期初 0、无流水、余额 0
- **WHEN** 编辑期初为 100000 分
- **THEN** `balanceCents` 变 100000 分
- **AND** 资产页分组小计同步变化

**理由**：`balanceCents` 是可重建缓存，任何改真相源的写路径都必须触发重算。

---

### Requirement: 稳定版前重置 Room 版本且不注册迁移

稳定版前 `AppDatabase version` SHALL 为 `1`，`Migrations.ALL` SHALL 为空数组，
系统 MUST NOT 注册任何 `Migration`。迁移 SQL 常量 MAY 保留供测试与历史追溯，
但 MUST NOT 被 Room 在运行时执行。

#### Scenario: 全新安装

- **GIVEN** 设备未装过本应用
- **WHEN** 首次打开
- **THEN** 走 `onCreate` 按 Entity 建库，含 `counterparty` 列
- **AND** 不执行任何迁移 SQL

#### Scenario: 覆盖安装需卸载

- **GIVEN** 设备已装 version 4 的包
- **WHEN** 直接覆盖安装本版本
- **THEN** 明确要求先卸载再用 CSV / 加密备份带走测试数据
- **AND** 文档不得声称可直接升级

**理由**：使用者明确「稳定版前只会用测试数据，数据结构完全不需要考虑升级迁移」。
保留三段迁移反而会让「加字段」每次都要写脚本，与当前阶段目标冲突。

---

### Requirement: 账户类型可改与自定义排序

系统 SHALL 允许修正账户类型（资产 ↔ 信用负债）并重算余额；
SHALL 允许无流水账户物理删除、有流水账户 MUST 改为归档；
SHALL 提供已归档账户查看与恢复入口；
账户排序展示 SHALL 按 `AccountType` 分组，**第 20 批修订**：SHALL 与分类一级 /
模板共用 `sh.calvin.reorderable:reorderable:3.1.0`（`ReorderableItem` +
`longPressDraggableHandle` + `rememberKeyedReorder` 适配层），资产页容器 SHALL 为
`LazyColumn`；库 SHALL 负责拖动中让位补间与边缘按距离变速滚动，
MUST NOT 出现「上下拖动同时滚屏导致拖拽被释放」，也 MUST NOT 再自研
「拖拽期间禁用父滚动 / 实测行高阈值」。松手 SHALL 经 `commitAccountOrderAll`
一次性写入 `sortOrder`；上移 / 下移 SHALL 保留在「⋯」菜单内作为无障碍兜底，
MUST NOT 作为主入口；顺序 SHALL 同步到记一笔的常用账户平铺。
所有新增写操作 MUST 包在同一个数据库事务内并在末尾重算余额。
资产页账户行 SHALL 与明细页同为纯行（16dp 内边距 + 56dp 行高 + `ListDivider`），
MUST NOT 用 `Card` 底色 / 阴影或 1dp 间距表达边界；
行内详情 / 对账 / 编辑 / 归档 / 删除等可点操作 SHALL 用 `bodyMedium`
且触摸区 MUST ≥40dp，MUST NOT 用 `labelSmall` 裸 `Text` 表达可点击项。

**理由**：使用者反馈「资产页账号列表因为底色看起来视觉效果没有明细页大」
「副标题是需要可以点击的，现在感觉字体偏小」「资产页排序还是上移、下移」；
第 16 批按标题整块 MODIFIED，避免 specs 同时存在「上移 / 下移排序」与
「统一拖拽排序」两条相反要求。

#### Scenario: 类型选错后修正
- **GIVEN** 一个被误建为「资产」的信用卡账户
- **WHEN** 编辑中改为「信用负债」并保存
- **THEN** 账户出现在信用负债分组，余额重算后数值一致

#### Scenario: 有流水账户禁止删除
- **GIVEN** 某账户已关联流水
- **WHEN** 用户点「删除」
- **THEN** 系统拒绝并提示改用归档，历史流水保持完整

#### Scenario: 组内拖拽实时让位
- **GIVEN** 「资产账户」组内有 6 个账户
- **WHEN** 长按第 1 个账户手柄向下拖过第 2 个
- **THEN** 拖动过程中其余账户立即让位，负债组顺序不变
- **AND** 松手后重进资产页顺序保持

---

### Requirement: 模板与分类的删除恢复
系统 SHALL 允许删除模板（草稿，不影响已生成流水）；
删除分类时 MUST 先检查关联流水，有流水 SHALL 自动降级为归档，
无流水 SHALL 才物理删除；已归档分类 SHALL 可恢复。

**理由**：使用者反馈「模板没有编辑和删除能力」「分类管理中没有删除功能」。

#### Scenario: 分类有流水
- **GIVEN** 分类「餐饮」下已有流水
- **WHEN** 用户删除该分类
- **THEN** 分类被归档而非删除，历史流水的分类归属不变

---

### Requirement: 账户类型支持四类且净资产不按类型分支

`AccountType` SHALL 包含 `ASSET` / `LIABILITY` / `PREPAID` / `INVESTMENT`，
分别对应资产账户、信用负债、充值储值卡、投资理财账户；资产页 SHALL 按类型分组展示。
净资产 SHALL 只按 `includeInTotal` 汇总余额，MUST NOT 按类型分支计算；
只有 `LIABILITY` SHALL 触发信用额度与负债展示。
枚举 SHALL 以名称字符串落库，新增类型 MUST NOT 需要改列定义或备份格式。

#### Scenario: 新增储值卡
- **GIVEN** 用户新增「公交卡」为 `PREPAID`、期初余额 100 元
- **WHEN** 打开资产页
- **THEN** 它出现在「充值 / 储值卡」分组，并按 `includeInTotal` 计入净资产

#### Scenario: 类型切换不算错钱
- **GIVEN** 一个 `ASSET` 账户有 3 条流水
- **WHEN** 用户把它改成 `INVESTMENT`
- **THEN** 余额按流水重算结果不变，净资产变化仅来自 `includeInTotal` 开关

**理由**：使用者反馈「账号类型除了资产账户、负债账户，是否再加充值账户、
投资理财账户」，公交卡 / 加油卡 / 股票基金是主流场景；钱迹也内置储值卡与投资账户。
边界是 v1 不做行情、净值与汇率，避免开无底洞。

---

### Requirement: 模板字段自由且顺序可调

模板 SHALL 是预填草稿：金额、分类、账户、备注、交易对方全部可选（仅名称必填），
生成流水时由默认账户兜底。周期规则会自动写入流水，其账户字段 SHALL 仍必填。
模板管理 **第 20 批修订**：SHALL 与分类一级 / 账户共用
`sh.calvin.reorderable:reorderable:3.1.0` 与 `rememberKeyedReorder` 适配层，
长按手柄拖动过程中由库 `animateItem()` 让位补间、边缘按距离变速滚动，
MUST NOT 再自研「禁用用户滚动 / 实测行高阈值」；松手一次性写入 `sortOrder`；
上移 / 下移 SHALL 保留在 ⋯ 菜单内作为无障碍兜底，MUST NOT 作为主入口。顺序 SHALL 复用到记一笔的常用模板平铺；
排序 MUST 走既有 `reorderTemplates` 单事务，MUST NOT 新开写余额路径。

#### Scenario: 支出模板不填转出账户
- **GIVEN** 新建支出模板「午餐」，只选了分类，账户留空
- **WHEN** 保存模板
- **THEN** 保存成功，不出现「请选择转出账户」

#### Scenario: 模板拖拽生效
- **GIVEN** 5 个模板，按 sortOrder 排列
- **WHEN** 长按第 5 个模板手柄向上拖过两个模板后松手
- **THEN** 它升到第 3 位，拖动过程中其余模板实时让位
- **AND** 记一笔常用模板前 6 个随之变化

**理由**：使用者反馈「模板页的排序还是上移、下移，考虑整改 app 的排序方案，
风格尽量统一」；按标题整块 MODIFIED，不留矛盾文本。

### Requirement: Firefly III 复式转单式映射

导入 Firefly III 导出时，系统 SHALL 把复式流水收敛成本项目单式流水，
MUST NOT 把外部对方账户建成账户、MUST NOT 引入复式分录。
映射 SHALL 为：Withdrawal → EXPENSE（账户取 source、收款方取 destination）；
Deposit → INCOME（账户取 destination、付款方取 source）；
Transfer → TRANSFER（source 与 destination 都映射成自有账户、交易对方留空）；
Opening balance → 不生成流水，转成账户期初余额且保留原符号；
Reconciliation 与未知类型 → 跳过并在报告列出。
流水金额 SHALL 一律取绝对值并转成 `Long` 分，恒正；
期初余额 SHALL 保留原符号（信用负债期初可为负）。
时间 SHALL 由 ISO 8601 带偏移解析成 epoch millis，
再经 `LedgerClock` 派生 `localDate` 与 `yearMonth`。
拆分交易 SHALL 按导出的每一行独立成一条流水，
`group_title` 写进备注作为分组线索，MUST NOT 把多行合并成一笔。
转账 SHALL 不设分类，原 FF3 分类名写进备注，避免污染统计口径唯一出口。

#### Scenario: 支出带收款方

- **GIVEN** 一行 `Withdrawal`，source = 交通银行信用卡2203，
  destination = 盒马，amount = -112.59，category = 活-购物
- **WHEN** 按映射生成流水
- **THEN** 得到一条 EXPENSE，金额 11259 分，
  账户 = 交通银行信用卡2203，收款方 = 盒马，
  一级分类 = 活、二级分类 = 购物

#### Scenario: 拆分交易不合并

- **GIVEN** 同一 `group_id` 下两行：鞋 124.00 与袜子 39.90，各有独立 `journal_id`
- **WHEN** 生成流水
- **THEN** 得到两条独立流水，金额分别 12400 分与 3990 分
- **AND** 两条备注都含同一个 `group_title` 分组名

**理由**：6.6.6 的真实导出每个 split 独立一行，合并会丢金额与分类明细；
本项目本来就是「一笔流水一个分类」，按行落库才是保真映射。

---

### Requirement: 导入来源唯一标记
导入流水的来源 MUST 完全由 `importSource` + `sourceId` 字段承载，MUST NOT 写入
备注；备注 MUST 逐字保留来源文件的原文（Firefly III 为 `notes` 列）；流水详情
MUST 显示来源信息；无对应字段的元数据（description / tags / budget / 分组）
MUST NOT 拼接进备注，且 MUST 在导入结果中明示未迁移。

#### Scenario: Firefly III 导入不改备注
- **WHEN** 导入含 `notes` 列的 Firefly III 导出
- **THEN** 落库备注与 `notes` 原文一致，不含 `ff3:` 前缀或「分组：」等拼接文本

#### Scenario: 详情看来源
- **WHEN** 使用者点开一条导入流水
- **THEN** 详情显示「来源：firefly-iii · journal xxx」，去重仍走唯一索引

## 归档合入：polish-stats-and-import-metadata

### Requirement: 分类显示名单一出口
分类显示名 MUST 由单一纯 JVM 出口构造：二级分类 MUST 显示为
「一级 / 二级」，一级分类 MUST 保持不含父级前缀的裸名。占比图例、占比扇区
下钻、筛选面板与明细筛选 chip MUST 全部经该出口渲染，MUST NOT 各自拼接
分隔符；只用于展示，MUST NOT 参与 SQL、MUST NOT 计入 `activeCount()`。

#### Scenario: 同名二级分类可分辨
- **WHEN** 统计页切「按小类」且两个不同一级下均存在名为「其他」的二级分类
- **THEN** 图例分别显示「餐饮 / 其他」与「交通 / 其他」，扇区与图例点击下钻
  后明细筛选 chip 显示同样的带父级全名

#### Scenario: 统计跳转与手工选一致
- **WHEN** 分别由统计页二级下钻、筛选面板手工选、明细 chip 兜底反查三种
  入口渲染同一个二级分类
- **THEN** 三者输出完全相同；一级下钻与手工选一级时均只显示一级名
