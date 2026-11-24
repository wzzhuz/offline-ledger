# 技术设计：账户排序拆分、模板正规化与回归修复

## 1. 核查事实（不以参考图推断现状）

见 `proposal.md` 表格。补充三条代码事实：

- `Reconciliation.displayTotals` 的 `totalLiabilitiesCents = abs(liabilitySum)`
  是**正值绝对值**；「负数表示」原本体现在**账户行**的负余额红字，
  不在概览区。Q1A 的「负数文本」是**纯展示层加负号**，
  `CurrencyGroup.liabilitiesCents` 仍为绝对值、不参与任何运算。
- `AccountQuickCandidates` 是纯 JVM 数据类，`QUICK_COUNT = 4`；
  `select()` 接收的 `items` 顺序直接决定「常用前 4」，
  所以只要换数据源排序即可，MUST NOT 改 `select` 的语义。
- `DatabaseModule` 已有 `.addMigrations(*Migrations.ALL)`，
  但 `ALL = emptyArray()`。使用者明确「测试数据、不考虑升级迁移」，
  故**不重新注册**；升 version 仅用于让 Room 明确拒绝旧库，
  测试期卸载重装、装前导出。

## 2. Q1A｜净资产标签 + 负债红字负数

蓝底 `AssetHeader` 每组渲染：

```
CNY                         ← 组名
净资产  8944.00 CNY         ← 白字；组名「净资产」替换原「X 小计」
资产 +8944.00   负债 -0.00  ← 负债浅红（蓝底高对比）+ "-" 前缀；资产白字
```

- **逐币种**：`state.groups.forEach` 一组一卡，MUST NOT `sumOf { it.netCents }`
  得到跨币种单一总数。
- 「净资产」三字只在**组内**出现，不违反基线
  `reporting-ui`「资产页按币种分组且移除单一净资产总数」。
- 账户行负余额红字 + 负数（`formatCents(account.balanceCents)` 原样）**不动**，
  这正是使用者认为「比一羽含义清晰」的点。

## 3. Q2A｜删除确认一定弹出

真机「没弹窗」。第 59 批把删除收进 `DropdownMenuItem` 后，
`onClick = { menu = false; onDelete() }` 让 `DropdownMenu` 的 Popup dismiss
与 `AlertDialog` 新窗口同帧，属于已知的 Compose 窗口竞争（**根因待真机复测，
不假装已确认**）。

修法：

```kotlin
var pendingDelete by remember { mutableStateOf<AccountEntity?>(null) }
// dropdown item: onClick = { menu = false; pendingDelete = account }
LaunchedEffect(menu, pendingDelete) {
    if (!menu && pendingDelete != null) {
        deleting = pendingDelete
        pendingDelete = null
    }
}
```

`deleting` 的 `AlertDialog` 加强为：标题「删除账户」、正文列出账户名 /
当前余额 / 「有流水的账户不能删除，只能归档」、红色警示文案、
确认按钮文案改「确认删除」（`ExpenseRed`）、取消按钮保留。
`vm.deleteAccount` 与「有流水只能归档」的既有事务语义零改动。

## 4. Q3A｜卡号后四位：单一真相源

| 层 | 改动 |
|---|---|
| `addAccount` | `name = name.trim()`，**删掉** `" (" + cardLast4 + ")"` 拼接；`cardLast4` 仍落库；新增 `cardKind` 落库 |
| 展示 | 统一 `displayName = account.name + (cardLast4?.let { " ($it)" } ?: "")` |
| 旧库去重 | 若 `account.name` 已以 `" (" + cardLast4 + ")"` 结尾，展示时**不再追加**；旧 `name` 不改写（零数据迁移） |
| `EditAccountDialog` | 初始化 `last4 = account.cardLast4 ?: ""`；新增输入框（4 位数字过滤）；`name` 输入框初始化时剥掉历史 ` (xxxx)` 后缀，避免编辑一次又变成双后缀 |
| 写路径 | `onConfirm` 签名 +`last4` +`cardKind` → `vm.editAccount` → `repository.updateAccountMeta` → DAO `UPDATE ... SET cardLast4 = :cardLast4, cardKind = :cardKind` |
| 基线 | `ledger-transactions`「快捷账户模板替代预制账户行」的 Scenario「显示名带 `(1234)`」整块 MODIFIED 为「`name` 不冗余拼接，展示层组合」 |

