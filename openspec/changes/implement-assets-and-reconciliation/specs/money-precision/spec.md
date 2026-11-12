# Delta for 金额精度与净资产

## ADDED Requirements

### Requirement: 净资产看板落地且不靠减法倒推

资产页 SHALL 以 `SUM(balanceCents WHERE includeInTotal = 1)` 展示净资产，
SHALL 分别展示总资产 `SUM(balance WHERE includeInTotal AND type = ASSET)` 与
总负债 `ABS(SUM(balance WHERE includeInTotal AND type = LIABILITY))`。
总负债 SHALL 明确标注「仅展示，不参与计算」。
系统 MUST NOT 用「总资产 − 总负债」反推净资产。

#### Scenario: 资产与负债并存

- **GIVEN** 资产账户 10000 分、信用卡 −500 分
- **WHEN** 打开资产页
- **THEN** 净资产显示 95.00 元
- **AND** 不得显示 105.00 元

#### Scenario: 排除账户不污染展示值

- **GIVEN** 另有一个 `includeInTotal = false` 的账户 20000 分
- **WHEN** 打开资产页
- **THEN** 净资产仍为 95.00 元
- **AND** 总资产 / 总负债也不含该账户

**理由**：「净资产 = SUM(balance)」与「总资产 − 总负债」在带符号余额下
只差一个负号，却是最容易算错钱的一处；三个数各自独立由 SQL 聚合，
并在单测里用排除账户证明净资产不是减法倒推出来的。

---

### Requirement: 信用账户可用额度按额加负余额计算

信用负债账户 SHALL 展示 `可用额度 = creditLimitCents + balanceCents`
（`balanceCents` 欠款为负），MUST NOT 写成 `creditLimitCents − balanceCents`。
无额度的账户 SHALL 不展示该项。

#### Scenario: 欠款 500 元、额度 20000 元

- **GIVEN** 信用卡 `creditLimitCents = 2000000`、`balanceCents = −50000`
- **WHEN** 查看账户卡
- **THEN** 可用额度显示 195.00 元
- **AND** 不显示 205.00 元

#### Scenario: 无额度

- **GIVEN** 一个 `creditLimitCents = null` 的负债账户
- **WHEN** 查看账户卡
- **THEN** 不显示可用额度行

**理由**：余额本就带符号，再减一次等于把欠款当成可用余额，凭空虚高。
