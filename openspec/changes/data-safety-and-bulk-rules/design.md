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

## 第 34 批实测回填（真实远端编号，2026-11-19）

| 项 | 实测 |
|---|---|
| 代码 commit | `4a86031926828f5a89e2f1e1ffed2cf4aa6783ad`（2 新增 / 15 修改，v0.2.16） |
| 首轮 push CI | `44440446947` **failure**，挂在「运行单元测试」；诊断回传日志定位 `BackupPayload.kt:54/58 Redeclaration`（脚本中断后重跑，前缀锚点幂等判断漏判，重复插入 `BackupAlias`） |
| 诊断通道 | 临时 dispatch-only `diag.yml` → run `44441001168`（artifact 下载被出网策略挡）、`44441535608`（改用 contents API 回传日志成功）；用完即删 |
| 修复 commit | `5cde279ee70d0ba63bd020d345839c5ef424da06`（只改 `BackupPayload.kt`） |
| 修复后 push CI | `44441996336` **success**（17 步，`CsvRebuildTest` 由单测步骤跑过） |
| release | `44443061506` **success**，23 步绿 / 5 步按 input 跳过，**「上传到坚果云」completed success** |
| 制品（Actions artifact zip 压缩后尺寸） | `offline-ledger-release-v0.2.16` 12350729 B；`offline-ledger-mapping-v0.2.16` 3718037 B；`offline-ledger-metrics-v0.2.16-p-m-nsfalse` 269 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，目录内容以使用者所见为准，不当作已验证 HEAD 200 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；写方法清单 41 个均在事务内 |

**产物口径**：约 24M 是 R8 release APK 原始字节，Actions 制品页约 12M 是 zip 二次压缩尺寸，两者同一个文件；不是 debug 变体。

**真实落盘**：`importBackup` 的 defer_foreign_keys / 断开自引用 / 父先子后 / foreign_key_check、备份 DTO 三处补齐、口令本机记住、`CsvCodec.detectFormat` + `parseRebuild`、设置页「从 CSV 重建」入口、`CsvRebuildTest`、两份 MODIFIED delta 与指南 §7.17。
`AppDatabase.version = 3` 未变 → **v0.2.15 上覆盖安装即可，不用卸载**。
`data-safety-and-bulk-rules` 与 `preset-data-and-counterparty` 仍进行中，未擅自归档。

## 第 35 批：「从 CSV 重建」报「账户不存在或已归档」（v0.2.17）

### 根因（真读源码后确认，不是门禁推测）

`SettingsPages.kt` 的「从 CSV 重建」按钮 → `SettingsViewModel.confirmCsvImport()`
无论 `plan.rebuild` 与否都调 `repository.importRecords(rows)`。
而 `LedgerRepository.importRecords` 是**第三方账单**语义：
`accountDao.all().filter { !it.isArchived }`，第一行匹配不上就抛
`IllegalArgumentException("账户不存在或已归档：" + row.accountName)`；
分类同样要求一级 / 二级存在且未归档。自有 11 列 CSV 不带账户表与分类树，
v0.2.14 又删了预制账户、换了分类清单 → 清库 / 重装后**必然**失败。

更该记的是两处相反文案（均出自第 34 批）：`SettingsPages` 帮助文本写
「清库后请用『从 CSV 重建』」，`CsvCodec.parseRebuild` 注释却写
「完整重建请用『从加密备份恢复』」。代码注释对了、UI 把人导向必失败的入口。

### 修法

1. **拆专用写路径** `rebuildFromOwnCsv`，不复用 `importRecords`：
   `db.withTransaction` → 归档账户 / 停用分类一并纳入已知集合 →
   `CsvRebuildResolver` 一次性收集缺失项 → 仅对 ready 行组装
   `RecordEntity`（`withResolvedRoot`、来源字段原样回写、复合唯一键去重）→
   末尾 `rebuildAllBalancesInternal()`；任一步抛错由 `withTransaction` 整库回滚。
2. **缺项不是异常**：跳过受影响行，`CsvRebuildOutcome` 带回
   `missingAccounts` / `missingCategories` / `skippedMissing`，
   摘要逐项列出，并说明 v0.2.14 换过清单导致按名失配不是数据损坏。
3. **纯 JVM 判定**：`CsvRebuildResolver.resolve(rows, accountNames, categoryPaths)`
   让「归档不拒 / 空库全缺 / 换名清单 / 同名二级分父级」四条可单测，
   不依赖 Room；`categoryPaths` 元素形如 `居住` 或 `居住/水费`。
