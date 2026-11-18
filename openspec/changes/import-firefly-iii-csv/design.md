# 设计：Firefly III 6.6.x CSV 迁移

## 1. 真实样本事实（2026-11-15 导出，6.6.6，1916 行 / 53 列）

以使用者提供的 `2026_11_15_transaction_export.csv` 为准，**不凭官方文档猜**：

| 项 | 实测 |
|---|---|
| 编码 / BOM | UTF-8 无 BOM；逗号分隔；表头 53 列 |
| 类型分布 | Withdrawal 1753 / Transfer 75 / Deposit 74 / Opening balance 14；**无 Reconciliation 类型行** |
| 金额符号 | Withdrawal 为负（-112.59），Transfer / Deposit 为正（31.72），Opening balance 带符号（可为 -1374.96）|
| 币种 | 全部 CNY，`foreign_currency_code` / `foreign_amount` 全空 |
| 日期 | `2026-11-15T11:22:00+08:00`，ISO 8601 带偏移；范围 2010-03-24 ~ 2026-11-15 |
| 账户类型 | source: Asset 1830 / Revenue 74 / Initial balance 12；dest: Expense 1753 / Asset 160 / Initial balance 2 / Debt 1 |
| 分类 | 单列「一级-二级」，如 `食-堂食`、`活-购物`、`入-工资`；也有单级 `宠`；30 行空 |
| 拆分交易 | 同 `group_id` 多行共 7 组（2~3 行），**每行有独立 `journal_id` / 金额 / 描述 / 分类**；`group_title` 非空 15 行 |
| 其他 | tags 77 行非空、notes 42 行、budget 全空 |

**两条对既有假设的修正，必须记住**：

1. 旧 issue 说「amount 全为负」「每 journal 只导一行、第二笔不导出」。6.6.6 的真实导出
   **已经是带符号且每个 split 独立一行**，明细一行没丢。因此原先「split 合并成一笔」
   的方案会凭空把「鞋 124 + 袜子 39.9」压成 163.9 并丢掉描述与分类 —— **改为按行逐条导入**，
   `group_title` 写进备注作为分组线索。这是本批唯一一处按真实样本推翻已拍板方案，
   已在交付说明里明示。
2. 官方导出教程标「不等于备份」「Firefly III 没有导入功能」，指的是规则 / 订阅 / 附件 /
   存钱罐 / 标签系统 / 预算等附属对象不闭环。本项目本来就没有这些概念，
   所以**不导入它们不构成损失**，只在 UI 明示范围。

## 2. 复式 → 单式映射

| FF3 type | 本项目 | 账户 | counterparty |
|---|---|---|---|
| Withdrawal | EXPENSE | source_name | destination_name（收款方） |
| Deposit | INCOME | destination_name | source_name（付款方 / 来源） |
| Transfer | TRANSFER | source → destination 双账户 | 空（转账隐藏该字段） |
| Opening balance | 不进流水 | 非 `Initial balance account` 那侧 | 空 |
| Reconciliation / 未知 | SKIPPED 并列入报告 | — | — |

- 流水金额一律 `BigDecimal.abs()` → `Long` 分，**恒正**；期初余额保留原符号
- 时间：`OffsetDateTime.parse` → `toInstant().toEpochMilli()` → `LedgerClock.fromEpochMilli`
  派生 `localDate` / `yearMonth`（Asia/Shanghai，与样本 `+08:00` 一致），
  避免指南 §3 T13 的 UTC 午夜差一天
- 分类：`substringBefore('-')` 为一级、`substringAfter('-', "")` 为二级，
  按 FF3 type 分 `EXPENSE` / `INCOME` 两组建树（同名一级跨类型分别建，不冲突）
- 转账不设分类（原 `内流-转账` 写进备注），避免污染收支统计唯一出口
- 外部账户（Expense / Revenue / Initial balance）**不建成账户**，只转 `counterparty`

## 3. 账户类型建议（预览页可逐个改）

