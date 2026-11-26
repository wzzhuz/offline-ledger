# refine-tag-filter-chips

## 为什么

v0.2.54 落地账单多标签后，明细页收到三条反馈：

1. 明细行里的标签用 Material3 `AssistChip`，32dp 高 + 内边距 + 允许换行，
   与主标题、金额同一视觉重量，「太大、太抢戏」。
2. 筛选面板里账户 / 分类 / 币种都走「整行点击 → 带搜索单选弹层」，
   只有标签是一行 `OutlinedTextField` 要手输，既不方便，也违反了基线
   「筛选面板统一网格与紧凑输入框」中「同区域 MUST NOT 混用 filled 与 outlined 输入框」。
3. 顶部筛选提示是 `drillLabel` 的三选一 `when` 分支：只认分类 / 交易对方 / 账户，
   且互斥；其余条件（类型、金额、币种、备注、描述、标签、时间）完全没有 chip。
   那枚 ✕ 还是拼在 label 字符串里的，点一下走 `clearFilter()` 清空**全部**筛选，
   不是清掉自己——「有的显示带叉、有的没有」的根因。

顺带查出一个计数 bug：`RecordFilter.activeCount()` 的 `listOf(...)` 忘了
第 75 批新增的 `tagName`，按标签筛选时「筛选 (N)」不加 1。

## 改什么

- 明细行标签改为轻量行内标记，单行不换行、最多 2 个 + 「+N」；详情页可展开全量。
- 筛选面板标签条件改 `PickRow` + 复用既有带搜索单选弹层，选项取 `TagDao.active()`，
  含「全部标签」；面板内 `produceState` 直查，不扩 `FilterSheet` 构造参数。
- 顶部改为统一一行 chips：每个生效条件各一枚「条件名：取值」，各自独立 ✕，
  点 ✕ **只清自己**；横向滚动不挤压列表，末尾留「清空」。
- `RecordFilter` 新增 `FilterField` 枚举与 `clearField()` 单一联动出口，
  `activeCount()` 补 `tagName`；`DetailViewModel` 增 `clearFilterField()`。

## 不改什么

- 零改表（`version = 8` 不动）→ v0.2.54 可直接覆盖安装。
- `pagedFiltered` 的 `EXISTS (:tagName)` 单选语义不变，MUST NOT 改成 `IN`。
- `timeSummary()` / `showMonthOverview()` / 统计下钻三条只读展示名 / 分页 SQL 全部不动。
- 备份、CSV、Firefly、回收站四条标签往返零改动；预制分类零改动。
- 统计页标签卡、标签管理页、记一笔多选弹层零改动。

## 验收

见 `specs/reporting-ui/spec.md` 的 4 条 Requirement / 10 个 Scenario，
以及 `docs/页面优化经验与避坑指南.md` §7.58 真机待验清单。
