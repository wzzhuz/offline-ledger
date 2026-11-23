# reporting-ui Delta（第 52 批）

## MODIFIED Requirements

### Requirement: 两级分类快选不得增加记账摩擦

分类选择 SHALL 先展示**启用一级**横向快捷栏。点击一级 SHALL 立即按一级记
（`categoryId` 指向该一级自身）并可展开二级 chips；**再点同一级 SHALL 收起二级栏**。
系统 MUST NOT 显示「就按xx记」之类二次确认按钮，MUST NOT 要求额外点击才能按一级保存，
MUST NOT 强制选到二级，MUST NOT 提供三级。选了二级后再点对应一级 SHALL 重置回一级。

#### Scenario: 只选一级

- **GIVEN** 点中央 `+`、点一级「餐饮」、输入金额
- **WHEN** 直接保存
- **THEN** ≤ 3 次交互完成记账，界面无任何确认按钮
- **AND** `categoryId = 1` 且 `rootCategoryId = 1`

#### Scenario: 选错二级后改按一级

- **GIVEN** 展开「餐饮」并点了「聚餐」
- **WHEN** 再点一次「餐饮」
- **THEN** `categoryId` 回到 1，二级栏收起，保存按一级记账

### Requirement: 记一笔布局紧凑且首屏可见备注与收款方

（原条款不变，补：）`FormRow("分类")` SHALL 带 `›` 且整行可点开全量选择器，
不新增 chip、不占额外首屏高度；首屏仍 MUST 一屏无需上滑可见。

### Requirement: 分类候选按收支类型统一过滤

（原条款不变，补：）新记一笔默认一级与切类型重置 SHALL 只取**启用且未归档**项，
MUST NOT 落到停用扩展项上。

### Requirement: 记一笔提供模板快选且两步内完成

（原条款不变，仅把「保持既有两级分类快选交互不变」改为：）
计算器键盘交互 SHALL 保持不变；两级分类快选按第 52 批语义调整。

## ADDED Requirements

### Requirement: 分类全量选择器采用分组 chips 且不用下拉框

`FormRow("分类", onRowClick = …)` SHALL 与「账户 ›」同构，整行可点开全量选择器。
全量选择器 SHALL 采用按一级分组的 WrapRow chips + 搜索框，
MUST NOT 使用 Spinner / ExposedDropdown / DropdownMenu，
MUST NOT 退化成需长距离滚动的竖排 TextButton 列表。
组标题 SHALL 可点 = 按一级直记，组内 chip SHALL 可点 = 选该二级；
点任一项 SHALL 立即选中并关闭，MUST NOT 二次确认。
未归档的停用一级 / 二级 SHALL 全部可见可搜并带「停用」角标；归档项 SHALL 不进选择器。
搜索命中 SHALL 跨组平铺，二级 label MUST 为「一级 / 二级」全名。

#### Scenario: 从更多分类里选停用项

- **GIVEN** 默认快捷栏只有 7 个启用一级
- **WHEN** 点「分类 ›」→ 搜索「话费」→ 点「居住 / 话费（停用）」
- **THEN** 弹层关闭，`categoryId = 4007`、`rootCategoryId = 4`
- **AND** 快捷栏临时显示「居住」与选中 chip，保存成功

#### Scenario: 按一级直记

- **GIVEN** 全量选择器展开「学习娱乐（停用）」组
- **WHEN** 点组标题
- **THEN** 立即按该一级选中并关闭，MUST NOT 弹确认框

#### Scenario: 首屏不被选择器挤占

- **GIVEN** 中小屏手机打开记一笔
- **WHEN** 不滚动、不点「分类 ›」
- **THEN** 金额 / 分类快捷栏 / 账户 / 日期 / 对方 / 描述 / 保存仍一屏可见
- **AND** 页面上没有「更多分类」chip

**理由**：使用者反馈停用项应像「选择更多账户」一样可达但不要用下拉框、
要尽可能方便选择；账户侧第 8/44 批的「整行 › + 全量弹层 + 当前选中兜底」已验证，
分类侧沿用同一模式但把长列表换成分组 chips，兼顾 106 行可达性与铁律 3。