| 命中词 | 建议类型 |
|---|---|
| 信用卡 / 花呗 / 月付 / 白条 / 债- | LIABILITY |
| 加油卡 / 充值卡 / 公交卡 / 一卡通 / 储值 | PREPAID |
| 证券 / 基金 / 余额宝 / 零钱通 / 小金库 / 理财 | INVESTMENT |
| 其余 | ASSET |

样本 21 个待映射账户据此得到 6 个 LIABILITY、2 个 PREPAID、5 个 INVESTMENT、8 个 ASSET。
净资产仍只按 `includeInTotal` 汇总，不按 type 分支。

## 4. 期初余额语义

`Opening balance` 行不生成流水，而是转成账户 `initialBalanceCents`：

- 映射到**新建账户**：直接把该账户所有期初行求和（14 行合计 1,019,242.43 元）作为初值
- 映射到**已有账户**：默认**不覆盖**（开关默认关），对应期初行计入「跳过」报告。
  只有使用者显式打开「覆盖已有账户的期初余额」且同名映射时才写回。
  理由：悄悄改掉使用者正在核对的余额，比少一条期初更糟。

## 5. 幂等与去重（第 23 批已改为数据库级）

> 本节为第 22 批旧实现，已被 §7.2 取代，保留仅为决策史；**当前实现以 §7.2 为准**。
> 旧做法：`note` 末尾带 `ff3:<journal_id>`，靠 `countDuplicate` 四元组判重，
> 零表结构。第 23 批按使用者 3-C 授权改为
> `records(importSource, sourceId)` 复合唯一索引 + 写前 `countByImportSource`，
> `AppDatabase.version = 2`、无 Migration，**必须卸载重装**。
> 备注里的 `ff3:<journal_id>` 仅保留作人类可读线索，不再承担幂等职责。

## 6. 事务与一致性

`LedgerRepository.importFirefly` 是第 40 个写方法，包在 `db.withTransaction` 内：

1. 按需新建账户（含期初余额）
2. 按需新建两级分类（`createdRoots` / `createdChildren` 缓存，避免同组重复插入）
3. 逐行 `countDuplicate` → `recordDao.insert(withResolvedRoot(entity))`
4. 末尾 `rebuildAllBalancesInternal()`

任一行非法（账户未映射、转账缺转入、金额 ≤ 0）抛异常 → 整体回滚。
每条都经过 `withResolvedRoot`（一级分类解析 + 币种继承 + 转账同币种校验），
与 `insertRecord` 完全同构，不新开写余额的分支。

## 7. 回退顺序（若 CI 或真机失败）

1. 编译错误先看 `import` 与 `internal fun parseRows` 可见性；
   `firstNotNullOfOrNull` / `OffsetDateTime` 需要 Kotlin 1.5+ 与 java.time desugaring
   （本项目已具备，LedgerClock 已用 `Instant` / `ZoneId`）
2. 若 `LazyColumn` 的 `items` 或 `Modifier.weight` 报解析错误，
   退回 `Column + forEach`（本页最多 49 行，不是万级列表）
3. 若 `createdRoots` 的 Elvis 链推断失败，退回显式 `if` 分支 + 非空 `val`
4. 真机若发现期初余额方向反了，只改 `buildPlan` 里 `Opening balance` 那一行，
   不动 Repository

## 8. 明确不做

复式模型、对方账户建成账户、汇率折算 / 跨币种混算、标签系统、规则引擎、
订阅 / 附件 / 存钱罐 / 预算导入、API 直连与 PAT 凭据（留作可选二期）。

## 9. 实测（2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `71baf76b35768b57e4c887a0ed970108d23f8de2`（21 文件，v0.2.6） |
| push CI | `44109301622` **success**，17 步全绿，首轮 Kotlin 编译零错误 |
| release | `44110502009` **success**，23 步绿 / 5 步按 input 跳过，**上传到坚果云 success** |
| 制品 | `offline-ledger-release-v0.2.6` 12,272,160 B；`offline-ledger-mapping-v0.2.6` 3,583,067 B；`offline-ledger-metrics-v0.2.6-p-m-nsfalse` 270 B |
| 静态检查 | 六个脚本本地与 CI 全 PASS；写方法清单 40 个 |
| 诊断 | 首轮零失败，未使用临时 `diag.yml`，`diag/` 零残留 |

