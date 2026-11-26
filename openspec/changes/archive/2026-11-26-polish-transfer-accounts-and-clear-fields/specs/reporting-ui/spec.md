# reporting-ui · delta（第 81 批）

## MODIFIED Requirements

### Requirement: 记一笔布局紧凑且首屏可见备注与收款方

「记一笔」支出 / 收入态首屏 SHALL 只呈现：类型 Tab、金额、日期时间无标签副行、
分类快捷行（带 `›` 整行可点全量选择器）、收款方与描述的**无边框内联行**，
以及固定底部区的键盘上方**单行 5 枚 chips**、键盘、保存按钮。
**转账态 SHALL 例外**：分类行、收款方行与联想条隐藏，
SHALL 在日期副行之后常驻**转出 / 转入两张账户卡**，其余维持不变。
账户在支出 / 收入态 SHALL 从首屏表单行移到 chips（默认账户预填、低频）；
备注与「不计入收支」、模板、标签、优惠/手续费 MUST NOT 占首屏表单区。
收款方与描述 MUST 仍在首屏可见（描述是明细主标题来源，MUST NOT 藏进面板）。
收款方联想条 SHALL 只在输入框聚焦或有内容时出现，MUST NOT 常驻。
活跃 change `record-category-picker-and-root-select` 的「点一级即按一级记、
再点收起二级、分类行带 ›」补述继续有效。

#### Scenario: 支出态首屏五块且不拥挤

- **GIVEN** 用户有 6 个模板、10 个账户、20 条历史收款方
- **WHEN** 打开记一笔（支出）且不做任何滚动
- **THEN** 首屏看到金额、分类快捷行、收款方/描述内联行、键盘上方单行 chips、保存
- **AND** 看不到账户表单行、模板表单行、标签行、备注输入框、常驻联想行

#### Scenario: 转账态两张账户主字段常驻

- **GIVEN** 用户切到「转账」Tab
- **WHEN** 不做任何滚动、不点任何 chip
- **THEN** 首屏直接看到「转出账户」与「转入账户」两张卡及其当前账户名
- **AND** MUST NOT 需要先打开底部 sheet 或切换分段才能看到它们

#### Scenario: 旧内容不因收起丢失

- **GIVEN** 编辑一条带备注、优惠、两个标签、且不计入收支的旧流水
- **WHEN** 打开编辑态
- **THEN** 对应 chips 在 label 上显示可读摘要，点开 sheet 可修改全部字段
- **AND** MUST NOT 让任何旧值不可见或不可修改

### Requirement: 记一笔采用统一表单网格与有上限的快捷区

「记一笔」SHALL 保留固定 52dp 标签列与细分割线，同区域 MUST NOT 混用
带框输入框、无边界文本按钮与芯片作为顶层块。**支出 / 收入态**账户与模板
SHALL 不再作为首屏表单按钮或平铺快捷 chips 存在，统一由键盘上方 chip
直达底部 sheet；**转账态**账户 SHALL 以两张首屏卡片常驻，MUST NOT 再进 chips。
分类快捷栏 SHALL 保留（低摩擦优点）。金额 SHALL 单独大字号展示。
「最近收款方 / 常用账户各最多 4 个」「模板每行 3 个两行铺开」的
快捷区上限条款随第 80 批作废。

#### Scenario: 账户 chip 显示默认账户（收支态）

- **GIVEN** 默认账户为「工商银行 (1122)」且为信用卡
- **WHEN** 打开记一笔（支出）
- **THEN** 账户 chip 显示该账户名，点开两列 sheet 可按「1122」搜索、可见余额
- **AND** 首屏表单区 MUST NOT 再出现「账户」行

#### Scenario: 转账态账户不在 chips

- **GIVEN** 用户切到转账
- **WHEN** 查看键盘上方 chips 与首屏表单区
- **THEN** chips 中没有账户入口，两张账户卡在表单区常驻
- **AND** 两处 MUST NOT 同时出现账户选择入口（双入口）

### Requirement: 记一笔各表单行高度量级一致

