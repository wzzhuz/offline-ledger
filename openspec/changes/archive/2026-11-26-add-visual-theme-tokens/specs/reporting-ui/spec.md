# Delta for 页面、性能与范围（reporting-ui）

## ADDED Requirements

### Requirement: 全局主题支持深浅模式且语义色全程可读

系统 SHALL 在 `LedgerTheme` 提供 `darkTheme` 参数，默认 SHALL 为 `isSystemInDarkTheme()`。
深色 SHALL 走本项目静态 `darkColorScheme`（背景 `0xFF121417`、卡片 `0xFF1E2025`），
MUST NOT 走系统动态深色、MUST NOT 使用纯黑或纯白。
浅色动态色 SHALL 由 `dynamicWithNeutralSurface()` 只借用 primary / secondary /
tertiary 及其容器色，中性槽位（background / surface / surfaceVariant /
surfaceContainer* / outlineVariant）MUST 套回第 70 批静态值，
MUST NOT 让页面底与卡片容器同色（这是「卡片边界糊」的根因）。
`LedgerColors` SHALL 以可观察状态持有 ExpenseRed / IncomeGreen / Neutral / Primary /
OnPrimary / Background / CardSurface / OnSurface / SurfaceVariant / OutlineVariant，
由 `LedgerTheme` 在一次 `SideEffect` 里同步；MUST NOT 用 CompositionLocal 重写
237 处引用（Canvas draw scope 与普通函数在非 Composable 上下文 SHALL 仍能读到）。
深色下 ExpenseRed / IncomeGreen SHALL 换成同语义的柔和珊瑚红 `0xFFF0A6A0` /
柔和绿 `0xFF9CCFA6`，`applyReverseColors` 反转开关在深浅切换后 MUST 仍生效。
MUST NOT 改动红绿语义、MUST NOT 引入新依赖或新权限。

#### Scenario: 系统切到深色

- **GIVEN** 使用者在系统设置切到深色
- **WHEN** 冷启动 App 并逐页查看明细 / 资产 / 统计 / 设置
- **THEN** 页面底为深灰蓝、卡片为略亮深灰蓝、文字为浅色，无残留纯白块
- **AND** 红绿金额仍可辨，配色反转开关状态不丢失

#### Scenario: 动态浅色下卡片边界

- **GIVEN** API 31+ 设备启用系统动态色且壁纸色调明显
- **WHEN** 打开统计页
- **THEN** 页面底仍是中性微灰、卡片仍是纯白，二者边界清晰可辨
- **AND** 强调色随壁纸变化，中性底不随壁纸漂

**理由**：第 78 批把卡片容器改成读 `scheme.surface` 后，与 `Scaffold` 的
`scheme.background` 在动态浅色下同色，真机反馈「分界不明显」。第 79 批拆开
「页面底 / 卡片面 / 次级底」三种语义后恢复，理由写入此处以免再次合并。

---

### Requirement: 金额文本统一等宽数字与四级排版

所有面向使用者的金额文本 SHALL 经 `LedgerType` 的金额样式渲染，
MUST 开启 `fontFeatureSettings = "tnum"` 等宽数字特性：
`AmountDisplay`（32sp Bold）SHALL 用于大额总览（统计页总支出、交易详情主金额），
`AmountRow`（16sp SemiBold）SHALL 用于单笔金额（明细行、收入 / 结余行），
`Title`（15sp Medium）与 `Caption`（12sp Normal）SHALL 分别为主标题与次要备注层级。
金额列表 SHALL 保持右对齐，MUST NOT 因 `+` / `-` / 小数点 / 千分位字形宽度跳动。
MUST NOT 逐个 `TextStyle` 散写字号，MUST NOT 改动既有固定列宽与网格结构。

#### Scenario: 明细列表金额对齐

- **GIVEN** 明细页连续多笔金额位数与正负号不同
- **WHEN** 上下滚动查看金额列
- **THEN** 各金额右对齐、小数点与末位对齐，不随字形宽度左右跳动

