# Delta for category-stats

## MODIFIED Requirements

### Requirement: 分类图标不做字符串映射

分类管理三页（分类列表 / 分类排序 / 分类选择）**以及记一笔「分类 ›」全量选择器
与明细筛选「分类」选择器** SHALL 使用本地 `CategoryIconCatalog` 呈现彩色具象图标，
图标资源 SHALL 以**编译期显式常量**登记并随包分发；
**记一笔快捷栏、明细列表行、账户详情与统计页** SHALL 继续使用
`CategoryIcon` 的中性圆 + 分类名中文首字，MUST NOT 受本批影响。

系统 MUST NOT 把 `iconName` 字符串在运行时反射或查表映射到 `ImageVector`
（T14：任一图标名不存在会让整份映射表编译失败）。
目录中缺项的分类 SHALL 回退 `CategoryIcon` 中性圆 + 首字，
MUST NOT 崩溃、MUST NOT 出现整页空白、MUST NOT 因单个资源缺失导致编译失败。
`iconName` 字段 SHALL 原样保留并作为目录键使用；`iconName` 为空时
SHALL 按预置分类 id 回查编译期表，使覆盖安装也能显示图标。
图标配色 SHALL 由纯 JVM `CategoryIconTints` 编译期常量表按一级领域分组提供，
MUST NOT 改写参与环形图扇区取色与 DAO 投影的 `colorHex`，
MUST NOT 逐图标随机高饱和。
图标资源 SHALL 为 Tabler Icons outline v3.34.0（MIT，pin 版本），
MUST NOT 复制第三方 App 的视觉资产；来源、许可证、版权声明与传递依赖审计
SHALL 记录在 change 的 `design.md`「依赖与许可」节与 `assets/NOTICE`，
MUST NOT 新增任何 Gradle 依赖、MUST NOT 引入含网络行为的图标加载库。

#### Scenario: 新增分类名不含已知图标名

- **GIVEN** 用户自定义一个分类，图标键是任意字符串或目录未收录
- **WHEN** 打开分类管理三页
- **THEN** 该格回退中性圆 + 中文首字正常显示
- **AND** 不因图标键不存在而编译失败或运行时崩溃

#### Scenario: 彩色图标只进分类选择组件

- **GIVEN** 分类管理列表页的「餐饮」显示彩色餐具图标
- **WHEN** 打开记一笔「分类 ›」全量选择器与明细筛选「分类」选择器
- **THEN** 两个选择器中的「餐饮」同样显示彩色餐具图标
- **AND** 记一笔快捷栏、明细列表行、账户详情、统计页仍是中性圆 +「餐」字
- **AND** 环形图扇区颜色与改动前一致、`colorHex` 零改动

**理由**：原口径把「记一笔页图标显示效果差」的结论写成了全局禁令。
第 1 轮使用者明确「不引入图标是原来记一笔页面有图标显示效果差，
我们现在先看分类管理相关页面」，故适用范围收窄到管理页；
第 2 轮（v0.2.36 真机反馈后）使用者对「新选择器图标」明确勾选
「A｜彩色 Tabler（需放宽本条 Requirement）」，故再放宽到**两个全量选择器**，
仍 MUST NOT 铺进快捷栏 / 列表行 / 统计页 —— 那正是当初效果差的部位。
仍禁止字符串反射映射，是因为 T14 的编译期风险与页面无关。

---

### Requirement: 分类管理全量父级与排序

