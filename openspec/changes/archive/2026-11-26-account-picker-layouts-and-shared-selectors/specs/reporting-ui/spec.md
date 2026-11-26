# reporting-ui · delta（第 83 批）

## MODIFIED Requirements

### Requirement: 分类全量选择器采用分组 chips 且不用下拉框

记一笔「分类 ›」、明细筛选「分类」、Firefly 导入分类映射、
**以及模板与周期规则表单的「分类」行** SHALL 复用同一份只读
`CategorySelectDialog`，采用**顶部搜索 + 左侧一级分组导航 + 右侧 4 列
等宽彩色图标网格**版式，4 字分类名 SHALL 完整可见。
**模板与周期规则 SHALL 以整行 `›` 入口打开该选择器**，
MUST NOT 再使用 `DropdownMenu` / Spinner / ExposedDropdown / 任何下拉列表；
`allowAll` SHALL 在该处复用为「不指定分类」，入口标题 SHALL 写明「可不指定」，
MUST NOT 把 `allowAll` 的 tile 文案改成本体之外的第二套实现。
选择器 SHALL 显示彩色 `CategoryIconCatalog` 图标，
统计页分类占比卡的图例列表 SHALL 同样使用彩色 `CategoryIconCatalog` 图标
（`CategoryGlyph`，缺项回退中性圆 + 首字）；
记一笔快捷栏、明细列表行、账户详情 SHALL 仍是中性圆 + 中文首字。
四处入口 SHALL 保持「整行 › 入口 + 立即选中 + 无输入框 / 无保存按钮 / 无写库路径」。
彩色图标配色 MUST 走 `CategoryIconTints`，`colorHex` MUST NOT 被改写，
统计扇区色板 MUST NOT 改为图标配色。
`CategorySelectDialog` 本体（`columns = 4` / `allowAll`）SHALL 零改动。

#### Scenario: 模板选分类不再下拉

- **GIVEN** 新建支出模板，预算分类有「餐饮 / 食材」
- **WHEN** 点「分类」整行
- **THEN** 打开与记一笔同款的 4 列图标网格，左侧一级导航可见
- **AND** 点「食材」立即回填并关闭，入口值显示「食材」
- **AND** 展开时 MUST NOT 出现任何下拉菜单

#### Scenario: 模板可不指定分类

- **GIVEN** 模板已选「餐饮 / 食材」
- **WHEN** 再点分类行并点「全部分类」
- **THEN** 入口值回到「不指定」，保存后 `categoryId = null`、`rootCategoryId = null`
- **AND** MUST NOT 因无法清空而被迫重建模板

#### Scenario: 统计占比图例彩色

- **GIVEN** 统计页分类占比卡处于「按小类」口径
- **WHEN** 查看图例列表
- **THEN** 预置分类显示彩色 Tabler 图标，自定义 / 缺项分类回退中性圆 + 中文首字
- **AND** 环形扇区颜色仍由 `ChartData.chartArgb` 按 id 稳定分配，与图标 tint 无关

#### Scenario: 其余页面零回归

- **WHEN** 打开记一笔快捷栏、明细列表行或账户详情
- **THEN** 分类标识仍是中性圆 + 中文首字，MUST NOT 出现彩色图标

### Requirement: 模板分类按收支类型过滤并按一级分组

模板与周期规则表单的分类选择器 SHALL 只显示与当前选中收支类型一致、
未归档的分类；`CategorySelectDialog` 的左侧一级导航 SHALL 表达一级分组，
二级 SHALL 经 `CategoryDisplay` 以「一级 / 二级」形式标注归属
（回填筛选 chip 的 `displayLabel` 契约一字不动）。
切换收支类型时 SHALL 清空已选分类；保存前 SHALL 校验分类类型与流水类型一致。
模板列表行 SHALL 显示该模板的分类。
**第 83 批归档修订**：原文「分类下拉」「展开分类下拉」措辞已随
`DropdownSelector` 整体下线作废，改为「整行 › 入口 + 通用选择器」；
账户选择同批改通用选择器（见 ADDED「模板与周期规则复用通用…」）。

#### Scenario: 选支出不出现收入分类

- **GIVEN** 新建模板时选中「支出」
- **WHEN** 点开分类选择器
- **THEN** 只出现餐饮 / 交通 / 购物等支出分类及其二级
- **AND** 不出现工资 / 月薪 / 奖金

#### Scenario: 二级归属清晰

- **GIVEN** 「食材」是「餐饮」的二级
- **WHEN** 查看选择器当前值与列表行
- **THEN** 显示「餐饮 / 食材」而非孤立的「└ 食材」

#### Scenario: 列表可读

