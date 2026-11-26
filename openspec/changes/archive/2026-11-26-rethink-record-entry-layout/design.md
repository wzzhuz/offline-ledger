# design · rethink-record-entry-layout

> **第 80 批整块修订**：下方决策 1–6 是第 77 批当时的推理，保留作为历史背景；
> 其中「低频项收进更多面板」「账户收敛成首屏一枚按钮」「联想移进面板」三条
> **已被真机反馈推翻**，最终形态以文末「第 80 批修订」为准。
> 历史记录写完即定型，不回改旧段落（活文档 §4.3 文档腐化教训）。

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


---

# 第 80 批修订（v0.2.59）：撤「更多」层，改一羽式 chips 直达

## 决策 7：层数比空白更伤低摩擦

真机对照一羽 4 张截图（键盘上方 chips / 模板 sheet / 账户 sheet / 不计入 sheet）：
一羽不是「把低频项藏起来」，而是**入口常驻一行 chips、内容按需弹出**。
第 77 批把 6 类低频项塞进一个「更多」面板，等于给每次使用都加一层：
- 模板 3～4 次点击（更多 → 模板 › → 全量 AlertDialog → 关面板），
  而本 change 原 delta 承诺 2 次；
- 标签 3 次（更多 → 标签行 → TagPickerDialog → 确认）；
- 账户 2 次，但因默认账户预填，绝大多数时候**根本不需要点**，
  却仍占着首屏一整行 `FormRow`。

**空间账**：删掉 `MoreEntry` 一行（约 38dp）抵掉 chips 一行（约 52dp 含 padding），
净增约 14dp 且 chips 在滚动表单区之外；两行平铺会净增约 40dp，
可能重演第 7 批真机反馈「要上滑才看到描述」。故按使用者选的 A 只做单行。

## 决策 8：五枚怎么定，不计入去哪

| chip | 弹层内容 | 为什么是它 |
|---|---|---|
| 账户 | 两列网格 + 余额 + 搜索；转账态顶部分段 | 默认账户低频，但必须能看到当前是谁 |
| 模板 | 一行一条：名称 / 备注 / 预填金额 | 2 次点击完成预填，比居中文字列表信息量高 |
| 备注 | 备注输入 + 不计入开关 | 两者本就同一 `FormRow`，合并最自然 |
| 标签 | 既有 `TagPickerDialog` | 已是完整弹窗，不包一层 sheet |
| 优惠 / 手续费 | 既有 `ExtraAmountSection`（sheet 内默认展开） | 复用，零新数据路径 |

「不计入收支」一羽是独立 chip（其弹层有两个开关），本项目只有
`excludeFromStats` 一个，放备注 sheet 里不占第 6 枚。
**一羽只借交互，不借业务字段**：截图里的「报销 / 应付」本项目没有对应开关，
MUST NOT 为对齐截图而新增。

## 决策 9：sheet 壳与账户交互

`BottomPickerSheet` 提为同包 public（模板组件复用），仍是
`Dialog` + `Box(Alignment.BottomCenter)` + `Surface(RoundedCornerShape(top 20dp))`
+ 32×4dp 拖拽把手 + `navigationBarsPadding()`，
MUST NOT 引 `ModalBottomSheet`（ExperimentalMaterial3Api，T12 惯例）。
账户 sheet 两列网格显示 `Cents(balanceCents).toYuanString()` 与币种、
搜索沿用 `accountBaseLabel`（含卡号后缀），沿用 `setFromAccount/setToAccount`。
**不做长按拖拽排序**：踩坑清单 §3.1 记过手势消费链坑，排序仍走既有独立页
`account-quick-sort`，不新增第 5 个 Tab。

## 决策 10：联想必须和输入框同屏

第 77 批 Q3A 把联想移进 MorePanel，等于让用户「先离开输入区再找候选」。
本批把 `counterparties` / `counterpartySuggestions` 两条既有 Flow 原样接回
**收款方输入框正下方**一条 40dp 内联条，用 `onFocusChanged` + 内容判空控制出现，
空输入给常用、有输入给模糊联想，互斥共用一条。
MUST NOT 常驻（否则重演第 77 批要解决的「常驻 40dp 区域」问题），
MUST NOT 塞进 chip 弹层（多一次点击且看不到输入上下文）。
ViewModel / DAO 零改动。

## 决策 11：编辑态旧值不能因为收起而丢

第 77 批用「更多 ›」一行摘要管全部旧值；撤掉这行后，摘要必须分散到各 chip：
备注前 3 字 + 「·不计入」标记、`#N`、优惠数值、账户名。
否则编辑一条带备注/优惠/标签的旧流水时，用户会以为旧值被清空。

## 考虑过的其他方案（第 80 批）

| 方案 | 为什么否决 |
|---|---|
| 6 项两行全平铺（一羽图 1 的形态） | 净增约 40dp，首屏「无需上滑」有风险；留作真机复验后的备选 |
| 联想常驻键盘上方 chip | 脱离输入上下文、占空间，且只在输入时有用 |
| 账户 sheet 内新做长按拖拽排序 | §3.1 手势消费链坑；已有独立排序页 |
| `ModalBottomSheet` | ExperimentalMaterial3Api，项目 T12 惯例不用 |
| 先归档第 77 批、再开新 change | 两层 delta 会 MODIFIED 同一批 Requirement，打架；使用者选「并进同一个 change 一起归档」 |
| 保留首屏账户行 + chips 里也放账户 | 两个入口重复，违反「同区域不混用」，且没解决账户降频 |

## 风险与真机待验

- 单行 5 枚在窄屏（≤360dp）每枚约 64dp，长账户名会 ellipsis；真机看是否够宽。
- chips 首次可发现性、sheet 打开时键盘/保存状态保留、转账分段是否清楚，
  全部列在活文档 §7.63 的 §8 待验清单，保持 `[ ]`。
- 账户从首屏移走后，用户第一眼看不到当前账户 —— 靠 chip 文案兜住，真机确认。
- 零改表 → 覆盖安装；CI 全绿不替代观感验收。
