# 数据保全与批量规整（第 31 批）

## Why
- 误卸载或「清除数据」会让内部私有目录整体删除；使用者希望把数据与 App 解耦，定期同步到自己的 NAS，手机损坏/丢失后可重建。
- 现有加密备份 DTO 是手动一次性导出，缺自动快照与恢复引导；`android:hasFragileUserData` 可给卸载加一道系统确认。
- 历史导入数据里存在不规范的交易对方、分类、备注，需要可预览、可撤销的批量调整，而不引入完整规则引擎。

## What Changes（阶段一）
- 真实核查 `DatabaseKeyManager` 与 `SecureBackupCodec`：确认数据库口令随卸载丢失、备份口令与数据库口令完全独立；因此**撤销门禁里「口令派生 KEK 封装 DB 密钥」的多余设计**，快照直接复用 `SecureBackupCodec` 即自包含。
- 新增纯 JVM `SnapshotCodec`：快照命名、SHA-256 校验和、临时文件优先清理与保留 N 份策略。
- 新增纯 JVM `BulkEditEngine`：条件匹配、动作应用、逐字段 `changeset` 撤销。
- `defaultVersionName` 0.2.14 → 0.2.15；Manifest 增加 `hasFragileUserData="true"`。

## 不在阶段一（如实列出）
SAF 目录授权与写入、每日定时调度、恢复页、批量规整页与落库事务接入尚未实现；阶段一不产出可安装包。

## Capabilities
- `backup-restore`：外部快照与跨卸载重建。
- `bulk-edit`：即时批量规整。

## Impact
零新增依赖、零新增权限、表结构不变；阶段一不出包。
