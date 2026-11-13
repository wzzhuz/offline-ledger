# Delta for 金额精度

## ADDED Requirements

### Requirement: CSV 金额只在展示层换算且币种列被忽略

CSV 导出的金额 SHALL 由 `Long` 分经 `BigDecimal` 换算成「元」两位小数；
导入 SHALL 由 `Cents.fromYuan` 把字符串转回 `Long` 分。
系统 MUST NOT 让 `Float` / `Double` 参与金额运算或解析。
导入文件的 `currencyCode` 列 SHALL 被忽略，
流水币种 MUST 从关联账户继承，
MUST NOT 因文件里的币种值与账户不一致而污染账本。

#### Scenario: 两位小数往返

- **GIVEN** 一笔 3850 分的 HKD 支出
- **WHEN** 导出再导入
- **THEN** CSV 中为 `38.50`，解析回 3850 分
- **AND** 全程无浮点参与

#### Scenario: 文件币种写错

- **GIVEN** 一行导入数据账户为 HKD 账户但 `currencyCode` 列写 CNY
- **WHEN** 导入
- **THEN** 流水币种为 HKD（继承账户）
- **AND** 不产生跨币种流水

**理由**：金额铁律不因新增明文格式破例；
币种是账户属性，外部文件不该有权改写，
否则一行笔误就能让统计口径错好几个月。
