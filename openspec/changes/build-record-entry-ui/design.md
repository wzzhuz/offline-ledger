# 技术设计：build-record-entry-ui

## 现状

阶段 1 已交付 5 张表、SQLCipher 加密、`withTransaction` 写后重算、
两级分类解析、最小 JSON 编解码与 33 个纯 JVM 测试。
`MainActivity` 仍是阶段 0 的占位 `Column`，`app/build.gradle.kts` 里
Compose / Room / Hilt / SQLCipher / serialization 都已声明并实测通过。

**阶段 2 不改表结构、不改备份格式、不改签名**，只加 UI 层与少量 DAO 查询。

## 1. 导航结构（以 specs 为准）

```
Scaffold
├── NavigationBar：明细 | 资产 | 统计 | 设置   （恰好 4 个，设置不是第 5 个）
├── floatingActionButton：中央大号 +  → 记一笔
└── NavHost：detail / assets / stats / settings
```

`reporting-ui/spec.md` 明确写「底部导航 SHALL 为 4 个 Tab：明细 / 资产 / 统计 / 设置；
中央 SHALL 为悬浮大号 + 按钮触发记一笔。设置 MUST NOT 作为第 5 个 Tab」。
上一轮对话里的「首页右上角设置入口」属于聊天记录口径，**不采用**。

资产页与统计页本阶段放**明确标注「阶段 3 / 4」的占位内容**，
这样导航结构一次成型，后续阶段只替换内容不重排骨架。

## 2. 新依赖：查证与锁定

| 依赖 | 版本 | 依据 |
|---|---|---|
| `androidx.compose.material:material-icons-extended` | BOM 2026.06.00 管理 | Compose BOM 2026.06 起停止通过 `material3` 传递图标 artifact，必须显式声明；同账号项目的升级记录明确写了「Added an explicit (BOM-managed) material-icons-core dependency」。本项目取超集 `extended`，图标可用性最高 |
| `androidx.navigation:navigation-compose` | 2.9.0 | 与 lifelog 实测组合一致；2.9.x 稳定线，Maven 仓库可查 2.9.0 正式版 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.1 | 与已声明的 `lifecycle-runtime-compose:2.9.1` 对齐 |
| `androidx.paging:paging-common` / `paging-compose` | 3.3.6 | Paging 3 稳定版；`paging-runtime` 是 RecyclerView 适配器，Compose 不需要（官方 Paging 3 依赖说明） |
| `androidx.room:room-paging` | **不引入** | Android Developers 的 Room 依赖说明与 Maven 包信息均确认 2.8.4 可用，但组合筛选是变长 WHERE，Room 生成的 PagingSource 需要 `@RawQuery` 或多份 `@Query`，反而绕 |

**刻意不引入 `hilt-navigation-compose`**：`hiltViewModel()` 需要它与 Hilt 2.57.1
再配一次版本，多一个未实测冲突面。改用 Hilt `@EntryPoint` + 标准
`ViewModelProvider.Factory`（`androidx.lifecycle.viewmodel.compose.viewModel`），
零新增依赖、零版本风险。Repository 与各 DAO 仍由阶段 1 的 `DatabaseModule` 提供。

**图标方案的两处收敛（与对齐结论的实现细节差异，如实记录）**：
① 对齐结论写「显式声明 material-icons-extended」，进一步查证后改为 **`material-icons-core`**：
官方 BOM 映射表显示两个图标 artifact 都冻结在 1.7.8，extended 已被官方弃用且体积达数 MB，
而本应用实际用到的图标（Add / Delete / Close / Settings / Home / List / Star /
KeyboardArrowLeft / KeyboardArrowRight）全部属于 frozen core set。取 core 更轻、依赖面更小。
② **分类图标不做字符串→ImageVector 映射**，而是用 `colorHex` 画圆 + 分类名中文首字。

理由：SeedData 里有 30 个 `iconName`（`Restaurant` / `DirectionsBus` /
`DeliveryDining` / `Soap` …），只要有一个在当前 BOM 的 extended 里不存在，
整份映射表就编译失败；而且中文记账 App 用「彩色圆 + 首字」辨识度反而更高
（钱迹等也是这个路子），还天然适配两级分类。`iconName` 字段原样保留，
留给将来做图标包时再用。导航与按钮图标只用 frozen core set 中确定存在的基础图标。

