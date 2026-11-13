## ADDED Requirements

### Requirement: 默认币种与默认时区按使用者所在地

新装应用的默认币种 SHALL 为 `CNY`，默认时区 SHALL 为 `Asia/Shanghai`。
默认币种 SHALL 可在设置页修改且只影响新建账户。
历史 spec 与备份兼容测试中出现的 `HKD` / `Asia/Hong_Kong`
SHALL 只作为多币种测试示例或历史默认值，MUST NOT 被当作现行默认。

#### Scenario: 全新安装

- **GIVEN** 设备在中国大陆、全新安装
- **WHEN** 查看四个预置账户
- **THEN** 币种均为 CNY
- **AND** `LedgerClock.zoneId` 为 `Asia/Shanghai`

#### Scenario: 日期分组不变

- **GIVEN** 时间戳 `2025-12-31T16:00:00Z`
- **WHEN** 派生本地日期
- **THEN** 仍是 `20260101` / `202601`（两地同为 UTC+8、均无夏令时）

**理由**：v2 设计期按「用户在香港」的错误假设写死 HKD / Asia/Hong_Kong；
使用者实际在江苏南京。改时区只修语义，不动任何已录日期。
