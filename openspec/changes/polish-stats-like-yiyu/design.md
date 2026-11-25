# 设计：统计页一羽式重排（第 68 批 → 第 69 批修订）

> **本文件已按第 69 批逐段撤回第 68 批的三处跑偏判断。**
> 第 68 批 design 里「蓝底身份区 + 双 Tab」「分类 / 收款方 / 账户合体卡」
> 「三个维度共用下钻跳明细 Tab」三节整体作废，以本文件为准。

## 1. 第 68 批错在哪（先认账，再改）

| 第 68 批做法 | 使用者反馈 | 第 69 批 |
|---|---|---|
| 新增 `StatsHeader` 蓝底 +「分析 / 概览」双 Tab | 「上面的月份之类切换我们已经有了，还支持月度/年度/多年，新增蓝底那块是多余的」 | **整块删除** `StatsHeader` 与 `statsTab`，回到白底 `headlineMedium「统计」` + 既有 `PeriodSwitcher` / `CurrencySwitcher` |
| 分类 / 收款方 / 账户三维度 chip 全塞进一张 `CategoryInsightCard` | 「这两个不是分类，混在一起不清晰」 | 分类占比卡**只做分类**；收款方 / 账户退回独立默认折叠卡 |
| 一级行「⌄」内联展开二级 + 点扇区 `navigate("detail")` | 「要看小类直接左上角切换小类」「最开始意图是参考明细页的方式进行展示，不用直接跳转明细页」 | 删「⌄」；左上角「大类 ⇄ / 小类 ⇄」；点行弹**底部 sheet** 按明细行渲染，三个跨 Tab 回调全删 |

**方法论订正**（活文档 §7.51）：第 68 批撤回 §7.3「合并=一屏硬塞」的外推是
对的，但随即又犯了一次同类错——把「一羽有蓝顶栏 / 有分类聚合页 / 有 Tab」
当成「我们也要加」，而没有核对我们已有的周期切换能力。借鉴竞品 MUST 先清点
「我们已有什么」，再决定「借什么」，MUST NOT 把对方有而我们没有的控件当成
待办清单。

## 2. 现状核查（代码事实）

- `StatsScreen.kt`：第 68 批后是 `StatsHeader`（蓝底 + TabRow）+ 小结 /
  趋势 / `CategoryInsightCard`（三维度）/ 资产四卡。
- `DonutChart.kt`：原生 Canvas `drawArc` + 一羽式图例列表
  （`CategoryGlyph` 彩色图标 / 名称 / 百分比 / ¥金额），`StatsLegend` 纯 JVM。
- `StatsViewModel`：`rootSlices` / `childSlices` / `topCounterparties` /
  `topAccounts` / `rankChildren` / `drillRows` 全在，
  `openRoot` / `openCategory` / `closeDrill` 与带 `LIMIT 100` 的
  `drillDownRowsForCurrency` 完整保留 —— **第 28 批改跳明细 Tab 后没人调用，
  但组件和 SQL 都还在**，Q4B 直接复用，不重写数据层。
- `DrillDownDialog.kt`：老的居中 `Dialog`，行是「日期 · 分类 + 备注 + 金额」，
  且带两个「去明细」按钮（正是 Q6A 要删的耦合出口）。
- `RecordDao.DrillRow`：只有 `id / type / amountCents / localDate / note /
  categoryName` 六列，缺账户名、卡号、描述、收款方、附加金额、时间。
- `RecordRowCard`（`DetailScreen.kt` L550）：方向箭头圆 + 描述/收款方主标题 +
  「分类 · 收款方 · 备注」副行 + `accountLabel` 带卡号 + 划线原价与优惠标签。
- `LedgerTheme.kt`：只设 `primary = LedgerColors.Primary`（深绿 `0xFF1B5E20`）
  / `secondary`，其余全走 M3 默认 `lightColorScheme` → `Card` 落在
  `surfaceContainerLow`，就是「灰不拉几」的根因。全 App 23 个文件含
  `Card` / `.background`。

## 3. Q1A + Q2A：分类占比卡（只做分类）

```
┌ [按大类 ⇄]                              分类管理 › ┐
│            ┌── 净支出 ──┐                        │
│            │  ¥510.00  │    ← 环，中心统一净支出    │
│            └───────────┘                        │
│ 🍴 餐饮                    43.73%   ¥223.00    │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░  (灰色长条)         │
│ 🚗 交通                    18.63%    ¥95.00    │
│ ▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░                  │
│ ...                                            │
└───────────────────────────────────────────────┘
```

