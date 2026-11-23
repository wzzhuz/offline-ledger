# ledger-transactions Delta（第 51 批）

## MODIFIED Requirements

### Requirement: 预置数据来源与数据库版本

预置分类 SHALL 来自随包 JSON 而非 Kotlin 常量；`AppDatabase.version` SHALL 为 **5**
（旧文写 3 为文档债，第 51 批核实）。
`SeedCallback.onCreate` 只对**全新库**生效，更换预制清单 MUST 全新安装，
MUST NOT 声称覆盖安装或导入旧备份后能生效。
分类 id 只是主键、不含语义，整表重编 MUST NOT 要求改写备份代码。

#### Scenario: 覆盖安装旧库

- **GIVEN** 设备上已有 v0.2.33 的库
- **WHEN** 覆盖安装 v0.2.34 并启动
- **THEN** 分类表保持旧内容，不出现重复或冲突
- **AND** 提示使用者「新清单需全新安装」

#### Scenario: 备份恢复

- **GIVEN** 一份含完整分类表的加密备份
- **WHEN** 执行 `importBackup`
- **THEN** 分类表整表替换、流水引用自洽
- **AND** 不依赖任何具体 id 数值
