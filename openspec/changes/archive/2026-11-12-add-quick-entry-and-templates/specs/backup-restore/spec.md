# Delta for 备份与恢复

## ADDED Requirements

### Requirement: 备份 schemaVersion 升至 2 且兼容 v1

`BackupSchema.CURRENT` SHALL 为 2、`MIN_SUPPORTED` SHALL 保持 1。
`BackupEnvelope` 的四项头部字段 MUST NOT 改动。
系统 SHALL 能导入 `schemaVersion = 1` 的加密备份且解析成功。

#### Scenario: 导入 v1 备份

- **GIVEN** 一份 v1 导出的 `schemaVersion = 1` 加密备份
- **WHEN** 用 v1.1 包导入
- **THEN** 解码与落库成功、全量替换现有数据
- **AND** 模板与周期规则列表为空
- **AND** 余额重算后净资产与备份一致

#### Scenario: 拒绝过高版本

- **GIVEN** 一份 `schemaVersion = 99` 的备份
- **WHEN** 解码
- **THEN** 抛出「备份版本高于当前应用」
- **AND** 现有账本完全不变

**理由**：`ignoreUnknownKeys = true` + 新增字段带默认值，
v1 JSON 缺两个键时会解析成空列表，兼容不需要额外分支。

---

### Requirement: 模板与周期规则纳入备份且全量替换语义一致

`BackupPayload` / `LedgerSnapshot` SHALL 增加 `templates` 与
`recurringRules` 两个列表，均带 `emptyList()` 默认值。
`exportSnapshot` SHALL 导出两张新表的全量数据，
`importBackup` SHALL 先清空再插入，事务末尾 SHALL 重算余额。
`BackupCounts` SHALL 增加两项带默认值的计数。

#### Scenario: v2 往返

- **GIVEN** 账本里有 3 条模板与 2 条规则
- **WHEN** 导出再导入
- **THEN** 模板 / 规则的字段全量一致
- **AND** `BackupCounts` 报告 3 / 2

#### Scenario: v1 备份清空规则

- **GIVEN** 当前有模板与规则，导入一份 v1 备份
- **WHEN** 覆盖导入完成
- **THEN** 模板与规则被清空
- **AND** 这与「全量替换」语义一致，不算数据丢失

**理由**：规则存 SharedPreferences 就进不了备份，换设备会全丢；
进 Room 并纳入 payload 才能「数据随时可带走」。
