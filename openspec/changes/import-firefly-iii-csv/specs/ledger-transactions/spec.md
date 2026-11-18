## ADDED Requirements

### Requirement: Firefly III 复式转单式映射

导入 Firefly III 导出时，系统 SHALL 把复式流水收敛成本项目单式流水，
MUST NOT 把外部对方账户建成账户、MUST NOT 引入复式分录。
映射 SHALL 为：Withdrawal → EXPENSE（账户取 source、收款方取 destination）；
Deposit → INCOME（账户取 destination、付款方取 source）；
Transfer → TRANSFER（source 与 destination 都映射成自有账户、交易对方留空）；
Opening balance → 不生成流水，转成账户期初余额且保留原符号；
Reconciliation 与未知类型 → 跳过并在报告列出。
流水金额 SHALL 一律取绝对值并转成 `Long` 分，恒正；
期初余额 SHALL 保留原符号（信用负债期初可为负）。
时间 SHALL 由 ISO 8601 带偏移解析成 epoch millis，
再经 `LedgerClock` 派生 `localDate` 与 `yearMonth`。
拆分交易 SHALL 按导出的每一行独立成一条流水，
`group_title` 写进备注作为分组线索，MUST NOT 把多行合并成一笔。
转账 SHALL 不设分类，原 FF3 分类名写进备注，避免污染统计口径唯一出口。

#### Scenario: 支出带收款方

- **GIVEN** 一行 `Withdrawal`，source = 交通银行信用卡2203，
  destination = 盒马，amount = -112.59，category = 活-购物
- **WHEN** 按映射生成流水
- **THEN** 得到一条 EXPENSE，金额 11259 分，
  账户 = 交通银行信用卡2203，收款方 = 盒马，
  一级分类 = 活、二级分类 = 购物

#### Scenario: 拆分交易不合并

- **GIVEN** 同一 `group_id` 下两行：鞋 124.00 与袜子 39.90，各有独立 `journal_id`
- **WHEN** 生成流水
- **THEN** 得到两条独立流水，金额分别 12400 分与 3990 分
- **AND** 两条备注都含同一个 `group_title` 分组名

**理由**：6.6.6 的真实导出每个 split 独立一行，合并会丢金额与分类明细；
本项目本来就是「一笔流水一个分类」，按行落库才是保真映射。


### Requirement: 导入来源唯一标记

`records` 与 `trash` SHALL 各带 `importSource` / `sourceId` 两列与复合唯一索引，
两列 MUST 允许 NULL（手工记账恒为 NULL，SQLite 唯一索引不把 NULL 视为相等）。
CSV 明文导出与加密备份 DTO SHALL 带这两列，旧格式缺列 MUST 按空值兼容解析。
删除→恢复 MUST 保留来源标记。自定义极简 CSV 未带来源列时 SHALL 用
「账户 + 金额 + 日期 + 备注」派生 `sourceId` 并标记 `importSource = CSV`。

#### Scenario: 同一份文件导入两次

- **GIVEN** 一份带 `importSource` / `sourceId` 的自有 CSV 导出
- **WHEN** 清库后导入一次，再往同一账本导入第二次
- **THEN** 第一次写入 N 条，第二次写入 0 条、跳过 N 条
- **AND** 数据库不出现任何重复流水
