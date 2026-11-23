# 技术设计：分类管理三页借鉴一羽布局与交互

## 现状（已实际读远端 main `3f386b7`）

| 项 | 事实 | 位置 |
|---|---|---|
| 列表 / 编辑 | 单文件 `CategoryManagerScreen` + `CategoryList` + `CategoryRootRow` + `CategoryEditor`，TopBar 白底、支出/收入为 `FilterChip` 行、顶部常驻说明灰字 | `ui/settings/CategoryManagerDialog.kt` |
| 一级行 | `▾/▸` + 纯文字名 + `「N 二级分类 · M 笔（含二级）」` + `≡` 拖柄 + `›` 看流水 + `⋮` 菜单 | 同上 L405–490 |
| 二级 | 展开态 `WrapRow` + `FilterChip(widthIn(max=150.dp))`，停用拼进名字 `（停用）` | 同上 L303–327 |
| 新增 | TopBar 小 `+` → `CategoryEditor` 独立视图，手选层级、二级父级为「搜索 + 限高 260dp 竖列表」，一次只存一条 | 同上 L495–623 |
| 排序 | **无独立页**：一级在列表里 `longPressDraggableHandle` 就地拖，松手 `commitRootOrder` 落库；二级只能点 chip 弹层上移/下移 | 同上 L253–262；VM L773–813 |
| 路由 | `composable("categories") { CategoryManagerScreen(onBack = { goBack() }) }` | `ui/LedgerApp.kt` L208 |
| 数据 | `categories` 表 `iconName` / `colorHex` / `sortOrder` / `enabled` 字段齐备，`version = 5`，`Migrations.ALL` 为空 | `CategoryEntity.kt`、`AppDatabase.kt` |
| 图标 | `CategoryIcon(name, colorHex, size)` = 中性圆 + 中文首字；`colorHex` 参与 `ChartData` 扇区取色 | `ui/components/LedgerUiParts.kt` L25–30、`engine/ChartData.kt` L212–233 |
| 清单 | v4，106 行 = 一级 15 + 二级 91；启用 47 / 停用 59；`iconName` 全空、`colorHex` 恒 `#888888` | `assets/default_categories.json` |
| 清单解析 | `SeedCatalog.parse` 只解析 `id/name/type/parentId/level/enabled/sortOrder`；`insertStatements` 把 `iconName` 写死 `''`、`colorHex` 写死 `'#888888'` | `data/local/SeedCatalog.kt` L10–43 |
| 同构 fallback | `SeedData.Row` **已有** `iconName` / `colorHex` 字段并已写进 INSERT | `data/local/SeedData.kt` L18–22、L156–161 |
| 依赖 | `sh.calvin.reorderable:reorderable:3.1.0`、`material-icons-core`（刻意不引 `material-icons-extended`） | `app/build.gradle.kts` L167–187 |
| 资源 | `res/` 下只有 values / xml，**无任何 drawable** | `app/src/main/res/` |

数量分布（用于定列数）：单父级二级数依次为
居住 11 / 餐饮 10 / 养车 9 / 购物 8 / 医疗 8 / 学习娱乐 8 / 交通 7 /
宠物 7 / 人情社交 6 / 其他收入 4 / 其他 3 / 工资 3 / 理财收益 3 / 奖金 2 / 兼职 2。
最长名 7 字「赡养·代缴水电」。

## 方案

### 1. 列表页：借层级与入口，不借「一级横排」

```
蓝底 TopBar:  ←  分类管理                    排序   ⋮
              [ 支出 | 收入 ]   ← Tab 坐进标题栏下部
────────────────────────────────────────────────────
LazyColumn（竖排，竖排，15 个一级不横排）
  ▾  [彩色圆图标] 餐饮  ⑪                        ⋮
     ┌────┬────┬────┬────┬────┐
     │ 图 │ 图 │ 图 │ 图 │ 图 │   5 列等宽，固定格高
     │正餐│外卖│早餐│午餐│晚餐│   文字单行 ellipsis
     ├────┼────┼────┼────┼────┤
     │ 图 │ 图 │ 图 │  ＋ │    │   末位「添加子类」
     └────┴────┴────┴────┴────┘
     ListDivider()
  ▸  [彩色圆图标] 交通  ⑦                        ⋮
  ...
────────────────────────────────────────────────────
底部胶囊：  已归档（N）  |  停用项管理        [ 蓝色大 + ]
```

