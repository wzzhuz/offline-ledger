# ledger-transactions Delta

## ADDED Requirements

### Requirement: 附加金额与实付真相源

流水 SHALL 以 `amountCents` 存「实际金额（实付）」为唯一真相源，
MUST 另以可空 `discountCents`（支出/收入优惠）与 `feeCents`
（转账手续费；负数表示转账优惠）记录附加金额。
原价 SHALL 由 `amountCents + discountCents` 推导，
MUST NOT 再落一个原价列，MUST NOT 把附加金额拼进 `note` / `description`。
系统 MUST NOT 因新增附加金额而改写任何 `SUM(amountCents)` 统计口径，
MUST NOT 改变 CSV `duplicateKey()` 与账户复合匹配键。

#### Scenario: 满减支出

- **GIVEN** 原价 500.00、优惠 29.99
- **WHEN** 保存一笔支出
- **THEN** 落库 `amountCents=47001`、`discountCents=2999`
- **AND** 统计与预算按 47001 计，详情页显示原价 500.00

#### Scenario: 优惠非法输入被拦截

- **WHEN** 优惠为 0、负数或大于账单金额
- **THEN** 保存前拦截并提示，MUST NOT 静默落库

### Requirement: 转账手续费参与余额且不进收支

TRANSFER 流水 SHALL 以「转出扣 `amount + fee`、转入收 `amount`」
参与账户余额；手续费 MUST NOT 产生对应入账、MUST NOT 进收支统计。
`RecordDao.aggregateAccountDeltas` 与 `LedgerEngine.deltasFrom`
SHALL 同步该规则，MUST NOT 只改一处。`feeCents` 为负时 SHALL 表示
「转账优惠」并在 UI 明示。EXPENSE / INCOME / SETTLEMENT 的净变动规则
SHALL 保持 `from -amount` / `to +amount` 零改动。

#### Scenario: 转账带手续费

- **GIVEN** A 转 B 1000.00、手续费 5.00
- **WHEN** 重算余额
- **THEN** A 减少 1005.00、B 增加 1000.00
- **AND** 收支统计不含 5.00，UI 明示「手续费不计入收支」

#### Scenario: 负 fee 为转账优惠

- **WHEN** `feeCents = -300`（转账优惠 3.00）
- **THEN** 转出少扣 3.00，文案显示「转账优惠」而非「手续费」

### Requirement: 信用卡还款优惠以转账优惠表达

信用卡还款优惠 SHALL 以 `TRANSFER` + 负 `feeCents`（转账优惠）表达，
MUST NOT 给 `SETTLEMENT` 附加金额、MUST NOT 自动补一笔独立冲减流水。
口径依据一羽记账官方资产管理手册「还款相当于转账」与 CSV 手册
「手续费填写负数金额表示转账优惠」。
`SETTLEMENT` SHALL 保持退款 / 报销收回款的原语义不变。
系统 SHALL 在 UI 明示「转账金额填账单全额、优惠填差额」，
使转出实扣 = 账单全额 − 优惠、转入 = 账单全额、欠款归零。
该笔 MUST NOT 进收入概览与趋势双线（`TRANSFER` 天然不进 `StatRules`）。

#### Scenario: 还款优惠平账

- **WHEN** 账单 500.00、还款优惠 29.99，按「金额 500.00 / 转账优惠 29.99」记账
- **THEN** 转出账户减少 470.01、信用账户增加 500.00、欠款归零
- **AND** 收支统计不出现 29.99 的「收入」

#### Scenario: SETTLEMENT 不受污染

- **WHEN** 一笔退款 / 报销收回款带附加金额
- **THEN** `ExtraAmountRules.validate` 拒绝保存
- **AND** `linkedRecordId` 仍只指向原支出流水
