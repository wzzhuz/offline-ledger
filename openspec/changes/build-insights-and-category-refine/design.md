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

**回退顺序实际用上第 1 条**：当时报 `Unresolved reference 'animateItem'`，去掉后
**实时重排仍然生效**（`orderedIds` 状态驱动列表重组），只是没有补间动画。
**第 19 批订正**：此处写「该 API 在 Compose BOM 2026.06 不存在」是错误结论——
官方 Lazy lists 文档明确有 `Modifier.animateItem()`（`LazyItemScope` 扩展）。
更可能是当时列表为普通 `Column`（无 `LazyItemScope` receiver）或 import 作用域不对。
不得再把它记为平台限制。第 3 条是独立发现的作用域错误，按「每次只改一处」在同一修复
commit 里一起处理。**如实限制**：让位是即时的、不是连续像素级补间，
顺滑度仍不如 Flutter `ReorderableListView`；不把「实时重排」包装成「动画已对齐」。
`nsfalse` 表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，
**不当作已验证 HEAD 200**。真机观感与累积未验清单继续按使用者决定整体推迟。


---

## 第 17 批（v0.2.4）：拖拽与父滚动竞争 / WrapRow 左对齐 / 实测行高

### 1. 真机反馈与根因（两条，按「全局搜同类写法」外推到四处）

**① 二级 chips 视觉居中、短名行右侧留白没用上。** 第 16 批 `WrapRow` 用
`layout(widest.coerceAtMost(constraints.maxWidth), totalHeight)`：容器宽度取
「最宽一行的实测宽」而非父宽，且 `horizontalAlignment` 从未声明。父级
`fillMaxWidth()` 的剩余空间由布局对齐策略决定，短 chips 行就被推到中间；
chip 内 label 又是 M3 默认居中。改法：显式 `horizontalAlignment` 参数
（默认 `Alignment.Start`）、`layout()` 宽度强制 `constraints.maxWidth`、
每行从 0 起排、行尾留白明确在右，chip label 显式 `TextAlign.Start`。

**② 资产页拖动排序时上下移动同时滚屏、拖拽事件被释放。** 两个 bug 叠加：

- **手势竞争**：资产页是 `Column + verticalScroll(rememberScrollState())`
  **整页一个滚动容器**，分类 / 模板是 `LazyColumn`。父 `scrollable` 在
  `PointerEventPass.Initial` 过 touch slop 拿到滚动权，子级手柄的
  `pointerInput` 只在 `onDrag` 里 `consume()`（Main pass）拦不住 Initial pass，
  于是父滚动开始 → 布局位移 → 子拖拽收到 `onDragCancel` → `end(false)` 回滚。
  **标准解法是拖拽期间禁用父滚动**，不是手动消费 down event：
  `verticalScroll(state, enabled = !anyDragging)` /
  `LazyColumn(userScrollEnabled = !anyDragging)`。
  （Compose 的 `detectDragGesturesAfterLongPress` 内部 `awaitFirstDown` 依赖
  未被消费的 down；在 Initial pass 显式 consume down 反而会让拖拽失效。
  本批按此如实实现，不把「消费 down」写成已做。）
- **跨行阈值按错行高**：`AccountCard` 根 `heightIn(min=56.dp)`，内部是
  「主行 + 可选副信息行 + 40dp 操作行」，实测约 76–96dp；而
  `rememberReorderDrag(rowHeightDp = 56)` 按 56 算「跨过一行」。分类一级
  56 / 二级 40 / 模板 44 也都是估算。手指还没拖过一整格就换一次位。
  改法：`rowMeasureModifier()` 用 `onSizeChanged` 把实测行高写回 state，
  `rowHeightDp` 退化为初值兜底；MUST 挂在单行根，MUST NOT 挂在含展开区
  / 多行 chips 的整块 item 上（否则把整块高度当行高）。

