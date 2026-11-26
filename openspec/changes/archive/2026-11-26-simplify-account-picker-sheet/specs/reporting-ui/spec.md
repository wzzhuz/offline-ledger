# reporting-ui · delta（第 84 批）

> 使用者真机反馈（截图 6 为一羽账户弹层，非本 App）：
> 「账户切换参考一羽用一个小图标呢，没必要大量按钮占空间」
> 「叉号按钮是否也不需要，系统的返回操作手势就可以了，账户搜索应该也不需要」
> 四项拍板：① 1B 用 `ModalBottomSheet`（为下滑手势破例）；
> ② 保留「当前：xxx」；③ 「不指定」收进网格第一格；④ 图标随模式变。

## MODIFIED Requirements

### Requirement: 记一笔低频项收起且输入框不撑高边框

**第 80 批归档修订**：低频项不再统一收进「更多」面板，改为各自 chip 直达。
模板、账户、标签、优惠/手续费、备注 +「不计入收支」SHALL 由键盘上方 chips
各自直接打开底部 sheet，MUST NOT 出现「更多 ›」入口或 `MorePanel` 容器，
MUST NOT 出现两个及以上并列折叠入口。每个 chip SHALL 在编辑态有值时显示
可读摘要。**第 84 批口径精确化**：本条约束的「sheet」特指
`BottomPickerSheet`（备注 / 优惠 / 标签 / 模板快选），它们 SHALL 继续用
`Dialog` + 底部对齐 `Surface` 实现，MUST NOT 引入
`ExperimentalMaterial3Api` 的 `ModalBottomSheet`；
**账户选择器不在此禁令范围内**，按第 84 批 ADDED「账户选择器采用原生底部弹层…」
走 `ModalBottomSheet`。T12 是记一笔低频项 sheet 的**局部惯例**，
MUST NOT 被误读成全仓禁令（统计页 `StatsDrillSheet` 自第 69 批起已用
`ModalBottomSheet`，且已写进 `category-stats` 主 spec）。
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

### Requirement: 记一笔输入框紧凑且支持一键清空

交易对方、描述、备注**三个**输入框的 placeholder SHALL 与输入文字、
表单标签统一为 `bodyMedium`，SHALL NOT 使用 placeholder 默认的 `bodyLarge`。
输入框 SHALL NOT 设置固定 `height` 或 `contentPadding`：
SHALL 用 `Modifier.heightIn(min = 40.dp)` 覆盖官方
`OutlinedTextFieldDefaults.MinHeight = 56.dp` 这个无条件下限。
三个输入框 SHALL 共用同一个 `ClearFieldIcon` 出口（36dp 容器 + 18dp Icon），
在**有内容时**显示尾部清空图标，点击 SHALL 立即清空内容；
空内容时 SHALL NOT 显示清空图标；图标 SHALL NOT 把输入框撑到 48dp。
选中态 SHALL 用 chip 的填充底色表达，SHALL NOT 用 `● ` 等文字前缀。

> **第 81 批归档修订**：本条原文后半段描述「『更多』展开区把备注与不计入
> 开关放在同一 `FormRow`」「『更多』入口带 ⌄/⌃ 展开指示器」「账户 › 整行点击」，
> 均为第 77 批之前形态。第 80 批撤掉「更多」层时漏改本条，造成主 spec
> 同时存在两条相反要求；本批整块删除过期段落，并把描述纳入覆盖范围。
>
> **第 84 批归档修订**：账户选择器的搜索框按使用者「账户没那么多」反馈整体下线，
> 故覆盖范围由四个输入框收为三个，删除「账户搜索 SHALL 支持按账户名筛选」
> 与四处 Scenario。同名卡改由 `accountSecondaryLabel` 的「尾号 xxxx」显示区分
> （见「筛选与统计的账户显示名与全站一致」），MUST NOT 因删搜索连带删掉 `cardLast4`。

#### Scenario: 三处处处可一键清空

- **GIVEN** 交易对方、描述、备注三处均已输入内容
- **WHEN** 分别点各自尾部清空图标
- **THEN** 对应内容立即为空且图标消失，无需用输入法逐字删除
- **AND** 三处 placeholder 完整显示、同为 `bodyMedium`、实际高度约 44dp

#### Scenario: 空内容不留白

- **GIVEN** 描述输入框为空
- **WHEN** 查看该行
- **THEN** 不显示清空图标，占位文字完整可见、下半部分不被裁切

