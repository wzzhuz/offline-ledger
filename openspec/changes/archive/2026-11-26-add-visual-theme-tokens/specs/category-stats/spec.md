# Delta for 分类与统计（category-stats）

## MODIFIED Requirements

### Requirement: 分类管理列表页采用一羽式布局

**第 79 批归档修订**：分类管理列表页 SHALL 采用中性卡片 TopBar（返回 / 标题 / 右上「排序」入口 + ⋮ 菜单），
「支出 / 收入」分段 SHALL 坐进标题栏下部，MUST NOT 再作为列表内独立 `FilterChip` 行。
首屏 MUST NOT 常驻整段灰色说明文字；「最多两级 / 笔数含二级 / 删除转归档」
三条 SHALL 收进 ⋮ →「使用说明」并在删除确认弹窗保留。
一级行 SHALL 为「彩色图标 + 名称 + 二级数角标 + 单 ⋮ 菜单」，
MUST NOT 重复显示「（支出）」类型后缀，MUST NOT 横排 `≡` / `›` / `⋮` 三个控件。
页面底部 SHALL 有固定胶囊容器承载「已归档（N）」与「停用项管理」两个入口，
以及蓝色大 `+` FAB 作为主新增入口；新增入口 SHALL 在列表滚动时保持可见。
系统 MUST NOT 提供「系统分类 / 分类扩展包」一键安装入口，
MUST NOT 因此改动 `assets/default_categories.json` 的 ID 规则或备份整表替换语义。

#### Scenario: 首屏层级
- **GIVEN** 打开分类管理页，支出下有 10 个一级
- **WHEN** 查看首屏
- **THEN** 中性卡片 TopBar 与支出 / 收入 Tab 可见、无常驻说明灰字
- **AND** 底部大 `+` FAB 固定可见，滚动到列表末行仍不被遮挡

#### Scenario: 底部入口不越界
- **GIVEN** 库里有已归档分类与 59 个停用扩展项
- **WHEN** 点底部胶囊的两个入口
- **THEN** 分别进入已归档恢复与停用项管理，均复用已有数据路径
- **AND** 预置清单 ID 与备份整表替换语义不变

**理由**：图 1 与图 2 的差距是六处叠加（顶栏层级、一级信息锚点、二级网格、
常驻说明、拇指区主操作、停用态），不是单点样式问题；
借组织方式与入口位置，不借连接能力（活文档 §7.3）。

---

---

---

### Requirement: 独立分类排序页与临时顺序

系统 SHALL 提供独立的「分类排序」全屏页（Navigation 路由），
由分类列表页右上「排序」入口进入，MUST NOT 新增第 5 个底部 Tab。
排序页 SHALL 按一羽记账图 3 **1:1** 组织：一级横排为 5 列图标、选中项带主色 12% 透明底与主色字（第 79 批，MUST NOT 用整块蓝底）；
选中一级的二级 SHALL 收进**插在一级 5 列区中间**的圆角白卡
（选中一级所在行之后、其余一级行之前），白卡内为 5 列等宽图标格，
且 SHALL 以灰阶单色显示，MUST NOT 在排序页保留彩色
（彩色只在分类列表页与全量选择器）。白卡 MUST NOT 退化为上下兄弟容器。
页面 SHALL 提供「保存」与「放弃」，MUST NOT 再常驻拖动说明文字或三道横线手柄；
可拖性由整格手势与底色分区表达（使用者第 4 轮 Q2A）。

排序页 SHALL 为**两态互斥**，这是使用者观察到的「一羽拖一级时二级折叠」：
一级态 SHALL 只渲染 5 列一级 `LazyVerticalGrid`，二级区 SHALL 折叠 / 不渲染，
容器内 MUST NOT 有 span item 或 section header；
点某个一级 SHALL 进入二级态，一级区 SHALL 改为**非惰性** 5 列 Row 静态展示并冻结不可拖；
**长按任意一级格 SHALL 立即自动收起二级区回到一级态**，
MUST NOT 要求点「收起，排一级」按钮（使用者第 4 轮 Q1A）。
圆角白卡 SHALL 插在选中一级所在行之后、其余一级行之前，
白卡内二级 SHALL 为第二套 `LazyVerticalGrid(GridCells.Fixed(5))` 可独立拖。