**③ 禁用滚动必须配套边缘自动滚动。** 资产页 10+ 账户时，禁用父滚动后
手指拖不到屏幕外的位置，等于功能倒退。共享 state 暴露 `edgeDirection`
（-1/0/1），`handleModifier` 用 `onGloballyPositioned` 记录手柄在 root 中的
位置、`change.position.y` 换算手指 root y，进入容器上下 48dp 边缘区时
页面按 16ms 帧 `dispatchRawDelta` 滚动。用 root 坐标近似、48dp 为粗判定，
**沙盒无法验手感，回退顺序见 §3**。

**④ 全局扫到的同类**：`CategoryManagerDialog` / `TemplateManagerDialog` 的
`LazyColumn` 同样存在竞争，只是分类一屏能放下、尚未触发；
`BudgetManagerDialog` 有 `verticalScroll` 但无拖拽，不动。
四处共用 `ui/components/ReorderSupport.kt` 单一来源，按指南 §7.2
「老页面 MUST 全局搜同类写法」一次修掉。

### 2. 方案（推荐 A 已确认）

| 项 | 落地 |
|---|---|
| WrapRow | `horizontalAlignment: Alignment.Horizontal = Alignment.Start`，`layout()` 占满父宽、每行从 0 起、行尾留白在右；chip label `TextAlign.Start` |
| 父滚动 | `ReorderBounds`（容器可见边界）+ `onDraggingChange` 汇总 `anyDragging`；资产页 `verticalScroll(enabled=!anyDragging)`，分类/模板 `LazyColumn(userScrollEnabled=!anyDragging)` |
| 边缘滚动 | `edgeDirection` + `LaunchedEffect` 每 16ms `dispatchRawDelta(±10dp)`；`ReorderBounds` 由列表根 `onGloballyPositioned` 写入 |
| 实测行高 | `rowMeasureModifier()`；资产挂 `AccountCard` 根、分类一级挂 `CategoryRootRow` 的 Row（**不挂含展开 chips 的 item Box**）、二级挂单个 chip Box、模板挂行 Row |
| 不变 | DAO / 写方法 / 表结构 / `sortOrder` 三元作用域 / 4 Tab / 上移下移 TalkBack 兜底 / `rememberSaveable` 折叠态 / `ListDivider` / 金额铁律 / 统计口径 / Manifest / `release.yml` |

### 3. 三级回退顺序（动手前写定，每次只改一处单独推送）

1. **`LazyListState.dispatchRawDelta` / `ScrollState.dispatchRawDelta` 编译不过**
   → 改 `LaunchedEffect` 内 `animateScrollBy(±10dp)`（suspend，逐帧调用）。
2. **禁用父滚动反而让 `onDragCancel` 更频繁 / 手势完全收不到**
   → 退回「不在 Initial pass 消费 down」的既有实现，改用 `NestedScrollConnection`
   在拖拽期间吞掉 `onPreScroll`，保留父滚动节点但不生效。
3. **边缘滚动抖动 / 手指 root 近似偏移导致误触**
   → 固定步进 + 阈值死区（连续 3 帧同方向才启动），或把 48dp 调到 64dp；
   仍不稳则本批只保留「禁用父滚动」，边缘滚动如实记为待下一批。

`androidx.compose.foundation.lazy.animateItem` 在 BOM 2026.06 不存在
（第 16 批首轮 CI 已证），本批继续不用；让位是即时重排，不是补间动画。

### 4. 验收（真机项 MUST 由使用者装机确认，CI 全绿不得代替）

1. 短名二级 chips 从左起排、行尾留白在右、chip 内文字不居中。
2. 资产页 10+ 账户滚动到底后，长按拖动不再带动页面滚动；顺序按视觉跨行变化；松手落库持久、重开保持。
3. 拖到屏幕上下边缘能自动滚到屏外账户并继续换位；切 Tab 返回不丢顺序。
4. 分类一级 / 二级、模板四处同样不误触滚动；↑↓ 兜底仍可用。
5. 归档校验 3 份 spec delta 按标题整块替换、无「估算行高 / 不禁用父滚动」矛盾文本。
6. CI 全绿、版本 0.2.4、远端 sha PASS、无 token 明文、六个静态检查全过。

