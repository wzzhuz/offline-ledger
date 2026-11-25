# 设计：统计页一羽式重排（第 68 批）

## 1. 现状核查（以代码为准，不照抄参考图当现状）

- `StatsScreen.kt`：白底 `headlineMedium「统计」` + `PeriodSwitcher` +
  `CurrencySwitcher`；`InsightSummaryCard` / `TrendCard` / `DonutInsightCard` /
  `RankInsightCard` 四张独立 `Card` 依次排列。
- `DonutChart.kt`：原生 Canvas `drawArc` 环 + 两列纯文字图例（名称 + 金额），
  无百分比、无图标、无笔数；图例整行可点 ≥40dp（第 27 批）。
- `ChartData.ChartSlice`：`id / name / colorHex / cents`，**无 count**。
- `StatsViewModel`：`rootSlices / childSlices` 来自
  `expenseByRootForCurrency` / `expenseByCategoryForCurrency` 等既有只读聚合，
  经 `ChartData.netSlices` 扣 SETTLEMENT；`catItems` 的 `count` 恒为 0。
  收款方 / 账户排行的 `RankItem` 带 `cnt`，但那是另一套 Top5 查询。
- 一羽图 2/3 是**单卡内**「环 + 外部引导线百分比 + 下方图例列表（彩色图标 /
  名称 / 百分比 / ¥金额 / 笔数）」，不是两张卡，也不是互斥切换 chip。
- 一羽「大类 餐饮 ¥268(4条)」=「小类 餐饮 ¥223(3条) + 外卖 ¥45(1条)」，
  分母同为净支出 ¥510 —— 与本项目「分类支出占比按净支出计算」同口径。
  所以本批**只动 UI**。

## 2. Q1A：合体方案

新增单卡 `CategoryInsightCard`，删除独立 `RankInsightCard` 的挂载：

```
┌ 分类占比                              分类管理 › ┐
│ [分类][收款方][账户]        （按大类][按小类] 仅分类维度） │
│            ┌── 净支出 ──┐                       │
│            │  ¥510.00  │   ← 环，中心统一写净支出   │
│            └───────────┘                       │
│  🍴 餐饮    43.73%   ¥223.00                  │
│  🚗 交通    18.63%    ¥95.00                  │
│  🧴 日用品  17.45%    ¥89.00                  │
│  🛒 购物    11.37%    ¥58.00                  │
│  🛵 外卖     8.82%    ¥45.00                  │
└──────────────────────────────────────────────┘
```

- **环**：分类维度沿用 `rootSlices` / `childSlices`；收款方 / 账户维度把
  `topCounterparties` / `topAccounts` 转成 `ChartSlice`（`id` 用 RankRow.id，
  色板仍走 `ChartData.chartArgb(id, index)`），中心改为该维度标题而非「净支出」，
  避免把 Top5 合计误读成全账净支出。
- **图例列表**：`sectors` 顺序 = 金额降序（既有 `ChartData.sectors` 保序），
  每行整行可点 ≥40dp，共用 `onSectorClick` → 按维度分派到既有三类下钻回调。
  百分比 = `cents / total`，纯整数分算 Float 只进显示，不回写金额。
- **分类维度二级展开**：复用 `state.rankChildren`，一级行右侧「⌄」展开二级，
  二级行直接调 `onOpenCategoryInDetail(id, false, ...)`。
- **分类管理短跳转**仍在标题右侧；合计一致性警告仍在 `StatsScreen` 顶层。

**下钻契约四层（§7.12）逐项保住**：扇区点击、图例整行点击、排行各维度、
展开子项，全部走 `state.drillRange` + `state.currencyCode`；
`onOpenCategories` 不变；`StatsDrillRange` 零改动。

### 2.1 撤回 §7.3 的旧结论

活文档 §7.3 把第 13 批「用 `InsightMode` 把排行与环形合并到一张洞察卡、
互斥切换 chip」与使用者「查看统计页就不应该在一屏里硬塞」绑定，
写成「合并 = 一屏硬塞」。**本批撤回这条外推**：一羽式是同卡上下排布、
环与图例同时可见、允许纵向滚动；第 13 批被推翻的是「二选一切换 + 压一屏」，
不是「同一张卡承载一个结论」。§7.50 追加订正说明。

### 2.2 明确未做

- 图例「(N条)」：分类维度 `ChartSlice` 无 count，新增分类笔数聚合会撞基线
  「既有聚合 SQL 不改」，本批不显示笔数，不伪造。
- 环外悬浮百分比文字：`DonutChart` 文件头禁用 `DrawScope.drawText` /
  `TextMeasurer`，悬浮定位需 `BoxWithConstraints` + `onSizeChanged`，
  本批改由图例列表承载百分比，环保持原生 `drawArc`，不画孤立引导线。

## 3. Q2A：彩色图标范围再放宽一次

- 统计占比图例的分类维度用既有 `CategoryGlyph(name, iconName = "", colorHex = "",
  grayscale = false, size = 32, categoryId = id)`；`iconName` 为空时按预置 id
  回查 `CategoryIconKeys`，覆盖安装兜底自动生效，零写库。
- 目录缺项 / 自定义分类回退 `CategoryIcon` 中性圆 + 中文首字。
- 收款方 / 账户维度没有分类图标，仍用中性圆 + 首字（非「分类标识」）。
- `colorHex` 与 `ChartData.chartArgb` 扇区色板**一字不动**：图标 tint 走
  `CategoryIconTints`，扇区色走按 id 稳定分配的低饱和色板，两套管径分离。
