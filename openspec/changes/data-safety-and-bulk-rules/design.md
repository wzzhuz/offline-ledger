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

## 第 31 批阶段二实现（真实落盘）

### 密钥与快照链路（不再用 KEK 封装）
- 阶段一已核查：`SecureBackupCodec` 用使用者自设备份口令 PBKDF2 派生，与数据库口令无关。
- 阶段二 `SnapshotPrefs` 用 AndroidKeystore 的独立别名 `offline_ledger_snapshot_pass`
  加密保存自动快照口令，供每日定时任务使用；**恢复不依赖 Keystore**，重装后
  Keystore 条目丢失时页面要求使用者手动输入同一口令。
- 主库仍在内部私有目录；快照只写到 SAF 授权目录（可指向 NAS 同步本地目录）。
- `SnapshotWriter`：`.tmp` → 写入 → 回读 SHA-256 校验 → `renameDocument` 成
  `.ledgerbak` → `pruneCandidates` 清理临时文件并保留最近 7 份。失败删临时文件。
- Uri 权限随卸载丢失、文件仍在：重装后 MUST 重新授权同一目录，恢复页列出成品。
- `SnapshotScheduler`：`AlarmManager.setInexactRepeating` + `SnapshotReceiver`
  （`goAsync` + IO 协程），失败只写日志不影响账本；导入/批量规整完成后也可手动一键。

### 批量规整落库
- `LedgerRepository.applyBulkTargets` 是新增的唯一写方法：`db.withTransaction` 内
  按 id 取原实体、只 copy 四个可变字段、经 `withResolvedRoot` 同步冗余一级分类，
  末尾 `rebuildAllBalancesInternal()` 一次。金额、账户、时间、币种一律不动。
- `BulkEditViewModel` 把 `BulkEditEngine.apply` 的 `changeset` 留在本次页面会话，
  撤销时 `revert` 取 before 再走同一写方法，逐字段还原。
- 静态检查 `check_transaction_annotations.py` 的写方法清单同步登记 `applyBulkTargets`。

### 三级回退（阶段二）
1. 若 `renameDocument` 在个别文件提供器上失败 → 退到「写唯一成品名 + 写完校验 + 不改名」，
   临时文件仍优先清理。
2. 若定时闹钟与应用生命周期冲突 → 退到「导入/批量规整完成后触发」+ 手动一键。
3. 若一次批量事务过大 → 按 500 条分批，但保持同一 `changeset` 与一次性撤销。
