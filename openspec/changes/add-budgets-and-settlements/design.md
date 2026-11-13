# 技术设计：add-budgets-and-settlements

## 现状（写码前实际读了什么）

| 已有能力 | 位置 | 阶段 |
|---|---|---|
| `RecordType { EXPENSE, INCOME, TRANSFER }` | `data/model/LedgerTypes.kt` | 1 |
| `StatRules.SQL_STAT_RELEVANT = type IN ('EXPENSE','INCOME') AND excludeFromStats = 0` | `engine/StatRules.kt` | 1 |
| `RecordDao` 6 条收支聚合 + 3 条阶段 4 下钻查询 | `data/local/dao/RecordDao.kt` | 1/4 |
| `aggregateAccountDeltas` 用 `from -amount / to +amount` 的 UNION ALL | `RecordDao.kt` | 1 |
| `AppDatabase version = 2`、`Migrations.ALL = arrayOf(MIGRATION_1_2)` | `data/local/` | 6a |
| `BackupSchema.CURRENT = 2` / `MIN_SUPPORTED = 1` | `backup/dto/BackupEnvelope.kt` | 6a |
| `BackupPayload` 7 个列表（含 6a 的 templates / recurringRules） | `backup/dto/` | 6a |
| `LedgerRepository` 26 个写方法、统一 `db.withTransaction` | `data/repository/` | 1–6a |
| `ChartData.checkTotals` / `sectors` / `fillDailySeries` 纯 JVM | `engine/ChartData.kt` | 4 |
| `StatsViewModel.reload` 组装 root/child slices 与 TotalsCheck | `ui/stats/` | 4 |
| `DetailScreen.RecordRowCard` 每行「编辑 / 删除」，已有 `AlertDialog` 模式 | `ui/detail/` | 2 |
| `DetailViewModel` 已持有 `repository` 与 `bumpRevision()` | `ui/detail/` | 2 |
| `SettingsScreen` 已有分类 / 模板 / 回收站 / 健康自检入口 | `ui/settings/` | 5/6a |
| `check_stat_rules.py` 9 个 marker；`check_transaction_annotations.py` 26 个写方法 | `scripts/` | 6a |

**关键差别**：6a 是两条 `CREATE TABLE`；**6b 是首次 `ALTER TABLE`**，
Room 打开数据库时的 `TableInfo` 校验会更严（新增列的类型 / notNull /
默认值都要与 Entity 逐字一致）。而且 `Migration(1→2)` 至今未在真机验过，
两次迁移叠加后的风险是累积的——这点必须写进交付说明，不能粉饰。

## 1. 闸门：只放行两项，把小组件推到 6c

`project.md` 的「v1.1 分批计划」表格：

| 项 | 原状态 | 6b 后 |
|---|---|---|
| 预算与超支提醒 | 6b 待决策 | **6b 本批实现** |
| 退款 / 报销成对模型 | 6b 待决策 | **6b 本批实现** |
| 小组件、通知栏磁贴 | 6b 待决策 | **移入 6c 待决策**，并写明原因 |
| 6c 三项 | 待决策 | 原样保留 |

**小组件为什么不做**：通知栏提醒在 Android 13+ 要 `POST_NOTIFICATIONS`
运行时权限；小组件要 Glance / RemoteViews 依赖与后台刷新。两者都与
「零新增权限、零新增依赖、零网络行为」基线冲突，且使用者已推迟真机验证，
做了也没法验手感。这不是「忘了做」，写进表里是为了下次不被当漏项。

`reporting-ui`「v1 范围收敛」这条 Requirement **不改**：它描述 v1 的
历史范围，在 v1.1 时代依然成立。6a 曾三次 MODIFIED 收口占位表述，
本轮不需要再动它。

## 2. SETTLEMENT：新增枚举值，但余额联动一行不改

```kotlin
enum class RecordType { EXPENSE, INCOME, TRANSFER, SETTLEMENT }
```

**刻意让 SETTLEMENT 复用既有余额算法**：生成时设
`fromAccountId = null`、`toAccountId = 原支出账户`，
`amountCents` 恒正。`aggregateAccountDeltas` 的 UNION ALL 会自动
把 `toAccountId += amount` 计入该账户，`LedgerEngine` 的
`deltasFrom` 同理。因此：

