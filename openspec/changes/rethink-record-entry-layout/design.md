# design · rethink-record-entry-layout

## 决策 1：分层不是删功能，是降视觉重量

| 层 | 元素 | 处置 |
|---|---|---|
| 必须常驻 | 类型 Tab / 金额 / 分类快捷栏 / 保存 | 不动，基线「保持低摩擦」 |
| 有默认值 | 日期时间（今天）、默认账户、上次分类 | 账户收敛成**一枚按钮**显示当前值 |
| 高频但不该用大框 | 收款方、描述 | **保留首屏**，改无边框内联 |
| 低频 | 模板、标签、优惠/手续费、备注+不计入、收款方联想 | 收进「更多」面板 |
| 已折叠 | 优惠、备注 | 并入「更多」，不再各自占一行入口 |

**描述刻意不折叠**：`RecordDao` 的明细主标题取 `COALESCE(description, counterparty)`，
藏起来之后用户想不起来填，主标题会静默退化回收款方 —— 那是改数据展示口径，不是排版。

**日期小字保留**：基线明写「日期与时间 SHALL 合并为紧随金额后的无标签可点副行」，
它本就只有一行小字，不占视觉重量；移进面板反而让「今天」不可见。

## 决策 2：无边框内联不换组件

`OutlinedTextField` 的 `colors` 全设 `Color.Transparent`（focused/unfocused/disabled
border + container），保留 placeholder / singleLine / 一键清空 / `heightIn(min=40)`
既有逻辑，只去掉整圈描边，行间用既有 `FormDivider()` 细线分隔。
**不换 `BasicTextField`**（会丢 placeholder 与 trailingIcon 的 M3 实现），
也不用 filled（官方 56dp，反而更高）。官方对多字段表单的说法是 outlined 视觉强调
低于 filled、适合降低存在感；同区域 MUST NOT 混用两种变体 —— 本批全站统一一种。

## 决策 3：账户/模板 = 单按钮 + 既有全量面板

- `LedgerPrefs.defaultAccountId` 与设置页「默认账户」机制**早已存在**，
  `RecordEditorViewModel.load()` 按 `偏好 → 上次账户 → 首个未归档` 预填。
  之前 UI 仍把「当前：X」+ 4 个 chips 全铺开，等于把已有默认值又展示一遍。
- `AccountStrip` 内部已有 `AccountPickerDialog`（搜索含卡号后缀），
  只隐藏 chips 行、保留按钮与弹层，**零新数据路径**。
- `TemplateQuickPickBar` 已有 `showAll` 全量弹层，同样只留「模板 ›」按钮。
- 一枚按钮仍带卡号后四位 + 12dp 储/信角标（第 60/61 批口径不变）。

## 决策 4：「更多」用 Dialog + 底部对齐 Surface，不用实验 API

`ModalBottomSheet` 属 `ExperimentalMaterial3Api`。项目历史在 T12 因实验性布局 API
踩过真机坑，分类/筛选全量选择也都用 `Dialog` + `Surface` 居中/底部对齐。
本批沿用：`Dialog` 内 `Box(Alignment.BottomCenter)` + `fillMaxWidth` Surface + 圆角，
`onDismissRequest` 关闭。观感接近底部 sheet，编译面最小。
入口「更多 ›」在编辑态有备注/优惠/标签值时显示摘要，旧内容绝不不可见。

## 决策 5：Q4B 动态色与 edge-to-edge 的代价（必须显式登记）

**冲突**：第 69–70 批按一羽把全局色板定死为 `F2F3F7` 底 / 纯白卡 / `5B7CFA` 清新蓝。
动态色启用后 `MaterialTheme.colorScheme.primary` 随壁纸变化；而
`LedgerColors.Primary` 是 object 常量、被统计蓝底 / `CategoryHeaderBlue` 等
**80+ 处 composition 外直接引用**，不会跟着变 → 全 App 会出现**两套蓝**。
`LedgerColors.ExpenseRed / IncomeGreen` 与配色反转开关本来就是独立 object，不受影响。

本批按 Q4B 执行，但保留**一行回退**：`LedgerTheme(dynamicColor: Boolean = true)`，
MainActivity 传 true；改为 false 即回到第 70 批静态板，零其他改动。
两套蓝的观感、深浅壁纸下的对比度、以及每页 insets 是否遮挡，全部留真机 `[ ]`。

**edge-to-edge**：`WindowCompat.setDecorFitsSystemWindows(window, false)` +
系统栏透明；根 `Surface` 仍 `fillMaxSize`（背景铺满），内容包
`Box(Modifier.safeDrawingPadding())`；`LedgerApp` 的 `Scaffold` 设
`contentWindowInsets = WindowInsets(0,0,0,0)` 防与外层双重 padding；
记一笔是独立 Dialog Window，根 Column 单独加 `statusBarsPadding() +
navigationBarsPadding()`。官方口径：内容画到系统栏下、可点区用 insets 避开。

## 决策 6：会撞的活跃 change 与基线（不默默绕）

主 spec 有 **9 条**记一笔条文，本批 MODIFIED 整块替换 7 条，不留两份相反要求：
「布局紧凑且首屏可见备注与收款方」「统一表单网格与有上限的快捷区」
「模板快捷区两行铺开且不横向滚动」「各表单行高度量级一致」
「低频项收起且输入框不撑高边框」「提供模板快选且两步内完成」
「提供收款方与常用收款方补全」。

`record-category-picker-and-root-select` 活跃 change **正在 MODIFIED 同一条**
「记一笔布局紧凑且首屏可见备注与收款方」，且写着「首屏仍 MUST 一屏无需上滑可见」。
本批不修改该活跃 change，只在 design 登记：归档时以本批 MODIFIED 为最终语义，
其「分类行带 ›、点一级即按一级记」的补述继续有效。
另与 `ff3-description-field`（描述规则）、`add-record-tags` / `refine-tag-filter-chips`
（标签）交叉，但都不动它们的文件。

**一处基线腐烂如实登记**：主 spec「模板 SHALL 点一下直接生成流水并关闭」，
代码 `applyTemplate()` 与组件注释都是**只预填草稿、不保存**。历史不一致，
本批不借机改语义，只把「两步」改述为「两次点击内完成预填」。

## 风险

- Q4B 是全 App 变色 + insets，23+ 个含 Card/背景/顶栏的文件观感都会变，
  CI 全绿不替代逐页真机。
- 「更多」面板把低频项从首屏拿走后，**首次可发现性**要靠真机看。
- 零改表 → 覆盖安装，与 v0.2.54 的卸载重装不同。
