# Delta for 备份与恢复

## ADDED Requirements

### Requirement: 备份 schemaVersion 升至 3 且兼容 v1 与 v2

`BackupSchema.CURRENT` SHALL 为 3、`MIN_SUPPORTED` SHALL 保持 1。
`BackupEnvelope` 四项头部字段 MUST NOT 改动。
系统 SHALL 能导入 `schemaVersion = 1` 与 `= 2` 的加密备份。

#### Scenario: 导入 v1 备份

- **GIVEN** 一份 v1 加密备份（无 templates / recurringRules / budgets / linkedRecordId）
- **WHEN** 用 6b 包导入
- **THEN** 解码与落库成功、新增列表为空、linkedRecordId 为 null
- **AND** 余额重算后净资产一致

#### Scenario: 导入 v2 备份

- **GIVEN** 一份 v2 加密备份（含模板与规则、无 budgets / linkedRecordId）
- **WHEN** 用 6b 包导入
- **THEN** 模板与规则完整保留、budgets 为空
- **AND** 余额重算后净资产一致

**理由**：`ignoreUnknownKeys` + 新字段带默认值，
旧 JSON 缺键时自动补空列表 / null，兼容不需要额外分支。

---

### Requirement: 预算规则与 linkedRecordId 纳入备份

`BackupPayload` SHALL 增加带默认空列表的 `budgets`，
`BackupRecord` SHALL 增加带默认 null 的 `linkedRecordId`，
`BackupCounts` SHALL 增加 `budgets` 计数。
`exportSnapshot` SHALL 导出全部预算，
`importBackup` SHALL 先清空再插入，事务末尾 SHALL 重算余额。

#### Scenario: v3 往返

- **GIVEN** 3 条预算规则与一条带 linkedRecordId 的 SETTLEMENT
- **WHEN** 导出再导入
- **THEN** 预算字段全量一致、SETTLEMENT 关联不丢失
- **AND** `BackupCounts` 报告 budgets = 3

#### Scenario: v1 备份清空预算

- **GIVEN** 当前有预算规则，导入一份 v1 备份
- **WHEN** 覆盖导入完成
- **THEN** 预算被清空
- **AND** 这与「全量替换」语义一致

**理由**：预算塞 SharedPreferences 就进不了备份，换设备全丢；
进 Room 并纳入 payload 才能「数据随时可带走」。