### Requirement: 记一笔常用候选按独立优先级排序且带卡种标志

记一笔常用账户候选 SHALL 按 `quickOrder` 排序取前 4，
SHALL 允许**不同 `AccountType` 的账户一起参与排序**（常用置顶），
MUST NOT 再取 `sortOrder` 的全局前 4。
`AccountQuickCandidates.select` 的「常用前 N + 当前选中项必可见」
语义 SHALL 保持不变，只更换传入列表的数据源顺序。
每个候选 chip SHALL 以**10dp** 小角标单字（「储」/「信」）
显示 `cardKind`，MUST NOT 用文字后缀挤占账户名宽度。
**第 61 批**：候选与全账户选择弹层的 label SHALL 经 `accountBaseLabel()`
带上 `cardLast4`（如「工商银行 (1122)」），MUST NOT 只显示纯名；
「当前：」文案 SHALL 同样基于该组合名。
**第 84 批修订**：账户搜索框已下线，原文「弹层搜索键」「输入后四位 MUST 能搜到」
作废；多张同名银行卡 SHALL 改由每格次要行「尾号 1122 / 3344」直接区分，
SHALL NOT 要求使用者记住或输入后四位。
`AccountQuickCandidates.select` / `labelOf` / `promoteById`
的纯 JVM 契约 SHALL 零改动，只更换映射进 `AccountQuickItem.name` 的字符串。
记一笔分类快捷栏图标 SHALL 由 40/34dp 收紧为 **28/24dp**，
分类管理页 5 列网格仍 SHALL 用 40dp（两处密度各按各的场景）。
资产页分组内排序 MUST NOT 改变候选顺序。

#### Scenario: 跨类型常用置顶

- **GIVEN** 使用者在「全部账户排序」页把一张信用卡与一张储蓄卡排到前两位
- **WHEN** 打开记一笔
- **THEN** 常用候选前两项就是这两张卡，且带各自的小标志
- **AND** 资产页分组内顺序未因此改变

#### Scenario: 多张同名银行卡可区分（改为显示区分）

- **GIVEN** 两张账户名同为「工商银行」、卡号后四位分别为 1122 与 3344
- **WHEN** 打开记一笔查看快捷候选 /「当前：」/ 全账户选择弹层
- **THEN** 三处都分别显示「工商银行 (1122)」与「工商银行 (3344)」
- **AND** 弹层每格次要行显示「尾号 1122 · 储蓄卡 · CNY」/「尾号 3344 · 信用卡 · CNY」
- **AND** MUST NOT 依赖搜索框才能分辨，两张卡各带不占名称宽度的 10dp 卡种小标志

#### Scenario: 记一笔图标密度收紧

- **GIVEN** 打开记一笔
- **WHEN** 查看分类快捷栏与账户候选 chip
- **THEN** 一级分类圆图标为 28dp（选中）/ 24dp（未选中），账户小标志为 10dp
- **AND** 分类管理页网格仍为 40dp，MUST NOT 被连带改小

### Requirement: 筛选与统计的账户显示名与全站一致

**第 62 批**：筛选面板的账户选项 SHALL 经 `accountBaseLabel()` 带上
`cardLast4`，MUST NOT 只显示纯名。
**第 84 批修订**：筛选面板的账户条件自第 83 批起已走通用
`AccountSelectDialog`，本批该选择器的搜索框下线，故原文「picker 的搜索按
label 匹配，输入后四位 SHALL 能定位」作废；同名卡 SHALL 由选项 tile 的
`accountSecondaryLabel` 次要行「尾号 xxxx」直接区分。
`FilterSheet.accountOptions` / `displayLabel` 仍 SHALL 保留作
`PickRow` 的**只读展示名**（第 62 批带卡号口径），MUST NOT 因删搜索框连带删除。
明细页的筛选 chip（含 `accountDisplayName` 为空时的 fallback）SHALL
同样带卡号。
统计页「账户」维度排行 SHALL 经 `accountBaseLabel()` 带卡号；
`InsightData.RankItem.name` 同时是下钻 `filterByAccount()` 的只读展示名，
故从统计跳明细后的筛选 chip SHALL 自动带上卡号，
MUST NOT 在 UI 侧再拼一次。
明细列表副行与流水详情页 SHALL 经 `accountLabel()` 组合 DAO 投影的
`fromAccountName` / `fromAccountLast4`（转入同理），
MUST NOT 直接显示 `row.fromAccountName` 纯名。
资产页删除确认弹窗、账户详情标题、默认账户设置、对账 / 核对标题、
回收站流水行 SHALL 全部带卡号。
**第 63 批修订 CSV 往返语义**：CSV 导出的 `account` 列 SHALL 写
**展示名**（`accountLabel(name, cardLast4)`，如「工商银行 (1122)」），
并 SHALL 新增可选 `cardLast4` 列写机器匹配键；
导入 SHALL 按 `(name, cardLast4)` 复合匹配，
MUST NOT 再用 `firstOrNull { it.name == ... }` 静默命中同名第一张卡。
**加密备份 / `BackupAccount.name` 仍是纯名且 MUST NOT 被改动**（走 `id` 还原）。