一级与二级 SHALL 各用一套 `rememberReorderableLazyGridState`
（`sh.calvin.reorderable:3.1.0` README 一等 API，原生支持 `LazyVerticalGrid`），
MUST NOT 用 `rememberReorderableLazyListState` 的一维 `LazyColumn`，
MUST NOT 自写 `Modifier.pointerInput` / `detectDragGestures*` 手势，
MUST NOT 为此新增任何第三方依赖。两套 state SHALL 状态隔离、互不干涉：
各自 `onMove` SHALL 只改自己那一段 `draftOrder`，
一个容器的拖动 MUST NOT 触发另一个容器的 `onMove`；
同一时刻 MUST 只有一个可拖容器，MUST NOT 让两个惰性网格嵌套。

库 README 的两条约束 SHALL 遵守：`onMove` MUST 在返回前同步更新列表，
否则拖动项闪烁；容器内若插入任何非拖 item，
`from.index` / `to.index` MUST 按 README「Section Headers and Footers」示例减偏移。
两态切换 MUST NOT 丢失任一层 `draftOrder`。

排序页 SHALL 维护 `draftOrder` 临时顺序状态，与已落库 `sortOrder` **完全分离**：
拖动 SHALL 只改 draft 内存状态，MUST NOT 逐次写库；
点「保存」SHALL 一次性按 `(type, level, parentId)` 三元组写回显式 `sortOrder`，
重开页面顺序 SHALL 保持；返回 / 放弃且存在未保存改动时 SHALL 明示「放弃修改」，
MUST NOT 静默写库，MUST NOT 让库内顺序发生任何变化。
状态写入 SHALL 用 `_state.update { }` 原子更新，
MUST NOT 用 `_state.value = _state.value.copy(...)` 散写（活文档 §7.11）。
保存 SHALL 由纯 JVM `CategoryManagementRules.rebuildOrder` 按三元组一次性重建
全量 id 顺序并写回，MUST NOT 丢项、MUST NOT 混入另一收支类型或另一父级；
既有 `commitRootOrder` / `commitChildOrder` / `moveCategory` SHALL 保留供列表页
上移 / 下移无障碍兜底使用。一级与二级拖动手柄 SHALL 挂在**整个格子**上，
短按展开与长按拖动 SHALL 共享 `MutableInteractionSource` 共存
（v3.1.0 README「Use with Card」段明确支持的写法），
`ReorderableItem` 的 key SHALL 与父容器 `item(key = ...)` 完全一致。

排序页白卡 SHALL 只出现在二级态，且外层一级区 MUST 为非惰性
`Column + Row(weight(1f))`，MUST NOT 用 `GridItemSpan` 常驻一级网格、
MUST NOT 用第二个惰性网格嵌进另一个惰性网格（同方向嵌套滚动 + 高度不确定）。
二级网格 SHALL 限高并自身滚动，外层 Column MUST NOT 再滚动。
上移 / 下移 SHALL 以每格 `semantics.customActions` 保留作 TalkBack 兜底
（5 列小格 MUST NOT 再堆按钮），只列可行方向、复用 `moveDraft*ByIndex`。