### 5. 否决

- 只调 `consume()` 不禁用父滚动（Initial pass 仍抢）；只禁滚动不补边缘滚动（功能倒退）。
- 只在资产页局部打补丁（分类 / 模板同病）；继续用 56/40/44 估算行高。
- `WrapRow` 换回 `chunked(3)` 或引入 `FlowRow + @OptIn`；`layout()` 继续取 widest。
- 引入 `sh.calvin.reorderable` / 任何第三方拖拽库（破零依赖基线）。
- 把 `rowMeasureModifier` 挂在含展开区的整块 item 上。
- 顺手给预算页加排序（当前无此能力，不新增）；为紧凑压触摸区到 40dp 以下。
- 只追加 ADDED 不改第 16 批 delta（指南 §5 规格债）。

### 6. 实测回填（第 17 批，2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `8f547c789686f25805c8e7ba9f86cc2b64131f90`（11 文件，v0.2.4） |
| push CI | `44057590347` **success**，17 步全绿，**首轮 Kotlin 编译零错误**（未走诊断通道） |
| release | `44058813183` **success**，23 步绿 / 5 步按 input 跳过；**上传到坚果云 completed success**；重复排队 `44059276870` 已 cancelled |
| 制品 | `offline-ledger-release-v0.2.4` 12,235,191 B；`offline-ledger-mapping-v0.2.4` 3,505,636 B；`offline-ledger-metrics-v0.2.4-p-m-nsfalse` 271 B |
| 远端校验 | 按最终 commit sha 比对 **293↔293 blob、sha 全一致、PASS**；`diag/` 零残留、workflow 仅 build/release、无 token 明文；六个静态检查本地与 CI 全 PASS |
| 文档回填 | 本 commit（仅文档，代码零变化） |

**首轮无失败**：动手前按 `design.md` §3 写定三级回退顺序，并把
`onEdgeScroll` 从 `suspend` lambda 收敛为普通 lambda（实际调用
`dispatchRawDelta` 是同步的），避免函数类型歧义；四个
`rememberReorderDrag` 调用点、`WrapRow` 唯一调用点、
`CategoryRootRow` 的 `measureModifier` 参数全部同步核对，无「改一处漏一处」。

**如实限制**：`nsfalse` 只表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验
只写日志不中断，**不当作已验证 HEAD 200**。边缘自动滚动用 root 坐标近似
+ 48dp 粗判定、手指 root y 由 `onGloballyPositioned` 记录手柄位置换算，
**沙盒无模拟器，手势竞争与边缘滚动完全验不了**；CI 全绿只证明
`verticalScroll / LazyColumn / pointerInput / onSizeChanged / dispatchRawDelta`
编译通过，不证明「手指拖到屏幕底部时列表按预期滚、父滚动不抢事件」。
这轮 MUST 由使用者装机拖一次才算数。若真机仍被抢，按 §3 回退顺序
退到 `NestedScrollConnection` 拦截；若边缘滚动抖动，退到固定步进 + 阈值死区。

---

## 第 19 批：引入开源拖拽库 + 隐私口径按使用者声明改写

### 1. 触发与根因

使用者验 v0.2.4 后报「资产页拖动排序还是有问题」。只读复核确认三处：
① `anyDragging` 只在长按成功 `start(id)` 后为 true，而长按等待约 500ms 窗口内
父 `verticalScroll` 仍 `enabled=true`，微小移动即过 touch slop 抢走 pointer，
`onDragStart` 根本不触发；② 资产页是 `Column+verticalScroll`，分类/模板是
`LazyColumn`，父 `pointerInput` 重启时序不同，「四处统一」并未真正成立；
③ `rowMeasureModifier` 按组件契约 MUST 只挂单行，实际挂在整张 `AccountCard`
（主行+副信息+40dp 操作行），且边缘坐标 `handleTops + change.position.y`
是随列表滚动漂移的混合量。

