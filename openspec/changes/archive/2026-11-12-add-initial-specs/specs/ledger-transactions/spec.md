# Delta for 账务联动与一致性

> 初始归档：本模块全部为新增要求（`ADDED`），无 `MODIFIED` / `REMOVED`。
> 归档时已原样合并至 `openspec/specs/ledger-transactions/spec.md`。

## ADDED Requirements

### Requirement: 写操作与余额变更同处一个事务

流水的新增、编辑、删除、恢复 SHALL 与账户余额变更包裹在同一个 Room `@Transaction` 内。系统 MUST NOT 在事务外单独修改 `balance`。

#### Scenario: 事务中途失败

- **GIVEN** 插入一笔支出的过程中发生异常
- **WHEN** 事务回滚
- **THEN** 该流水未落库且账户余额未变
- **AND** 不出现「流水已记但余额未变」

**理由**：原子性是账本的最低要求。事务只保证单次操作原子，长期一致性由下一条的重建机制保证。

---

### Requirement: 余额是派生缓存而非真相源

唯一真相源 SHALL 为 `accounts.initialBalance` + 全部有效流水 + 全部 `BalanceAdjustment`。`accounts.balance` SHALL 明确降级为可重建缓存。

#### Scenario: 余额字段被篡改

- **GIVEN** 直接修改数据库使某账户 `balance` 偏离流水聚合值
- **WHEN** 冷启动触发账本自检
- **THEN** 系统检测到不一致并自动重算修复
- **AND** 写一条审计日志

#### Scenario: 用户手动校验

- **GIVEN** 用户在设置页点「校验并修复余额」
- **WHEN** 执行全量重算
- **THEN** 所有账户余额与流水聚合一致
- **AND** 展示校验结果

**理由**：只靠事务无法覆盖导入、迁移、崩溃、预置数据等场景。把余额设为缓存，等于给长期一致性加了自愈能力。

---

### Requirement: 余额可全量重算

系统 SHALL 提供唯一权威函数 `rebuildAllBalances()`，在事务内按流水对账户的 `+/- amount` 聚合、叠加校准 delta 与期初后写回 `balance`。冷启动、导入完成、任何写操作后 SHALL 调用重算或受影响账户重算。

#### Scenario: 导入后重算

- **GIVEN** 从备份全量恢复账本
- **WHEN** 导入事务完成后
- **THEN** `rebuildAllBalances()` 被执行
- **AND** 净资产与流水聚合结果一致

**理由**：一条聚合 SQL 即可完成，个人账本 5 万行在十毫秒量级，成本远低于维护双写正确性的风险。

---

### Requirement: 编辑流水先回滚旧影响

编辑流水 SHALL 消除旧记录对余额的全部影响后再应用新记录。实现 MAY 采用「写入新记录 → 全量重算」的等价简化路径。

#### Scenario: 修改金额

- **GIVEN** 把一笔 30 元支出改为 50 元
- **WHEN** 保存
- **THEN** 账户余额只再减少 20 元
- **AND** 不是减少 50 元也不是不变

#### Scenario: 修改账户与类型

- **GIVEN** 把一笔支出改为收入并换账户
- **WHEN** 保存
- **THEN** 原账户余额回滚、新账户按收入增加
- **AND** 月报分类与金额同步更新

**理由**：v1 规定「回滚旧 → 写新 → 应用新」三步，逻辑正确但分支多；直接重算更不易错，二者等价即可。

---

### Requirement: 回收站恢复重新走插入事务

恢复已删除流水 SHALL 重新执行插入事务并重算余额，MUST NOT 只翻转一个删除标记而不联动余额。

#### Scenario: 恢复支出

- **GIVEN** 回收站中恢复一笔 30 元支出
- **WHEN** 确认恢复
- **THEN** 该流水回到明细列表
- **AND** 对应账户余额重新扣减 3000 分

**理由**：「只有入口没有出口」是已记录过的真实坑：归档/删除必须配套反向操作，且反向操作要走同一套联动逻辑。

---

### Requirement: 有流水的账户与分类只能归档

系统 SHALL 对存在关联流水的账户与分类使用 `isArchived` 归档，MUST NOT 物理删除。外键 SHALL 使用 `onDelete = RESTRICT`。

#### Scenario: 删除已用账户

- **GIVEN** 用户尝试删除有 200 笔流水的微信零钱账户
- **WHEN** 提交删除
- **THEN** 系统只允许归档，账户与历史明细保持可查
- **AND** 不产生孤儿流水

**理由**：物理删除会留下孤儿流水，历史统计随之失真。归档的成本几乎为零。

---

### Requirement: 全局唯一统计口径

系统 SHALL 定义唯一常量 `isStatRelevant = type IN (EXPENSE, INCOME) AND isDeleted = 0 AND excludeFromStats = 0`，报表、导出、明细概览 MUST 共用该出口。`TRANSFER` 与 `BALANCE_ADJUST` MUST NOT 计入收支统计。

#### Scenario: 年报不混入转账与校准

- **GIVEN** 账本含 1 笔转账、1 笔余额校准、1 笔不计入收支的垫付
- **WHEN** 生成年度报表
- **THEN** 三者均不出现在收入/支出/分类占比中
- **AND** 明细页与导出结果口径一致

**理由**：v1 只在明细页写了过滤条件，报表与导出没写——「删掉的账还躺在年报里」是最伤信任的 bug。单一出口让各页面没有机会各写一遍裸 SQL。

---
