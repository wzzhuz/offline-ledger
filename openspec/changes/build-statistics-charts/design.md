# 技术设计：build-statistics-charts

## 现状（关键：统计所需的 SQL 早就写好了）

开工前实际读了 `RecordDao.kt` / `CategoryDao.kt` / `MonthRange.kt` / `LedgerDi.kt`，
确认阶段 1 / 2 已把本阶段要用的**全部数据出口**备好：

| 已有能力 | 位置 | 阶段 |
|---|---|---|
| `totalExpense(ym)` / `totalIncome(ym)` | `RecordDao` | 1 |
| `expenseByRoot(ym)` → `GROUP BY rootCategoryId` | `RecordDao` | 1 |
| `expenseByCategory(ym)` → `GROUP BY categoryId` | `RecordDao` | 1 |
| `monthlyTrend(from,to)` → 12 行 × 收入/支出 | `RecordDao` | 1 |
| `dailyTotals(ym)` → 整月按日聚合，最多 31 行 | `RecordDao` | 2 |
| `pagedFiltered(...)` → 一条 SQL 覆盖全部组合筛选 | `RecordDao` | 2 |
| `CategoryDao.all()` → 名称 / `colorHex` / `parentId` / `level` | `CategoryDao` | 1 |
| `MonthRange.of(ym)` → 当月首尾日期与天数 | `core/time` | 0 |
| `StatRules.SQL_STAT_RELEVANT` | `engine` | 1 |
| `LedgerEntryPoint` 已暴露 `recordDao()` / `categoryDao()` | `ui/LedgerDi.kt` | 2 |

**结论**：`AppDatabase` 保持 `version = 1`，**本阶段不写 Migration、不改备份格式、
不改 `schemaVersion`、不新增任何 Gradle 依赖、不新增 Repository 写方法**。
阶段 3 靠「阶段 1 预留锚点字段」省掉 Migration，阶段 4 靠「阶段 1/2 预留聚合 SQL」
省掉数据层改动——这是分阶段计划里「每阶段结束必须能编译运行」带来的复利。

**唯一新增的 DAO 方法**是一条只读查询
`subcategoryTotalsUnderRoot(ym, rootId)`，用于「点一级扇区 → 展开二级构成」。
它同样带 `type = 'EXPENSE' AND excludeFromStats = 0`，并被
`check_stat_rules.py` 的新扩展覆盖。

## 1. 纯 JVM 数据准备层 `engine/ChartData.kt`

把「补 0 / 合计校验 / 扇区角度 / 点击命中 / 折线归一化 / 颜色解析」收敛到一个
纯 JVM object，好处是全部可单测（沙盒无模拟器也能跑），UI 只负责画。

```kotlin
data class ChartSlice(val id: Long?, val name: String, val colorHex: String?, val cents: Long)
data class ChartSector(val slice: ChartSlice, val startAngle: Float, val sweepAngle: Float)
data class DailyPoint(val day: Int, val expenseCents: Long, val incomeCents: Long)
data class MonthPoint(val month: Int, val incomeCents: Long, val expenseCents: Long)
data class TotalsCheck(val rootCents: Long, val childCents: Long, val totalCents: Long,
                       val consistent: Boolean, val driftCents: Long)
```

| 函数 | 做什么 | 为什么放纯 JVM |
|---|---|---|
| `checkTotals(root, child, total)` | 分别 `sumOf` 三个口径并比对，返回差额 | 合计写错不崩溃、编译也能过，只能靠单测拦 |
| `fillDailySeries(ym, rows)` | 按 `MonthRange.of(ym)` 的天数补齐 1..N，缺日为 0 | 闰年 / 大小月 / 空月最容易漏 |
| `fillYearSeries(year, rows)` | 补齐 1..12 月，空月 0 | 年度双线不断线、不缺月 |
| `sectors(slices)` | 金额 → 扇区起止角（12 点方向起、顺时针） | 角度计算与命中可测 |
| `sectorHit(sectors, angleDeg)` | 点击角度归一化后落在哪个扇区 | 环形图交互唯一容易算错处 |
| `normalize(values)` | Long 分 → 绘图用 0..1 比例 | 全 0 / 单系列 / 最大值边界 |
| `parseHexArgb(hex)` | 手写 `#RRGGBB` → ARGB `Long` | 不依赖 `android.graphics.Color`，纯 JVM 可测 |