- 需 MODIFIED 的 3 条基线：`reporting-ui`「分类全量选择器采用分组 chips
  且不用下拉框」、`category-stats`「分类图标不做字符串映射」、
  「分类管理彩色图标目录与缺项兜底」，把「统计页 SHALL 中性圆」逐处改为
  「统计页分类占比图例 SHALL 彩色 Tabler，其余页面仍中性圆」。

## 4. Q3A：统计页内其余改动

### 4.1 蓝底 TopBar + 分析 / 概览 Tab

- `StatsScreen` 顶部改蓝底 `Box`（复用 `CategoryHeaderBlue = 0xFF5B7CFA`，
  不新造颜色），左侧「统计」+ 周期标签，右侧周期左右箭头与「ⓘ 口径」入口。
- 「分析 / 概览」两个**真实 Tab**：分析 = 小结 + 趋势 + 合体卡 +（年度）资产卡；
  概览 = 小结 + 合体卡（不重复趋势）。一羽「概览」页未提供截图，
  按「精简版分析」等价实现，design 如实登记，非 1:1。
- `PeriodSwitcher`（月度 / 年度 / 多年）与 `CurrencySwitcher` 保留，
  移到蓝底区下方紧凑行，零功能改动。

### 4.2 小结卡进度条化

收入 / 支出 / 结余三行对齐；支出行下加红色 `MiniBar`，
宽度 = `totalExpense / max(totalIncome, totalExpense)`（纯显示换算，不回写金额）；
保留「怎么算？」、日均、已报销 / 净支出、预算条。

### 4.3 趋势卡气泡与四项摘要

- 点选汇总从纯文字改为蓝色圆角 `Surface` 气泡（图下随选更新，
  MUST NOT 用 AlertDialog 遮挡图表）。
- 卡底四项摘要：**笔数（本周期总流水）/ 日均 / 单日最高 / 峰值日**，
  全部由 `state.recordCount` / `state.averageCents` / `state.daily` 派生，
  零新增 DAO。一羽按收入 / 支出分列笔数，我们只有总笔数，
  如实标注「本周期流水笔数（含收入），不新增聚合 SQL」。
- 既有常驻点数值、竖向引导线、横屏 `ChartFullscreenActivity` 零改动。

## 5. Q1A 规格改法：6 条 MODIFIED 整块替换

| spec | Requirement 标题 | 改什么 |
|---|---|---|
| reporting-ui | 统计页落地月年切换与三种图表 | 卡片顺序改为「蓝底 TopBar → 小结 → 趋势 → **占比与排行合体卡** →（年度）资产卡」；撤回「两张卡 MUST NOT 合并 / MUST NOT 为一屏合并」 |
| reporting-ui | 分类占比卡提供分类管理短跳转 | 「MUST NOT 让两类卡片合并」改为「合体卡 SHALL 保留本入口」 |
| reporting-ui | 分类全量选择器采用分组 chips 且不用下拉框 | 「统计页 SHALL 中性圆」改为「统计占比图例 SHALL 彩色 Tabler」 |
| category-stats | 环形图支持一级与二级切换并落地三级下钻 | 「排行卡与占比卡 MUST NOT 合并、三维度只在排行卡内部切换」改为「合体卡内维度切换」 |
| category-stats | 分类图标不做字符串映射 | Scenario「彩色图标只进分类选择组件」扩到统计占比图例 |
| category-stats | 分类管理彩色图标目录与缺项兜底 | Scenario「统计页仍是中性圆」改为「统计占比图例彩色」 |

另 2 条 ADDED：蓝底 TopBar 与双 Tab、合体卡一羽式图例列表。
每条 ≥1 Scenario、含 RFC2119 关键词，标题与既有 284 条零冲突。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 合卡顺手删下钻（§7.12） | 逐项对照四层契约；新增 `check_stats_merge.py` 静态断言三类回调与展开子项仍在 |
| 收款方 / 账户维度环合计 ≠ 净支出被误读 | 中心标题按维度变化，不写「净支出」；色板仍按 id 稳定 |
| 彩色图标越界到记一笔 | 只在合体卡图例调 `CategoryGlyph`；静态脚本断言其余页面零引用 |
| 蓝底 TopBar 遮挡周期切换 | 周期 / 币种 switcher 移到蓝底下独立紧凑行，真机待验 |
| Compose 编译 | 只用基础 API（Box/Column/Row/Canvas/Modifier），不引实验性 API；CI 兜底 |
| 覆盖安装 | `version = 7` 不变、`Migrations.ALL` 空不变，零改表 |

## 7. 与活跃 change 的交叉

- `detail-filter-and-transfer-edit`：不动统计下钻携带查看周期的实现。
- `detail-scope-and-record-page`：不动明细侧周期收敛。
- `add-discount-and-fee`：统计仍 `SUM(amountCents)` 按实付，附加金额不进统计。
- `ff3-description-field` / `record-category-picker-and-root-select` /
  `restructure-preset-categories`：本批不碰筛选规则、选择器、预置清单。

## 8. 落盘清单

| 层 | 文件 |
|---|---|
| UI 主体 | `ui/stats/StatsScreen.kt` |
| 环形图例 | `ui/charts/DonutChart.kt` |
| 版本 | `app/build.gradle.kts` → 0.2.48 |
| 静态检查 | `scripts/check_stats_merge.py`（新增） |
| 单测 | `app/src/test/.../StatsLegendTest.kt`（新增，纯 JVM） |
| 四件套 | 本目录 7 个文件 |
| 文档 | 活文档 §7.50、`openspec/project.md` 第 68 批 |

`StatsViewModel` / `ChartData` / `RecordDao` / `InsightData` **零改动**：
全部复用既有 state 字段与聚合结果。
