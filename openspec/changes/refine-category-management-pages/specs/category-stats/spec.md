# Delta for category-stats

## MODIFIED Requirements

### Requirement: 分类图标不做字符串映射

分类管理三页（分类列表 / 分类排序 / 分类选择）SHALL 使用本地
`CategoryIconCatalog` 呈现彩色具象图标，图标资源 SHALL 以**编译期显式常量**
登记并随包分发；记一笔、明细、账户详情与统计页 SHALL 继续使用
`CategoryIcon` 的中性圆 + 分类名中文首字，MUST NOT 受本批影响。

系统 MUST NOT 把 `iconName` 字符串在运行时反射或查表映射到 `ImageVector`
（T14：任一图标名不存在会让整份映射表编译失败）。
目录中缺项的分类 SHALL 回退 `CategoryIcon` 中性圆 + 首字，
MUST NOT 崩溃、MUST NOT 出现整页空白、MUST NOT 因单个资源缺失导致编译失败。
`iconName` 字段 SHALL 原样保留并作为目录键使用；图标配色 SHALL 由目录的
`iconTintHex` 提供，MUST NOT 改写参与环形图扇区取色与 DAO 投影的 `colorHex`。
图标资源 MUST NOT 复制第三方 App 的视觉资产；来源、许可证与传递依赖审计
SHALL 记录在 change 的 `design.md`「依赖与许可」节，MUST NOT 引入
含网络行为的图标加载库。

#### Scenario: 新增分类名不含已知图标名

- **GIVEN** 用户自定义一个分类，图标键是任意字符串或目录未收录
- **WHEN** 打开分类管理三页
- **THEN** 该格回退中性圆 + 中文首字正常显示
- **AND** 不因图标键不存在而编译失败或运行时崩溃

#### Scenario: 彩色图标不越界到记一笔

- **GIVEN** 分类管理列表页的「餐饮」显示彩色餐具图标
- **WHEN** 打开记一笔页与明细页
- **THEN** 同一分类仍是中性圆 +「餐」字
- **AND** 环形图扇区颜色与改动前一致

**理由**：原口径把「记一笔页图标显示效果差」的结论写成了全局禁令。
使用者 2026-11-23 明确「不引入图标是原来记一笔页面有图标显示效果差，
我们现在先看分类管理相关页面」，故适用范围收窄到管理页；
仍禁止字符串反射映射，是因为 T14 的编译期风险与页面无关。

---

### Requirement: 分类管理全量父级与排序

分类管理 SHALL 提供一级与二级排序，**排序作用域 SHALL 为
`(type, level, parentId)` 三元组**：一级只在同收支类型的一级之间排序，
二级只在所属一级内部排序，MUST NOT 把全部未归档分类当成一个全局数组交换相邻项。
**第 20 批整块修订（替换第 15-17 批自研拖拽表述）**：分类一级、资产页账户、
模板管理 SHALL 共用 `sh.calvin.reorderable:reorderable:3.1.0`（Apache-2.0、
Maven Central、纯 UI 库、无运行时网络行为）与
`ui/components/ReorderListSupport.kt` 的 `rememberKeyedReorder` 适配层：
MUST 用 `ReorderableItem` + 稳定 `key`，MUST 用 `longPressDraggableHandle` 长按手柄，
库 SHALL 负责拖动中 `animateItem()` 让位补间、pointer pass 顺序、长按窗口、
拖到边缘按距离变速自动滚动，MUST NOT 再自写 `pointerInput` / `scrollable` /
「拖拽期间禁用父滚动」/ 实测行高阈值；资产页容器 SHALL 为 `LazyColumn`
（库的边缘滚动仅支持 Lazy 容器），MUST NOT 用 `Column + verticalScroll`。
拖动中 `onMove` SHALL 只改内存顺序，松手 `onDragStopped` SHALL 一次性写入
显式 `sortOrder`，重开页面顺序 SHALL 保持；MUST NOT 使用跨应用
`dragAndDropSource/Target` 协议。资产页账户的展示仍按 `AccountType` 分组，
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
分类排序页内选中一级的二级 SHALL **临时排成一维列表**以适配
`reorderable` 的一维模型并支持点按拖，列表页仍 SHALL 为等宽图标网格；
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