**⚠️ 关于「绝对禁止浮点数」的边界（如实说明）**：
金额的**存储、求和、比较、差额**全部是 `Long` 分，绝不出现浮点。
唯一用 `Float` 的地方是「金额比例 → Canvas 绘制角度 / 归一化坐标」这一步**几何换算**，
结果只进 `DrawScope`，**不回写任何金额、不参与统计**。
这不是金额运算，是 Canvas 绘制的必要条件；写在这里是为了让半年后的人
不会误以为金额铁律被破了，也不会误以为可以用 Float 去累加金额。

## 2. Canvas 组件（零 `TextMeasurer`、零 `android.graphics`）

```
ui/charts/DonutChart.kt   —— drawArc 画扇区 + Box 叠中心总额 + 图例
ui/charts/LineChart.kt    —— drawLine 画网格 / 折线 / 点，x 轴刻度用 Compose Text
```

**刻意不在 `DrawScope` 里画文字**：`drawText` 需要 `rememberTextMeasurer()`
（Compose 版本相关 API），而图例、中心总额、坐标轴刻度用普通 Compose `Text`
叠在 `Box` / `Row` / `Column` 里即可，零额外 API、零版本风险。
Canvas 只负责弧、线、点、网格——这也是阶段 0「去掉所有『或』、锁定依赖」
之后最省心的一次实现。

**环形图点击命中**：`pointerInput` 拿到点击坐标 → 相对圆心算
`atan2` 角度 → 归一化到 [0,360) → `ChartData.sectorHit` 返回扇区下标。
命中检测本身是纯 JVM 的，单测覆盖跨 0° 边界与空扇区。

**折线归一化**：`LineChart` 接收 `List<LineSeries>`（label / color / `List<Float>`），
`Float` 值由 `ChartData.normalize` 从 `Long` 分算好，UI 不再碰金额换算。
收入绿、支出红，与 `reporting-ui` 中文语境配色一致。

**颜色解析**：扇区颜色用 `ChartData.parseHexArgb` 得到 ARGB Long，
再 `Color(argb)` 构图；解析失败回退 `LedgerColors.Neutral`。
`LedgerUiParts.CategoryIcon` 用的是 `android.graphics.Color.parseColor`（UI 层可用），
两条路径互不干扰，图表层不因此依赖 Android SDK。

## 3. DAO 追加（一条只读查询，零新增表）

```sql
SELECT categoryId, SUM(amountCents) AS totalCents
  FROM records
 WHERE type = 'EXPENSE' AND excludeFromStats = 0
   AND yearMonth = :ym AND rootCategoryId = :rootId
 GROUP BY categoryId
 ORDER BY totalCents DESC
```

命名 `subcategoryTotalsUnderRoot`，**刻意不以 `expenseByCategory` 开头**：
`check_stat_rules.py` 用 `dao.find('fun ' + marker)` 回溯最近的 `@Query`，
若新函数名前缀撞上已有 marker，会把校验指向错误的查询。
这是沿用阶段 1 归档那两次「宽泛子串自误报」换来的经验。

同时把 `subcategoryTotalsUnderRoot` 加进
`scripts/check_stat_rules.py` 的统计查询 marker 列表，
要求其 `@Query` 同样带 `excludeFromStats = 0`。
`check_transaction_annotations.py` 的 `WRITE_METHODS` 保持 13 个不变
（本阶段**不新增写方法**）。

## 4. 统计页（替换 `StatsPlaceholder`，导航结构不变）