分类管理 SHALL 提供一级与二级排序，**排序作用域 SHALL 为
`(type, level, parentId)` 三元组**：一级只在同收支类型的一级之间排序，
二级只在所属一级内部排序，MUST NOT 把全部未归档分类当成一个全局数组交换相邻项。
**第 55 批整块修订（替换第 20 批表述）**：资产页账户、模板管理与分类管理
SHALL 继续共用 `sh.calvin.reorderable:reorderable:3.1.0`（Apache-2.0、
Maven Central、纯 UI 库、无运行时网络行为）；资产页账户与模板管理 SHALL 沿用
`ui/components/ReorderListSupport.kt` 的 `rememberKeyedReorder` 适配层与
`rememberReorderableLazyListState`。**分类列表页 SHALL NOT 再提供就地拖拽**，
一级顺序调整统一交给独立「分类排序」页；排序页一级与二级 SHALL 各用一套
`rememberReorderableLazyGridState`（v3.1.0 README 一等 API，原生支持
`LazyVerticalGrid` / `GridCells.Fixed(5)`），MUST NOT 再退回一维 `LazyColumn`、
MUST NOT 自写 `Modifier.pointerInput` 手势。
排序页 SHALL 为**两态互斥**：拖一级时二级区折叠 / 不渲染，
点开一级后一级区冻结、白卡内二级 5 列网格独立拖；
同一时刻 MUST 只有一个可拖容器，两套 state 的 `onMove` MUST 各管各段 `draftOrder`。
两处 MUST 用 `ReorderableItem` + 稳定 `key`（与父容器 `item(key = ...)` 一致），
一级 MUST 用 `longPressDraggableHandle`、二级 MUST 用 `draggableHandle`，
且两者 SHALL 挂在**整个格子**根 Modifier 上，MUST NOT 再画三道横线小手柄。
库 SHALL 负责拖动中让位补间、pointer pass 顺序、长按窗口与边缘滚动，
MUST NOT 再自写 `pointerInput` / `scrollable` / 实测行高阈值，
MUST NOT 使用跨应用 `dragAndDropSource/Target` 协议。
拖动中 `onMove` SHALL 只改 `draftOrder` 内存顺序，**松手 MUST NOT 写库**；
点「保存」才一次性写入显式 `sortOrder`，重开页面顺序 SHALL 保持。
资产页容器 SHALL 为 `LazyColumn`（库的边缘滚动仅支持 Lazy 容器），
MUST NOT 用 `Column + verticalScroll`；资产页账户的展示仍按 `AccountType` 分组，
落库顺序 SHALL 由 `commitAccountOrderAll` 全量写回，MUST NOT 丢项。

**本批整块修订（替换第 14–16 批 `WrapRow` 自适应 chips 表述）**：
分类列表页的二级 SHALL 以**固定列数等宽图标网格**展示，
默认 5 列、窄屏可降为 4 列，每格 `weight(1f)` 等宽、固定格高、
gutter 均匀、图标 + 单行文字（超长名 ellipsis），
末格 SHALL 固定为「添加子类」；MUST NOT 再用按文本宽度收缩的自适应 chips，
MUST NOT 把「（停用）」拼进分类名称撑破格子（停用 SHALL 用小角标表达），
MUST NOT 用 `chunked(3)` / `weight` 混搭造成行尾碎留白，
MUST NOT 用 `LazyVerticalGrid` 嵌进 `LazyColumn.item {}` 造成同方向嵌套滚动，
MUST NOT 用 `@OptIn(ExperimentalLayoutApi)` 的 `FlowRow`（T12），
MUST NOT 用卡片堆叠 / 阴影表达格子边界（第 13 批已证留白大）。
分类排序页内选中一级的二级 SHALL 为 **5 列等宽图标网格**，
由第二套 `rememberReorderableLazyGridState` 提供点按拖（库原生支持 Grid），
且 SHALL 与一级态互斥；列表页仍 SHALL 为等宽图标网格；
上移 / 下移 SHALL 在列表页二级操作弹层保留为无障碍兜底
（TalkBack 无法执行拖动手势），MUST NOT 作为主入口。

