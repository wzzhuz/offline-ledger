# 设计说明 · 第 13 批

## 0. 根因（动手前先写，见指南 §6.1）

统计页「不好看」不是缺图，是**层级平铺**：`StatsScreen.kt` 一个 `verticalScroll(Column)` 里
所有区块同权重，概览卡只给三个数、环形图之后没有排行结论、两行口径灰字常驻、
预算与一致性警告各占一块、点数值还要弹 AlertDialog。
BeeCount 的 `analytics_content.dart` 用 `BeeSurface` 把每个结论装进独立卡，
`BeeAnalyticsSummary` 给大数字 + 笔数 + 可解释均值，`AnalyticsRanking` 给占比条 + 百分比 +
展开二级 + 点进 `CategoryDetailPage`。差距是**信息组织方式**，不是控件数量。

分类管理页第 12 批刚修好骨架（TopBar 固定 + `LazyColumn` + ⋯ 菜单 + 独立编辑视图），
所以本批**不动骨架**，只补三类信息：收支分段、每类笔数、点分类看流水。
`CategoryManagePage` 的 `_CategoryGridView` 网格卡片不照搬——它在 10 一级 + 20 二级时
会回到「卡片堆叠」；`CategoryDetailPage` 的「分类 → 流水」闭环值得借。

## 1. 实现结构

| 层 | 文件 | 做法 |
|---|---|---|
| DAO | `RecordDao.kt` | 4 条只读聚合，全部带唯一口径 + 币种，返回小 row 类；不新增表 |
| 纯 JVM | `engine/InsightData.kt`（新） | 笔数 / 日均月均 / Top 占比 / 一级笔数含二级累加，全部可单测 |
| VM | `StatsViewModel.kt` | state 加 `recordCount / topCounterparties / topAccounts / averageCents`，`reload()` 一次填齐 |
| UI | `StatsScreen.kt`（重写） | 固定顶部 → 总结卡 → 分类洞察卡 → 趋势卡 → 年度资产卡 → 口径入口；⚠ 警告置顶 |
| 偏好 | `LedgerPrefs.kt` / `LedgerUiParts.kt` / `MainActivity.kt` | `hideAmounts` + 全局可观察 `AmountMask.hidden`（`mutableStateOf`） + `formatCents` 单入口遮蔽 |
| 分类 | `CategoryManagerDialog.kt` / `SettingsViewModel.kt` | 分段 chips、笔数、点行下钻 Dialog（复用 `DrillRow` + LIMIT） |
| 导航 | `LedgerApp.kt` | `StatsScreen(onOpenCategories = { navigate("categories") })`，不新增路由 |
| 检查 | `scripts/check_stat_rules.py` | marker 清单加 4 条新查询 |
| 测试 | `app/src/test/.../InsightDataTest.kt` | 纯 JVM：均值、占比、Top 排序、笔数累加、空数据 |

## 2. 考虑过的其他方案（含否决理由）

| 方案 | 否决理由 |
|---|---|
| 引入图表库 / 设计系统包 | 零新增依赖基线；Compose BOM 跨版本签名风险；原生 Canvas 已够 |
| 统计页一屏塞 6 张卡 | 重犯筛选面板「滑两屏」；改为 4 张卡 + 口径收纳 + 空态引导 |
| 隐私模式用普通全局 `var enabled` | Compose 不重组；改用 `mutableStateOf`，组合期间读取会被 snapshot 记录 |
| 把 `formatCents` 改成 composable 全局替换 | 它在 `values.map{}` 等非 `@Composable` lambda 里被调用，会编译失败；改为在 `formatCents` 单入口读 `AmountMask.hidden`，零调用点改动 |
| 分类详情复用 `DrillDownDialog` | 它绑定 `StatsUiState`；改为管理页内独立 AlertDialog + 新 DAO 查询，避免耦合 |
| 分类笔数每条单独查 `linkedRecordCount` | O(n) 查询；改一条 `GROUP BY` 返回 Map |
| 照搬 `_CategoryGridView` 网格 / 线性图标目录 | 卡片堆叠回退 / 与中性单色圆冲突 |
| 平均值口径改成「按有流水的天数」 | 会与 BeeCount 的「总额 ÷ 周期天数」语义不一致且需解释分母；保持自然日/月数并在弹窗写清 |
| 多币种折算后给单一净资产 | 6c-1 使用者已拍板严格不混算 |

## 3. 范围决策（显式写，避免下批把有意保留的当漏项）

- **保留**：环形图与一级/二级下钻、双折线与 TrendMetric、资产累计推算、横屏全屏页、
  点数值 AlertDialog、ChartZoomButton、`ChartData` 全部方法、Top5 之外的分类仍走环形图下钻
- **保留**：操作型浮层 10 处浮动 `label`（第 12 批已显式决策，见 `design.md` §2.1）；
  本批顺手把「偏好」页那 1 处设置类浮动 `label` 改 `placeholder`，属老写法同步
