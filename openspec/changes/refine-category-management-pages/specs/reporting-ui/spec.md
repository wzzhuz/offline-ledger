# Delta for reporting-ui

## MODIFIED Requirements

### Requirement: 管理类页面入口固定且列表与编辑分离

分类管理、模板与周期记账、预算管理、回收站这四个管理类页面 SHALL 采用
「**顶部固定新增入口 + 惰性列表 + 编辑独立视图**」结构：
新增 / 编辑入口 SHALL 固定在 TopBar 或列表顶部，MUST NOT 因列表条目增多而下移；
列表 SHALL 用 `LazyColumn` 一类惰性容器承载，MUST NOT 全量 compose；
列表行 MUST NOT 用多个 48dp `TextButton` 横排撑高，行操作 SHALL 收进紧凑菜单；
编辑视图 SHALL 与列表状态分离，编辑既有项 SHALL 完整回填。
回收站每行 SHALL 保留时间、交易对方、分类、账户与备注信息，
MUST NOT 为压缩高度删除这些字段；右侧操作 SHALL 横排紧凑排布。
全仓选中态 SHALL 用 `FilterChip` / `selected` 容器底色表达，
MUST NOT 用 `● ` 文字前缀；管理类页面的输入框 SHALL 用 `placeholder` 而非浮动 `label`，
高度 SHALL 用 `heightIn(min)` 调整，MUST NOT 用固定 `height` 裁切占位文字。

**全站列表分割规范（第 15 批）**：明细、分类、资产、模板、预算、回收站这六类列表
SHALL 共用 `ui/components/LedgerUiParts.kt` 的 `ListDivider()` 单一来源，
1dp / `Neutral.copy(alpha = 0.10f)` / 距屏幕 16dp，MUST NOT 各页各画一遍 Box。
行高 SHALL 按信息量分档（明细 60dp / 资产 56dp / 管理页 44dp），
MUST NOT 一刀切；可点区 MUST ≥40dp。资产页账户行 SHALL 与明细页同为纯行，
MUST NOT 用 `Card` 底色 / 阴影或 1dp 间距表达边界（第 16 批修正：第 15 批只
补了分割线、没拆 Card，卡片内边距仍把内容往里收一圈，视觉量级小于明细页）。

**全站排序交互统一（第 20 批整块修订）**：分类一级、资产账户、模板 SHALL 共用
`sh.calvin.reorderable:reorderable:3.1.0`（Apache-2.0、纯 UI 库、无运行时网络行为）
与 `ui/components/ReorderListSupport.kt`；资产页 SHALL 改 `LazyColumn`，
MUST NOT 用 `Column + verticalScroll`。库 SHALL 负责拖动中 `animateItem()` 让位补间、
长按窗口、pointer pass、边缘按距离变速自动滚动；业务层 SHALL 只在 `onMove` 改内存
顺序、松手一次性落库。

**本批整块修订（替换第 16 / 20 批 `WrapRow` 自适应 chips 表述）**：
分类列表页二级 SHALL 为固定列数等宽图标网格（默认 5 列、窄屏 4 列，
`Row + weight(1f)`、固定格高、均匀 gutter、长名单行 ellipsis，
末格为「添加子类」），MUST NOT 再用按文本宽度收缩的自适应 chips，
MUST NOT 把「（停用）」拼进名称，MUST NOT 让短名行的剩余留白碎在行尾，
MUST NOT 用 `LazyVerticalGrid` 嵌进 `LazyColumn.item {}` 造成同方向嵌套滚动，
MUST NOT 用 `@OptIn(ExperimentalLayoutApi)` 的 `FlowRow`（T12），
MUST NOT 用 `Card` 底色 / 阴影表达格子边界。
分类排序页内选中一级的二级 SHALL 临时排成一维列表以适配 `reorderable`
的一维模型并支持点按拖；列表页仍 SHALL 为等宽图标网格。
上移 / 下移 SHALL 在列表页二级操作弹层保留为无障碍兜底
（TalkBack 无法执行拖动手势），MUST NOT 作为主入口。
分类管理新增的排序页与分类选择页 SHALL 仍为全屏独立页（Navigation 路由），
MUST NOT 做成内容受限的小弹窗，MUST NOT 新增第 5 个底部 Tab；
排序页的 `draftOrder` 临时顺序 SHALL 与已落库 `sortOrder` 分离，
放弃 MUST NOT 写库，保存 SHALL 一次性全量写回且 MUST NOT 丢项。
分类列表页一级 SHALL 保持竖排 `LazyColumn`，MUST NOT 把 15 个一级横排塞进一屏；
底部胶囊入口与大 `+` FAB SHALL 在列表滚动时保持可见。

依赖取舍按 2026-11-17 使用者澄清：判断标准是「是否默认回传厂商且用户不可控」，
不是「依赖数量」；仍 MUST 记录坐标、许可证与传递依赖审计，仍 MUST NOT 引入
广告 / 埋点 / 崩溃上报 / 远程配置 SDK。零表结构变更。

#### Scenario: 分类增多不推走入口

- **GIVEN** 系统有 10 个一级分类与 20 个二级分类
- **WHEN** 打开分类管理页并滚动列表
- **THEN** 新增入口与大 `+` FAB 仍固定可见

#### Scenario: 六页分割线一致

- **GIVEN** 依次打开明细、分类、资产、模板、预算、回收站
- **WHEN** 查看相邻两行之间
- **THEN** 均为同一颜色 / 厚度 / 16dp 内缩的细线，无卡片贴片或无线留白

#### Scenario: 等宽网格不留碎白
- **GIVEN** 「居住」下有 11 个二级，含 7 字长名与停用项
- **WHEN** 展开「居住」
- **THEN** 二级呈 5 列等宽格、gutter 均匀、行尾无大片碎留白
- **AND** 长名单行省略、停用为小角标、末格为「添加子类」

#### Scenario: 排序页放弃不落库
- **GIVEN** 在排序页拖动过一级与二级但未保存
- **WHEN** 按返回并选择放弃
- **THEN** 库内 `sortOrder` 零变化，重进列表页顺序不变
- **AND** 底部 4 Tab 结构与进入前一致

**理由**：第 15 批把「统一网格」从「左边界 + 行高量级」补完第三半——
行与行之间的边界也要单一来源；第 12 批只同步了容器骨架，没同步分割写法。
使用者 2026-11-23 用真机对照图纠正了「等宽图标网格必然留白大」的外推：
留白来自 `WrapRow` 按文本收缩 + 行尾余量 + 停用后缀拼进名称，
不是固定列数网格本身。本批按标题整块替换，
MUST NOT 只追加 ADDED 而让两条相反要求并存（活文档 §5）。