- **等宽网格实现**：`Row` + 每格 `Modifier.weight(1f)`，行数 = `ceil(n/5)`，
  格高固定，gutter 均匀 → 留白不再碎在行尾。
  MUST NOT 用 `LazyVerticalGrid` 嵌进 `LazyColumn.item {}`
  （同方向嵌套滚动 + 高度不确定），MUST NOT 用 `FlowRow`（T12）。
  稳定 `Layout` API、零新增依赖，符合活文档 §7.6 的 T12 例外判断。
- **停用态**：小角标（图标右下角一个中性色圆点或角标文字），
  MUST NOT 再 `child.name + "（停用）"` 拼进文字——那正是当前撑破 chip 的根因。
- **一级行**：`≡` 拖柄与 `›` 看流水收进 ⋮ 菜单（改名 / 看流水 /
  停用 / 上移 / 下移 / 删除 / 使用说明）。上移 / 下移作为 TalkBack 兜底保留。
  二级数角标取代「10 二级分类」长文本；笔数仍由 `GROUP BY` 一次查出，
  移到 ⋮ 菜单的「看流水」或行副行，MUST NOT 逐行查库。
- **说明文字**：收进 ⋮ →「使用说明」，首屏不再常驻一整段灰字；
  「最多两级 / 笔数含二级 / 删除转归档」三条在删除确认弹窗里仍保留，
  避免活文档 §2.1「降噪过头砍易用」。
- **底部胶囊只借位**：「系统分类 / 分类扩展包」换成已有的
  「已归档（N）」「停用项管理」，不碰 `default_categories.json` 的
  ID 规则与备份整表替换语义。

### 2. 独立排序页：临时顺序与 `sortOrder` 分离

```
蓝底 TopBar:  ←  分类排序                          保存
一级横排 5 列（选中项蓝底 + 角标）
┌──────────────── 圆角白卡 ────────────────┐
│  选中一级的二级：灰阶单色，一维列表        │
│  ≡ 饮料   ≡ 外卖   ≡ 早餐 …（点按即拖）    │
└──────────────────────────────────────────┘
其余一级横排 5 列（灰阶）
        长按拖动一级，点按拖动二级
                    保存
```

- **状态分层**（踩坑清单 3.4「一个字段别管两种语义」+ 活文档 §7.11 原子写）：
  `draftOrder: Map<groupKey, List<Long>>` 只活在排序页 ViewModel，
  与已落库 `sortOrder` 完全分离；拖动只改 draft；
  点「保存」才一次性 `reorderCategories(...)` 按三元组写回；
  返回 / 放弃 MUST NOT 写库，有未保存改动时 SHALL 明示「放弃修改」。
- **一级横排**：排序页用 `LazyVerticalGrid` 或 `Row` 分行都行，
  但拖拽仍 MUST 用 `reorderable` 的 `longPressDraggableHandle`；
  库只支持一维 `LazyColumn`，故一级横排时**拖动手柄挂在每个格子上**，
  落库仍按三元组重建全量 id 顺序（沿用 `commitRootOrder` 已有语义）。
- **二级点按拖**：排序页内把选中一级的二级**临时排成一维列表**，
  用 `draggableHandle`（非长按）挂在项首小拖柄上，避开「点按 = 编辑」冲突；
  列表页网格保持不动。MUST NOT 在二维网格上硬做点按拖。
- **灰阶降饱和**：排序页图标统一去饱和（一羽图 3 自己就是灰的），
  彩色只在浏览页，减少拖动时的视觉噪声。
- **上移 / 下移**在列表页弹层保留，MUST NOT 作为主入口。

### 3. 分类选择页：只借版式，不改语义

一羽「添加二级分类」页的版式为「顶部上下文（一级分类：餐饮）+
名称输入 + 颜色圆点 + 继续添加 + 搜索 + 左侧图标分组导航 / 右侧图标网格」。
本 change 把这套版式用于**选择**场景：