新增二级分类的父级选择 SHALL 以「分组导航 + 等宽网格 + 搜索」展示所有
未归档一级分类，MUST NOT 因下拉高度或屏幕宽度截断；
SHALL 显示当前选中的父级及其收支类型，并 SHALL 支持「继续添加」连续建二级。
分类管理 SHALL 提供「支出 / 收入」分段切换，默认支出，
切换后 MUST NOT 混排另一类型的分类，列表行 MUST NOT 重复显示「（支出）」类型后缀。
一级分类 SHALL 默认全部折叠，点击一级行 SHALL 展开 / 收起其二级分类。
SHALL 显示每个一级的二级分类数与流水笔数；一级行的笔数 SHALL 含其所有二级分类
（标注「含二级」），笔数 MUST 由一条 `GROUP BY` 只读查询得出，MUST NOT 逐行查库。
点击二级网格项 SHALL 可编辑该分类，改名 / 删除 / 上移 / 下移 / 看流水五项 MUST 全部保留；
点击一级行 ⋮ 菜单里的「看流水」SHALL 打开该分类的近期流水详情弹窗，
MUST 带 `LIMIT`、MUST NOT 全量加载，且 MUST 保留时间、金额、分类与备注。
一级行的折叠展开态 SHALL 用 `rememberSaveable` 保存，配置变更后 MUST NOT 丢失。
编辑视图 SHALL 按「名称 / 类型或父级」分组，输入框 SHALL 用 `placeholder`
与 `heightIn(min = 40.dp)`，MUST NOT 用固定 `height` 裁切占位文字。
分类管理 MUST 继续采用「顶部固定新增入口 + 惰性列表 + 编辑独立视图」结构
（第 12 批已落地）：一级列表 SHALL 保持竖排 `LazyColumn`，
MUST NOT 把 15 个一级横排塞进一屏，MUST NOT 把列表改回全量 `Column`，
MUST NOT 为增加信息而一次把全部二级平铺成独立行。
一级列表相邻项之间 SHALL 有 `ListDivider()` 细线；一级名称 SHALL 显著重于二级格
（`titleSmall + SemiBold`）；一级行右侧 SHALL 只保留一个 ⋮ 菜单，
MUST NOT 再横排 `≡` / `›` / `⋮` 三个控件。

#### Scenario: 10 个一级全可见
- **GIVEN** 系统有 10 个一级分类
- **WHEN** 打开新增二级分类的父级网格
- **THEN** 10 个一级分类均可滚动查看与搜索，选中项高亮并显示收支类型

#### Scenario: 一级下移只换一级
- **GIVEN** 支出一级「餐饮 / 零食 / 水果」与若干二级分类
- **WHEN** 点「餐饮」下移
- **THEN** 一级顺序变为「零食 / 餐饮 / 水果」，二级与收入分类顺序完全不变

#### Scenario: 二级只在父级内排
- **GIVEN** 「餐饮」下有 8 个二级、「零食」下有 5 个二级
- **WHEN** 点「餐饮·早餐」下移
- **THEN** 只与「餐饮」下的二级换位，「零食」下的二级顺序不变

#### Scenario: 拖动中实时让位且不落库
- **GIVEN** 排序页一级「餐饮 / 零食 / 水果 / 交通」
- **WHEN** 长按「餐饮」手柄向下拖过「零食」
- **THEN** 拖动过程中「零食」立即让位、显示为「零食 / 餐饮 / 水果 / 交通」
- **AND** 松手后仅 `draftOrder` 变化，库内 `sortOrder` 不变
- **AND** 点「保存」后重进页面顺序保持

#### Scenario: 11 个二级排成等宽网格
- **GIVEN** 「居住」下有 11 个二级，含 7 字长名与若干停用项
- **WHEN** 展开「居住」
- **THEN** 二级呈 5 列等宽图标格、共 3 行，gutter 均匀、行尾无碎留白
- **AND** 长名单行省略且点开弹层可见全名，停用为小角标不撑破格子
- **AND** 末格为「添加子类」