各表单行内容高度 SHALL 落在 **32–44dp** 区间。收款方与描述的
`OutlinedTextField` SHALL 把 `colors` 的 border 与 container 设为
`Color.Transparent`，MUST NOT 保留整圈描边；MUST NOT 改用 `BasicTextField`
或 filled 变体。一键清空图标、placeholder、`heightIn(min = 40.dp)` 与
`bodyMedium` 字号 SHALL 保持不变。**一键清空图标 SHALL 用 36dp 容器 +
18dp Icon，MUST NOT 用默认 48dp 的 `IconButton` 把输入行撑高。**
键盘上方 chips 与收款方联想条的每一个可点项 SHALL 用
`heightIn(min = 40.dp)` 容器承载，字号 SHALL NOT 低于 `labelMedium`。

#### Scenario: 清空图标不撑高

- **GIVEN** 描述输入框有内容、尾部出现清空图标
- **WHEN** 与相邻行对比高度
- **THEN** 该行仍在 32–44dp 区间，MUST NOT 凸出 12dp 以上
- **AND** 点图标后内容立即为空且图标消失

#### Scenario: chips 与联想条一次点中

- **GIVEN** 打开记一笔且收款方输入框聚焦
- **WHEN** 点键盘上方任一 chip 或联想条里的任一收款方
- **THEN** 一次点击即打开对应 sheet 或填入输入框，SHALL NOT 误触相邻项
- **AND** 可点区不低于 40dp、字号为 `labelMedium`

### Requirement: 记一笔低频项由键盘上方芯片直达

「记一笔」**支出 / 收入态** SHALL 在数字键盘上方提供**固定单行 5 枚等分 chips**：
账户 / 模板 / 备注 / 标签 / 优惠；**转账态 SHALL 为 4 枚**：
模板 / 备注 / 标签 / 手续费，MUST NOT 出现「转出/转入」账户 chip
（账户已常驻首屏两张卡）。「不计入收支」开关 SHALL 并入备注 sheet，
MUST NOT 为它新增第 6 枚 chip。每枚 SHALL 用 `heightIn(min = 40.dp)` 容器、
`RoundedCornerShape(12.dp)` 与 `labelMedium` 字号，点一下 SHALL 直接打开
对应底部 sheet，MUST NOT 经过「更多」这一层。chips 条 SHALL 位于滚动表单区
之外、与键盘同属固定底部区，MUST NOT 挤压首屏。
各 sheet 关闭后 SHALL 保留金额、分类、账户、收款方、描述与保存按钮的全部状态。

#### Scenario: 单行五枚且账户低频（收支态）

- **GIVEN** 用户设置了默认账户
- **WHEN** 打开记一笔（支出）
- **THEN** 键盘上方恰好 5 枚 chips，账户 chip 显示默认账户名但不要求选择
- **AND** 点任一枚直接进入对应 sheet，MUST NOT 出现「更多 ›」

#### Scenario: 转账态四枚

- **GIVEN** 类型切到转账
- **WHEN** 查看键盘上方
- **THEN** 恰好 4 枚 chips（模板 / 备注 / 标签 / 手续费）
- **AND** 首屏两张账户卡可见，底部仍是明细 / 资产 / 统计 / 设置 4 个 Tab

#### Scenario: sheet 不遮挡主流程

- **GIVEN** 已输入金额、选好分类、填了收款方
- **WHEN** 打开并关闭备注 sheet
- **THEN** 金额、分类、收款方、描述、保存按钮状态全部保留

**理由**：第 80 批把「账户低频」当成全类型结论，转账态没有默认账户兜底，
属于外推错误。第 81 批按真机反馈把转账账户还原为主字段并移除重复入口。

### Requirement: 记一笔输入框紧凑且支持一键清空

交易对方、描述、备注与账户搜索**四个**输入框的 placeholder SHALL 与输入文字、
表单标签统一为 `bodyMedium`，SHALL NOT 使用 placeholder 默认的 `bodyLarge`。
输入框 SHALL NOT 设置固定 `height` 或 `contentPadding`：
SHALL 用 `Modifier.heightIn(min = 40.dp)` 覆盖官方
`OutlinedTextFieldDefaults.MinHeight = 56.dp` 这个无条件下限。
四个输入框 SHALL 共用同一个 `ClearFieldIcon` 出口（36dp 容器 + 18dp Icon），
在**有内容时**显示尾部清空图标，点击 SHALL 立即清空内容；
空内容时 SHALL NOT 显示清空图标；图标 SHALL NOT 把输入框撑到 48dp。
账户搜索 SHALL 支持按账户名（含卡号后缀）筛选。
选中态 SHALL 用 chip 的填充底色表达，SHALL NOT 用 `● ` 等文字前缀。

