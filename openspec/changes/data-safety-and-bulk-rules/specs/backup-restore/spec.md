## ADDED Requirements

### Requirement: 自动快照自包含且可跨卸载重建
写入外部授权目录的快照 SHALL 使用 `SecureBackupCodec` 加密并带 SHA-256 校验和与 schema 版本；恢复时 SHALL 仅凭使用者自设备份口令解密，MUST NOT 依赖 AndroidKeystore 中的数据库口令。裸 `.db` 文件 MUST NOT 作为重建依据，主库 MUST 留在内部私有目录。

#### Scenario: 卸载重装后重建
- **WHEN** 使用者误卸载后重装并重新授权同一目录、选择最近快照
- **THEN** 校验通过、凭口令解密、单事务重建并完成余额重算
- **AND** 账户、分类、流水、模板、预算与统计合计与快照一致

#### Scenario: 原子写与保留策略
- **WHEN** 快照写入过程中被中断
- **THEN** 目录里 MUST NOT 出现被当成成品的半截文件
- **AND** 成品文件保留最近 7 份，临时文件优先清理

### Requirement: 卸载时给出保留数据确认
Manifest SHALL 声明 `android:hasFragileUserData="true"`；该声明 MUST NOT 被当作对「清除数据」的保障，也不得替代外部快照。

#### Scenario: 手滑卸载
- **WHEN** 使用者在卸载对话框操作
- **THEN** 系统提示是否保留此应用的数据
