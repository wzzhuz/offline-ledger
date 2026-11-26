# design · simplify-account-picker-sheet

## 决策 0：清点更正 —— T12 是局部惯例，不是全仓禁令

阶段 0 我写「T12 否决 `ModalBottomSheet`，1B 要破例」。核查后发现说错了：

| 证据 | 内容 |
|---|---|
| `ui/stats/DrillDownDialog.kt:15-17,47,54` | 第 69 批起 `@OptIn(ExperimentalMaterial3Api::class)` + `ModalBottomSheet(onDismissRequest = …)`，内容是 `Column { LazyColumn }` |
| `openspec/specs/category-stats/spec.md` | 「统计页 SHALL 提供 `StatsDrillSheet`（`ModalBottomSheet`）」 |
| `scripts/check_stats_merge.py` | 断言 `ModalBottomSheet` / `StatsDrillSheet` / `ExperimentalMaterial3Api` 三个串存在 |
| 全仓 `@OptIn(ExperimentalMaterial3Api::class)` | 5 个文件已在用（AssetsScreen / DetailScreen / FilterSheet / RecordEditorDialog / DrillDownDialog） |

所以第 77 / 80 / 83 批反复写的「不用实验 API」约束的是
**记一笔低频项 sheet**（`BottomPickerSheet` / `TemplateQuickPick`），
主 spec 里那条挂在「记一笔低频项收起且输入框不撑高边框」下面。
本批把该条**口径精确化**（写明作用域），而不是删掉禁令 —— 那两个文件一字不动。

这是 §7.51 的又一次实例：借竞品交互前先清点本仓已有什么。
同时 `check_record_layout.py` 对 `TemplateQuickPick.kt` 的
`ModalBottomSheet` 禁止断言**保留**，避免有人以后顺手把它也换掉。

## 决策 1：用 `ModalBottomSheet`，正好省掉自绘遮罩

使用者 1B 要的是**下滑关闭手势**。原壳是
`Dialog + Box(fillMaxSize) + 底部 Surface`，遮罩那层没挂 `clickable`，
只有系统返回和「选中自动关」两条路径，删叉号会真的关不掉。

`ModalBottomSheet` 自带：scrim 点击关闭、swipe-to-dismiss、系统返回，
外加默认 drag handle（一羽截图里也有那道灰横条，正好提示可下滑）。
因此：

- MUST NOT 再自己补遮罩 `clickable` —— 会与 sheet 的 scrim 抢事件。
- 关闭路径统一交给 `onDismissRequest`；点 tile 时 `onSelect(id)` 后主动
  `onDismiss()` 一次（sheet 会自行收起），MUST NOT 重复调两次。
- `containerColor = LedgerColors.CardSurface`（第 79 批起刻意固定不随动态色漂），
  保留默认 drag handle，不传自定义 `sheetState`（与 `StatsDrillSheet` 先例一致，
  少一个版本敏感参数）。

`ModalBottomSheet` 嵌在 `Dialog` 里（明细 `FilterSheet`、记一笔
`RecordEditorDialog`、模板页）是否有问题：项目已有 `FilterSheet`（Dialog）
内嵌 `CategorySelectDialog`（Dialog）的先例，两个独立 window 叠加是允许的。
本批不改 4 个调用点容器，真机验嵌套时的 scrim 观感。

## 决策 2：图标轮换，图标显示当前模式

使用者第 1 项「点按钮在两种之间轮换，同时图标可以用不同的来区分不同模式
（可选）」——可选部分本批采纳，因为**单枚图标没有文字时，图标本身就是
唯一的状态反馈**，不做反而分不清当前在哪种视图。

- `GLOBAL` → `Icons.Filled.List`（material-icons-core，全仓已用）
- `GROUPED` → `LayoutGridGlyph()`：`Canvas` 画 4 个带 3dp 圆角的方块，
  线宽/填充与 `Icons.Filled.List` 同为实心 `OnSurface`
- `contentDescription` 写「当前 X，点击切换到 Y」，TalkBack 可读
- 点一下 `onLayoutChange(if (GLOBAL) GROUPED else GLOBAL)`，零菜单零二次点击

