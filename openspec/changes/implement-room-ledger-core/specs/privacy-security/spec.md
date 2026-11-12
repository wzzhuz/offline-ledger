# Delta for 隐私与安全基线

## ADDED Requirements

### Requirement: 数据库以 SQLCipher 加密打开

系统 SHALL 通过 `SupportOpenHelperFactory` 打开 Room 数据库，
口令 SHALL 由 `DatabaseKeyManager` 生成，
且 SHALL 在打开任何数据库之前调用 `System.loadLibrary("sqlcipher")`。

#### Scenario: 直接读取数据库文件

- **GIVEN** 有人取得应用数据目录下的 `offline_ledger.db`
- **WHEN** 用普通 SQLite 工具打开
- **THEN** 无法读到明文账目

**理由**：零网络权限之外，设备丢失与文件残留是纯本地方案仅剩的风险面，
加密是必要兜底；第一条铁律不能只停在 Manifest 上。

---

### Requirement: 数据库口令由 Android Keystore 保护

系统 SHALL 用 Android Keystore 的 AES-256/GCM 密钥加密数据库口令，
密文与 IV MAY 存于 `SharedPreferences`，
密钥材料 MUST NOT 离开 Keystore，口令 MUST NOT 以明文持久化。

#### Scenario: 应用重启后仍可打开同一数据库

- **GIVEN** 应用进程被杀死后重新启动
- **WHEN** `DatabaseKeyManager.getOrCreatePassphrase` 执行
- **THEN** 解密得到与首次完全相同的 32 字节口令
- **AND** 无需用户重新输入

**理由**：口令一旦每次随机生成，重启后数据库就打不开，等于数据全丢。
