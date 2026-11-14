## ADDED Requirements

### Requirement: CSV 与加密备份携带收款方

CSV 表头 SHALL 增加第 9 列 `counterparty`，导出 SHALL 写出该列；
导入时该列 SHALL 为**可选**（缺列按空串处理），保证旧 8 列 CSV 仍可导入。
加密备份的 `BackupRecord` / `BackupTemplate` / `BackupRecurringRule`
SHALL 带 `counterparty: String = ""`，往返后 MUST NOT 丢失。
导入去重键 MUST NOT 包含 `counterparty`。

#### Scenario: 往返一致

- **GIVEN** 一条收款方为「盒马」的支出
- **WHEN** 导出加密备份再导入
- **THEN** 该流水收款方仍为「盒马」

#### Scenario: 旧 CSV 仍可导入

- **GIVEN** 一份只有 8 列、无 `counterparty` 的 CSV
- **WHEN** 解析
- **THEN** 不报「表头缺少列」
- **AND** 每行收款方为空串

**理由**：使用者上一版导出的测试数据要能带回新装包；同时收款方
要能在电脑 Excel 与备份里一起核对。
