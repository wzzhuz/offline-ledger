# Delta for reporting-ui

## ADDED Requirements

### Requirement: 文本诊断入口与原文预览页

系统 SHALL 提供 `TextIntakeActivity`，通过 `ACTION_PROCESS_TEXT` 与
`ACTION_SEND`（`text/plain`）两个 intent-filter 接收使用者主动提交的整页文本，
入口标签 SHALL 为「离线账本 · 文本诊断」，MUST NOT 使用「记一笔」等暗示已能记账的文案。
预览页 SHALL 以只读方式显示：来源（`process_text` / `send`）、
字符数与截断标记、**全部**疑似金额串（正则 `-?\d[\d,]*\.\d{2}`）、
方向与时间与商户三项命中、以及「是否达到后续自动落库门槛」的只读预演标签；
SHALL 提供一键复制、一键清空与关闭，并 SHALL 用 `SelectionContainer`
允许使用者长按选择原文贴回分析。
本批 SHALL NOT 写 Room、SHALL NOT 调用 `LedgerRepository`、
SHALL NOT 生成流水、SHALL NOT 修改任何既有页面。

#### Scenario: 支付宝账单详情全选分享

- **GIVEN** 使用者在 HyperOS 3.0 的支付宝账单详情页用系统「全选」选中整页文字
- **WHEN** 点「分享」→「离线账本」
- **THEN** 预览页显示来源 `send`、原文全文、全部疑似金额串与四项命中结果
- **AND** 数据库流水条数不变、账户余额不变

#### Scenario: 多个金额候选

- **GIVEN** 原文里同时出现 `-12.00`、`0.20`、`2026` 附近的小数与合计金额
- **WHEN** 预览页渲染
- **THEN** 全部候选逐条列出，不折叠成单一「识别结果」
- **AND** 「达到门槛」标签为否
- **AND** 系统不猜测哪个是真实金额

#### Scenario: 原文超长

- **GIVEN** 提交的原文超过 20,000 字符
- **WHEN** 预览页渲染
- **THEN** 显示前 20,000 字符并显式标注「已截断」
- **AND** 门槛预演标签为否

**理由**：真机原文质量（节点聚合 vs 系统 OCR、噪声多少、字段是否齐全）尚未验过，
此时写死解析器与自动落库等于把不确定性固化进代码。诊断页把原文与命中情况摊开，
让使用者人工判一次，再决定规则表怎么写。

---

### Requirement: 后续批自动落库置信度门槛

同一 change 的后续批（规则表 + 自动落库）SHALL 遵守：
金额 MUST 精确匹配 `-?\d+\.\d{2}` 且**候选唯一**；
方向、时间、商户四项齐全 SHALL 才允许自动落库；
缺任一项、出现多候选、或疑似 OCR 噪声（错字、小数点丢失、字段乱序）
SHALL 降级为草稿并提示手填，MUST NOT 由启发式或模型猜一个值补上。
去重复用 `importSource` + `sourceId` 或
「账户 + `amountCents` + `localDate` + `note`」复合键口径，
MUST NOT 新开一条写余额的分支。
自动落库 SHALL 仅适用于 `auto-text-intake` 入口；
模板、手动记一笔、桌面快捷方式仍 MUST NOT 自动落库。

#### Scenario: 四项齐全且金额唯一

- **GIVEN** 原文精确包含唯一金额 `-12.00`、支付时间、商户全称与明确方向标签
- **WHEN** 后续批的识别链路运行
- **THEN** 允许直接落库并给出可撤销提示
- **AND** 走既有 Repository 事务，余额自动重算

#### Scenario: 金额候选不唯一

- **GIVEN** 原文出现两个以上符合金额格式的字符串
- **WHEN** 后续批的识别链路运行
- **THEN** 不落库、不取第一个候选，降级为草稿并列出候选供使用者选
- **AND** 账户余额保持原值

**理由**：`project.md` 原始场景里弃用 Firefly 的原因正是「差 1 分钱、账单太多核算太费事，
最后记一笔虚拟支出凑平」。0 次确认 + 不确定抽取会把这类事故自动化，
与铁律 2「账要算得准」直接冲突；门槛必须在实现前写进规格而非留在聊天里。