## 3. 分页：自定义 PagingSource，不是 Room PagingSource

```
RecordFilter (StateFlow)
   └─ flatMapLatest ─> Pager(PagingConfig(pageSize=60, prefetchDistance=20,
     │                       enablePlaceholders=false))
   │                    └─ RecordPagingSource<Int, RecordRow>
   │                         └─ RecordDao.pagedFiltered(...)
   └─ cachedIn(viewModelScope) ─> collectAsLazyPagingItems()
```

**关键反模式防线**：`PagingData` MUST 是 ViewModel 里独立的 `Flow` 属性，
MUST NOT 包进 `data class UiState(val pagingData: PagingData<...>)`——
任何状态变化都会重建 UiState 并把列表弹回顶部（Paging 3 官方反模式清单）。
`Pager` 的 `Flow` 也 MUST 是 `val` 一次性创建，不能在 Composable 重组里 new。

`RecordFilter` 带一个 `revision: Int`。保存 / 删除 / 恢复成功后 `revision++`，
`flatMapLatest` 因此重建 Pager → 列表刷新，且不需要持有 `PagingSource`
引用去手工 `invalidate()`。这比「写完后到处通知列表」少一条容易漏的路径。

`LoadResult.Page.nextKey = offset + rows.size`（不是 `+ loadSize`），
`prevKey = (offset - loadSize).coerceAtLeast(0)`，`getRefreshKey` 以
`anchorPosition - initialLoadSize/2` 为中心，避免刷新后跳回顶部。

## 4. 一条 SQL 覆盖全部组合筛选

不用 `@RawQuery`，用**可选参数为 null 的短路条件**，一条 `@Query` 覆盖
时间 / 账户 / 类型 / 一级 / 二级分类 / 金额区间 / 备注全文的所有组合：

```sql
WHERE (:fromDate IS NULL OR r.localDate >= :fromDate)
  AND (:toDate   IS NULL OR r.localDate <= :toDate)
  AND (:accountId IS NULL OR r.fromAccountId = :accountId
                          OR r.toAccountId   = :accountId)
  AND (:type IS NULL OR r.type = :type)
  AND (:rootCategoryId IS NULL OR r.rootCategoryId = :rootCategoryId)
  AND (:categoryId     IS NULL OR r.categoryId     = :categoryId)
  AND (:minAmount IS NULL OR r.amountCents >= :minAmount)
  AND (:maxAmount IS NULL OR r.amountCents <= :maxAmount)
  AND (:noteQuery IS NULL OR r.note LIKE '%' || :noteQuery || '%')
ORDER BY r.localDate DESC, r.id DESC
LIMIT :limit OFFSET :offset
```

- 全部参数化绑定，无字符串拼接，**无 SQL 注入面**
- `LEFT JOIN categories / accounts` 一次取回分类名、主题色、账户名，
  避免列表渲染时「每行查一次」的 O(n²) 写法
- `localDate` 范围走阶段 1 已建的 `(localDate, id)` 索引
- 统计口径只出现在概览与按日合计里，且复用 `StatRules` 的
  `excludeFromStats = 0` 条件；`TRANSFER` 与已删除（在 trash）流水天然不混入

**按日合计单独一条 `GROUP BY`**：

```sql
SELECT localDate,
       SUM(CASE WHEN type='EXPENSE' THEN amountCents ELSE 0 END) AS expenseCents,
       SUM(CASE WHEN type='INCOME'  THEN amountCents ELSE 0 END) AS incomeCents
FROM records
WHERE yearMonth = :ym AND excludeFromStats = 0
  AND type IN ('EXPENSE','INCOME')
GROUP BY localDate
```

日期头显示的是**整月按日聚合的真实值**（最多 31 行），
不是已加载分页数据求和——后者会随滚动不断变，正是 `project.md`
明确禁止的 `records.size` 类错误。

## 5. 记一笔：低摩擦优先

