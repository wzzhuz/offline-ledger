# reporting-ui · delta（第 77 批 + 第 80 批，最终形态，一并归档）

> 第 80 批整块改写：真机反馈「更多」这一层增加点击、账户因默认账户本就低频，
> 按一羽改为键盘上方单行 5 枚 chips 各自直达底部 sheet。
> 原 ADDED「记一笔低频项集中在更多面板」作废，替换为下面两条 ADDED。
> 既有 Requirement 标题沿用历史名称（归档匹配基准），正文均为最终行为。

## MODIFIED Requirements

### Requirement: 记一笔布局紧凑且首屏可见备注与收款方

「记一笔」首屏 SHALL 只呈现：类型 Tab、金额、日期时间无标签副行、分类快捷行
（带 `›` 整行可点全量选择器）、收款方与描述的**无边框内联行**，以及固定底部区的
键盘上方**单行 5 枚 chips**、键盘、保存按钮。账户 SHALL 从首屏表单行移到 chips
（默认账户预填、低频）；备注与「不计入收支」、模板、标签、优惠/手续费
MUST NOT 占首屏表单区。收款方与描述 MUST 仍在首屏可见（描述是明细主标题来源，
MUST NOT 藏进面板）。收款方联想条 SHALL 只在输入框聚焦或有内容时出现，
MUST NOT 常驻。活跃 change `record-category-picker-and-root-select` 的
「点一级即按一级记、再点收起二级、分类行带 ›」补述继续有效。

#### Scenario: 首屏五块且不拥挤

- **GIVEN** 用户有 6 个模板、10 个账户、20 条历史收款方
- **WHEN** 打开记一笔且不做任何滚动
- **THEN** 首屏看到金额、分类快捷行、收款方/描述内联行、键盘上方单行 chips、保存
- **AND** 看不到账户表单行、模板表单行、标签行、备注输入框、常驻联想行

#### Scenario: 旧内容不因收起丢失

- **GIVEN** 编辑一条带备注、优惠、两个标签、且不计入收支的旧流水
- **WHEN** 打开编辑态
- **THEN** 对应 chips 在 label 上显示可读摘要，点开 sheet 可修改全部字段
- **AND** MUST NOT 让任何旧值不可见或不可修改

### Requirement: 记一笔采用统一表单网格与有上限的快捷区

「记一笔」SHALL 保留固定 52dp 标签列与细分割线，同区域 MUST NOT 混用
带框输入框、无边界文本按钮与芯片作为顶层块。账户与模板 SHALL 不再作为
首屏表单按钮或平铺快捷 chips 存在，统一由键盘上方 chip 直达底部 sheet；
分类快捷栏 SHALL 保留（低摩擦优点）。金额 SHALL 单独大字号展示。
「最近收款方 / 常用账户各最多 4 个」「模板每行 3 个两行铺开」的
快捷区上限条款随本 change 作废。

#### Scenario: 账户 chip 显示默认账户

- **GIVEN** 默认账户为「工商银行 (1122)」且为信用卡
- **WHEN** 打开记一笔
- **THEN** 账户 chip 显示该账户名，点开两列 sheet 可按「1122」搜索、可见余额
- **AND** 首屏表单区 MUST NOT 再出现「账户」行

#### Scenario: 没有模板时

- **GIVEN** 尚未创建任何模板
- **WHEN** 点键盘上方「模板」chip
- **THEN** sheet 显示「还没有模板」，手工记账路径完全不受影响

### Requirement: 模板快捷区两行铺开且不横向滚动

**第 80 批归档修订**：标题沿用历史名称，实际形态已改为 chip 直达。
常用模板 SHALL 收敛为键盘上方「模板」chip + 一羽式底部 sheet，
sheet SHALL 一行一条给出名称、备注与预填金额，MUST NOT 再以每行 3 个、
两行等宽铺开，MUST NOT 经过「更多」层，MUST NOT 用居中 `AlertDialog` 纯文字列表。
点一项 SHALL 预填草稿并关闭 sheet，MUST NOT 自动落库。