4. **文案掰正**：导出按钮改「Excel 核对用」，重建按钮标明「需已有账户与分类」，
   帮助文本删掉「清库后用 CSV 重建」，嗅探引导补「清库 / 换机走加密备份或快照」，
   弹窗标题按 `plan.rebuild` 区分并加缺项说明。
5. **写方法清单**登记 `rebuildFromOwnCsv`（第 42 个），保持六个静态检查全绿。

### 回退顺序

1. 若 `CsvRebuildResolver` 判 `一级/二级` 与 `CategoryDisplay.resolve` 口径冲突 →
   退到只按一级名判定、二级缺失时落到一级并计入 notices，不新增 SQL。
2. 若缺项行太多导致重建结果不可用 → 摘要直接建议改用加密备份 / 快照恢复，
   **不自动新建账户或分类**（会污染现有体系，也不该由 CSV 决定）。
3. 若 `rebuildFromOwnCsv` 与 `importRecords` 出现重复逻辑 → 只抽「组装
   `RecordEntity`」的私有函数，两条路径的校验语义 MUST 保持不同。

### 硬约束

`importRecords` 第三方账单严格语义 MUST NOT 放宽；归档 / 停用 MUST NOT
当成缺失；缺项 MUST NOT 静默丢弃或整批抛异常；CSV MUST NOT 被扩成
自包含格式；表结构不变 → 覆盖安装即可；MUST NOT 改 `release.yml`。

## 第 35 批实测回填（真实远端编号，2026-11-19）

| 项 | 实测 |
|---|---|
| 代码 commit | `e635c9a436ecd9a3ee33f90eedc00be7f1daccb6`（2 新增 / 10 修改，v0.2.17） |
| push CI | `44447674151` **success**，17 步全绿，新增 `CsvRebuildResolverTest` 由单测步骤跑过 |
| release | `44448470911` **success**，23 步绿 / 5 步按 input 跳过，**「上传到坚果云」completed success** |
| 制品（Actions artifact zip 压缩后尺寸） | `offline-ledger-release-v0.2.17` 12353413 B；`offline-ledger-mapping-v0.2.17` 3723958 B；`offline-ledger-metrics-v0.2.17-p-m-nsfalse` 269 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，目录内容以使用者所见为准 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；写方法清单 42 个均在事务内 |

**产物口径**：约 24M 是 R8 release APK 原始字节，Actions 制品页约 12M 是 zip 二次压缩尺寸，两者同一个文件；不是 debug 变体。
`AppDatabase.version = 3` 未变 → **v0.2.16 上覆盖安装即可，不用卸载**。
`data-safety-and-bulk-rules` 与 `preset-data-and-counterparty` 仍进行中，未擅自归档。


## 第 36 批（v0.2.18）：规则 2.0 与分类迁移映射表

**根因**：一期「即时批量规整」只有 AND 条件、单一固定目标值、规则用完即弃，
无法表达「旧分类 A/B/C 或对方关键词散乱记录一次圈出」与「20 组旧分类一次映射到新分类」。
Firefly III 的规则是「条件组 + 动作组 + 规则组有序 + strict/非 strict + 对历史回溯应用」，
本批借其模型，不引触发器自动跑 / 表达式引擎 / 标签体系 / stop processing。

**实现取舍（与门禁描述的一处偏差，如实登记）**：门禁写「规则四表」，
实作收敛为 `rule_groups` + `rule_definitions` **两张表**，条件与动作序列化为 JSON 列、零外键。
理由：四种操作符各建表会把恢复链路的 FK 拓扑序再拉长一层，v0.2.16 刚修完 1811，
不为结构纯度再冒一次风险；能力范围（可保存、有序、AND/OR、取反、多动作、回溯）不缩水。

| 项 | 落地 |
|---|---|
| 纯 JVM 内核 | `RuleModels.kt` / `RuleEngine.kt`：ALL/ANY、negate、七种操作符（含正则、为空、不等于）、多动作、组内 sortOrder 有序、`changeset` 逐字段撤销 |
| 分类迁移 | `CategoryMigration` + `migrationReport`（每组命中数 / 冲突组 / 未命中）+ 首次命中优先，与规则组共用同一 `applyBulkTargets` 写路径 |
| 持久化 | `AppDatabase.version = 4`，两张表零外键；恢复按 groups → definitions 顺序插入，纳入 `defer_foreign_keys` / `foreign_key_check` 同一事务 |
| 备份 | `BackupPayload.ruleGroups` 带默认值，旧备份缺键解析为空列表；导出与自动快照自动带上规则 |
| UI | 设置页按钮文案精简为「从 CSV 重建」并给同排按钮 `maxLines = 1`；「批量规整」页新增「规则与分类迁移」弹层，预览 → 单事务 → 末尾重算 → 一键撤销 |
| 不做 | 导入后自动触发（触发器默认关）、规则组嵌套、表达式引擎、标签/预算联动、stop processing |

