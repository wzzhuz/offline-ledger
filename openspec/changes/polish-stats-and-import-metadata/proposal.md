# 统计页与导入元数据收尾（第 26 批）

## Why
v0.2.9 导入结果已可用，但三处体验仍不顺：统计页点分类看流水是弹窗（与已统一的
明细分页/下拉刷新/筛选面板割裂）；年度趋势 12 个点竖屏读不出数值；Firefly III
导入仍往备注拼 `ff3:` 前缀，污染原始备注，而来源本可由 `(importSource, sourceId)`
字段承载。

## What Changes
- 统计页点分类（二级扇区 / 排行 / 下钻二级）改为带筛选跳明细 Tab，复用
  `applyFilter` / `PagingData` / 官方下拉刷新；明细页显示可一键清除的筛选 chip。
- 年度与多年趋势：关键点常驻简化值、上下交错；点按出现图下汇总文字与一条瞬时
  竖向虚线引导线，不再弹 AlertDialog。
- Firefly III 导入：备注保持 FF3 `notes` 原文逐字不改；来源完全由
  `importSource` / `sourceId` 承载并在流水详情显示；CSV 明文与加密备份 DTO 已带
  这两个字段（第 23 批），本批核对其往返不丢。

## Capabilities
- `category-stats` MODIFIED 1
- `reporting-ui` MODIFIED 1
- `backup-restore` MODIFIED 1
- `ledger-transactions` MODIFIED 1

## Impact
零表结构变更（`AppDatabase.version = 2` 不变）→ v0.2.9 覆盖安装即可。
不新增 NavHost 路由、不新增依赖、不新增权限。