#### Scenario: 两个模板两次点击完成预填

- **GIVEN** 用户有「午餐」「早餐」两个模板
- **WHEN** 点「模板」chip，再点「午餐」
- **THEN** 表单按模板预填、sheet 关闭，可继续改金额后保存
- **AND** 全过程 2 次点击，MUST NOT 出现「更多 › → 模板 › → 全部模板」三层

### Requirement: 记一笔各表单行高度量级一致

各表单行内容高度 SHALL 落在 **32–44dp** 区间。收款方与描述的
`OutlinedTextField` SHALL 把 `colors` 的 border 与 container 设为
`Color.Transparent`，MUST NOT 保留整圈描边；MUST NOT 改用 `BasicTextField`
或 filled 变体。一键清空图标、placeholder、`heightIn(min = 40.dp)` 与
`bodyMedium` 字号 SHALL 保持不变。键盘上方 chips 与收款方联想条的
每一个可点项 SHALL 用 `heightIn(min = 40.dp)` 容器承载，字号 SHALL NOT
低于 `labelMedium`；MUST NOT 以「简洁 / 降噪」为由把可点区压到 40dp 以下。

#### Scenario: chips 与联想条一次点中

- **GIVEN** 打开记一笔且收款方输入框聚焦
- **WHEN** 点键盘上方任一 chip 或联想条里的任一收款方
- **THEN** 一次点击即打开对应 sheet 或填入输入框，SHALL NOT 误触相邻项
- **AND** 可点区不低于 40dp、字号为 `labelMedium`

### Requirement: 记一笔低频项收起且输入框不撑高边框

**第 80 批归档修订**：低频项不再统一收进「更多」面板，改为各自 chip 直达。
模板、账户、标签、优惠/手续费、备注 +「不计入收支」SHALL 由键盘上方 chips
各自直接打开底部 sheet，MUST NOT 出现「更多 ›」入口或 `MorePanel` 容器，
MUST NOT 出现两个及以上并列折叠入口。每个 chip SHALL 在编辑态有值时显示
可读摘要。sheet SHALL 用 `Dialog` + 底部对齐 `Surface` 实现，
MUST NOT 引入 `ExperimentalMaterial3Api` 的 `ModalBottomSheet`。
文本输入 SHALL 用 placeholder 而非浮动 label。
日期与时间 MUST 仍为紧随金额后的无标签可点副行（不算低频项）。

#### Scenario: 去掉中间层

- **GIVEN** 新建一笔流水
- **WHEN** 想添加优惠
- **THEN** 直接点「优惠」chip 即得输入 sheet，MUST NOT 先点「更多 ›」
- **AND** 首屏不再出现「▸ 添加优惠」与「更多（备注 · 不计入收支）」两行

#### Scenario: 编辑态 chip 带旧值

- **GIVEN** 编辑一条备注「同事聚餐 AA」、优惠 5 元、两个标签的旧流水
- **WHEN** 查看键盘上方 chips
- **THEN** 备注 / 优惠 / 标签三枚 chip 各自显示摘要，MUST NOT 显示成空白「备注 / 优惠 / 标签」而让人以为旧值丢失

### Requirement: 记一笔提供模板快选且两步内完成

「记一笔」SHALL 通过键盘上方「模板」chip 提供模板预填，
从打开记一笔到**完成表单预填** SHALL 不超过 2 次点击，MUST NOT 弹二次编辑。
模板语义 SHALL 为**预填草稿、不直接生成流水**（与既有 `applyTemplate()` 一致；
主 spec 旧文「点一下直接生成流水并关闭」为历史腐烂，本 change 以代码为准）。
计算器键盘交互与两级分类快选 SHALL 保持不变。

#### Scenario: 两次点击预填午餐

- **GIVEN** 用户建了「午餐」模板
- **WHEN** 点「模板」chip → 点「午餐」
- **THEN** 表单按模板预填，可继续改金额后保存，MUST NOT 自动落库

