# 技术设计：implement-assets-and-reconciliation

## 现状（关键：本阶段几乎不需要动数据层结构）

阶段 1 已把阶段 3 需要的字段全部预留好了：

| 已有能力 | 位置 |
|---|---|
| `lastReconciledAt` / `lastReconciledBalanceCents` | `AccountEntity` 第 27–28 行 |
| 同名两个字段 | `BackupAccount` 第 13–14 行 |
| 双向映射（含锚点） | `LedgerMappers.toDto` / `toEntity` 第 36–37、45–46 行 |
| 独立校准表与撤销 | `BalanceAdjustmentEntity` / `BalanceAdjustmentDao.revoke` |
| `revokeAdjustment` 事务 | `LedgerRepository` |
| `rebuildAllBalancesInternal()` | `LedgerRepository` 唯一余额写入路径 |
| `observeNetWorth()` | `AccountDao` 已是 `SUM(balanceCents WHERE includeInTotal = 1)` |
| 统计口径唯一出口 | `StatRules.SQL_STAT_RELEVANT` |
| 分页 / 筛选 / 按日合计 SQL | 阶段 2 `RecordDao` |

**结论**：`AppDatabase` 保持 `version = 1`，**本阶段不写 Migration、不改备份格式、
不改 `schemaVersion`、不新增任何 Gradle 依赖**。风险比阶段 2 更低。
这也印证了 v2 规格书里「v1 不做多币种也要先留 `currencyCode` 字段」的判断：
预留字段的价值在后续阶段兑现。

## 1. 纯 JVM 对账引擎 `engine/Reconciliation.kt`

把「算差额 / 判尾差 / 展示聚合值 / 金额反查区间 / 疑似标记位」收敛到一个纯 JVM
object，好处是全部可单测（沙盒无模拟器也能跑），UI 与 DAO 都不各自心算。

```kotlin
const val TAIL_DIFFERENCE_THRESHOLD_CENTS = 100L      // 1.00 元
const val APPROX_TOLERANCE_CENTS = 100L              // 近似反查 ±1.00 元
const val LARGE_AMOUNT_THRESHOLD_CENTS = 100_000L    // 1000.00 元，疑似异常大额
const val DUPLICATE_WINDOW_MILLIS = 86_400_000L      // 24 小时

difference(book, actual) = actual - book
isTailDifference(delta) = |delta| <= 阈值          // 边界含等号
availableCredit(limit, balance) = limit + balance  // balance 为负，别再减一次
```

**展示值独立计算**，不是用减法倒推净资产：

```kotlin
data class AccountTotalsInput(balanceCents, isLiability, includeInTotal)
data class DisplayTotals(netWorthCents, totalAssetsCents, totalLiabilitiesCents)

netWorth       = SUM(balance WHERE includeInTotal)                // 唯一计算式
totalAssets    = SUM(balance WHERE includeInTotal AND !isLiability)
totalLiabilities = ABS(SUM(balance WHERE includeInTotal AND isLiability))  // 仅展示
```

单测里特意构造一个 `includeInTotal = false` 的账户，此时
`netWorth ≠ totalAssets − totalLiabilities`，用来证明净资产不是减法算出来的。

**金额反查三模式**统一折算成 `(minCents, maxCents)` 一对绑定参数，
交给同一条 SQL，不拼字符串：

| 模式 | 下界 | 上界 |
|---|---|---|
| EXACT | v | v |
| APPROX | max(0, v − 100) | v + 100 |
| RANGE | 用户输入最小 | 用户输入最大 |

下界 clamp 到 0（金额恒正），避免负区间把 SQLite 比较搞出意外结果。

## 2. DAO 追加（全为参数化 SQL，零新增表）

**AccountDao**：`observeAccounts()` Flow、总资产 / 总负债两条聚合、
`updateReconciliationAnchor(id, at, balance)`、`updateAccountMeta(...)`、
`archiveAccount(id)`、`hasRecords(accountId)`（决定能否归档时给提示）。

