# Delta for ledger-transactions

## MODIFIED Requirements

### Requirement: 快捷账户模板替代预制账户行

> 标题沿用基线原名以命中整块替换；**本 Requirement 的现行语义以本节正文为准**。

首次启动 SHALL 为零账户；新增账户页 SHALL 提供只读快捷模板，
模板 SHALL 由新增的 `AccountTemplateSheet` 底部 sheet 承载，
按 `AccountTemplate.group`（支付余额 / 银行储蓄卡 / 信用负债）分组展示，
**资产组与信用负债组 MUST NOT 混在同一组**；花呗 / 白条 / 美团月付 / 抖音月付
SHALL 归信用负债组，MUST NOT 与支付宝余额、微信零钱并列。
模板 sheet SHALL 为 2 列等宽图标格、每格显示中性圆 + 中文首字与模板名，
4~5 字模板名 SHALL 单行完整可见，MUST NOT 被省略；
末行不足 2 个 SHALL 补 `weight(1f)` 空占位保持列对齐。
点选模板 SHALL 只回填账户名称与账户类型，SHALL 立即关闭 sheet，
MUST NOT 写库、MUST NOT 改 Entity、MUST NOT 清空已填的卡号后四位；
sheet 底部 SHALL 保留「自定义名称」入口（关闭 sheet 回主表单，不填模板）。
MUST NOT 引入在线图标库 / PNG 下载（需 `project.md` 显式授权联网，本批不申请）/ 字母索引，MUST NOT 复制一羽记账的视觉资产。
主表单 SHALL 保留「卡号后四位（可选）」输入，`needsLast4` 模板仍 SHALL 可落库。

#### Scenario: 三击建卡

- **WHEN** 使用者在新建页点「从模板选择」→ 选「招行储蓄卡」→ 输入 1234 后保存
- **THEN** 创建 `LIABILITY` 之外的资产账户，显示名带 `(1234)`，`cardLast4` 落库
- **AND** 不产生任何预制账户行、不写库于 sheet 打开期间

#### Scenario: 信用负债组不混排

- **GIVEN** 打开模板 sheet
- **WHEN** 查看分组
- **THEN** 「支付余额」「银行储蓄卡」「信用负债」三组各自独立
- **AND** 花呗 / 京东白条 / 美团月付 / 抖音月付均在信用负债组

#### Scenario: 自定义名称不误填

- **GIVEN** 打开模板 sheet 后未点任何模板
- **WHEN** 点底部「自定义名称」
- **THEN** sheet 关闭、账户名称保持原值，可继续手填名称与类型

**理由**：本批把模板从主表单 `FilterChip` 网格移到聚焦的底部 sheet，
是对一羽「选择银行」弹窗组织方式的等价借鉴；「按资产组 / 信用负债组展示、
点选自动填名称与类型、可填卡号后四位、不产生预制账户行」这些已验证结论
全部保留，只替换承载容器与视觉密度。按标题整块 MODIFIED，
MUST NOT 追加 ADDED 让两条相反要求并存。
