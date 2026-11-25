# backup-restore Delta

## ADDED Requirements

### Requirement: 附加金额在 CSV / 备份 / 回收站全程往返

`CsvRow` SHALL 增 `discountYuan` / `feeYuan` 两列（导出必带、
导入缺列按空串），MUST NOT 改动 `duplicateKey()` 与账户
`(name, cardLast4)` 复合匹配。加密备份 DTO、`LedgerSnapshot`、
回收站实体 SHALL 补两可选字段并按默认值兼容旧备份；
删除→恢复 MUST NOT 丢失附加金额。

#### Scenario: 老 CSV 兼容

- **WHEN** 导入第 8~13 列的老 CSV（无两列）
- **THEN** 按空串解析、附加金额为 null，其余字段与匹配键行为零变化

#### Scenario: 回收站往返

- **WHEN** 删除一条带优惠的流水再从回收站恢复
- **THEN** `discountCents` 原值恢复，MUST NOT 归零

### Requirement: 升表版本与迁移口径

`AppDatabase.version` SHALL 升到 7；按使用者既定「测试数据、
不考虑升级迁移」口径，`Migrations.ALL` SHALL 保持空、
`MigrationContractTest` SHALL 零改动。文档 MUST 明示
「覆盖安装会开库失败，MUST 先导出备份再卸载重装」，
MUST NOT 包装成「可直接覆盖安装」。

#### Scenario: 版本与迁移一致

- **WHEN** 静态检查核对 version / ALL / SQL 列名
- **THEN** version=7、ALL 为空、`MigrationContractTest` 断言不变
