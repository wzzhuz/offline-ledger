# 技术设计：optimize-cold-start-first-frame

## 现状

### 冷启动链路（逐文件核实，main `6f85c8318bda`；feature 分支启动链路源码一致）

| 顺序 | 位置 | 行为 |
|---|---|---|
| 1 | `OfflineLedgerApp.onCreate` | 同步 `System.loadLibrary("sqlcipher")` |
| 2 | `MainActivity.onCreate` | 主线程 `LedgerPrefs(this)` 读 SharedPreferences、`applyReverseColors` / `AmountMask.hidden` / `applyFlagSecure` |
| 3 | `MainActivity.setContent` | `LedgerTheme` → `Surface` → 解锁判定 → `LedgerApp` |
| 4 | `LedgerApp` | `viewModel(factory = ledgerFactory(context) { ep -> DetailViewModel(...) })` → `EntryPointAccessors.fromApplication` 首次构建 Hilt 单例图 |
| 5 | `DatabaseModule` | `provideOpenHelperFactory` 同步跑 `DatabaseKeyManager.getOrCreatePassphrase`（`KeyStore.load` / `getEntry` / AES-GCM 解密）；`provideDatabase` 构建 Room（`@Singleton`） |
| 6 | SQLCipher 首次开库 | `SupportOpenHelperFactory(byte[])` → 原生 `nativeKey` → `sqlite3_key(db, key, size)`；传的不是 `x'...'` raw 格式，**每次开库走密钥派生**（官方默认 256,000 次 PBKDF2 ≈ 512,000 次 SHA-512），再加首页校验 / WAL |
| 7 | `DetailViewModel.init` | 5 个并发协程：`refreshMonthCounts`、`accountDao.all()`、`categoryDao.all()`、`recordDao.allCounterparties()`（全表 DISTINCT）、`_filter.collect { reload(f) }`（`reload` 内 `totalExpense` / `totalIncome` / `dailyTotals` + 再跑一次 `refreshMonthCounts`） |
| 8 | `DetailScreen` | `vm.pagingData.collectAsLazyPagingItems()` → `LazyColumn`；首屏数据未到时列表为空 |
| 9 | `LedgerApp` 另一 `LaunchedEffect(Unit)` | `applyDueRules(today)` 冷启动补记，命中时 `rebuildAllBalancesInternal()` 写库并 `refreshTick += 1` |

`themes.xml` 只有平台 `Theme.Material.Light.NoActionBar`，无 SplashScreen compat、无定制 `windowBackground`；
**但本次症状是「列表空着」而非白屏，说明界面框架已出，瓶颈在 1~8 之间**。

### 为什么不是查询慢（使用者的天然对照实验）

- 切月份：`flatMapLatest` 重建 Pager，重跑 `pagedFiltered` + 三条月聚合 + `monthCounts` → 无感。
- 切统计页：`StatsViewModel.init { reload() }` 跑几十条聚合（趋势 / Top5 / 洞察 / 预算 / 日合计）→ 无感。
- 切资产页 / 记一笔：各自 ViewModel 独立取数（`AssetsViewModel.reload()` / `RecordEditorViewModel.load()`）→ 无感。
- 三者都跑在**同一个已打开的加密库**上，页照样要解密。因此「SQL 聚合」与「页级加解密」均被排除。
- 5 次杀进程冷启动稳定 1~2s → 排除「ART 攒够 profile 后下次变快」；Baseline Profile 仍可能是主菜（安装时 AOT 治的正是「每次冷启都付一次」），但哪一段占大头未测。

### 现有失效出口不统一（缓存作废必须挂在最底层）

| 出口 | 覆盖 |
|---|---|
| `DataRefreshBus.notifyChanged()` | 导入 / 恢复 / 批量规整 / 规则 / 快照设置等设置页动作 |
| `DetailViewModel.bumpRevision()` | 明细删除 / 报销 / 下拉刷新 / `refreshTick` 触发 |
| `refreshTick += 1` | 记一笔保存、`applyDueRules` 命中 |

`applyDueRules` 在冷启动执行且会写库，正好可能落在缓存窗口内，造成「快照已渲染 → 真查询开始 → 补记插入 → Pager 又重建一次」。

### 隐私与构建现状

- `backup_rules.xml` / `data_extraction_rules.xml` 只有三条 `domain="database"` 的 exclude，**没有 `domain="file"`**；
  `check_manifest_privacy.py` 也只检查 `domain="database"` 与两个 section。
