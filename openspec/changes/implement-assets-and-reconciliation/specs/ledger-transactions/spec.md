# Delta for 账务联动与一致性

## ADDED Requirements

### Requirement: 对账是唯一的校准写入路径

系统 SHALL 通过 `LedgerRepository.reconcile(...)` 生成校准，
MUST NOT 让 UI 层直接插入 `BalanceAdjustmentEntity` 或直接改 `balanceCents`。
`reconcile` SHALL 返回差额并 SHALL 在事务内完成插入、重算与锚点推进。

#### Scenario: UI 绕过 Repository

- **GIVEN** 有人试图在 ViewModel 里直接调 `adjustmentDao.insert`
- **WHEN** 静态检查或评审
- **THEN** 被判定为违规
- **AND** 必须改走 `reconcile`

**理由**：唯一写入口才能保证「写后重算」不被漏掉；
阶段 1 已经把 9 个写方法收敛到 Repository，阶段 3 新增 4 个也照此办理。

---

### Requirement: 账户管理写操作走事务且只归档不物理删

`createAccount` / `updateAccountMeta` / `archiveAccount` SHALL 包在
`withTransaction` 内；新增账户后 SHALL 调用 `rebuildAllBalances()` 兜底。
存在关联流水的账户 SHALL 只用 `isArchived` 归档，MUST NOT 物理删除。

#### Scenario: 新增账户

- **GIVEN** 新增一个期初 100.00 元的资产账户
- **WHEN** 保存
- **THEN** `balanceCents = 10000` 且净资产同步增加
- **AND** 不产生负余额或无主流水

#### Scenario: 归档有流水的账户

- **GIVEN** 某账户有 200 笔流水
- **WHEN** 用户点归档
- **THEN** 账户 `isArchived = 1`、历史明细仍可查
- **AND** 不产生孤儿流水

**理由**：物理删除会留下孤儿流水、历史统计失真；归档成本几乎为零。

---

### Requirement: 锚点不参与余额真相源

`rebuildAllBalances()` 的真相源 SHALL 保持为
`initialBalance + 流水净变动 + 未撤销校准 delta`，
`lastReconciledAt` / `lastReconciledBalanceCents` SHALL NOT 出现在余额计算中。
修改或清空锚点 MUST NOT 改变任何账户余额。

#### Scenario: 清空锚点

- **GIVEN** 某账户锚点为 2026-10-01、余额 −50000 分
- **WHEN** 把两个锚点字段置空
- **THEN** 余额与净资产完全不变
- **AND** 只有核对视图的默认范围退回「全部流水」

**理由**：锚点是「上次对到哪」的展示字段，若当期初会破坏
「任何时刻可全量重建」这条账本最不能动的铁律。
