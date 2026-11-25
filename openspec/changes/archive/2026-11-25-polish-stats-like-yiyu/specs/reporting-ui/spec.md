## MODIFIED Requirements

### Requirement: 统计页落地月年切换与三种图表

统计页 SHALL 提供月度 / 年度 / 多年切换与周期左右切换，SHALL 展示所选周期的
总收入 / 总支出 / 结余（结余为负用红色）。月度视图 SHALL 展示当月每日支出折线，
年度视图 SHALL 展示 1–12 月收入 vs 支出双折线，并 SHALL 展示按当前币种累计推算的
资产余额趋势（MUST NOT 跨币种混算）。
**第 69 批撤回第 68 批的蓝底身份区与「分析 / 概览」双 Tab**：统计页 SHALL 以
原有白底「统计」标题 + 既有月度 / 年度 / 多年周期切换 + 币种切换为顶部，
MUST NOT 新增蓝底块、MUST NOT 新增第二组 Tab、MUST NOT 改变既有周期切换能力。
统计页 SHALL 同时提供**分类占比卡**（环形图 + 一羽式图例列表，只承载分类维度）
与**收款方 / 账户默认折叠只读卡**（两者各自独立），
分类占比卡内 SHALL 同时可见环形图与其下方图例列表，两者 SHALL 共享同一份
净支出切片数据、MUST NOT 出现两个不一致的合计。
统计页 SHALL 以「白底标题 + 周期 / 币种切换 →（警告置顶）小结卡 → 趋势卡 →
分类占比卡 → 收款方 / 账户默认折叠卡 →（年度）资产卡 → 口径说明入口」的层级组织；
每张卡 SHALL 只承载一个结论、SHALL 有清晰边界，MUST NOT 让所有区块同权重平铺、
MUST NOT 让灰色口径说明常驻两行。统计页是浏览型长报表，SHALL 允许纵向滚动。
「一级合计 = 二级合计 = 总净支出」不一致时的警告 MUST 置顶显眼，
MUST NOT 为美观收掉。既有常驻点数值、竖向引导线与横屏放大页 MUST NOT 因本批回退。

#### Scenario: 月度视图

- **GIVEN** 用户选择 2026 年 11 月
- **WHEN** 查看统计页
- **THEN** 显示该月总收入 / 总支出 / 结余、每日支出折线、分类占比卡与默认折叠的收款方 / 账户卡
- **AND** 当日无支出的日子折线值为 0 而非断线
- **AND** 环形图与下方图例列表同屏可见，二者合计一致

#### Scenario: 周期切换不被改动

- **WHEN** 操作月度 / 年度 / 多年与币种切换
- **THEN** 两行控件完整可见可点，统计数字按新周期 / 币种刷新
- **AND** 顶部没有蓝底块、没有「分析 / 概览」Tab

#### Scenario: 年度视图

- **GIVEN** 用户切到 2026 年度
- **WHEN** 查看统计页
- **THEN** 显示 1–12 月收入与支出两条折线、分类占比卡与按当前币种的资产余额趋势
- **AND** 无流水的月份两条线均为 0 而非缺月

#### Scenario: 合计不一致警告

- **GIVEN** 一级 / 二级 / 总净支出三者不一致
- **WHEN** 打开统计页
- **THEN** 置顶显示警告卡与差额

#### Scenario: 不回到互斥切换

- **WHEN** 打开统计页分类占比卡
- **THEN** 环形图与图例列表同时可见
- **AND** 不存在「看环 / 看排行」二选一的切换 chip

### Requirement: 分类占比卡提供分类管理短跳转

分类占比卡 SHALL 在标题右侧提供「分类管理 ›」入口，点击 SHALL 进入已有的
分类管理二级页（Navigation 路由 `categories`），MUST NOT 新增导航格数或第 5 个 Tab。
第 69 批把收款方 / 账户拆回独立卡后，该入口 SHALL 保留在分类占比卡上，
MUST NOT 因拆分而丢失；MUST NOT 新增第二个「分类管理」入口。

#### Scenario: 从占比卡进分类管理

- **GIVEN** 统计页分类占比卡
- **WHEN** 点「分类管理 ›」
- **THEN** 进入分类管理页，返回后底部仍是 4 个 Tab

#### Scenario: 拆分不丢入口

- **WHEN** 收款方 / 账户拆成独立折叠卡后
- **THEN** 分类占比卡标题右侧仍只有一个「分类管理 ›」入口且可点

### Requirement: 分类全量选择器采用分组 chips 且不用下拉框

记一笔「分类 ›」与明细筛选「分类」SHALL 复用同一份只读
`CategorySelectDialog`，采用**顶部搜索 + 左侧一级分组导航 + 右侧 4 列
等宽彩色图标网格**版式，4 字分类名 SHALL 完整可见。
选择器 SHALL 显示彩色 `CategoryIconCatalog` 图标，
统计页分类占比卡的图例列表 SHALL 同样使用彩色 `CategoryIconCatalog` 图标
（`CategoryGlyph`，缺项回退中性圆 + 首字）；
记一笔快捷栏、明细列表行、账户详情 SHALL 仍是中性圆 + 中文首字。
两处选择器 SHALL 保持「整行 › 入口 + 立即选中 + 无输入框 / 无保存按钮 / 无写库路径」，
MUST NOT 因统计页放行彩色图标而改变。
彩色图标配色 MUST 走 `CategoryIconTints`，`colorHex` MUST NOT 被改写，
统计扇区色板 MUST NOT 改为图标配色。

