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
- [ ] 真机复核（使用者，刻意保留）

### 本期实现边界（如实说明）

「从 CSV 重建」只覆盖 `EXPENSE` / `INCOME` 且账户与一级分类齐备的行，
复用既有 `importRecords` 并保持 `importSource/sourceId` 幂等。
`TRANSFER` / `SETTLEMENT`、无账户、无一级分类的行明确列为「格式跳过」，
完整重建（含转账、账户、模板、别名、启用态）仍走「从加密备份恢复」或
「从快照恢复」。二者是互补入口，不是同一份数据的两套写法。
