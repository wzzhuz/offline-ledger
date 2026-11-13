# 技术设计：add-multi-currency-basics

## 0. 正面回答使用者的问题：没有历史数据，数据库迁移是什么？

> 使用者原话：「我理解当前应用还处于开发阶段，没有历史数据，数据库迁移是什么。」

**先给结论：你判断得对，而且此前几轮我把风险说得过重了。**

### 迁移到底是什么

Room（SQLite）在建库时给数据库一个 `version`。
APK 升级后代码里的 `version` 变大了，Room 打开旧库时会：
① 比对代码声明的表结构与实际表结构；
② 若版本不同，**必须**有一份「从旧结构改到新结构」的 SQL 脚本，
   否则直接抛 `IllegalStateException`（App 打不开）；
③ 这份脚本就是 `Migration`，逐段登记在 `Migrations.ALL` 里。

本项目三段脚本实际做的事：

| 迁移 | 做了什么 | 引入阶段 |
|---|---|---|
| `1→2` | `CREATE TABLE record_templates` + `recurring_rules` | 6a |
| `2→3` | `CREATE TABLE budgets` + `records` / `trash` 各加 `linkedRecordId` | 6b |
| `3→4` | `records` / `trash` 各加 `currencyCode` | **6c-1** |

### 为什么「没有历史数据」这个判断很关键

**全新安装时 Room 走 `onCreate`，直接按最新 `version = 4` 建库，
一行 Migration 都不会执行。** 迁移只在「手机上已经装过旧版本、
又覆盖安装新版本」时才会跑。

而实际情况是：

- 使用者**至今一次都没装过这个 App**，所有 APK 只存在于 GitHub Actions 制品里
- 所以 `1→2`、`2→3` 两段脚本**从未在真机上运行过**，
  也从未作用于任何真实账本数据
- 若他直接下载 6c-1 的包安装 → 全新安装 → 零迁移
- 只有「先装了 6c-1，之后再装 6c-2」才会跑一段 `Migration(4→5)`
- 所谓「三次叠加迁移」，只在「从阶段 0 的 debug 包一路覆盖升级上来」
  才成立，而那条路径基本不会发生

**因此此前几轮写的「`Migration(1→2)` + `Migration(2→3)` 从 v1 覆盖升级
是本项目最大残余风险」措辞过重，本 change 如实修正**：
真实风险不是「升级会丢数据」，而是
**「这些脚本 + Room 的 schema 校验 + SQLCipher 打开数据库，
在真机上一次都没跑过」**——即「未验证」，不等于「高危」。
真机清单按这个口径重写（见第 9 节）。

### 那为什么还要写迁移、不删掉

1. **Room 强制**：只要 `version` 从 3 变 4 而不给 `Migration(3,4)`，
   覆盖安装必崩。写了才有 `ALL` 让 Room 依次执行
2. **一旦开始装机体验就会用到**：使用者明确「完成度更高再统一装机」，
   之后只要继续迭代并覆盖安装，迁移就是真实路径
3. **CI 与备份兼容是另一条独立防线**：`BackupSchema.MIN_SUPPORTED = 1`
   让 v1/v2/v3 备份仍可导入，这走的是 DTO 默认值兼容，与迁移不是一回事
4. **删掉反而是埋坑**：以后任何一次中间版本装机都会缺路径

**考虑过但否决**：既然 1/2/3 从未真机跑过，能否合并成一条 `Migration(1,4)`
或把 `version` 重置为 1？否决——Room 支持跳版本，但若使用者某天装过
中间版本，就要同时提供 `1→4` / `2→4` / `3→4` 多条，反而更乱；
逐段保留最稳，且成本只是几个常量与一段 ALTER SQL。

## 1. 现状（写码前实际读了什么）

| 已有 | 位置 | 结论 |
|---|---|---|
| `currencyCode` 在 `AccountEntity` / `BackupAccount` / `LedgerSnapshot` / `BackupEnvelope` | 多处 | **账户侧已预留** |
| `LedgerCurrency.DEFAULT = "HKD"` | `core/currency/` | 单币种常量，可直接复用 |
| `records` / `trash` | `data/local/entity/` | **无币种字段**（v2 建议只落实了一半） |
| `AppDatabase version = 3`、`ALL` 含两段 | `data/local/` | 6c-1 升 4、加第三段 |
| `BackupSchema.CURRENT = 3` / `MIN_SUPPORTED = 1` | `backup/dto/` | 升 4 |
| `RecordDao` 12 条收支聚合 + 4 条 SETTLEMENT | `dao/RecordDao.kt` | **一行不动**，新增 ForCurrency 组 |
| `StatRules.SQL_STAT_RELEVANT` | `engine/` | 一字不改 |
| `aggregateAccountDeltas()` | `RecordDao.kt` | **绝不能按币种过滤**（会算错余额） |
| `AssetsViewModel.netWorthCents` | `ui/assets/` | 改为按币种分组 |
| `StatsViewModel.reload` | `ui/stats/` | 改为走 ForCurrency 查询 |
| `check_stat_rules.py` 12 个 marker | `scripts/` | 扩到 22 个 |
| `check_transaction_annotations.py` 30 写方法 | `scripts/` | 扩到 `version=4` / 三段迁移 |

