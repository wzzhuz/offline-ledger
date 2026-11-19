## ADDED Requirements

### Requirement: 快捷账户模板替代预制账户行
首次启动 SHALL 为零账户；资产页新增账户对话框 SHALL 提供只读快捷模板，按资产组与信用负债组展示，点选后自动填名称与类型，并可填卡号后四位。花呗 / 白条 / 美团月付 / 抖音月付 MUST 归信用负债组，MUST NOT 与支付宝余额、微信零钱并列。

#### Scenario: 三击建卡
- **WHEN** 使用者点「招行储蓄卡」模板并输入 1234 后保存
- **THEN** 创建 `LIABILITY` 之外的资产账户，显示名带 `(1234)`，`cardLast4` 落库
- **AND** 不产生任何预制账户行

### Requirement: 商户别名归一一期
`counterparty_aliases(pattern → shortName)` SHALL 在 CSV / Firefly III 导入落库前把商户全称归一成手工简称；`records.counterparty` MUST 保持自由文本，MUST NOT 引入外键或对方管理页。无命中时 MUST 原样返回。

#### Scenario: 全称归一
- **WHEN** 导入含「南京盒马网络科技有限公司」且别名表有「盒马网络科技 → 盒马」
- **THEN** 落库的 `counterparty` 为「盒马」

#### Scenario: 未知商户不动
- **WHEN** 交易对方无匹配别名
- **THEN** 落库文本与账单原文逐字一致

## MODIFIED Requirements

### Requirement: 预置数据来源与数据库版本
预置分类 SHALL 来自随包 JSON 而非 Kotlin 常量；`AppDatabase.version` SHALL 为 3。测试阶段 MUST 卸载重装后再导入重建，MUST NOT 声称覆盖安装可用。
