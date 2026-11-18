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
- [ ] 出包 v0.2.15 并上传坚果云（release 实测回填时勾）
- [ ] 真机复核（使用者，刻意保留）

### 一期实现边界（如实说明）
`BulkConditions` 内核支持对方相等、时间窗与范围参数，`BulkTarget` 未含账户字段；
阶段二 UI 只暴露「对方包含 / 备注包含 / 金额区间 / 改分类 / 改对方 / 备注替换 /
设为不计入收支」，范围固定为全部流水。时间窗、按 `importSource` 限定与改账户
留二期，不写入本次 Requirement，避免规格与实现出现两套清单。
