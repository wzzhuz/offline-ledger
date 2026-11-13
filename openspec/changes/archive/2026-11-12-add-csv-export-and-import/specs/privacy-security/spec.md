# Delta for 隐私与安全基线

## ADDED Requirements

### Requirement: 明文导出只走用户自选位置且不外发

CSV 明文导出 SHALL 通过 SAF 写入用户自选位置，
系统 MUST NOT 声明 `android.permission.INTERNET`、
MUST NOT 申请任何存储权限、MUST NOT 向任何网络地址上传数据、
MUST NOT 在应用私有目录留下明文账本副本。
系统 MUST NOT 以「明文文件有风险」为由提供默认关闭、
强制二次确认或「勿放公共目录」提示等拦截；
导出入口 MAY 提供一次避免误触覆盖的常规确认。
`check_manifest_privacy.py` SHALL 继续 PASS。

#### Scenario: 导出 CSV 后检查应用行为

- **GIVEN** 用户导出一份 CSV 到自选目录
- **WHEN** 检查 Manifest 与网络行为
- **THEN** 无 `INTERNET` 权限、无网络上传、私有目录无副本
- **AND** 设置页说明「明文文件由你自行保管；应用不会上传任何数据」

#### Scenario: 有人再次以明文风险拦截 CSV

- **GIVEN** 有人提议禁用 CSV 或加「默认关闭 + 强制二次确认」
- **WHEN** 评审
- **THEN** 评审改按「是否上传第三方」判断
- **AND** 不把文件是否加密当作隐私合规条件

**理由**：6c-1 使用者已正式推翻「明文=风险」的前提；
本批把落地行为写死，避免错误论据以 UI 文案形式悄悄回来。