`nsfalse` 表示坚果云上传步骤 `skipped=false`（认证 / 建目录 / PUT 均未失败）；
HEAD 落盘校验只写日志不中断，不当作已验证 HEAD 200。

**真机未验**：用真实 1916 行样本导入、核对各账户余额与统计页合计、
映射预览与结果报告在 6.73 寸上的可读性 —— 这三项由使用者装机确认，
CI 全绿只证明编译与单测通过。



## 7. 第 23 批：导入闭环、数据库级幂等与统计增强

### 7.1 根因（先写再动手，指南 §6.1）

| 反馈 | 根因 |
|---|---|
| 导入完没提示 | 事务提交后只写 `state.message`，`firefly=null` 让页面回到「选文件」分支，摘要被说明文本挤到屏幕外 |
| 切月份才刷新 | `DetailViewModel.pagingData` 由 `_filter` 参数触发；导入写库不改 `_filter`，`cachedIn` 的旧 PagingData 一直用着。只加下拉手势不改数据源，拉了也刷不出来 |
| 环形图全同色 | `CategoryEntity.colorHex` 默认 `#888888`；自动生成的分类全部同色，`sliceArgb` 优先读它，扇区糊成一块 |
| 横屏压状态栏 | 全屏 `Surface` 没吃 WindowInsets，标题行与状态栏时间/电量叠在一起 |
| 缺净结余 / 多年 | 趋势卡只有 EXPENSE / INCOME / BOTH 三档，`StatsMode` 只有 MONTH / YEAR |

### 7.2 3-C 表结构与回退顺序

`records(importSource, sourceId)` 复合唯一索引，两列可空：
SQLite 唯一索引里 NULL 互不冲突，手工记账恒 `null`，不受影响。
`trash` 同步两列，删除→恢复不丢来源。备份 DTO / CSV 导出都带两列，
旧备份靠默认值、旧 CSV 靠「缺列按空」继续可导。

`AppDatabase.version = 2`，`Migrations.ALL` 仍空、`fallbackToDestructiveMigration`
仍被静态检查硬禁 —— 所以**卸载重装是唯一安装路径**，不允许覆盖安装。

三级回退顺序（若 CI 红）：
1. 先只修 Kotlin 编译错误（import / 重载 / 参数），不动结构；
2. 若 Room schema 校验报错，核对 `@ColumnInfo` 名与索引列名，不改 version 语义；
3. 若唯一索引导致导入事务抛约束异常，退回「写前 `countByImportSource` + 快照回滚」，
   不引入 `OnConflictStrategy.IGNORE` 掩盖问题。

### 7.3 五项对齐落地

- 结果摘要：`SettingsState.fireflyResult` 独立于 message，页面内常驻卡 +
  「去看明细 / 关闭结果」；CSV 导入结果带「疑似匹配 K 项、按勾选跳过 J 行」。
  **范围如实说明**：疑似匹配本次只实现「跳过该行 / 仍导入」二选，
  不提供「用账单覆盖手工」写路径——避免静默改写手工流水，也避免给手工行
  赋 `sourceId` 触发唯一键冲突；若后续要补，走 `updateRecord` 且保持
  `importSource = null`，单独建 change。
- 刷新：`DataRefreshBus`（进程内 SharedFlow）→ `LedgerApp.refreshTick` →
  明细 `bumpRevision()` / 资产 `vm.reload()` / 统计 `vm.reload()`。
  明细页顶部 28dp 条接管下滑手势，MUST NOT 挂整个列表（第 17/18 批同型坑）。
- 图表：`ChartData.chartArgb(id, index)` 按分类 id 稳定取色，扇区与图例共用；
  分类标识仍中性单色圆，不恢复 `colorHex` 可配、不用高饱和跳色。
- 净结余：`seriesMaxAbsSigned` / `normalizeSigned` / `yAxisLabelsSigned`，
  0 落在绘图区正中，横屏页也带 `EXTRA_SIGNED`，仅同币种、不跨币种混算。