#### Scenario: 资产页拖拽不误触滚动（第 17 批）
- **GIVEN** 资产账户超过一屏，需要滑动查看更多
- **WHEN** 长按某账户手柄上下拖动
- **THEN** 页面不随手指滚动，账户按实测行高跨行换位，松手顺序持久
- **AND** 拖到屏幕上下边缘时列表自动滚动，可继续与屏幕外账户换位

#### Scenario: 分类详情
- **GIVEN** 某分类有 300 条流水
- **WHEN** 点击该分类行
- **THEN** 弹窗显示最近若干条（带 LIMIT）流水的时间、金额与备注
- **AND** 不把 300 条全部加载进内存

**理由**：第 14 批补默认折叠与 chips，第 15 批修排序作用域真 bug，
第 16 批把四处排序收敛到同一实现并补实时让位，第 20 批换成熟库。
使用者 2026-11-23 用真机对照图指出本页观感明显弱于一羽记账，
并纠正「等宽图标网格必然留白大」的外推：留白大的根因是
`WrapRow` 按文本收缩 + 行尾留白 + 停用后缀拼进名称，不是固定列数网格。
本批按标题整块替换，MUST NOT 再追加一条 ADDED 覆盖旧文本（活文档 §5）。

---

## ADDED Requirements

### Requirement: 分类管理列表页采用一羽式布局

分类管理列表页 SHALL 采用蓝底 TopBar（返回 / 标题 / 右上「排序」入口 + ⋮ 菜单），
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
- **THEN** 蓝底 TopBar 与支出 / 收入 Tab 可见、无常驻说明灰字
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

### Requirement: 独立分类排序页与临时顺序

系统 SHALL 提供独立的「分类排序」全屏页（Navigation 路由），
由分类列表页右上「排序」入口进入，MUST NOT 新增第 5 个底部 Tab。
排序页 SHALL 按一羽记账图 3 **1:1** 组织：一级横排为 5 列图标、选中项带蓝底与角标；
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
白卡 SHALL 用与一级区明显不同的浅色底（浅蓝灰）标注二级区域、二级格仍为白底圆角；
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

### Requirement: 分类选择页采用分组导航与等宽网格版式

系统 SHALL 提供「分类选择」页，借用一羽「添加二级分类」页的版式：
顶部上下文（父级与收支类型）+ 名称输入 + 左侧分组导航 + 右侧等宽图标网格 +
顶部搜索 +「继续添加」。从列表页展开区末位「添加子类」进入时，
父级与收支类型 SHALL **预先带入**并在标题写明「添加到 〈父级〉› 〈收支类型〉」，
MUST NOT 要求用户重新选择层级与搜索父级。
同一版式 SHALL 可用于「停用项管理」批量挑选停用扩展项。

「继续添加」保存后 SHALL 只清空名称并保留父级、收支类型与输入焦点，
每次保存 SHALL 走独立事务，MUST NOT 攒批写入、MUST NOT 丢失已填上下文。
系统 SHALL 校验同父级下二级名称不重复，冲突时 SHALL 就地提示且 MUST NOT 写库。
本页 SHALL 只借版式：MUST NOT 改成「从预设分类库挑一个分类加进来」，
MUST NOT 改动 `assets/default_categories.json` 的 ID 规则、
启用 / 停用语义或备份整表替换语义。
输入框 SHALL 用 `placeholder` 与 `heightIn(min = 40.dp)`，
MUST NOT 用固定 `height`、MUST NOT 用浮动 `label`。

#### Scenario: 从餐饮行添加子类
- **GIVEN** 展开「支出 / 餐饮」并点末位「添加子类」
- **WHEN** 选择页打开
- **THEN** 父级「餐饮」与「支出」已预填，左侧导航停在餐饮，顶部搜索可用
- **AND** 输入「夜宵」保存后可直接继续输入下一个名称

#### Scenario: 同父级重名被拒
- **GIVEN** 「餐饮」下已有「正餐」
- **WHEN** 在选择页再次提交「正餐」
- **THEN** 就地提示重名、分类树不变、无新行写入

