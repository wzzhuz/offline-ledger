# money-precision Delta

## ADDED Requirements

### Requirement: 附加金额沿用整数分且不引入浮点

`discountCents` / `feeCents` SHALL 为 `Long` 分（可空），
与 `amountCents` 同一精度体系。系统 MUST NOT 让 `Float` / `Double`
参与附加金额的存储、求和、比较或校验；原价推导 SHALL 为整数加法。
CSV 导出 SHALL 由 `Long` 分经 `BigDecimal` 换算成元两位小数，
导入 SHALL 经 `Cents.fromYuan` 转回分。

#### Scenario: 分币优惠

- **GIVEN** 优惠 0.30 元
- **WHEN** 落库并导出 CSV
- **THEN** `discountCents=30`、CSV 为 `0.30`，往返不产生尾差

#### Scenario: 原价不带第二真相源

- **WHEN** 编辑一条带优惠的流水
- **THEN** 原价输入框由 `amount + discount` 推导回填
- **AND** 保存时只写 `amountCents` 与 `discountCents`，MUST NOT 写原价列
