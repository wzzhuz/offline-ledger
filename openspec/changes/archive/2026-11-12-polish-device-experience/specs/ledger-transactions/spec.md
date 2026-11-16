## ADDED Requirements

### Requirement: 收款方是流水级自由文本标签

`records` 与 `trash` SHALL 各有 `counterparty TEXT NOT NULL DEFAULT ''` 列，
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
- **THEN** 走 `onCreate` 按 Entity 建库，含 `counterparty` 列
- **AND** 不执行任何迁移 SQL

#### Scenario: 覆盖安装需卸载

- **GIVEN** 设备已装 version 4 的包
- **WHEN** 直接覆盖安装本版本
- **THEN** 明确要求先卸载再用 CSV / 加密备份带走测试数据
- **AND** 文档不得声称可直接升级

**理由**：使用者明确「稳定版前只会用测试数据，数据结构完全不需要考虑升级迁移」。
保留三段迁移反而会让「加字段」每次都要写脚本，与当前阶段目标冲突。

### Requirement: 账户类型可改与自定义排序
系统 SHALL 允许修正账户类型（资产 ↔ 信用负债）并重算余额；
SHALL 允许无流水账户物理删除、有流水账户 MUST 改为归档；
SHALL 提供已归档账户查看与恢复入口；
SHALL 支持账户上移 / 下移排序，且顺序 SHALL 同步到记一笔的常用账户平铺。
所有新增写操作 MUST 包在同一个数据库事务内并在末尾重算余额。

**理由**：使用者反馈「新建账户类型选错了没有修改功能，也不能删除重建；归档后账户无查看入口；排序是不是也没做」。

#### Scenario: 类型选错后修正
- **GIVEN** 一个被误建为「资产」的信用卡账户
- **WHEN** 编辑中改为「信用负债」并保存
- **THEN** 账户出现在信用负债分组，余额重算后数值一致

#### Scenario: 有流水账户禁止删除
- **GIVEN** 某账户已关联流水
- **WHEN** 用户点「删除」
- **THEN** 系统拒绝并提示改用归档，历史流水保持完整

### Requirement: 模板与分类的删除恢复
系统 SHALL 允许删除模板（草稿，不影响已生成流水）；
删除分类时 MUST 先检查关联流水，有流水 SHALL 自动降级为归档，
无流水 SHALL 才物理删除；已归档分类 SHALL 可恢复。

**理由**：使用者反馈「模板没有编辑和删除能力」「分类管理中没有删除功能」。

#### Scenario: 分类有流水
- **GIVEN** 分类「餐饮」下已有流水
- **WHEN** 用户删除该分类
- **THEN** 分类被归档而非删除，历史流水的分类归属不变

### Requirement: 账户类型支持四类且净资产不按类型分支

`AccountType` SHALL 包含 `ASSET` / `LIABILITY` / `PREPAID` / `INVESTMENT`，
分别对应资产账户、信用负债、充值储值卡、投资理财账户；资产页 SHALL 按类型分组展示。
净资产 SHALL 只按 `includeInTotal` 汇总余额，MUST NOT 按类型分支计算；
只有 `LIABILITY` SHALL 触发信用额度与负债展示。
枚举 SHALL 以名称字符串落库，新增类型 MUST NOT 需要改列定义或备份格式。

#### Scenario: 新增储值卡
- **GIVEN** 用户新增「公交卡」为 `PREPAID`、期初余额 100 元
- **WHEN** 打开资产页
- **THEN** 它出现在「充值 / 储值卡」分组，并按 `includeInTotal` 计入净资产

#### Scenario: 类型切换不算错钱
- **GIVEN** 一个 `ASSET` 账户有 3 条流水
- **WHEN** 用户把它改成 `INVESTMENT`
- **THEN** 余额按流水重算结果不变，净资产变化仅来自 `includeInTotal` 开关

**理由**：使用者反馈「账号类型除了资产账户、负债账户，是否再加充值账户、
投资理财账户」，公交卡 / 加油卡 / 股票基金是主流场景；钱迹也内置储值卡与投资账户。
边界是 v1 不做行情、净值与汇率，避免开无底洞。

---

### Requirement: 模板字段自由且顺序可调

模板 SHALL 是预填草稿：金额、分类、账户、备注、交易对方全部可选（仅名称必填），
生成流水时由默认账户兜底。周期规则会自动写入流水，其账户字段 SHALL 仍必填。
模板管理 SHALL 支持上移 / 下移调整顺序，顺序 SHALL 复用到记一笔的常用模板平铺；
排序 MUST 走既有 `reorderTemplates` 单事务，MUST NOT 新开写余额路径。

#### Scenario: 支出模板不填转出账户
- **GIVEN** 新建支出模板「午餐」，只选了分类，账户留空
- **WHEN** 保存模板
- **THEN** 保存成功，不出现「请选择转出账户」

#### Scenario: 模板排序生效
- **GIVEN** 5 个模板，按 sortOrder 排列
- **WHEN** 在管理页把第 5 个连点两次「↑」
- **THEN** 它升到第 3 位，记一笔常用模板前 4 个随之变化

**理由**：使用者反馈「模板管理里面模板是否也应该支持调整顺序。转出账户必填不合理吧，
模板应该比较自由」；iCost 也允许模板字段不完整并标红提示。

---
