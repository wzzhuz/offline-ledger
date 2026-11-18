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
第 23 批：导入前 SHALL 按「金额分相同 + 同一账户 + localDate ±1 天」生成疑似匹配候选，
逐笔由使用者勾选「跳过该行 / 仍导入」；MUST NOT 自动合并、静默删除或按描述文本判等。
确认后 SHALL 只写入未勾选跳过的行，结果 SHALL 报告疑似匹配数与按勾选跳过的行数。

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
导入 SHALL 给每条流水写入 `importSource = FIREFLY_III` 与 `sourceId = journal_id`，
由 `records(importSource, sourceId)` 复合唯一索引保证数据库级幂等；
备注里的 `ff3:<journal_id>` 只作人类可读线索，MUST NOT 再当去重键。
`AppDatabase.version` SHALL 升到 2 且不注册 Migration，使用者 MUST 卸载重装并先导出备份；
MUST NOT 引入网络权限或含网络行为的依赖。

#### Scenario: 真实样本迁移

- **GIVEN** 一份 6.6.6 导出的 1916 行 `transactions.csv`
- **WHEN** 解析并确认导入
- **THEN** 报告「1753 条支出 / 74 条收入 / 75 条转账 / 14 条期初余额」
- **AND** 支出取来源账户、收款方取外部对方账户；收入取目标账户、付款方取外部对方账户
- **AND** 期初余额转成账户 `initialBalanceCents` 且不生成流水
- **AND** 每条带唯一键 `FIREFLY_III / journal_id`，重复导入第二次全部跳过

#### Scenario: 已有账户的期初余额

- **GIVEN** 某 FF3 账户映射到 App 内已存在的同名账户
- **WHEN** 未打开「覆盖已有账户的期初余额」
- **THEN** 不修改该账户现有 `initialBalanceCents`
- **AND** 对应期初行计入跳过数并在结果里明示

**理由**：一次性搬家最怕悄悄改掉使用者正在核对的余额；
零表结构幂等键符合稳定版前不背升级负担的约束。


### Requirement: 导入结果反馈与当前页刷新

所有导入 / 备份恢复成功后，系统 SHALL 在对应页面给出整页结果视图
（写入 / 跳过 / 新建账户 / 新建分类 / 期初应用 / 期初跳过）并附一次 Snackbar 反馈，
MUST NOT 只用一行容易被挤出屏幕的 message，也 MUST NOT 在写入完成后自动返回。
写入期间 SHALL 显示「正在写入 N 条」与进度指示，MUST NOT 只把确认按钮置灰。
Firefly III 结果视图 SHALL 提供「去看明细」与「完成」入口。
写操作成功后 SHALL 发一次进程内数据变更事件，明细 / 资产 / 统计 MUST
在**当前月份 / 当前币种**立即重查，使用者 MUST NOT 靠「切月份再切回来」触发刷新。
明细页 SHALL 使用官方 `PullToRefreshBox` 提供下拉刷新：手势只在可滚动内容已到顶部、
不能继续上划时接管，超过阈值松手触发，刷新完成 SHALL 给出结果反馈；
MUST NOT 用教手势的常驻小字、MUST NOT 自造拖动动画、MUST NOT 额外增加
TopAppBar 无障碍刷新按钮（使用者明确要求不增加业务按钮）。

#### Scenario: 导入后不切月份

- **GIVEN** 使用者在明细页当前月份，从设置页完成一次 Firefly III 导入
- **WHEN** 回到明细页
- **THEN** 当前月份立即出现新流水，月度合计与日头同步更新
- **AND** 列表滚动位置与筛选条件不因刷新而被清空
