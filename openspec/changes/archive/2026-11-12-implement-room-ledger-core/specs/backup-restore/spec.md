# Delta for 备份与恢复

## ADDED Requirements

### Requirement: 备份头部不动且 payload 另起

系统 SHALL 新增顶层 `BackupFile(envelope, payload)`，
`BackupEnvelope` 的四项头部字段 SHALL NOT 变更。
`BackupPayload` SHALL 含 accounts / categories / records /
balanceAdjustments / trash 五类 DTO。

#### Scenario: 旧信封仍可解析

- **GIVEN** 阶段 0 导出的、只有头部字段的信封
- **WHEN** 用阶段 1 的解码器读取
- **THEN** 不抛异常
- **AND** 四项头部值保持不变

**理由**：Entity 加字段不该破坏旧备份解析；头部稳定是「旧备份可导入」的前提。

---

### Requirement: 编解码为纯 JVM 且可往返

`BackupExporter.encode` 与 `BackupImporter.decode` SHALL 不依赖 Android SDK，
以便纯 JVM 单测验证往返。

#### Scenario: 导出再导入余额一致

- **GIVEN** 一个含账户、两级分类、若干流水与一条校准的账本快照
- **WHEN** 编码为 JSON 再解码并交给 `LedgerEngine.computeBalances`
- **THEN** 各账户余额与原快照完全一致
- **AND** 净资产一致

**理由**：开发期表结构天天变，必须能边写边导出验证；纯 JVM 才能在没有模拟器的环境里测。

---

### Requirement: 导入事务化且导入后强制重算

整包恢复 SHALL 在 `withTransaction` 内先全量替换再调用 `rebuildAllBalances()`，
解析或校验失败 SHALL 整体回滚。系统 SHALL 把阶段 1 的明文导出标注为
「开发期用，勿放公共目录」。

#### Scenario: 导入中途失败

- **GIVEN** 备份文件结构非法
- **WHEN** 执行导入
- **THEN** 事务回滚，原账本完全不变
- **AND** 不留半套数据

**理由**：覆盖导入是最危险的操作，「修到一半」比「没导入」更难恢复。
