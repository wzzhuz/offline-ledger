## ADDED Requirements

### Requirement: 预置分类清单可替换且支持启用开关
随包 `assets/default_categories.json` SHALL 是分类清单的唯一可替换来源；更换清单 MUST NOT 需要改 Kotlin 代码。`categories.enabled` SHALL 控制录入侧可见性，停用 MUST NOT 删除行、MUST NOT 改写已有流水，且统计、排行、历史、CSV 导出与加密备份 MUST 仍读全量。

#### Scenario: 停用后记一笔不可选但统计仍计入
- **WHEN** 使用者停用一级「养车」
- **THEN** 记一笔、筛选面板、预算、模板的分类选项不再出现「养车」及其二级
- **AND** 统计占比卡、排行、历史流水与该分类的既有金额保持不变

#### Scenario: 重新启用
- **WHEN** 使用分类管理再次启用「养车」
- **THEN** 该一级与其二级重新出现在录入侧，历史流水归属不变

### Requirement: 按 A 方案区分租房与代缴父母水电
「居住」一级 SHALL 只包含租房 / 自住产生的成本（房租、水费、电费、燃气、物业、宽带）。代父母缴纳的水电 SHALL 归到「人情 / 赡养·代缴水电」，MUST NOT 与租房费用混在同一一级下。

#### Scenario: 水电两种动机分开
- **WHEN** 使用者记一笔老家父母的水电费
- **THEN** 可选「人情 / 赡养·代缴水电」
- **AND** 「居住 / 水费」「居住 / 电费」仍只用于自己住处

#### Scenario: 同名二级不再混淆
- **WHEN** 图表或筛选 chip 显示二级分类名
- **THEN** MUST 经 `CategoryDisplay.resolve` 带父级名，且每个一级下 MUST NOT 再设名为「其他」的二级