- **左上角单个标签**：显示当前口径「按大类 ⇄」/「按小类 ⇄」（一羽图 2/3
  就是「小类 ⇌」「大类 ⇌」），点一下 `vm.setSliceLevel` 来回切。
  MUST NOT 再有两枚 FilterChip，MUST NOT 有一级「⌄」展开。
- **环**：分类维度沿用 `rootSlices` / `childSlices`，
  中心统一「净支出 ¥X」，不再有 Top5 冒充净支出的分支。
- **图例列表**：`sectors` 顺序 = 金额降序，每行整行可点 ≥40dp，
  含彩色 `CategoryGlyph`（缺项回退中性圆）、名称、百分比、`¥金额`、
  **灰色长条**（Q2A：浅灰轨道 `#EDEFF5` + 低饱和蓝 `#5B7CFA` 填充，
  宽度 = `StatsLegend.percent`，纯显示换算不回写金额）。
  百分比 / 金额 / 灰条三者 SHALL 同源于 `row.percent`。
- **点行**：打开页内底部 sheet（见 §5），MUST NOT `navigate("detail")`。
- 「分类管理 ›」仍在标题右侧；合计一致性警告仍在 `StatsScreen` 顶层。

## 4. Q3A：收款方 / 账户独立卡、默认折叠

`OtherRankCard` 一张卡，默认 `collapsed = true`，标题行 = 左上角单个
「收款方 ⇄ 账户」标签 + 右侧「展开 / 收起」。展开后列出 Top5：
中性圆 + 首字 / 名称（账户带卡号，沿用 `accountBaseLabel`）/ ¥金额 /
灰色长条。**只读展示，无下钻、无跳转**（Q6A 彻底解耦；一羽也没在排行里
给收款方做跳转）。两个维度共用同一份 `state.topCounterparties` /
`state.topAccounts`，零新增 DAO。

## 5. Q4B + Q6A：底部 sheet 按明细行渲染，删跨 Tab

### 5.1 数据层（动 DAO 投影，如实登记）

`RecordDao.DrillRow` 扩为明细行所需字段，**全部带默认值**，
旧调用点零改动即可编译：

```
id, type, amountCents, timestamp, localDate, note, categoryName,
categoryColorHex, counterparty, description,
fromAccountName, fromAccountLast4, toAccountName, toAccountLast4,
discountCents, feeCents
```

两条既有 `@Query` 扩 `LEFT JOIN accounts fa/ta` 并 SELECT 新列，
**`type IN ('EXPENSE','INCOME') AND excludeFromStats = 0` 唯一口径与
`LIMIT :limit` 一字不动**；新增一条同口径的
`drillDownRowsForRootCurrency`（`WHERE r.rootCategoryId = :rootId`），
供「按大类」点一级时查该大类下全部流水。
`check_stat_rules.py` 的 marker 清单加这一条，确保它也带唯一口径。

`StatsViewModel.openSliceRows(categoryId, isRoot, displayName)`：
按 `isRoot` 选上面两条查询之一，`LIMIT 100`，填 `drillRows` +
`drillCategoryId` + `drillCategoryName`。`openRoot` / `openCategory` /
`backToChildren` / `closeDrill` 保留（不删别处可能引用），
但 UI 不再调用 `openRoot` 的二级构成弹窗。

### 5.2 UI 层

`DrillDownDialog.kt` 保留文件路径，内容改为
`@OptIn(ExperimentalMaterial3Api::class) fun StatsDrillSheet(...)`：
`ModalBottomSheet` + `LazyColumn`，每行**逐项对齐 `RecordRowCard`**——
方向箭头圆（←/→/⇄/↩）、描述→收款方→分类的主标题、
「分类 · 收款方 · 备注」副行、`accountLabel` 带卡号的账户名、
红/绿金额、划线原价 + 蓝/灰「优惠 / 手续费」标签、
`MonthRange.dateLabel(localDate)` 日期。
标题「{分类名} · 流水」，副标题「最多 100 条，按日期倒序」。
**删掉**旧弹窗里「查看该大类全部流水 ›」「明细」两个跨 Tab 按钮。

### 5.3 解耦（Q6A）

