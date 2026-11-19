# bulk-edit Delta

## ADDED Requirements

### Requirement: 规则支持描述字段

规则字段目录 SHALL 包含「描述」条件，并 SHALL 提供「描述改为」动作；动作 MUST 走既有预览 → 单事务 → 末尾重算 → changeset 逐字段撤销链路。

#### Scenario: 按描述圈选并批量改名

- **WHEN** 配置条件「描述包含 超市」、动作「描述改为 牛奶鸡蛋」并应用到历史
- **THEN** 预览给出命中数与前后对比，确认后写入，撤销逐字段还原，备注与对方不受影响
