# rethink-record-entry-layout

## 为什么

v0.2.55 之后真机反馈「记一笔元素越来越多、看着杂乱、布局不美观」。
核查 `RecordEditorDialog.kt`（984 行）：首屏同时铺了模板快捷栏、类型 Tab、大金额、
优惠折叠、日期时间、标签、分类快捷、账户「当前：」+ 4 chips、收款方带框输入框
+ 常驻 40dp 联想行、描述带框输入框、备注/不计入折叠、自绘键盘、全宽保存 —— 
**12 个视觉块里只有 2 处做了折叠**，且两个 `OutlinedTextField` 用 `heightIn(min=40)`
压扁官方 56dp 下限却保留整圈描边，三块带框/带底区域叠起来近 150dp，比金额还重。

同时 Q4B 要求全 App 上动态色 + edge-to-edge。这是系列第一次改全局主题，
与第 69–70 批「按一羽定死浅灰底 / 纯白卡 / 清新蓝」的口径**直接冲突**，代价见 design。

## 改什么

- 首屏收敛为：类型 Tab → 金额 → 日期小字 → 分类 › → 默认账户按钮 → 
  收款方（无边框内联）→ 描述（无边框内联）→ 「更多 ›」→ 键盘 → 保存。
- 模板、标签、优惠/手续费、备注 + 不计入收支、收款方联想 chips 收进「更多」底部面板。
- 账户与模板从「快捷 chips 铺开」改为「显示当前值的一枚按钮 + 点击开全量选择面板」，
  沿用已有 `AccountPickerDialog` / 模板全量弹层，不新建数据路径。
- `LedgerTheme` API 31+ 启用 `dynamicLightColorScheme`，低版本回退既有静态板；
  `MainActivity` 开启 edge-to-edge，根内容用 `safeDrawingPadding()` 避开系统栏，
  `Scaffold.contentWindowInsets` 置 0 防双重 padding，记一笔 Dialog 单独避让。

## 不改什么

- 分类快捷栏（用户明确的优点：选择方便、低摩擦）、默认账户预填机制、
  上次分类/时间记忆、整数分键盘、金额真相源、优惠/手续费数据模型、标签关联表、
  统计口径、备份/CSV/Firefly 往返、全部 Room 表结构。
- 零改表（`version = 8` 不变）→ v0.2.55 直接覆盖安装。
- 不引入 `ModalBottomSheet` 等实验性 API（项目 T12 惯例用 `Dialog` + 底部对齐 Surface）。

## 验收

见 `specs/reporting-ui/spec.md` / `specs/privacy-security/spec.md` 的
10 条 Requirement（7 MODIFIED + 3 ADDED）/ 20 个 Scenario；真机清单见活文档 §7.59。
