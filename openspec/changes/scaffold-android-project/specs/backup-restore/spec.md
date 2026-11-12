# Delta for 备份与恢复

## ADDED Requirements

### Requirement: 备份信封头部稳定且带版本号

系统 SHALL 提供 `BackupSchema.CURRENT` 与 `MIN_SUPPORTED` 常量，
并提供与 Room Entity 分离的 `@Serializable BackupEnvelope`。
信封头部 SHALL 包含 `schemaVersion`、`exportedAtEpochMilli`、`currencyCode`
与 `counts`，且头部字段 SHALL NOT 在后续阶段变更。

#### Scenario: 阶段 1 往备份里加数据 DTO

- **GIVEN** 阶段 1 定义了 `BackupAccount` / `BackupRecord` 等数据 DTO
- **WHEN** 把它们加入 `payload`
- **THEN** `schemaVersion`、`exportedAtEpochMilli`、`currencyCode` 三项字段名与含义不变
- **AND** 阶段 0 已导出的信封仍能被解析

#### Scenario: 序列化往返

- **GIVEN** 构造一个 `BackupEnvelope(exportedAtEpochMilli = 123456789L, currencyCode = "HKD")`
- **WHEN** 用 `kotlinx.serialization` 编码再解码
- **THEN** `schemaVersion` 为 1、`currencyCode` 为 `"HKD"`、时间戳不变

**理由**：备份格式必须在数据层阶段就定下来，否则开发期表结构频繁变动时
无法边写边导出验证。头部稳定是「旧备份可导入」的前提。