**理由**：预置清单 v2 有 91 个二级、59 个停用扩展项，逐条新建是高频路径；
父级预填 + 连续添加把「选层级 → 搜父级 → 保存 → 重来」压缩为一次进入连续输入。
使用者选的是「借版式做选择页」（A），不是改数据语义（B）。

---

### Requirement: 分类管理彩色图标目录与缺项兜底

系统 SHALL 提供 `CategoryIconCatalog` 作为分类管理三页图标的**唯一出口**，
以**编译期显式常量表**（`when (iconKey) -> R.drawable.*`）登记随包本地
VectorDrawable，MUST NOT 用字符串反射 / 运行时查表映射到 `ImageVector`。
图标资源 SHALL 为 **Tabler Icons outline v3.34.0**（pin 到具体 tag，非 `@latest`，
MIT，`Copyright (c) 2018-2026 Tabler`），只取所需 SVG 转成本地 drawable；
来源、pin 版本、许可证与传递依赖审计 SHALL 落在 change 的 `design.md`，
MIT 全文与版权声明 SHALL 落在 `assets/NOTICE` 随 APK 分发。
MUST NOT 复制第三方记账 App 的图标资源，
MUST NOT 引入 `material-icons-extended`、第三方图标 AAR 或任何含网络行为的
图片加载库，MUST NOT 新增任何 Gradle 依赖。

`assets/default_categories.json` 每行 SHALL 携带可选 `iconKey`，
`SeedCatalog` SHALL 解析并写入 `categories.iconName`；
新字段 SHALL 可选，缺字段的旧 JSON / 旧备份 MUST 仍可解析。
补字段 MUST NOT 改动 `id / name / type / parentId / level / enabled / sortOrder`
中任何一个值，MUST NOT 改写 `colorHex`。
图标配色 SHALL 由纯 JVM `CategoryIconTints` 编译期常量表按**一级领域分组、
同组同色、低饱和**提供，MUST NOT 逐图标随机高饱和；
排序页 SHALL 降饱和为中性灰；MUST NOT 让配色参与统计口径。

**覆盖安装兜底**：`iconName` 为空时系统 SHALL 按预置分类 id 回查
`CategoryIconKeys.keyOfPreset(id)`，使**库已存在的覆盖安装也能立刻显示图标**，
MUST NOT 为此写库、改 Entity、改备份 DTO 或升数据库版本。
用户手工新建分类的 id 不在预置表内，SHALL 回退中性圆 + 中文首字。
目录或缺项表查不到的分类 SHALL 回退中性圆 + 首字，页面 MUST NOT 有空白格、
MUST NOT 崩溃、MUST NOT 因单个资源缺失导致编译失败。
统计页环形图扇区取色与 `RecordDao` 的 `categoryColorHex` 投影 SHALL 保持零变化。

#### Scenario: 清单补字段不改数据语义
- **GIVEN** 清单 v4 的 106 行补上了 `iconKey`
- **WHEN** 全新安装并核对分类表
- **THEN** 所有 id / 名称 / 层级 / 启用态 / sortOrder 与补字段前完全一致
- **AND** `colorHex` 仍为原值，统计扇区颜色不变

#### Scenario: 覆盖安装也有图标
- **GIVEN** 用户从 v0.2.35 覆盖安装，库内 106 行 `iconName` 仍是空串
- **WHEN** 打开分类管理列表页
- **THEN** 预置分类按 id 回查目录正常显示彩色图标
- **AND** 数据库版本号不变、无任何写库操作、备份 DTO 不变

#### Scenario: 图标资源缺项
- **GIVEN** 用户手工新建了一个分类，或某 iconKey 不在编译期目录里
- **WHEN** 打开三页网格
- **THEN** 该格显示中性圆 + 首字，页面无空白格、无崩溃
- **AND** 记一笔快捷栏 / 明细列表行 / 账户详情 / 统计页的分类标识仍是中性圆 + 首字

