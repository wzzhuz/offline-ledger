# 设计（第 31 批·阶段一）

## 真实核查（本批先补的前置，不是推演）
- `data/local/DatabaseKeyManager.kt`：`KEY_ALIAS = "offline_ledger_db_key"`，32 字节随机口令由 AndroidKeystore 的 AES-256/GCM 密钥加密，密文 + IV 存 `SharedPreferences("offline_ledger_key_meta")`。卸载或清除数据时 Keystore 条目与 SharedPreferences 一起删除，**裸 `.db` 文件在重装后无法解密**，故不得作为重建依据。
- `backup/crypto/SecureBackupCodec.kt`：文件布局 `OLBK` magic + 16B salt + 12B IV + AES-256/GCM(Gzip(JSON))，口令由使用者自设、PBKDF2WithHmacSHA256 120000 次派生，**与数据库口令完全无关**。
- `backup/dto/BackupEnvelope.kt`：已有 `schemaVersion`（`BackupSchema.CURRENT = 4`）与 `MIN_SUPPORTED = 1`，旧版本靠缺省值兼容。

## 方案修正
门禁里写的「首次授权 SAF 目录设备份口令、用 KEK 封装 DB 密钥」是多余的：现有 `SecureBackupCodec` 本就是自包含加密，恢复时输入使用者口令即可解密，无需把数据库口令带出设备。本批**撤销该设计**，快照 = `SecureBackupCodec.encode(BackupExporter.encode(snapshot), passphrase)` + 外层校验和与文件名元数据。

## 分层
- 主库：仍留内部私有目录，MUST NOT 外部化直接打开（避免 NAS 同步客户端半途改动导致 SQLite 损坏）。
- 快照：SAF 授权目录（Downloads/Documents 或 NAS 同步客户端本地目录），只写成品 `.ledgerbak`；临时文件 `.tmp` 优先清理；原子改名；保留最近 7 份。
- 恢复：重装 → 重新授权目录（Uri 权限随卸载丢、文件仍在）→ 选快照 → 校验 → 单事务重建 → 末尾重算。
- `hasFragileUserData="true"`：仅让卸载多一道系统确认，对「清除数据」无效，不能当唯一保障。

## 批量规整
- 一期只做即时调整，不持久化规则定义、不做导入后自动触发、不做规则组顺序（Firefly III 的完整规则引擎留二期）。
- 条件全部满足；动作逐条应用；`changeset` 保存变更前完整字段，一键撤销逐字段还原。
- 落库 MUST 单事务 + 末尾重算；MUST 先预览命中数与前后对比，禁止静默改写历史。

## 三级回退
1. 若 SAF 原子改名在部分文件提供器上失败 → 退到「写唯一文件名 + 写完校验 + 不改名」。
2. 若定时调度与应用生命周期冲突 → 退到「导入/批量规整完成后触发」+ 手动一键。
3. 若批量规整落库事务过大 → 按 500 条分批，但保持同一 `changeset` 与一次性撤销。