`cardLast4` 是「真相源」，`name` 只存纯名；这是 #4 双后缀的根治。

## 5. Q4A｜两套顺序 + 升表

### 数据

```kotlin
// AccountEntity 新增（Kotlin 默认值不产生 SQL DEFAULT，故 migration SQL 必须显式写）
@ColumnInfo(name = "quickOrder") val quickOrder: Int = 0,
@ColumnInfo(name = "cardKind")  val cardKind: String? = null
```

`AppDatabase version = 6`；**`Migrations.ALL` 保持 `emptyArray()`，
不写任何 ALTER 常量、不改 `MigrationContractTest`**
（使用者：现在都是测试数据，不需要考虑升级迁移）。
`SeedData` 的 accounts INSERT 列清单补 `quickOrder, cardKind`，
VALUES 补 `0, NULL` —— 这一项仍必须做，因为全新安装 / 备份恢复
走 Entity 创建路径，列清单与 Entity 不一致会直接 SQL 报错。
`BackupAccount` 加 `quickOrder: Int = 0` / `cardKind: String? = null`
（带默认值兼容旧备份），`LedgerMappers` 双向补齐。

### 两套语义

| | `sortOrder` | `quickOrder` |
|---|---|---|
| 作用域 | 资产页**同 `AccountType` 分组内** | **全部账户**，不分类型 |
| 写入 | `commitAccountOrderAll` / `moveAccount`（已有，保留） | 新增 `commitQuickOrderAll` |
| 读取 | `AccountDao.all()` `ORDER BY sortOrder, id` | 新增 `allByQuickOrder()` `ORDER BY quickOrder, id` |
| 消费 | 资产页列表 | 记一笔候选 `AccountQuickCandidates.select(..., 4)` |
| 新增账户 | 追加到该类型末尾 | 追加到全局末尾 |

`moveAccount` 第 15 批「分组内交换」的既有实现**一字不动**——
使用者已确认「列表页面排序希望根据相同银行的排序」，
分组内排序正是这个语义。拆开之后它不再污染记一笔。

### 独立「全部账户排序」页

新增路由 `account-quick-sort`（NavHost `composable`，4 Tab 不变），
入口放在资产页蓝底身份区「记一笔优先排序」文字按钮。
参考分类排序页的组织方式但更简单（单列表、无两态）：

- `LazyColumn` + `rememberReorderableLazyListState` +
  `rememberKeyedReorder`（复用 `ui/components/ReorderListSupport.kt`）
- `draftOrder` 与 `quickOrder` **完全分离**：放弃零写库，保存时
  `commitQuickOrderAll` 按 `(id, index)` 一次性写入，写完 `reload()`
- 每行显示小标志 + 账户名 + 类型标签（帮助用户知道自己在排什么）
- 上移 / 下移保留 `semantics.customActions` 作 TalkBack 兜底
- 顶部「保存 / 放弃」+ 一行提示「这里排的是记一笔里常用账户的先后顺序」
- MUST NOT 自写 `pointerInput`；MUST NOT 让该页改动 `sortOrder`

基线 `ledger-transactions`「账户类型可改与自定义排序」里
「顺序 SHALL 同步到记一笔的常用账户平铺」整块 MODIFIED 为两套解耦表述。

## 6. Q5A + Q6A｜模板清单与正规命名

`AccountTemplate` 扩字段：

```kotlin
data class AccountTemplate(
    val label: String,      // 正规全称，如「工商银行」
    val name: String,       // 回填到账户的纯名，同 label
    val type: AccountType,  // ASSET(储蓄卡) / LIABILITY(信用卡)
    val group: String,      // 支付余额 / 银行储蓄卡 / 银行信用卡 / 网络信用
    val needsLast4: Boolean,
    val cardKind: String?,  // "DEBIT" / "CREDIT" / null
    val iconKey: String?    // Q7 图标键位，资源待补
)
```

银行 21 家 × 2 卡种 = 42 条 + 支付余额 2 + 网络信用 4 = **48 条**：