**第 4 轮交互优化（使用者 Q1A / Q2A / Q3A）**：二级态外层 5 列 Row 的最后一行
不足 5 个时 SHALL 以 `weight(1f)` 空占位补齐，MUST NOT 让剩余格子被拉宽导致列不对齐。
白卡内二级网格为 `GridCells.Fixed(5)`，最后一排留白但格宽恒定，MUST NOT 改自适应列。
白卡 SHALL 用主色 12% 透明底标注二级区域（第 79 批）、二级格仍为白底圆角；
MUST NOT 再用「点按小手柄拖动排序」之类小字重复提示，
MUST NOT 在页面顶部或底部常驻拖动说明文字，只保留分组标题与「保存 / 放弃」。
二级态静态一级格为触发「长按自动收起」MAY 用
`Modifier.pointerInput { detectTapGestures(onLongPress = …) }`——
这仅是一个状态切换触发器，不涉及坐标换算、让位补间或索引，
MUST NOT 与「MUST NOT 自写排序拖动手势」混为一谈。

**已降级的风险**：上一版登记的「自实现手势 pointer pass 竞争」
「`GridItemSpan` 与一级拖动索引错位」「回退到上移 / 下移按钮」三项，
因改用库原生 Grid API 且两态无 span item 而不再成立，
MUST NOT 继续列进真机待验清单；CI 全绿仍 MUST NOT 替代真机观感验收。

#### Scenario: 试排可放弃
- **GIVEN** 在排序页拖动了一级与二级、尚未点保存
- **WHEN** 按返回并选择放弃
- **THEN** 库内 `sortOrder` 零变化，重进列表页顺序与进入前完全一致

#### Scenario: 保存按三元组落库
- **GIVEN** 调整了支出一级顺序与「餐饮」下二级顺序
- **WHEN** 点「保存」并重进页面
- **THEN** 一级只在支出一级之间换位、二级只在「餐饮」内部换位
- **AND** 收入分类与另一父级的二级顺序完全不变

#### Scenario: 拖一级时二级折叠

- **GIVEN** 分类排序页处于一级态
- **WHEN** 长按某个一级**格子**开始拖动、跨行换位
- **THEN** 全程二级区不渲染，容器内无 span item、无索引偏移
- **AND** 让位动画与边缘滚动由 `reorderable` 提供，只改一级段 `draftOrder`

#### Scenario: 两级拖拽互不干涉

- **GIVEN** 点某个一级进入二级态
- **WHEN** 在白卡内拖动二级 5 列图标格
- **THEN** 一级区冻结不可拖，一级 `draftOrder` 零变化
- **AND** 只有该父级下二级段 `draftOrder` 改变，另一父级与收入分类不受影响
- **AND** 两态来回切换后两层 `draftOrder` 均不丢失

#### Scenario: 长按一级自动收起二级

- **GIVEN** 处于二级态、白卡已展开「餐饮」的二级
- **WHEN** 长按任意一级格（含非选中一级）
- **THEN** 二级区立即收起并回到一级态，MUST NOT 出现「收起，排一级」按钮
- **AND** 两层 `draftOrder` 均不丢失；再长按一次即可开始一级拖动

#### Scenario: 末行不足 5 列仍对齐

- **GIVEN** 一级数为 13 个（末行 3 个）、二级数为 11 个
- **WHEN** 展开某级进入二级态
- **THEN** 一级区末行 3 格仍各占 1/5 宽、右侧 2 格为空占位，与其他行列线对齐
- **AND** 白卡内二级最后一排格子宽度与前排一致，不被拉宽

#### Scenario: 无手柄与无重复提示

- **GIVEN** 一级态与二级态
- **WHEN** 查看页面
- **THEN** 页面上 MUST NOT 出现三道横线「≡」手柄、MUST NOT 出现「长按拖动一级，点按拖动二级」
  或「点按小手柄拖动排序」等说明文字
- **AND** 二级区靠浅色底 + 白格区分，一级格与二级格整格均可直接拖动

**理由**：现网是「列表里就地拖 + 松手即落库」，既易误触也无法反悔；
一羽的独立页 + 保存/放弃是更稳的组织方式。临时顺序与落库值分离同时
消除「一个字段同时表达试排与最终顺序」的语义混用（踩坑清单 3.4）。

---

---