**BalanceAdjustmentDao**：`byAccountId(accountId)`、`byId(id)`。

**RecordDao**：新增 `ReviewRow` POJO 与三条查询：

- `accountRecords(accountId, limit)`：账户详情专属流水（LEFT JOIN 取分类名，
  `ORDER BY localDate DESC, id DESC`）
- `reviewRows(accountId, fromTimestamp, minAmount, maxAmount, largeThreshold, limit)`：
  核对视图基础集。**范围由 SQL 限定**（账户 + 锚点后 + 可选金额区间），
  `LIMIT :limit` 兜底；MUST NOT 全表加载。
- `countExactAmount(accountId, amountCents, fromTimestamp)`：
  供「未找到 0.01 的记录」判断，返回 Int。

`ReviewRow.suspectFlags` 是 SQL `CASE` 算出的位掩码：

| 位 | 含义 | SQL 条件 |
|---|---|---|
| 1 | 金额为 0 | `amountCents = 0` |
| 2 | 未分类 | `categoryId IS NULL AND type <> 'TRANSFER'` |
| 4 | 异常大额 | `amountCents >= :largeThreshold` |

**第 4 个标记「同账户同金额 24h 内重复」在 ViewModel 里补算**，
理由是 EXISTS + ABS(timestamp 差) 的嵌套子查询会让 Room 的列名校验变脆，
而基础集已经被 SQL 限定在「单账户 + 锚点后 + 最多 500 行」，
对这几百行做一次 `groupBy(amount to timestamp)` 的 O(n log n) 判定，
既不是全表加载，也不把复杂 SQL 推给编译器。
**这是如实记录的取舍**：三个标记在 SQL、一个标记在已限定结果集上补算，
不假装「全部在 SQL 里」，也不违反性能铁律的实质。

「只看疑似项」同样是对这个已限定结果集做显示过滤，不是重新查全表。

## 3. Repository：reconcile 是唯一新增事务写方法

```kotlin
suspend fun reconcile(accountId, actualBalanceCents, memo, source) = db.withTransaction {
    val acc = accountDao.byId(accountId) ?: throw IllegalArgumentException(...)
    val delta = Reconciliation.difference(acc.balanceCents, actualBalanceCents)
    if (delta != 0L) {
        adjustmentDao.insert(BalanceAdjustmentEntity(
            accountId = accountId, deltaCents = delta,
            memo = memo, source = source, createdAt = now
        ))
        rebuildAllBalancesInternal()          // 写后重算，唯一余额写入路径
    }
    accountDao.updateReconciliationAnchor(accountId, now, actualBalanceCents)
}
```

**为什么 delta = 0 也要推进锚点**：账实相符本身就是一次成功对账，
锚点应当前移，下次核对范围才缩小。此时不生成无意义的 0 元调整记录。

**为什么锚点不参与余额计算**：`rebuildAllBalancesInternal()` 的真相源是
`initialBalance + 流水聚合 + 未撤销校准`，锚点只是两个「上次对到哪」的展示字段。
若把锚点当期初、锁定锚点前流水，就破坏了「任何时刻可全量重建」这条铁律。

新增写方法 `reconcile` / `createAccount` / `updateAccountMeta` / `archiveAccount`
全部包在 `db.withTransaction` 内，并**同步加进**
`scripts/check_transaction_annotations.py` 的 `WRITE_METHODS` 清单
（原清单硬编码 9 个方法，不扩就会漏检新事务）。

## 4. 资产页（替换 `AssetsPlaceholder`，导航结构不变）

```
AssetsScreen
├── 净资产大卡：SUM(balance WHERE includeInTotal = 1)
├── 总资产 / 总负债（展示用绝对值，注明「仅展示，不参与计算」）
├── 「+ 新增账户」
├── 资产账户组：名称 / 余额 / 计入净资产标记
└── 信用负债账户组：名称 / 余额 / 可用额度 = 额度 + 余额
```

