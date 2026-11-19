# 设计：第 44 批（v0.2.26）

## 根因 1：跨 Tab 下钻工厂写死 allMonths

第 42 批把「不带时间 = 全部记录」设为全局默认时，`RecordFilter.forCategory` /
`forCounterparty` / `forAccount` 三个工厂同步写成 `allMonths = true`，
统计页的周期上下文被抹掉。修复只在 `navigateToDetail` 这一条出口注入：

- `StatsDrillRange.of(mode, yearMonth, year)` 是周期换算的单一出口：
  月 → 当月首末日、年 → 1/1~12/31、多年 → 近 5 年整段。
- `StatsUiState.drillRange` 计算属性把快照交给三类共 7 个下钻调用点，
  经 `LedgerApp` 传入 `DetailViewModel.filterByXxx`，最终落到三个工厂。
- 复用既有 `(:fromDate IS NULL OR ...)` 参数化短路，零新增 DAO 出口。
- chip 由 `RecordFilter.timeSummary()` 单一出口显示「时间：X（来自统计）」，
  `periodDisplayName` 是只读展示名，不进 SQL、不进 `activeCount()`。
- 明细页手工改筛选 / 清空筛选时清掉 `periodDisplayName`，回到「全部记录」。

回退顺序：若 `StatsDrillRange` 换算与统计页 `range()` 出现分歧，
以 `MonthRange.of` 为准；`range()` 已改为复用 `drillRange`，不存在两份逻辑。

## 根因 2：快捷账户行只展示常用前 4 个且过滤归档

`RecordEditorViewModel.load()` 其实已按 `r.fromAccountId/r.toAccountId` 精确回填 state，
问题在 UI：`AccountStrip` 的 `visible = accounts.filter { !it.isArchived }`，
`AccountQuickRow` 又 `accounts.take(QUICK_COUNT)`；记录引用的冷门 / 归档账户
根本不在可见候选里，一排 chip 没有一个选中，被误读成「账户被换了」。

修复：

- 不再过滤归档账户；`AccountQuickCandidates.select` 把当前选中项追加进候选（去重），
  常用前 4 个顺序不变；归档账户 label 加「（已归档）」。
- 行内明确写出「当前：xxx」；id 找不到时显示「原账户已删除，请重新指定」。
- `save()` 对非 null 但已不存在的账户 id 显式返回错误，不静默兜底成常用账户。
- 新建态仍按偏好 / 进程记忆 / 常用频次默认选账户，不受影响。

## 硬约束

预览/写入链路不动；恢复链路不动；统计口径唯一出口不动；
`UNIQUE(importSource, sourceId)` 幂等不动；两页下拉刷新、无障碍刷新按钮不动。

## 取舍

- 多年视图是有界的「近 5 年」，因此也带整段范围；真正无界的视图才不带时间。
- 月分组头、CSV 重建、规则三页分层等上一批成果本批不触碰。
- 单测为纯 JVM：周期换算、下钻工厂、账户候选合并；编辑态 round-trip 仍需真机复核。