连续三轮真机验不过，判定为**平台级手势知识**而非逻辑 bug，改用成熟库替换最脆一层。

### 2. 选型

`sh.calvin.reorderable:reorderable:3.1.0`（Apache-2.0，Maven Central，2026-04-20）：
支持 `LazyColumn` / Grid / `Column` / `Row`、分段头与非可拖拽项、
立即或长按拖拽、子控件手柄、**按距边缘距离变速自动滚动**、
`Modifier.animateItem()` 补间让位、内置无障碍 `CustomAccessibilityAction`
（正好对应本项目 `↑↓` 兜底）。传递依赖无网络客户端。
对比 `org.burnoutcrew.composereorderable:0.9.6`（停在 2022、首项不动画）后不采用。

### 3. 使用者隐私口径澄清（本批一并落地）

> app 是自用的，自己不会主动往不可信的三方服务器上传。权限在自己手里就可以。
> 前面不用钱迹等 app 原因是它们默认就把数据上传到他们服务器、不支持用户控制。

据此把 `project.md` 铁律 1 / 技术栈依赖取舍 / 永久不做清单、
`check_manifest_privacy.py`（硬禁令 → 显式授权闸门）、
`privacy-security` 3 份 MODIFIED、Manifest 注释、活文档 §2.7 全部改写：
**红线 = 默认回传厂商且用户不可控；不是「无 INTERNET」或「零新增依赖」的形式禁令**。
当前 App 实际仍不声明 `INTERNET`（功能不需要），但不再作为否决理由。

### 4. 分批与回退顺序

1. **本 commit**：只加依赖坐标 + 口径/规格/文档订正，不动任何 Kotlin 拖拽代码。
   这次 CI 是「库 3.1.0 与 BOM 2026.06.00 是否兼容」的真实门槛。
2. 兼容确认后：先接资产页（改 `LazyColumn`，库 state + `ReorderableItem` +
   `longPressDraggableHandle`），逐页再接分类一级与模板；二级 chips 保留自研 `WrapRow`。
3. 若库与 BOM 不兼容 → 退到 vendor 源码（按 Apache-2.0 裁进仓库、保留 LICENSE/NOTICE）；
   若仍不顺 → `↑↓` 恢复主入口、拖拽降级为可选，如实记入指南，不无限迭代手势。

### 5. 实测回填（第 19 批 A 第一步，2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `aa26aa87f293`（8 文件；仅依赖坐标 + 口径/规格/文档，**未动任何 Kotlin 拖拽代码**） |
| push CI | `44074939870` **success**（静态检查 6 项 / 单测 / Debug APK / release lintVital 全绿） |
| 并发取消 | `44074938760` cancelled（同 commit 的 dispatch 与 push 撞 concurrency，正常） |
| 制品 | `offline-ledger-debug-apk` 24,179,773 B |
| **库兼容性判定** | **PASS** —— Gradle 成功解析 `sh.calvin.reorderable:reorderable:3.1.0`、Kotlin 编译与 lintVital 全过，证明与 Compose BOM `2026.06.00` / Kotlin `2.3.21` 兼容；无版本冲突、无传递网络客户端 |
| 本地静态检查 | 六个全 PASS（含改写后的 `check_manifest_privacy.py` 显式授权闸门） |
| 回填 | 仅文档 commit，代码零变化 |

**本批刻意不升版本号**：v0.2.4 仍是当前可装包；库已入依赖树但拖拽实现仍为自研，
升 `0.2.5` SHALL 留到代码接入完成、真机可验之后，避免出现「版本号升了但行为没变」的错位。
**如实限制**：CI 全绿只证明**库能编译进项目**，不证明拖拽手感变好——
手势替换尚未写一行 Kotlin。真机项继续由活文档 §8 继承。