- 六大行：工商银行 / 建设银行 / 农业银行 / 中国银行 / 交通银行 / 邮储银行
- 股份行：招商 / 浦发 / 中信 / 光大 / 华夏 / 民生 / 广发 / 兴业 / 平安 / 浙商
- 城商行：南京 / 北京 / 上海 / 江苏 / 宁波

`AccountTemplateSheet` 按 `group` 分 4 组（**资产组与信用负债组 MUST NOT 混排**，
基线要求），2 列等宽格。每格：中性圆 + 首字 + 正规全称 +
**12dp 小角标**（「储」/「信」）贴在图标圆右下角。

**小标志不占名称宽度**（使用者 Q6A 补充要求）：用 `Box` 叠在
`CategoryIcon` 右下角，`size(12.dp)` 圆角小方块 + `labelSmall` 以下字号单字，
名称 `Text` 仍是 `maxLines = 1` + `TextOverflow.Ellipsis`，
MUST NOT 用「· 储蓄卡」这类文字后缀挤占。

`AccountQuickItem` 加 `cardKind`，记一笔 chip 的 `leadingIcon` 用同一小标志；
`AccountQuickCandidates.labelOf` 仍只返回纯名，不加后缀。

## 7. Q7 知识产权登记（如实，不写成结论）

> **第 64 批注**：本节为第 60 批历史判断，资源已按使用者意见补齐；
> 实际来源、逐项登记与商标口径见 `assets/NOTICE` [A]~[E] 与下文第 64 批要点。

- 使用者判断：「用 Logo 指示账户归属，记账类，没有授权风险」，
  并指定 IconFont 为来源。**本批按此执行，不再劝阻**。
- 上一轮所列公开材料与该判断**不一致**：工行官网《权利声明》要求
  转载或以其它方式使用须书面许可；民生银行《法律声明》要求未经书面许可
  不得复制 / 留存 / 编辑 / 组合；Simple Icons 的 CC0 明确不覆盖商标权；
  《商标法》第 43 条规定商标使用许可须签合同并备案；
  第三方服务方口径为逐家取得许可。
- 本批**不把这些写成「已解决」或「无风险」**，只登记为
  「由使用者决策并承担」；也不复制一羽视觉资产。
- IconFont 平台图标作者与授权逐个不同 → `assets/NOTICE` MUST 逐项登记
  作者 / 来源 / 授权类型 / 传递依赖，MUST NOT 用平台名称代替逐项审计。
- **本批（第 60 批历史，已被第 64 批取代）资源目录为空**：`AccountIconCatalog` 只含 `else` 回退分支，
  三页全部中性圆 + 首字。拿到导出文件后补 `when` 分支即可，不改任何 UI 代码。

## 8. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 升表后旧库覆盖安装开库失败 | 使用者已明确测试数据、不考虑迁移；`version = 6` 让 Room 明确拒绝，**卸载重装 + 装前导出**即可，不作为缺陷 |
| `MigrationContractTest` 的 `ALL.isEmpty()` 断言 | **保持不变**，本批不注册任何 Migration，测试零改动 |
| `SeedData` 列清单与 Entity 不一致 | INSERT 补两列，静态检查 + 单测覆盖 |
| 旧备份恢复丢 `quickOrder` / `cardKind` | DTO 带默认值，Mappers 双向补齐；旧备份解析为零值=末尾顺序，可接受 |
| 编辑页把历史 ` (1122)` 后缀又写回 `name` | 初始化时剥后缀，`name` 只存纯名 |
| 两套排序互相污染 | `moveAccount` 只碰 `sortOrder`；新页只碰 `quickOrder`；两个 DAO 方法分离 |
| 48 条模板让 sheet 过长 | 按 4 组 + 2 列 + `verticalScroll`，沿用 §7.39 末行占位 |
| 小标志挤压名称 | 叠在图标右下角 12dp，名称仍单行省略 |
| 删除弹窗仍不弹 | `pendingDelete + LaunchedEffect(menu)` 解耦；真机复测列入 §8 6.36 |

## 9. 验证（10 条验收标准，由我拟）