#### Scenario: 字体不支持 tnum 时

- **GIVEN** 设备字体未提供 OpenType `tnum` 特性
- **WHEN** 渲染金额文本
- **THEN** 页面不崩溃、金额完整可读，该项 MUST 列入真机待验而非写成「必然生效」

**理由**：金额对齐是记账 App 的高频可读性需求；`tnum` 依赖字体特性，
故只保证样式出口统一，不承诺所有 OEM 字体必然生效。

---

### Requirement: 视觉设计 token 与表面色语义分离

系统 SHALL 提供 `ui/theme/LedgerDesignTokens.kt` 作为视觉常量单一真相源：
`LedgerShapes.Card`（16.dp 圆角）、`LedgerShapes.Icon`（12.dp）、
`LedgerSpacing.CardGap`（16.dp）、`LedgerSpacing.CardPadding`（16.dp）、
`LedgerType`（金额四级排版）。
表面色 SHALL 拆成三个独立语义出口，MUST NOT 再用一个 `Surface` 包打天下：
`Background`（页面 / 全屏底）、`CardSurface`（卡片 / 顶栏 / 弹层，浅色纯白 /
深色 `0xFF1E2025`，刻意固定不随动态色漂）、`OnSurface`（卡片上的文字图标）。
顶栏与资产页身份区 SHALL 用中性卡片承载，MUST NOT 使用整块高饱和蓝底；
主色 SHALL 只作小面积点缀（分组小竖条、进度条填充、FAB、选中态 12% 透明底、
切换图标、选中文字），MUST NOT 因中性化把主色整体删除。
统计页卡片 SHALL 走统一 `StatsCard` 出口，`CardGap` SHALL 为 16.dp，
MUST NOT 为降噪把间距收到 12dp 以下导致边界糊。

#### Scenario: 顶栏风格一致

- **GIVEN** 依次打开资产页、新建账户页、全部账户排序页、分类管理 / 选择 / 排序页
- **WHEN** 查看各页顶部区域
- **THEN** 均为中性卡片 + `OnSurface` 文字，无突兀整块蓝底
- **AND** 主色仍出现在分组小竖条 / 进度条 / FAB 等小面积位置

#### Scenario: 卡片间距回归

- **GIVEN** 统计页有小结卡、趋势卡、分类占比卡等多张卡
- **WHEN** 滚动浏览
- **THEN** 卡片之间 16dp 间隙，边界清晰，MUST NOT 回到 12dp 的糊边界

**理由**：第 78 批把固定 `Color.White` 换成 `scheme.surface`、把间距收到 12dp，
两条真机反馈（分界糊、蓝底突兀）都由「语义合并 + 数值收紧」引入。
拆开语义与回到已验证数值，比继续调色更可靠。

---

## MODIFIED Requirements

### Requirement: 资产页蓝底身份区与分组头部强化

**第 79 批归档修订**：本条标题沿用历史名称以便 MODIFIED 匹配，实际形态已改为中性卡片。
资产页 SHALL 以中性卡片顶部身份区承载页面标题、账户数与「含已归档」/「+ 新增」入口，
并在身份区内**按 `currencyCode` 逐组**展示资产 / 负债 / 净资产。
MUST NOT 把 `state.groups` 汇总成一个跨币种的单一「净资产」总数，
MUST NOT 因借鉴一羽记账截图而违反基线「资产页按币种分组且移除单一净资产总数」。
账户分组标题 SHALL 用蓝色小竖条 + 分组名 + 账户数表达，
MUST NOT 显示按 `AccountType` 聚合的跨币种金额。
账户行 SHALL 保持纯行 + `ListDivider()`，MUST NOT 用 `Card` 底色 / 阴影 / 1dp 间距
表达行边界（基线「管理类页面入口固定且列表与编辑分离」）。

#### Scenario: 两币种账户不出现虚假总数

