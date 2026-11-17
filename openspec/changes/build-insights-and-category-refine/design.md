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

### 实测回填（第 14 批，2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `ce919f59b549c4f64900d2674144cc4723529cbf`（8 文件，v0.2.1） |
| push CI | `44011265168` **success**，17 步全绿，**首轮 Kotlin 编译零错误** |
| release | `44012015782` **success**，28 步；坚果云上传步骤 `completed success` |
| 制品 | `offline-ledger-release-v0.2.1` 12,218,823 B；`mapping-v0.2.1` 3,484,719 B；`metrics-v0.2.1-p-m-nsfalse` 271 B |
| 远端校验 | 按最终 commit sha 独立比对 **291↔291 blob、sha 全一致、PASS**；无 `diag/` 残留、无 token 明文 |
| 推送断言 | 差异集合恰好 8 个预期文件、零删除、`.github/` 与 `.gitignore` 误删保护通过（沿用第 13 批事故后的防线） |

**首轮无失败**：动手前先写根因与三级回退顺序；并主动把
`rememberSaveable { mutableStateOf(setOf<Long>()) }` 改为 `String` 键派生 `Set<Long>`，
规避「保存 `MutableState` 包装对象」的 saver 版本依赖，回退顺序第 2 条未用上。
`nsfalse` 表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，
**不当作已验证 HEAD 200**。真机观感与累积未验清单仍按使用者决定整体推迟。

---

# 第 15 批 · 全站列表规范 / 排序作用域 / 一级拖拽（v0.2.2）

## 0. 动手前根因（指南 §6.1）

**① 一级之间边界不清**：`CategoryRootRow` 无任何分割线，只有 `heightIn(min = 44.dp)`；
展开 chips 与下一个一级之间仅 `padding(bottom = 6.dp)`。明细页基准是
`fillMaxWidth + padding(horizontal=16.dp) + height(1.dp) + Neutral 0.10f`
（`DetailScreen.kt` 内联 Box）。五个列表页各自画或压根不画 → 风格漂移。
**这是第 11~14 批「老页面写法同步」的第三次外推**：统一网格不止左边界（§2.3），
连「行与行之间的边界」也要单一来源。

**② 一级显小 / chip 边框过长**：一级 `bodyMedium` + `labelSmall` 副行，二级
`FilterChip` 有填充底色且 M3 默认 label 字重更显眼，视觉重量被拉开；chip 用
`Modifier.weight(1f)` 三列等宽 + 补空 `Box(weight(1f))`，短名也被拉成整列宽。

**③ 排序不生效（真 bug，不是手感）**：`SettingsViewModel.moveCategory` 把
**全部未归档分类**（一级+二级、支出+收入）当一个数组交换相邻项，
`reorderCategories` 再按全局下标写 `sortOrder`，而 UI 先按 `type + level` 过滤出
`roots`、按 `parentId` 过滤出 `children` → 点一级「下移」常常只是和某个二级或
收入分类换了位置，`roots` 顺序纹丝不动。全局数组写法在
`AssetsViewModel.moveAccount` 同样存在（资产页按 `AccountType` 分组后也会「点了没反应」）。

**④ 资产页压缩过头**：`Card(padding(vertical = 1.dp))` + 内部
`padding(horizontal=10.dp, vertical=3.dp)`，账户卡几乎贴在一起，边界靠 1dp 间距表达。

**⑤ 拖拽**：Compose **没有内置列表重排组件**；`dragAndDropSource/Target` 是跨应用
传 `ClipData` 的协议（把图片拖进另一个 App），不是列表内换位。第三方
`reorderable` 库要新增依赖，且社区普遍踩「拖拽后 UI 不更新」
（`mutableStateListOf` / 稳定 `key` 绑定）的坑。采用零依赖 scoped 实现：
只在**一级行**加长按手柄，`pointerInput { detectDragGesturesAfterLongPress }` +
`draggedId` / `graphicsLayer` 位移 + 稳定 `key`，松手一次性把显式 `sortOrder` 落库。
**首版限制（如实记录）**：拖拽中不做实时让位动画、不做边缘自动滚动；
被拖行跟随手指，松手后按累计位移计算目标下标一次性重排。二级仍是 chips 网格，
保留操作弹层的上移 / 下移（已修作用域），不对 chips 做拖拽换位。

**⑥ 设置页顺序**：现状 `数据与备份 → 安全 → 偏好 → 账本 → 关于`，
按使用者给的使用频率改为 `账本 → 数据与备份 → 偏好 → 安全 → 关于`。
`reporting-ui` 一条 Requirement 把分组顺序写死，只改代码会留下规格与实现相反
→ 按指南 §5 用 MODIFIED 整块替换。

