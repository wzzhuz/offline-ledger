# Delta for 金额精度

## ADDED Requirements

### Requirement: 币种是标签不参与金额运算

`currencyCode` SHALL 是 ISO 4217 字符串标签，
`amountCents` SHALL 始终为 `Long` 分。
系统 MUST NOT 把不同币种的 `amountCents` 直接相加，
MUST NOT 在金额运算中引入汇率或浮点换算。

#### Scenario: 两币种流水并存

- **GIVEN** HKD 支出 3850 分与 CNY 支出 2000 分
- **WHEN** 分别查看两个币种的统计
- **THEN** 各自返回 3850 与 2000，不出现 5850 这个跨币种和
- **AND** 金额全程为 Long 分

**理由**：严格不混算是 6c-1 的核心口径；
一个虚假精确的折算总数比按币种分组列小计更糟。