| 入口 | 用途 |
|---|---|
| 列表页展开区末位「添加子类」 | 打开选择页，**父级与收支类型已预填**，标题写明「添加到 餐饮 › 支出」 |
| ⋮ →「停用项管理」 | 打开同一版式的选择页，右侧网格列出停用扩展项，可批量启用 |
| 顶部搜索 | 按名称筛一级 / 二级，复用现有 `contains(ignoreCase = true)` |

- **继续添加**：保存后不清空父级与收支类型，只清名称并保留键盘焦点，
  可连续建二级；每次写库独立事务，MUST NOT 攒一批再写（活文档 §7.11）。
- **不是**「从预设分类库挑一个加进来」：不改清单语义、不碰
  `restructure-preset-categories` 的 ID 规则与「误删预制分类不可逆」策略。
- 名称 MUST 补重名校验（同父级下同名），失败就地提示、不写库。
- 输入框仍用 `placeholder` + `heightIn(min = 40.dp)`，
  MUST NOT 回退到已被证伪的固定 `height`（活文档 §7）。

### 4. 彩色图标目录：显式常量，不反射，不碰 `colorHex`

- 新增 `ui/components/CategoryIconCatalog.kt`：
  **编译期显式常量表** `when (iconKey) { ... }`，每个分支引用随包本地
  `ImageVector` / drawable 资源常量，`else ->` 回退 `CategoryIcon` 中性圆 + 首字。
  MUST NOT 用 `iconName` 字符串在运行时反射 / 查表映射到 `ImageVector`
  （T14：任一图标名不存在会让整份映射表编译失败）。
- **数据来源**：`assets/default_categories.json` 每行补 `iconKey` / `iconTintHex`
  两个字段（**不动** `id / name / type / parentId / level / enabled / sortOrder`）；
  `SeedCatalog.Row` 与 `insertStatements` 同步解析并写入 `iconName`；
  `SeedData` fallback 同构补齐，保持「换清单只改 JSON、不改 Kotlin」的既有约定。
- **MUST NOT 改写 `colorHex`**：该字段参与 `ChartData` 扇区取色与
  `RecordDao` 的三处 `categoryColorHex` 投影。图标配色由**纯 JVM
  `engine/CategoryIconTints.kt` 的编译期常量表**按一级领域分组提供
  （同组同色、11 种低饱和色），与 `colorHex` 两套管径分离，统计口径零影响。
- **适用范围**：只有列表 / 排序 / 选择三页读目录；
  `CategoryIcon(...)`（中性圆 + 首字）在记一笔、明细、账户详情、
  统计页的调用点**一字不动**。
- **资源来源与许可证**：MUST NOT 复制一羽记账的图标资源（第三方视觉资产
  需授权）。**已确认采用 Tabler Icons outline v3.34.0（MIT）**，
  只取 90 个 SVG 转成 `res/drawable` 本地资源，
  许可证、来源链接与传递依赖审计见本文件「依赖与许可」节与 `assets/NOTICE`。
  > **已确认（2026-11-23）**：使用者选定 Tabler。实际覆盖**全部 106 个
  > 预置分类**（90 个唯一 iconKey），非仅高频项；详见「图标资源落地记录」。
  > `SeedCatalog` 保留 `iconTintHex` 可选解析口子供将来按分类级自定义，
  > 本批不写库、不参与运行时取色；清单最终只补 `iconKey` 一个字段。

### 5. 状态与落库

- `SettingsViewModel` 新增 `draftOrder` 与 `saveDraftOrder()` / `discardDraftOrder()`；
  既有 `commitRootOrder` / `commitChildOrder` / `moveCategory`
  **签名与三元组语义不变**，排序页保存复用它们。
- 列表页展开态继续用 `rememberSaveable`（第 14 批），网格化不改状态作用域。
- 所有写方法继续走 Repository 事务，MUST NOT 绕过
  `check_transaction_annotations.py` 的注解门禁。

## 依赖与许可

