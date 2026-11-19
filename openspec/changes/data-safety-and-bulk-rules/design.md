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

## 第 31 批阶段二实测回填（真实编号）

| 项 | 实测 |
|---|---|
| 代码 commit | `84846cdf3f9a315c16da7b82da5ed9a100f250c7`（6 新增 / 9 修改，v0.2.15） |
| push CI | `44332258434` **success**，17 步全绿，首轮 Kotlin 编译零错误；`BulkEditRevertTest` 由单测步骤跑过 |
| release | `44333917298` **success**，23 步绿 / 5 步按 input 跳过；**「上传到坚果云」completed success** |
| 制品（Actions artifact zip 压缩后尺寸） | `offline-ledger-release-v0.2.15` 12342643 B；`offline-ledger-mapping-v0.2.15` 3714261 B；`offline-ledger-metrics-v0.2.15-p-m-nsfalse` 270 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，不当作已验证 HEAD 200 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；写方法清单 41 个均在事务内 |

**产物口径**：约 24M 是 R8 release APK 原始字节，Actions 制品页约 12M 是 zip
二次压缩尺寸，两者同一个文件；不是 debug 变体。

**阶段二真实落盘**：`SnapshotPrefs` / `SnapshotWriter` / `SnapshotScheduler` /
`SnapshotSettingsScreen` / `BulkEditScreen` / `BulkEditRevertTest`；
数据与备份页新增「自动快照与恢复」「批量规整」两个入口；
`LedgerRepository.applyBulkTargets` 登记进写方法清单。
`AppDatabase.version = 3` 未变 → v0.2.14 上覆盖安装即可，不用卸载。

## 第 34 批：恢复 1811 与两套 CSV 契约（真实源码前置核查）

### 根因（读码后修正门禁里的推测）

1. **1811 主因**：`categories` 自引用且 `onDelete = ForeignKey.RESTRICT`。
   原 `importBackup` 用 `DELETE FROM categories` 逐行清表，行顺序不保证，
   先删父行会让子行 `parentId` 悬空；插入时 `file.payload.categories`
   来自导出查询结果，也不保证父级排在子级之前。Room 默认外键是 immediate，
   事务末尾补齐也来不及。事务内写 `PRAGMA foreign_keys = OFF` 是 no-op。
2. **全量替换漏表**：`counterparty_aliases`（第 30 批新增）既不 clear 也不
   在 `BackupPayload` 里，恢复后残留旧规则；`enabled` / `cardLast4` 同样
   不在 DTO 里，恢复后回默认值。
3. **CSV 失败真因与门禁推测不同**：`CsvCodec.export` 与 `parseImport` 用的是
   **同一套 11 列表头**，表头并不冲突。失败发生在行级校验：
   导出是全量流水（含 `TRANSFER`、无账户、无一级分类），而账单解析器
   只接受 `EXPENSE` / `INCOME` 且要求账户名与一级分类非空，
   任一行不合法就整份拒绝。

### 修法

- `importBackup`：`db.openHelper.writableDatabase` 在事务内执行
  `PRAGMA defer_foreign_keys = ON`（提交时统一检查），先
  `UPDATE categories SET parentId = NULL` 断开自引用，再按
  records/trash/adjustments/templates/rules/budgets/aliases → categories →
  accounts 清空；插入 accounts → categories（父先子后）→ 其余；
  `PRAGMA foreign_key_check` 有行即抛异常，整个事务回滚；末尾重算余额。
- DTO：`BackupAccount.cardLast4` / `BackupCategory.enabled` /
  `BackupAlias` + `BackupPayload.counterpartyAliases`，全部带默认值，
  `ignoreUnknownKeys` 下旧备份缺键自动补空，不破坏 V1/V2/V3 兼容。
- 口令：`SnapshotPrefs` 已是 Keystore AES-256/GCM 加密存本机、卸载失效；
  手动备份对话框复用它做「预填 + 记住」。文件加密本体与恢复解密路径不改。
- CSV：`CsvCodec.detectFormat` 按首行必填列判定 OWN / OTHER（不靠文件名）；
  `parseRebuild` 只对 OWN 生效，跳过行写 `notices` 而非 `errors`；
  `SettingsViewModel.readCsvPlan(..., rebuild)` 一个入口按标志分流，
  不复制第二套确认流程。

### 三级回退（第 34 批）

1. 若 `defer_foreign_keys` 在 SQLCipher 版本上提交时不生效 →
   退到纯拓扑排序 + 事务外短暂关闭 FK，仍保留 `foreign_key_check`。
2. 若 `UPDATE categories SET parentId = NULL` 触发布尔态问题 →
   退到「先按 level 降序逐行删子级，再删父级」的显式删除序。
3. 若 `parseRebuild` 的跳过行太多导致重建不完整 → 结果摘要直接建议
   改用加密备份恢复，不自动补齐转账。

### 硬约束

MUST NOT 删 FK 约束或用事务内 `foreign_keys=OFF` 绕错；
MUST NOT 留半截库；MUST NOT 把明文做成默认；
MUST NOT 让「导入账单」静默吞自有格式；
自有 CSV 重建 MUST 带来源字段；表结构不变 → 覆盖安装即可。

## 第 34 批实测回填（真实编号，由推送脚本补入）

见 `tasks.md` 阶段三与指南 §8 补记。