1. 蓝底区逐币种显示「净资产」，负债红字且带负号，无跨币种单一总数。
2. 账户行负余额仍为红字负数；资产 / 负债口径与 `displayTotals` 一致。
3. 点「⋮ → 删除」一定弹出二次确认，含账户名 / 余额 / 红字警示 /「确认删除」。
4. 编辑账户可改卡号后四位并落库；列表与记一笔不再出现 `(1122)(1122)`。
5. 新建填 1234 后 `name` 为纯名、`cardLast4` 落库、展示名带一次 `(1234)`。
6. 资产页分组内排序不改变记一笔候选顺序。
7. 独立全账户排序页保存 / 放弃语义正确，放弃零写库。
8. 记一笔候选按 `quickOrder` 前 4，跨类型混排，常用可置顶。
9. 模板 48 条、4 组不混排、正规全称、12dp 小标志不挤名称。
10. 全新安装 / 卸载重装后 `SeedData` 列清单对齐、旧备份可恢复（缺两字段取默认值）、
    4 Tab 与各页零回归；**不要求覆盖安装数据不丢**（测试数据，不考虑迁移）。

## 10. 验证手段

- 本地：六个静态检查、spec 结构、Kotlin 括号 / 字符串 / 引用符号自查、
  Migration SQL 与 Entity 列名对照、改动文件清单。
- CI：push 六静态检查 + 单测 + Debug APK + Release lintVital。
- 出包：默认 `workflow_dispatch`，按 commit sha 逐文件 tree + content 双校验，
  以 Release `asset.digest` 为准。
- 真机：§8 第 60 批新增待验（观感 / 手势 / 键盘 / 模板滚动），既有项整体继承；
  **CI 全绿不替代观感 / 手势 / 键盘验收**；迁移不在验收范围。

---

## 第 61 批修订（v0.2.42）：记一笔卡号显示、图标密度、卡种可编辑

使用者在 v0.2.41 真机后给出 3 条反馈，**三条都是缺陷级修复、无方向分歧**，
按既有节奏一次性做完出包。

| # | 核查事实 | 修法 |
|---|---|---|
| 1 | `AccountQuickItem` 只有 `id/name/isArchived/cardKind`，`labelOf` 只返回纯名；`cardLast4` **根本没进候选数据**。第 60 批的「显示名带 `(1234)`」只落在资产页 `AccountCard` 的内联 `shownName` 里，记一笔候选 / 全账户选择弹层 /「当前：」/ 独立排序页各自用 `account.name` | 新增 `accountBaseLabel()` 唯一组合点（含旧库后缀去重），四处统一复用；候选映射时把组合名传进 `AccountQuickItem.name`，`select`/`labelOf`/`promoteById` 纯 JVM 契约零改动；弹层搜索键也改按组合名，输入「1122」可搜到 |
| 2 | 记一笔页面看得见的大图标是分类快捷栏 `CategoryIcon(size = 40/34)`；第 60 批刚给账户 chip 加的 12dp 小标志又撑宽了整排 | 分类快捷栏收紧到 **28/24dp**（分类管理页 5 列网格仍 40dp，两处密度各按各的场景）；`AccountKindBadge` 加 `size` 参数，记一笔传 **10dp**（6sp 字），Spacer 3→2dp，chip 文字加单行省略 |
| 3 | 两条根因叠加：① 导入建账户 `AccountEntity(...)` 没写 `cardKind` / `quickOrder`；② **`EditAccountDialog` 的 `onConfirm` 写死传 `account.cardKind`**，就算有选择器也存不回去 | 新增页与编辑页都补「不设 / 储蓄卡 / 信用卡」三态选择；编辑页传选中的 `cardKind`；导入建账户补 `quickOrder` 与保守默认 `cardKind`（`LIABILITY → CREDIT`，`ASSET` 不猜——微信零钱/支付宝/储蓄卡混在一起，猜了会误标） |

### 不改什么

`AccountQuickCandidates` 纯 JVM 契约、`cardLast4` 单一真相源、
两套排序、`Migrations.ALL` 空 + `MigrationContractTest` 零改动、
48 条模板清单、4 Tab、Q7 资源仍待补（**第 60 批历史，第 64 批已补齐**）、
零新增 Gradle 依赖、`INTERNET` 权限。

### 验收（由我拟）

