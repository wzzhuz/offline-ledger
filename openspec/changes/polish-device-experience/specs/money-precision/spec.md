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

### Requirement: 图表刻度仅为展示换算
竖轴刻度 SHALL 由 `ChartData.yAxisLabels` 用 `BigDecimal` 把整数分格式化为元字符串，
MUST NOT 回写任何金额、MUST NOT 参与统计求和、MUST NOT 引入浮点金额运算。

**理由**：图表加数值刻度后仍要守住「金额存储与比较一律 Long 分」的铁律。

#### Scenario: 刻度不回写
- **GIVEN** 最大支出 100000 分
- **WHEN** 生成竖轴刻度
- **THEN** 返回 5 个元字符串且账本金额一分不变
