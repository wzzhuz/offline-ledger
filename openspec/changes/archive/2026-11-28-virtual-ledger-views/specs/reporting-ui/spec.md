# Delta: reporting-ui

## ADDED Requirements

### Requirement: 虚拟账本以标签视图实现且切换不带录入归属

应用 SHALL 提供「虚拟账本」能力：每个虚拟账本对应一条统一前缀标签 `台账:<名称>`
与一个独立只读视图，MUST NOT 引入 `bookId`、账本维度、互斥归属或第二个数据库。
主界面 4 Tab 顶部 SHALL 提供切换器，二级页 MUST NOT 重复显示；切换器 SHALL
**只切浏览范围，MUST NOT 携带录入归属**，默认永远回主账本且 MUST NOT 跨启动记忆。
记一笔中央 `+` 按钮与自动记账入口 SHALL 永远落主账本，MUST NOT 感知当前虚拟账本。

#### Scenario: 切换不影响自动记账

- **GIVEN** 用户从切换器进入「结婚礼金」台账页后返回，或停留在任一虚拟账本视图
- **WHEN** 通过系统分享把一笔支付宝消费原文送入自动记账
- **THEN** 该流水仍落在主账本，不带 `台账:` 标签、不勾不计入收支
- **AND** 主账本收支统计如实包含这笔消费

#### Scenario: 零改表可覆盖安装

- **GIVEN** 用户已安装 v0.2.69
- **WHEN** 覆盖安装本批版本
- **THEN** 既有流水、账户、标签与备份格式全部不变
- **AND** Room version 仍为 9，不注册 Migration、不新增 Entity

---

### Requirement: 台账页按人核算往来且录入自动带不计入

台账页 SHALL 按 `counterparty` 分组显示 收 / 还 / 待还 三列与本台账流水列表，
并提供「记收礼」「记还礼」两个入口。台账聚合 SQL MUST NOT 过滤 `excludeFromStats`，
SHALL 分别 SUM `INCOME` 与 `EXPENSE` 方向金额并带 `currencyCode` 条件；
`待还 = 收 − 还`，SHALL NOT 设置「结清」状态。
台账录入 SHALL 自动带入本账本标签与「不计入收支」开关，MUST NOT 预填分类或账户 id。
设置页 SHALL 提供新建 / 重命名 / 归档 / 排序与「台账记录不计入收支」总开关（默认开）。

#### Scenario: 礼金不虚高

- **GIVEN** 用户新建虚拟账本「结婚礼金」
- **WHEN** 点「记收礼」填对方张三与 500 元并保存
- **THEN** 流水带 `台账:结婚礼金` 标签且 `excludeFromStats = true`
- **AND** 主账本月度收入、支出与预算合计均不含这笔
- **AND** 台账页张三一行显示 收 500.00 / 还 0.00 / 待还 500.00

#### Scenario: 改名不丢历史

- **GIVEN** 「结婚礼金」下已有若干流水
- **WHEN** 在设置页把视图改名为「婚礼往来」
- **THEN** 绑定标签保持 `台账:结婚礼金` 不变，历史流水仍归入该视图

---

### Requirement: 主账本明细默认排除台账记录

明细分页查询 SHALL 默认排除带 `台账:%` 标签的流水，MUST NOT 改变方法签名、
`RecordFilter` 结构或 Paging 调用链。用户显式按标签筛选时 SHALL 恢复显示台账记录，
作为查回入口。MUST NOT 用 `excludeFromStats` 过滤明细列表本身。

#### Scenario: 查回台账记录

- **GIVEN** 主账本明细默认不显示台账流水
- **WHEN** 用户在筛选面板按 `台账:结婚礼金` 精确筛选
- **THEN** 该台账流水重新出现在明细列表
- **AND** 明细行仍可进入详情页查看不计入收支标记
