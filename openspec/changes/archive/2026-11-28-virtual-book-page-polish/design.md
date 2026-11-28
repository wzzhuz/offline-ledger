# 第 93 批 Design：台账页重做与明细可见性收口

## 决策 1：撤回「主账本明细默认排除台账记录」

第 91 批为了「像独立账本」在 `pagedFiltered` 加了 `NOT EXISTS (... vt.name LIKE '台账:%')`。
清点后发现这是**过度设计**：

- `excludeFromStats = true` 已经让统计 / 预算 / 趋势 / 日期头合计过滤掉这些流水，
  月度收入不虚高的目标本来就达成了；
- 明细列表的 `pagedFiltered` 从来**不过滤** `excludeFromStats`，只返回该列供 UI 标记；
- 再排除一层，而台账页当时又没有编辑入口 → 这些流水在全 App **没有任何可改可删路径**。

真机反馈「只在主记录显示」正是数据失联的表现。修法：删掉排除段，`pagedFiltered`
回到零改动，更简单、也更符合「虚拟账本只是视图」的定位。

> 这是本项目第三次「注释写对了、实现做过头」（第 91 批 design 写「切换不带录入归属」，
> 代码却在明细 SQL 里做了互斥隔离）。写进踩坑清单 §7.73。

## 决策 2：编辑 / 删除复用既有路径，不新写事务

| 动作 | 复用 | 说明 |
|---|---|---|
| 编辑 | `openEditor(recordId)` → 全屏 `RecordEditorDialog` | 编辑态表单已包含金额、对方、标签、不计入开关；**不带任何预填** |
| 删除 | `LedgerRepository.deleteRecord(id)` | 已有移入回收站 + 标签快照 + 余额重算，MUST NOT 新开写余额分支 |

删除事务跑在 `LocalLifecycleOwner.current.lifecycleScope`，**不放进 `LaunchedEffect`
或弹层局部 `rememberCoroutineScope`**——第 89 批「The coroutine scope left the composition」
就是 `LaunchedEffect` 内部改自己的 key 导致事务被取消后伪装成业务错误。
删除成功后 `reload += 1` 触发本地重载；编辑保存由 LedgerApp 既有 `onSaved → refreshTick++` 覆盖。

**预期行为**：编辑态里若用户去掉 `台账:` 标签，该笔会离开台账视图，属正常。

## 决策 3：台账页五块结构（不做独立统计页）

```
标题行（← / 名称，自己 statusBarsPadding）
总览卡：总收 / 总还 / 待还 / 笔数      ← 由 partyTotals 在页内汇总，零新 SQL
分段 chip：往来汇总 | 流水
内容区：  按人卡片   或   按 localDate 分组 + 当日收/还小计 + 可点可删流水行
底部固定录入区：记收礼 | 记还礼        ← 不随 LazyColumn 滚动
```

总览数据复用 `partyTotals` 的结果求和，**不给 `StatsScreen` 加台账 scope**：
统计模块全是全库口径，分类占比 / 趋势 / 对方排行都要带一层 scope 才对，
成本接近真多账本，且与「视图级虚拟账本」定位冲突。礼金场景真正要回答的是
「这次总共收了多少 / 还了多少 / 还欠多少」，页内总览 + 按人汇总已经覆盖。

标题行自己加 `statusBarsPadding()`：二级页 Scaffold topBar 不渲染，
第 91 批漏了这层避让，顺手补齐（第 77 批 edge-to-edge 口径）。

## 决策 4：切换器从常驻条压成明细页一枚图标按钮

- `VirtualBookSwitcher` 去掉 `Surface` / `statusBarsPadding` / 说明文字，
  变成 `IconButton(36dp) + DropdownMenu`；口径注释「不携带录入归属」保留。
- LedgerApp 的 topBar 只留一层与状态栏等高的背景薄层做 edge-to-edge 避让
  （4 个主 Tab 都受益，内容区比第 91 批多回一整行）。
- 入口通过 `virtualBookEntry: @Composable () -> Unit` slot 挂进
  `MonthOverviewCard` 第一行（月份卡标题行）末尾；资产 / 统计 / 设置不显示。
- 说明文字「虚拟账本只是视图，记账仍进主账本」移到设置页与台账页总览卡内。

360dp 窄屏估算：←48 + 标题(weight, ellipsis) + 月/年 chip×2(~88) + →48 + 新按钮36
+ 左右 padding 32 ≈ 252 固定开销，标题仍有约 100dp 可显示「2026年11月 ▾」。

## 考虑过的其他方案（否决）

| 方案 | 否决理由 |
|---|---|
| 保留明细排除，只在台账页补编辑 / 删除 | 数据仍在主账本失联，治标不治本；「视图」不该让流水从账本里消失 |
| 给统计模块加台账 scope、做独立统计页 | 接近真多账本成本，余额与统计割裂，违背「零 bookId」 |
| 复刻整套明细页（Paging + 筛选 + 详情页）到台账 | 重复一套 UI 与状态机，维护成本翻倍，且台账只有几十笔 |
| 删除用 `rememberCoroutineScope()` | 第 89 批 composition scope 取消坑 |
| 去掉常驻切换器、改从设置页进 | 违背用户要的「左上角账本切换」形态 |
| 把切换器压成 32dp chip 但仍在 4 Tab 常驻 | 仍占一整行，未解决「太占位置」 |

## 影响面核查

- `check_virtual_books.py` 原断言「主账本明细缺少台账标签排除条件」→ **反转为 MUST NOT 排除**；
  另加台账页五块能力、LifecycleOwner scope、明细 slot 等断言。
- 主 spec「主账本明细默认排除台账记录」标题改为「台账流水在主账本明细可见且带不计入标记」；
  该 Requirement 由本系列 change 自身引入，无活跃 change 以它做 MODIFIED 匹配基准，改标题安全。
- 第 92 批 Requirement 的 Scenario「主账本明细默认不显示这笔」同步改为「可见且带标记」。
- 零改表（version 9）、零改备份 DTO / CSV、零新增依赖 / 权限 / `INTERNET`。
