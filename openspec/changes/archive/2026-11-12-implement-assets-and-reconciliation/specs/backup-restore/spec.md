# Delta for 备份与恢复

## ADDED Requirements

### Requirement: 对账锚点随备份往返且不改信封

`BackupAccount` SHALL 保留 `lastReconciledAt` / `lastReconciledBalanceCents` 两个可空字段，
`LedgerMappers.toDto` / `toEntity` SHALL 双向映射它们。
导出 SHALL 自动带上锚点，SHALL NOT 为此修改 `BackupEnvelope` 头部或递增 `schemaVersion`。
阶段 3 MUST NOT 写 Room Migration。

#### Scenario: 锚点备份往返

- **GIVEN** 某账户锚点为 `at = 1762000000000`、`balance = 99999` 分
- **WHEN** 导出 JSON 再解析
- **THEN** 两个锚点字段值不变
- **AND** `schemaVersion` 仍为 1、信封四项头部不变

#### Scenario: 旧备份无锚点

- **GIVEN** 一份阶段 1 导出的、账户无锚点字段的备份
- **WHEN** 解析并导入
- **THEN** 两个锚点字段为 `null`
- **AND** 核对视图默认退回「全部流水」且不报错

**理由**：字段在阶段 1 就预留了，阶段 3 只是启用；
可空 + 默认 null 保证旧备份兼容，不需要递增版本号或写迁移。