### Requirement: 记一笔提供收款方与常用收款方补全

「记一笔」SHALL 在首屏提供收款方**无边框内联**自由文本输入；
系统 SHALL 从既有流水 `DISTINCT` 出常用收款方，并 SHALL 在**收款方输入框正下方**
提供一条 40dp 内联联想条：输入框为空时 SHALL 显示常用、有输入时 SHALL 改为
模糊联想，两者互斥共用同一条，点一下 SHALL 填入输入框。
联想条 SHALL 仅在输入框聚焦或有内容时出现，MUST NOT 常驻首屏、
MUST NOT 塞进 chips 弹层或「更多」面板（脱离输入上下文）。
明细行 SHALL 展示收款方。收款方 MUST NOT 建成账户、MUST NOT 引入复式分录。

#### Scenario: 输入时联想同屏可见

- **GIVEN** 账本里已有「盒马」「地铁」
- **WHEN** 点收款方输入框并输入「盒」
- **THEN** 输入框正下方立刻出现「盒马」联想条，点一下填入
- **AND** 联想条与输入框同屏，MUST NOT 需要上滑或打开任何面板

#### Scenario: 未聚焦不占空间

- **GIVEN** 刚打开记一笔、收款方输入框未聚焦且为空
- **WHEN** 查看首屏
- **THEN** 收款方行下方无联想条，MUST NOT 常驻一行 40dp 区域

## ADDED Requirements

### Requirement: 记一笔低频项由键盘上方芯片直达

「记一笔」SHALL 在数字键盘上方提供**固定单行 5 枚等分 chips**：
账户 / 模板 / 备注 / 标签 / 优惠（转账时为手续费）。
「不计入收支」开关 SHALL 并入备注 sheet（它本就与备注同一行），
MUST NOT 为它新增第 6 枚 chip。每枚 SHALL 用 `heightIn(min = 40.dp)` 容器、
`RoundedCornerShape(12.dp)` 与 `labelMedium` 字号，点一下 SHALL 直接打开
对应底部 sheet，MUST NOT 经过「更多」这一层。chips 条 SHALL 位于滚动表单区之外、
与键盘同属固定底部区，MUST NOT 挤压首屏。
转账态账户 chip SHALL 显示「转出/转入」，其 sheet 内 SHALL 用两个 `FilterChip`
切换转出 / 转入，MUST NOT 新增第 5 个底部 Tab。
各 sheet 关闭后 SHALL 保留金额、分类、账户、收款方、描述与保存按钮的全部状态。

#### Scenario: 单行五枚且账户低频

- **GIVEN** 用户设置了默认账户
- **WHEN** 打开记一笔
- **THEN** 键盘上方恰好 5 枚 chips，账户 chip 显示默认账户名但不要求选择
- **AND** 点任一枚直接进入对应 sheet，MUST NOT 出现「更多 ›」

#### Scenario: 转账态账户分段

- **GIVEN** 类型切到转账
- **WHEN** 点「转出/转入」chip
- **THEN** sheet 顶部出现转出 / 转入两个分段，分别选择后各自回填
- **AND** 底部仍是明细 / 资产 / 统计 / 设置 4 个 Tab

#### Scenario: sheet 不遮挡主流程

- **GIVEN** 已输入金额、选好分类、填了收款方
- **WHEN** 打开并关闭备注 sheet
- **THEN** 金额、分类、收款方、描述、保存按钮状态全部保留

**理由**：第 77 批把低频项全塞进「更多」是降噪过头——层数比空白更伤低摩擦，
真机实测模板要 3～4 次点击。一羽的做法是「入口常驻但内容按需弹出」：
入口只占一行 chips，不展开就不占内容区。默认账户机制早已存在，
账户本就不该在首屏占一行。

### Requirement: 收款方联想条与输入框同屏