**零新增资源**：主 spec 有「MUST NOT 为对齐截图新增 Gradle 依赖或图标资源」，
`Canvas` 自绘不是 drawable 资源，`Icons.Filled.List` 是既有依赖。
MUST NOT 引 `material-icons-extended`（拿 GridView）、MUST NOT 加新 XML。

## 决策 3：删搜索框的连锁代价 = 8 条 MODIFIED

使用者「账户没那么多」成立，但账户搜索在 spec 里不是孤立的：

| Requirement | 过期内容 | 处理 |
|---|---|---|
| 记一笔输入框紧凑且支持一键清空 | 「**四个**输入框」「账户搜索 SHALL 支持筛选」「四处处处可一键清空」 | 收为三个，删两处 |
| 记一笔常用候选…带卡种标志 | 「弹层搜索键」「输入 3344 能搜到第二张卡」 | 改为由「尾号」显示区分 |
| 筛选与统计的账户显示名与全站一致 | 「picker 的搜索按 label 匹配」「筛选 picker 搜索框输入 3344」 | 改为 tile 次要行区分 |
| 记一笔采用统一表单网格… | Scenario「点开两列 sheet 可按 1122 搜索」 | 改为尾号区分 |
| 账户选择面板展示品牌图标与卡种 | 「搜索 SHALL 按 accountBaseLabel 过滤」「搜索命中跨组平铺」 | 删除，加 `cardLast4` MUST 保留 |
| 账户选择器提供全局与分组两种布局 | 「两枚分段」「搜索跨组平铺 Scenario」「MUST NOT 引入 ModalBottomSheet」 | 三处都改 |
| 模板与周期规则复用通用… | Scenario「含双布局分段」 | 改为单枚图标 |
| 记一笔低频项收起… | 「MUST NOT 引入 ExperimentalMaterial3Api 的 ModalBottomSheet」 | **精确化作用域**，不删禁令 |

**关键：同名银行卡必须仍可区分。** 删搜索后唯一手段是每格次要行
`尾号 1122 · 储蓄卡 · CNY`，由 `accountSecondaryLabel` 生成。
所以 `cardLast4` / `accountSecondaryLabel` / `accountBaseLabel`（「当前：」用）
MUST NOT 被连带删掉；`FilterSheet.accountOptions` 仍用于 `PickRow` 的
只读展示名（第 62 批带卡号口径），也保留。这是第 84 批静态断言里
显式检查 `cardLast4` 的原因。

## 决策 4：「不指定」收进网格第一格

使用者第 3 项按推荐来。原来 `NoneTile` 是 `fillMaxWidth()` 的独立横幅，
比真实账户 tile 多占一行。本批引入
`sealed class AccountSlot { Item(account) / None(label) }`，
让 `chunked(2)` 自然把它排进网格：

- **全局视图**：slots = `[None] + 全部账户`，第一行是「不指定 | 第一个账户」，
  与一羽截图「无账户 | 中国银行」同构。
- **分组视图**：`None` 单独占第一行第一格（第二格 `Spacer`），
  排在所有 `AccountType` 组标题之前 —— 它不属于任何类型组，
  硬塞进第一个组标题下会语义错乱。如实登记：这与全局视图的排布略有差异，
  是「不属于任何组」的正确表达，不是漏对齐。

tile 样式与真实账户完全同款（56dp 高、12dp 圆角、选中 12% 主色底），
图标位换成灰圆「—」。MUST NOT 再出现独立整行横幅。

## 决策 5：滚动口径跟 `StatsDrillSheet`

`ModalBottomSheet` 的内容是 `ColumnScope`。若沿用第 83 批的
`Column + verticalScroll`，在无限高度约束下会与 swipe-to-dismiss 抢滚动，
且 sheet 需测量内容高度。改用 `LazyColumn` + `items(chunked(2))`，
组标题用 `item {}` 插入 —— 与 `DrillDownDialog.kt:69` 完全一致。
MUST NOT 用 `LazyVerticalGrid`（§7.3 / T12）、
MUST NOT 新做长按拖拽（§3.1，排序仍走 `account-quick-sort` 独立页）。

## 考虑过的其他方案