- **不新增**：标签表、热力图、分类迁移、每月起始日、本地图片凭证、年度报告海报
  （第二批可选，需单独拍板）

## 4. 三级回退顺序（首轮 CI 若红，每次只改一处单独推送）

1. 新增 DAO 查询若 Room 解析失败 → 先简化为 `COUNT(*)` / 单列 `GROUP BY`，去掉 JOIN
2. `AmountMask.hidden` 若重组或 import 报错 → 退回只在 `StatsScreen` 内用局部遮蔽变量，
   其余页面暂不遮蔽（数据不变，功能降级但可编译）
3. 统计页重写若尺寸/引用错误 → 保留旧 `StatsScreen.kt` 的图表调用块，只把总结卡与排行卡
   作为新增 `Card` 插到 `OverviewCard` 之后，最小改动达成结论层

## 5. 实测回填

（推送后写：commit sha / push CI run id / release run id / 制品大小 / 首轮失败根因 / 真机未验清单继承）


## 实测结果

- push CI `44002929060` success（17 步）；release `44004138708` success（23 步，坚果云 completed success）
- 制品字节：APK 12,217,237 / mapping 3,476,907 / metrics 270
- 远端按最终 commit sha 逐文件校验 291↔291 PASS，无 `diag/` 残留、无 token 明文
- 六个静态检查全 PASS；`check_stat_rules.py` 的 4 条新只读聚合 marker 已在清单内
- 隐私模式落地为 `AmountMask.hidden` 单入口 + `formatCents` 可观察状态（MainActivity / LedgerUiParts / SettingsViewModel / StatsScreen 四处）
- 真机观感与累积未验清单仍按使用者决定整体推迟，见下节与活文档 §8


## 第 14 批（真机反馈修正，与第 13 批同属一个未归档 change）

### 根因（动手前先写，指南 §6.1）

第 13 批自己把「统计页一屏塞 6 张卡 → 改为 4 张卡」写进 §2 取舍表，并把排行与环形图
用 `InsightMode` 切换 chip 合并进一张「分类洞察卡」。使用者真机反馈「结构更混乱」、
「查看统计页就不应该在一屏里面硬塞，滑动屏幕是可以接受的」。
根因是**把第 11 批筛选面板（操作型浮层，选完即走）的「一屏」经验错误外推到统计页
（浏览型长报表）**：为省空间合并卡片，反而让每个模块的边界变糊。
「借鉴竞品先分组织方式与连接能力」仍成立，但「一屏」是我额外加的取巧，不是竞品要求。
分类管理第 12 批只修了表单位置，二级仍全量平铺 30 行，与「留白多」同源。

### 实现

| 层 | 文件 | 做法 |
|---|---|---|
| 统计页 | `StatsScreen.kt` | 删 `InsightMode`；`CategoryInsightCard` 拆成 `DonutInsightCard` + `RankInsightCard` 两张独立卡；顺序改为 总结 → 趋势 → 占比 → 排行 →（年度）资产 → 口径；「分类管理 ›」短跳转移到占比卡 |
| 分类 | `CategoryManagerDialog.kt` | 一级默认折叠、`rememberSaveable` 存 String 键派生 `expandedRoots: Set<Long>`；展开态二级 `chunked(3)` chips + 补空位；点 chip 弹操作 Dialog（改名/删除/上移/下移/看流水）；一级行 `›` 看流水、⋯ 菜单不变 |
| 规格 | 4 份 delta | 按标题整块替换 reporting-ui / category-stats 的矛盾 Requirement，不新增 MODIFIED；`build-and-ci` / `privacy-security` 不动 |
| 版本 | `app/build.gradle.kts` | 0.2.0 → 0.2.1 |

零表结构变更、零新增依赖/权限、DAO / `InsightData` / `StatsViewModel` / 环形下钻 /
双折线 / 资产累计推算 / 点数值 / 横屏页 / `AmountMask` 全部不动。

### 范围边界（显式写，避免下批当漏项）

- 折线图保留，不因 BeeCount 用柱状而改；AI / 分享 / 年度账单 / 海报 / 彩色图标目录不补
- 展开态 `rememberSaveable` 跨配置变更有效；默认 NavHost 未启用 `restoreState`，
  切 Tab 完全销毁 destination 时回到全折叠，属可接受的轻量 UI 态
  （与第 5 批必须提到 Activity 作用域的筛选条件不同量级）

### 三级回退顺序（首轮 CI 若红，每次只改一处单独推送）

1. 拆分两卡后编译报引用错 → 先保留 `CategoryInsightCard` 旧函数体，只调顶层调用顺序
2. 展开态存取报类型错 → `expandedKey` 已用 String 规避 saver，若仍报则退回 `remember`（仅配置变更不保存）
3. chips 网格 `weight(1f)` 报尺寸错 → 退回每个 chip 固定宽度 + 横向 `Row`

### 实测回填

（推送后写：commit sha / push CI run id / release run id / 制品大小 / 首轮失败根因 / 真机未验清单继承）