**v2 与实现的偏差如实记录**：v2 原文写「在 `accounts` 和 `records` 上
先把 `currencyCode` 字段留出来」，阶段 1 只在账户侧做了。
这不是 bug（v1 单币种，账户币种等价于流水币种），
但 6c-1 必须补流水侧，否则无法按币种拆报表。

## 2. 口径修正：明文导出不再被当成隐私违规

使用者本轮明确：

> 「导出明文是可接受的，数据掌控在自己手里就可以，
> 不可接受的是市场上 app 将数据保存在他们服务器上。」

**阶段 5 当时的决策**：v1 不做 CSV，理由写的是
「CSV 是明文导出，与『导出文件默认加密』张力最大」。
这个**结论保留**（6c-1 不做 CSV），但**理由被使用者正式推翻**。

若只留着不改，会发生什么：以后开 6c-2 做 CSV 时，
新会话读 specs 会看到「明文风险」「默认关闭」「导出前二次确认」
「明文文件勿放公共目录」，于是又拿一条使用者否定的理由去拦功能。
这正是本仓库反复吃过的「规格腐烂 / 规格债」。

所以 6c-1 做三件事：

1. **`project.md` 铁律 1** 改写：隐私边界 = 不上传第三方 / 不托管；
   本地导出明文不违规；JSON 已加密属已交付能力，MUST NOT 倒退
2. **2 份 MODIFIED**（按标题整块替换，不是追加）：
   - `backup-restore`「CSV 格式约定」：去掉「默认关闭 / 二次确认 /
     导出即明文」等以明文风险为前提的约束，去掉阶段号绑定，
     只保留格式约定与「新增导出格式前 MUST 显式更新闸门」
   - `privacy-security`「导出文件默认加密」：保留「JSON 备份 SHALL
     默认口令加密」「MUST NOT 在私有目录留明文备份」，
     删掉 CSV 条款与「v1 SHALL NOT」的阶段绑定，
     补一段明确的隐私边界陈述
3. **`reporting-ui`「v1 范围收敛」不改**：它描述 v1 历史范围，至今成立

**明确不做的**：不把已加密的 JSON 备份改成明文
（「明文可接受」≠「拆掉已有能力」）；不在 6c-1 加 CSV 入口
（结论保留，只是改为批次顺序）。

## 3. 严格不混算（A′）的语义

| 维度 | 规则 |
|---|---|
| 金额 | `amountCents` 仍是 `Long` 分；**币种只是标签，不参与任何加减** |
| 资产页 | 按 `currencyCode` 分组，每组列「资产合计 / 负债合计 / 小计」；**不显示单一净资产总数** |
| 统计页 | 顶部币种切换，默认选中主币种（第一个未归档账户的币种，或 HKD）；只统计所选币种 |
| 转账 | `fromAccount` / `toAccount` 币种 MUST 一致，否则拒绝并提示 |
| 余额重算 | `aggregateAccountDeltas()` **不过滤币种** —— 账户余额本就是账户自身币种，按币种过滤会算错 |
| 明细页 | 6c-1 保持全量并标注「全部币种」；与统计页口径差异作为 6c-2 一致性收尾（明确取舍，写进 tasks） |
| 汇率 | **不引入**。不存汇率表、不折算、不显示「折算后总资产」 |

**为什么不给一个「折算后净资产」总数**：汇率要有来源、日期、
历史版本，还要回答「报表按哪天折算」。个人账本给一个虚假精确的数字
比不给更糟。按币种分组列小计，语义诚实。

## 4. 数据层：两条 ALTER + 备份 v4

```kotlin
const val SQL_3_4_ALTER_RECORDS =
    "ALTER TABLE `records` ADD COLUMN `currencyCode` TEXT NOT NULL DEFAULT 'HKD'"
const val SQL_3_4_ALTER_TRASH =
    "ALTER TABLE `trash` ADD COLUMN `currencyCode` TEXT NOT NULL DEFAULT 'HKD'"
val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```

