# 技术设计：资产账户页借鉴一羽的 UI 优化

## 1. 核查结论与纠错

**纠错**：上一轮把三张一羽截图误当本 App 现状。核查 `AssetsScreen.kt`（626 行）、
`AccountEntity.kt`、`AccountTemplates.kt`、`AssetsViewModel.kt`、
`LedgerApp.kt` 与 4 份基线 spec 后，真实差距见 `proposal.md`「先收回上一轮的一处误判」。
本设计以**代码事实**为准，不以参考图推断现状。

**一羽的实现查不到**：它是闭源商业 App，官网无源码与技术博客；只能按截图与
使用者描述借鉴「组织方式」，MUST NOT 冒充其实现细节（沿用 §7.38 口径）。

## 2. 与基线硬约束的逐条兼容

| # | 基线条文 | 落地方式 |
|---|---|---|
| 1 | `reporting-ui`「资产页按币种分组且移除单一净资产总数」 | 蓝底概览区**逐币种**渲染 `CurrencyGroup`：`资产 / 负债 / 小计` 三值。MUST NOT 把 `state.groups` 求和成一个数；`CurrencyGroup.netCents` 只在单币种组内展示 |
| 2 | `reporting-ui`「管理类页面入口固定且列表与编辑分离」 | 账户行仍是纯行 + `ListDivider(Modifier.padding(horizontal = 16.dp))`；MUST NOT 用 `Card` / 阴影。5 个 `MiniAction` 删除，操作收进 `DropdownMenu`（与基线「行操作 SHALL 收进紧凑菜单」对齐，顺带修掉现状的横排撑高） |
| 3 | 同上「全站排序交互统一」 | 保留 `rememberKeyedReorder` + `rememberReorderableLazyListState` + `commitAccountOrderAll`；`longPressDraggableHandle` 从「≡」小 Box 改挂**整格 Column 根**，去掉三道横线视觉；上移 / 下移保留在菜单作 TalkBack 兜底 |
| 4 | `ledger-transactions`「快捷账户模板替代预制账户行」 | 模板从主表单 `FilterChip` 网格移到 `AccountTemplateSheet`；仍按 `AccountTemplate.group`（支付余额 / 银行储蓄卡 / 信用负债）分组、点选自动填 `name` + `type`、`needsLast4` 的卡号后四位仍由主表单输入并落库。整块 MODIFIED 该 Requirement，MUST NOT 追加 ADDED 留两条相反要求 |
| 5 | `reporting-ui`「资产页提供账户编辑且可改期初余额与币种」 | `EditAccountDialog` 本批**零改动**；新建页 `onConfirm(name, type, initial, limit, last4)` 签名不变，`AssetsViewModel.addAccount` 零改动 |
| 6 | `money-precision` 净资产 / 可用额度 | 副文案只用 `Reconciliation.availableCredit(creditLimitCents, balanceCents)`；`LIABILITY` 且 `balanceCents == 0` 显示「无欠款」，其余沿用「可用 X」「不计入净资产」 |
| 7 | `privacy-security`「不声明网络权限」 | Manifest 当前不声明 `INTERNET`，且「未经 `project.md` 显式授权的任何联网行为仍 MUST NOT 出现」——**基线明确纠正过「把权限形式当铁律」的混淆**。账户图标复用 `CategoryIcon(name, colorHex, size)`（中性圆 + 首字，该函数本就忽略 `colorHex`）；**零新增 drawable、零新增依赖、零网络请求**。本批不申请授权，MUST NOT 引入一羽的 IconFont PNG 下载或银行 logo |
| 8 | 零表结构变更 | `AccountEntity` 字段一个不动，`AppDatabase.version = 5` 不动；不新增备注 / 隐藏 / 默认使用 / 图标字段。图标按 `name.take(1)` + `type` 在 UI 层即时计算 |

## 3. Q1A｜资产列表（低风险）

| 部位 | 现状 | 改后 |
|---|---|---|
| 顶部 | 白底 `headlineMedium("资产")` + 白底 `Card` 按币种小计 + 计数行 + 文字「+ 新增」 | 蓝底 `CategoryHeaderBlue` 身份区：标题「资产账户」+ 账户数 + 「含已归档」+「+ 新增」；下方仍逐币种渲染资产 / 负债 / 小计（白字） |
| 分组头 | 灰色 `SectionTitle(labelLarge)` | 蓝色小竖条 + 分组名 + 「N 个账户」；**不显示分组金额**（分组按 `AccountType`，跨币种，显示金额会撞约束 #1） |
| 账户行 | `name · 币种` + 金额；下方 5 个文字按钮 +「≡」+「⋮」 | `CategoryIcon` 圆 + 名称（含 `(last4)`、`· 币种`、归档标注）+ 金额右对齐 + 副文案单行；操作收进单「⋮」菜单；拖柄挂整格 |
| 分割 | `ListDivider()` | 不变 |

菜单项固定 7 项：详情 / 对账 / 编辑 / 归档或恢复 / 删除 / 上移 / 下移。
「删除」保留红色语义与既有 `deleting` 确认弹窗；「归档」沿用 `onArchived`。
整格长按拖动与「⋮」点击共存：`longPressDraggableHandle` 与 `IconButton` 分属
不同节点，长按窗口由库管理，真机待验互不误触（§8 6.30）。

## 4. Q2A｜新建账户页