`StatsScreen` 签名删 `onOpenCategoryInDetail` /
`onOpenCounterpartyInDetail` / `onOpenAccountInDetail`，只留
`onOpenCategories`；`LedgerApp` 删三块 `detailVm.filterBy* +
navigate("detail")` lambda。`DetailViewModel` 的三个方法**保留不删**
（`detail-filter-and-transfer-edit` / `detail-scope-and-record-page`
两个活跃 change 正在改明细侧，不碰它们），但统计侧不再注入状态，
§7.26 / §7.29 / §7.30 那套「来自统计」chip / 双周期源 / 默认值扩散
不再被触发。

需 MODIFIED 的基线：`category-stats`「三级下钻路径」与
「下钻明细复用组合筛选且不全量加载」——落点从「跳明细 Tab +
组合筛选 chip」改为「页内 sheet + 带 LIMIT 的既有 SQL」，
不是删能力，是换承载且顺手消除耦合。

## 6. Q5B：全局清新配色（改动面最大，如实登记）

使用者明确选 B（全 App 一起变），不做局部 `StatsPalette`。

`LedgerTheme.kt` 的 `lightColorScheme` 补齐一羽式浅色体系：

| 槽位 | 值 | 作用 |
|---|---|---|
| `background` / `surfaceContainer*` | `#F4F6FB` / `#FFFFFF` | 页面浅蓝灰底、卡片纯白 |
| `surface` / `surfaceVariant` | `#FFFFFF` / `#EEF1F8` | 明细页背景、次级块 |
| `onSurface` / `onSurfaceVariant` | `#1F2430` / `#6B7280` | 柔和深灰蓝，不用纯黑 |
| `outlineVariant` | `#DDE2EE` | 分割线 |
| `primary` / `onPrimary` | `#5B7CFA` / 白 | 与 `CategoryHeaderBlue` 同系，Tab / 按钮 / 进度 |

`LedgerColors.Primary` 同步改为 `#5B7CFA` 保持单一真相源。
`ExpenseRed`（红 = 支出）/ `IncomeGreen`（绿 = 收入）与
`applyReverseColors` 反转开关 **一字不动**；`CategoryHeaderBlue` 常量不动。
`AssetsScreen` / 分类管理三页等显式 `Color(0xFFF5F6FA)` / `Color.White`
硬编码与新体系同色系，无需逐个改。

**风险如实登记**：全 23 个含 `Card` / `.background` 的文件观感都会变，
`DetailScreen` 的 `surfaceVariant.copy(alpha=0.45f)`、
`FilterSheet` 的 `outlineVariant` 都会跟着变浅。
本批只改色值、不改任何布局 / 手势 / 数据；CI 全绿不替代全 App 观感真机验。
记一笔 / 分类管理 / 资产页三处已有的蓝底身份区与新 primary 同系，不会撞色。

## 7. 规格改法：10 条 MODIFIED 整块替换 + 4 条 ADDED

| spec | Requirement | 第 69 批改什么 |
|---|---|---|
| reporting-ui | 统计页落地月年切换与三种图表 | 删蓝底/双 Tab；卡片层级改「白底标题 → 小结 → 趋势 → 分类占比 → 收款方账户折叠卡 →（年度）资产」 |
| reporting-ui | 分类占比卡提供分类管理短跳转 | 「合体卡」改「分类占比卡」，入口保留 |
| reporting-ui | 分类全量选择器采用分组 chips 且不用下拉框 | 措辞「合体卡」→「分类占比卡」，统计图例彩色口径不变 |
| category-stats | 环形图支持一级与二级切换并落地三级下钻 | 删三维度收进合体卡 / 一级⌄展开；改左上角单标签 + 页内 sheet |
| category-stats | 分类图标不做字符串映射 | 措辞同步，彩色图例口径不变 |
| category-stats | 分类管理彩色图标目录与缺项兜底 | 措辞同步，彩色图例口径不变 |
| category-stats | 三级下钻路径 | 落点从跳明细 Tab 改页内 sheet |
| category-stats | 下钻明细复用组合筛选且不全量加载 | 删「跳明细 Tab + 组合筛选 chip + Activity 作用域」，改「页内 sheet + 既有 LIMIT SQL」 |
| reporting-ui | 图表点数值常驻显示 | 不动（保留） |
| category-stats | 分类支出占比按净支出计算 | 不动（保留） |

> 后两条本可直接不动，但「统计页落地月年切换…」整块替换会覆盖卡片层级描述，
> 为避免与「两张卡 MUST NOT 合并」历史措辞并存，一并列为 MODIFIED 锚点复核。
> 实际 delta 里按最小必要原则只写前 8 条（后两条仅在静态检查里断言未被改动）。