`DEFAULT 'HKD'` 是关键：SQLite `ADD COLUMN` 带 `NOT NULL` 必须给默认值，
否则旧行填不进去。Entity 侧 `currencyCode: String = LedgerCurrency.DEFAULT`
与列定义逐字一致（Room 的 `TableInfo` 校验会比对 defaultValue）。

`BackupRecord` 加 `currencyCode: String = "HKD"`，v1/v2/v3 JSON 缺键
自动补默认值。`BackupSchema.CURRENT = 4`、`MIN_SUPPORTED = 1` 不变；
信封四项头部一字不改。

## 5. 写入侧：币种从账户继承

| 入口 | 币种来源 |
|---|---|
| 记一笔 EXPENSE | `fromAccountId` 账户 |
| 记一笔 INCOME | `toAccountId` 账户 |
| 记一笔 TRANSFER | 两账户；**不一致则拒绝** |
| 模板 / 周期补记 | 模板里指定的账户（走同一套 `insertRecord` 路径） |
| SETTLEMENT 报销 | 原支出流水的 `currencyCode`（原样复制） |
| 覆盖导入 | DTO 里的 `currencyCode`（旧备份默认 HKD） |

为控制改动面，6c-1 **不在 UI 里让用户手选流水币种**——
流水币种永远等于账户币种，这是单账户单币种的自然语义，
也避免多一个必填项增加记账摩擦（铁律 3）。

`withResolvedRoot` 之后统一由一个 `withCurrency(...)` 补齐：
所有写路径共用，不新开写余额的分支。
`WRITE_METHODS` 不新增（仍 30 个），只是 `insertRecord` / `updateRecord` /
`recordFromTemplate` / `recordSettlement` / `importBackup` 内部补字段。

## 6. 统计查询：新增 ForCurrency 组，既有 12 条一行不动

```sql
-- 例：expenseByRootForCurrency
SELECT rootCategoryId AS categoryId, SUM(amountCents) AS totalCents
  FROM records
 WHERE type = 'EXPENSE' AND excludeFromStats = 0
   AND yearMonth = :ym
   AND currencyCode = :currencyCode
 GROUP BY rootCategoryId ORDER BY totalCents DESC
```

新增 10 条：`totalExpenseForCurrency` / `totalIncomeForCurrency` /
`expenseByRootForCurrency` / `expenseByCategoryForCurrency` /
`monthlyTrendForCurrency` / `rootTotalsForCurrency` /
`categoryTotalsForCurrency` / `settlementByRootForCurrency` /
`settlementByCategoryForCurrency` / `settlementTotalForCurrency` /
`drillDownRowsForCurrency` / `subcategoryTotalsUnderRootForCurrency`
（共 12 条，覆盖统计页所有聚合与下钻）。

每条 MUST 带 `excludeFromStats = 0`（唯一口径）与
`currencyCode = :currencyCode`。`check_stat_rules.py` marker 从 12 扩到 24。

**为什么不改原 12 条加可选参数**：那要同步改
StatsViewModel / DetailViewModel / AssetsViewModel / 对账 ViewModel 全部调用点，
且 `aggregateAccountDeltas` 绝不能加币种条件，混在一起容易误伤。
沿用 6b 验证过的「既有聚合 SQL 零改动 + 新增独立查询」思路，回归面最小。

## 7. UI 改动

| 页面 | 改动 |
|---|---|
| 资产页 | `AssetsUiState` 加 `groups: List<CurrencyGroup>`；顶部改为分组小计列表；**删掉单一净资产大数字**；账户列表仍按资产/负债分组，每行显示币种 |
| 统计页 | 顶部加币种切换 chips（来自活跃账户的去重币种列表），默认选中第一个；`reload()` 全程走 ForCurrency 查询；标注「当前币种：HKD · 不跨币种汇总」 |
| 记一笔 | 金额下方小字显示所选账户币种；跨币种转账保存时给出错误提示 |
| 明细页 | 顶部标注「全部币种」（6c-1 口径边界，6c-2 再收口） |

4 Tab 结构不变；不新增页面；不引入新依赖。

## 8. 测试策略