`AddAccountDialog` 从 `AlertDialog` 改为：

```
Dialog(usePlatformDefaultWidth = false) → Surface(fillMaxSize, 浅灰底)
 └ Column
    ├ 蓝底 TopBar：← 关闭 | 「添加资产账户」| 保存 TextButton
    ├ Column(weight=1f, verticalScroll)
    │   ├ AssetFormSectionTitle("账户信息")
    │   ├ 白底圆角小卡：CategoryIcon(44) + OutlinedTextField(账户名称)
    │   ├ 小字「点下方『从模板选择』可快速填入名称与类型」
    │   ├ OutlinedTextField(卡号后四位，可选)
    │   ├ AssetFormSectionTitle("账户类型") + 原 chunked(2) FilterChip
    │   ├ AssetFormSectionTitle("余额") + 期初余额 + 信用额度(LIABILITY)
    │   ├ AssetFormSectionTitle("快捷模板") + 「从模板选择」入口
    │   └ errorText
    └ 底部大 Button(保存)
```

- **字段与顺序不变**：名称 / 卡号后四位 / 类型 / 期初余额 / 信用额度，
  仅用分组标题与白底小卡重新组织视觉；`placeholder` 而非浮动 `label`、
  `heightIn(min)` 而非固定 `height`（基线要求）。
- **不新增**备注 / 隐藏账户 / 默认使用 / 计入总资产开关：这些是一羽的业务字段，
  本 App 无对应 Entity 列；`EditAccountDialog` 已有「计入净资产」Switch，
  本批不动。MUST NOT 为对齐截图而升表或伪造开关。
- 图标是**只读预览**（`name.take(1)`），不是可点选的图标库；提示文字明确指向
  「从模板选择」，避免误导使用者以为能换图标。
- 模板 sheet 作为同一 `Dialog` 内的 `Box` 叠加层渲染，**MUST NOT 嵌套第二个
  `Dialog`**（避免窗口叠放与返回键歧义）。

## 5. Q3A｜模板选择底部 sheet

`AccountTemplateSheet.kt`（新只读组件，无写库路径）：

- 半透明遮罩 + 底部圆角白卡；标题「选择账户模板」，**无**魔法棒编辑图标、
  **无**「IconFont 下载 PNG」提示（一羽的在线能力，本批不申请联网授权）。
- 一行精简提示「点选后自动填入名称与类型，可再改卡号后四位」。
- 按 `AccountTemplates.ALL.groupBy { it.group }` 分组
  （支付余额 / 银行储蓄卡 / 信用负债，满足基线「按资产组与信用负债组展示」）。
- **2 列**等宽格：每格 `CategoryIcon(32)` + `label`，`Row(weight(1f))`，
  4~5 字模板名（「工行储蓄卡」5 字）在 360dp 下格宽约 156dp，单行完整可见；
  末行单数补 `Spacer(weight(1f))` 保持列对齐（沿用 §7.39 的末行占位经验）。
- 底部「自定义名称」TextButton = 关闭 sheet 回主表单，不填模板。
- `onPick(template)` 只做 `name = template.name; type = template.type`，
  其余状态（卡号后四位 / 期初余额）保留，`needsLast4` 不被强制清空。

一羽的「★ / A / B 字母索引」对应其上百个银行清单；本 App 只有 12 个模板，
按 `group` 分组已足够，**不引入字母索引**（如实登记为等价适配，非遗漏）。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 蓝底区被误读成「单一净资产总数」 | 逐币种渲染、每组显式标注 `currencyCode`；真机待验「两币种账户」场景 |
| 整格长按拖柄与「⋮」/ 行点击误触 | 拖柄只挂在行根 Column、菜单是独立 IconButton；库管理长按窗口；§8 6.30 真机待验 |
| 近全屏 `Dialog` 在小屏被键盘顶掉保存按钮 | 底部保存按钮在 `Column` 外层（非 scroll 区），`verticalScroll` 只包表单；真机待验键盘弹起 |
| 模板 sheet 与主表单状态回写 | 纯 `onPick` 回调回填两个 `mutableStateOf`，无 ViewModel 参与；点选后立即关闭 |
| 删掉 5 个 `MiniAction` 后入口不可达 | 7 项全部进 `DropdownMenu`，`onDetail/onReconcile/onEdit/onDelete/onMove/onArchived` 回调一个不删 |
| `EditAccountDialog` 仍是小弹窗、与新建页视觉不一致 | 本批明确只改新建页；不一致登记为已知边界，后续单独决策，MUST NOT 偷偷扩建 |
| 文档与代码矛盾 | 两份 delta spec 按标题整块 MODIFIED / 新增 ADDED；活文档 §7.41 记录纠错与七条约束 |

## 7. 验证

- 本地：六个静态检查、spec 结构（每条 ≥1 Scenario + RFC2119 + 标题唯一）、
  Kotlin 括号 / 字符串自查、改动文件清单与零改表核对。
- CI：push 触发六个静态检查 + 单测 + Debug APK + Release lintVital。
- 出包：`workflow_dispatch` 默认参数（R8 / lint / 建 Release / 坚果云），
  按 commit sha 逐文件 tree + content 双校验，以 Release `asset.digest` 为准。
- 真机：§8 第 59 批新增 6.30–6.34 待验，既有项整体继承；
  **CI 全绿不替代观感 / 手势 / 键盘 / 覆盖安装验收**。
