# Delta for 隐私与安全基线

## ADDED Requirements

### Requirement: 预算与报销不新增权限或网络依赖

阶段 6b 的预算管理、SETTLEMENT 流水、净支出统计
SHALL NOT 新增任何 `uses-permission`，
SHALL NOT 引入含网络行为的依赖或任何新 Gradle 依赖，
SHALL NOT 改变 `allowBackup=false` 与数据库备份排除规则。
超支提醒 SHALL 只在应用内呈现，MUST NOT 申请 `POST_NOTIFICATIONS`。
`check_manifest_privacy.py` MUST 在阶段 6b 之后仍 PASS。

#### Scenario: 检查 Manifest

- **GIVEN** 阶段 6b 已落地
- **WHEN** 运行 `check_manifest_privacy.py`
- **THEN** 仍只有 `USE_BIOMETRIC` 一个 normal 级权限
- **AND** 无 `INTERNET`、无存储权限、无 `POST_NOTIFICATIONS`

#### Scenario: 依赖清单

- **GIVEN** `app/build.gradle.kts`
- **WHEN** 与阶段 6a 对比
- **THEN** 新增依赖数为 0

**理由**：「不信任第三方」这条铁律要在每个阶段重新确认；
小组件与通知栏提醒正因为这条约束被移入 6c。
