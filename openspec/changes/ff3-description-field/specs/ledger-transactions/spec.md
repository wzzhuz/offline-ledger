# ledger-transactions Delta

## ADDED Requirements

### Requirement: 交易描述独立字段

流水 SHALL 以独立可空 `description` 列保存「这笔干嘛的」，MUST NOT 拼进备注、MUST NOT 覆盖交易对方。

#### Scenario: Firefly III 导入三值分离

- **WHEN** 导入一行 FF3 交易，其 `description` 为「牛奶鸡蛋」、`notes` 为「另有信息」、对方为「虚拟-不记录」
- **THEN** 落库后 description / note / counterparty 三者各在各的，导入结果不静默丢弃 description

#### Scenario: 描述为空时主标题回退

- **WHEN** 明细列表渲染一条 description 为空、交易对方为「肯德基」的流水
- **THEN** 主标题显示「肯德基」，不显示空描述占位；description 有值时主标题改用描述，对方与分类退到副行

#### Scenario: 描述可空且可联想

- **WHEN** 使用者在记一笔不填描述、或在筛选/规则中输入描述关键词
- **THEN** 不强制填写；筛选与规则 MUST 支持「描述包含 / 为空 / 不为空」并给出限量联想
