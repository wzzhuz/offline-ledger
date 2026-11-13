## ADDED Requirements

### Requirement: 收款方与默认币种不引入任何网络行为

新增 `payee` 自由文本与默认币种偏好 SHALL 全部留在本地，
系统 MUST NOT 声明 `INTERNET`、MUST NOT 引入网络依赖、
MUST NOT 把收款方或账户信息发往任何第三方。
收款方常用值 SHALL 由本地 SQL `DISTINCT` 得出，MUST NOT 走在线词库。

#### Scenario: 权限复核

- **GIVEN** 本批次完成
- **WHEN** 检查 Manifest 与依赖
- **THEN** 无 `INTERNET`、无新增权限、无新增依赖
- **AND** `check_manifest_privacy.py` PASS

**理由**：使用者澄清隐私边界是「数据不能存在厂商服务器」；
本地明文文件由自己掌控可以接受，但网络行为仍是不可越过的红线。