#### Scenario: 许可声明随包
- **GIVEN** 构建出的 APK
- **WHEN** 查看 `assets/NOTICE` 与 change 的 `design.md`
- **THEN** 含 Tabler 的来源、pin 版本、MIT 全文与版权行
- **AND** Gradle 依赖表无任何新增条目

**理由**：`iconName` / `colorHex` 字段自阶段 1 就预留且全为空，
放行彩色图标必须补一份图标数据；把配色与 `colorHex` 分离，
是为了不让管理页的观感改动污染已锁死的统计口径。
覆盖安装兜底是为了不把「零改表可覆盖安装」写成一句空话。

---

### Requirement: 记一笔与筛选复用分类网格选择组件

系统 SHALL 提供一份**只读**共享分类选择组件 `CategorySelectSheet`，
版式与「分类选择」页一致（顶部搜索 + 左侧一级分组导航 + 右侧 **4 列**等宽彩色图标网格），
但 MUST NOT 含名称输入框、MUST NOT 含「继续添加」/ 保存按钮、
MUST NOT 暴露任何写库路径。记一笔 `FormRow("分类")` 的全量选择器
与明细筛选面板 `PickRow("分类")` 的全量单选弹层 SHALL 复用**同一**组件，
MUST NOT 各写一份网格实现。

组件 SHALL 按收支类型过滤分类；数据源 MUST NOT 用 `filter { it.enabled }`
把停用项删掉，未归档的停用一级 / 二级 SHALL 全部可见可搜并带「停用」角标，
归档项 SHALL 不进选择器（沿用基线「停用分类在全量选择器中仍可选」）。
搜索命中 SHALL 跨组平铺。**网格 tile 与搜索结果的显示名 SHALL 一律用裸名**，
MUST NOT 拼父级前缀——左侧分组导航已表明父级，拼成「餐饮 / …」会把 5 列格单行省略成
只见一级名（使用者第 4 轮第 5 条 / Q3A）。选中后**回填筛选侧的 `displayName`
MUST 仍经 `CategoryDisplay` 拼成「一级 / 二级」**，MUST NOT 改动第 29 批由
统计占比图例、扇区下钻、筛选面板、明细筛选 chip 四处共用的显示名契约。
跨组搜索时不同父级下的同名二级 MAY 显示不出归属，这是 Q3A 明确接受的代价。

点任一项 SHALL **立即选中并关闭弹层**，MUST NOT 加二次确认；
记一笔侧 SHALL 回填 `categoryId` / `rootCategoryId` 并保持快捷栏当前选中项兜底，
筛选侧 SHALL 回填 `rootId` / `categoryId` 与只读展示名，
并保留既有「全部分类」清空项与一级 / 二级层级区分（共享组件 SHALL 用显式 `level` 字段分派，
MUST NOT 再拼 `r:` / `c:` 字符串前缀）。
固定高度的弹层 MUST NOT 撑高筛选面板本体，面板行网格仍 SHALL 遵守
「筛选面板统一网格与紧凑输入框」。

「分类选择」页（添加子类 / 父级预填 / 继续添加 / 同父级重名校验）
SHALL 保持原功能不变，MUST NOT 因抽取共享组件而丢失写路径。