| 项 | 坐标 / 来源 | 许可证 | 传递依赖 | 运行时网络 |
|---|---|---|---|---|
| 拖拽（既有） | `sh.calvin.reorderable:reorderable:3.1.0` | Apache-2.0 | kotlin-stdlib-common、compose runtime/animation/foundation | 无 |
| 图标资源（第 55 批落地） | **Tabler Icons outline v3.34.0**（pin 版本，非 `@latest`）<br>`https://github.com/tabler/tabler-icons`<br>`https://tablericons.com/` | **MIT**<br>`Copyright (c) 2018-2026 Tabler` | **无**（只取 90 个 SVG 转成 `res/drawable/ic_cat_*.xml` 随包，零新增依赖、零 AAR、零 Maven 坐标） | 无 |

**版权声明落盘**：MIT 全文 + 版权行 + 来源 + pin 版本写在 `app/src/main/assets/NOTICE`，
随 APK 分发，满足 MIT「保留版权与许可声明」的唯一义务 。选 Tabler 而非
Lucide（ISC，部分图标源自 Feather 带 MIT 声明，NOTICE 要列两套）是因为许可单一、
6,257 个图标覆盖中文记账特有概念更全；统一 24×24 网格 / 2px 居中描边 /
`stroke="currentColor"` / 无固定色，正好把上色权留给我们，不会回到记一笔页
「6~7 种高饱和色乱跳」的老坑 。

MUST NOT 引入 `material-icons-extended`（`build.gradle.kts` L168–170 已刻意排除，
官方自述 excessive size、同类项目实测全量 AAR 编进 11,400 个类占 43%）；
MUST NOT 引入第三方 Tabler Compose AAR（Maven Central 有 MIT 封装，但会把全量
图标类编进 dex，绕回体积老路）；MUST NOT 引入图片加载库（无远程图片需求）。

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 1:1 照抄一羽三页（含一级列表横排、扩展包一键装） | 最像竞品 | 列表页 15 个一级横排一屏放不下；扩展包会碰清单 ID 规则与备份整表替换；活文档 §7.3 明确「借鉴先分清组织方式与连接能力」 |
| 继续用 `WrapRow`，只给 chip 加彩色圆点 | 改动最小、零改 spec | 使用者已明确否掉：留白碎在行尾、长短不一，正是「效果差一大截」的根因 |
| `LazyVerticalGrid` 做二级网格 | API 现成、自动分行 | 嵌在 `LazyColumn.item {}` 里是同方向嵌套滚动，高度不确定；且一级行展开/收起时网格高度难算。改用 `Row + weight` 稳定 Layout，行数显式计算 |
| `FlowRow` + `@OptIn(ExperimentalLayoutApi)` | 自适应换行最自然 | 活文档 T12 / §7.6：BOM 2026.06 注解状态不确定，为「更优雅」引入实验性 API 不值当 |
| 二维网格上直接做点按拖 | 与一羽图 3 视觉一致 | `reorderable` 是一维列表模型（§7.7），二维索引 → 位置换算会重新踩 4 轮 pointer pass 坑。排序页内临时改一维，列表页保持网格 |
| 图标名 → `ImageVector` 运行时查表 | 清单改起来直观 | T14：任一图标名不存在即整份映射表编译失败。改编译期显式常量 + `else` 兜底 |
| 直接改 `colorHex` 给图标上色 | 复用已有字段 | `colorHex` 参与环形图扇区取色与 3 处 DAO 投影，会污染统计口径；图标配色另开 `iconTintHex` |
| 复制一羽的图标资源 | 观感 100% 对齐 | 第三方视觉资产需授权，个人自用也不该静默搬运；改用可商用集并记录许可证 |
| 把「添加子类页」改成从预设库挑分类 | 减少手打 | 会改清单语义、碰 ID 规则与备份整表替换；使用者选的是「借版式做选择页」（A），不是改语义（B） |
| 排序页沿用「松手即落库」 | 少一个保存按钮 | 一羽的核心交互是「试排可放弃」；且就地拖与浏览态混在一起易误触。临时顺序与落库分离也顺带修掉「一个字段管两种语义」 |
| 新增 `brief.md` 之类文件承载对齐结论 | 上下文最完整 | 违反工作流「零新增文件类型」，对齐结论流进本 proposal |