- **GIVEN** 一个 HKD 账户与一个 CNY 账户
- **WHEN** 打开资产页查看顶部身份区
- **THEN** 出现 HKD / CNY 两组各自的资产 / 负债 / 净资产
- **AND** 页面不出现把两币种加在一起的单一净资产数额

#### Scenario: 分组标题不混算

- **GIVEN** 「资产账户」分组下有 CNY 与 HKD 两个账户
- **WHEN** 查看分组标题
- **THEN** 显示蓝色小竖条、分组名与「2 个账户」，MUST NOT 显示跨币种分组金额

---

---

> **归档注记**：本条「小计」标签按第 60 批 Q1A 统一为「净资产」，见「资产页逐币种净资产标签与负债红字负数」。

---

### Requirement: 新建账户页采用近全屏紧凑表单

新增账户 SHALL 由 `AlertDialog` 小弹窗改为近全屏 `Dialog`
（`usePlatformDefaultWidth = false`）：中性卡片 TopBar「← 添加资产账户 / 保存」、
分组小竖条、「图标 + 账户名称」紧凑白底小卡、底部大保存按钮。
表单字段与顺序 SHALL 保持不变（账户名称 / 卡号后四位 / 账户类型 /
期初余额 / 信用额度），`onConfirm` 签名与 `AssetsViewModel.addAccount` SHALL 零改动；
输入框 SHALL 用 `placeholder` 与 `heightIn(min)`，MUST NOT 用浮动 `label` 或固定 `height`。
MUST NOT 新增备注、隐藏账户、默认使用或账户图标等业务字段，
MUST NOT 升 `AppDatabase` 版本；`EditAccountDialog` 本批 SHALL 零改动。
MUST NOT 新增 Navigation 路由或改变 4 Tab 结构。

#### Scenario: 新建页字段零回归

- **GIVEN** 打开「+ 新增」
- **WHEN** 点「招行储蓄卡」模板、输入卡号后四位 1234 与期初余额 1000 并保存
- **THEN** 创建非 `LIABILITY` 的资产账户，名称带 `(1234)`，期初余额重算生效
- **AND** 页面仍是 4 Tab、无新路由、数据库版本不变

#### Scenario: 键盘弹起仍可保存

- **GIVEN** 中小屏手机在新建页聚焦「期初余额」
- **WHEN** 软键盘弹起
- **THEN** 底部大保存按钮仍可见可点，表单区可滚动，MUST NOT 被键盘完全遮挡

---

---

### Requirement: 资产页逐币种净资产标签与负债红字负数

资产页中性卡片顶部身份区 SHALL 按 `currencyCode` **逐组**渲染，
每组 SHALL 把原「X 小计」标为「**净资产**」，
SHALL 以常规色显示「资产 +X」、以语义 `LedgerColors.ExpenseRed`（深色下自动为柔和珊瑚红 `0xFFF0A6A0`）显示「负债 -X」
（负号为纯展示文本，`CurrencyGroup.liabilitiesCents` 仍为绝对值、
MUST NOT 参与任何运算）。
账户行负余额 SHALL 继续以红字负数显示，MUST NOT 被本批改掉。
MUST NOT 把各组 `netCents` 汇总成一个跨币种的单一「净资产」总数，
MUST NOT 违反基线「资产页按币种分组且移除单一净资产总数」。

#### Scenario: 两币种各自净资产

- **GIVEN** 一个 HKD 账户与一个 CNY 账户，其中 CNY 有一笔信用负债欠款
- **WHEN** 打开资产页查看顶部身份区
- **THEN** 出现 HKD / CNY 两组各自的「净资产」，CNY 组负债为红字负号
- **AND** 页面不出现把两币种加在一起的单一净资产数额

#### Scenario: 账户行负数语义不变

- **GIVEN** 信用卡账户 `balanceCents = -50000`
- **WHEN** 查看账户行
- **THEN** 金额显示为负数且为红字，MUST NOT 变成绝对值或白字

---

---

---

### Requirement: 全部账户排序独立页