#### Scenario: 筛选与统计不再撞名

- **GIVEN** 两张账户名同为「工商银行」、卡号后四位 1122 / 3344
- **WHEN** 打开筛选面板账户选项、统计页账户排行、从排行跳明细后的筛选 chip
- **THEN** 三处都分别显示「工商银行 (1122)」与「工商银行 (3344)」
- **AND** 筛选面板账户 tile 的次要行同样带「尾号 1122」/「尾号 3344」，
  MUST NOT 依赖搜索框才能分辨

#### Scenario: 导出带卡号且复合匹配回原账户

- **GIVEN** 两张 `name = "工商银行"`、`cardLast4` 为 1122 / 3344 的账户各有流水
- **WHEN** 导出 CSV
- **THEN** `account` 列分别是「工商银行 (1122)」「工商银行 (3344)」，
  `cardLast4` 列分别是 `1122` / `3344`
- **WHEN** 用该文件重建 / 导入
- **THEN** 两笔流水分别回到 id 对应的原账户，MUST NOT 归到同一张卡
- **AND** 加密备份的 `BackupAccount.name` 仍是纯名「工商银行」

#### Scenario: 老 CSV 同名歧义显式报错

- **GIVEN** 库里有两张同名「工商银行」、卡号 1122 / 3344
- **WHEN** 导入一份没有 `cardLast4` 列、账户列写纯名「工商银行」的老 CSV
- **THEN** 导入 MUST 报「账户不唯一，请指定 cardLast4」并整批回滚
- **AND** MUST NOT 静默把流水挂到第一张卡

### Requirement: 记一笔采用统一表单网格与有上限的快捷区

「记一笔」SHALL 保留固定 52dp 标签列与细分割线，同区域 MUST NOT 混用
带框输入框、无边界文本按钮与芯片作为顶层块。**支出 / 收入态**账户与模板
SHALL 不再作为首屏表单按钮或平铺快捷 chips 存在，统一由键盘上方 chip
直达底部 sheet；**转账态**账户 SHALL 以两张首屏卡片常驻，MUST NOT 再进 chips。
分类快捷栏 SHALL 保留（低摩擦优点）。金额 SHALL 单独大字号展示。
账户选择面板 SHALL 按「账户选择面板展示品牌图标与卡种」呈现，
MUST NOT 再是纯文字列表。
「最近收款方 / 常用账户各最多 4 个」「模板每行 3 个两行铺开」的
快捷区上限条款随第 80 批作废。

#### Scenario: 账户 chip 显示默认账户（收支态）

- **GIVEN** 默认账户为「工商银行 (1122)」且为信用卡
- **WHEN** 打开记一笔（支出）
- **THEN** 账户 chip 完整显示该账户名，点开两列弹层同名卡由「尾号 xxxx」区分
- **AND** 首屏表单区 MUST NOT 再出现「账户」行
- **AND**（第 84 批）弹层顶部 MUST NOT 出现搜索框、叉号按钮或并排分段 chip

### Requirement: 账户选择面板展示品牌图标与卡种

