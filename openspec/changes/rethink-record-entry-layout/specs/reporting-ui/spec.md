# reporting-ui · delta

## MODIFIED Requirements

### Requirement: 记一笔布局紧凑且首屏可见备注与收款方

「记一笔」首屏 SHALL 只呈现：类型 Tab、金额、日期时间无标签副行、分类快捷行
（带 `›` 整行可点全量选择器）、**一枚显示默认账户的按钮**、收款方与描述的
**无边框内联行**、「更多 ›」入口、键盘、保存按钮。备注与「不计入收支」
MUST NOT 占首屏；模板、标签、优惠/手续费、收款方联想 chips MUST NOT 占首屏。
收款方与描述 MUST 仍在首屏可见（描述是明细主标题来源，MUST NOT 藏进面板）。
活跃 change `record-category-picker-and-root-select` 的「点一级即按一级记、
再点收起二级、分类行带 ›」补述继续有效。

#### Scenario: 首屏只有五块视觉主体

- **GIVEN** 用户有 6 个模板、10 个账户、20 条历史收款方
- **WHEN** 打开记一笔且不做任何滚动
- **THEN** 首屏看到金额、分类快捷行、账户按钮、收款方/描述内联行、保存
- **AND** 看不到模板 chips、账户 chips 行、常驻联想行、标签行、备注输入框

#### Scenario: 旧内容不因折叠丢失

- **GIVEN** 编辑一条带备注、优惠、两个标签的旧流水
- **WHEN** 打开编辑态
- **THEN** 「更多 ›」入口显示摘要，点开可修改全部字段，MUST NOT 让旧值不可见

### Requirement: 记一笔采用统一表单网格与有上限的快捷区

「记一笔」SHALL 保留固定 52dp 标签列与细分割线，同区域 MUST NOT 混用
带框输入框、无边界文本按钮与芯片作为顶层块。账户与模板的全量选择
SHALL 各由**一枚显示当前值的按钮**触发，MUST NOT 平铺快捷 chips；
分类快捷栏 SHALL 保留（低摩擦优点）。金额 SHALL 单独大字号展示。
「最近收款方 / 常用账户各最多 4 个」「模板每行 3 个两行铺开」的
快捷区上限条款随本批作废，由全量面板承载。

#### Scenario: 账户按钮含卡号与卡种

- **GIVEN** 默认账户为「工商银行 (1122)」且为信用卡
- **WHEN** 查看记一笔首屏
- **THEN** 账户按钮显示该名称与 12dp「信」角标，点开全量面板可按「1122」搜索

#### Scenario: 没有模板时

- **GIVEN** 尚未创建任何模板
- **WHEN** 打开记一笔
- **THEN** 不显示模板按钮，手工记账路径完全不受影响

### Requirement: 模板快捷区两行铺开且不横向滚动

常用模板 SHALL 收敛为「模板 ›」单枚按钮 + 全量选择面板，
MUST NOT 再以每行 3 个、两行等宽铺开。全量面板 MUST 提供搜索与全名显示，
长模板名在按钮上单行省略、MUST NOT 撑高行。面板 SHALL 复用既有全量弹层，
MUST NOT 新建数据路径。

#### Scenario: 六个模板不占首屏

- **GIVEN** 用户有 6 个常用模板
- **WHEN** 打开记一笔
- **THEN** 首屏只有「模板 ›」一枚按钮（或在「更多」内），点开可见 6 个全名

### Requirement: 记一笔各表单行高度量级一致

各表单行内容高度 SHALL 落在 **32–44dp** 区间。收款方与描述的
`OutlinedTextField` SHALL 把 `colors` 的 border 与 container 设为
`Color.Transparent`，MUST NOT 保留整圈描边；MUST NOT 改用 `BasicTextField`
或 filled 变体（官方 56dp 更高、且会与其余行混用变体）。
一键清空图标、placeholder、`heightIn(min = 40.dp)` 与 `bodyMedium` 字号
SHALL 保持不变。可点区 MUST NOT 低于 40dp。

#### Scenario: 两个内联行不再突兀

- **GIVEN** 打开记一笔首屏
- **WHEN** 查看收款方与描述两行
- **THEN** 两行只有细分割线、无整圈边框，placeholder 完整不裁，与其他行齐高

### Requirement: 记一笔低频项收起且输入框不撑高边框

模板、标签、优惠/手续费、备注 +「不计入收支」、收款方联想 chips
SHALL 统一收进「更多」面板，MUST NOT 各自在首屏占一行入口。
「更多」入口 SHALL 在编辑态有值时显示可读摘要。面板 SHALL 用
`Dialog` + 底部对齐 `Surface` 实现，MUST NOT 引入 `ExperimentalMaterial3Api`
的 `ModalBottomSheet`。文本输入 SHALL 用 placeholder 而非浮动 label。
日期与时间 MUST 仍为紧随金额后的无标签可点副行（不算低频项）。

#### Scenario: 一个入口管全部低频项

- **GIVEN** 新建一笔流水
- **WHEN** 查看首屏
- **THEN** 只有一枚「更多 ›」，点开后模板/标签/优惠/备注/不计入/联想全部可达
- **AND** 首屏不再出现「▸ 添加优惠」与「更多（备注 · 不计入收支）」两行

