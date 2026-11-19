## MODIFIED Requirements

### Requirement: 设置页提供 CSV 导出与账单导入入口

设置页 SHALL 在「数据与备份」区提供三个入口：「导出 CSV（核对 / 重建用）」、
「导入账单（第三方账单）」、「从 CSV 重建（本 App 导出文件）」，
分别使用 SAF `CreateDocument("text/csv")` 与 `OpenDocument`。
MUST NOT 再显示「v1 不提供 CSV 导出（明文文件）」或任何
以明文风险为前提的提示。
「导入账单」SHALL 只吃支付宝 / 微信 / 银行 / Firefly III 等第三方账单；
本 App 自有导出文件 SHALL 走「从 CSV 重建」，
投错入口 SHALL 给出明确引导且 MUST NOT 改动账本。
导入前 SHALL 展示「将写入 N 条 / 跳过 M 条重复」确认；
结果或错误 SHALL 在现有 message / error 区展示，
重建里被格式跳过的行 SHALL 在结果摘要中计数说明。
4 个底部 Tab 结构 MUST NOT 改动。

#### Scenario: 导出入口
- **GIVEN** 使用者在设置页点「导出 CSV（核对 / 重建用）」
- **WHEN** 选择保存位置
- **THEN** 写出 UTF-8 BOM 的 11 列 CSV 文件
- **AND** 页面显示导出行数

#### Scenario: 投错入口
- **GIVEN** 使用者选中本 App 自己导出的 CSV
- **WHEN** 点「导入账单（第三方账单）」
- **THEN** 提示改用「从 CSV 重建」或「从加密备份恢复」，账本不改动

#### Scenario: 旧文案消失
- **GIVEN** 6c-2 已落地
- **WHEN** 查看设置页
- **THEN** 不存在「v1 不提供 CSV 导出」「明文文件，列在 v1.1」等字样

**理由**：原先「导出文件可原样导入重建」的措辞没有区分两套格式，
导致使用者按直觉点「导入账单」被行级校验拒绝。按标题整块替换，
避免 specs 同时存在「可原样导入」与「必须分流」两套相反表述。