| 层 | 覆盖 |
|---|---|
| `CurrencyInheritTest` 纯 JVM | 支出/收入/转账/报销的币种继承、同币种校验、默认 HKD |
| `BackupV3CompatTest` 纯 JVM | v1 / v2 / v3 备份解析成默认 HKD、v4 往返带币种 |
| `MigrationContractTest` 扩 | `Migration(3,4)` 登记、两条 ALTER SQL 含 DEFAULT 'HKD' |
| `StatRulesTest` 不动 | 唯一口径常量保持 |
| 六个静态检查 | `version = 4`、三段迁移、24 个 marker、30 写方法、无 INTERNET |
| Actions | 单测 + debug + release lintVital |

## 9. 真机验证累积清单（使用者明确继续推迟，全部未验证）

沿用 6a 的决定、6b 再次确认、6c-1 继续沿用：
**先不做真机实测，等完成度更高再统一装机体验**。
以下各项**全部保持未验证**，不得在后续阶段被改写成「已通过」：

- Room 在真机打开加密数据库并执行 `onCreate`（**全新安装路径**）
- SQLCipher 在真机加载 `.so` 并打开数据库
- R8 release 包真机启动
- `Migration` 三段脚本在真机执行（**按当前用法首次装机不会触发**）
- BiometricPrompt 弹窗 / SAF 文件选择器
- 5 万条切月 / 年报聚合耗时、滚动掉帧
- 模板快选、周期补记、桌面快捷方式手感
- 报销入口、预算进度条、净支出环形图
- **6c-1 新增**：币种切换、分组小计、同币种转账提示

**装机前建议先用当前版本导出一份加密备份**（虽然目前没有历史数据，
但一旦开始录入就适用）。**风险口径已按第 0 节修正**：
是「未验证」，不是「升级会丢数据」。

## 考虑过的其他方案

| 方案 | 为什么没选 |
|---|---|
| 6c 四项全做 | 三张表 + 新权限 + 新依赖 + 明文导出一次摊开，且真机一次没验 |
| 引入汇率、给折算后净资产总数 | 汇率来源 / 日期 / 历史版本无底洞，虚假精确比不显示更糟 |
| 给既有 12 条 SQL 加可选 `:currencyCode` | 要同步改 4 个 ViewModel 调用点，`aggregateAccountDeltas` 易被误加条件算错余额 |
| 按币种过滤余额重算 | **直接算错账户余额**，绝对禁止 |
| 把 JSON 备份降级成明文 | 使用者说「明文可接受」≠「拆掉已有能力」；倒退无收益 |
| 保留「明文风险」论据不动 | 以后开 CSV 会被使用者否定的理由拦住，规格腐烂 |
| 合并成 `Migration(1,4)` 或重置 version=1 | 装过中间版本时要补多条跳版本迁移，反而更乱 |
| `fallbackToDestructiveMigration` | 永久不做 |
| 6c-1 顺手做 CSV | 结论「本批不做」保留，只是理由改为批次顺序 |
| 明细页也按币种筛选 | 会连带改 `pagedFiltered` 动态 SQL 与筛选面板，范围膨胀；明确留 6c-2 一致性收尾 |

## 取舍

选择「拆批 + 2 份 MODIFIED 修正论据 + 流水币种继承 +
独立 ForCurrency 查询 + 严格不混算 + 显式 Migration(3→4)」：

- **牺牲**：明细页与统计页 6c-1 期间口径不完全一致（已标注）；
  没有「折算后总资产」这一个总数；新增 12 条查询与 2 个 ALTER
- **换取**：既有 12 条聚合 SQL 与唯一口径常量零改动；余额算法零改动；
  零新增依赖 / 权限 / 网络行为；4 Tab 与既有交互零改动；
  **错误隐私论据被清掉**，以后开 CSV 不再被误拦

## 验证

- [x] 阶段 0 五项对齐结论齐全、分级判定已告知并等确认
- [x] 使用者确认「按推荐来」（A + A′ + A）并澄清隐私观
- [x] 阶段 6b 已归档，基线 162 条 Requirement / 264 个 Scenario
- [x] 闸门已拆批放行 6c-1；小组件 / 跨币种混算移永久不做
- [x] 铁律 1 与 2 份 MODIFIED 已修正明文论据
- [x] 写码前实际读了 `currencyCode` 分布、`Migrations.ALL`、
  `BackupSchema`、`RecordDao` 全部签名、两个检查脚本、两条待 MODIFIED 原文
- [x] delta 每条至少一个 GIVEN/WHEN/THEN + 「理由」，2 份 MODIFIED 写完整替换文本
- [ ] CI 全绿（写作时未完成）
- [ ] 推送后用远端 Git Trees API 逐文件比对 blob sha
- [ ] 使用者确认后归档本 change
