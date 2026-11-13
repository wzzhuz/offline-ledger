# Delta for 备份与恢复

## ADDED Requirements

### Requirement: 备份 schemaVersion 升至 4 且兼容 v1 至 v3

`BackupSchema.CURRENT` SHALL 为 4、`MIN_SUPPORTED` SHALL 保持 1，
`BackupEnvelope` 四项头部字段 MUST NOT 改动。
`BackupRecord` SHALL 增加带默认值的 `currencyCode`，
系统 SHALL 能导入 `schemaVersion` 为 1 / 2 / 3 的加密备份。

#### Scenario: v3 备份导入

- **GIVEN** 一份含 budgets 与 linkedRecordId、无 currencyCode 的 v3 备份
- **WHEN** 用 6c-1 包导入
- **THEN** 全量一致、旧流水币种默认 HKD、budgets 完整保留

#### Scenario: v4 往返

- **GIVEN** 一条 CNY 流水
- **WHEN** 导出再导入
- **THEN** 币种字段保持 CNY

**理由**：DTO 新字段带默认值 + `ignoreUnknownKeys` 让旧备份缺键自动补 HKD，
兼容不需要额外分支；信封头部不动保证既有解析器不返工。

## MODIFIED Requirements

### Requirement: CSV 格式约定

若提供 CSV 导出，SHALL 使用 UTF-8 with BOM，
金额列以「元」为单位保留两位小数，并 SHALL 注明 `currencyCode` 列。
JSON 加密备份 SHALL 保持口令加密（已交付能力，MUST NOT 倒退成明文）。
新增任何导出格式之前 MUST 先显式更新 `project.md` 的 v1.1 分批计划。
**本地导出文件为明文不构成隐私基线违规**：
隐私边界是账本数据 MUST NOT 上传第三方服务器或托管给厂商，
文件留在用户自己的设备或自选位置即由用户自行掌控。

#### Scenario: 导出文件留在用户自选位置

- **GIVEN** 用户通过 SAF 导出一份账本文件
- **WHEN** 选择本机目录或自选同步文件夹
- **THEN** 文件只写入该位置，应用 MUST NOT 发起任何网络上传
- **AND** 是否加密由所用导出格式决定，不由隐私基线强制

#### Scenario: 有人以「明文风险」否决 CSV

- **GIVEN** 有人提议以「CSV 是明文、与导出默认加密冲突」否决 CSV
- **WHEN** 评审
- **THEN** 评审发现该理由已被使用者在 6c-1 正式推翻
- **AND** 只按批次顺序与闸门状态决策，不再做隐私评审

**理由**：6c-1 使用者明确「导出明文是可接受的，数据掌控在自己手里就可以，
不可接受的是市场上 app 将数据保存在他们服务器上」。
原 Requirement 的「默认关闭 / 导出前二次确认 / 注明导出即明文 /
明文文件勿放公共目录」都以「明文=风险」为前提，属于被推翻的论据；
同时原文本绑定「v1 SHALL NOT」阶段版本号，会随阶段推进反复失效。
本 MODIFIED 保留格式约定与闸门纪律，删除错误论据与阶段绑定，
不新增任何 CSV 入口（结论保留，仅理由改为批次顺序）。