### Requirement: 记一笔提供模板快选且两步内完成

「记一笔」SHALL 通过「模板 ›」按钮提供模板预填，
从打开记一笔到**完成表单预填** SHALL 不超过 2 次点击，MUST NOT 弹二次编辑。
模板语义 SHALL 为**预填草稿、不直接生成流水**（与既有 `applyTemplate()` 一致；
主 spec 旧文「点一下直接生成流水并关闭」为历史腐烂，本批以代码为准）。
计算器键盘交互与两级分类快选 SHALL 保持不变。

#### Scenario: 两次点击预填午餐

- **GIVEN** 用户建了「午餐」模板
- **WHEN** 点「更多 › → 午餐」
- **THEN** 表单按模板预填，可继续改金额后保存，MUST NOT 自动落库

### Requirement: 记一笔提供收款方与常用收款方补全

「记一笔」SHALL 在首屏提供收款方**无边框内联**自由文本输入；
系统 SHALL 从既有流水 `DISTINCT` 出常用收款方，但联想 chips
SHALL 放在「更多」面板内，MUST NOT 在首屏常驻一行 40dp 区域。
点 chip SHALL 填入输入框。明细行 SHALL 展示收款方。
收款方 MUST NOT 建成账户、MUST NOT 引入复式分录。

#### Scenario: 联想不占首屏

- **GIVEN** 账本里已有「盒马」「地铁」
- **WHEN** 打开记一笔
- **THEN** 首屏收款方行下方无 chips；点「更多 ›」可见并点选填入

## ADDED Requirements

### Requirement: 记一笔低频项集中在更多面板

「记一笔」SHALL 提供唯一的「更多 ›」入口与唯一的 `MorePanel` 容器，
SHALL 按「模板 / 标签 / 优惠或手续费 / 备注与不计入收支 / 收款方联想」
顺序组织，MUST NOT 出现两个及以上并列的低频折叠入口。
面板 SHALL 点遮罩或返回键关闭，MUST NOT 阻断键盘与保存按钮。

#### Scenario: 面板不遮挡主流程

- **GIVEN** 用户展开「更多」面板
- **WHEN** 关闭面板
- **THEN** 金额、分类、账户、收款方、描述、保存按钮状态全部保留

#### Scenario: 转账也有更多面板

- **GIVEN** 类型切到转账（无分类、无收款方）
- **WHEN** 点「更多 ›」
- **THEN** 面板显示模板 / 标签 / 手续费 / 备注，MUST NOT 显示收款方联想

### Requirement: 全局主题支持动态色且可一行回退

`LedgerTheme` SHALL 在 API 31+ 使用 `dynamicLightColorScheme(LocalContext.current)`，
API ≤ 30 SHALL 回退到既有静态浅色板，MUST NOT 在低版本崩溃。
`LedgerTheme` SHALL 暴露 `dynamicColor: Boolean = true` 参数，
传 `false` SHALL 完整回到第 70 批静态板，MUST NOT 需要改第二处。
`LedgerColors.ExpenseRed / IncomeGreen` 与配色反转开关
MUST NOT 受动态色影响。

#### Scenario: 低版本回退

- **GIVEN** 设备为 Android 10（API 29）
- **WHEN** 冷启动 App
- **THEN** 使用静态板正常渲染，无 `dynamicLightColorScheme` 调用

#### Scenario: 一行回退

- **GIVEN** 动态色下观感不接受
- **WHEN** MainActivity 把 `LedgerTheme(dynamicColor = false)`
- **THEN** 全 App 回到浅灰底 / 纯白卡 / 清新蓝，红绿语义与反转开关不变

#### Scenario: 已知代价显式登记

- **GIVEN** 动态色启用且 `LedgerColors.Primary` 仍被统计蓝底等处直接引用
- **WHEN** 查看统计页与记一笔
- **THEN** 允许出现两套蓝；该差异 MUST 在交付说明与活文档中登记，
  MUST NOT 写成「已完全统一」

### Requirement: 全 App 采用 edge-to-edge 且可点区避开系统栏

`MainActivity` SHALL 调 `WindowCompat.setDecorFitsSystemWindows(window, false)`
并把系统栏设透明；背景 SHALL 铺满屏幕，根内容 SHALL 用
`Modifier.safeDrawingPadding()` 避开系统栏与手势区。
`LedgerApp` 的 `Scaffold` SHALL 设 `contentWindowInsets = WindowInsets(0,0,0,0)`，
MUST NOT 与外层 safeDrawing 叠加双重 padding。
记一笔的独立 `Dialog` Window SHALL 单独对根 Column 应用
`statusBarsPadding()` 与 `navigationBarsPadding()`，
MUST NOT 让「记一笔」标题或底部保存按钮被系统栏遮挡。

#### Scenario: 标题不被状态栏盖住

- **GIVEN** 全面屏手势导航设备
- **WHEN** 打开记一笔
- **THEN** 「记一笔」标题在状态栏下方完整可见，保存按钮在手势条上方完整可点

#### Scenario: 主页面不双重留白

- **GIVEN** 进入明细 / 统计 / 资产任一 Tab
- **WHEN** 查看顶部与底部留白
- **THEN** 内容只避让一次系统栏，MUST NOT 出现两倍 padding 的空白带