```
StatsScreen
├── 时间维度切换：月度 2026-11 ◀ ▶ ／ 年度 2026 ◀ ▶
├── 收支对比概览：总收入 / 总支出 / 结余（结余为负用红色）
├── 合计一致性警告条（不一致才出现，显示差额）
├── 「按大类 / 按小类」切换 + DonutChart
│     └── 点一级扇区 → 展开该 root 下二级构成（Dialog）
│           └── 点二级 → 该分类明细列表（复用 pagedFiltered + LIMIT）
├── 月度：每日支出折线（1..月末，空日补 0）
└── 年度：1–12 月 收入 vs 支出 双折线
```

`LedgerApp` 只把 `composable("stats") { StatsPlaceholder() }` 换成
`{ StatsScreen() }`，4 Tab 与中央 FAB 一行不动。

**`StatsViewModel`** 只读、无写事务：
`StateFlow<StatsUiState>` 持有 `yearMonth` / `year` / `mode`（MONTH/YEAR）/
`sliceLevel`（ROOT/CHILD）/ 概览数字 / 扇区 / 日序列 / 月序列 / 合计校验 /
选中的 rootId 与下钻列表。切月、切年、切层级都重新跑对应聚合 SQL，
**每次只返回十几行 / 31 行 / 12 行**，不加载记录实体。

**下钻路径**（`category-stats` spec 的三级下钻）：
一级扇区点击 → `subcategoryTotalsUnderRoot(ym, rootId)` → 二级构成列表；
二级点击 → `pagedFiltered(fromDate, toDate, ..., categoryId = id, ..., limit=100, offset=0)`
→ 明细 Dialog。复用阶段 2 的组合筛选 SQL，不另写一套、不全量加载。

**合计一致性**在 ViewModel 里用 `ChartData.checkTotals` 算，
`consistent = false` 时统计页顶部显示红色警告条与差额（分），
且 `ChartDataTest` 用构造数据断言一致 / 不一致两条路径。

## 5. 1 份 MODIFIED（彻底收口「统计页占位」）

阶段 3 归档 `design.md` 记录：替换后的「四 Tab 骨架一次成型」仍写着
「统计页在阶段 4 落地前 SHALL 显示明确标注『阶段 4』的占位内容」，
Scenario 还写「**GIVEN** 阶段 3 版本」——这是同一个坑**第二次复发**。

本 change 的 `specs/reporting-ui/spec.md` 用
`## MODIFIED Requirements` 写出**完整替换文本**：

- 正文改成「统计页 SHALL 展示月 / 年报表（收支概览、分类占比、趋势曲线）」，
  **不含任何阶段版本号、不含「占位」字样**
- Scenario「打开统计页」改成「**GIVEN** 应用已记入若干流水 → **WHEN** 点统计 →
  **THEN** 显示月 / 年切换、收支概览、环形图与趋势曲线」
- 理由段明写「这是同一条 Requirement 的第二次 MODIFIED，
  目的是终结『每阶段都要改占位表述』的循环」，并纠正阶段 3 delta
  自称「不再绑定阶段版本号」却仍绑定的自述不一致

归档阶段 4 时按标题**第二次整块替换**，而不是追加 ADDED。
这样阶段 5 及以后都不必再为「某页当前是占位」反复修补。

**抽象规则**（建议上提 `my-app-workflow/踩坑清单.md`，本轮不改那个仓库）：
`delta` 的 Requirement 只描述系统**最终 SHALL 做什么**；
「某页当前是占位」属于 `tasks.md` / `proposal.md` 的过程状态，不进 `specs/`。

## 6. 测试策略与如实说明的限制