- 多年：`StatsMode.MULTI_YEAR`，复用 `monthlyTrendForCurrency` 在纯 JVM 层按年归并，
  不新增 SQL；点年份 `vm.openYear()` 下钻回 YEAR。

### 7.4 五个疑问的答复写进规格

Q1 重复导入不会双份，按 `(importSource, sourceId)` 唯一索引 + 写前查重；
Q2 现在**有**类似 `journal_id` 的唯一标记了（数据库级）；
Q3 CSV 明文含 11 列（含来源），加密备份 DTO 含两字段；按 B 方案**不写 V1→V2
   schema 迁移兼容层**，重建主路径是「卸载重装 → 重跑同一份 FF3 CSV」，
   手工补录 / 模板 / 预算等不在 FF3 CSV 内的部分按使用者授权接受重建；
   旧备份 JSON 因新字段带默认值仍可解析，但**不承诺完整重建**；
Q4 跨来源对账是「疑似匹配预览」而非自动去重，逐笔确认、禁止静默合并；
Q5 预制账户 / 分类是 `SeedData` 代码常量，首次建库写进表，之后是普通行。

### 7.5 规格修订（指南 §5）

本 change 未归档，`backup-restore` 里「MUST NOT 新增字段」「ff3 前缀实现幂等」
两条旧表述按 3-C **直接修订为最终行为**，不追加 ADDED 制造相反文本。
`reporting-ui` / `ledger-transactions` 各补结果摘要与唯一标记的 ADDED。


## 10. 第 23 批实测回填（2026-11-18）

| 项 | 结果 |
|---|---|
| 代码 commit | `2e6b227cfd5f73b9202afdf6e906262c4810adf0`（单次 commit，34 文件：2 新增 + 32 修改、零删除） |
| push CI | `44221209264` **success**，17 步全绿，**首轮 Kotlin 编译零错误**；未走临时诊断通道 |
| release | `44222024298` **success**，23 步绿 / 5 步按 input 跳过；**「上传到坚果云」步骤 success** |
| 制品 | `offline-ledger-release-v0.2.7` 12,285,184 B；`offline-ledger-mapping-v0.2.7` 3,603,885 B；`offline-ledger-metrics-v0.2.7-p-m-nsfalse` 270 B |
| 差异校验 | 推送前「远端 305 ↔ 本地 307 blob」差异集合断言 PASS；`diag/` 零残留、workflow 仅 build/release |
| 静态检查 | 六个脚本本地全 PASS；Kotlin 大括号 / 引号粗检 0 个不平衡文件 |

**安装口径（B 方案）**：`AppDatabase.version = 2`、无 Migration，
**必须卸载重装，不允许覆盖安装**。重建主路径为「卸载重装 → 重跑同一份 FF3 CSV」；
不写 V1→V2 schema 兼容层，手工补录 / 模板 / 预算等不在 FF3 CSV 内的部分
按使用者授权接受重建。装前必须提醒「先导出，再看目录」。


## 11. 第 24 批：把 v0.2.7 的三个「半成品」收口（v0.2.8）

### 11.1 根因（先写再动手，指南 §6.1）

| 反馈 | 根因 |
|---|---|
| 导入完没提示 | v0.2.7 的结果卡挂在 `Column` 顶部，与「选文件」分支并列；`firefly=null` 后页面回到说明文本，卡被挤出视野。且状态机只有 idle/confirming/result 三态，缺 `running`——1916 行写入期间只有按钮置灰，用户只能去明细页猜 |
| 横屏底部时间轴被压没 | 第 23 批把 `systemBarsPadding()` 套在整块 `Surface` 上，横屏下 statusBars + navigationBars + captionBar 一起扣掉可用高度；`LineChart(chartHeight=300.dp)` 又是固定值，横轴标签槽首当其冲被裁 |
| 「下滑提示小字」不对 | 第 23 批自造了 28dp 窄条 + `detectDragGestures`，既不是通用做法，也写教手势的常驻小字。官方 Pull-to-Refresh 的定义就是「内容在 Index 0、不能继续上划时向下拉，过阈值松手触发，指示器一直留到刷新完成」 |

