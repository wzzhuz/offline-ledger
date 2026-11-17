# polish-settings-pages · Design（第 12 批：设置 Tab 管理页收口）

## 0. 本批定位

使用者体验 v0.1.8 后反馈「设置 Tab 里分类管理 / 模板管理 / 回收站」：

1. 分类列表和新增分类在同一页，分类越多新增入口越往下移，局部留白多
2. 模板管理有同样的通用问题
3. 回收站多条记录之间留白很大

并追加「**顺带检查其他没提到的页面**」。

这是 `docs/页面优化经验与避坑指南.md` 挂进 `AGENTS.md` 必读位置后，
**第二次外推到非记一笔页面**（第一次是第 11 批筛选面板，见指南 §7.1）。
本批的核心价值不只是修好 3 页，而是验证「老页面 MUST 全局搜同类写法」这条规则：
前面 11 批已经统一过的写法，在阶段 2–6 就没再碰过的设置类页面里**成片残留**。

---

## 1. 根因

### 1.1 「新增入口越推越下」= 列表与表单塞进同一个 `Column(verticalScroll)`

| 页 | 文件 | 结构 |
|---|---|---|
| 分类管理 | `ui/settings/CategoryManagerDialog.kt` | 说明 → **全部一级/二级列表**（每个 root + children）→ 改名框 → 新增一级表单 → 新增二级表单（含父级 10 个 `TextButton` 全量平铺）→ 归档区 |
| 模板管理 | `ui/templates/TemplateManagerDialog.kt` | 说明 → **全部模板/规则列表** → 新增标题 → `QuickEntryForm`（6 输入框 + 3 下拉 + chips + Switch + Button） |
| 预算管理 | `ui/budget/BudgetManagerDialog.kt` | **同样结构**（使用者未点名，全局扫描发现） |

三页都是 `Column(Modifier.verticalScroll(rememberScrollState()))` 全量 compose，
表单排在列表之后。10 个一级 + 20 个二级 = 30 行 × 每行 4 个 `TextButton`，
父级选择又 10 行 → 60+ 行；模板 20 条再叠 ~10 个表单控件。

**一处注释纠正**：分类管理写「用普通 Column 而非嵌套 LazyColumn，避免无限高度与滚动冲突」——
这只对**嵌套**成立。顶层用 `LazyColumn` 无无限高度问题，反而避免 30+ 行全量 compose。

### 1.2 「局部留白多」= 48dp `TextButton` 撑高 + 多层 padding

M3 `TextButton` 默认 48dp 高，文字只有 20dp → 每行上下凭空 28dp 空白；
叠外层 `padding(vertical=8.dp)`、每 root `padding(vertical=2.dp)`、
各段标题 `padding(top=12/16.dp)`、父级 10 个 `TextButton` 平铺 = 480dp 纯空白感。

### 1.3 回收站：右侧竖排按钮撑高整行

`TrashScreen` 已是 `LazyColumn`（比前两页好），但每行左列最多 4 行文字，
右列 **2 个竖排 `TextButton`** = 96dp，把行高硬撑到 ~120dp，
再加 `padding(vertical=10.dp)`×2 与 1dp 分割线 → 6.73 寸一屏只看得到约 5 条。
第 5 批补齐的「时间 / 对方 / 分类 / 账户 / 备注」信息 MUST 保留，靠重排不靠删。

---

## 2. 全局扫描结果（36 个 UI 文件）

按指南 §6.1「实际读，不凭印象」对全部 `ui/**/*.kt` 做了同类写法扫描：

| 老写法 | 残留位置 | 已统一于 |
|---|---|---|
| `● ` 文字前缀表选中（**9 处**） | `AssetsScreen` 3（含已归档 toggle + 账户类型 ×2）、`StatsScreen` 2（趋势指标 chip）、`CategoryManagerDialog` 1（父级选择）、`TemplateManagerDialog` 3（类型 chips / 周期 chips） | 第 8 / 11 批已换 `FilterChip` 底色 |
| 浮动 `label = { Text(...) }`（**28 处**） | 管理类 18：`CategoryManager` 4、`TemplateManager` 6、`BudgetManager` 2、`AssetsScreen` 6；操作型浮层 10：`BackupPasswordDialog` 1、`AppLockScreen` 1、`ReconcileDialog` 2、`ReviewDialog` 3、`DetailScreen` 报销 1、`SettingsPages` PIN + 阈值 2 | 第 7 / 8 / 10 批 → `placeholder` + `bodyMedium` + `heightIn(min=40)` |
| `DropdownMenu` 不限高（**5 处**） | `TemplateManager.DropdownSelector`、`BudgetManager`、`SettingsPages` 币种 / 默认账户（`AssetsScreen` 的账户类型是 chips、币种下拉仅 3 项，无需限高） | 第 6 批分类管理反馈过同问题 |
| `Column(verticalScroll)` 装长列表 | 4 个管理页 + `AssetsScreen` / `StatsScreen` / `SettingsScreen` / `RecordEditorDialog` | 第 11 批筛选面板 |
| 固定 `height(1.dp/6.dp/32.dp)` 分割线/色块 | 多处 | **非输入框，合法**，不在本批改动范围（指南 §7 只禁「输入框固定 height」） |