| 区域 | 实现 |
|---|---|
| 三 Tab | 支出 / 收入 / 转账，切换只改 `type` 与账户字段，不清空金额 |
| 分类网格 | 一级彩色圆网格直接点；点一级后展开该大类二级 chips；**提供「就按一级记」**，两级分类不得把「点两下」变成点四下 |
| 金额 | 内置计算器键盘（0-9、`.`、`+ - × ÷`、`⌫`、`C`、`=`），表达式用 `BigDecimal` 求值 |
| 账户 | 胶囊选择器；支出 / 收入单账户，转账 from → to 两个 |
| 日期 | 显示 `YYYY-MM-DD` + 左右箭头按天调整（今天 / 昨天一键可达）；时间取当前时分 |
| 备注 / 开关 | 备注输入框 + 「不计入收支」开关 |
| 保存 | 构造 `RecordEntity`，`localDate` / `yearMonth` 由 `LedgerClock` 派生，交 `LedgerRepository.insertRecord` / `updateRecord`（内部已含事务与重算） |

**默认记忆**：`RecordEditorViewModel` 记住上次账户 / 上次分类 / 上次使用时间，
下次打开直接预填（`reporting-ui` spec 的「记一笔保持低摩擦」）。
本阶段用进程内 `SavedStateHandle` 级状态，跨进程持久化留到阶段 5 偏好设置。

**编辑复用**：从明细点某行 → 同一个 `RecordEditorDialog` 传 `recordId`，
ViewModel 载入原流水后填入；保存走 `updateRecord`（阶段 1 事务内写入 + 重算，
等价于「回滚旧影响 + 应用新影响」）。

**金额精度**：`Calculator.evaluate()` 纯 JVM、全程 `BigDecimal`，
除法用 `divide(..., 8, HALF_UP)` 防无限小数，最终 `setScale(2, HALF_UP)`
后交给 `Cents.fromYuan`。任何 `Float` / `Double` 都不得出现在金额路径上。

## 6. 回收站与临时导出

- 回收站：列 `trashDao.all()`，每行「恢复」→ `LedgerRepository.restoreRecord`
  （事务内插回 records + 重算）、「彻底删除」→ `TrashDao.deleteByOriginalId`
- 设置页「导出明文 JSON」：`BackupExporter.encode(repository.exportSnapshot())`
  写到 `context.filesDir`（应用私有目录，不需要任何存储权限），
  再用 `BackupImporter.decode` 自校验并回报「可解析，N 条流水」。
  **这是阶段 5 SAF 加密导出的临时替身**，界面上必须醒目标注「明文，勿外传」。

`privacy-security` 铁律不破：新增依赖零网络行为，仍不声明 `INTERNET`，
导出文件只写应用私有目录。

## 7. 测试策略与如实说明的限制

沙盒无 Android SDK / 模拟器，Compose UI 与 Paging 都跑不了 instrumentation。

| 层 | 验证方式 | 覆盖 |
|---|---|---|
| 计算器表达式 / 金额换算 / 月份范围 | 纯 JVM JUnit 单测 | `12.5+3×2`、除法舍入、非法表达式、无浮点、跨月范围 |
| DAO SQL 合法性 / 新依赖解析 / Compose 编译 | CI `assembleDebug` + `assembleRelease` | KSP 校验 @Query、图标 artifact、Paging / Navigation |
| 5 万条真机切月耗时 / 滚动掉帧 | **本阶段无法验证** | 只验证「走分页 SQL + 索引」，不假报性能达标 |
| 真机打开 SQLCipher 数据库 | **留到阶段 2 起真机** | 阶段 1 已如实说明的边界 |

**这不是「性能已达标」**，必须写在这里：分页结构由代码与静态检查保证，
毫秒级指标需要设备。把「编译通过」说成「性能达标」是最容易自欺的一类错误。

### CI 失败时的回退顺序（每次只改一处、单独推送）

1. 图标 artifact 或图标名不存在 → 按编译错误改 import / 换 core set 内基础图标；
   仍不行则彻底去掉图标依赖，用文字或 Canvas 自绘矢量替代
2. `paging-compose:3.3.6` 与 BOM 2026.06 的 Compose 版本冲突 → 先降级到 3.3.5
3. `navigation-compose:2.9.0` 与 Kotlin 2.3 冲突 → 升到 2.9.8（同账号项目已有 2.9.8 升级记录）
4. Room 不接受 `:param IS NULL OR ...` 或 `LIMIT :limit OFFSET :offset` →
   拆成常量 SQL + `@RawQuery(SimpleSQLiteQuery)`，仍保持参数化绑定