每个账户卡两个入口：「详情」→ `AccountDetailDialog`，「对账」→ `ReconcileDialog`。
`LedgerApp` 只把 `composable("assets") { AssetsPlaceholder() }` 换成
`{ AssetsScreen() }`，4 Tab 与中央 FAB 一行不动。

**账户详情 Dialog**：改名、`includeInTotal` 开关、归档（有流水时只归档不物理删，
符合 `project.md` 永久不做）、该账户专属流水列表、余额调整记录折叠 + 撤销。
撤销走已有 `revokeAdjustment`（事务内 isRevoked = 1 + 重算）。

**新增账户**：插入时 `balanceCents = initialBalanceCents`，随后调
`rebuildAllBalances()` 统一兜底；`type` 用 `AccountType` 枚举经 `Converters`
以名称字符串落库，不用 ordinal。

## 5. 对账与核对视图（资产页内 Dialog，不新增 Tab）

```
ReconcileDialog
├── 账面余额（只读）/ 输入实际余额（元，BigDecimal -> Cents）
├── 差额 = 实际 - 账面
├── |差额| <= 1.00 元  → 「忽略尾差并校准」一键按钮
└── |差额| >  1.00 元  → 「先核对」入口（显示锚点后流水条数）
                        + 「仍要校准」（备注必填，否则禁用）

ReviewDialog（从核对进入）
├── 范围：默认锚点后 / 可放宽近 7、30、90 天
├── 金额反查输入 + 等于 / 区间 / 近似 三模式
├── 只看疑似项开关 + 标记 chips
└── 流水列表；无匹配时「未找到 0.01 的记录」
```

**阈值与容差本阶段写成常量**（`Reconciliation` 里的三个 const），
不做配置 UI。`reconciliation/spec.md` 原话是「可配置阈值（默认 1.00 元）」，
本阶段兑现「默认 1.00 元」，可配置 UI 留到阶段 5 偏好设置——
这不违反 spec，只是把配置能力的落地推迟，并在 `MODIFIED` 文本里写明。

**大额校准备注必填**在 UI 层禁用按钮实现，Repository 层也校验
`memo.isNotBlank()` 双保险。

## 6. 两条 MODIFIED（兑现阶段 2 归档记录的规格债）

| 模块 | 标题 | 为什么必须按标题替换 |
|---|---|---|
| `reporting-ui` | 四 Tab 骨架一次成型 | 原 Scenario 写「点资产 → 显示阶段 3 占位说明」，阶段 3 后反了 |
| `reconciliation` | 阶段边界明确 | 原 Scenario 写「没有按差额反查与对账锚点入口」，阶段 3 后反了 |

两份 delta 都写**修改后的完整要求文本**，标题保持与原 Requirement 一致，
归档阶段 3 时按标题替换。**不能只 ADDED 新要求**：那样 specs 会同时存在
「资产页显示占位」与「资产页显示净资产」两条矛盾要求——
正是阶段 1 归档时踩过的「ADDED 了新要求却忘了 MODIFIED 旧要求」。
本 change 把它提前到写作阶段处理，而不是等归档再补。

MODIFIED 后的「阶段边界明确」不再绑定阶段版本号，改为定义**功能归属边界**：
明细筛选面板只做基础组合筛选；差额反查 / 锚点 / 疑似标记属于资产页核对视图。
这样阶段 4、5 之后依然成立。

## 7. 测试策略与如实说明的限制

| 层 | 验证方式 | 覆盖 |
|---|---|---|
| 对账引擎（纯 JVM） | JUnit 单测 | 差额正负、尾差边界 100/101 分、可用额度、展示聚合值不被减法污染、三种反查区间、疑似标记解码 |
| DAO SQL / 事务 / Compose 编译 | CI `assembleDebug` + `assembleRelease` | Room KSP 校验 @Query、事务包裹、UI 编译 |
| 备份锚点往返 | 纯 JVM 单测 | `BackupAccount` 带锚点 encode/decode 后字段不变 |
| 5 万条真机切月 / 滚动掉帧 / SQLCipher 真机打开 | **本阶段仍无法验证** | 沙盒无 SDK / 模拟器，不假报性能达标 |