| 层 | 验证方式 | 覆盖 |
|---|---|---|
| `ChartData`（纯 JVM） | JUnit 单测 | 合计一致/不一致、闰年 2 月 29 天、大小月、空月补 0、单系列、全 0 序列、扇区角度与跨 0° 命中、颜色解析合法/非法 |
| 新增 SQL 口径 | `check_stat_rules.py` 扩展 | `subcategoryTotalsUnderRoot` 必须带 `excludeFromStats = 0` |
| DAO / Compose / Canvas 编译 | CI `assembleDebug` + `assembleRelease` | Room KSP 校验 @Query、DrawScope API、导航接线 |
| 5 万条真机切月 <200ms / 年报聚合 <300ms / 滚动掉帧 | **本阶段仍无法验证** | 沙盒无 SDK / 模拟器，不假报性能达标 |

**统计口径写错不会崩溃、只会算错数**，所以本阶段特别依赖
纯 JVM 单测 + `check_stat_rules.py` 双保险，而不是「编译通过就算过」。

### CI 失败时的回退顺序（每次只改一处、单独推送）

1. `drawArc` / `drawLine` / `Path` 的 `DrawScope` 签名或 `Style` 参数差异 →
   按编译错误补 `Stroke(width = ...)` 与 `@OptIn`
2. `pointerInput` / `detectTapGestures` 的 `Offset` 坐标换算 →
   简化为「图例列表点击」而非扇区点击（功能不丢，交互降级）
3. Room 不接受新增的 `GROUP BY` 列名 → 对齐 `CategoryTotalRow` 的 `AS` 别名
4. `Color(argb: Long)` 构造重载不匹配 → 改用 `Color(argb.toULong())` 或
   `Color(red, green, blue)` 三通道构造
5. `check_stat_rules.py` 新 marker 的 `@Query` 回溯取错 → 确认函数名前缀未撞已有 marker
6. 单测里 `MonthRange.of` 依赖 `java.time.YearMonth`（纯 JVM 可用）→
   若 JVM 版本不支持则改用查表法

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 不建 change 直接写统计页 | 少 4 个文档 | MODIFIED 收口、Canvas 取舍、口径决策会失溯源；违反流程 |
| 引入 Vico 画三种图 | 少写 Canvas 代码 | `project.md` 永久不做；Compose API 跨版本变动大；会新增依赖 |
| `drawText` + `rememberTextMeasurer` 画刻度 | 图表全在一个 Canvas 里 | Compose 版本相关 API；用普通 `Text` 叠加零风险 |
| 在 Canvas 里直接查 DAO | 少一层 ViewModel | 违反分层、重组即查库；纯 JVM 数据层不可测 |
| 把 `PagingData` 放进 `StatsUiState` | 下钻看起来「统一」 | Paging 3 已知反模式；下钻用一次性 LIMIT 查询即可 |
| 下钻加载该分类全部流水 | 实现最短 | 违反性能铁律；复用 `pagedFiltered` + LIMIT 100 |
| `records.size` / Kotlin 内存分组做统计 | 不用写 SQL | 项目明确禁止；且已加载数会随分页跳变 |
| 对 `timestamp` 做 `strftime` 分组 | 不用冗余列 | 索引失效 + 时区串月；已有 `localDate` / `yearMonth` |
| 一次性把阶段 5 也做掉 | 进度看起来快 | 范围蔓延是个人项目烂尾首因；v1 收敛更值钱 |
| 年度用分组柱状图 | 单月绝对值直观 | 12 组 × 2 柱刻度挤；使用者原话是「曲线变化」，双折线更贴 |
| 先做环形图、折线留到 4b | 少一轮编译风险 | 统计页短期仍是半成品；三种图复用同一数据层，一次做完成本更低 |
| 只追加 ADDED、不改统计页占位 Requirement | 归档脚本更简单 | specs 会同时存在「统计页是占位」与「统计页有报表」两条矛盾要求 |
| 新增 Gradle 依赖做颜色解析 | 省十几行 | 零依赖是隐私基线的一部分；手写 `#RRGGBB` 解析即可 |

## 取舍

选择 **「零结构变更 + 复用既有 DAO 聚合 + 原生 Canvas + 纯 JVM 数据准备层 +
1 份 MODIFIED 收口」**：

