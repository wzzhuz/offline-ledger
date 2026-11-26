# simplify-account-picker-sheet

## 为什么

v0.2.62 真机（截图 6 是**一羽的账户弹层，不是本 App**）两条反馈：

1. 「账户切换参考一羽用一个小图标呢，没必要大量按钮占空间」。
2. 「叉号按钮是否也不需要，系统的返回操作手势就可以了，账户搜索应该也不需要，没那么多账户」。

对照 v0.2.62：账户选择器顶部堆了 **4 行控件**——「标题 + 叉号」/
「当前：xxx」/ 两枚「全局排序 · 按类型分组」chip / 搜索框。确实比一羽重。

## 先认账：我上一轮把 T12 说成了全仓禁令

阶段 0 的对齐回复里我写「T12 惯例否决 `ModalBottomSheet`，破例要引实验 API」。
**清点后这是不准确的**：`ModalBottomSheet` 在本仓**早有先例且已归档**——
`ui/stats/DrillDownDialog.kt` 自第 69 批起就是
`@OptIn(ExperimentalMaterial3Api::class) + ModalBottomSheet`，
`category-stats` 主 spec 明写「统计页 SHALL 提供 `StatsDrillSheet`
（`ModalBottomSheet`）」，`scripts/check_stats_merge.py` 还断言了它。
第 77 / 80 / 83 批反复写的「不用实验 API」只约束
**记一笔低频项 sheet**（`BottomPickerSheet` / `TemplateQuickPick`），
是局部惯例，不是全仓铁律。这两个文件本批一字不动，其禁令继续有效。

所以使用者选 **1B 不是破例，是回到项目已有先例**；
同时 `ModalBottomSheet` 自带点遮罩 / 下滑 / 系统返回三种关闭路径，
恰好把我上轮说的「删叉号必须自己补遮罩 clickable」这一必做项省掉了。
这又是一次 §7.51「借鉴竞品先清点我们已有什么」。

## 使用者四项拍板

| # | 问题 | 拍板 |
|---|---|---|
| 1 | 一个小图标怎么切换 | **点按钮在两种布局间轮换**，图标随模式变（可选，本批采纳） |
| 2 | 叉号 / 搜索 | **都删**（叉号靠系统返回，搜索因账户不多不需要） |
| 3 | 壳 | **1B 用 `ModalBottomSheet`**（要下滑手势） |
| 4 | 「当前：xxx」/「不指定」 | **保留当前值**；「不指定」**按推荐收进网格第一格** |

## 改什么

- `ui/components/AccountSelectSheet.kt` 重写壳：
  `Dialog + 底部对齐 Surface` → `ModalBottomSheet(containerColor = CardSurface)`，
  内容用 `LazyColumn` + `chunked(2)` 两列网格（与 `StatsDrillSheet` 同款滚动口径，
  MUST NOT 用 `Column + verticalScroll` 与下滑手势抢滚动）。
- 顶部压到 **1 行控件**：标题 + 一枚 24dp 轮换图标 + 下一行「当前：xxx」。
  删除 `Icons.Filled.Close` 叉号、`OutlinedTextField` 搜索框、两枚 `FilterChip`。
  图标显示**当前模式**：`GLOBAL` = 既有 `Icons.Filled.List`（三横线），
  `GROUPED` = `Canvas` 自绘 2×2 圆角方块，零新增 drawable / 零新增依赖。
- 「不指定账户 / 全部账户」由独立整行横幅改为**同款 tile 收进网格第一格**
  （灰圆「—」+ 标签）；分组视图里排在所有 `AccountType` 组标题之前。
- 删搜索后同名卡的唯一区分手段变成次要行「尾号 xxxx」，
  `cardLast4` / `accountSecondaryLabel` MUST NOT 被连带删除；
  `FilterSheet.accountOptions` / `displayLabel` 仍保留作 `PickRow` 只读展示名。
- 版本号 0.2.63；`check_record_layout.py` 第 83 批断言换成第 84 批口径
  （新增/残留双向校验 + 归档态「delta 必须已合并」）；
  活文档 §7.67 / §8、`project.md` 登记。
- **8 条 MODIFIED + 1 条 ADDED**：删一个搜索框的真实代价是
  4 条 Requirement 里都写着「账户搜索 / 输入 3344 能搜到」，必须同步收口，
  否则又是一轮规格腐烂。

## 不改什么

- `LedgerPrefs.accountPickerLayout` 跨启动持久化、两套排序口径
  （`quickOrder` / `sortOrder`）、44dp `AccountIconBox`、次要小字、不显示余额、
  `allowNone`、4 个调用点容器 —— 全部零改动。
- `BottomPickerSheet`（备注 / 优惠 / 标签）与 `TemplateQuickPick` 一字不动，
  仍是 `Dialog + Surface`，其 T12 禁令继续有效；
  `check_record_layout.py` 对 `TemplateQuickPick.kt` 的 `ModalBottomSheet` 禁止断言保留。
- `CategorySelectDialog` 本体零改动（`check_firefly_mapping.py` 锁着版式）。
- **零改表**：`AppDatabase.version` 仍 9、`Migrations.ALL` 仍空、备份 DTO 零改动，
  **可直接覆盖 v0.2.62**。零新增 Gradle 依赖 / 零 `INTERNET` / 零运行时权限 /
  零新增 drawable。4 Tab、分类快捷栏、转账两卡、聚合 SQL、CSV/Firefly 往返零改动。
- 截图里一羽的「余额数字」「长按拖动排序提示」「点此切换显示余额」「右上角 +
  新增账户」「蓝色顶栏」**都不做**：余额是第 82 批明确否过的，拖拽 §3.1 否过，
  `+` 会牵到导航与数据刷新（扩面），蓝底顶栏第 79 批已改中性白卡。
  按 §7.41 只借「标题栏一枚小图标 + 两列网格」的组织方式。

## 对齐结论（阶段 0，五项）

| # | 项 | 结论 |
|---|---|---|
| 1 | 要解决什么 | 弹层顶部 4 行控件压到 1 行；两 chip → 一枚轮换图标；删叉号与搜索；壳换 `ModalBottomSheet` 拿下滑手势；同步 8 条 spec 与脚本 |
| 2 | 改什么 / 不改什么 | 见上两节 |
| 3 | 关键约束 | 三条铁律；T12 只是记一笔低频项 sheet 的局部惯例（统计页先例已核实）；删搜索后 `cardLast4` MUST 保留作同名卡区分；MUST NOT 自己补遮罩 clickable；MUST NOT 用 `LazyVerticalGrid` / 长按拖拽；零新增依赖与图标资源；沙盒不编译，走 Actions |
| 4 | 验收标准 | 12 静态检查 + 单测 + Debug + Release lintVital 全绿；真机确认图标轮换与状态自解释、三种关闭路径都可用、模板/周期/明细三处选择器一致、转账锁定目标零回归、坚果云 job step success |
| 5 | 被否决方案 | 保留两枚小 chip 只缩字（仍占一行）；图标用新 drawable（撞「不新增图标资源」）；`Dialog + 自绘遮罩 clickable`（拿不到下滑手势，且与 sheet scrim 冲突时多余）；照抄余额 / 长按提示 / 新增 + / 蓝底顶栏；删 `FilterSheet.accountOptions`（会丢展示名）；`LazyVerticalGrid`（§7.3） |

## 验收

`specs/reporting-ui/spec.md`（8 MODIFIED + 1 ADDED）；
真机清单见活文档 §7.67 的 §8。本批可直接覆盖 v0.2.62。