系统 SHALL 在收款方输入框与其联想条之间保持**零中间层**：
联想条 SHALL 由输入框的聚焦状态与内容驱动，MUST NOT 由 chips 触发、
MUST NOT 由「更多」面板承载、MUST NOT 缓存成常驻列表。
`RecordEditorViewModel` 的 `counterparties` / `counterpartySuggestions`
两条数据链 SHALL 保持不变，MUST NOT 新建查询或引入网络请求。

#### Scenario: 零新数据路径

- **GIVEN** 本批落地后
- **WHEN** 核对 `RecordEditorViewModel`
- **THEN** 仍只有 `distinctCounterparties()` 与既有模糊联想，无新增 DAO 方法

**理由**：联想只在输入时有用，必须与输入框同屏；塞进 chip 弹层会多一次点击
且看不到输入上下文。

### Requirement: 全局主题支持动态色且可一行回退

`LedgerTheme` SHALL 在 API 31+ 借用系统动态色，API ≤ 30 SHALL 回退静态浅色板，
MUST NOT 在低版本崩溃。`LedgerTheme` SHALL 暴露 `dynamicColor: Boolean = true`
参数，传 `false` SHALL 完整回到第 70 批静态板，MUST NOT 需要改第二处。
**第 78 / 79 批归档补述**：动态浅色 SHALL 由 `dynamicWithNeutralSurface()`
只借 primary / secondary / tertiary 等强调色，中性槽位（background /
surface / surfaceVariant / surfaceContainer* / outlineVariant）MUST 套回静态值，
MUST NOT 让页面底与卡片容器同色；「两套蓝」已由可观察 `LedgerColors` 收敛，
MUST NOT 再登记为「允许出现两套蓝」。`LedgerColors.ExpenseRed / IncomeGreen`
与配色反转开关 MUST NOT 受动态色影响。深浅模式以归档的
「全局主题支持深浅模式且语义色全程可读」为准。

#### Scenario: 低版本回退

- **GIVEN** 设备为 Android 10（API 29）
- **WHEN** 冷启动 App
- **THEN** 使用静态板正常渲染，无动态色调用

#### Scenario: 一行回退

- **GIVEN** 动态色下观感不接受
- **WHEN** MainActivity 把 `LedgerTheme(dynamicColor = false)`
- **THEN** 全 App 回到浅灰底 / 纯白卡 / 清新蓝，红绿语义与反转开关不变

#### Scenario: 中性槽位不随壁纸漂

- **GIVEN** 动态色启用且壁纸色调明显
- **WHEN** 打开统计页
- **THEN** 页面底仍是中性微灰、卡片仍是纯白，边界清晰
- **AND** 强调色随壁纸变化，中性底不变

### Requirement: 全 App 采用 edge-to-edge 且可点区避开系统栏

`MainActivity` SHALL 调 `WindowCompat.setDecorFitsSystemWindows(window, false)`
并把系统栏设透明；背景 SHALL 铺满屏幕，根内容 SHALL 用
`Modifier.safeDrawingPadding()` 避开系统栏与手势区。
`LedgerApp` 的 `Scaffold` SHALL 设 `contentWindowInsets = WindowInsets(0,0,0,0)`，
MUST NOT 与外层 safeDrawing 叠加双重 padding。
记一笔的独立 `Dialog` Window SHALL 单独对根 Column 应用
`statusBarsPadding()` 与 `navigationBarsPadding()`，
MUST NOT 让「记一笔」标题或底部保存按钮被系统栏遮挡。
键盘上方 chips 与各底部 sheet SHALL 同样避让手势区。

#### Scenario: 标题与 chips 不被遮挡

- **GIVEN** 全面屏手势导航设备
- **WHEN** 打开记一笔并点开备注 sheet
- **THEN** 标题完整可见、chips 与 sheet 的「完成」按钮在手势条上方完整可点

#### Scenario: 主页面不双重留白

- **GIVEN** 进入明细 / 统计 / 资产任一 Tab
- **WHEN** 查看顶部与底部留白
- **THEN** 内容只避让一次系统栏，MUST NOT 出现两倍 padding 的空白带
