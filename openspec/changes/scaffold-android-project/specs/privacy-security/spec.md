# Delta for 隐私与安全基线

## ADDED Requirements

### Requirement: Manifest 隐私基线可逐条核查

`AndroidManifest.xml` SHALL 满足：不声明 `android.permission.INTERNET`、
`android:allowBackup="false"`、通过 `android:fullBackupContent` 与
`android:dataExtractionRules` 引用排除数据库的规则文件。
系统 MUST NOT 声明 `READ/WRITE_EXTERNAL_STORAGE`。

#### Scenario: 安装前查看权限

- **GIVEN** 用户在系统设置中查看应用权限
- **WHEN** 打开该应用详情
- **THEN** 权限列表为空或仅含无害权限
- **AND** 不含任何网络与存储权限

#### Scenario: 系统尝试自动备份

- **GIVEN** 用户换机或系统触发 Auto Backup
- **WHEN** 系统读取应用数据
- **THEN** 账本数据库不被上传
- **AND** 迁移数据须通过用户主动导出

**理由**：系统 Auto Backup 默认把应用数据备份到云端，
等于把最敏感的账本交给第三方，与「不信任第三方」直接冲突且极易被忽略。

---

### Requirement: 隐私基线由自动化脚本检查

系统 SHALL 提供 `scripts/check_manifest_privacy.py`，
对 Manifest、两个备份规则 XML 与依赖声明做静态检查，
并 SHALL 在 CI 编译前执行，失败即中断。

#### Scenario: 备份规则漏写 database 排除

- **GIVEN** 有人删掉 `data_extraction_rules.xml` 里的 `<exclude domain="database" ...>`
- **WHEN** CI 执行检查脚本
- **THEN** 脚本报错、job 失败
- **AND** 不会产出一个可被系统备份数据库的版本

**理由**：文字约定会被忘记，可执行检查不会。
且备份规则只在 release 的 `lintVital` 才校验，静态检查能在更早一步拦住。
