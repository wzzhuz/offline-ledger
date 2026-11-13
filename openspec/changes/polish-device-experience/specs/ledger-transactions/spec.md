## ADDED Requirements

### Requirement: 收款方是流水级自由文本标签

`records` 与 `trash` SHALL 各有 `payee TEXT NOT NULL DEFAULT ''` 列，
模板与周期规则 SHALL 能把收款方带入生成的流水。收款方 MUST NOT 建成账户、
MUST NOT 有外键、MUST NOT 产生任何复式分录或余额联动。
删除后从回收站恢复 SHALL 保留收款方。

#### Scenario: 删除恢复不丢

- **GIVEN** 一条带收款方「盒马」的支出
- **WHEN** 删除再从回收站恢复
- **THEN** 恢复后的流水收款方仍为「盒马」
- **AND** 余额重算结果与删除前一致

#### Scenario: 模板带入

- **GIVEN** 模板设了收款方「房租中介」
- **WHEN** 从模板记一笔
- **THEN** 生成的流水带同一收款方

**理由**：使用者明确「只是不用那么严格，也不需要搞成账户」；
一个带默认值的文本列即可满足核对，不引入第二套账务模型。

---

### Requirement: 账户编辑改期初余额后必须重算

`updateAccountMeta` SHALL 在同一 `db.withTransaction` 内更新账户元数据，
改动 `initialBalanceCents` 时 SHALL 在事务末尾调用 `rebuildAllBalancesInternal()`。
系统 MUST NOT 让 `balanceCents` 缓存与「期初 + 流水 + 校准」真相源漂移。

#### Scenario: 期初余额生效

- **GIVEN** 账户期初 0、无流水、余额 0
- **WHEN** 编辑期初为 100000 分
- **THEN** `balanceCents` 变 100000 分
- **AND** 资产页分组小计同步变化

**理由**：`balanceCents` 是可重建缓存，任何改真相源的写路径都必须触发重算。

---

### Requirement: 稳定版前重置 Room 版本且不注册迁移

稳定版前 `AppDatabase version` SHALL 为 `1`，`Migrations.ALL` SHALL 为空数组，
系统 MUST NOT 注册任何 `Migration`。迁移 SQL 常量 MAY 保留供测试与历史追溯，
但 MUST NOT 被 Room 在运行时执行。

#### Scenario: 全新安装

- **GIVEN** 设备未装过本应用
- **WHEN** 首次打开
- **THEN** 走 `onCreate` 按 Entity 建库，含 `payee` 列
- **AND** 不执行任何迁移 SQL

#### Scenario: 覆盖安装需卸载

- **GIVEN** 设备已装 version 4 的包
- **WHEN** 直接覆盖安装本版本
- **THEN** 明确要求先卸载再用 CSV / 加密备份带走测试数据
- **AND** 文档不得声称可直接升级

**理由**：使用者明确「稳定版前只会用测试数据，数据结构完全不需要考虑升级迁移」。
保留三段迁移反而会让「加字段」每次都要写脚本，与当前阶段目标冲突。
