# 备份与恢复 Specification

## Purpose

定义备份格式、兼容性、导入事务与导入后重算。备份是本项目的交付证明，必须在数据层阶段就最小可用。

---

## Requirements

### Requirement: 备份 DTO 与 Entity 分离并带版本号

备份 SHALL 使用与 Room Entity 分离的 DTO，并 SHALL 包含 `schemaVersion`。系统 SHALL 支持旧版本备份导入，导入时 SHALL 补全新增字段的默认值。

#### Scenario: v1 备份导入 v2

- **GIVEN** 导入一份无 `rootCategoryId` / `parentId` 的旧备份
- **WHEN** 解析
- **THEN** 所有分类 `parentId = null`、`level = 0`、`rootCategoryId = 自身 id`
- **AND** 历史流水 `rootCategoryId = categoryId`，统计结果正确

**理由**：Entity 加字段不该破坏旧备份解析；版本号让未来迁移有明确入口。

---

### Requirement: 导出流式写入且默认加密压缩

导出 SHALL 采用协程 + 流式序列化写入，SHALL 经 Gzip 压缩并默认口令加密。系统 MUST NOT 把全部记录拼成一个超大 String 再写出。

#### Scenario: 大数据量导出

- **GIVEN** 账本含 5 万条流水
- **WHEN** 执行导出
- **THEN** 逐条写入并生成文件
- **AND** 内存占用平稳，不发生 OOM

**理由**：10 年账本约 2–5 万条，全量字符串拼接是最容易在老数据上炸掉的写法。

---

### Requirement: 导入事务化且失败整体回滚

整包恢复 SHALL 在单个事务内完成，SHALL 先校验 `schemaVersion` 与外键完整性；校验或解析失败 SHALL 整体回滚，MUST NOT 留下半套数据。

#### Scenario: 导入中途失败

- **GIVEN** 备份文件在中途损坏
- **WHEN** 解析到第 N 条报错
- **THEN** 事务回滚
- **AND** 原账本数据完全不变

**理由**：覆盖导入是最危险的操作，「修到一半」比「没导入」更难恢复。

---

### Requirement: 导入后强制重算与自检

导入完成后 SHALL 执行 `rebuildAllBalances()` 与账本健康自检，检查孤儿流水、`rootCategoryId` 一致性、调整记录合法性。

#### Scenario: 恢复备份

- **GIVEN** 从加密 JSON 全量恢复
- **WHEN** 事务提交后
- **THEN** 自动重算余额
- **AND** 净资产与流水聚合一致，异常写入审计日志

**理由**：导入等价于一次性写入上万条流水，必须走与日常写操作相同的一致性保障。

---

### Requirement: 覆盖导入前二次确认与口径明确

覆盖导入前 SHALL 二次确认并明确提示「将全量替换现有全部数据」。v2 SHALL 把预置账户/分类的去重规则定义为**全量替换**。

#### Scenario: 覆盖导入

- **GIVEN** 用户选择一份备份进行恢复
- **WHEN** 确认前
- **THEN** 弹窗说明将替换全部数据
- **AND** 确认后按全量替换执行，不产生重复预置分类

**理由**：「合并还是替换」不写死，实现时必然摇摆，且会产生重复的默认账户。

---

### Requirement: 最小可用导出在第一阶段落地

分阶段计划 SHALL 把「最小可用 JSON 导出」安排在数据层阶段，早于任何页面实现。

#### Scenario: 数据层阶段验收

- **GIVEN** 第 1 阶段完成
- **WHEN** 验收
- **THEN** 能导出并重新导入一份含流水与账户的备份
- **AND** 每个阶段结束都跑一遍导出

**理由**：开发期表结构天天变，没有导出等于边写边丢数据；备份也是「数据是你的」这一铁律的可验证证据。

---

### Requirement: CSV 格式约定

CSV 导出 SHALL 使用 UTF-8 with BOM，金额列以「元」为单位保留两位小数，并 SHALL 注明「导出即明文」。

#### Scenario: Excel 打开

- **GIVEN** 用户在电脑用 Excel 打开导出的 CSV
- **WHEN** 打开
- **THEN** 中文分类名与备注不乱码
- **AND** 金额显示为两位小数

**理由**：方便电脑核对是 CSV 的唯一目的，编码与单位不写清楚就会变成「打开了但看不懂」。

---

### Requirement: 备份信封头部稳定且带版本号

系统 SHALL 提供 `BackupSchema.CURRENT` 与 `MIN_SUPPORTED` 常量，
并提供与 Room Entity 分离的 `@Serializable BackupEnvelope`。
信封头部 SHALL 包含 `schemaVersion`、`exportedAtEpochMilli`、`currencyCode`
与 `counts`，且头部字段 SHALL NOT 在后续阶段变更。

#### Scenario: 阶段 1 往备份里加数据 DTO

- **GIVEN** 阶段 1 定义了 `BackupAccount` / `BackupRecord` 等数据 DTO
- **WHEN** 把它们加入 `payload`
- **THEN** `schemaVersion`、`exportedAtEpochMilli`、`currencyCode` 三项字段名与含义不变
- **AND** 阶段 0 已导出的信封仍能被解析

#### Scenario: 序列化往返

- **GIVEN** 构造一个 `BackupEnvelope(exportedAtEpochMilli = 123456789L, currencyCode = "HKD")`
- **WHEN** 用 `kotlinx.serialization` 编码再解码
- **THEN** `schemaVersion` 为 1、`currencyCode` 为 `"HKD"`、时间戳不变

**理由**：备份格式必须在数据层阶段就定下来，否则开发期表结构频繁变动时
无法边写边导出验证。头部稳定是「旧备份可导入」的前提。

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

### Requirement: 对账锚点随备份往返且不改信封

`BackupAccount` SHALL 保留 `lastReconciledAt` / `lastReconciledBalanceCents` 两个可空字段，
`LedgerMappers.toDto` / `toEntity` SHALL 双向映射它们。
导出 SHALL 自动带上锚点，SHALL NOT 为此修改 `BackupEnvelope` 头部或递增 `schemaVersion`。
阶段 3 MUST NOT 写 Room Migration。

#### Scenario: 锚点备份往返

- **GIVEN** 某账户锚点为 `at = 1762000000000`、`balance = 99999` 分
- **WHEN** 导出 JSON 再解析
- **THEN** 两个锚点字段值不变
- **AND** `schemaVersion` 仍为 1、信封四项头部不变

#### Scenario: 旧备份无锚点

- **GIVEN** 一份阶段 1 导出的、账户无锚点字段的备份
- **WHEN** 解析并导入
- **THEN** 两个锚点字段为 `null`
- **AND** 核对视图默认退回「全部流水」且不报错

**理由**：字段在阶段 1 就预留了，阶段 3 只是启用；
可空 + 默认 null 保证旧备份兼容，不需要递增版本号或写迁移。