5. `hilt` EntryPoint 取 DAO 报错 → 核对 `@InstallIn(SingletonComponent::class)`
6. `collectAsLazyPagingItems` / `items` API 签名变化 → 按编译错误补 `@OptIn`

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 不建 change 直接写 UI | 少 4 个文档 | 导航结构、Paging 取舍、图标方案都会失去溯源；违反流程 |
| 一次性把资产 / 统计 / 设置真做掉 | 看起来进度快 | 范围蔓延是个人项目烂尾首因；违反「每阶段结束必须能编译运行」 |
| `getAll()` 后在 Kotlin 里按日分组 | 实现最省事 | 几万对象 inflate，切月必卡；`reporting-ui` spec 明确 MUST NOT |
| 把 `PagingData` 放进 `UiState` | 状态集中 | Paging 3 已知反模式，任何状态变化都会 scroll-to-top |
| 计算器用 `Double` 求值再转分 | 代码短 | `0.1+0.2` 类误差会直接写进账本，违反金额铁律 |
| `room-paging:2.8.4` + DAO 返回 PagingSource | 官方标准、与 Room 同版本 | 动态组合筛选要 `@RawQuery` 或多份 `@Query`，组合爆炸；自定义 PagingSource 反而更直白 |
| `@RawQuery` 拼 WHERE | 任意组合一条 SQL | 字符串拼接有注入面与转义风险；可选参数短路同样一条 SQL 且全参数化 |
| 分类图标按 `iconName` 映射到 `ImageVector` | 图标好看 | 30 个图标名任一在当前 BOM 不存在就编译失败；彩色圆 + 中文首字零风险且辨识度更高 |
| 引入 `material-icons-extended` 取图标超集 | 图标名可用性最高 | 官方已冻结并弃用该 artifact，体积达数 MB；实际用到的图标都在 core set 内 |
| 引入 `hilt-navigation-compose` 用 `hiltViewModel()` | 写法标准 | 多一个未实测版本冲突面；`@EntryPoint` + 标准 Factory 零新增依赖 |
| 日期头用已加载分页数据求和 | 少一条 SQL | 数值随滚动跳变，正是 `project.md` 禁止的 `records.size` 类错误 |
| 三级分类 / 模板 / 预算 / 快捷方式 | 功能更全 | `project.md`「已确认不做」v1.1 清单，不得悄悄加回 |
| 设置做成第 5 个 Tab 或首页右上角入口 | 少一个占位 Tab | 与 `reporting-ui` spec 冲突，且聊天记录口径不是真理源 |
| 引入 Vico / Coil / Glide | 图表 / 图片现成 | `project.md` 永久不做；Coil / Glide 会传递引入 OkHttp，与零网络权限冲突 |

## 取舍

选择 **「自定义 PagingSource + 单条可选参数 SQL + 4 Tab 一次成型 +
彩色圆中文首字 + BigDecimal 计算器」**：

- **牺牲**：真机性能与 SQLCipher 运行仍未实测；首次 CI 可能因图标 / Paging
  版本多一轮修复；资产与统计暂时是占位页
- **换取**：不新增网络面依赖、不改表结构、不改备份格式；
  分页与筛选只落在一条 SQL 上；低摩擦路径没有被多级分类拉长

## 首轮 CI 实测（两轮，如实记录）

**第 1 轮（commit `a088868aff`）**：静态检查全 success，`compileDebugKotlin` 失败，
4 个错误全是「引用位置写错」，不是设计问题：

| 症状 | 根因 | 解法 |
|---|---|---|
| `MainActivity.kt` Unresolved `fillMaxSize` | 补了 `Modifier` 导入但漏了 `androidx.compose.foundation.layout.fillMaxSize` | 补导入 |
| `RecordPagingSource.kt` Unresolved `LoadParams` / `LoadResult` | 这两个是 **`PagingSource` 的嵌套类**，不在 `androidx.paging` 顶层包 | 改用 `PagingSource.LoadParams` / `PagingSource.LoadResult` |
| `LedgerDi.kt` Unresolved `EntryPointAccessors` | 正确包名是 `dagger.hilt.android.EntryPointAccessors`，不是 `dagger.hilt.*` | 改 import |