- `RecordRow`（`RecordDao.kt` L26）是 DAO 行类，**无 `@Serializable`**；项目约定「备份 DTO 与 Entity 分离」。
- `app/proguard-rules.pro` 无 kotlinx.serialization 相关 keep；release 才跑 R8 / `lintVital`（踩坑 2.3）。
- `SnapshotWriter` 走 `repository.exportSnapshot() → JSON → SecureBackupCodec → SAF`，**不是复制 db 文件**；
  CSV / 加密备份 / 自动快照均只读 Room，不受启动快照影响。

## 方案

### 核心原则

**缓存不是第二数据源，是一次性启动快照。** 一致性不靠「让缓存永远等于数据库」，而靠
「只活 1~2s 的窗口 + 写入时主动失效 + 读取时严格判废 + 窗口结束立即退场」。
**不在冷启动时查库校验缓存**——那又变成串行等开库，前功尽弃。

### 数据结构

新建独立 DTO（建议 `data/local/startup/StartupSnapshot.kt`，纯 Kotlin + `@Serializable`，不依赖 Android / Room）：

```
StartupSnapshot(
  schemaVersion: Int,        // 快照格式版本，与 AppDatabase.version 分别记录、分别比对
  dbVersion: Int,            // 落盘时的 AppDatabase.version（编译期常量，不查库）
  yearMonth: Int,            // 快照对应的默认月
  granularity: String,       // MONTH / YEAR
  savedAtEpochMilli: Long,   // 仅用于展示与超时兜底，不参与判废主逻辑
  rows: List<SnapshotRow>    // 最多 60 条，字段取 RecordRow 渲染所需子集
)
```

`SnapshotRow` 只带渲染字段（id / type / amountCents / localDate / yearMonth / 主标题 / 对方 / 分类名 / 颜色 /
账户名 / `excludeFromStats` 等），**MUST NOT 直接序列化 `RecordRow`**。
落盘位置 `context.noBackupFilesDir`（API 21+，天然不进 Auto Backup / 设备间转移），
先写 `.tmp` → 校验 → 原子 rename（沿用 `SnapshotWriter` 已验证的写法），避免半截文件。

### 首屏双源状态机

```
ColdStart
  ├─ 解锁？（否 → AppLockScreen，不渲染任何账目）
  ├─ 读 StartupSnapshot（IO，主线程只做一次小文件读取）
  │    ├─ 判废任一不满足 → 骨架态（C1 兜底）
  │    └─ 命中 → 快照态（C2）：立刻渲染 60 行
  ├─ 真实 Paging 与快照态并行（现有 pagingData / flatMapLatest / cachedIn 不新起一条流）
  ├─ loadState.refresh: Loading → NotLoading 且首屏结果确定
  │    └─ 一次性原子换源：真实态接管，快照视图销毁，真数据覆写快照文件
  └─ 之后：切月 / 筛选 / 下拉 / 写操作全走既有 revision + flatMapLatest，与快照再无关系
```

**换源必须等 `NotLoading`，不能一有 `itemCount > 0` 就切**：Paging 3 的 refresh 会先清空现有 items。
若快照态与真实态共用一个 `LazyColumn` 数据源，切换瞬间会先闪一次空列表——正好把「列表空着」复现一遍。
因此两者是**两个独立内容分支**，原子换源，中间不留空帧。

诚实记录：若真查询与快照内容不同（补记插入、跨月了），切换会有一处内容跳变，这是**正确行为**（真数据覆盖旧快照）。
验收只要求「不闪空」，**不承诺零跳变**。

### 判废条件（任一不满足 → 骨架态，不使用快照）

1. 当前 `_filter` 为无筛选默认视图：无显式范围 / 无分类 / 账户 / 对方 / 描述 / 金额 / 币种 / 类型条件，`periodDisplayName == null`
2. 默认月视图（或默认年视图，按 `displayGranularity()` 判定）
3. 快照 `yearMonth` == `MonthRange.currentYearMonth()`（跨月后旧快照不作废式展示）
4. 快照 `dbVersion` == 编译期 `AppDatabase.version`（常量比对，**不查库**）
5. 快照格式 `schemaVersion` == 当前实现常量
6. 应用锁已解锁（`unlocked == true`）
7. 快照文件可读且解析成功、行数在 1..60