系统 SHALL 提供独立「全部账户排序」页（NavHost 路由 `account-quick-sort`），
入口 SHALL 在资产页中性卡片顶部身份区固定可见，MUST NOT 新增第 5 个底部 Tab。
该页 SHALL 以单一 `LazyColumn` 展示**全部未归档账户、不分类型**，
SHALL 复用 `sh.calvin.reorderable:reorderable:3.1.0` 与
`rememberKeyedReorder`，MUST NOT 自写 `Modifier.pointerInput`；
SHALL 提供「保存 / 放弃」与一行「这里排的是记一笔里常用账户的先后顺序」提示。
`draftOrder` SHALL 与已落库 `quickOrder` 完全分离：
放弃 MUST 零写库，保存 SHALL 一次性写入并在完成后刷新。
每行 SHALL 显示小标志、账户名与类型标签，
上移 / 下移 SHALL 以 `semantics.customActions` 保留作 TalkBack 兜底。

#### Scenario: 保存与放弃

- **GIVEN** 在独立页拖动若干账户后
- **WHEN** 点「放弃」
- **THEN** 页面关闭、`quickOrder` 零变化、记一笔候选不变
- **WHEN** 重新进入拖动后点「保存」
- **THEN** `quickOrder` 一次性写回、记一笔候选按新顺序、`sortOrder` 零变化

#### Scenario: 导航格数不变

- **GIVEN** 从资产页进入全部账户排序页
- **WHEN** 查看底部导航
- **THEN** 仍是明细 / 资产 / 统计 / 设置 4 个 Tab


---

---

---

### Requirement: 统计页采用一羽式中性灰底与纯白卡片

系统 SHALL 在 `LedgerTheme` 的 `lightColorScheme` 中提供一羽式浅色体系：
页面背景 SHALL 为**中性浅灰** `0xFFF2F3F7`（第 69 批的 `0xFFF4F6FB`
太浅且偏蓝，与白卡对比不足，第 70 批按真机图三改中性灰），
`surface` 与全部 `surfaceContainer*` SHALL 为纯白 `0xFFFFFFFF`
或近白 `0xFFFAFAFC`，`surfaceVariant` SHALL 为 `0xFFE9EBF1`，
`onSurface` SHALL 为柔和深灰蓝 `0xFF1F2430`，
`outlineVariant` SHALL 为 `0xFFE2E4EB`，
`primary` SHALL 为清新蓝 `0xFF5B7CFA`。
**第 79 批归档修订**：API 31+ 动态浅色 SHALL 由 `dynamicWithNeutralSurface()`
只借用动态强调色，上述中性槽位 MUST 套回本静态板的值，
MUST NOT 让 `scheme.background` 与卡片容器同色。
`CategoryHeaderBlue` SHALL 读 `LedgerColors.Primary` 以保持单一真相源。
统计页每张卡片 SHALL 走统一 `StatsCard` 出口：
显式 `CardDefaults.cardColors(containerColor = LedgerColors.CardSurface)` +
`CardDefaults.cardElevation(defaultElevation = 0.dp)` + `LedgerShapes.Card`（16.dp）。
卡片间距 SHALL 为 `LedgerSpacing.CardGap = 16.dp`，MUST NOT 收到 12dp。
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

#### Scenario: 动态浅色下卡片边界不糊

- **GIVEN** API 31+ 设备启用系统动态色
- **WHEN** 打开统计页
- **THEN** 卡片容器读固定的 `LedgerColors.CardSurface`，与页面中性灰底边界清晰
- **AND** MUST NOT 出现第 78 批「卡片与底同色」的回归

#### Scenario: 红绿语义不变

- **GIVEN** 使用者开启「配色反转」
- **WHEN** 查看任意金额
- **THEN** 红绿语义按既有开关互换，浅灰 / 纯白体系本身不变

#### Scenario: 全 App 观感待验

- **WHEN** 第 70 批出包后装机
- **THEN** 记一笔 / 明细 / 资产 / 分类管理 / 设置各页观感 MUST 由真机逐页确认
- **AND** CI 全绿不替代观感验收