### 2.1 范围决策（避免改动面无谓爆炸）

**本批改**：
- 4 个管理页布局重排（分类 / 模板 / 预算 / 回收站）
- 全仓 `● ` 前缀清零（9 处，统一 `FilterChip` 底色或 `selected` 态）
- 管理类 18 处浮动 `label` → `placeholder` + `bodyMedium` + `heightIn(min=40)`
- `TemplateManager.DropdownSelector` 限高 `heightIn(max=320.dp)` + 搜索

**保留但记录**（写进指南 §7.1，避免下批又当漏项）：
操作型浮层的一次性短表单 10 处浮动 `label` 不改。理由：弹起即用即关、
不构成列表网格、不与其它行并排，「浮动 label 撑高」问题不显著；
改它会把改动面扩散到 6 个额外页面，收益不抵风险。`LedgerApp.kt:132` 的
`label = { Text(tab.label) }` 是 `NavigationBarItem` 的 label，与输入框无关，不改。

---

## 3. 方案与回退顺序

### 3.1 新增入口（推荐 A，已由使用者确认）

TopBar 右侧固定「+」`IconButton`，点开**同一 composable 内切换编辑视图**
（标题变「新增分类 / 编辑模板」、返回回列表）。列表页只剩纯 `LazyColumn`。

- 零 NavHost 路由改动，与「分组列表 → 二级页」心智一致
- 入口永远固定可见，MUST NOT 随条目数下移
- 编辑既有项 MUST 回填全部字段，保存后回列表并刷新

`SettingsPageTopBar` 扩展 `onAdd: (() -> Unit)? = null` 可选参数，
既不影响其余 6 个二级页，也不改任何路由。

### 3.2 列表行压缩（推荐 A）

管理页每行 4 个 `TextButton` 收进一个「⋯」`IconButton` + `DropdownMenu`
（上移 / 下移 / 改名 / 删除 / 恢复），行高压到 ~44dp；
回收站右侧两按钮改横排紧凑按钮，4 行文字重排成「主行（时间·类型·金额·对方）
+ 副行（分类·账户·备注）」，`padding(vertical)` 10→6dp，保留细分割线，信息一条不丢。

### 3.3 三级回退顺序（每次只改一处单独推送）

1. **首选**：页内 `editingMode` 状态切换 + `LazyColumn` + `SettingsPageTopBar(onAdd)`
2. 若 `LazyColumn` 与「展开/折叠已归档」状态冲突 → 退回 `Column(verticalScroll)`
   但把列表拆成 `items` 惰性块，只让编辑视图独占全屏
3. 若 `DropdownMenu` 在 `LazyColumn` item 里报「无限高度」→ 退回行内 2 个
   32dp `IconButton`（改名 / 删除）+ 长按进更多，绝不恢复 4 个 48dp `TextButton`

---

## 4. 已证伪、不得重试（指南 §7）

- 固定 `Modifier.height()` 压输入框 → 裁 placeholder（第 8 批）
- `String` 版 `OutlinedTextField` 加 `contentPadding` → 重载解析陷阱（第 8 批）
- `BasicTextField + DecorationBox` 自绘 → 接管边框/焦点/清空，风险不值当
- `● ` 文字前缀、`chunked(3)` 多列平分、单行横滚选项（第 8 / 11 批）
- 可点区压到 40dp 以下（三次「降噪过头」回摆）
- 删除已补齐的信息字段来换紧凑

---

## 5. 规格债处理（指南 §5：MODIFIED 整块替换，不追加 ADDED）

扫描发现 **2 处规格腐烂**：

1. `reporting-ui`「模板与规则管理收纳进设置页且不改导航」仍写
   「**Dialog SHALL 提供**」「**THEN 弹出 Dialog**」，但第 5 批已升独立全屏页。
   → MODIFIED：改成独立页 + 顶部固定新增入口 + 列表与编辑视图分离。
