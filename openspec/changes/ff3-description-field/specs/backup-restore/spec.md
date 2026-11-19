# backup-restore Delta

## ADDED Requirements

### Requirement: 描述字段随备份与快照重建

加密备份与自动快照 SHALL 带上每条流水的 description；旧备份缺该键时 MUST 按空串解析，不得整份拒绝。

#### Scenario: 导出再恢复描述不丢

- **WHEN** 导出一份含 description 的加密备份并恢复
- **THEN** 恢复后 description 原样存在，`PRAGMA foreign_key_check` 零错误，不复发 1811

#### Scenario: 旧备份缺键兼容

- **WHEN** 恢复一份不含 description 键的 v4 备份
- **THEN** 解析成功，历史流水 description 为空串，其余字段不受影响

#### Scenario: 自有 CSV 重建

- **WHEN** 从本 App 导出的 12 列 CSV 重建
- **THEN** description 原样回写且二次重建按复合唯一键幂等；旧 11 列 CSV 缺列时按空串处理