- **GIVEN** 已存一个模板选了「餐饮 / 食材」
- **WHEN** 打开模板管理
- **THEN** 列表行显示名称、金额与「餐饮 / 食材」

### Requirement: 账户选择面板展示品牌图标与卡种

账户选择 SHALL 复用 `ui/components/AccountSelectDialog` **唯一通用出口**，
记一笔账户 chip、转账两张账户卡、模板与周期规则表单的转出/转入行、
明细筛选（含统计账户排行下钻）SHALL 全部调用它，
MUST NOT 再存在第二份账户下拉或第二套账户网格。
每格 SHALL 复用项目既有 `AccountIconBox`（预制品牌 / 自定义 / 中性首字回退），
图标尺寸 SHALL 为 **44dp**；卡种角标 SHALL 按图标尺寸等比放大
（`size * 0.4`，约 17dp，字号 `size * 0.5`），MUST NOT 再用
12dp / 7sp 的「储 / 信」小圆。
面板与转账两卡 MUST NOT 显示账户余额；卡号后四位与卡种 SHALL 降为次要小字
（形如 `尾号 1122 · 储蓄卡 · CNY`），由全站唯一组合点
`ui.components.accountSecondaryLabel` 生成，SHALL NOT 要求使用者记忆卡号。
搜索 SHALL 仍按 `accountBaseLabel`（账户名含卡号后缀）过滤，
**搜索命中 SHALL 跨组平铺**；归档项由调用方决定是否过滤，组件不过滤。
MUST NOT 为对齐截图新增 Gradle 依赖或图标资源。
**第 83 批归档修订**：本 Requirement 原只约束记一笔账户 sheet；
现扩为全站通用出口，并追加下条 ADDED 的双布局能力。

#### Scenario: 一眼分储信

- **GIVEN** 用户有「工商银行」储蓄卡与「招商银行」信用卡
- **WHEN** 打开任一账户选择器
- **THEN** 每格有 44dp 品牌图标与放大角标，次要行显示「尾号 xxxx · 储蓄卡 / 信用卡」
- **AND** 不出现余额数字，MUST NOT 只剩纯文本 + 12dp 小角标

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

## ADDED Requirements

### Requirement: 账户选择器提供全局与分组两种布局

`AccountSelectDialog` 顶部 SHALL 提供且仅提供两枚分段
**「全局排序」/「按类型分组」**（`AccountPickerLayout.GLOBAL` / `GROUPED`），
默认 SHALL 为 `GLOBAL`。两种布局 SHALL 共用同一份过滤后的账户数据，
MUST NOT 引入第三种布局、MUST NOT 改任何落库排序字段。

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
MUST NOT 引入 `ModalBottomSheet`（T12）、长按拖拽（§3.1）或新增依赖。

#### Scenario: 两种顺序各自正确

- **GIVEN** 在「全部账户排序」页把信用卡 A 排到第一、资产页把储蓄卡 B 排到资产组第一
- **WHEN** 打开账户选择器看「全局排序」，再切「按类型分组」
- **THEN** 全局视图第一项是 A；分组视图「资产账户」组第一项是 B
- **AND** 切回全局仍是 A，`sortOrder` 与 `quickOrder` 两列均零变化

#### Scenario: 跨启动记住

- **GIVEN** 切到「按类型分组」并关闭弹层
- **WHEN** 杀掉进程重启 App、再打开任一账户选择器
- **THEN** 仍是「按类型分组」选中态
- **AND** 换设备 / 清数据后回退默认「全局排序」

#### Scenario: 搜索跨组平铺

- **GIVEN** 当前是「按类型分组」视图
- **WHEN** 在搜索框输入「3344」
- **THEN** 结果按全局顺序平铺、不带分组标题，输入后四位能定位到对应卡
- **AND** 清空搜索后回到原分组视图

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

#### Scenario: 周期规则改回不指定账户

- **GIVEN** 编辑一条周期规则，转出账户原为「工商银行 (1122)」
- **WHEN** 点转出账户行 → 点「不指定账户」→ 保存
- **THEN** 保存被既有必填校验拦下并给出「请选择转出账户」
- **AND** 表单上值确实变成「不指定」，MUST NOT 还卡着旧账户无法清空

#### Scenario: 模板账户可留空

- **GIVEN** 新建一个模板（草稿语义，账户可留空）
- **WHEN** 点「不指定账户」并保存
- **THEN** 保存成功，`fromAccountId = null`，生成流水时由默认账户兜底

#### Scenario: 统计下钻与明细同一面板

- **GIVEN** 从统计页账户排行点某账户下钻到明细
- **WHEN** 打开筛选面板的「账户」行
- **THEN** 出现与记一笔同款的通用账户选择器（含双布局分段）
- **AND** 点「全部账户」清空该筛选条件
