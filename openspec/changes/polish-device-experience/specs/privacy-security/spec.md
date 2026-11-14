## ADDED Requirements

### Requirement: 收款方与默认币种不引入任何网络行为

新增 `counterparty` 自由文本与默认币种偏好 SHALL 全部留在本地，
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


### Requirement: 交易对方字段不扩大数据外泄面
系统 SHALL 把交易对方作为本地流水字段保存，MUST NOT 因联想功能引入网络请求、
MUST NOT 声明 `INTERNET`；联想 SHALL 由本地 `DISTINCT ... LIKE` 查询实现。

**理由**：交易对方常含商户名，属敏感文本；隐私边界仍是「不上传第三方」。

#### Scenario: 联想离线
- **GIVEN** 用户输入「盒」
- **WHEN** 联想列表出现「盒马」
- **THEN** 结果来自本地 Room 查询，应用未发起任何网络请求