### 一致性保障（五条不变量）

1. **写操作即失效**：任何写库成功（记一笔 / 编辑 / 删除 / 报销 / 导入 / 恢复 / 批量规整 / 规则回溯 / 周期补记命中）
   MUST 同时作废快照态并触发重查。实现上挂在 `LedgerRepository` 的最底层写事务出口（所有写路径都过
   `db.withTransaction { ... rebuildAllBalancesInternal() }`），**不挂三个上层出口中的任意一个**，
   避免「改一处漏一处」（活文档 §7.27 已两次踩过默认值扩散的坑）。
   作废动作 = 删 / 标记快照文件不可用 + 立即切真实态或骨架态 + 递增既有 revision。
2. **不进任何数据出口**：启动快照 MUST NOT 参与 `exportSnapshot()` / `SnapshotWriter` / CSV / 加密备份 /
   `BackupPayload` / 统计聚合 / 余额重算 / `records` 写入。Room 始终是唯一权威。
3. **不绕应用锁**：快照渲染排在解锁之后；`unlocked == false` 时只渲染 `AppLockScreen`。
4. **失败兜底**：refresh 失败或超 3s → 骨架态 + 提示，MUST NOT 无限期把旧快照冒充最新数据。
   「失败也 MUST 出结果卡，不能只留一行 error 或停在转圈」（活文档 §7 既有教训）。
5. **窗口结束即退场**：换源后快照视图销毁；缓存只供**下一次冷启动**使用，进程内不再读取第二次。

### 隐私配套

- `noBackupFilesDir` + 在 `backup_rules.xml` / `data_extraction_rules.xml` 各补一条 `domain="file"` exclude 双保险。
  **MUST NOT 写 `<include>`**：只写 include 是白名单模式，再 exclude 未 include 的路径会被 `lintVital` 判 fatal（踩坑 2.3）。
- `check_manifest_privacy.py` 扩展：除 `domain="database"` 外，校验 `domain="file"` 排除存在；PASS 文案同步更新。
- `android:allowBackup="false"` / `hasFragileUserData` / 数据库三条 exclude 一条不改。

### 构建配套

- 独立 DTO + kotlinx.serialization；`app/proguard-rules.pro` 补对应 keep；
  CI 以 R8 release 包真机验证反序列化（debug 不混淆，验不出来，踩坑 2.3）。
- 新增纯 JVM 单测：判废条件 7 条（含跨月 / dbVersion 不符 / 有筛选 / 未解锁 / 解析失败）、
  换源时序（Loading→NotLoading 前不得销毁快照态）、写后失效（挂底层出口而非上层）。

### 诊断打点（第一个 task，测完再定后续）

5 个时间戳，一次冷启动即可看出 1~2s 落在哪一段：
`Application.onCreate` 入口 → `loadLibrary` 返回 → `getOrCreatePassphrase` 返回 → 首次 DAO 查询前（Room open 完成） → 首屏 Paging 返回。

判读：
- 落在前三段（加密链）→ 后续可加「开库后台预热 / 与首屏并行」，或另提 change 评估 B（raw key）/ A（去加密）。
- 落在后两段（非加密链）→ 追加自有 Baseline Profile（Macrobenchmark 覆盖冷启动→明细首屏）作为条件性 task。
- 分散 → 感知优先（本方案）已是性价比最高，不再扩大改动面。

打点 MUST 可关闭、MUST NOT 常驻持久化、MUST NOT 写进账本数据文件；交付前确认可从 release 包移除或默认关闭。

### 启动期顺带减负（非主方案）

- `DetailViewModel.init` 与 `reload()` 各跑一次 `refreshMonthCounts` → 去重。
- `allCounterparties()` 全表 DISTINCT、`monthCounts` 整年 GROUP BY → 改成首帧完成后的低优先级后台预取，
  **用户点开筛选面板 / 年月选择器时数据必须已就绪**（不制造空态，见「考虑过的其他方案」）。