> **第 81 批归档修订**：本条原文后半段描述「『更多』展开区把备注与不计入
> 开关放在同一 `FormRow`」「『更多』入口带 ⌄/⌃ 展开指示器」「账户 › 整行点击」，
> 均为第 77 批之前形态。第 80 批撤掉「更多」层时漏改本条，造成主 spec
> 同时存在两条相反要求；本批整块删除过期段落，并把描述纳入覆盖范围。

#### Scenario: 四处处处可一键清空

- **GIVEN** 交易对方、描述、备注、账户搜索四处均已输入内容
- **WHEN** 分别点各自尾部清空图标
- **THEN** 对应内容立即为空且图标消失，无需用输入法逐字删除
- **AND** 四处 placeholder 完整显示、同为 `bodyMedium`、实际高度约 44dp

#### Scenario: 空内容不留白

- **GIVEN** 描述输入框为空
- **WHEN** 查看该行
- **THEN** 不显示清空图标，占位文字完整可见、下半部分不被裁切

## ADDED Requirements

### Requirement: 转账态转出与转入账户常驻首屏

「记一笔」转账态 SHALL 在日期副行之后、描述行之前提供**两张中性卡片**，
分别承载「转出账户」与「转入账户」，MUST NOT 折叠进 chips 或底部 sheet。
每张卡片 SHALL 显示目标标签、当前账户名（含卡号后四位）、余额与币种，
以及 `›` 指示器；未选择时 SHALL 显示「选择账户」，
原账户已删除时 SHALL 显示「原账户已删除，请重新指定」，MUST NOT 静默留空。
卡片 SHALL 用 `RoundedCornerShape(16.dp)` 与 `LedgerColors.CardSurface`，
MUST NOT 使用阴影、描边或卡片堆叠表达边界（§7.41 借观感不借堆叠）。
左图标容器 SHALL 为 40dp 圆角矩形 + 主色 12% 透明底，
MUST NOT 为此新增 `material-icons-extended` 等 Gradle 依赖。
点某张卡 SHALL 打开 `AccountPickerSheet` 并把 target 锁定为该卡，
sheet 标题 SHALL 相应为「选择转出账户」/「选择转入账户」，
**MUST NOT 在 sheet 内再显示转出 / 转入分段**（避免双入口）。
系统 MUST NOT 提供转出 / 转入 ⇅ 互换手势（使用者明确否决）。
选择后 SHALL 只回填对应一侧账户，另一侧 MUST NOT 被改动；
沿用既有 `setFromAccount` / `setToAccount`，MUST NOT 新建数据路径。

#### Scenario: 一眼看到两张卡

- **GIVEN** 用户切到转账、尚未选择任何账户
- **WHEN** 打开记一笔且不滚动、不点任何 chip
- **THEN** 首屏出现「转出账户」「转入账户」两张卡，均显示「选择账户」
- **AND** 卡片与微灰底边界清晰，MUST NOT 有阴影或描边

#### Scenario: 点卡锁定目标且只改一侧

- **GIVEN** 已选转出为「中国银行」、转入为「支付宝」
- **WHEN** 点「转入账户」卡并在 sheet 里选「花呗」
- **THEN** sheet 标题为「选择转入账户」、无分段，转入回填为「花呗」
- **AND** 转出仍为「中国银行」，金额、日期、描述、保存按钮状态不变

#### Scenario: 不引入互换手势

- **GIVEN** 两卡都已选好账户
- **WHEN** 查看两卡之间区域
- **THEN** 不存在 ⇅ 互换按钮或长按互换手势
- **AND** 改任一侧只能点对应卡片

#### Scenario: 零新依赖与零改表

- **GIVEN** 本批落地后
- **WHEN** 核对 Gradle 依赖与 `AppDatabase.version`
- **THEN** 依赖清单与 v0.2.59 逐项一致、数据库版本仍为 8

**理由**：支出/收入态靠默认账户兜底，账户是低频；转账态转出与转入都是
必填主字段，没有默认值可猜。把主字段折进 chip 是第 80 批的外推错误，
真机反馈后按一羽「两张大白卡」的观感还原，但不借卡片堆叠、不新增互换手势。
