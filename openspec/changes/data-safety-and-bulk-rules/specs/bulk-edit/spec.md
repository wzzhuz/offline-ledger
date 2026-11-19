## ADDED Requirements

### Requirement: 批量规整可预览且可逐字段撤销
批量调整 SHALL 先给出命中条数与前后对比，确认后 MUST 在单事务内写入并末尾重算；变更 MUST 生成 `changeset` 保存每个受影响字段的变更前值，撤销 MUST 逐字段还原。禁止静默改写历史，禁止一期持久化规则定义或导入后自动触发。

#### Scenario: 改分类并撤销
- **WHEN** 使用者按「交易对方包含 X」把 N 条记录改为某分类后执行撤销
- **THEN** N 条记录的所有字段与调整前逐字段一致

#### Scenario: 条件互不串味
- **WHEN** 同时设置对方包含、备注包含、分类与金额区间
- **THEN** 只命中同时满足全部条件的记录


## 第 36 批（v0.2.18）：规则 2.0 与分类迁移映射表

**根因**：一期「即时批量规整」只有 AND 条件、单一固定目标值、规则用完即弃，
无法表达「旧分类 A/B/C 或对方关键词散乱记录一次圈出」与「20 组旧分类一次映射到新分类」。
Firefly III 的规则是「条件组 + 动作组 + 规则组有序 + strict/非 strict + 对历史回溯应用」，
本批借其模型，不引触发器自动跑 / 表达式引擎 / 标签体系 / stop processing。

**实现取舍（与门禁描述的一处偏差，如实登记）**：门禁写「规则四表」，
实作收敛为 `rule_groups` + `rule_definitions` **两张表**，条件与动作序列化为 JSON 列、零外键。
理由：四种操作符各建表会把恢复链路的 FK 拓扑序再拉长一层，v0.2.16 刚修完 1811，
不为结构纯度再冒一次风险；能力范围（可保存、有序、AND/OR、取反、多动作、回溯）不缩水。

| 项 | 落地 |
|---|---|
| 纯 JVM 内核 | `RuleModels.kt` / `RuleEngine.kt`：ALL/ANY、negate、七种操作符（含正则、为空、不等于）、多动作、组内 sortOrder 有序、`changeset` 逐字段撤销 |
| 分类迁移 | `CategoryMigration` + `migrationReport`（每组命中数 / 冲突组 / 未命中）+ 首次命中优先，与规则组共用同一 `applyBulkTargets` 写路径 |
| 持久化 | `AppDatabase.version = 4`，两张表零外键；恢复按 groups → definitions 顺序插入，纳入 `defer_foreign_keys` / `foreign_key_check` 同一事务 |
| 备份 | `BackupPayload.ruleGroups` 带默认值，旧备份缺键解析为空列表；导出与自动快照自动带上规则 |
| UI | 设置页按钮文案精简为「从 CSV 重建」并给同排按钮 `maxLines = 1`；「批量规整」页新增「规则与分类迁移」弹层，预览 → 单事务 → 末尾重算 → 一键撤销 |
| 不做 | 导入后自动触发（触发器默认关）、规则组嵌套、表达式引擎、标签/预算联动、stop processing |

**测试**：`RuleEngineTest` 6 条（ANY/ALL/取反/组顺序/映射命中与冲突/撤销还原）、
`RuleBackupRoundTripTest` 2 条（规则随备份往返、旧备份缺键仍可解析）。
门禁里写的「恢复 androidTest」未做：CI 无模拟器不跑 androidTest，
改为纯 JVM 备份往返单测覆盖 DTO 层，恢复链路仍待真机「导出→清数据→重装恢复」复核。

**`version = 4` → 必须卸载重装、装之前先导出**；v0.2.17 覆盖安装会开库失败。