ADDED 4 条：分类占比卡一羽式图例（含灰条）、收款方账户默认折叠只读卡、
分类流水页内底部 sheet、全局清新配色。
**删除**第 68 批 ADDED「统计页蓝底身份区与分析概览双 Tab」。

## 8. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 删跨 Tab 回调漏改某处 | 静态脚本断言 `LedgerApp` 无 `filterBy*` + `StatsScreen` 签名只剩 `onOpenCategories` |
| Q4B 新 SQL 漏唯一口径或 LIMIT | `check_stat_rules.py` 加 marker；SQL 照抄既有 `drillDownRowsForCurrency` 改 WHERE |
| Room 投影列名不匹配 | `DrillRow` 新字段全带默认值；两条 SQL 都补齐列；CI 编译兜底 |
| `ModalBottomSheet` 实验性 API | `@OptIn(ExperimentalMaterial3Api::class)`，BOM 2026.06 已含 |
| Q5B 全 App 变色翻车 | 只改色值不改布局；红绿语义与反转开关不动；§8 新增全 App 观感待验 |
| 合卡/拆卡又丢警告或短跳转 | 静态脚本断言「合计不一致」与「分类管理 ›」仍在 |
| 覆盖安装 | `version = 7` 不变、`Migrations.ALL` 空不变，零改表 |

## 9. 落盘清单

| 层 | 文件 |
|---|---|
| 主题 | `ui/theme/LedgerTheme.kt` |
| DAO | `data/local/dao/RecordDao.kt`（`DrillRow` + 3 条查询） |
| VM | `ui/stats/StatsViewModel.kt`（新增 `openSliceRows`） |
| UI 主体 | `ui/stats/StatsScreen.kt` |
| 图例 | `ui/charts/DonutChart.kt` |
| 页内 sheet | `ui/stats/DrillDownDialog.kt`（内容重写为 `StatsDrillSheet`） |
| 解耦 | `ui/LedgerApp.kt` |
| 版本 | `app/build.gradle.kts` → 0.2.49 |
| 静态检查 | `scripts/check_stat_rules.py` / `check_stats_merge.py`（重写） |
| 文档 | 活文档 §7.51、`openspec/project.md` 第 69 批 |

`ChartData` / `InsightData` / `StatsDrillRange` / `StatsLegend` /
`RecordEntity` / `AppDatabase` **零改动**。

---

## 10. 第 70 批修订：分类图三条观感优化

> 本节只改分类占比卡的呈现与全局色值；第 69 批的撤回结论、Q6A 解耦、
> 页内 sheet、折叠只读卡全部保留。

### 10.1 标题：文本与图标分离

第 69 批 Q1A 做成「`FilterChip` 里写『按大类 ⇄』」——文字和切换符号挤在一个
chip 里，既不像一羽也不简洁。一羽图 1/2/3 是 `▌大类 ⇌` / `▌小类 ⇌`：
**蓝色竖条 + 粗体纯文本 + 旁边一个独立的 ⇄ 图标**。本批照此拆成三件套：

```
Row {
  Box(3dp × 16dp, CategoryHeaderBlue, 2dp 圆角)      // 竖条
  Text("大类" / "小类", titleMedium, Bold)            // 纯文本，无 ⇄
  IconButton(size 32dp) { SwitchLevelIcon() }         // 独立图标按钮
}
```

`SwitchLevelIcon` 是 `Canvas` 自绘的两条反向箭头（上→ / 下←），
零新增 Gradle 依赖、零 drawable 资源、零 `Icons.*` 版本风险。
右上角「分类管理 ›」保留。**截图里那个粉色「支出 ⇌」按钮不加**：
使用者没提，而且第 68 批刚因为「把对方有的控件全搬过来」返工过一次，
这次严格只做列出的三条。同理，截图里的蓝底 TopBar /「分析 / 概览」Tab
也不加回——那是第 69 批明确删掉的。

### 10.2 配色：找到「灰不拉几」的真正根因

第 69 批把 `lightColorScheme` 补齐了，但真机仍觉得「主体不突出、边界不清晰」。
核查发现两层原因：