### 11.2 落地

- 导入页 `when { result / st==null / else }` 四态：`result` 为**整页结果视图**（不自动返回）+ Snackbar；`busy` 时显示「正在写入 N 条」+ `LinearProgressIndicator`；`fireflyResult` 独立于 `message`，避免被说明文本挤掉。
- 明细页用 `androidx.compose.material3.PullToRefreshBox` 替换自造条；删掉《从此向下滑刷新当前月份》小字；`isRefreshing` 与首屏加载标志分离，刷新结束 `SnackbarHost` 反馈「已刷新当前月份 · N 条记录」；`DataRefreshBus` 驱动的自动刷新保持不动，不产生双转圈。**按使用者要求不加 TopAppBar 无障碍刷新入口**。
- 横屏页：`Surface` 不再整体吃 inset；`BoxWithConstraints.statusBarsPadding()` 算可用高度，`chartHeight` 由 `maxHeight - reserved` 动态得出（预留标题 / 横轴标签 / 说明 / 导航栏）；只有底部说明行吃 `navigationBarsPadding()`。
- 版本 0.2.7 → 0.2.8；表结构仍 v2 不变 → **v0.2.7 上覆盖安装即可**，但从更早版本升上来仍须卸载重装。

### 11.3 三级回退顺序（若 CI 红）

1. 先只修 Kotlin 编译错误（import / `ExperimentalMaterial3Api` opt-in / 重载解析），不动布局；
2. 首轮 CI 实测命中第 1 条：`PullToRefreshBox` 报 `Unresolved reference` 的根因是
   **包名写错**——官方在 `androidx.compose.material3.pulltorefresh` 子包，不在
   `androidx.compose.material3` 根包；不是 BOM 缺 API。只改一行 import 即全绿，
   第 2 / 3 条回退均未启用。若将来确实缺该 API，再退到
   `rememberPullToRefreshState` + `Modifier.pullRefresh` + `PullRefreshIndicator`，
   仍零第三方依赖；
3. 若横屏动态高度导致 Canvas 溢出，退到固定 `chartHeight = 180.dp` + 底部
   `navigationBarsPadding()`，不再动 insets 分层。

### 11.4 实测回填

| 项 | 结果 |
|---|---|
| 代码 commit | `eec6e9409fd17aa5f6c264a1be94d298fd3c97ed`（8 文件，v0.2.8）→ 首轮 CI 红 → 修复 + 清理诊断通道 `d39bf68135ada345ff3c26adf95816f283890069`（最终 HEAD） |
| push CI | `44230612494` **failure**（`PullToRefreshBox` import 包名错）；临时 diag workflow `44230973142` 回传 gradle 日志定位；修复后 `44231434403` **success**（17 步全绿） |
| release | `44232196858` **success**，23 步绿 / 5 步按 input 跳过；**「上传到坚果云」步骤 success** |
| 制品 | `offline-ledger-release-v0.2.8` 12,317,033 B；`offline-ledger-mapping-v0.2.8` 3,656,535 B；`offline-ledger-metrics-v0.2.8-p-m-nsfalse` 272 B |
| 仓库卫生 | 307↔307 blob、sha 零差异 PASS；`diag/` 零残留、workflow 仅 build/release；六个静态检查全 PASS |

安装口径：表结构仍 v2 不变 → **v0.2.7 上覆盖安装即可**；从 v0.2.6 及更早升上来仍须卸载重装。
`nsfalse` 只表示认证 / 建目录 / PUT 均未失败，HEAD 落盘校验只写日志不中断，
不当作已验证 HEAD 200。**真机复核仍由使用者装机确认**：导入完成提示、
下拉刷新手感与结果反馈、横屏三键 / 手势导航两种模式下底部时间轴完整性。


## 12. 第 25 批：导入结果真正可见 + 资产页下拉刷新（v0.2.9）

### 12.1 根因（先写再动手，指南 §6.1）