## 1. 实现结构

| 层 | 文件 | 做法 |
|---|---|---|
| 共享组件 | `ui/components/LedgerUiParts.kt` | 新增 `ListDivider(modifier)`：1dp / Neutral 0.10，**水平内缩由调用方给** |
| 明细 | `DetailScreen.kt` | 内联 Box → `ListDivider(Modifier.padding(horizontal = 16.dp))`，保持基准不变 |
| 分类 / 模板 / 预算 / 回收站 | 各自列表 | 外层 `padding(horizontal = 12.dp)`，调用时补 `4.dp` → 视觉基准统一 16dp |
| 资产 | `AssetsScreen.kt` | 账户卡 1dp → 4dp 组距 + 8dp 行内边距 + `ListDivider`；Card 外层再包一层 Column |
| 分类 | `CategoryManagerDialog.kt` | 一级 `titleSmall + SemiBold`；chip 去 `weight` 改 `widthIn(max=150.dp)` 自适应、label 显式 `bodyMedium`；一级行长按拖手柄 |
| 分类 VM | `SettingsViewModel.kt` | `groupKey = (type, level, parentId)`；`persistWithGroup()` 组内重排 + 组间保持原序，一次性写全量 id；新增 `commitRootOrder()` |
| 账户 VM | `AssetsViewModel.kt` | `moveAccount` 作用域改为 `AccountType`，同模式按组重建 |
| 设置 | `SettingsScreen.kt` | 四张 `SettingGroupCard` 顺序调整 |
| 版本 | `app/build.gradle.kts` | `0.2.1 → 0.2.2` |

**零表结构变更 / 零 Migration / 零新增依赖 / 零新增权限**；`CategoryDao`、备份 DTO、
统计口径、金额铁律、4 Tab、NavHost、`release.yml` 全部不动。排序仍走已有
`reorderCategories` / `reorderAccounts` 事务（含末尾重算），`WRITE_METHODS` 不变。

## 2. 全站列表规范（写进活文档 §7.5，后续页面直接抄）

- **分割线**：`ListDivider()` 单一来源；1dp、`Neutral.copy(alpha = 0.10f)`、距屏幕 **16dp**
  （明细外层 0 → 传 16dp；其余列表外层 12dp → 传 4dp）
- **行高分档**：明细 60dp（四列网格）、资产 56dp（账户卡）、管理页 44dp（单行 + ⋯ 菜单）
- **水平内边距**：行内容 16dp 基准；MUST NOT 用多个 48dp `TextButton` 横排撑高
- **可点区** ≥40dp（指南 §7，第三次「降噪过头」的硬约束）
- **选中态** `FilterChip` 容器底色，MUST NOT 用 `● ` 前缀

> 已知不完美：外层 12dp 与补 4dp 是务实取舍，避免为对齐把五个页面的每一行
> 内边距全部重写。后续若统一外层为 0，只需把各页 `ListDivider` 调用改成 16dp，
> 组件本身不动。

## 3. 三级回退顺序（首轮 CI 若红，每次只改一处单独推送）

1. **拖拽编译错**（最可能：`graphicsLayer` / `detectDragGesturesAfterLongPress` /
   `rememberUpdatedState` / `Icons.Filled.Menu` 的 import 或 API 签名）
   → 先退回「保留上移 / 下移，去掉 `dragModifier` 与 `pointerInput`」，功能不丢、先保绿。
2. **`widthIn` / `FontWeight` / `ListDivider` import 漏项** → 按编译器点名补 import，
   一次只补一组。
3. **VM 分组逻辑编译错**（`Triple` 类型推断、`suspend` 作用域）→ 显式写
   `Triple<RecordType, Int, Long?>` 与 `private suspend fun`，不改 DAO。

## 4. 被否决的方案

- `dragAndDropSource/Target` 做列表内重排（跨应用协议，用错场景）
- 引入 `reorderable` / `sh.calvin.reorderable` 等第三方库（破零依赖基线）
- 二级 chips 也做拖拽换位（网格拖动手感差，且会让折叠态密度回退）
- 只给分类页加线、其余页不动（第 12 批「老页面写法同步」已证伪）
- 把管理页行高统一拉到 60dp（又松回两屏）
- 只改设置页代码不改 spec / 只追加 ADDED 制造两份清单（指南 §5）
- 为紧凑删回收站信息或压触摸区到 40dp 以下

