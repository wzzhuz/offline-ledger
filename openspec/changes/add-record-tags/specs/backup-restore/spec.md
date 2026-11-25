# backup-restore · Delta Specification

> 第 75 批：标签的四条往返。1 条 ADDED + 1 条 MODIFIED。

## ADDED Requirements

### Requirement: 标签随备份 CSV Firefly 与回收站四条链路往返

系统 SHALL 让标签随加密备份、CSV、Firefly III 导入与回收站删除/恢复四条链路保真往返：

- 加密备份：`BackupPayload` SHALL 增加 `tags: List<BackupTag>`，
  `BackupRecord` SHALL 增加 `tagNames: List<String>`；新增字段 MUST 带默认值，
  旧备份与旧快照缺键 SHALL 解析为空列表且不破坏 `ignoreUnknownKeys` 兼容。
  `exportSnapshot` / `importBackup` / `BackupExporter` / `SnapshotCodec` MUST 同步。
- CSV：`CsvRow` SHALL 增加 `tags: String`（逗号分隔标签名、最多 8 个），
  `CsvCodec.HEADER` SHALL 在末尾追加 `tags` 并列进 optional 集合；
  `parseImport` / `parseRebuild` / `detectFormat` 三处 MUST 同步，
  旧 8~15 列文件缺列 SHALL 按空串兼容。
  `CsvRow.duplicateKey()` MUST NOT 含 tags。
- Firefly III：`FireflyRow.tags` SHALL 透传到 `FireflyEntry`，
  `importFirefly` 写流水后 SHALL 按逗号拆分落成标签；
  备注 SHALL 仍只保留 `notes` 原文，MUST NOT 再出现标签串。
  `docs/FireflyIII导入说明.md` 与 Firefly 预览页「不会导入标签系统」文案 SHALL 同步删除。
- 回收站：`TrashEntryEntity` SHALL 增加 `tagNames` 文本快照；
  `deleteRecord` SHALL 在移动前存快照，`restoreRecord` SHALL 按名重新绑定关联。
  MUST NOT 为 trash 建外键级联关联表（会随 records 删除被清空）。

四条链路 SHALL 统一走「按名 `ensureByName` 复用或新建 + 整体替换关联」，
MUST NOT 各自写一套映射。含逗号的标签名 SHALL 在导入时报错并列入报告。

#### Scenario: 加密备份往返

- **GIVEN** 一笔挂 2 个标签的流水与一个已归档标签
- **WHEN** 导出加密备份、清库、恢复
- **THEN** 标签定义、归档状态与流水关联全部还原

#### Scenario: 旧备份缺键

- **GIVEN** 一份 v0.2.53 导出的备份（无 `tags` 键）
- **WHEN** 恢复
- **THEN** 解析成功、标签为空列表，既有流水与账户一分不差

#### Scenario: CSV 往返

- **GIVEN** 一笔挂「家用,旅行」的流水
- **WHEN** 导出 CSV 再导入
- **THEN** `tags` 列为「家用,旅行」，导入后仍挂这两个标签
- **AND** 旧文件无 `tags` 列时不报错

#### Scenario: Firefly 标签落位

- **GIVEN** 一行 FF3 流水 `tags = "朋友,旅行"`
- **WHEN** 确认导入
- **THEN** 该流水挂两个标签，备注里 MUST NOT 出现「朋友,旅行」

#### Scenario: 删除后恢复

- **GIVEN** 一笔挂 3 个标签的流水
- **WHEN** 删除再从回收站恢复
- **THEN** 3 个标签全部回来，且未产生重复标签

## MODIFIED Requirements

### Requirement: Firefly III 导出导入

系统 SHALL 提供 Firefly III 6.6.x `transactions.csv` 的一次性历史迁移。
解析 MUST 按列名别名映射表头并复用引号状态机，MUST 为纯 JVM 且先全量校验，
任一行非法 SHALL 返回带行号的错误且 MUST NOT 进入写入事务。
解析通过后 SHALL 展示映射预览：待映射账户数、待映射分类数、
将写入流水数、期初余额行数、跳过行数、外币与不支持类型的提示。
账户与分类 SHALL 逐项可改：同名已有项自动建议，允许一键新建或跳过分类；
分类本体 SHALL 走全量只读选择器按 id 精确映射（第 74 批）。
确认后 SHALL 在单个事务内按需新建账户与两级分类、批量写入流水、
**按 `tags` 列建立标签关联**、末尾重算余额；任一行失败 SHALL 整体回滚。
已有账户期初 SHALL 按保持 / 覆盖 / 差额调整三态策略处理，
改名映射 SHALL 同样生效，`ADJUST` SHALL 幂等（第 74 批）。
导入 SHALL 给每条流水写入 `importSource = FIREFLY_III` 与 `sourceId = journal_id`，
由 `records(importSource, sourceId)` 复合唯一索引保证数据库级幂等。
`AppDatabase.version` SHALL 为 `8`（第 75 批订正，旧文写 2 为文档债）
且不注册 Migration，使用者 MUST 卸载重装并先导出备份；
MUST NOT 引入网络权限或含网络行为的依赖。
规则、订阅、附件、存钱罐、预算、汇率折算、复式分录、外部对方账户建账户
仍 SHALL 不导入；**标签系统 SHALL 不再列入「不导入」清单**。

#### Scenario: 真实样本迁移

- **GIVEN** 一份 6.6.6 导出的 1916 行 `transactions.csv`
- **WHEN** 解析并确认导入
- **THEN** 报告「1753 条支出 / 74 条收入 / 75 条转账 / 14 条期初余额」
- **AND** 每条带唯一键 `FIREFLY_III / journal_id`，重复导入第二次全部跳过
- **AND** 带 `tags` 的行落成标签，不含标签的行备注不被改写

#### Scenario: 已有账户的期初余额

- **GIVEN** 某 FF3 账户映射到 App 内已存在的同名账户
- **WHEN** 期初策略为「保持」
- **THEN** 不修改该账户现有 `initialBalanceCents`
- **AND** 对应期初行计入跳过数并在结果里明示