1. 两张同名「工商银行」在记一笔候选 /「当前：」/ 全账户弹层分别显示 `(1122)` / `(3344)`；
2. 弹层搜「3344」命中对应卡；旧库已含后缀的账户只显示一次；
3. 分类快捷栏 28/24dp、账户小标志 10dp，分类管理网格仍 40dp；
4. 导入的 LIABILITY 带「信」、ASSET 不带标志且可在编辑页改成「储」并落库；
5. 编辑页改卡种保存后重进仍保持；记一笔 / 资产页 / 独立排序页零回归。


### 第 61 批实现要点

- `AccountDisplayLabel.kt`：`accountBaseLabel(account)` 与
  `stripLegacyCardSuffix(name, last4)` 一正一反两个纯函数，
  放 `ui.assets` 包（不进 `core`，避免 core 依赖 Room Entity，
  维持 `AccountQuickCandidates` 的纯 JVM 分层）。
- `AccountKindBadge(cardKind, modifier, size = 12)`：默认 12dp 供资产页 /
  模板 sheet / 独立排序页，记一笔唯一传 10dp。
- `RecordEditorDialog`：`CategoryIcon` 40/34→28/24；
  候选 `AccountQuickItem(it.id, accountBaseLabel(it), ...)`；
  弹层 `filter { accountBaseLabel(it).contains(query) }` 且显示组合名。
- `AssetsScreen`：`shownName` 改调 `accountBaseLabel`；
  Add/Edit 各加一行三态 FilterChip；Edit 的 `onConfirm` 传 `cardKind` 变量。
- `LedgerRepository` 导入建账户：补 `quickOrder` 与
  `cardKind = if (type == LIABILITY) "CREDIT" else null`。
- 头部过时注释（「数据侧零改动 / version = 5 / v0.2.39 可覆盖安装」）同步更正，
  MUST NOT 留着和第 60/61 批事实矛盾。

---

## 第 62 批实现要点：组合逻辑下沉到投影层之外

**问题**：第 61 批抽了 `accountBaseLabel()`，但只把 6 个页面接上；
`account.name` 在 UI 层还有 13 处直接引用，DAO 投影更是从 SQL 直接取
`fa.name`，完全绕过组合点。使用者一句「全量排查」正好点破：
**只抽函数不等于全站统一，必须把每个消费点逐一过一遍**。

### 三层结构（最终形态）

```
AccountEntity ──accountBaseLabel(account)──┐
                                           ├─→ accountLabel(name, last4)  ← 唯一实现
RecordRow / ReviewRow（DAO 只带原值）──────┘         ↑
                                         UI 各消费点一律调它
```

- `accountLabel(name: String?, last4: String?): String?`：
  空 last4 原样返回；旧 `name` 已以 ` (last4)` 结尾时不再追加；
  `name` 为 null 时返回 null（供 `?:` 链使用）。
- `accountBaseLabel(account)` = `accountLabel(account.name, account.cardLast4) ?: ""`。
- `RecordRow` / `ReviewRow` 加 `fromAccountLast4` / `toAccountLast4`
  （带默认值，无手工构造点，零破坏）；两条 SQL 各补两列。
  **不在 SQL 里 `|| ' (' || cardLast4 || ')'`** —— 那样旧库去重逻辑要写第二遍，
  且 SQL 与 Kotlin 两份必然漂移。

### 一条改动连带修好几处

`StatsViewModel` 账户排行的 `InsightData.RankItem.name` 不只是排行榜文字，
它还是 `onOpenAccountInDetail(id, row.name, ...)` →
`DetailViewModel.filterByAccount()` → `RecordFilter.accountDisplayName`
→ 明细筛选 chip 的**唯一来源**。所以改这一行，统计排行 + 下钻 chip 同时修好，
MUST NOT 再在 `StatsScreen` 或 `LedgerApp` 里拼一次。

### 数据语义红线

`RecordDao` 的 CSV 导出投影 `COALESCE(a.name,'') AS accountName`、
`LedgerRepository` 的 `CsvRow(accountName = r.accountName)`、
`BackupAccount.name` 一律**不动**。导出列是导入时的账户匹配键，
拼上 `(1122)` 会让往返把同一张卡当新账户。这条与「显示名」必须分开，
是本次全量排查最容易改错的地方。