- `accountDao.all()` / `categoryDao.all()` 只有几十行，保留在 init。
- `LedgerApp` 的 `applyDueRules(today)` 挪到首屏稳定后的空闲窗口，或保证最终那次 refresh 吸收补记结果。

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| **A 去掉 SQLCipher，改明文 Room** | 省 KDF + 页解密 + so 加载，最贴合「自用可信、加密非红线」的新口径 | 属改铁律 + 改 3 条 spec + 删密钥管理的破坏性变更；现有库打不开，须卸载重装并先导出后导入重建。本轮选「感知优先、零破坏」，且实测已排除页加解密是瓶颈，收益不明。**不否决方案本身，仅本轮不实施**，将来须独立 change + 显式写进 `project.md` |
| **B 保留加密但跳过 KDF**（`x'<64hex>'` raw key / `postKey` 下调 `kdf_iter`） | 省最主要的固定 CPU 成本，不动铁律、改动面最小 | 同样改变已有库的密钥语义，旧库打不开 → 也要卸载重装；且未打点前不确定 KDF 就是 1~2s 的主因。留作打点后的条件性后续 |
| **自有 Baseline Profile 作本轮唯一主菜** | 官方给首次启动约 30% 收益，治的正是「每次冷启都付一次」 | 不解决「首屏数据到达前列表空着」的体感；且需 Macrobenchmark + 真机 profile，本轮先打点，按结果决定是否追加，不预埋结论 |
| **SplashScreen compat / 定制 `windowBackground` 作主方案** | 改善「打开瞬间」观感，官方推荐 | 使用者明确是「列表空着」，界面框架已出；启动画面只遮前段，不解决空列表。降为可选观感项，本轮不做 |
| **把首屏查询瘦身作主方案** | 减并发查询、去重 | 切月 / 统计页无感、2000 条下聚合毫秒级，已被实测证伪为瓶颈。降为「顺带减负」 |
| **缓存作为长期第二数据源 / 冷启动查库校验缓存** | 看起来「更一致」 | 违背核心原则：查库校验等于重新串行等开库，前功尽弃；长期双源必然产生同步与口径分裂（活文档 §4.3 文档腐化同源） |
| **绕过应用锁先渲染快照** | 再快一帧 | 违反 `privacy-security`「解锁前不展示任何账目」硬约束，MUST NOT |
| **靠 ART 自然攒 profile 等下次变快** | 零改动 | 5 次杀进程冷启动稳定 1~2s，使用者实测已证伪 |
| **只要 C1 骨架、不做 C2 快照** | 零缓存文件、零一致性风险 | 立刻有界面但是空壳，离「钱迹那种立马打开看到账目」差一层；保留为 **C1 兜底**，不作为唯一形态 |
| **小组件 / 通知栏磁贴「秒开」** | 钱迹有「秒开模式」 | `project.md`「永久不做」清单；且钱迹公开资料里的秒开模式是「启动后界面直接换成记账界面」，不是常驻入口，不能照搬 |
| **全量内存分组换速度** | 实现简单 | `reporting-ui` 明令禁止，分页 + SQL 聚合口径不得回退 |

## 取舍

**换来的**：冷启动立刻看到真账目（C2 命中时），不缩短那 1~2s 的 CPU 总工作量，但用户看不见它；
零改表、零重装、零规格破坏、不动加密与统计口径；先测后改，不为未验证假设付代价。

**牺牲的**：
1. 磁盘上多一份**明文账目快照**（本地私有目录、排除备份、只活到下次换源、可被任一写操作作废）——
   这是「感知优先」的对价，必须靠隐私配套与不变量兜住，不能悄悄扩大用途。
2. 换源时可能有一次内容跳变（真数据覆盖旧快照）；只承诺「不闪空」，不承诺「零跳变」。
3. 增加一份 DTO、一个状态机、一套判废与失效逻辑；`DetailScreen` 复杂度上升。
4. 本轮**不缩短总时长**，`<1s` 总基线仍待后续按打点结果处理；若使用者将来要「真快」，仍需 B / A 或 Baseline Profile。

## 风险

