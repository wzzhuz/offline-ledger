# Delta for 备份与恢复

## ADDED Requirements

### Requirement: 设置页提供临时明文导出入口

设置页 SHALL 提供「导出明文 JSON」按钮，调用 `BackupExporter.encode` 后
把文件写入应用私有目录（`context.filesDir`），SHALL 立即用 `BackupImporter.decode`
自校验并回报「可解析，N 条流水」。界面 SHALL 醒目标注「明文，勿外传」。
系统 MUST NOT 为此申请任何存储权限。

#### Scenario: 每阶段跑一遍备份

- **GIVEN** 阶段 2 已记入若干笔流水
- **WHEN** 点「导出明文 JSON」
- **THEN** 生成文件且自校验通过、回报流水条数
- **AND** 文件位于应用私有目录，未申请存储权限

#### Scenario: 明文警告

- **GIVEN** 用户打开设置页
- **WHEN** 看到导出按钮
- **THEN** 可见「明文，勿外传」提示
- **AND** 说明阶段 5 会替换为 SAF 加密导出

**理由**：`project.md` 要求「每阶段结束必须能编译运行并跑一遍导出备份」；
加密 SAF 导出属阶段 5，本阶段用私有目录明文导出兑现这条流程要求，
同时不把明文文件暴露到公共目录。