## 取舍

选「**借布局 + 改三条 spec + 零改表 + 零新增第三方库**」：

- **牺牲**：视觉不可能 100% 等于一羽（图标资源自有、底部入口语义不同、
  一级列表仍竖排）；图标资源需使用者补一批，第一批覆盖率不齐。
- **换取**：不动统计口径、不动记一笔页、不动预置清单数据语义，
  v0.2.35 覆盖安装即可生效；三页观感与操作路径对齐一羽；
  排序从「就地拖 + 松手落库」升级为「独立页 + 保存/放弃」。

与活文档 §7.3 的结论一致：**借组织方式，不借连接能力**。

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| 图标资源缺失导致部分格显示中性圆，观感不齐 | `else` 兜底 + 第一批覆盖一级 15 + 高频二级；tasks 列「图标资源来源待确认」，MUST NOT 假装已齐 |
| 等宽网格 5 列在窄屏（≤360dp）图标过小 | 格宽 = `(屏宽 - 2×16dp - 4×gutter)/5`；设最小格宽，不足时降为 4 列；真机待验 360dp / 6.73 寸 / 横屏 |
| 7 字长名单行省略后看不全 | 点击格仍打开操作弹层显示全名；MUST NOT 让长名换行撑高格子 |
| 排序页横排一级 + 拖拽手柄错位点 | `ReorderableItem` key 与 `items(key=...)` 完全一致（§7.7）；落库按三元组重建全量顺序，MUST NOT 丢项 |
| `draftOrder` 与 `_state` 非原子写 | 沿用 `_state.update { }` 原子写（活文档 §7.11），禁止 `_state.value = _state.value.copy(...)` 散写 |
| 改 `CategoryManagerDialog.kt` 与未归档 change 同文件 | 按远端 main 内容做块级精确替换，MUST NOT 整文件回写（§3 T6）；改完 grep 全部引用点 |
| 改 `SeedCatalog` 解析后纯 JVM 单测挂 | 新增字段全部可选（缺失按默认），旧 JSON / 旧备份仍可解析；补 `SeedCatalogTest` |
| spec 出现两条相反要求 | 3 条按标题整块 MODIFIED，不追加 ADDED 覆盖旧文本；归档前查重复标题（活文档 §5） |

## 方案 A 实施记录（2026-11-23）

使用者确认四件套后选**方案 A**：三页布局与交互全部落地，图标资源与许可证后置。

**API 查证（不凭记忆写）**：`sh.calvin.reorderable` v3.1.0 的 README 经 GitHub
Contents API 读取，确认 `rememberReorderableLazyGridState(lazyGridState) { from, to }`
存在，并附 `LazyVerticalGrid` 完整示例；`ReorderableItem(state, key)`、
`Modifier.draggableHandle()` / `longPressDraggableHandle()` 均在
`ReorderableCollectionItemScope` 内可用。因此排序页一级**可以合法横排 5 列拖拽**，
无需退回竖排。`from.index` / `to.index` 直接映射到 `draftOrder` 的下标。

**实际落地与文档的偏差（如实登记，不包装成一致）**：

