# 任务（第 31 批）

## 阶段一（本次真实完成）
- [x] 远端与本地基线核查：HEAD `d7bafd66e6e7` / 版本 0.2.14
- [x] 真实读取 `DatabaseKeyManager.kt` / `SecureBackupCodec.kt` 并修正设计
- [x] `SnapshotCodec` 纯 JVM 内核 + 单测
- [x] `BulkEditEngine` 纯 JVM 内核 + 单测
- [x] 版本 0.2.15、Manifest `hasFragileUserData`
- [x] 六个静态检查本地 PASS
- [x] push CI 与远端 blob 校验

## 阶段二（未完成，不冒称）
- [ ] SAF 授权目录 + 原子写 + 保留策略接线
- [ ] 每日定时调度（复用 AlarmManager，零新增依赖）
- [ ] 恢复页：选快照 → 校验 → 解密 → 单事务重建 → 末尾重算
- [ ] 批量规整页：范围/条件/动作/预览/落库事务/一键撤销
- [ ] 出包 v0.2.15 并上传坚果云
- [ ] 真机复核（使用者）
