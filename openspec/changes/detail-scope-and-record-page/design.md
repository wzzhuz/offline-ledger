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
