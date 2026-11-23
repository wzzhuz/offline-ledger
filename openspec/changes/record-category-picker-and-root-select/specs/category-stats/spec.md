# category-stats Delta（第 52 批）

## MODIFIED Requirements

### Requirement: 预置分类清单可替换且支持启用开关

`categories.enabled` SHALL 只控制**记一笔默认快捷栏**的可见性：停用 MUST NOT 删除行、
MUST NOT 改写已有流水、**MUST NOT 从「分类 ›」全量选择器或筛选面板隐藏**（仅加「停用」角标）。
统计、排行、历史、CSV 导出与加密备份 MUST 仍读全量。

#### Scenario: 停用后不进默认快捷栏但统计仍计入

- **WHEN** 停用一级「宠物」
- **THEN** 记一笔默认快捷栏不再出现「宠物」及其二级
- **AND** 「分类 ›」全量选择器与筛选面板仍可搜到并选中它们（带「停用」角标）
- **AND** 统计占比卡、排行、历史流水与既有金额保持不变

#### Scenario: 重新启用

- **WHEN** 分类管理再次启用「宠物」
- **THEN** 该一级与其二级重新出现在默认快捷栏，历史流水归属不变

## ADDED Requirements

### Requirement: 停用分类在全量选择器中仍可选

编辑引用停用分类的历史流水时，`RecordEditorViewModel` SHALL 提供**全量**分类数据源，
MUST NOT 在查询层 `filter { it.enabled }` 删掉停用项。
默认快捷栏 SHALL 由纯 JVM 出口 `CategoryQuickCandidates.visibleRoots / visibleChildren`
只列启用且未归档项，但**当前选中项所属一级与该二级 MUST 追加**。
筛选面板分类选项 SHALL 同样去掉 `enabled` 过滤，仅以「（停用）」角标区分。

#### Scenario: 编辑停用分类的历史流水

- **GIVEN** 一条流水引用停用的「居住 / 话费」
- **WHEN** 打开编辑流水
- **THEN** 快捷栏出现「居住」且「话费」为选中态
- **AND** 保存成功，历史 `categoryId` / `rootCategoryId` 不被改写

#### Scenario: 全量选择器不含归档项

- **GIVEN** 库里存在已归档分类
- **WHEN** 打开「分类 ›」全量选择器
- **THEN** 归档项不出现（归档 = 不再新选）
- **AND** 归档项仍可经快捷栏「当前选中项」兜底显示，统计口径不变

**理由**：第 51 批的 59 个 `enabled=false` 扩展项在旧语义下连数据源都进不去，
「更多分类」无从可选。语义收窄后停用回归「默认不占快捷栏、按需可选」本意，
统计 / 历史 / 导出读全量的既有铁律不变。
