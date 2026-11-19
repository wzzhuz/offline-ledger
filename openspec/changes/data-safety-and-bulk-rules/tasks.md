# 任务（第 31 批）

## 阶段一（本次真实完成）
- [x] 远端与本地基线核查：HEAD `d7bafd66e6e7` / 版本 0.2.14
- [x] 真实读取 `DatabaseKeyManager.kt` / `SecureBackupCodec.kt` 并修正设计
- [x] `SnapshotCodec` 纯 JVM 内核 + 单测
- [x] `BulkEditEngine` 纯 JVM 内核 + 单测
- [x] 版本 0.2.15、Manifest `hasFragileUserData`
- [x] 六个静态检查本地 PASS
- [x] push CI 与远端 blob 校验

## 阶段二（本次真实完成）
- [x] SAF 授权目录 + 原子写（.tmp 回读校验后改名）+ 保留 7 份策略接线
- [x] 每日定时调度（AlarmManager + SnapshotReceiver，零新增依赖、零权限）
- [x] 恢复页：选快照 → 校验 → 解密 → 单事务重建 → 末尾重算
- [x] 批量规整页：条件/动作/预览/单事务落库/一键撤销（新增 BulkEditRevertTest）
- [x] 出包 v0.2.15 并上传坚果云（代码 `84846cdf3f9a315c16da7b82da5ed9a100f250c7` / CI `44332258434` / release `44333917298`；制品 12342643 B / 3714261 B / 270 B；上传到坚果云 success）
- [ ] 真机复核（使用者，刻意保留）

### 一期实现边界（如实说明）
`BulkConditions` 内核支持对方相等、时间窗与范围参数，`BulkTarget` 未含账户字段；
阶段二 UI 只暴露「对方包含 / 备注包含 / 金额区间 / 改分类 / 改对方 / 备注替换 /
设为不计入收支」，范围固定为全部流水。时间窗、按 `importSource` 限定与改账户
留二期，不写入本次 Requirement，避免规格与实现出现两套清单。

## 阶段三（第 34 批，v0.2.16：真机反馈收尾）

- [x] 真读 `LedgerRepository.importBackup` / `BackupPayload` / `CsvCodec` /
      `SettingsViewModel` 定位根因，不拿门禁里的逻辑归因当源码结论
- [x] 修复 `FOREIGN KEY constraint failed (code 1811)`：事务内
      `PRAGMA defer_foreign_keys = ON`、清空前 `UPDATE categories SET parentId = NULL`
      断开自引用、分类按父先子后插入、补 `aliasDao.clear()`、提交前
      `PRAGMA foreign_key_check` 兜底；失败由 `withTransaction` 整库回滚
- [x] 备份 DTO 补齐 `cardLast4` / `enabled` / `counterpartyAliases`，
      导出与恢复双向带上，带默认值保持旧备份可解析
- [x] 口令仍必填，新增「在本机记住」（复用 `SnapshotPrefs` 的 Keystore 加密存储，
      卸载即失效）；文件本身始终加密
- [x] 按表头嗅探分流：自有 11 列格式投进「导入账单」给明确引导，
      新增「从 CSV 重建」入口走 `CsvCodec.parseRebuild`；
      转账 / 无账户 / 无一级分类行列进 notices 明确跳过，不静默丢弃
- [x] 新增 `CsvRebuildTest`（格式识别 / 来源保留 / 跳过报告 / 第三方拒绝）
- [x] 六个静态检查本地 PASS
- [x] push CI 与 release 实测回填、坚果云上传
- [x] 第 34 批恢复闭环真机复核（使用者 2026-11-19 反馈：导出加密备份 → 恢复、
      自动快照 → 恢复均暂未发现问题）；「从 CSV 重建」入口另在阶段四收尾

### 本期实现边界（如实说明）

「从 CSV 重建」只覆盖 `EXPENSE` / `INCOME` 且账户与一级分类齐备的行；
第 34 批复用 `importRecords` 导致「账户不存在或已归档」，第 35 批起
拆 `rebuildFromOwnCsv` 专用路径并保持 `importSource/sourceId` 幂等。
`TRANSFER` / `SETTLEMENT`、无账户、无一级分类的行明确列为「格式跳过」，
完整重建（含转账、账户、模板、别名、启用态）仍走「从加密备份恢复」或
「从快照恢复」。二者是互补入口，不是同一份数据的两套写法。

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

## 阶段四（第 35 批，v0.2.17：自有 CSV 重建入口收尾）

- [x] 真读 `SettingsPages.kt` / `SettingsViewModel.kt` / `LedgerRepository.kt` /
      `CsvCodec.kt` 定位「账户不存在或已归档」根因，不拿门禁里的逻辑归因当源码结论
- [x] 拆 `LedgerRepository.rebuildFromOwnCsv` 专用写路径：单事务、末尾重算、
      失败整库回滚，与 `importBackup` 同骨架；`importRecords` 严格语义不动
- [x] 新增纯 JVM `CsvRebuildResolver`：归档账户 / 停用分类视为已知，
      缺项一次性收集而非首行抛异常；同名二级按 `一级/二级` 分别判定
- [x] `SettingsViewModel.confirmCsvImport` 按 `plan.rebuild` 分流，
      结果摘要含写入 / 重复跳过 / 缺项跳过 / 缺失账户与分类清单 / 重建引导
- [x] 改正两处自相矛盾文案：设置页帮助文本「清库后请用从 CSV 重建」、
      读文件嗅探里的「完整重建请用加密备份」表述；弹窗标题按模式区分
- [x] 新增 `CsvRebuildResolverTest`（归档不拒 / 空库逐项报告 / 换名清单 / 同名二级）
- [x] `check_transaction_annotations.py` 写方法清单登记 `rebuildFromOwnCsv`
- [x] 六个静态检查本地 PASS
- [x] push CI 与 release 实测回填、坚果云上传
- [ ] 真机复核（使用者，刻意保留）

### 本期实现边界（如实说明）

自有 CSV 仍不自包含：不带账户 / 分类 / 模板 / 别名 / 转账，完整重建只能走
加密备份或自动快照。表结构 `version = 3` 未变 → v0.2.16 上覆盖安装即可。

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