- **牺牲**：Canvas 自绘代码量比图表库大；扇区点击降级路径需要预案；
  真机性能仍未实测；统计页不含导出 / 偏好设置
- **换取**：不写 Migration、不改备份格式、不新增依赖、导航零改动；
  统计口径错误由纯 JVM 单测 + 静态检查拦住；
  「统计页占位」这条反复复发的规格债被彻底终结

## 实现期补充（如实记录与写作稿的偏离）

写作稿说「最多补一条 `WHERE rootCategoryId = :rootId GROUP BY categoryId` 查询」，
实际落地是 **4 条只读查询**，都是被真实需求逼出来的，没有一条是范围蔓延：

| 查询 | 为什么必须有 |
|---|---|
| `subcategoryTotalsUnderRoot(fromYm, toYm, rootId)` | 一级扇区下钻。改成 `yearMonth BETWEEN` 范围参数后，**月度与年度视图共用一条**，比写作稿的单月版本更省 |
| `rootTotalsInRange(fromYm, toYm)` | 年度视图也要展示一级分类占比，而 `expenseByRoot(ym)` 只按月 |
| `categoryTotalsInRange(fromYm, toYm)` | 同上，二级口径 |
| `drillDownRows(fromYm, toYm, categoryId, limit)` | 下钻明细 |

**为什么下钻明细没复用 `pagedFiltered`**：写作稿说「复用阶段 2 组合筛选 + 分页」，
实际读代码发现 `pagedFiltered` 是**明细页筛选查询，不带 `excludeFromStats = 0`**，
而统计页进来的明细 MUST NOT 显示「不计入收支」的流水。
若在 Kotlin 里对结果再过滤，就成了「全量加载后过滤」的变体。
所以新增一条带**唯一统计口径 + LIMIT + LEFT JOIN 取分类名**的只读查询，
一次查询、最多 100 行。这是**有意偏离**，不是漏看 spec。

**三条小偏离**：

1. `FlowRow`（`ExperimentalLayoutApi`）→ 普通 `Column` + `chunked(2)` 两列 `Row`。
   理由：图例不需要实验性布局，去掉一个版本风险点。
2. `Color(argb: Long)` → `Color(argb.toInt())`。
   Compose 的 `Color` 只有 `Color(Int)` 构造（`LedgerColors` 已在用），
   ARGB Long 取低 32 位即正确位模式；写作稿回退顺序第 4 条提前兑现。
3. 年度 `monthlyTrend(from,to)` 从「调两次」改成取一次存 `yearTrend` 复用，
   少一次范围聚合。

**`check_stat_rules.py` 的统计清单从 6 扩到 9**（4 条新查询里
`subcategoryTotalsUnderRoot` 已在批 A 登记，这里补 3 条）；
`check_transaction_annotations.py` 的 `WRITE_METHODS` **保持 13 个不变**——
阶段 4 不新增任何写方法，只读查询不该被误登记进事务清单。

## 首轮 CI 实测：一次全绿（本项目首次）

**commit `7e4ad2433381` / run `43347923787`**：
五个静态检查、keystore 校验、单元测试、`assembleDebug`、
`assembleRelease`（含 `lintVital`）、签名诊断、制品上传**全部 success**，
产出 `offline-ledger-debug-apk` **22,712,565 bytes**（阶段 3 为 22,618,116 bytes）。

这是阶段 0 以来**第一次首轮 CI 没有任何编译错误、也没有测试常数写错**。
对比：

| 阶段 | 首轮结果 |
|---|---|
| 0 | 挂在 `keytool -printcert -jarfile` + `bash -e` 的 grep 返回码（非编译问题） |
| 1 | 单测净资产期望值手写成 147000（应为 247000） |
| 2 | 4 个 import / 嵌套类引用错误 + 测试维度数写成 8 |
| 3 | 测试构造 `LedgerSnapshot` 漏传 3 个必填参数；**主源码零编译错误** |
| **4** | **首轮全绿，主源码与测试均零错误** |