账户选择 SHALL 复用 `ui/components/AccountSelectDialog` **唯一通用出口**，
记一笔账户 chip、转账两张账户卡、模板与周期规则表单的转出/转入行、
明细筛选（含统计账户排行下钻）SHALL 全部调用它，
MUST NOT 再存在第二份账户下拉或第二套账户网格。
第 84 批：该出口 SHALL 以 `ModalBottomSheet` 呈现，
顶部 SHALL 只有「标题 + 一枚布局轮换图标 + 当前：xxx」一行，
MUST NOT 再出现叉号关闭按钮、搜索框或并排分段 chip；
关闭由 sheet 自带的点遮罩 / 下滑 / 系统返回三种路径承担。
每格 SHALL 复用项目既有 `AccountIconBox`（预制品牌 / 自定义 / 中性首字回退），
图标尺寸 SHALL 为 **44dp**；卡种角标 SHALL 按图标尺寸等比放大
（`size * 0.4`，约 17dp，字号 `size * 0.5`），MUST NOT 再用
12dp / 7sp 的「储 / 信」小圆。
面板与转账两卡 MUST NOT 显示账户余额；卡号后四位与卡种 SHALL 降为次要小字
（形如 `尾号 1122 · 储蓄卡 · CNY`），由全站唯一组合点
`ui.components.accountSecondaryLabel` 生成。
**第 84 批**：账户搜索框整体下线，同名卡 SHALL 仅由该次要行区分，
`cardLast4` MUST NOT 被连带删除。
`allowNone` 的「不指定账户 / 全部账户」SHALL 与真实账户同款 tile
收进**网格第一格**（灰圆「—」+ 标签），MUST NOT 再单独占一条整行横幅；
分组视图里它 SHALL 排在所有 `AccountType` 组标题之前。
归档项由调用方决定是否过滤，组件不过滤。
MUST NOT 为对齐截图新增 Gradle 依赖或图标资源
（轮换图标用既有 `Icons.Filled.List` + Compose `Canvas` 自绘，零新增 drawable）。

#### Scenario: 一眼分储信

- **GIVEN** 用户有「工商银行」储蓄卡与「招商银行」信用卡
- **WHEN** 打开任一账户选择器
- **THEN** 每格有 44dp 品牌图标与放大角标，次要行显示「尾号 xxxx · 储蓄卡 / 信用卡」
- **AND** 不出现余额数字、搜索框与叉号，MUST NOT 只剩纯文本 + 12dp 小角标

#### Scenario: 不指定收进网格第一格

- **GIVEN** 模板表单或明细筛选（`allowNone = true`）
- **WHEN** 打开账户选择器
- **THEN** 网格第一格是「不指定账户 / 全部账户」灰圆 tile，与真实账户等高同宽
- **AND** MUST NOT 再出现一条独立的整行横幅

#### Scenario: 自定义图标也走同一位置

- **GIVEN** 某账户在编辑页导入了自定义 PNG
- **WHEN** 打开账户选择器 / 资产页 / 排序页 / 模板预览 / 明细筛选
- **THEN** 各处都显示该自定义图标，尺寸 / 角标与预制一致
- **AND** 导入失败或文件缺失时回退中性圆 + 中文首字，MUST NOT 显示空白

#### Scenario: 记一笔转账仍锁定目标

- **GIVEN** 转账态点「转出账户」卡
- **WHEN** 选择一张卡
- **THEN** 只写回 `fromAccountId`，`toAccountId` 不变，弹层关闭
- **AND** 弹层内 MUST NOT 再出现「转出账户 / 转入账户」分段

### Requirement: 账户选择器提供全局与分组两种布局

`AccountSelectDialog` 标题栏右侧 SHALL 提供**且仅提供一枚**图标按钮，
点一下 SHALL 在两种布局间轮换（`AccountPickerLayout.GLOBAL` ↔ `GROUPED`），
MUST NOT 再出现两枚并排的分段 chip、MUST NOT 弹二级菜单、
MUST NOT 引入第三种布局、MUST NOT 改任何落库排序字段。
图标 SHALL 显示**当前模式**且两种模式用不同图形区分：
`GLOBAL` 用既有 `Icons.Filled.List`（三横线），
`GROUPED` 用 `Canvas` 自绘 2×2 圆角方块；
`contentDescription` SHALL 写明「当前 X，点击切换到 Y」供 TalkBack 使用。
默认 SHALL 为 `GLOBAL`。两种布局 SHALL 共用同一份账户数据。

- `GLOBAL` SHALL 按 `quickOrder` 全局排序、不分 `AccountType`、归档沉底，
  与「全部账户排序」独立页及记一笔既有候选顺序完全一致。
