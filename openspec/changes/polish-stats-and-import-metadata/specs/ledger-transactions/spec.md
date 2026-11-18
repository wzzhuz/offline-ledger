# ledger-transactions Delta

## MODIFIED Requirements

### Requirement: 导入来源唯一标记
导入流水的来源 MUST 完全由 `importSource` + `sourceId` 字段承载，MUST NOT 写入
备注；备注 MUST 逐字保留来源文件的原文（Firefly III 为 `notes` 列）；流水详情
MUST 显示来源信息；无对应字段的元数据（description / tags / budget / 分组）
MUST NOT 拼接进备注，且 MUST 在导入结果中明示未迁移。

#### Scenario: Firefly III 导入不改备注
- **WHEN** 导入含 `notes` 列的 Firefly III 导出
- **THEN** 落库备注与 `notes` 原文一致，不含 `ff3:` 前缀或「分组：」等拼接文本

#### Scenario: 详情看来源
- **WHEN** 使用者点开一条导入流水
- **THEN** 详情显示「来源：firefly-iii · journal xxx」，去重仍走唯一索引