> **余额聚合 SQL、LedgerEngine、rebuildAllBalancesInternal 全部一行不改**，
> 净资产自然回到垫付前。

**统计口径也一字不改**：`SQL_STAT_RELEVANT` 只认 EXPENSE/INCOME，
SETTLEMENT 因 type 不符被天然排除 → 年度收入不虚增、
月/年趋势双线不受污染、收支概览保持原样。

**新增枚举值的连锁改动（必须逐一处理，否则穷尽 `when` 编译失败）**：

| 位置 | 处理 |
|---|---|
| `LedgerUiParts.amountColor` | 加 `SETTLEMENT -> IncomeGreen`（收回款正向） |
| `LedgerUiParts.signedAmount` | 加 `SETTLEMENT -> "+" + formatCents` |
| `RecordEditorViewModel.save` 的 `when (s.type)` | 加分支返回「报销/退款请从明细页发起」，SETTLEMENT 不走手工记一笔 |
| `TemplateManagerDialog` 的 `RecordType.entries` | 过滤掉 SETTLEMENT，模板/规则类型仍只有三类 |
| `StatRules` / `LedgerEngine.isStatRelevant` | 不需改（只认两个类型） |
| `CategoryTree.resolveRootId` | 不需改（只禁 TRANSFER 分类） |
| `RecordEditorDialog.TypeTabs` | 硬编码三 Tab，不需改 |

**`linkedRecordId`**：`RecordEntity` 加可空 `Long?` 列，
SETTLEMENT 指向原支出流水 id；普通流水为 null。
明细页据此可展示「已报销」关系。

## 3. 净支出：展示层减法，既有 6 条聚合 SQL 全不动

新增三条只读查询（都带 `excludeFromStats = 0`，
纳入 `check_stat_rules.py` marker 清单）：

```sql
-- settlementByRootInRange
SELECT rootCategoryId AS categoryId, SUM(amountCents) AS totalCents
  FROM records WHERE type = 'SETTLEMENT' AND excludeFromStats = 0
    AND yearMonth BETWEEN :from AND :to GROUP BY rootCategoryId

-- settlementByCategoryInRange
SELECT categoryId, SUM(amountCents) AS totalCents
  FROM records WHERE type = 'SETTLEMENT' AND excludeFromStats = 0
    AND yearMonth BETWEEN :from AND :to GROUP BY categoryId

-- settlementTotalInRange
SELECT COALESCE(SUM(amountCents), 0) FROM records
 WHERE type = 'SETTLEMENT' AND excludeFromStats = 0
   AND yearMonth BETWEEN :from AND :to
```

`ChartData.netSlices(expense, settlementMap)` 纯 JVM：按 id 对齐相减、
过滤 `<= 0`、保留原 name/colorHex。`StatsViewModel.reload` 里：

- `totalExpenseCents` **保持毛支出**（概览不回归）
- 新增 `settlementCents` / `netExpenseCents`
- root/child slices 都过 `netSlices` 后再进环形图
- `checkTotals(roots, children, netExpenseCents)` → 三者一致
- 概览加一行「已报销 / 退款」与「净支出」，并标注
  「分类占比与预算均按净支出计算」

**为什么不改 6 条聚合 SQL**：那条常量是全局唯一口径，
动它就要同步改统计页、明细概览、下钻、年度趋势、单测与静态检查，
回归面远大于加两条独立查询。展示层减法的成本是「毛支出与净支出
两个数都要展示」，换来的是零回归——这笔账划算。

## 4. 预算：规则长期生效，消耗全部走 SQL 聚合

`budgets` 表（**不声明索引 / 外键**，沿用 6a 教训）：

| 列 | 含义 |
|---|---|
| `id` | 主键自增 |
| `name` | 展示名 |
| `scope` | `TOTAL` / `ROOT` / `CATEGORY` |
| `categoryId` | ROOT 指向一级 id、CATEGORY 指向二级 id、TOTAL 为 null |
| `limitCents` | 每月限额，Long 分 |
| `sortOrder` / `isArchived` / `createdAt` | 排序、归档、创建时间 |