| 方案 | 为什么否决 |
|---|---|
| 两枚小 chip 只缩字 / 换成两个图标按钮 | 仍占一行，不满足「一个小图标」 |
| 图标固定不变、点开两项小菜单 | 多一次点击，且看不出当前状态 |
| 保留叉号，只删搜索 | 使用者明确「叉号也不需要」 |
| `Dialog + 自绘遮罩 clickable` | 拿不到下滑手势（1B 的核心诉求） |
| 引 `material-icons-extended` 拿 GridView | 新增依赖，撞主 spec「不新增图标资源」 |
| 新加 `ic_grid.xml` drawable | 同上 |
| 删 `FilterSheet.accountOptions` | 会连带丢掉 `PickRow` 展示名 |
| 照抄余额 / 长按提示 / + 新增 / 蓝底顶栏 | 第 82 / §3.1 / 扩面 / 第 79 批已否 |
| 把 `BottomPickerSheet` 也一起换成 `ModalBottomSheet` | 扩面，且会撞 `check_record_layout.py` 对 `TemplateQuickPick` 的断言；本批只收窄账户选择器 |

## 风险与真机待验

- **下滑手势与 `LazyColumn` 嵌套滚动**：列表到顶时手势是否顺利交给 sheet。
- **sheet 嵌在 `Dialog` 内**：明细 / 记一笔 / 模板三处 scrim 是否叠两层、
  点遮罩关的是哪一层。
- 单枚图标无文字时，首次使用能否看懂「点一下会变布局」。
- 分组视图里「不指定」独占第一行第一格（第二格空）是否显得空。
- 删搜索后，同名两卡靠「尾号」是否一眼可分。
- drag handle + 标题栏 + 「当前：」是否仍比一羽多一行。
- 全部列在活文档 §7.67 的 §8，保持 `[ ]`；CI 全绿不替代观感验收。

---

## 第 85 批修订注记（修 bug，保留上方原文）

**上方「决策 1」中「不传自定义 `sheetState`（与 `StatsDrillSheet` 先例一致，
少一个版本敏感参数）」这句已被真机推翻，作废。**

真机反馈（v0.2.63）：「上滑展开之后再下滑收不起来，页面卡着一上一下」；
以及「在上滑展开全屏情况下，用系统返回手势是回到半屏，不是关闭，其他 App 是关闭」。

根因：`ModalBottomSheet` 默认 `sheetState` 是
`Hidden / PartiallyExpanded / Expanded` 三态。打开先停半屏，上滑到
`Expanded` 后下滑只回 `PartiallyExpanded`（不关闭），系统返回同理；
再叠加内部 `LazyColumn` 在 nested scroll 里抢下滑事件，就出现拉扯。
`StatsDrillSheet` 内容短、从没触发这个冲突，把它当先例外推是误判。

修法：显式 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`
传给 `ModalBottomSheet`，去掉中间档。打开即全展开，
列表到顶后下滑直接 dismiss、系统返回直接 dismiss。
两个反馈是**同一个根因**，一次修复同时解决，不需要两轮。

- 本批按 `my-app-workflow` 步骤 1「修 bug（恢复原本行为）→ 不需要建 change，
  直接做」执行，**没有新建 change 四件套**；但按「改完代码不更新 spec」禁令，
  已同步 `reporting-ui` 主 spec 的实现约束与两个回归 Scenario、静态脚本断言、
  活文档 §7.67 回填与本节修订注记。
- 归档 delta 的 spec.md **保持原样**作历史快照（行为承诺「下滑可关闭」
  本来就是对的，是实现没做到）；当前生效语义以主 spec 第 85 批修订为准。
- 踩坑清单命中：活文档 §7.7 第 17 批「父子手势竞争」、
  §1505「同方向嵌套滚动 + 高度不确定」。上表「风险与真机待验」里
  「下滑手势与 `LazyColumn` 嵌套滚动是否顺」一项至此有结论：
  **默认三态下不顺，跳过半屏态后顺**。
- 代价如实登记：弹层不再有半屏态，一打开即接近全屏，比参考图占得高。
  使用者核心诉求是「下滑收得起 / 返回直接关」，这是唯一稳解；
  若真机嫌全屏太满，另走一轮调观感，MUST NOT 为此退回默认三态。