### 实测回填（第 15 批，2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `6cf6092db3ee8a1b4757c93da158a1880737cf91`（17 文件，v0.2.2） |
| 首轮 push CI | `44033035742` **failure**，挂在「运行单元测试」，Kotlin 编译失败 |
| 诊断通道 | 临时 dispatch-only `diag.yml`（`e6e5b357…`）→ run `44033638697` success → 把 gradle 日志回传 `diag/unit.log`（job logs 仍 403） |
| 唯一编译错误 | `CategoryManagerDialog.kt:476 Unresolved reference 'Menu'` —— material-icons-core 无 `Icons.Filled.Menu`；拖手柄改为文本 `≡` |
| 修复 commit | `143327b85b334c9b71305446a53d7c6dde638610`（同时删除 diag.yml 与 diag/unit.log） |
| push CI | `44034000868` **success**（17 步全绿） |
| release | `44034725835` **success**，23 步绿 / 5 步按 input 跳过；坚果云上传步骤 `completed success`；重复排队的 `44035074087` 已 cancelled |
| 制品 | `offline-ledger-release-v0.2.2` 12,222,648 B；`mapping-v0.2.2` 3,492,783 B；`metrics-v0.2.2-p-m-nsfalse` 272 B |
| 远端校验 | 按最终 commit sha 比对 **291↔291 blob、sha 全一致、PASS**；`diag/` 与 `diag.yml` **零残留**，workflow 仅 build/release 两份；六个静态检查全 PASS |

**首轮失败正好落在 `design.md` 三级回退顺序第 1 条的相邻路径**：不是拖拽 API 签名错，
而是给手柄挑了个不存在的图标。诊断日志证实全仓**仅此 1 条** `e:`，其余 16 个改动文件
（含 `graphicsLayer` / `pointerInput` / `Triple` 分组 / `ListDivider`）编译零错误，
回退顺序 2、3 条未用上。
`nsfalse` 表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，
**不当作已验证 HEAD 200**。真机观感与累积未验清单仍按使用者决定整体推迟。


---

## 第 16 批（v0.2.3）：全站排序统一 + 资产页纯行化

### 1. 根因（三条真机反馈归一）

| 反馈 | 根因 |
|---|---|
| 资产页「底色导致视觉比明细页小」 | 第 15 批只补 `ListDivider`、没拆 `Card`；卡片内边距 + 阴影把内容往里收一圈。这是「统一网格第三半」只做了一半 |
| 可点副标题字偏小 | `MiniAction` = `labelSmall` 裸 `Text` + `clickable + padding(vertical=2dp)`，触摸高约 20dp。违反活文档 §2.1「可点区 ≥40dp」，是同一类违反的**第三次**（第 9 批最近交易方、第 10 批联想行） |
| 二级 chips 短名右侧空 | 第 15 批把 chip 改 `widthIn(max)` 按文本收缩，但分行仍是 `chunked(3)` 固定 3 个；省出的宽度空在行尾 |
| 排序生硬 / 风格不统一 | 一级拖拽 `onDrag` 只累加 `acc`，`onDragEnd` 才一次 `add(to, removeAt(from))` → 拖动中其余行原地不动、松手整列跳变；二级 / 资产 / 模板仍是上移下移。四处共四套写法 |

BeeCount 的参照是 Flutter `ReorderableListView`：官方行为即「拖动经过其他条目时自动平移让位」「被拖项浮起阴影」。Compose 无内置等价组件，也不引入 `reorderable` 第三方库（破零依赖基线）。

### 2. 落地方案

- 新增 `ui/components/ReorderSupport.kt` 单一来源：`WrapRow`（稳定 `Layout` API，按实测宽度换行，**刻意不用实验性 `FlowRow` 注解**，见 §3 T12 例外）+ `ReorderDragState` + `rememberReorderDrag` + `ReorderHandle`。
- **实时让位**：`drag()` 每跨过 `rowPx` 即 `removeAt/add` 更新 `orderedIds`，列表随之重组、其余项**立即**让位；被拖项 `zIndex + shadowElevation + alpha` 浮起。松手才 `onCommit` 一次性落库；`onDragCancel` 回滚快照。
  **如实限制**：本 BOM 无 `androidx.compose.foundation.lazy.animateItem`（首轮 CI 报 Unresolved reference，已按 §3 回退顺序第 1 条去掉），因此让位是即时的、没有补间动画，顺滑度仍不如 Flutter `ReorderableListView`。