2. `reporting-ui`「设置页收纳全部设置项且不改导航」的 Scenario 写
   「**可见备份、安全、偏好、分类管理、健康检查与回收站六组**」，
   实际是 4 组（数据与安全 / 偏好 / 账本）+ 关于。
   → MODIFIED：改成「4 组分组列表 + 关于」，并补「管理页入口固定」Scenario。

另 **1 条 ADDED**：描述「管理类页面入口固定 / 懒加载列表 / 紧凑行 /
全仓选中态与输入框写法统一」，作为后续页面可直接复用的长期约束。

---

## 6. 验收

1. 30 个分类 / 20 个模板时「+」仍在顶部可见、列表可滚、无需上滑找新增
2. 点「+」进编辑视图；编辑既有项内容完整回填，保存后列表刷新
3. 10 条回收站记录一屏可见 ≥5 条且时间 / 对方 / 分类 / 账户 / 备注齐全
4. 排序 / 改名 / 删除 / 归档 / 恢复 / 启用开关逐项可用
5. 全仓 `● ` 前缀归零；管理类浮动 `label` 归零；模板分类下拉不限高且可搜索
6. 归档后校验 specs 无「Dialog」与实际独立页矛盾的要求
7. CI 全绿、版本 0.1.9、远端 sha PASS、无 token 明文、Manifest 仍无 `INTERNET`

---

## 7. 边界

零表结构变更 → **覆盖安装即可，不用卸载**。`AppDatabase` 仍 `version = 1`、
备份格式 / `schemaVersion` / 4 Tab / NavHost 路由图 / DAO 与写方法 / 金额铁律 /
统计口径 / `release.yml` 全部不动。R8 启动与 SQLCipher 开库已由连续装机实际通过；
其余真机未验清单由指南 §8 继承，不因本批出包改写。

## 8. 实测回填（第 12 批）

| 项 | 结果 |
|---|---|
| 前批归档 | `compact-filter-sheet` 3 ADDED + 4 MODIFIED 合并 + move + 校验 PASS（221 Req / 384 Scenario） |
| 代码 commit | `e15db82beb16`（25 更新 + 6 删除：归档 move + 4 管理页 + 全仓写法统一 + 新 change 四件套 + specs 合并） |
| push CI | `43867480282` **success**，17 步全绿，**首轮 Kotlin 编译零错误** |
| 六个静态检查 | 本地与 CI 全 PASS；迁移 2 项按「稳定版前暂缓」为记录性提示 |
| release | `43868660461` **success**，23 步绿 / 5 步按 input 跳过（plain、AAB、创建 Release 未触发；lint 开启） |
| 坚果云 | 步骤 `completed success`；metrics 名 `nsfalse` 表示 `skipped=false`（认证 / 建目录 / PUT 均未失败）。**HEAD 落盘校验只写日志不中断，不当作已验证 HEAD 200** |
| 制品 | `offline-ledger-release-v0.1.9` 12,202,522 B；`mapping-v0.1.9` 3,447,379 B；`metrics-v0.1.9-p-m-nsfalse` 271 B；debug APK 23,950,946 B |
| 版本号 | `defaultVersionName = 0.1.9`，产物名带版本，不同名覆盖旧包 |
| 零结构变更 | `AppDatabase` 仍 `version = 1`，无 Migration、无新增依赖 / 权限，Manifest 仍无 `INTERNET` → 覆盖安装即可 |

**首轮零编译错误**（连续第 3 批）：动码前先写 §1 根因 / §2 全局扫描 / §3.3 三级回退顺序；
4 个管理页整体重写时只复用已核实的 ViewModel 方法签名；推送前用「逐 OutlinedTextField 块」
与「逐文件 import 粗扫」两条独立路径自检，并按 §4 走第三条路径复核校验器误报。
新写文件花括号 / 圆括号均平衡，无 `diag/` 残留。

**顺带检查「其他页面」的最终结论**：全仓 36 个 UI 文件扫描后，
`● ` 文字前缀 9 处全部清零（含首次扫描漏掉的 4 处字符串插值）；
管理类浮动 `label` 18 处改 `placeholder`；操作型浮层 10 处**有意保留**并写进 §2.1 与指南 §7.2；
预算页虽未被点名但因同结构一并收口。范围决策记录已并入活文档 §7.2。

**未验证（必须由使用者装机确认）**：6.73 寸上 30 分类 / 20 模板时「+」是否固定可见、
编辑视图进出与既有项回填、回收站一屏可见条数、`⋯` 菜单与 `FilterChip` 底色观感。
累积未验清单（BiometricPrompt / SAF / Excel CSV / 5 万条切月耗时等）由活文档 §8 继承，不因本批出包改写。