**本轮不新增依赖**（阶段 2 已引入 Paging / Navigation / 图标 / ViewModel），
版本组合沿用阶段 0 锁定并经三轮 CI 验证的那套，回退分支预计用不上。

### CI 失败时的回退顺序（每次只改一处、单独推送）

1. Room 不接受 `CASE ... END` 求和作列 / `:param IS NULL OR ...` →
   把疑似标记简化为单独三条 `SELECT` 或移到 ViewModel 对已限定结果集计算
2. `ReviewRow` 列名与 `LEFT JOIN` 别名对不上 → 按编译错误对齐 `AS` 别名
3. `Flow<List<AccountEntity>>` 与 Room 2.8.4 的 suspend/observable 写法冲突 →
   改用 `suspend fun all()` + ViewModel 手动 reload
4. Compose `Dialog` / `LazyColumn` / `Switch` API 签名变化 → 按编译错误补 `@OptIn`
5. `check_transaction_annotations.py` 新增方法名拼写不一致 → 对齐清单

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 不建 change 直接写资产页 | 少 4 个文档 | 两条规格债、锚点事务、阈值决策都会失溯源；违反流程 |
| 为锚点补字段并写 Migration 1→2 | 看起来「正式」 | **字段阶段 1 已预留**，纯属自造破坏性变更；真实账本能不迁移就不迁移 |
| 校准伪装成 EXPENSE + 「余额校准」伪分类 | 复用现有流水表 | `project.md` 永久不做；会污染明细、分类、年报，重算时重复扣 |
| 锚点存 SharedPreferences / 内存 | 不动数据库 | 备份会丢、重装即失效、「数据是你的」铁律要求锚点随账本走 |
| 锚点参与余额计算 / 锁定锚点前流水 | 核对范围更「硬」 | 破坏 `rebuildAllBalances()` 全量重建，是账本最不能动的一条 |
| 全表加载后在 Kotlin 里筛疑似流水 | SQL 最简单 | 违反性能铁律；基础集改为「账户 + 锚点后 + LIMIT」的 SQL 范围查询 |
| 4 个疑似标记全塞进一条 EXISTS 嵌套 SQL | 全部在 SQL | Room 列名校验变脆；3 个 CASE + 1 个在已限定结果集补算，更稳且如实记录 |
| 核对视图做成第 5 个 Tab | 入口明显 | 违反 4 Tab spec；放资产页内 Dialog，导航零改动 |
| 顺手把环形图 / 趋势 / CSV / 应用锁做掉 | 进度看起来快 | 阶段 4 / 5 范围蔓延，是个人项目烂尾首因 |
| 阈值做成设置页可配置 | 完全兑现 spec「可配置」 | 提前引入阶段 5 偏好存储；本阶段常量 + MODIFIED 写明推迟 |
| 物理删除有流水的账户 | 交互简单 | `project.md` 永久不做，只用 `isArchived` 归档 |
| 用「总资产 − 总负债」算净资产 | 直觉 | 负债余额本就为负，再减一次凭空虚高（v1 规格书的经典坑） |

## 取舍

选择 **「零表结构变更 + 独立校准 + 锚点事务 + 资产页内 Dialog + 参数化范围 SQL + 阈值常量」**：

- **牺牲**：阈值暂不可配置；4 个疑似标记里 1 个在 Kotlin 补算；
  统计页仍是占位；真机性能仍未实测
- **换取**：不写 Migration、不改备份格式、不新增依赖、导航零改动；
  两条规格债在写作阶段就用 MODIFIED 修掉；
  差额 1 分钱一键抹平、几十块能反查这两条最真实的诉求都落地

## 首轮 CI 实测（如实记录）

