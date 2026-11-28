# Delta: ledger-transactions

## ADDED Requirements

### Requirement: 台账聚合走独立只读查询

虚拟台账 SHALL 使用 `VirtualBookDao` 的独立只读查询，MUST NOT 复用 `tagStats`
（后者过滤不计入且不分方向）。聚合 SHALL 按 `counterparty` 分组、空对方归
「（未填对方）」、分 INCOME / EXPENSE 求和、带 `currencyCode` 条件，
MUST NOT 写 Room、MUST NOT 新开写余额分支。

#### Scenario: 收还方向不串

- **GIVEN** 张三收 500、还 600
- **WHEN** 打开台账页
- **THEN** 显示 收 500.00 / 还 600.00 / 待还 -100.00
- **AND** 不把两笔相消成单一净额记录

---

### Requirement: 标签按需创建且定义不进备份

`台账:` 标签 SHALL 由既有 `LedgerRepository.bindTags` 在保存流水时按需自动创建，
MUST NOT 改预制种子数据。虚拟账本定义 SHALL 存 SharedPreferences，
MUST NOT 进 Room、备份 DTO 或 Migration；但流水标签 SHALL 随
`BackupRecord.tagNames` 完整往返。

#### Scenario: 换设备重建视图

- **GIVEN** 用户在旧设备把加密备份恢复到新设备
- **WHEN** 在设置页新建同名「结婚礼金」虚拟账本
- **THEN** 台账页重新聚合出历史收礼 / 还礼流水
- **AND** 不计入收支标志仍为 true
