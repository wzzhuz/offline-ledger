## MODIFIED Requirements

### Requirement: 账单导入解析失败整体回滚

系统 SHALL 提供两种互不冲突的账单导入入口：自定义极简 CSV / TSV 账单导入，
以及 Firefly III 6.6.x `transactions.csv` 导出导入。
自定义极简 CSV 的解析 MUST 为纯 JVM 且先全量校验，
任一行非法（金额、类型、日期、账户名、分类名）SHALL 返回带行号的错误报告，
且 MUST NOT 进入写入事务。
校验全部通过后 SHALL 展示「将写入 N 条」确认，
确认后 SHALL 在单个事务内批量写入并在末尾重算余额，
MUST NOT 留下半套数据。
自定义极简 CSV 导入 SHALL 只接受 `EXPENSE` / `INCOME`，
MUST NOT 生成 TRANSFER 或 SETTLEMENT；该限制**仅约束自定义极简 CSV 入口**，
不适用于 Firefly III 导出导入。
`currencyCode` 列 SHALL 在自定义极简 CSV 导入时被忽略，币种 MUST 从账户继承。

#### Scenario: 一行非法金额

- **GIVEN** 一份 10 行自定义极简 CSV，其中第 7 行金额写成 `abc`
- **WHEN** 导入
- **THEN** 报告「第 7 行：金额格式非法」且账本零变化
- **AND** 不产生任何流水、余额不变

#### Scenario: 合法导入

- **GIVEN** 10 行合法自定义极简 CSV，其中 2 行与已有流水重复
- **WHEN** 确认导入
- **THEN** 写入 8 条、报告「跳过 2 条重复」
- **AND** 余额重算后各账户一致、币种从账户继承

**理由**：导入是最容易写脏账本的路径；
「先全量校验 + 单事务 + 末尾重算」沿用 `importBackup` 已验证的范式。
Firefly III 入口是另一种文件格式，不能因为这里有 `EXPENSE / INCOME` 限制
就推断系统整体禁止导入 TRANSFER。

## ADDED Requirements

### Requirement: Firefly III 导出导入

系统 SHALL 提供 Firefly III 6.6.x `transactions.csv` 的一次性历史迁移。
解析 MUST 按列名别名映射表头并复用引号状态机，MUST 为纯 JVM 且先全量校验，
任一行非法 SHALL 返回带行号的错误且 MUST NOT 进入写入事务。
解析通过后 SHALL 展示映射预览：待映射账户数、待映射分类数、
将写入流水数、期初余额行数、跳过行数、外币与不支持类型的提示。
账户与分类 SHALL 逐项可改：同名已有项自动建议，允许一键新建或跳过分类。
确认后 SHALL 在单个事务内按需新建账户与两级分类、批量写入流水、
末尾重算余额；任一行失败 SHALL 整体回滚。
导入 SHALL 用 `ff3:<journal_id>` 前缀写进备注实现幂等，
MUST NOT 新增表、字段、Migration 或网络权限。

#### Scenario: 真实样本迁移

- **GIVEN** 一份 6.6.6 导出的 1916 行 `transactions.csv`
- **WHEN** 解析并确认导入
- **THEN** 报告「1753 条支出 / 74 条收入 / 75 条转账 / 14 条期初余额」
- **AND** 支出取来源账户、收款方取外部对方账户；收入取目标账户、付款方取外部对方账户
- **AND** 期初余额转成账户 `initialBalanceCents` 且不生成流水
- **AND** 备注带 `ff3:<journal_id>`，重复导入第二次全部跳过

#### Scenario: 已有账户的期初余额

- **GIVEN** 某 FF3 账户映射到 App 内已存在的同名账户
- **WHEN** 未打开「覆盖已有账户的期初余额」
- **THEN** 不修改该账户现有 `initialBalanceCents`
- **AND** 对应期初行计入跳过数并在结果里明示

**理由**：一次性搬家最怕悄悄改掉使用者正在核对的余额；
零表结构幂等键符合稳定版前不背升级负担的约束。
