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
