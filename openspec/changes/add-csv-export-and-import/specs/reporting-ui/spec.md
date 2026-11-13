# Delta for 界面与交互

## ADDED Requirements

### Requirement: 设置页提供 CSV 导出与账单导入入口

设置页 SHALL 在「数据与备份」区提供「导出 CSV」与「导入账单」两个入口，
分别使用 SAF `CreateDocument("text/csv")` 与 `OpenDocument`。
MUST NOT 再显示「v1 不提供 CSV 导出（明文文件）」或任何
以明文风险为前提的提示。
导入前 SHALL 展示「将写入 N 条 / 跳过 M 条重复」确认；
结果或错误 SHALL 在现有 message / error 区展示。
4 个底部 Tab 结构 MUST NOT 改动。

#### Scenario: 导出入口

- **GIVEN** 用户在设置页点「导出 CSV」
- **WHEN** 选择保存位置
- **THEN** 写出 UTF-8 BOM 的 CSV 文件
- **AND** 页面显示导出行数

#### Scenario: 旧文案消失

- **GIVEN** 6c-2 已落地
- **WHEN** 查看设置页
- **THEN** 不存在「v1 不提供 CSV 导出」「明文文件，列在 v1.1」等字样

**理由**：设置页那句旧文案是阶段 5 按被推翻理由写的，
留着会和实际功能直接相反，属于规格腐烂。

---

### Requirement: 明细页提供可选币种筛选且与统计页口径一致

明细页 SHALL 提供币种筛选，默认 SHALL 为「全部币种」。
选中具体币种时列表与按日合计 MUST 只含该币种，
且 SHALL 与统计页同月份同币种的支出合计一致。
`pagedFiltered` 与 `dailyTotals` SHALL 通过
`(:currencyCode IS NULL OR currencyCode = :currencyCode)` 短路实现，
null 时行为 MUST NOT 改变。

#### Scenario: 选中 HKD

- **GIVEN** 同月有 HKD 与 CNY 两类流水
- **WHEN** 明细页筛选选 HKD
- **THEN** 列表与每日合计只含 HKD
- **AND** 该月 HKD 支出合计与统计页 HKD 视图一致

#### Scenario: 默认全部币种

- **GIVEN** 用户未设置币种筛选
- **WHEN** 打开明细页
- **THEN** 行为与 6c-1 一致，显示全部币种

**理由**：6c-1 明细页仍写「全部币种（按币种筛选请到统计页）」，
两页口径不一致；可选参数短路是最小改动，
且沿用既有唯一统计口径，不新开一份 SQL。
