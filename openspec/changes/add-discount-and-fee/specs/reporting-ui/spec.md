# reporting-ui Delta

## ADDED Requirements

### Requirement: 附加金额的录入与展示

记一笔 SHALL 提供「优惠 / 手续费」折叠入口：支出/收入只显示优惠、
转账只显示手续费；填入后 SHALL 即时显示「原价 / 优惠 / 实付」
或「转出 / 手续费 / 实付扣款」三行。明细行 SHALL 显示划线原价
与蓝色「优惠 X」小标签，手续费 SHALL 用中性灰标签区分。
详情页 SHALL 以三行展示并支持编辑。系统 MUST NOT 引入会员墙、
MUST NOT 联网、MUST NOT 复制竞品视觉资产。

#### Scenario: 明细一眼看出优惠

- **WHEN** 明细出现原价 500.00 / 优惠 29.99 的流水
- **THEN** 显示划线 500.00 与蓝色「优惠 29.99」，实付 470.01 为主金额

#### Scenario: 转账编辑态不越界

- **WHEN** 编辑一笔转账并填写手续费
- **THEN** 转出/转入账户仍按记录 id 精确回填
- **AND** 本批 MUST NOT 改动 `detail-filter-and-transfer-edit` 的账户回填语义
