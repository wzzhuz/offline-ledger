# 第 45 批设计

## 根因
1. `RecordFilter.allMonths` 一个布尔同时承担「视图模式」与「是否有筛选」，v0.2.24 把默认值改成 `true` 后，连 `currentMonth()` / `clearFilter()` 一起放开成全库，标题与列表脱节。
2. 编辑态账户列表按「常用频次」排序，当前记录引用的冷门 / 归档账户被挤到后面。
3. `DetailScreen` 点击行先弹动作菜单，编辑 / 删除 / 报销都在第二层。

## 决策
### 1. 语义裁决单一出口
- `RecordFilter` 新增 `DetailGranularity { MONTH, YEAR }`，`periodBounds()` 按粒度给闭区间（月用 `MonthRange.of`，年用 `YYYY0101..YYYY1231`）。
- `resolveAllMonths(requested)` 纯函数裁决：有显式范围 → 尊重面板；无范围 + `activeCount() > 0` → 全部时间；无范围 + 无条件 → 回到按周期窄视图。
- `currentMonth()` 与 `clearFilter()` 置 `allMonths = false`；三个跨 Tab 下钻工厂保持 `allMonths = fromDate == null && toDate == null`（有范围生效、无界视图仍全库），统计下钻带周期不回退。
- **取舍**：`RecordFilter(ym)` 直接构造的默认仍为 `allMonths = true`，以保留第 42 批既有单测语义；真机入口统一走 `currentMonth()`。显式构造代表「调用方已明确要全库」。
- 顶部加「月 / 年」小切换，标题用 `periodLabel(anchorYm)` 单一出口；月份条仍是定位锚点，不过滤。

### 2. 编辑态置顶
- `AccountQuickCandidates.promoteIds` / `promoteText` 只重排列表，不改选中项；第 44 批的按 id 精确回填、归档可见、已删除保存前拦截全部保留。
- `RecordEditorViewModel.load()` 仅在编辑分支应用置顶，记一笔新建态仍按常用优先。

### 3. 详情页
- 新增 `RecordDetailPage`（只读全字段 + 顶栏锁 / 删除 + 报销操作区）。
- `DetailScreen` 用 `actionRow` 作二级页状态：`actionRow == null` 渲染原列表，非空时列表整体让位、详情页占满屏。**不新增 NavHost 路由、不动 4 Tab**。
- 锁 = 进入编辑的视觉开关（非加密 / 非权限），复用既有全屏 `RecordEditorDialog` 表单，不重写编辑器。
- 删除走既有 `pendingDelete` 二次确认，报销走既有 `settleRow` + `remainingSettlement` / `settle` 事务，末尾重算不回退。

## 第 46 批补充（v0.2.28）：切月刷新 / chip 同行 / 系统返回

### 根因
1. 第 42 批把月份降成「只定位、不过滤」时 `locateMonth()` 只写 `_anchorMonth` 与 `_overview`，**没写回 `_filter.yearMonth`**；`effectiveFromDate()` 的 `periodBounds()` 永远按初始月算 → 无筛选时标题变了列表不动，锚点又找不到目标月数据 → 弹「继续下滑后再点一次」。第 45 批让无筛选默认按周期后，这个缺口被放大。
2. 月 / 年 chip 是独立第二行 `Row`，标题行只有左右箭头，多占一屏高度。
3. 详情页是 `actionRow` 二级页、没走 NavHost，`OnBackPressedDispatcher` 不知道内部还开着一层 → 系统 back（全面屏左滑）直接收掉 Activity。

### 决策
- `locateMonth()` 同步 `_overview.value.copy(yearMonth)` 后写回 `_filter.copy(yearMonth, revision+1)`：无筛选时 `flatMapLatest` 重建 Pager、`periodBounds()` 按新月重算；有筛选无范围时 `effective*` 仍 null、全库重查后锚点定位。只改两个字段，筛选条件一条不丢。`overview` 先同步，避免 `_filter.collect → reload()` 读到旧月份把概览拉回去。
- 锚点 `LaunchedEffect(anchorMonth, itemCount, refresh)`：只在 `LoadState.NotLoading` 且分页数据到达后滚到该月首条；**删掉 Snackbar 兜底**，目标月无记录时列表本身为空即反馈，空月在 Picker 已灰显。不新造「继续下滑」交互、不拦 predictive back。
- 月 / 年 `FilterChip` 移入标题 Row，标题 `weight(1f)` + `maxLines=1` + ellipsis，删掉独占的第二行。
- `BackHandler(enabled = true, onBack = closeDetail)` 挂在详情页分支，`closeDetail` 单一出口同时供顶部关闭图标与「返回列表」按钮使用；编辑表单是外部 Dialog，其 `onDismiss` 自行处理未保存改动，本批不重复拦截、不接管返回动画。

### 取舍（如实）
- 切月现在会重建 Pager（第 42 批曾刻意避免），但这是「切月即刷新」的必要代价；`revision` 递增走既有 `flatMapLatest` 通道，零新增 SQL 出口。
- 只读态左滑直回列表；编辑态的「放弃修改」确认由编辑 Dialog 自身负责，不在 `BackHandler` 里再叠一层弹窗，避免嵌套返回。
- 零改表（`AppDatabase.version = 5` 未变）→ v0.2.27 上覆盖安装即可。

### 回退顺序（第 46 批）
1. 切月刷新异常 → `locateMonth()` 退回只写 `_anchorMonth` / `_overview`，锚点恢复旧「已加载项」逻辑（但会复发本批症状，需同步回滚 spec）。
2. `BackHandler` 误拦系统返回 → 摘掉该行，仅保留 `closeDetail` 统一按钮出口。
3. chip 同行挤压 → 退回独立第二行，或把 `FilterChip` 换更窄的 `TextButton`。

## 回退顺序
1. 详情页异常 → 恢复 `actionRow` 的动作菜单 Dialog（`RecordDetailPage` 可直接不调用）。
2. 粒度语义异常 → `currentMonth()` / `clearFilter()` 改回 `allMonths = true`，`resolveAllMonths` 恒返回 `true`。
3. 置顶异常 → `load()` 里去掉两次 promote 调用，排序恢复第 44 批行为。

## 不引
第三方日期库、表达式引擎、真加密锁、NavHost 路由、任何 DAO / 统计口径改动。

## 实测回填（第 45 批）

- 代码 commit：`657ac195d74febf8bb7812bc541017b69cd3077f`
  （首轮 `149ca6c9024b3d257dd0d838111ff0eba61e1e5c` 的 `promoteIds` 类型不匹配，
  修复为泛型 `promoteById`；中间 `69dddc7134d0` 为临时诊断 workflow，已清理）
- push CI：`44661745120` **success** 17/17（首轮 `44660731626` 失败于「运行单元测试」）
- release：`44662596613` **success**，28 步 = 23 绿 / 5 跳过，「上传到坚果云」success；
  并发 `44662933616` 已 cancelled（dispatch 回连 502 重试导致，未重复上传）
- 制品：`offline-ledger-release-v0.2.27` **12,405,979 B**、
  `offline-ledger-mapping-v0.2.27` **3,826,420 B**、
  `offline-ledger-metrics-v0.2.27-p-m-nsfalse` **270 B**
- 零改表（`AppDatabase.version = 5` 未变）→ v0.2.26 上覆盖安装即可
- 坚果云目录以使用者实际所见为准；上传 step 成功不等于目录可见