- `GROUPED` SHALL 按 `AccountType` 固定组序
  `ASSET → LIABILITY → PREPAID → INVESTMENT` 分组，
  **组内 SHALL 按 `sortOrder` 继承资产页分组排序**，组间标题复用
  `AccountType.displayName()`；MUST NOT 把 `quickOrder` 混进分组视图。
- 切换后同一账户在两种视图中位置不同 SHALL 视为**预期行为**，MUST NOT 被
  「统一排序」修掉（活文档 §1753 两套排序分离的既有铁律）。

布局选择 SHALL 跨启动持久化于 `LedgerPrefs.accountPickerLayout`
（`"global"` / `"grouped"`，未设置或非法值回退 `"global"`），
由 `rememberAccountPickerLayout()` 统一读写；SHALL NOT 进 Room、
SHALL NOT 进备份 DTO、SHALL NOT 触发 Migration，
`AppDatabase.version` 仍 SHALL 为 9、`Migrations.ALL` 仍为空数组。
**第 84 批修订**：原文「MUST NOT 引入 `ModalBottomSheet`（T12）」作废——
该禁令只是记一笔低频项 sheet 的局部惯例，统计页 `StatsDrillSheet` 自第 69 批
起已用 `ModalBottomSheet` 并写进 `category-stats` 主 spec；
本选择器按使用者 1B 拍板改用 `ModalBottomSheet`，
MUST NOT 再自己补遮罩 `clickable`（会与 sheet 自带 scrim 抢事件）。
长按拖拽（§3.1）与新增依赖仍 MUST NOT。

#### Scenario: 两种顺序各自正确

- **GIVEN** 在「全部账户排序」页把信用卡 A 排到第一、资产页把储蓄卡 B 排到资产组第一
- **WHEN** 打开账户选择器看全局视图，再点右上角图标切到分组视图
- **THEN** 全局视图第一项是 A；分组视图「资产账户」组第一项是 B
- **AND** 再点一次图标切回全局仍是 A，`sortOrder` 与 `quickOrder` 两列均零变化

#### Scenario: 跨启动记住

- **GIVEN** 点图标切到分组视图并关闭弹层
- **WHEN** 杀掉进程重启 App、再打开任一账户选择器
- **THEN** 仍是分组视图，右上角图标是 2×2 方块
- **AND** 换设备 / 清数据后回退默认全局视图与三横线图标

#### Scenario: 图标轮换与状态自解释

- **GIVEN** 当前为全局视图
- **WHEN** 查看标题栏右侧
- **THEN** 只有一枚三横线图标，无文字分段、无叉号、无搜索框
- **WHEN** 点一下
- **THEN** 立刻变为带 `AccountType` 组标题的分组视图，图标变为 2×2 方块

### Requirement: 模板与周期规则复用通用分类与账户选择器

模板与周期规则共用的 `QuickEntryForm` SHALL 以整行 `›` 入口
（标签列 + 值 + `›`）打开通用选择器：分类走 `CategorySelectDialog`，
转出 / 转入账户走 `AccountSelectDialog`。
`DropdownSelector` SHALL 从全仓下线，MUST NOT 再出现任何下拉式分类/账户选择。
账户选择 SHALL 支持「不指定账户」（`allowNone`），
选中后 SHALL 能把已有账户改回 `null`，MUST NOT 迫使使用者重建模板或周期规则；
`QuickEntryForm` SHALL 接收 `List<AccountEntity>` 而非 `List<Pair<Long,String>>`，
以便通用选择器显示图标、卡种与卡号尾号。
保存时的分类类型校验、周期规则账户必填校验、转账两账户不同校验 SHALL 零改动。
统计页无独立账户筛选入口，账户排行下钻 SHALL 继续经
`DetailViewModel.filterByAccount` 落到明细 `FilterSheet`，
该面板的账户条件 SHALL 同样走 `AccountSelectDialog`
（`allowNone = true`，`noneLabel = "全部账户"`，归档项不进选择器）。
**第 84 批修订**：四处入口打开的账户选择器 SHALL 是同一份
`ModalBottomSheet`（单枚图标轮换、无叉号 / 无搜索 / 不指定入网格），
MUST NOT 出现「双布局分段」字样或第二套壳。

#### Scenario: 周期规则改回不指定账户

- **GIVEN** 编辑一条周期规则，转出账户原为「工商银行 (1122)」
- **WHEN** 点转出账户行 → 点网格第一格「不指定账户」→ 保存
- **THEN** 保存被既有必填校验拦下并给出「请选择转出账户」
- **AND** 表单上值确实变成「不指定」，MUST NOT 还卡着旧账户无法清空