> **第 63 批注**：上段关于 **CSV 导出** 的结论已被使用者要求显式推翻 ——
> 它只防住了「导入当新账户」，却没防住「同名两卡静默归错」。
> 现改为导出带展示名 + `cardLast4` 列、导入复合匹配。
> **加密备份 `BackupAccount.name` 仍 MUST NOT 改**（走 `id` 还原）。

### 单测

新增 `AccountDisplayLabelTest`（5 个用例，纯 JVM、不碰 Room Entity）：
无 last4 原样、追加一次、旧库后缀不重复、旧库后缀与新 last4 不同时追加、
null name 透传。锁住最容易漂移的分支。

### 真机待验

排行榜 `RankRowView` 与筛选 picker 行都是 `maxLines = 1`，
组合名变长后可能截断；统计 / 筛选 / 明细三处观感与可区分度需真机看。
CI 全绿不替代观感验收。

---

## 第 63 批实现要点：CSV 往返改成复合键

### 为什么必须下沉到 core

第 62 批的组合实现在 `ui/assets/AccountDisplayLabel.kt`，依赖 Room
`AccountEntity`。而 CSV 导出 / 导入属于 `backup.csv` 纯 JVM 层，
不能依赖 ui。若为了导出再写一份 `name + " (" + last4 + ")"`，
就会出现「UI 一份、Codec 一份、SQL 一份」三处漂移 —— 正是 §7.44
明令禁止的。所以抽 `core/AccountLabel.kt`：

```
core/accountLabel(name, last4)          ← 唯一实现（纯 JVM）
core/stripAccountCardSuffix(name,last4) ← 严格互逆
   ├─ ui.assets.accountBaseLabel(entity)   （Entity 重载）
   ├─ ui/detail/* 的 DAO 投影组合
   ├─ backup.csv.AccountCsvMatch           （解析 + 复合匹配）
   └─ LedgerRepository.exportCsvRows       （导出展示名）
```

`ui/assets/AccountDisplayLabel.kt` 只留转发，MUST NOT 再写一份逻辑；
`DetailScreen` / `RecordDetailPage` 的 import 改指 core。

### CSV 格式（13 列，第 13 列可选）

```
date,type,amount,account,categoryRoot,category,note,currencyCode,
counterparty,importSource,sourceId,description,cardLast4
```

- `account` = `accountLabel(a.name, a.cardLast4)` → 人眼可分
- `cardLast4` = `a.cardLast4` 或空 → 机器精确匹配
- `HEADER` 加列，`parseImport` / `parseRebuild` / `detectFormat`
  三处 `optional` 集合都加 `cardLast4`，**老 8~12 列文件零改动仍可导入**
- `CsvRow.cardLast4` 是末尾带默认值的参数，所有既有位置构造零破坏
- `duplicateKey()` 仍用 `accountName`，自有导出重复导入保持幂等

### 匹配算法（`AccountCsvMatch.kt`，纯 JVM）

```
parseAccountCell(cell, column):
    inline = Regex("^(.*) \((\d{4})\)$") 匹配 account 尾部
    base   = inline?.group1 ?: cell.trim()
    last4  = column(4位数字) ?: inline?.group2
    → ParsedAccount(base, last4)

resolveAccount(refs, parsed):
    hits = refs.filter {
        stripAccountCardSuffix(it.name, it.cardLast4) == parsed.name
            || it.name == parsed.name
        && (parsed.last4 == null || it.cardLast4 == parsed.last4)
    }
    hits.size == 1 → Unique(id)
    hits.isEmpty() → Missing
    else           → Ambiguous（列出候选展示名）
```

`parsed.last4 == null` 表示老 CSV / 第三方账单：不限定卡号，
但同名多条仍 MUST 报歧义。`stripAccountCardSuffix` 让旧库
`name` 已含 ` (1122)` 的账户不会被判成缺失。

### 四个消费点