1. `background = F4F6FB` 太浅且偏蓝，与白卡对比不足 →
   改**中性浅灰** `F2F3F7`，与项目既有 `CategoryManagerDialog` /
   `AssetsScreen` 的 `F2F3F7` / `F5F6FA` 同系，不新造一套灰。
   `surfaceContainer*` 全部收成纯白 / 近白，`surfaceVariant` / `outlineVariant`
   同步去掉蓝味。`MainActivity` 已有全屏 `Surface`，`background` 确实生效，
   不是「没包 Surface」的问题。
2. **M3 `Card` 默认带 1.dp `tonalElevation`**，会把 `containerColor`
   与 primary 混合，纯白被染成淡蓝灰 —— 这才是「灰卡」的根因。
   本批新增统计页统一出口 `StatsCard`：
   `colors = CardDefaults.cardColors(containerColor = Color.White)` +
   `elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)` +
   `RoundedCornerShape(16.dp)`，7 处 `Card(` 全量替换。
   靠「中性灰底 + 纯白卡 + vertical 8dp gap + 圆角」形成清晰边界，
   MUST NOT 新增阴影 / 描边（基线「明细页极简网格」禁用）。

`ExpenseRed` / `IncomeGreen` / `applyReverseColors` / `CategoryHeaderBlue`
一字不动。全 23 个含 Card / background 的文件观感变化继续如实登记。

### 10.3 环外指示线：撤回两次「明确未做」，但不碰禁用 API

第 68/69 批两次把「环外悬浮百分比」列为**明确未做**，理由是
`DonutChart` 文件头禁用 `DrawScope.drawText` / `TextMeasurer`。
当时我把 `BoxWithConstraints` 也一并归为「版本相关 API」而回避，
其实是把「不想做定位计算」写成了「能力不允许」——
**第三次同类外推**（§7.37 / §7.50 / §7.51 之后）。使用者这轮明确要指示线，
本批撤回那条结论，用完全合规的方式实现：

```
var boxSize by remember { mutableStateOf(IntSize.Zero) }
Box(Modifier.height(250.dp).onSizeChanged { boxSize = it }) {
    // ① guides：Composable 层按 boxSize 算，Canvas 与 Text 共用
    //    mid = startAngle + sweepAngle/2；按 cos(mid) 分左右
    //    p1 = 环外缘点；p2 = 沿角再出 14dp；endX = 左右边缘 6dp
    //    同侧按 y 排序、间隔 <22dp 时下推，避免叠线
    // ② Canvas：drawArc 画环 + drawLine 斜线/水平线 + drawCircle 端点
    // ③ 普通 Text：align(TopStart/TopEnd) + absoluteOffset { IntOffset(...) }
    //    直接用 g.y 定位，颜色 = 扇区色，文案「名称 百分比」
}
```

- 环半径 `min(cx,cy) * 0.78f` → `0.60f`，给左右各约 90dp 文字留空间；
  `thickness` 与 pointerInput 命中半径同步取 `0.60f`，MUST NOT 出现
  「画得小、点得大」或反之。
- 折线末端 y 用**去重叠后**的 `g.y`，线与文字严格对齐。
- 空态 `sectors.isEmpty()` → `guides` 为空，只画灰环、不画线。
- `guides` 在 Composable 层算完再传给 Canvas，**MUST NOT 在 DrawScope 内
  回写 state**（会引发重组循环）。

用到的全是基础 API：`onSizeChanged` / `absoluteOffset` / `drawLine` /
`drawCircle`。零 `drawText`、零 `TextMeasurer`、零 `FlowRow`、零实验性注解。

### 10.4 明确未动

图例灰条结构、收款方 / 账户折叠卡的「收款方 ⇄ / 账户 ⇄」标签
（使用者说的是「分类图」，不越界改别的卡）、页内 sheet 行样式、
页内下钻 SQL、统计口径、`version = 7`、`Migrations.ALL`、零 `INTERNET`。

### 10.5 落盘清单（第 70 批）

| 层 | 文件 |
|---|---|
| 环 + 指示线 | `ui/charts/DonutChart.kt` |
| 标题 + 卡片出口 | `ui/stats/StatsScreen.kt` |
| 全局色值 | `ui/theme/LedgerTheme.kt` |
| 版本 | `app/build.gradle.kts` → 0.2.50 |
| 静态检查 | `scripts/check_stats_merge.py` |
| 四件套 | 本目录（proposal / design / tasks + 两份 delta spec） |
| 文档 | 活文档 §7.52、`openspec/project.md` 第 70 批 |