**测试**：`RuleEngineTest` 6 条（ANY/ALL/取反/组顺序/映射命中与冲突/撤销还原）、
`RuleBackupRoundTripTest` 2 条（规则随备份往返、旧备份缺键仍可解析）。
门禁里写的「恢复 androidTest」未做：CI 无模拟器不跑 androidTest，
改为纯 JVM 备份往返单测覆盖 DTO 层，恢复链路仍待真机「导出→清数据→重装恢复」复核。

**`version = 4` → 必须卸载重装、装之前先导出**；v0.2.17 覆盖安装会开库失败。

## 第 36 批交付状态（真实远端编号，2026-11-19）

- [x] push CI 与 release 实测回填、坚果云上传
- [ ] 真机复核（使用者，刻意保留）

## 第 36 批实测回填（真实远端编号，2026-11-19）

| 项 | 实测 |
|---|---|
| 代码 commit | `3d8f3b8682eb4d3cf66cc497f13a5c3b7911b3c7`（8 新增 / 13 修改 / 0 删除，v0.2.18） |
| push CI | `44455688567` **success**，17 步 success，`RuleEngineTest` / `RuleBackupRoundTripTest` 由单测步骤跑过 |
| release | `44470577388` **success**，23 步 success / 5 步按 input 跳过，**「上传到坚果云」completed success** |
| 制品（Actions artifact zip 压缩后尺寸） | `offline-ledger-release-v0.2.18` 12386391 B；`offline-ledger-mapping-v0.2.18` 3762956 B；`offline-ledger-metrics-v0.2.18-p-m-nsfalse` 270 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，目录内容以使用者所见为准 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；写方法清单沿用 `applyBulkTargets` 等已登记方法 |

**真实落盘**：规则 2.0（`RuleModels` / `RuleEngine` / `RuleManagerDialog`）、
`rule_groups` + `rule_definitions` 两张零外键表、`BackupPayload.ruleGroups` 往返、
分类迁移映射表的命中 / 冲突 / 未命中报告、设置页「从 CSV 重建」文案精简与同排 `maxLines = 1`。

**如实边界**：门禁写「规则四表」，实作收敛为两张表；门禁写「恢复 androidTest」，
因 CI 无模拟器不跑 androidTest，改为纯 JVM `RuleBackupRoundTripTest` 覆盖 DTO 层，
恢复链路仍待真机「导出 → 清数据 → 卸载重装 → 恢复」复核。
`AppDatabase.version = 4`、无迁移层 → **必须卸载重装、装之前先导出**，
v0.2.17 覆盖安装会开库失败。约 24M 是 R8 release APK 原始字节，
Actions 制品页约 12M 是 zip 二次压缩尺寸，两者同一个文件；不是 debug 变体。
`data-safety-and-bulk-rules` 与 `preset-data-and-counterparty` 仍进行中，未擅自归档。


## 第 37 批：规则编辑器三段式（v0.2.19）

**根因（先写再改，本节与实现同 commit）**：引擎早就通用——`RuleField` / `RuleOp` /
`RuleActionSpec` 支持「字段 + 操作符 + 值」与多动作，`RuleEngine` 支持 ALL/ANY、取反、
组顺序、`changeset` 撤销。v0.2.18 的 `RuleManagerDialog` 却只暴露四个写死槽位
（对方包含 / 备注包含 / 对方改为 / 备注替换），使用者不能自选「这次按分类圈、按金额圈、
还是按 importSource 圈」。上一批把 Firefly III 的**能力清单**误读成**表单项清单**，
还用独立「分类迁移映射表」打补丁，舍本逐末。分类调整本就是一条普通规则，
不该是第二个功能。

**三张截图对齐**：FF3 条件下拉把「字段 + 操作符」合成一个超长单选（文字截断、
含 SEPA / 对账 / 日志 ID / 创建更新时间等本项目没有的字段），规则编辑页是四列硬表格、
窄屏挤压，动作值全靠手打。本项目改为两级短选择 + 卡片式动态行 + 值控件分派；
FF3 `description` = 本项目交易对方、`notes` = 备注，故不加独立 description 列；
「设置目标账户」映射成「交易对方改为」（不建对方账户、不引复式）。