**第 5 轮选择器密度优化（v0.2.38 装机反馈，1A / 2B）**：共享组件右侧网格 SHALL
为 **4 列**等宽格（`columns = 4`），MUST NOT 用 5 列——5 列时左侧 96dp 导航列
挤压后每格文字区仅约 37.6dp，4 个中文字名被单行省略成 2 字（使用者实测）。
左侧导航 SHALL 宽 80dp、与网格间距 SHALL 4dp、
Dialog 外层与内容层 padding SHALL 各 8dp（收窄累计非内容宽度），
使右侧实得约 244dp、每格文字区约 57dp，4 字名 SHALL 完整可见。
**范围锁定**：只改 `CategorySelectSheet.kt` 这一份只读组件；
分类管理列表页（`CategoryManagerDialog.kt` 传 `columns = 5`）、
分类排序页（两套 `GridCells.Fixed(5)`）、原「分类选择」页（本就是 4 列）、
共用 `CategoryIconGrid` / `CategoryTileCell` 的格高与行距 SHALL 一律零改动
（组件为三页共用，格高全局改会外溢，2B 明确不含紧凑格高）。
7 字长名（如「赡养·代缴水电」）在 57dp 下 SHALL 仍单行省略，
MUST NOT 换行撑高格子、MUST NOT 改共用格高；点格仍 SHALL 立即选中并关闭。

#### Scenario: 二级裸名显示但回填仍带父级

- **GIVEN** 筛选面板打开分类选择器，左侧选中「餐饮」分组
- **WHEN** 查看右侧网格、点「外卖」格
- **THEN** 网格格显示裸名「外卖」，MUST NOT 显示「餐饮 / 外卖」或被单行省略成「餐饮 / …」
- **AND** 筛选 chip 的 `displayName` 仍为「餐饮 / 外卖」，与统计 / 明细口径一致
- **AND** 搜索结果里的二级同样显示裸名

#### Scenario: 4 列下四字名完整可见

- **GIVEN** 360dp 屏打开记一笔「分类 ›」，左侧选中「餐饮」分组
- **WHEN** 查看右侧二级网格
- **THEN** 网格为 4 列等宽格，每格文字区约 57dp，「外卖早点」一类 4 字名完整可见，
  MUST NOT 被单行省略成 2 字
- **AND** 一级格与二级格仍同处首行（`gridCells` 只补末行空位），无整行空白断层
- **AND** 分类管理列表页与排序页仍是 5 列，格高与行距与 v0.2.38 完全一致
- **AND** 7 字长名仍单行省略，格子高度不撑高

#### Scenario: 记一笔从网格选停用项

- **GIVEN** 新记一笔，默认快捷栏只有启用一级
- **WHEN** 点「分类 ›」→ 搜索「话费」→ 点「居住 / 话费（停用）」
- **THEN** 弹层立即关闭、无二次确认，`categoryId = 4007`、`rootCategoryId = 4`
- **AND** 快捷栏临时显示「居住」一级与选中的「话费」，保存成功
- **AND** 网格中「话费」显示彩色图标、「停用」角标可见

#### Scenario: 筛选面板选分类并回填

- **GIVEN** 明细页筛选面板，库内含停用项与归档项
- **WHEN** 点「分类」行 → 在共享网格里选「餐饮 / 正餐」→ 应用并再次打开面板
- **THEN** `rootId` / `categoryId` 正确回填、只读展示名为「餐饮 / 正餐」
- **AND** 归档项未出现、分类条件未被静默清空、面板高度不随选项数增长

#### Scenario: 只读且添加子类零回归

- **GIVEN** 两处选择器与分类选择页
- **WHEN** 分别打开
- **THEN** 两处选择器无名称输入框、无「继续添加」/ 保存按钮、无任何写库路径
- **AND** 从列表页「餐饮」行点「添加子类」仍可父级预填、连续添加、重名被拒

**理由**：使用者 2026-11-23 装机体验 v0.2.36 后反馈
「添加子类页面可以作为记一笔里面还有筛选里面分类选择页面的样式，
只是把添加的输入框和按钮去除，应该比原来的分类选择好」；
记一笔原「分组 chips + 搜索」与筛选原「竖排单选列表」都不如分组导航 + 等宽网格。
抽取只读变体而非改造原页，是为了满足使用者第 3 问「保留」——
新增子类 / 连续添加属高频写路径，MUST NOT 被顺手删掉。
