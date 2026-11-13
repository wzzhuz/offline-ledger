# Delta for 隐私与安全基线

## ADDED Requirements

### Requirement: CI 网络请求与 App 权限边界明确

出包流水线在 GitHub runner 上访问坚果云 WebDAV 的行为 SHALL 仅用于
上传**安装包（APK）**，MUST NOT 传输任何账本数据、备份文件或用户口令。
系统 SHALL 继续满足「App 不声明 `android.permission.INTERNET`」，
`check_manifest_privacy.py` MUST 在出包阶段之后仍 PASS。
坚果云账号与应用密码、正式签名密钥 SHALL 取自 GitHub Secrets，
MUST NOT 写进仓库、日志或 Job Summary。

#### Scenario: 出包后检查 App 权限

- **GIVEN** release 工作流已成功上传 APK 到坚果云
- **WHEN** 检查 `AndroidManifest.xml` 与 CI 静态检查结果
- **THEN** 仍无 `INTERNET`、无存储权限
- **AND** `USE_BIOMETRIC` 仍是白名单里唯一的权限

#### Scenario: 日志里找凭据

- **GIVEN** 一次完整的 release 构建日志与 Job Summary
- **WHEN** 搜索 `JGY_USER` / `JGY_APP_PASSWORD` / keystore 口令
- **THEN** 只能看到 Secret 名与补齐路径，看不到取值
- **AND** `check_workflow_syntax.py` 未发现硬编码凭据字面量

**理由**：「不信任第三方」指账本数据不托管；runner 上传的是编译产物。
把两者混为一谈会做出错误设计，但反过来也不能因为「CI 能联网」
就放松 App 的零网络权限铁律——明确写死边界才不会在下个阶段被误放宽。