真机反馈「导入结果还是没显示，只有正在导入动画」= 卡在 `running`。
第 24 批已把 `result` 做成整页 `when` 第一分支、也写了 `fireflyResult = outcome`，
但 `SettingsViewModel` 全文件 44 处状态写都是
`_state.value = _state.value.copy(...)`——**读 + 改 + 写非原子**。
`confirmFireflyImport` 在结果赋值**之前**先 `launch` 了 `loadCategories()` /
`loadAccounts()`；这两个协程各自 `withContext(IO)` 挂起后基于旧快照回写，
完全可能把刚赋上的 `fireflyResult` 覆盖回 `null`、`busy` 覆盖回 `true`。
状态机分支写得再对，状态本身被覆盖就永远进不去 `result`。
这是指南 §3 T7「作用域错误」的并发变体，也是第 6 次「把准备做的状态当已验证结果」。

### 12.2 落地

- `SettingsViewModel` 44 处状态写统一改成 `MutableStateFlow.update { st -> st.copy(...) }`
  （lambda 参数命名 `st`，避免遮蔽参数内可能存在的 `it`）；
  `confirmFireflyImport` 改 `try/catch/finally` 单一出口：结果只在成功分支赋一次，
  `finally` 只兜底复位 `busy`，`loadCategories()` / `loadAccounts()` 移到结果赋值之后。
- `FireflyImportOutcome` 加 `success / errorMessage / failRows / durationMs / totalRows`
  （全带默认值，repository 的 6 参数位置构造不动）；结果卡显示耗时与文件总行数，
  **失败同样出结果卡**并给原因与「完成 / 重试」路径，不再只留一行 error。
- 结果出现时 `LaunchedEffect` 强制 `showConfirm = false`；确认弹窗条件补 `result == null`。
- 资产页复用明细页已验证的官方 `PullToRefreshBox`：专用 `isRefreshing` 标志、
  `vm.reload()` + Snackbar「已刷新资产 · 账户与余额已重算」、零第三方坐标、
  **不加无障碍按钮**（第 24 批使用者口径）。导入后的自动刷新仍由 `LedgerApp`
  的 `refreshTick` 通道驱动，手动下拉不重复发 `DataRefreshBus` 事件，避免双转圈。
- 表结构仍 `version = 2` 不变 → **v0.2.8 上覆盖安装即可**。

### 12.3 三级回退顺序（若 CI 红）

1. 先只修编译错误（`update` import / `ExperimentalMaterial3Api` opt-in / 括号），不动逻辑；
2. 若 `update` 与当前协程版本不兼容，退到手写 `while(true) { compareAndSet }` 原子循环，
   仍不用非原子写；
3. 若资产页 `PullToRefreshBox` 与 `rememberReorderableLazyListState` 手势冲突，
   退到只把 `CurrencyGroupsCard` 以上的头部包进刷新容器，账户列表不动。

### 12.4 实测回填

| 项 | 结果 |
|---|---|
| 代码 commit | `024ee5aeb04d55f7204e9d69c2fc09cb0e64dacf`（10 文件：0 新增 + 10 修改、零删除） |
| push CI | `44243316679` **success**，17 步全绿，**首轮 Kotlin 编译零错误**；未走临时诊断通道 |
| release | `44244105038` **success**，23 步绿 / 5 步按 input 跳过；**「上传到坚果云」步骤 success** |
| 制品 | `offline-ledger-release-v0.2.9` 12,317,953 B；`offline-ledger-mapping-v0.2.9` 3,661,914 B；`offline-ledger-metrics-v0.2.9-p-m-nsfalse` 270 B |
| 静态检查 | 六个脚本本地全 PASS；Kotlin 大括号 / 引号粗检 0 个不平衡文件 |
| 文档回填 | 本 commit（`docs(import-feedback)`），代码零变化 |

真机复核仍由使用者装机确认：1916 行走完后结果卡可见（含耗时 / 行数）、
失败也出卡、资产页到顶下拉出圆形指示器且松手有反馈、明细自动刷新不回退。