**改什么**：`RuleEditorCatalog`（目录单一来源）、`RuleModels` 枚举末尾追加值 /
`BulkTarget` 三个只读条件字段、`RuleEngine` 支持账户 / 币种 / 来源 / 布尔字段与
NOT_EQUALS / APPEND_NOTE、`RecordDao.searchNotes` 限量联想、
`RuleManagerScreen` 重写为独立全屏动态编辑器、`BulkEditScreen` 入口早返回、
`specs/bulk-edit/spec.md` 整块替换、版本 0.2.19。

**不改**：`rule_groups` + `rule_definitions` 两张表、`AppDatabase.version = 4`、
`BackupPayload` / `BackupExporter` / 恢复拓扑序、`applyBulkTargets` 写入字段、
`SecureBackupCodec`、统计口径唯一出口、两页下拉刷新、无障碍刷新按钮、`release.yml`。

**执行第一步（已做，非推断）**：真读 `RuleModels` / `RuleManagerScreen` /
`CategoryDao` / `RecordDao` / `LedgerRepository`，确认「通用模型是否完整暴露」
（是，但枚举不全且 UI 写死）、「分类选择器是否过滤停用项」（旧 UI 用 `categoryDao.all()`
按名匹配，根本没弹窗；新弹窗用 `all()` 含停用与归档）、「是否真的零改表」（是，
只扩纯 JVM 模型与 UI，Room schema 未动）。

**回退顺序**：
1. 若动态行编译不过 → 保留 `RuleManagerDialog` 全屏封装，退回 `Dialog` 内滚动布局
2. 若 ID 型条件不匹配 → `RuleEngine.rawMatch` 已改为按 id 判定，不再拿显示名比字符串
3. 若联想误伤 → `suggest` 降到只按对方，备注联想关掉
4. 若「保存并立即应用」时序不稳 → 退成「保存 + 预览 + 手动确认写入」两步
5. 都不行 → 恢复 v0.2.18 的 `RuleManagerDialog` 文件（已进 git，可整文件回滚），
   不动规则表与备份 DTO

**一处安全加固**：空关键词 + 空 ID + 空区间的条件现在直接不命中，
`saveDraft` 另校验「至少一条有效条件」与「SET_CATEGORY 必须选了分类」，
避免新规则默认空行把全库分类清空。

**两处如实边界**：① 独立全屏页用 `RuleManagerScreen` + `BulkEditScreen` 早返回实现，
没有新增 NavHost 路由（避免动 4 Tab 结构）；`RuleManagerDialog` 保留为全屏 Dialog
兼容封装。② 编辑态 `draft` 存在 ViewModel 而非 `rememberSaveable`——
`RuleDefinition` 非 Parcelable，ViewModel 跨旋转更稳。

**零改表 → v0.2.18 上覆盖安装即可，不用卸载。**

## 第 37 批交付状态（真实远端编号，2026-11-19）

- [x] push CI 与 release 实测回填、坚果云上传
- [ ] 真机复核（使用者，刻意保留）

## 第 37 批实测回填（真实远端编号，2026-11-19）

| 项 | 实测 |
|---|---|
| 代码 commit | `997e4d195ee718993350a47142026aeb48166477`（主代码，3 新增 / 11 修改 / 0 删除）→ `8b983c2872dd`、`07787056d153`（两次单测断言修正），最终 HEAD `07787056d15329ce704e8a25b1ca1e8cf8397826`（v0.2.19） |
| push CI | 首轮 `44478463075` failure、并发 `44479256085` cancelled，修复后 `44479652044` **success**，17 步 success，静态检查与单测步骤均 success |
| release | `44480858062` **success**，23 步 success / 5 步按 input 跳过，**「上传到坚果云」completed success** |
| 制品（Actions artifact zip 压缩后尺寸） | `offline-ledger-release-v0.2.19` 12399036 B；`offline-ledger-mapping-v0.2.19` 3789871 B；`offline-ledger-metrics-v0.2.19-p-m-nsfalse` 270 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，目录内容以使用者所见为准 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；未新增写方法，`applyBulkTargets` 沿用 |

**真实落盘**：`RuleEditorCatalog`（字段 → 操作符 / 值控件单一来源）、
`RuleManagerScreen` 独立全屏动态行编辑器（标题 / ALL-ANY / 取反 / 多动作 / 启停）、
`RecordDao.searchNotes` 限量联想 + 200ms 防抖、分类与账户弹窗含停用归档并显示「一级 / 二级」、
删独立映射表 UI 改「批量录入」生成 N 条普通规则且给出命中 / 冲突 / 未命中报告、
「保存并立即应用到历史」复用预览 → 单事务 → 末尾重算 → 撤销。