**第 1 轮（commit `78a197a70a`）**：五个静态检查全 success，
`compileDebugKotlin` **一次通过**——零新增依赖、零表结构变更、
`reviewRows` 的 `CASE` 位掩码 SQL 与可选参数短路都被 Room 2.8.4 接受，
第 7 节预留的 SQL 回退分支没有用上。

失败挂在 **测试代码自己漏传参数**：`BackupAnchorTest` 构造 `LedgerSnapshot`
时只写了 `accounts` 与 `exportedAtEpochMilli`，而 `LedgerSnapshot` 的前四个
字段（`accounts` / `categories` / `records` / `balanceAdjustments`）**没有默认值**。
`compileDebugUnitTestKotlin` 报
`No value passed for parameter 'categories' / 'records' / 'balanceAdjustments'`。

**这与阶段 1（净资产常数写错）、阶段 2（筛选维度数写成 8）是同一类错误**：
构造或断言时凭印象写，没核对目标类型的实际定义。
往后写测试 SHOULD 先读一次被构造 data class 的字段清单，
并把「补 `emptyList()`」这种样板写全。

**诊断手段**：job logs 依旧 403（网络策略），沿用阶段 2 的变通方案，
临时在 workflow 里把日志与 JUnit XML commit 回 `diag/`，从 Contents API 读到
`e: ` 行才定位到具体文件。GITHUB_TOKEN 的 commit 不触发新 workflow，无循环；
诊断步骤用完已移除，`diag/` 由单次 commit 推送删除。

**值得记下的好消息**：本阶段**主源码零编译错误**。
阶段 2 首轮有 4 个 import / 嵌套类引用错误，阶段 3 一个都没有，
说明「先读现有 API 再写代码」与「零新增依赖」确实把风险压下来了。

## 风险

**残余风险 1**：Room 2.8.4 对 `CASE` 求和列 / 可选参数短路 SQL 的绑定不接受。
**缓解**：第 7 节回退顺序第 1 条；每次只改一处单独推送；推完查 Actions 确认确实跑了。

**残余风险 2**：`AssetsScreen` 与 `AccountDetailDialog` / `ReconcileDialog` /
`ReviewDialog` 四个新 Composable 首次编译可能有 API 签名差异。
**缓解**：只用 Material3 基础组件；按编译错误逐条补 `@OptIn` 或换重载。

**残余风险 3**：`check_transaction_annotations.py` 的 `WRITE_METHODS` 是硬编码清单，
新增 4 个写方法若不扩清单会漏检。
**缓解**：本 change 同步扩到 13 个方法，并在 `build-and-ci` delta 写成 Requirement。

**残余风险 4**：锚点时间戳用 `System.currentTimeMillis()`，设备时间被改会让
「锚点后」切片错乱。
**缓解**：锚点只影响核对范围、不影响余额；UI 显示锚点日期，用户可手动放宽到
7 / 30 / 90 天；时间可信度问题记入阶段 5 偏好设置再统一处理。

**残余风险 5**：用户在大额差额下跳过核对直接校准，仍可能把真实漏记抹平。
**缓解**：大额时默认只给「先核对」，「仍要校准」要求必填备注且二次确认；
校准独立成表、可撤销，事后发现也能回退再查。

## 验证

- [x] 阶段 0 五项对齐结论齐全、分级判定已告知并等确认
- [x] 阶段 2 已按规范归档，无进行中 change 冲突
- [x] 两条规格债用 MODIFIED 写出完整替换文本，标题与原 Requirement 一致
- [x] Delta 每个 Requirement 至少一个 GIVEN / WHEN / THEN 场景 + 「理由」
- [x] 首轮 CI：`compileDebugKotlin` 一次通过，仅测试代码漏传 3 个必填参数（已修）
- [ ] 修正后重跑，job 结论为 success
- [ ] 推送后用远端 Git Trees API 逐文件比对 sha
- [ ] 使用者确认后归档本 change