**规则不含 `yearMonth`**：一条「餐饮 1000 元」规则对每个自然月都生效，
省掉每月重建；切月时消耗按当前 range 实时聚合。
代价是历史月份无法单独调额度——个人自用够，
且 6c 若要做就得加字段，属于明确取舍。

`BudgetEngine`（纯 JVM）：

```
progress(limitCents, spentCents) -> BudgetProgress(
    limitCents, spentCents, remainingCents, overCents,
    usedRatio (仅绘图), isOver
)
```

消耗来源**全部是 SQL 聚合结果**，按 scope 取：

- `TOTAL` → `totalExpense(range) - settlementTotal(range)`
- `ROOT` → `rootTotalsInRange` 对应 id − `settlementByRootInRange` 对应 id
- `CATEGORY` → `categoryTotalsInRange` − `settlementByCategoryInRange`

**MUST NOT 用 `pagingItems` 求和**（分页只加载一屏，会随滚动跳变）。
超支只做统计页红色标记与进度条，**不发通知**。

## 5. Migration(2→3)：首次 ALTER TABLE

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(SQL_2_3_CREATE_BUDGETS)
        db.execSQL("ALTER TABLE `records` ADD COLUMN `linkedRecordId` INTEGER")
    }
}
val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
```

- `linkedRecordId: Long?` → `INTEGER`（可空、无 NOT NULL、无 DEFAULT），
  与 SQLite `ADD COLUMN` 生成的列定义完全一致
- budgets 表无索引/外键 → SQL 只需一条 `CREATE TABLE`，
  列顺序不影响 Room 按列名比较
- `AppDatabase version = 3`、`DatabaseModule.addMigrations(*Migrations.ALL)`
  自动覆盖两段迁移（Room 会依次执行 1→2、2→3）
- `exportSchema` 仍 `false`（CI 无模拟器，理由见 6a design）
- `SeedCallback.onCreate` 仍只对全新库生效，6b 不补 seed

**静态检查扩展**（`check_transaction_annotations.py`）：
`version = 3`、`ALL` 含 `MIGRATION_1_2` 与 `MIGRATION_2_3`、
两条 SQL 常量列齐全、ALTER TABLE 语句含 `linkedRecordId`。
沿用「先剥注释 + 精确匹配」规则，别宽泛搜子串
（本仓库已有 7 次检查器自误报教训）。

## 6. 备份：CURRENT=3 / MIN_SUPPORTED=1

| 改动 | 做法 |
|---|---|
| `BackupSchema` | `CURRENT = 3`、`MIN_SUPPORTED = 1` |
| `BackupEnvelope` | **四项头部一字不改** |
| `BackupCounts` | 加 `budgets: Int = 0` |
| `BackupPayload` | 加 `budgets: List<BackupBudget> = emptyList()` |
| `BackupRecord` | 加 `linkedRecordId: Long? = null` |
| `LedgerSnapshot` | 加 `budgets`，带默认值 |
| `BackupExporter` | 计入 counts |
| `exportSnapshot` / `importBackup` | 处理 budgets 表，末尾重算 |
| `BackupImporter` | **不改**，`MIN_SUPPORTED = 1` 放行 v1/v2 |

**v1 / v2 兼容**：`ignoreUnknownKeys = true` + 新增字段带默认值，
旧 JSON 缺 `budgets` / `linkedRecordId` 时解析成空列表 / null。
覆盖导入语义一致（旧备份会清空现有预算规则）。

## 7. Repository：4 个新写方法

| 方法 | 行为 |
|---|---|
| `createBudget(b)` | 校验名称非空、限额 > 0、scope 与 categoryId 一致 |
| `updateBudget(b)` | 同上 |
| `archiveBudget(id)` | 只归档不物理删 |
| `recordSettlement(originalId, amountCents, atEpochMilli)` | 同事务生成 SETTLEMENT 流水 |

`recordSettlement` 校验链：原记录存在 → `type == EXPENSE` →
累计已报销 + 本次 `<=` 原金额 → `toAccountId = 原记录.fromAccountId`
→ 沿用原 `categoryId` / `rootCategoryId` → `linkedRecordId = 原 id`
→ `withResolvedRoot` → `recordDao.insert` → `rebuildAllBalancesInternal()`。
**不新开写余额路径**，与 `insertRecord` / `recordFromTemplate` 完全同构。

`WRITE_METHODS` 从 26 扩到 30。

## 8. UI：三处落点，4 Tab 不变

| 落点 | 改动 |
|---|---|
| 明细页 | `RecordRowCard` 对 EXPENSE 加「报销」文字按钮 → `AlertDialog` 输入金额（默认剩余可报）→ `DetailViewModel.settle()` → `bumpRevision()` |
| 统计页 | `OverviewCard` 加「已报销 / 净支出」两行；`BudgetSection` 展示每条预算的限额 / 已用 / 进度条 / 超支标记 |
| 设置页 | 加「预算管理」按钮 → `BudgetManagerDialog`（列表 + 新增 + 归档） |

**实现期一处有意偏离**：对齐结论写「记一笔里报销/退款成对入口」，
实现放在**明细页每笔支出行**。理由：成对模型必须能看到并选中原流水，
明细页天然有这个上下文，而记一笔 Dialog 是「新建」语义，
硬塞一个「选历史流水」的列表反而多两步。偏离写在这里，不埋在注释里。

预算管理 Dialog 复用 `ledgerFactory` 手动工厂，不引入
`hilt-navigation-compose`；`BudgetManagerViewModel` 与其它页面同构。

## 9. 测试策略与如实说明的限制

| 层 | 验证方式 | 覆盖 |
|---|---|---|
| 净支出 | `SettlementNetTest` 纯 JVM | 按 id 相减、超额钳到 0、Map 空、负数不出现 |
| 预算 | `BudgetEngineTest` 纯 JVM | 未超 / 恰好 / 超支、零限额、剩余与比例 |
| 备份 | `BackupV2CompatTest` 纯 JVM | v1 / v2 JSON 解析成空 budgets 与 null linked、v3 往返带 budgets |
| 迁移 | `MigrationContractTest` 扩 | `CREATE TABLE budgets` 列齐全、ALTER TABLE 含 linkedRecordId、ALL 两段 |
| 静态检查 | 六个脚本 | 30 个写方法在事务内、`version = 3`、两段迁移、SQL 列对齐、9+3 个统计查询口径 |
| 构建 | Actions | 单测 + debug / release + lintVital |
| **真机** | **使用者已决定推迟** | 见下 |

### 真机验证累积清单（全部未验证，不得改写成「已通过」）

使用者在 6a 结束时决定：**先不做真机实测，继续下一阶段，
等完成度更高再统一装机体验**。6b 之后清单变成：

- **`Migration(1→2)` + `Migration(2→3)` 从 v1 覆盖升级后数据是否完好**
  （两次叠加，风险累积，**本项目最大残余风险**）
- R8 release 包真机能否正常启动
- SQLCipher 在真机打开加密数据库
- BiometricPrompt 弹窗 / SAF 文件选择器
- 5 万条切月 / 年报聚合耗时、滚动掉帧
- 模板快选、周期补记、桌面快捷方式手感
- **6b 新增**：报销/退款入口、预算进度条、净支出环形图手感

**装机前建议先用旧版导出一份加密备份**。CI 编译通过 ≠ 覆盖升级数据还在，
这一条在后续每个阶段的对齐结论里都要重新列出。

### CI 失败回退顺序（每次只改一处、单独推送）

1. 穷尽 `when` 因新枚举值报错 → 对照第 2 节连锁改动表逐点补齐
2. KSP 报 Entity / Migration 相关 → 核对 budgets 列名与 SQL 逐字一致，
   以及 `linkedRecordId` 的 `INTEGER` 无 NOT NULL
3. `RecordType.valueOf` 在旧数据上抛异常 → 不该发生（v1/v2 备份无 SETTLEMENT），
   但若出现，检查 Converters 是否仍按 name 存取
4. Compose 的 `LinearProgressIndicator` / `DropdownMenu` API 签名 →
   对照 `TemplateManagerDialog` 既有写法，不引入实验性 API
5. 静态检查自误报 → 精确匹配 + 先剥注释复核，不放宽成子串
6. 单测常数写错 → 先读目标 data class 全部字段，别凭印象构造（已 8 次教训）

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 绕过闸门直接写 | 少改文档 | 清单就是防范围蔓延的，破例一次以后失效 |
| 6c（多币种/CSV/账单导入）搭车 | 一次做完 | 明文导出与隐私铁律张力最大；报表混算另需汇率，风险不同量级 |
| 小组件 / 通知栏提醒一起做 | 功能更全 | `POST_NOTIFICATIONS` + 新依赖，且真机手感无法验证；移入 6c 并写明原因 |
| 退款记普通 `INCOME` + `excludeFromStats` | 零统计改动 | **分类支出仍显示垫付原额**，正是要解决的问题；只解决收入虚增 |
| 新增 SETTLEMENT 后改 6 条聚合 SQL | 口径更「统一」 | 要动唯一常量并同步 5 处调用方，回归面远大于加两条查询 |
| 负金额表达冲减 | 一行减法 | 违反 `amountCents` 恒正铁律，且会污染余额聚合 |
| 统计口径改成含 SETTLEMENT | 少一条查询 | 会把报销款算进收入概览，年度收入虚增 |
| 预算按 `yearMonth` 存每月一条 | 历史月可单独调 | 每月要重建 12 条规则，个人自用收益不抵复杂度 |
| 预算塞 SharedPreferences | 零迁移 | 进不了备份，换设备规则全丢 |
| `fallbackToDestructiveMigration` | 一行搞定 | 真实账本数据全丢，永久不做 |
| 用分页数据算预算消耗 | 少写 SQL | 分页只加载一屏，数值随滚动跳变，违反万级性能铁律 |

## 取舍

选择 **「闸门分批 + SETTLEMENT 成对 + 展示层净支出减法 +
展示层预算进度 + 显式 Migration(2→3)」**：

- **牺牲**：首个 ALTER TABLE 迁移的真机正确性未验证；
  新增 1 张表 / 1 个 DAO / 4 个写方法 / 3 条查询 / 2 个 Dialog；
  概览同时出现毛支出与净支出两个数，用户要理解差别；
  历史月份不能单独设预算
- **换取**：分类占比与预算反映真实花费；年度收入不再虚增；
  既有 6 条聚合 SQL 与唯一口径常量零改动；余额算法零改动；
  零新增依赖、零新增权限、零网络行为；4 Tab 与既有交互零改动

## 风险

**残余风险 1（最大）**：两次叠加迁移在真机覆盖升级时 schema 校验失败
或数据丢失。**缓解**：budgets 无索引/外键、SQL 只 CREATE TABLE +
ADD COLUMN、静态检查核对列名、`ALL` 显式登记；
**真机验证由使用者明确推迟**，装前建议导出加密备份。

**残余风险 2**：新增枚举值漏改某个穷尽 `when`。
**缓解**：第 2 节列了完整连锁改动表，回退顺序第 1 条专门兜这个。

**残余风险 3**：净支出出现负数（报销多于支出）。
**缓解**：`recordSettlement` 校验累计不超过原金额；
`ChartData.netSlices` 再钳一次到 0，双保险。

**残余风险 4**：预算规则指向已归档分类 → 消耗查不到。
**缓解**：按 id 聚合仍能查到历史流水（归档不改 `rootCategoryId`），
UI 显示「分类（已归档）」而非空白。

**残余风险 5**：静态检查脚本再次自误报。
**缓解**：精确匹配 + 先剥注释；FAIL 走第二条独立路径复核。

## 验证

- [x] 阶段 0 五项对齐结论齐全、分级判定已告知并等确认
- [x] 使用者确认「按推荐来」
- [x] 阶段 6a 已归档，基线 149 条 Requirement / 237 个 Scenario
- [x] 闸门已显式放行 6b 两项、小组件移 6c
- [x] 写码前实际读了 StatRules / ChartData / StatsViewModel /
  DetailScreen / DetailViewModel / RecordDao / Migrations / 两个检查脚本 /
  所有 `RecordType.` 引用点
- [x] 6 份 delta 共 13 条 ADDED，每条至少一个 GIVEN/WHEN/THEN + 「理由」
- [ ] CI 全绿（写作时未完成）
- [ ] 推送后用远端 Git Trees API 逐文件比对 blob sha
- [ ] 使用者确认后归档本 change
