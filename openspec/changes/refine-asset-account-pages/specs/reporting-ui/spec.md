# Delta for reporting-ui

## ADDED Requirements

### Requirement: 资产页蓝底身份区与分组头部强化

资产页 SHALL 以蓝底顶部身份区承载页面标题、账户数与「含已归档」/「+ 新增」入口，
并在身份区内**按 `currencyCode` 逐组**展示资产 / 负债 / 小计。
MUST NOT 把 `state.groups` 汇总成一个跨币种的单一「净资产」总数，
MUST NOT 因借鉴一羽记账截图而违反基线「资产页按币种分组且移除单一净资产总数」。
账户分组标题 SHALL 用蓝色小竖条 + 分组名 + 账户数表达，
MUST NOT 显示按 `AccountType` 聚合的跨币种金额。
账户行 SHALL 保持纯行 + `ListDivider()`，MUST NOT 用 `Card` 底色 / 阴影 / 1dp 间距
表达行边界（基线「管理类页面入口固定且列表与编辑分离」）。

#### Scenario: 两币种账户不出现虚假总数

- **GIVEN** 一个 HKD 账户与一个 CNY 账户
- **WHEN** 打开资产页查看蓝底身份区
- **THEN** 出现 HKD / CNY 两组各自的资产 / 负债 / 小计
- **AND** 页面不出现把两币种加在一起的单一净资产数额

#### Scenario: 分组标题不混算

- **GIVEN** 「资产账户」分组下有 CNY 与 HKD 两个账户
- **WHEN** 查看分组标题
- **THEN** 显示蓝色小竖条、分组名与「2 个账户」，MUST NOT 显示跨币种分组金额

---

### Requirement: 资产账户行紧凑化且操作收进菜单

资产账户行 SHALL 以「图标圆 + 名称 / 卡号后四位 / 币种 + 金额右对齐 + 副文案」
组织，图标 SHALL 复用 `CategoryIcon` 的中性圆 + 中文首字，
MUST NOT 引入彩色图标、银行 logo 资源或在线图标下载（联网能力 SHALL 先经 `project.md` 显式授权，本批不申请）。
信用负债账户 SHALL 在余额为零时显示「无欠款」、有额度时沿用
`Reconciliation.availableCredit` 显示「可用 X」；
「不计入净资产」与「已归档」标注 SHALL 保留。
行操作 SHALL 收进单个「⋮」紧凑菜单，菜单 SHALL 含
详情 / 对账 / 编辑 / 归档或恢复 / 删除 / 上移 / 下移七项，
MUST NOT 再用多个文字按钮横排撑高行。
`longPressDraggableHandle` SHALL 挂在整格行根上，
MUST NOT 再显示三道横线「≡」小手柄；`rememberKeyedReorder`、
`commitAccountOrderAll` 与上移 / 下移 TalkBack 兜底 SHALL 零回归。

#### Scenario: 行紧凑且七项可达

- **GIVEN** 一个含卡号后四位的储蓄卡账户与一个信用负债账户
- **WHEN** 查看账户行并点「⋮」
- **THEN** 行显示图标圆、名称带 `(last4)`、金额右对齐；信用负债显示「无欠款」或「可用 X」
- **AND** 菜单七项逐项可达，长按整格仍可拖动排序
- **AND** 页面不出现「≡」手柄与横排文字操作按钮

---

### Requirement: 新建账户页采用近全屏紧凑表单

新增账户 SHALL 由 `AlertDialog` 小弹窗改为近全屏 `Dialog`
（`usePlatformDefaultWidth = false`）：蓝底 TopBar「← 添加资产账户 / 保存」、
分组小竖条、「图标 + 账户名称」紧凑白底小卡、底部大保存按钮。
表单字段与顺序 SHALL 保持不变（账户名称 / 卡号后四位 / 账户类型 /
期初余额 / 信用额度），`onConfirm` 签名与 `AssetsViewModel.addAccount` SHALL 零改动；
输入框 SHALL 用 `placeholder` 与 `heightIn(min)`，MUST NOT 用浮动 `label` 或固定 `height`。
MUST NOT 新增备注、隐藏账户、默认使用或账户图标等业务字段，
MUST NOT 升 `AppDatabase` 版本；`EditAccountDialog` 本批 SHALL 零改动。
MUST NOT 新增 Navigation 路由或改变 4 Tab 结构。

#### Scenario: 新建页字段零回归

- **GIVEN** 打开「+ 新增」
- **WHEN** 点「招行储蓄卡」模板、输入卡号后四位 1234 与期初余额 1000 并保存
- **THEN** 创建非 `LIABILITY` 的资产账户，名称带 `(1234)`，期初余额重算生效
- **AND** 页面仍是 4 Tab、无新路由、数据库版本不变

#### Scenario: 键盘弹起仍可保存

- **GIVEN** 中小屏手机在新建页聚焦「期初余额」
- **WHEN** 软键盘弹起
- **THEN** 底部大保存按钮仍可见可点，表单区可滚动，MUST NOT 被键盘完全遮挡