| 路径 | 行为 |
|---|---|
| `findImportCandidates` | 复合匹配；缺失/歧义跳过，**不报疑似候选**（宁可不报，不挂错卡） |
| `importRecords` | 复合匹配；缺失/歧义抛 `IllegalArgumentException`，整批回滚 |
| `rebuildFromOwnCsv` | `CsvRebuildResolver.resolve(rows, List<AccountRef>, ...)`，唯一命中才 ready；写入时复用同一个 id。缺项报告沿用原始账户名（保持既有报告格式），歧义才附候选与 `cardLast4` 提示 |
| `importFirefly` | `targetName` 仍纯名，新增 `targetCardLast4`；同名多条时 `SettingsViewModel` 不自动映射，改用 isNew 让使用者在下拉里选（下拉显示展示名） |

`CsvRebuildResolver` 保留旧 `Set<String>` 重载委托到新签名，
既有单测零改动；新签名用 `AccountRef` 才能带卡号。

### 数据语义红线（本批修订版）

| 载体 | 口径 |
|---|---|
| CSV `account` + `cardLast4` | 展示名 + 复合键，**允许带卡号** |
| 加密备份 `BackupAccount.name` | **仍是纯名**，走 `id` 还原，MUST NOT 改 |
| Room `accounts.name` | **仍是纯名**，`cardLast4` 单一真相源 |
| `CsvRow.duplicateKey()` | 不变（用 `accountName`） |

### 风险

`account` 列变长后 `CsvRow.duplicateKey()` 的派生 sourceId 与老文件不同，
但 `recordDao.countDuplicate(accountId, ...)` 仍兜底，幂等性不丢。
`parseAccountCell` 只认尾部 ` (4位数字)`，账户名本身带括号但不是 4 位数字
时按字面处理，不误拆。真机需验 Excel 打开 13 列不乱码、重建后归属正确。

---

## 第 64 批实现要点：账户品牌图标实际补齐

### 从哪获取（使用者明确要的答案）

| 源 | 覆盖 | 许可 | 本批是否采用 |
|---|---|---|---|
| **icongo/bank-logos** | 422 个 SVG，含 21 家中国银行，1024×1024 viewBox | **MIT** | ✅ 采用 |
| **Simple Icons** | wechat / alipay / meituan / tiktok 等品牌 | **CC0-1.0** | ✅ 采用 |
| **IconFont（使用者最初指定）** | 海量 UGC 图标 | **逐个不同**，部分图库需打赏才商用 | ❌ 未采用 |
| Wikimedia Commons | 部分 `PD-textlogo` 银行 logo | 公有领域（著作权），商标另算 | 备选 |
| 各银行官网 / 官方 App 素材 | 官方徽记 | 需逐家书面许可 | 未取得 |

**IconFont 为什么没用**：平台不生产图标，全部由设计师上传；单图标详情页
授权标签可能是「非商业性免费」「商用需打赏（最低 3 元）」「CC0」「MIT」，
官方库与 UGC 混排；下载需登录态导出。聚合页不等于「可随包分发」。
本批拿不到登录态、更无法逐项核验，因此改用两个带明确开源许可的仓库，
并把每个 slug 逐行登记进 NOTICE。若后续改用 IconFont，
操作路径写在 `tools/account_icon_sources.md` 与 NOTICE [A2]。

### 资源处理（可复现）

```
SVG (1024² 或 24²)
  → cairosvg 512px 透明 PNG
  → getbbox() 裁掉透明边距（银行横版 logo 会被放大到撑满）
  → 等比缩到 104px
  → 叠 128×128 白色圆底 + 3px 浅灰描边
  → res/drawable-nodpi/ic_acct_<key>.png
```

白圆底放在**资源层**而不是 UI 层：`AccountIconBox` 里原本
「有 res 直接显示 Icon、无 res 走 CategoryIcon 中性圆」，
自带圆底后两者视觉尺寸一致，**UI 代码零改动**。
`tools/build_account_icons.py` 可复现全流程（构建期工具，App 不执行）。

### 27 个键位 / 26 个文件

- 21 家银行：icbc / ccb / abc / boc / bocom / psbc / cmb / spdb / citic /
  ceb / hxb / cmbc / cgb / cib / pab / czb / njcb / bob / bosc / jsb / nbcb
- 6 个支付与网络信用：wechat / alipay / huabei / meituan / douyin / jdbaitiao
- `huabei` 复用 `ic_acct_alipay`（花呗是支付宝旗下，母品牌图形指示归属），
  所以 27 个键位只有 26 个 PNG。