#### Scenario: 统计占比图例彩色

- **GIVEN** 统计页分类占比卡处于「按小类」口径
- **WHEN** 查看图例列表
- **THEN** 预置分类显示彩色 Tabler 图标，自定义 / 缺项分类回退中性圆 + 中文首字
- **AND** 环形扇区颜色仍由 `ChartData.chartArgb` 按 id 稳定分配，与图标 tint 无关

#### Scenario: 其余页面零回归

- **WHEN** 打开记一笔快捷栏、明细列表行或账户详情
- **THEN** 分类标识仍是中性圆 + 中文首字，MUST NOT 出现彩色图标

## ADDED Requirements

### Requirement: 统计页采用一羽式中性灰底与纯白卡片

系统 SHALL 在 `LedgerTheme` 的 `lightColorScheme` 中提供一羽式浅色体系：
页面背景 SHALL 为**中性浅灰** `0xFFF2F3F7`（第 69 批的 `0xFFF4F6FB`
太浅且偏蓝，与白卡对比不足，第 70 批按真机图三改中性灰），
`surface` 与全部 `surfaceContainer*` SHALL 为纯白 `0xFFFFFFFF`
或近白 `0xFFFAFAFC`，`surfaceVariant` SHALL 为 `0xFFE9EBF1`，
`onSurface` SHALL 为柔和深灰蓝 `0xFF1F2430`，
`outlineVariant` SHALL 为 `0xFFE2E4EB`，
`primary` SHALL 为清新蓝 `0xFF5B7CFA` 且与 `CategoryHeaderBlue` 同系。
`LedgerColors.Primary` SHALL 同步为该值以保持单一真相源。
统计页每张卡片 SHALL 走统一 `StatsCard` 出口：
显式 `CardDefaults.cardColors(containerColor = Color.White)` +
`CardDefaults.cardElevation(defaultElevation = 0.dp)` +
`RoundedCornerShape(16.dp)`。
**M3 `Card` 默认 1.dp tonalElevation 会把纯白染成淡蓝灰，
这是「主体不突出、卡片边界糊」的根因，MUST 显式归零。**
SHALL 靠「中性灰底 + 纯白卡 + 卡片间距 + 圆角」形成清晰边界，
MUST NOT 新增阴影、MUST NOT 新增描边、MUST NOT 用卡片堆叠
（基线「明细页极简网格」要求）。
`ExpenseRed`（红 = 支出）/ `IncomeGreen`（绿 = 收入）语义色与
`applyReverseColors` 反转开关 MUST NOT 改动；`colorHex` MUST NOT 被改写。
配色改动 SHALL 只改色值，MUST NOT 改任何布局 / 手势 / 数据 / 聚合 SQL。
第 69 批使用者选 Q5B（全 App 一起变），全 23 个含 `Card` / `.background`
的文件观感都会变化，MUST 如实登记并列入真机待验。

#### Scenario: 灰底白卡边界清晰

- **WHEN** 打开统计页
- **THEN** 页面底为中性浅灰、每张卡片为纯白圆角块，卡片之间间隙与边界清晰可辨
- **AND** 不再出现 M3 默认 tonalElevation 染出的淡蓝灰卡面

#### Scenario: 红绿语义不变

- **GIVEN** 使用者开启「配色反转」
- **WHEN** 查看任意金额
- **THEN** 红绿语义按既有开关互换，浅灰 / 纯白体系本身不变

#### Scenario: 全 App 观感待验

- **WHEN** 第 70 批出包后装机
- **THEN** 记一笔 / 明细 / 资产 / 分类管理 / 设置各页观感 MUST 由真机逐页确认
- **AND** CI 全绿不替代观感验收

### Requirement: 收款方与账户默认折叠只读卡

统计页 SHALL 提供一张与分类占比卡**独立**的「收款方 / 账户」卡片，
默认 SHALL 为折叠态，MUST NOT 常驻展开；点击标题行 SHALL 展开或收起。
卡片左上角 SHALL 只有一个「收款方 ⇄ 账户」切换标签，点一下在两个维度间来回切，
MUST NOT 摆一排维度 chip、MUST NOT 新增第二个切换控件。
展开后 SHALL 按金额降序列出当前维度 Top5：中性圆 + 中文首字、
名称（账户维度 SHALL 沿用 `accountBaseLabel` 带卡号）、
¥金额与按占比填充的灰色长条。
两个维度 SHALL 复用既有 `state.topCounterparties` / `state.topAccounts`，
MUST NOT 新增 DAO 聚合查询、MUST NOT 跨币种混算。
该卡 SHALL 为**只读展示**，MUST NOT 跳明细 Tab、MUST NOT 向
`DetailViewModel` 注入筛选状态；收款方 / 账户不是分类，
MUST NOT 混进分类占比卡。

#### Scenario: 默认折叠

- **WHEN** 首次打开统计页
- **THEN** 收款方 / 账户卡只显示标题行与「展开」，不显示 Top5 列表

#### Scenario: 切换维度并展开

- **WHEN** 点标题展开，再点左上角「收款方 ⇄ 账户」
- **THEN** 列表切换到另一维度 Top5，仍只有这一个切换控件
- **AND** 账户行显示带卡号的显示名，灰条长度与占比一致

#### Scenario: 只读不跳转

- **WHEN** 点展开列表里的任意一行
- **THEN** 不切换 Tab、不写入明细筛选，页面保持统计页
