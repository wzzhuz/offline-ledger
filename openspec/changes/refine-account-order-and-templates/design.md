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
- **本批资源目录为空**：`AccountIconCatalog` 只含 `else` 回退分支，
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