#### Scenario: 模板账户可留空

- **GIVEN** 新建一个模板（草稿语义，账户可留空）
- **WHEN** 点网格第一格「不指定账户」并保存
- **THEN** 保存成功，`fromAccountId = null`，生成流水时由默认账户兜底

#### Scenario: 统计下钻与明细同一面板

- **GIVEN** 从统计页账户排行点某账户下钻到明细
- **WHEN** 打开筛选面板的「账户」行
- **THEN** 出现与记一笔同款的通用账户选择器（单枚图标轮换的 `ModalBottomSheet`）
- **AND** 点网格第一格「全部账户」清空该筛选条件

## ADDED Requirements

### Requirement: 账户选择器采用原生底部弹层与单枚图标轮换

`AccountSelectDialog` SHALL 以
`ModalBottomSheet`（`@OptIn(ExperimentalMaterial3Api::class)`）为壳，
`containerColor = LedgerColors.CardSurface`，保留默认 drag handle。
关闭 SHALL 由组件自带的三条路径承担，MUST NOT 再自定义叉号按钮或遮罩点击层：

- 点遮罩（scrim）
- 下滑弹层（swipe to dismiss）——使用者第 84 批 1B 明确要这个手势
- 系统返回键 / 侧滑返回手势
- 点任一账户或「不指定」tile 后 SHALL 立即写回并关闭

顶部 SHALL 只有一行：标题（`titleMedium`）+
一枚 24dp 布局轮换图标按钮 + 下一行「当前：xxx」（`labelSmall`，
使用者第 2 项明确保留）。MUST NOT 出现
`Icons.Filled.Close` 叉号、`OutlinedTextField` 搜索框、
`FilterChip` 分段或任何独立整行的「不指定」横幅。

内容 SHALL 用 `LazyColumn` + `chunked(2)` 的 `Row(weight(1f))` 两列网格，
组标题以 `item {}` 插入；MUST NOT 用 `LazyVerticalGrid`（T12 / §7.3）、
MUST NOT 用 `Column + verticalScroll`（与 sheet 下滑手势抢滚动），
MUST NOT 新做长按拖拽（§3.1，排序仍走「全部账户排序」独立页）。
`ModalBottomSheet` 与 `Dialog` 嵌套 SHALL 沿用项目既有先例
（`FilterSheet` 内已嵌 `CategorySelectDialog`），MUST NOT 为此改 4 个调用点的容器。

**零新增依赖 / 零新增权限 / 零新增图标资源**：
`ModalBottomSheet` 来自已在用的 `androidx.compose.material3:material3`
（BOM 2026.06.00），`Icons.Filled.List` 来自已在用的 material-icons-core，
分组图标是 `Canvas` 自绘矩形；MUST NOT 加 `material-icons-extended`、
MUST NOT 加图片加载库、MUST NOT 新增 drawable、MUST NOT 声明 `INTERNET`。
`AppDatabase.version` 仍 SHALL 为 9，本批 SHALL 可直接覆盖 v0.2.62。

#### Scenario: 三种关闭路径都可用

- **GIVEN** 记一笔（或模板 / 周期 / 明细筛选）打开账户选择器
- **WHEN** 分别用系统返回手势、点上方灰色遮罩、向下滑弹层
- **THEN** 三次都能关闭弹层且未选中任何账户
- **WHEN** 再点一张账户
- **THEN** 写回对应 id 并关闭，MUST NOT 出现「关不掉」或双击才关

#### Scenario: 顶部只有一行控件

- **GIVEN** 打开任一账户选择器
- **WHEN** 查看网格上方
- **THEN** 只有标题 + 一枚图标 + 「当前：xxx」灰字
- **AND** MUST NOT 出现「全局排序 / 按类型分组」两枚 chip、搜索框或叉号
- **AND** 「不指定 / 全部账户」在网格第一格而非独立横幅

#### Scenario: 依赖与权限零变化

- **GIVEN** v0.2.62 的依赖清单与 `AndroidManifest.xml`
- **WHEN** 构建 v0.2.63
- **THEN** 两者逐项一致，`app/build.gradle.kts` 无新增 implementation，
  Manifest 无新增 `uses-permission`，`app/src/main/res/drawable/` 数量不变
