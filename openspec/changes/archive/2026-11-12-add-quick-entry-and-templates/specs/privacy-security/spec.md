# Delta for 隐私与安全基线

## ADDED Requirements

### Requirement: 快捷方式与周期补记不新增权限或网络依赖

阶段 6a 的静态快捷方式、冷启动补记、模板与规则落库
SHALL NOT 新增任何 `uses-permission`，SHALL NOT 引入
含网络行为的依赖，SHALL NOT 改变 `allowBackup=false` 与数据库备份排除规则。
`check_manifest_privacy.py` MUST 在阶段 6a 之后仍 PASS。

#### Scenario: 检查 Manifest

- **GIVEN** 阶段 6a 已落地
- **WHEN** 运行 `check_manifest_privacy.py`
- **THEN** 仍只有 `USE_BIOMETRIC` 一个 normal 级权限
- **AND** 无 `INTERNET`、无存储权限、无 `SCHEDULE_EXACT_ALARM`

#### Scenario: 依赖清单

- **GIVEN** `app/build.gradle.kts`
- **WHEN** 与阶段 5 对比
- **THEN** 新增依赖数为 0
- **AND** 未出现 WorkManager / AlarmManager 相关库

**理由**：低摩擦功能最容易顺手引入后台权限与上报 SDK；
「不信任第三方」这条铁律要在每个阶段重新确认一次。