#### Scenario: 拖动中实时让位
- **GIVEN** 一级「餐饮 / 零食 / 水果 / 交通」
- **WHEN** 长按「餐饮」手柄向下拖过「零食」
- **THEN** 拖动过程中「零食」立即让位、列表显示为「零食 / 餐饮 / 水果 / 交通」
- **AND** 松手后重进页面顺序不变（一次性落库，非松手才跳变）

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
排序页 SHALL 把一级横排为 5 列图标、选中项带蓝底与角标；
选中一级的二级 SHALL 收进圆角白卡并以灰阶单色显示，
MUST NOT 在排序页保留彩色（浏览页才上色）。
页面 SHALL 明示「长按拖动一级，点按拖动二级」并提供「保存」。

排序页 SHALL 维护 `draftOrder` 临时顺序状态，与已落库 `sortOrder` **完全分离**：
拖动 SHALL 只改 draft 内存状态，MUST NOT 逐次写库；
点「保存」SHALL 一次性按 `(type, level, parentId)` 三元组写回显式 `sortOrder`，
重开页面顺序 SHALL 保持；返回 / 放弃且存在未保存改动时 SHALL 明示「放弃修改」，
MUST NOT 静默写库，MUST NOT 让库内顺序发生任何变化。
状态写入 SHALL 用 `_state.update { }` 原子更新，
MUST NOT 用 `_state.value = _state.value.copy(...)` 散写（活文档 §7.11）。
保存 SHALL 复用既有 `commitRootOrder` / `commitChildOrder` / `moveCategory` 语义，
全量写回 MUST NOT 丢项；一级横排时拖动手柄 SHALL 挂在每个格子上，
`ReorderableItem` 的 key SHALL 与父 `items(key = ...)` 完全一致。

#### Scenario: 试排可放弃
- **GIVEN** 在排序页拖动了一级与二级、尚未点保存
- **WHEN** 按返回并选择放弃
- **THEN** 库内 `sortOrder` 零变化，重进列表页顺序与进入前完全一致

#### Scenario: 保存按三元组落库
- **GIVEN** 调整了支出一级顺序与「餐饮」下二级顺序
- **WHEN** 点「保存」并重进页面
- **THEN** 一级只在支出一级之间换位、二级只在「餐饮」内部换位
- **AND** 收入分类与另一父级的二级顺序完全不变

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
以编译期显式常量登记随包本地图标资源，MUST NOT 用字符串反射 / 运行时查表。
`assets/default_categories.json` 每行 SHALL 可携带 `iconKey` / `iconTintHex`，
`SeedCatalog` 与 `SeedData` fallback SHALL 同构解析；
新字段 SHALL 可选，缺字段的旧 JSON / 旧备份 MUST 仍可解析。
补字段 MUST NOT 改动 `id / name / type / parentId / level / enabled / sortOrder`
中任何一个值，MUST NOT 改写 `colorHex`。

图标资源 SHALL 为自有手绘或可商用矢量集，
来源、许可证与传递依赖审计 SHALL 落在 change 的 `design.md`；
MUST NOT 复制第三方记账 App 的图标资源，
MUST NOT 引入 `material-icons-extended` 或任何含网络行为的图片加载库。
MUST NOT 在图标资源未确认 / 未提供的情况下伪造 106 个矢量资源；
未覆盖项 SHALL 回退中性圆 + 中文首字并显式列为真机待验。
统计页环形图扇区取色与 `RecordDao` 的 `categoryColorHex` 投影 SHALL 保持零变化。

#### Scenario: 清单补字段不改数据语义
- **GIVEN** 清单 v4 的 106 行补上了图标字段
- **WHEN** 全新安装并核对分类表
- **THEN** 所有 id / 名称 / 层级 / 启用态 / sortOrder 与补字段前完全一致
- **AND** `colorHex` 仍为原值，统计扇区颜色不变

#### Scenario: 图标资源缺项
- **GIVEN** 目录只覆盖了一级 15 与部分二级
- **WHEN** 打开三页网格
- **THEN** 未覆盖分类显示中性圆 + 首字，页面无空白格、无崩溃
- **AND** 该缺项被登记为真机待验，MUST NOT 被写成「图标已对齐」

**理由**：`iconName` / `colorHex` 字段自阶段 1 就预留且全为空，
放行彩色图标必须补一份图标数据；把配色与 `colorHex` 分离，
是为了不让管理页的观感改动污染已锁死的统计口径。