**第 2 轮（commit `ad3bd87359`）**：Kotlin 编译全绿、Room KSP / Hilt / Paging /
Navigation / 图标 artifact 全部解析通过，挂在**一个测试自己写错的期望值**：
`RecordFilterTest` 里 `activeCount()` 实际设置了 7 个维度，断言却写 8。
JUnit XML 给出 `expected:<8> but was:<7>`，改成 7 即可。

**这与阶段 1 是同一类错误**：心算了一个常数却没数清楚。
往后这类计数断言 SHOULD 用「构造条件时显式列出维度」或直接比对集合大小，
不手写常数。

**诊断手段**：沙盒读 Actions job logs 与 check-runs 都是 403（网络策略），
按踩坑清单 2.5 的变通方案，临时在 workflow 里加了「失败时把日志与
JUnit XML commit 回 `diag/`」的步骤，从 Contents API 读到 XML 才定位到具体断言。
GITHUB_TOKEN 产生的 commit 不触发新 workflow，没有形成循环；
诊断步骤用完已移除，`diag/` 由单次 commit 推送删除。

**新依赖实测结论**：`material-icons-core`（BOM 管理）、`navigation-compose:2.9.0`、
`lifecycle-viewmodel-compose:2.9.1`、`paging-common/compose:3.3.6`
与既有 AGP 8.13.0 / Kotlin 2.3.21 / Compose BOM 2026.06.00 / Hilt 2.57.1
**一次通过**，design.md 第 7 节预留的版本回退分支没有用上。
`@EntryPoint` + 标准 `ViewModelProvider.Factory` 也未引入额外冲突。

## 风险

**残余风险 1**：Compose BOM 2026.06.00 与 `paging-compose:3.3.6`、
`navigation-compose:2.9.0` 版本组合首次编译不兼容。
**缓解**：第 7 节回退顺序；每次只改一处单独推送；推完查 Actions 列表确认确实跑了。

**残余风险 2**：`material-icons-extended` 由 BOM 管理但个别图标名在该版本被移除。
**缓解**：只用基础图标 + 分类图标不映射字符串；编译错误按行定位。

**残余风险 3**：Room 对可选参数短路 SQL 的 `:param IS NULL` 绑定或
`LIMIT :limit` 不接受。
**缓解**：回退到 `@RawQuery(SimpleSQLiteQuery)`，仍保持参数化绑定，不用字符串拼接值。

**残余风险 4**：`flatMapLatest` + `revision` 重建 Pager 时，若筛选 StateFlow
未变（`distinctUntilChanged`）会漏刷新。
**缓解**：`revision` 是 `RecordFilter` 的字段，写成功后必然产生新实例；
单测与静态检查覆盖「保存后 revision 递增」。

**残余风险 5**：明文 JSON 导出被用户误放到公共目录。
**缓解**：只写 `context.filesDir` 应用私有目录，界面醒目标注「明文，勿外传」；
阶段 5 用 SAF 加密导出替换。

## 验证

- [x] 阶段 0 五项对齐结论齐全、分级判定已告知并等确认
- [x] 阶段 1 已按规范归档，delta 合并进 specs，无进行中 change 冲突
- [x] Delta 每个 Requirement 至少一个 GIVEN / WHEN / THEN 场景 + 「理由」
- [x] Kotlin 编译门禁全绿（新依赖全部解析通过）
- [x] 修正测试期望值后重跑：run 43218438836（commit fa7d33e6ff）**job 结论 success**，
  五个静态检查 / keystore 校验 / 单测 / debug APK / release(lintVital) / 签名诊断 / 制品上传 全部 success
- [x] 推送后用远端 Git Trees API 逐文件比对 blob sha，PASS
- [x] 产物 offline-ledger-debug-apk 22,481,423 bytes（阶段 1 为 21,636,979 bytes，
  增量主要来自 Paging / Navigation / 图标资源）
- [ ] 推送后用远端 Git Trees API 逐文件比对 sha
- [ ] 使用者确认后归档本 change
