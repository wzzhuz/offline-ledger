# 设计决策（第 26 批 · v0.2.10）

## 1. 分类下钻：复用明细页，不复制列表
现状 `DrillDownDialog` 在弹窗里放一个 `LazyColumn` 展示 100 条流水，与明细页的
分页 / 下拉 / 筛选 / 编辑全部割裂。第 11 批已证伪「操作型浮层塞长列表」。
本批把「查看该分类流水」改到明细 Tab：`StatsScreen` 新增
`onOpenCategoryInDetail(id, isRoot, yearMonth, currencyCode)`，`LedgerApp` 调
`detailVm.filterByCategory(...)` 后 `navigate("detail")`。筛选状态仍提升到
Activity 作用域（第 5 批），切 Tab 不丢。

**边界（如实记录）**：跨 Tab 只带分类 + 币种 + `yearMonth`，不带 `fromDate/toDate`
跨月条件。原因是 `DetailViewModel.reload()` 的月概览只按 `yearMonth` 聚合，若带
整年 from/to 会让「月概览」与「列表」口径不一致——那是既有设计，不在本批扩大。
年度 / 多年视图下钻落到统计页当前 `yearMonth`，用户可在筛选面板改范围。
一级 → 二级构成的弹窗保留（它是选择器，不是「查看明细」），点二级才跳明细。

## 2. 年度 12 点数值：关键点常驻 + 交错 + 单条引导线
360dp 屏去掉内边距后每点约 27dp，`12,345.67` 放不下；12 根虚线必然交叉。
本批：`LineChart` 新增 `staggerLabels`（奇偶上下错开）与 `guideIndex`（只画选中点
一条竖向虚线），`StatsScreen` 在 YEAR / MULTI_YEAR 传 `compactCents` 简化值，
保留 `ChartData.annotationIndices` 只标峰值 / 末点 / 选中点。点按不再弹
AlertDialog，改图下随选更新的汇总文字 +「查看该年」/「清除选中」。

**未做**：浮层气泡（竖屏 27dp 间距下易遮挡数据点，且触摸区难做宽）；12 点全量
常驻数值。完整 12 月数值表仍在横屏放大页。

## 3. 备注原样 + 来源字段
第 23 批已给 `records` / `trash` 加 `importSource` + `sourceId` 与
`UNIQUE(importSource, sourceId)`，CSV 明文与备份 DTO 也已带字段（**本批查证确认，
上一轮门禁里「要补 DTO」的担心是多余的**）。仍残留的是 `FireflyCsvCodec` 把
description / 分组 / 标签 / 原备注 / 预算 / `ff3:` 拼进 note。本批改为
`note = r.notes` 逐字不改，其余无对应字段的元数据不再塞进备注，在导入结果中明示。
流水详情弹窗显示「来源：firefly-iii · journal xxx」。

**历史数据**：已导入的 1916 行备注带旧前缀，改代码不洗历史；按测试阶段口径
重装重导即可，不写一次性迁移。

## 4. 回退顺序（动手前先写）
1. `LineChart` 新参数默认值不改既有调用点；若 `PathEffect` 解析失败 → 去掉虚线只留交错。
2. `StatsScreen` 回调全部给默认空实现；若跨 Tab 编译报错 → 退回保留 DrillDownDialog。
3. 备注去前缀导致单测红 → 同步改测试断言为「note 不含 ff3: 且 journalId 独立」。
