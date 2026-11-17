# ledger-transactions · Delta

## MODIFIED Requirements

### Requirement: 账户类型可改与自定义排序

系统 SHALL 允许修正账户类型（资产 ↔ 信用负债）并重算余额；
SHALL 允许无流水账户物理删除、有流水账户 MUST 改为归档；
SHALL 提供已归档账户查看与恢复入口；
账户排序 SHALL 按 `AccountType` 分组，SHALL 与分类 / 模板共用
`ui/components/ReorderSupport.kt` 的 `rememberReorderDrag`：
长按手柄拖动过程中其余账户 SHALL 实时让位，松手一次性写入 `sortOrder`；
上移 / 下移 SHALL 保留在「⋯」菜单内作为无障碍兜底，MUST NOT 作为主入口；
顺序 SHALL 同步到记一笔的常用账户平铺。
所有新增写操作 MUST 包在同一个数据库事务内并在末尾重算余额。
资产页账户行 SHALL 与明细页同为纯行（16dp 内边距 + 56dp 行高 + `ListDivider`），
MUST NOT 用 `Card` 底色 / 阴影或 1dp 间距表达边界；
行内详情 / 对账 / 编辑 / 归档 / 删除等可点操作 SHALL 用 `bodyMedium`
且触摸区 MUST ≥40dp，MUST NOT 用 `labelSmall` 裸 `Text` 表达可点击项。

**理由**：使用者反馈「资产页账号列表因为底色看起来视觉效果没有明细页大」
「副标题是需要可以点击的，现在感觉字体偏小」「资产页排序还是上移、下移」；
第 16 批按标题整块 MODIFIED，避免 specs 同时存在「上移 / 下移排序」与
「统一拖拽排序」两条相反要求。

#### Scenario: 类型选错后修正
- **GIVEN** 一个被误建为「资产」的信用卡账户
- **WHEN** 编辑中改为「信用负债」并保存
- **THEN** 账户出现在信用负债分组，余额重算后数值一致

#### Scenario: 有流水账户禁止删除
- **GIVEN** 某账户已关联流水
- **WHEN** 用户点「删除」
- **THEN** 系统拒绝并提示改用归档，历史流水保持完整

#### Scenario: 组内拖拽实时让位
- **GIVEN** 「资产账户」组内有 6 个账户
- **WHEN** 长按第 1 个账户手柄向下拖过第 2 个
- **THEN** 拖动过程中其余账户立即让位，负债组顺序不变
- **AND** 松手后重进资产页顺序保持

---

### Requirement: 模板字段自由且顺序可调

模板 SHALL 是预填草稿：金额、分类、账户、备注、交易对方全部可选（仅名称必填），
生成流水时由默认账户兜底。周期规则会自动写入流水，其账户字段 SHALL 仍必填。
模板管理 SHALL 与分类 / 账户共用 `ui/components/ReorderSupport.kt` 的
`rememberReorderDrag`：长按手柄拖动过程中其余模板 SHALL 实时让位，
松手一次性写入 `sortOrder`；上移 / 下移 SHALL 保留在 ⋯ 菜单内作为无障碍兜底，
MUST NOT 作为主入口。顺序 SHALL 复用到记一笔的常用模板平铺；
排序 MUST 走既有 `reorderTemplates` 单事务，MUST NOT 新开写余额路径。

#### Scenario: 支出模板不填转出账户
- **GIVEN** 新建支出模板「午餐」，只选了分类，账户留空
- **WHEN** 保存模板
- **THEN** 保存成功，不出现「请选择转出账户」

#### Scenario: 模板拖拽生效
- **GIVEN** 5 个模板，按 sortOrder 排列
- **WHEN** 长按第 5 个模板手柄向上拖过两个模板后松手
- **THEN** 它升到第 3 位，拖动过程中其余模板实时让位
- **AND** 记一笔常用模板前 6 个随之变化

**理由**：使用者反馈「模板页的排序还是上移、下移，考虑整改 app 的排序方案，
风格尽量统一」；按标题整块 MODIFIED，不留矛盾文本。