- 四处接入：分类一级（56dp）、分类二级 chips（40dp）、资产账户（按 `AccountType` 分组、56dp）、模板（44dp）。
- 三个 VM 各加一个按组落库方法：`commitChildOrder(parentId, ids)`、`commitAccountOrder(type, ids)`、`commitTemplateOrder(ids)`，复用既有 `persistWithGroup` / `reorderAccounts` / `reorderTemplates` 单事务，**DAO 与表结构一字不动**。
- 上移 / 下移全部保留（分类 ⋯ 菜单与二级操作 Dialog、模板 ⋯ 菜单、资产新增 ⋯ 菜单）作为 TalkBack 兜底，不再占主行。
- 资产页 `AccountCard` 去 `Card` → 明细页同款纯行（16dp / 56dp / `ListDivider`）；5 个操作 `bodyMedium` + `heightIn(min=40).widthIn(min=40)`，副信息仍 `labelSmall` 纯文本（本就不用点）。
- **规格收口**：本 change 内 `category-stats` 原有**两个同标题 MODIFIED 块**合并为一个；`ledger-transactions` 新增 2 份 MODIFIED；`reporting-ui` 修订「资产页账户卡」表述。零表结构 → 覆盖安装即可。

### 3. 三级回退顺序（首轮 CI 若红，每次只改一处单独推送）

1. **`animateItem()` / `zIndex` / `graphicsLayer` / `Layout` API 签名错** → 先去掉 `.animateItem()`（实时重排仍生效，仅少让位动画），保绿后再补。
2. **`rememberUpdatedState` / `detectDragGesturesAfterLongPress` / 循环内 `rememberReorderDrag` 调用点错** → 退回「四处保留上移下移 + 共享组件只留 `WrapRow`」，功能不丢。
3. **VM 落库方法编译错（`Triple` 推断 / `AccountType` import）** → 显式写类型参数，不改 DAO。

### 4. 被否决的方案

- 松手才重排（本轮根因，已证伪）
- 引入 `sh.calvin.reorderable` / 任何第三方拖拽库（破零依赖）
- `dragAndDropSource/Target` 做列表内换位（跨应用 ClipData 协议）
- 实验性 `FlowRow` + `@OptIn(ExperimentalLayoutApi::class)`（BOM 2026.06 注解状态不确定，改用稳定 `Layout`）
- 把 ↑↓ 全删（TalkBack 无兜底）
- 资产页操作收进 ⋯ 菜单（副标题需要直接可点）
- 只放大操作字不拆 `Card`（底色问题不解决）
- 只追加 ADDED 不改旧 Requirement（指南 §5）
- 顺手给预算页加排序（当前无此能力，避免范围蔓延）

### 5. 实测回填（第 16 批，2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `49516fd15550594154b2a36a8109302cc53b2243`（14 文件，v0.2.3） |
| 首轮 push CI | `44045003985` **failure**，挂在「运行单元测试」（Kotlin 编译） |
| 诊断通道 | 临时 dispatch-only `diag.yml`（`7a94c11c740c…`）→ run `44045388978` success → 日志回传 `diag/unit.log`（`de1d21ac3ea1`）；job logs 仍 403 |
| 三条编译错误 | ① `CategoryManagerDialog.kt:48 Unresolved reference 'animateItem'`；② `TemplateManagerDialog.kt:5` 同；③ `TemplateManagerDialog.kt:183 @Composable invocations can only happen from the context of a @Composable function`——`rememberReorderDrag` 被放进非 composable 的 `LazyListScope` |
| 修复 commit | `3e367748af2f061b0589d2e32eab17c8b68f8f85`（去掉 `animateItem` import 与调用、把 `tplDrag`/`orderedTemplates` 提升到 `@Composable` 函数体、删除 `diag.yml` 与 `diag/unit.log`） |
| push CI | `44045792325` **success**（17 步全绿） |
| release | `44046503643` **success**，23 步绿 / 5 步按 input 跳过；**上传到坚果云 completed success**；重复排队 `44046920100` 已 cancelled |
| 制品 | `offline-ledger-release-v0.2.3` 12,231,819 B；`offline-ledger-mapping-v0.2.3` 3,502,962 B；`offline-ledger-metrics-v0.2.3-p-m-nsfalse` 271 B |
| 远端校验 | 按最终 commit sha 比对 **293↔293 blob、sha 全一致、PASS**；`diag/` 与 `diag.yml` 零残留、workflow 仅 build/release 两份；无 token 明文；六个静态检查全 PASS |

**回退顺序实际用上第 1 条**：`androidx.compose.foundation.lazy.animateItem` 在 Compose BOM
2026.06 不存在，去掉后**实时重排仍然生效**（`orderedIds` 状态驱动列表重组），
只是没有补间动画。第 3 条是独立发现的作用域错误，按「每次只改一处」在同一修复
commit 里一起处理。**如实限制**：让位是即时的、不是连续像素级补间，
顺滑度仍不如 Flutter `ReorderableListView`；不把「实时重排」包装成「动画已对齐」。
`nsfalse` 表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，
**不当作已验证 HEAD 200**。真机观感与累积未验清单继续按使用者决定整体推迟。