| 项 | design 原方案 | 实际实现 | 原因 |
|---|---|---|---|
| 排序页容器 | 一级横排 + 选中二级白卡「插在」一级网格中间 | 一级网格与二级列表是 `Column` 的**兄弟**：网格限高 240dp、二级 `weight(1f)` | 一个惰性容器里混 `GridItemSpan` 与嵌套 `LazyColumn` 会造成同方向嵌套滚动 + 高度不确定。兄弟容器零风险，组织方式（横排一级 + 白卡二级）一致 |
| 一级拖动手柄 | 挂在整个格子上 | 挂在格子内 32dp 的「≡」小手柄上，名称另可点选中 | README 明确 clickable 容器与 draggableHandle 共存需额外处理；分离手柄与点击零冲突 |
| `default_categories.json` | 补 `iconKey` / `iconTintHex` | 首批方案 A 阶段零改；图标落地后**只补 `iconKey` 一个字段**，106 行数据语义零改动 | 配色权威来源改为 `CategoryIconTints` 编译期常量表（同组同色、11 色），清单不需要逐行存色值；`SeedCatalog` 仍保留 `iconTintHex` 可选解析口子供将来扩展 |
| `CategoryManagerDialog.kt` | 块级精确替换（§3 T6） | **完整重写** | 本批改动同时覆盖 TopBar / 列表 / 一级行 / 编辑视图，块级替换 10+ 处更易漏。改前已读远端 `main` 全文并 grep 全部调用点，改后跑括号平衡、重复 import 与六个静态检查。§3 T6 针对的是「脚本持有旧快照整文件回写把刚加的按钮盖掉」，不是禁止完整重写单文件 |
| 排序页 VM 实例 | 与列表页共享 | 各路由独立 `settingsVm()` 实例 | `settingsVm()` 用默认 `viewModel()` 作用域，跨 NavBackStackEntry 不共享。列表页加 `LaunchedEffect(Unit) { loadCategories() }`，从排序页 / 选择页返回时重新查库，保证新 `sortOrder` 立即可见 |

**新增文件**：`engine/CategoryManagementRules.kt`（纯 JVM：网格分行 / 三元组重建 / 重名）、
`ui/components/CategoryIconCatalog.kt`（图标唯一出口 + 5 列等宽网格）、
`ui/settings/CategorySortScreen.kt`、`ui/settings/CategoryPickerScreen.kt`、
`engine/CategoryManagementRulesTest.kt`、`data/SeedCatalogOptionalIconTest.kt`。

**方案 A 收口**：布局与交互先落地、图标资源后置的做法已按使用者 2026-11-23
「按 Tabler 来」完成收口；下面单独记录图标批，未结项仅剩真机观感验收。
首批的「图标资源来源 / 许可证 / 清单补字段」三项现已全部有落盘产物，
MUST NOT 再写成待确认。

## 图标资源落地记录（Tabler / 方案 A 收尾）

**选型**：Tabler Icons outline **v3.34.0**（pin 到具体 tag，非 `@latest`），
MIT 单一许可、6,257 个 24×24 / 2px 描边 / `stroke="currentColor"` 线性图标。
经 GitHub `zipball` API 拉下该 tag 整包（34.8 MB / 4,963 个 outline SVG），
只取所需 **90 个唯一 iconKey**，覆盖清单 **全部 106 个预置分类**。
未选 Lucide 是因为 ISC + Feather 衍生 MIT 双声明会让 NOTICE 变两套；
未引 Maven Central 上的第三方 Tabler Compose AAR 是为了不把全量图标类编进 dex。

**转换（零新增依赖）**：Tabler SVG 根上统一 `fill="none"` / `stroke-width="2"` /
`stroke-linecap="round"` / `stroke-linejoin="round"`，图标体**只有 `<path d="..."/>`**
（生成脚本断言无 `circle/rect/g/use` 等元素）。用脚本把每个 `d` 原样写进
`res/drawable/ic_cat_*.xml` 的 `android:pathData`，配
`strokeColor="#FF000000"` / `strokeWidth="2"` / round cap+join，
由 Compose `Icon(painterResource(...), tint = ...)` 在运行时染色。
比 SVG→`ImageVector` PathData DSL 更稳： arc / 相对命令交给 Android 框架解析，
不需要自己写 SVG path 解释器；90 个 XML 全部通过 `ElementTree` 解析自检。
生成脚本是一次性产物、不进仓库，产物（XML + Kotlin 表）落盘可复核。

**目录与配色（编译期显式常量，非运行时反射）**：

| 文件 | 职责 |
|---|---|
| `ui/components/CategoryIconCatalog.kt` | `when (iconKey) -> R.drawable.ic_cat_*`，90 个分支；`else -> null`。`CategoryGlyph` 组合「浅色圆底 + 单色线性图标」，缺项回退 `CategoryIcon` 中性圆 + 首字 |
| `engine/CategoryIconTints.kt` | 纯 JVM，90 个 iconKey → 领域色（ARGB Int）。**11 种低饱和色、按一级领域分组、同组同色**；收入统一 `IncomeGreen`；排序页 `GRAYSCALE` 降饱和 |
| `engine/CategoryIconKeys.kt` | 纯 JVM，106 个预置 id → iconKey |

