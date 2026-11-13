# Delta for 流水与账务联动

## ADDED Requirements

### Requirement: 导入流水复用既有写路径且不新增去重字段

`LedgerRepository.importRecords` SHALL 登记进 `WRITE_METHODS`
并 SHALL 包裹在 `db.withTransaction` 内。
每行 SHALL 经 `withResolvedRoot`（含一级分类解析与 `withCurrency` 币种继承）后
由 `recordDao.insert` 写入，事务末尾 MUST 调用 `rebuildAllBalancesInternal()`。
系统 MUST NOT 为导入新开一条写余额的分支。
去重 SHALL 按「账户 + `amountCents` + `localDate` + `note`」
走参数化 `SELECT COUNT(*)` 查询，MUST NOT 为此新增字段或表。

#### Scenario: 导入 8 条合法流水

- **GIVEN** 8 行通过全量校验的 CSV
- **WHEN** 确认导入
- **THEN** 8 条流水走同一事务、末尾一次重算
- **AND** 每条币种等于其账户币种、一级分类正确

#### Scenario: 事务中途抛异常

- **GIVEN** 批量写入第 5 行时解析失败
- **WHEN** 事务结束
- **THEN** 前 4 行也不落库
- **AND** 账户余额与导入前完全一致

**理由**：复用 `withResolvedRoot` 是避免「导入流水币种错 / 一级分类空 /
余额没重算」三件套的最稳做法；去重不加字段，
正好验证稳定版前不再为升级背负担。

## MODIFIED Requirements

### Requirement: 显式迁移且 seed 覆盖升级场景

**稳定版前的版本策略（条件性暂缓）**：
使用者至今未装机、稳定版前只会使用测试数据，不存在需要保留的旧库，
覆盖升级路径 MUST NOT 作为当前验收范围。
数据结构 SHALL 可按功能需要自由调整：
允许重置 `AppDatabase version`、不再逐段追加 `Migration`、
不保证旧备份向后兼容、`BackupSchema.CURRENT` / `MIN_SUPPORTED` 可随实现调整。
已落地的三段 Migration 与备份兼容代码 SHALL 保留但冻结，
MUST NOT 作为后续阶段的验收条件，
`check_transaction_annotations.py` 的迁移/版本检查 SHALL 降级为记录性提示。
`fallbackToDestructiveMigration` 禁令在稳定版前暂缓，
但源码中出现仍 SHALL 被静态检查判失败（视为误用）。
**任何阶段 MUST NOT 以「会破坏升级 / 要写 Migration / 旧备份读不了」
为由否决数据结构调整。**
**恢复条件**：使用者明确说明「需要考虑升级迁移」时，
MUST 重新启用显式 `Migration`、备份向后兼容、
`fallbackToDestructiveMigration` 禁令，
并把「从旧版覆盖升级数据完好」加回真机验证清单。

#### Scenario: 稳定版前调整数据结构

- **GIVEN** 某阶段需要给 Entity 加字段
- **WHEN** 评审以「要写 Migration / 旧备份读不了」为由否决
- **THEN** 评审依据 `project.md`「稳定版前的版本策略」驳回该否决
- **AND** 允许直接改 Entity 并重置 version

#### Scenario: 迁移门禁降级

- **GIVEN** 有人删掉 `MIGRATION_3_4`
- **WHEN** 跑 `check_transaction_annotations.py`
- **THEN** 只打印记录性提示，job 不因此失败
- **AND** 删掉某个写方法的事务包裹仍使 job 失败

#### Scenario: 使用者要求恢复升级支持

- **GIVEN** 使用者明确说明「需要考虑升级迁移」
- **WHEN** 下一阶段开工
- **THEN** 重新启用显式 Migration 与备份兼容
- **AND** 真机清单加回覆盖升级验证

**理由**：使用者在 6c-2 前明确「暂时不用考虑升级场景……
除非我说明需要考虑升级迁移」。原 Requirement 要求逐段显式迁移、
seed 覆盖升级场景，与当前阶段的实际约束相反；
若只追加新要求不 MODIFY 旧要求，specs 会同时存在两条矛盾约束。
本 MODIFIED 写清「条件性暂缓 + 恢复条件」，
MUST NOT 被写成「永远不做升级」；其余十几条历史 Requirement 保留不删，
由 `project.md` 暂缓执行清单统一声明优先级。