| 风险 | 怎么验证没出错 |
|---|---|
| 换源闪空帧，把「列表空着」复现 | 真机录屏逐帧看 Loading→NotLoading 之间；单测锁死「NotLoading 前不得销毁快照态」；验收项「无空帧」 |
| 写操作后旧账残留 | 真机在快照窗口内手快点「记一笔」；单测覆盖「底层写事务出口触发作废」；验收项「写后缓存立即退场」 |
| 缓存绕过应用锁 | 真机开启应用锁冷启动，解锁前截图应无任何账目；spec Scenario 锁死 |
| 快照文件被系统备份 / 被其他路径读出 | `noBackupFilesDir` + `domain="file"` exclude 双保险；`check_manifest_privacy.py` 扩展检查；换机 / 设备转移场景复核 |
| R8 下 `kotlinx.serialization` 反序列化失败 | 必须用 **R8 release 包**真机冷启动验证；debug 包验不出来；proguard keep 覆盖 DTO 与生成的 serializer |
| 半截快照文件 | `.tmp` → 校验 → 原子 rename；解析失败按判废回退骨架 |
| `applyDueRules` 落在窗口内造成二次 Pager 重建 | 真机构造一条到期规则冷启动；挪到首屏稳定后并让最终 refresh 吸收补记结果 |
| 跨月 / 改筛选后仍展示上月旧账 | 判废条件 3 / 1 覆盖；纯 JVM 单测逐条断言 |
| 把「CI 全绿」当成性能已验 | 沙盒无真机，首帧耗时 / 无空帧 / 解锁边界 MUST 显式列真机验收；活文档 §8 结项时写明「实测 / 仍未验」 |
| 打点常驻或污染账本 | 可关闭、不持久化、不入导出；交付前复核 release 包默认关闭 |
| 改动波及既有明细行为（切月 / 下钻 / 筛选） | 保持 `pagingData` / `RecordFilter` / `revision` 链路不变；回归活文档 §8 第 45–48 批待验项 |

**实现前必读**：`docs/页面优化经验与避坑指南.md` §1 + §6.1（状态与流程）、§2/§3/§6.2（改 UI 时）、§6.3（交付前检查）；
踩新坑 MUST 按「症状 → 根因 → 解法 → 防范」补进该活文档（AGENTS.md 硬性要求）。

---

## 实现偏差登记（第 49 批，如实记录，不粉饰）

三处与上文门禁描述不完全一致，均为实现期基于代码事实做的收敛，能力范围不缩水：

| 门禁写法 | 实际落地 | 理由 |
|---|---|---|
| 「作废动作挂在 `LedgerRepository` 最底层写事务出口」 | 挂在 `LedgerRepository.init` 注册的 **Room `InvalidationTracker.Observer`**（监听 11 张表），任何写事务提交即 `StartupSnapshotBus.invalidate()` | 逐方法在 `db.withTransaction` 末尾加一行要改 42 处，极易漏（活文档 §7.27「改一处漏一处」已两次踩过）；且 `check_transaction_annotations.py` 逐方法校验 `db.withTransaction` 字面，不能替换成包装函数。InvalidationTracker 是数据库层通知，覆盖面严格大于逐个写方法，含账户 / 分类 / 模板 / 规则等元数据写。仍是「不依赖三个上层出口」的底层出口，spec 意图不变 |
| 「`allCounterparties()` 全表 DISTINCT 改首帧后低优先级预取」 | **保留在 `DetailViewModel.init`**，只去掉 `refreshMonthCounts` 的重复执行 | `FilterSheet` 的对方联想直接读 `vm.counterparties`，延后会在「冷启动立刻点筛选」时制造空态——这正是阶段 0 对话里自己修正过的「不制造空态」。且 2000 条下 DISTINCT 毫秒级，实测已证伪查询是瓶颈；`YearMonthPicker` 的 `disableEmptyMonths` 依赖 `monthCounts`，更不能延后。`tasks.md` 5.2 相应收敛为「去重 + 不制造空态」 |
| 「`applyDueRules` 挪到首屏稳定后的空闲窗口」 | 由 `LedgerApp` 监听 `startupPhase == LIVE` 后触发，而不是定时 delay | 用状态而非时间做门控，避免「首屏还没到就补记」或「首屏已到却还在等计时器」两种边界；命中补记会写库并 invalidate，由最终那次 refresh 吸收，不会造成二次重建 |

另：`StartupTrace.ENABLED` 是编译期常量 `false`，release 默认关闭；诊断时改 `true` 重编一次即可读五段耗时，
不写文件、不进导出。`tasks.md` 1.2–1.4 的真机测量与「是否追加 Baseline Profile」属于使用者侧动作，
代码侧只提供打点能力，**不预埋结论**。

`StartupSnapshotCodec.isUsable` 的 `unlocked` 参数由架构天然保证：`MainActivity` 只在 `unlocked == true`
分支渲染 `LedgerApp` → `DetailScreen`，快照态不可能出现在解锁前；纯 JVM 单测仍单独断言
`unlocked = false` 时判废，防止将来有人把快照渲染挪到锁外。
