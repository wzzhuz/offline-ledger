## ADDED Requirements

### Requirement: 真机反馈批次的静态检查与单测

本批次 SHALL 保持六个静态检查全 PASS，且 SHALL 新增纯 JVM 单测覆盖
默认币种 / 时区、`payee` 在备份与 CSV 的往返、以及 8 列旧 CSV 兼容。
CI 编译通过 MUST NOT 被表述为「可覆盖升级」或「真机已验证」。

#### Scenario: 检查全绿

- **GIVEN** 推送本批次代码
- **WHEN** Actions 跑完
- **THEN** 六个静态检查、单测、debug / release 构建全绿
- **AND** 能在 Actions 列表查到该次运行

#### Scenario: 迁移门禁不误伤

- **GIVEN** `AppDatabase version = 1` 且 `Migrations.ALL` 为空
- **WHEN** 跑 `check_transaction_annotations.py`
- **THEN** 只产生记录性 warnings，job 仍 success
- **AND** 写方法漏事务仍使 job 失败

**理由**：稳定版前迁移检查已降级为 warnings（6c-2），本批次要确认
重置版本不会意外让 CI 变红。