- `douyin` 用 Simple Icons 的 `tiktok` 音符（同品牌图形）。
- `jdbaitiao` 无 CC0 / 明确授权图形 → **本作品原创**红底白「白」字，
  MUST NOT 临摹京东商标；拿到授权直接替换同名 PNG。
- `drawableFor()` 仍是编译期 `when` 显式常量表（T14），
  MUST NOT 改成运行时反射。

### 授权口径（如实，不写成「无风险」）

使用者判断是「用 Logo 仅指示账户归属、记账类场景」。
2026 年修订《商标法》第 73 条第 3 款：仅为指示用途、适用对象、应用场景
或表明真实来源而使用相关注册商标，专用权人无权禁止正当使用，
但容易导致混淆的除外 —— 这为指示性使用提供了依据。
但 **MIT / CC0 只覆盖 SVG 代码与本项目原创绘制，不覆盖商标权**；
Simple Icons 官方免责明确写「CC0 不意味着项目内所有 logo 也是 CC0，
每个 logo 都是所属公司的商标」；工行 / 民生官网权利声明仍保留
「未经书面许可不得复制、留存、编辑」等表述，本批未取得逐家书面授权。
以上差异在 `assets/NOTICE` [C] 如实并列、标注由使用者决策并承担。

### 已知限制（真机待验）

银行 logo 是**横版含中文文字**，34dp 以下主要靠徽记颜色与形状区分，
文字看不清；需要更清晰 MUST 换成纯徽记版本并重新登记。
Simple Icons 的满幅色块（支付宝蓝 / 美团黄）与银行横版 logo
在圆内的占幅不同，观感需真机看。所有资源随包分发、
零 `INTERNET`、零新增 Gradle 依赖、零表结构变更。

---

## 第 65 批实现要点：横版 → 纯徽记

### 为什么不是「换个文件」这么简单

`icongo/bank-logos` 每个银行有两个文件：
- `xxx.svg`：**横版**，徽记 + 中文/英文行名（第 64 批用的，34dp 下糊成细条）
- `xxx-rect.svg`：**方形纯徽记**，无行名（第 65 批改用）

但直接换源仍不够：`-rect.svg` 的第一个 path 是
`d="M0 0m224 0l576..." fill="#FFFFFF"` 的 1024 圆角纯白底，
它让 `getbbox()` 返回整张画布，徽记无法裁切放大。
**MUST 先删该 path，再 getbbox → 缩到 104px → 叠白圆底**。
第 64 批 19 家徽记偏小的真正原因就在这里，本批一并修正，
所以本批不只是「换源」，是「换源 + 修正裁切」。

### 三处特殊处理

| 键位 | 问题 | 处理 |
|---|---|---|
| `ceb` | `cebbank-rect.svg` = 黄色 E 徽记 + 紫色 `ank` 字样 | 剔除 `#6B1785` 紫色 path，只留黄色徽记 |
| `pab` | `pingan-rect.svg` = 橙色方块内「中国平安」文字标，裁不出纯图形 | 本作品原创：橙色圆角块 + 白色双弧/竖线 |
| `jdbaitiao` | 无纯图形官方徽记，第 64 批是红底白「白」字 | 本作品原创：红色圆角块 + 三条白横杠（顺手去掉中文） |

三家都登记为「原创 / 非官方 logo」，MUST NOT 写成已获授权。

### 范围边界

使用者原话是「**银行** logo 选择不带中文的」。4 个 Simple Icons
（wechat / alipay / meituan / douyin）是满幅方块标，不是横版行名细条，
34dp 下清晰；`alipay`「支」与 `meituan`「美团」是官方方块文字标。
本批**不扩张范围**到支付类，在 NOTICE [65-A]/[65-C] 明文写出该保留，
避免「悄悄改了使用者没要求的东西」。若要一并去掉，需下一批明确指令。

### 为什么 UI 零改动

圆底放在资源层、键位与 `R.drawable` 引用都不变 ——
第 64 批承诺的「替换资源只覆盖同名 PNG」在本批真实验证了一次：
`AccountIconCatalog.kt` 与三页 Compose 代码**一个字节都不用改**，
只替换 26 个 PNG 内容 + 构建脚本 + NOTICE + 文档。

