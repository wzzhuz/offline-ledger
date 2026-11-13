# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: 迁移静态检查在稳定版前降级为记录性提示

`check_transaction_annotations.py` SHALL 把
`AppDatabase version`、`Migrations.ALL`、`addMigrations`、
CREATE / ALTER SQL 常量与 `DEFAULT 'HKD'` 检查从硬失败改为
**记录性提示（打印但不 `return 1`）**。
**保留硬失败**：31 个写方法 MUST 包在 `db.withTransaction` 内、
源码出现 `fallbackToDestructiveMigration` MUST 失败。
其余五个静态检查脚本 MUST NOT 改动。
`WRITE_METHODS` SHALL 从 30 扩到 31（新增 `importRecords`）。

#### Scenario: 删掉一段迁移

- **GIVEN** `Migrations.ALL` 只含两段
- **WHEN** CI 执行静态检查
- **THEN** 打印「迁移检查：稳定版前暂缓」提示但 job 仍 success

#### Scenario: 写方法漏事务

- **GIVEN** `importRecords` 未包裹 `db.withTransaction`
- **WHEN** CI 执行静态检查
- **THEN** 检查 FAIL
- **AND** 报错指出 `importRecords`

**理由**：`project.md`「稳定版前的版本策略」已明确条件性暂缓；
门禁若继续硬卡，就等于用代码把被使用者否定的约束偷偷加回来。
写方法事务与破坏性迁移禁令是账本正确性底线，不能一起降级。

---

### Requirement: 6c-2 单测覆盖 CSV 编解码与门禁降级

系统 SHALL 提供 `CsvCodecTest` / `CsvImportPlanTest` /
`RecordFilterCurrencyTest` / `MigrationGateRelaxedTest` 纯 JVM 单测类，
覆盖 UTF-8 BOM、8 列表头、两位小数、引号转义、TSV、
非法行带行号、去重键、币种列忽略、`activeCount()` 与门禁 `warnings`。
`testDebugUnitTest` MUST 在 CI 中单独执行。

#### Scenario: 含逗号的备注

- **GIVEN** 一条备注为「午餐, 加饮料」的流水
- **WHEN** 导出再解析
- **THEN** 备注完整还原，不被拆成两列

#### Scenario: 门禁含 warnings

- **GIVEN** 6c-2 已落地
- **WHEN** 检查 `check_transaction_annotations.py`
- **THEN** 存在 `warnings` 且 version / ALL 检查不写入 `problems`

**理由**：CI 无模拟器且使用者已推迟真机验证，
纯 JVM 层是唯一能自动验证「CSV 字节正确、解析回滚、门禁真的降级」的地方。
