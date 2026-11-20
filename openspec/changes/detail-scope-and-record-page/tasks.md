# 第 45 批任务

## 实现
- [x] `RecordFilter` 增 `DetailGranularity` / `periodBounds()` / `periodLabel()` / `resolveAllMonths()`
- [x] `currentMonth()` / `clearFilter()` 回到按周期；`applyFilter` 走 `resolveAllMonths` 裁决
- [x] `DetailViewModel.setGranularity` + 顶部「月 / 年」chip
- [x] `AccountQuickCandidates.promoteIds` / `promoteText`
- [x] `RecordEditorViewModel.load()` 编辑态置顶
- [x] 新增 `RecordDetailPage`，`DetailScreen` 改二级页切换
- [x] `DetailScopeTest` 7 条、`AccountPromoteTest` 5 条纯 JVM 单测
- [x] 版本 0.2.27、活文档 §7.27 / §8、project.md 第 45 批

## 构建与发布
- [x] push CI 17 步全绿
- [x] release 出包 + 上传坚果云
- [x] 实测回填 + 文档 CI + 最终 blob 校验

## 真机复核（留 [ ] 给使用者）
- [ ] 刚进明细页 / 清空筛选：月粒度标题「2026-11」且只当月；切「年」标题「2026 年」且只当年
- [ ] 加一条筛选但不设时间段：chip 明示全部时间、能滚完所有命中记录
- [ ] 统计页月 / 年 / 多年下钻仍带时间、清空后回默认周期
- [ ] 点记录直接进详情页、锁切编辑、删除与报销二次确认仍在
- [ ] 点进转账编辑什么都不改直接保存：转出 / 转入账户零变化，且两者显示在最前
- [ ] 描述主标题回退、CSV 重建、规则、两页下拉刷新不回退

### 构建实测编号
- push CI `44661745120` success 17/17（首轮 `44660731626` 单测红，已修复）
- release `44662596613` success，上传坚果云 success，并发 `44662933616` cancelled
- 制品 12,405,979 B / mapping 3,826,420 B / metrics 270 B

# 第 46 批任务（v0.2.28）

## 实现
- [x] `DetailViewModel.locateMonth` 写回 `_filter.yearMonth` + `revision`，同步 `_overview.yearMonth`
- [x] `DetailScreen` 锚点改为分页数据到达后定位，删除「继续下滑后再点一次」Snackbar
- [x] 月 / 年 `FilterChip` 并入标题 Row，删独占第二行，标题单行省略
- [x] `BackHandler` 消费系统返回，`closeDetail` 单一出口供关闭图标 / 「返回列表」共用
- [x] `DetailMonthSwitchTest` 5 条纯 JVM 单测（月 / 年切月、跨月模式、显式范围、条件不丢）
- [x] spec 追加三条 MODIFIED Requirement、design 追加第 46 批、活文档 §7.28 / §8、project.md 第 46 批
- [x] 版本 0.2.28；零改表（`version = 5` 不变）

## 构建与发布
- [x] push CI `44686081288` success 17/17（静态检查 6 项 + 单测 + Debug/Release 编译全绿）
- [x] release `44687082430` success，23 绿 / 5 跳过，上传到坚果云 success
- [x] 实测回填 + 文档 CI + 最终 blob 校验

### 第 46 批构建实测编号
- 代码 `8a10edf368b6`（1 新增 / 8 修改）
- 制品 `release-v0.2.28` 12408292 B / `mapping-v0.2.28` 3826447 B / `metrics-...-nsfalse` 269 B
- 并发 release `44687435727` cancelled，保留并跑完 `44687082430`

## 真机复核（留 [ ] 给使用者）
- [ ] 月 / 年粒度点箭头或 YearMonthPicker 切月：标题与列表同步刷新，不再出现「继续下滑」
- [ ] 有筛选无时间段时切月：仍是全部时间命中记录，定位到目标月首条
- [ ] 目标月无记录：列表为空、标题为该月，不提示「尚未加载」
- [ ] 月 / 年 chip 与标题同一行、不挤压重叠；顶部不再多占一行
- [ ] 详情页全面屏左滑返回列表、不退出 App；顶部 X 与「返回列表」行为一致
- [ ] 统计下钻带时间、编辑态零改保存账户不变、报销 / 删除二次确认、规则 / CSV 重建不回退