**为什么这次能做到**（值得固化的做法）：

1. 写作前**实际读了** `RecordDao` / `CategoryDao` / `MonthRange` / `LedgerDi` /
   `LedgerUiParts` 的现有定义，而不是凭印象写 API
2. 写作稿里预判的 4 个 Canvas 风险点在**落地前就绕开**：
   `FlowRow(ExperimentalLayoutApi)` → `Column + chunked(2)`；
   `Color(argb: Long)` → `Color(argb.toInt())`；
   `toSlices` 两个 `List` 重载 → 改名 `toDrillSlices`；
   非空 `categoryId` 上多余的 `?.let` → 直接 `to`
3. `check_kotlin_strings.py` 与逐文件字符串复核在推送前各跑一遍，
   三引号 SQL 块未闭合检查为 0
4. 纯 JVM `ChartDataTest` 的期望值全部按函数定义推出来，
   没有手写一个凭印象的常数（阶段 1 / 2 都栽在这里）

**注意**：这不等于真机验证。5 万条切月 / 年报聚合耗时、滚动掉帧、
SQLCipher 真机打开数据库仍未实测，见第 6 节测试边界。

## 风险

**残余风险 1**：`DrawScope.drawArc` 的 `startAngle` / `sweepAngle` 方向、
`useCenter` 与 `Stroke`/`Fill` 样式的 API 签名与预期不同。
**缓解**：第 6 节回退顺序第 1 条；扇区角度计算在纯 JVM 单测里先验证，
UI 只消费角度，不在 Composable 里重算。

**残余风险 2**：`pointerInput` 的坐标是相对 Canvas 左上角的 px，
与圆心、半径的换算涉及 `density`。
**缓解**：命中函数接收「相对圆心的归一化角度」，换算集中在 Composable 一处；
若仍不稳，降级为「图例列表点击」——下钻能力不丢。

**残余风险 3**：新增查询的 `GROUP BY categoryId` 在 `rootCategoryId` 有 NULL 的
历史流水上会漏行（旧备份无 `rootCategoryId`）。
**缓解**：`BackupAccount` 式兼容在阶段 1 已做；本阶段下钻查询按 rootId 过滤，
NULL 根的历史流水不会出现在任何一级扇区，与 `expenseByRoot` 行为一致，
不产生「合计对不上」的假警告。

**残余风险 4**：合计一致性警告可能因「一级直记」与「二级记账」混合而误报。
**缓解**：`rootCategoryId` 在阶段 1 已由 `CategoryTree.resolveRootId` 冗余，
一级直记时指向自身；`checkTotals` 只比对三个 `SUM`，不关心层级混合。

**残余风险 5**：`ChartData.normalize` 用 `Float` 做比例换算，
可能被误读为违反「绝对禁止浮点数」。
**缓解**：第 1 节已明确边界——金额的存储 / 求和 / 比较全是 `Long`，
Float 只用于几何换算且不回写；`ChartDataTest` 里金额断言全用 `Long`。

## 验证

- [x] 阶段 0 五项对齐结论齐全、分级判定已告知并等确认
- [x] 阶段 3 已按规范归档，无进行中 change 冲突
- [x] 1 份 MODIFIED 写出完整替换文本，标题与原 Requirement 一致，不含阶段版本号
- [x] Delta 每个 Requirement 至少一个 GIVEN / WHEN / THEN 场景 + 「理由」
- [x] CI 全绿：run 43347923787（commit 7e4ad2433381）**首轮 success**，
  五个静态检查 / keystore 校验 / 单测 / debug APK / release(lintVital) / 签名诊断 / 制品上传 全部 success
- [x] 推送后用远端 Git Trees API 逐文件比对 blob sha，PASS
- [x] 产物 offline-ledger-debug-apk 22,712,565 bytes
- [ ] 推送后用远端 Git Trees API 逐文件比对 sha
- [ ] 使用者确认后归档本 change