**如实边界**：规则组嵌套、表达式引擎、标签 / 预算联动、stop processing、导入后自动触发
均不做（触发器默认关）；「描述」映射到已有「交易对方」、FF3 `notes` 映射到「备注」，
**零改表** → `AppDatabase.version = 4` 未变，**v0.2.18 上覆盖安装即可，不用卸载**。
约 24M 是 R8 release APK 原始字节，Actions 制品页约 12M 是 zip 二次压缩尺寸，
两者同一个文件；不是 debug 变体。`data-safety-and-bulk-rules` 与
`preset-data-and-counterparty` 仍进行中，未擅自归档。


## 第 38 批（v0.2.20）根因与回退顺序

**现象**：保存的规则列表与规则编辑器堆在同一滚动列；保存后没有再次执行入口；
「预览」既像看详情又像查命中，语义混淆；规则入口埋在「数据与备份」里。

**根因**：v0.2.19 只把 UI 从固定槽位换成动态行，没有把「列表 / 编辑 / 应用」三种
职责分开；`RuleViewModel` 里其实早有 `previewRule` / `previewGroup` / `commit` / `revert`，
能力在引擎里，缺的是列表页的执行入口与页面分层。

**做法**：`RuleListScreen` 用 `rememberSaveable` 的 `page` 状态在三个独立
`@Composable` 全屏页之间切换，共用同一个 `RuleViewModel`（切换不掉草稿）；
设置页复用现有 `bulk` 路由指向新列表页，零新增 NavHost 路由；
`RuleUiState.lastApplied` 只是内存态展示，不落表、不进备份 DTO。

**回退顺序**：
1. 状态栈旋转丢态 → `rememberSaveable` 已保底；仍不行则退回单个 NavHost 路由内 `when(page)`。
2. 「应用」误写成直接执行 → 一律回到 `previewRule` → `commit`，MUST NOT 静默改写。
3. 删旧门后已存规则丢失 → 只删 UI 跳板，`rule_definitions` 数据一行不动。
4. 拆页引发编译红 → 优先保留 `RuleManagerScreen` / `RuleManagerDialog` 兼容壳，
   不回退动态编辑器本身。

**不动**：`RuleEngine` / `applyBulkTargets` / `changeset` / 两张规则表 / `version = 4` /
备份 DTO 与恢复拓扑序 / CSV 重建按钮文案 / 两页下拉刷新 / 无障碍刷新按钮 / Manifest / `release.yml`。

## 第 39 批（v0.2.21）：4 条反馈的根因与回退顺序

第 38 批只落了「列表 / 编辑器 / 查看匹配记录」三页与设置一级入口，使用者实测后仍发现
「新建分组内联在列表」「按钮满宽等权」「批量录入只认半角竖线且整批一句报错」「分类切到对方时
旧分类 id 残留」。根因是编辑态与导航的契约没补齐，不是引擎能力不足：RuleEditorCatalog
本就是「字段 → 操作符 → 值控件」单一来源。本批只改编辑态、解析、导航与视觉：

| 反馈 | 修法 |
|---|---|
| 生成规则报「未解析出有效映射」 | batchRules 加 issues 逐行收集；正则容错 `|｜→Tab` 与前后空格；空行 / `#` 跳过；新分类由弹窗回填全名，不再靠键盘猜名字 |
| 触发条件切到交易对方值没清空 | 字段切换时 copy 清空 value 与全部类型化字段，其余行不动 |
| 带文字提示的边框 | 全部 OutlinedTextField 的 label lambda 改 placeholder，去掉悬浮边框文字 |
| 分组与列表混页 / 无分类预览 | 新增 RuleGroupContent 独立页；ruleSummary 把条件与动作按 CategoryDisplay 的全名渲染在列表行 |
| 新建按钮太大 | 主操作单个实心不 fillMaxWidth，次操作 TextButton，行内操作紧凑，Arrangement.End 靠右 |

**回退顺序**：若 Kotlin 的 `split(Regex, limit)` 在当前 stdlib 解析不到，退到手动 indexOf 多分隔符；
若 ruleSummary 因 RuleUiState 字段缺失编译不过，退到只显示 r.name + 条件数量，不引入新 DAO 查询；
若 placeholder 在 BOM 版本上不可用，退回 label 但去掉文字（保持单行）。

零改表 → v0.2.20 上覆盖安装即可。`version = 4` 未变。