配色克制是刻意的：Tabler 本身单色，上色权完全在我们手里；「同组同色」
拿到一羽式彩色网格的观感，同时避开活文档 §2.6 记一笔页「6~7 种高饱和色
一眼不清晰」的翻车路径。`colorHex` 一字未动，两套管径分离。

**覆盖安装兜底（本批最容易漏的一点）**：`SeedCatalog.insertStatements`
只在 `SeedCallback.onCreate` **全新安装**时把 `iconKey` 写进 `categories.iconName`。
v0.2.35 覆盖安装的用户库已存在，106 行 `iconName` 仍是空串；零改表前提下
不能靠迁移回填。因此 `CategoryGlyph` 在 `iconName` 为空时按
`CategoryIconKeys.keyOfPreset(categoryId)` 回查，**覆盖安装也能立刻看到图标**，
且零写库、零 Entity 变更、零备份 DTO 变更。手工新建分类 id 从 `max+1` 起、
永不回填空洞，查不到即回退中性圆。4 个 `CategoryGlyph` 调用点全部传 `categoryId`。

**清单与 fallback**：`default_categories.json` 106 行各补一行 `iconKey`
（保留原 2 空格缩进的最小 diff），`id/name/type/parentId/level/enabled/sortOrder`
**逐字段零改动**；`SeedData` fallback 的 `CategorySeed` 本就有 `iconName` 字段、
`c()` 仍给空串，仅用于纯 JVM 结构校验，首次安装实际读 assets JSON，
`SeedCatalogTest` 的 6 项一致性断言不受新增字段影响。

**版权声明**：`app/src/main/assets/NOTICE` = 来源 + pin 版本 + `Copyright (c)
2018-2026 Tabler` + MIT 全文，随 APK 分发，满足 MIT 唯一义务。

**单测**：`CategoryIconTintsTest`（清单每个 iconKey 都有配色、未知 key 返 null、
收入统一绿）、`CategoryIconKeysTest`（106 个预置 id 全覆盖且都有配色、
自定义 id 返 null）、`SeedCatalogOptionalIconTest`（旧清单无图标字段仍可解析）。

**零改表复核**：`AppDatabase.version` 仍 5、`Migrations.ALL` 仍空、
`colorHex` 一字未动（仍参与 `ChartData` 扇区取色与 `RecordDao` 三处投影）、
`app/build.gradle.kts` 依赖表一字未动、六个静态检查本地全 PASS。
清单只新增可选字段、不改任何数据语义 → **v0.2.35 直接覆盖安装生效**。

## 验证

- [ ] 六个静态检查本地与 CI 全 PASS（`check_kotlin_strings` / `check_docs_consistency` /
      `check_manifest_privacy` / `check_stat_rules` / `check_transaction_annotations` /
      `check_workflow_syntax`）
- [ ] 按最终 commit sha 查 tree，逐文件比对 blob sha，`远端校验: PASS`
- [ ] 给出真实 commit sha + run id；版本号递增、产物名带短 sha、不同名覆盖
- [ ] `mapping.txt` 随对应 APK 保留；无 `diag/` 残留、无 token 明文、
      Manifest 仍无 `INTERNET`
- [ ] **真机待验（CI 全绿不替代，活文档 §1）**：
  5 列网格在 360dp / 6.73 寸观感与 gutter 均匀度、长名省略、停用角标可辨、
  蓝底 TopBar 与底部 FAB 不遮挡列表末行、⋮ 菜单七项操作全可达、
  排序页长按一级 / 点按二级手感与让位补间、放弃后顺序零变化、
  保存后三元组作用域正确、重进页面顺序保持、连续添加不清父级、
  重名校验提示、图标缺项兜底显示、记一笔 / 明细 / 统计页分类标识仍为中性圆、
  横屏与旋转屏幕后展开态与 draft 状态
- [ ] §8 既有真机未验清单**整体继承**，本批新增项续写进活文档 §8；
      未验项 MUST NOT 被改写成「已通过」
