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


---

## 第 27 批追加（v0.2.11 · 下钻契约收尾）

### Why
v0.2.10 把分类下钻改到明细 Tab 后，使用者反馈四处同根缺口：chip 显示
「分类筛选：分类」且筛选面板看不到已选分类；饼图只有扇区能点、图例不能点且
相邻颜色太近；排行失去二级展开；收款方 / 账户排行点行无反应。

### What Changes
- 跨 Tab 下钻补齐「条件值 + 只读展示名 + 面板维度 + 三维入口」四层契约，
  二级分类同时带父级 id；面板合并为一行「分类」+ 带搜索全量弹层。
- 环形图图例整行可点、换色相分明且按 id 稳定的低饱和色板。
- 排行一级内联展开二级（`rememberSaveable`），三个维度全部跳明细。

### Capabilities
- `category-stats` MODIFIED 1（整块替换，不追加 ADDED）
- `reporting-ui` ADDED 1
- `ledger-transactions` 主 spec MODIFIED 1（筛选条件清单整块替换）

### Impact
零表结构（`AppDatabase.version = 2` 不变）→ v0.2.10 上覆盖安装即可。
不新增依赖 / 权限 / 路由；明细与资产下拉刷新、横屏 insets、无障碍刷新按钮
的既有口径均不回退。

## 第 28 批追加（v0.2.12 · 下钻契约收口与构建产物口径）

使用者验收 v0.2.11 后反馈两处：占比卡切「按大类」时点扇区只弹二级构成弹窗，
无法按一级跳明细；按收款方跳转后筛选面板「分类」行与「交易对方」框同时出现收款方名。
另核查 12M / 24M 包体差异，订正上一批的错误归因。

- 一级扇区 / 图例直接带 `rootCategoryId` 跳明细 Tab，弹窗同时补「查看该大类全部流水」
- `RecordFilter` / `FilterValues` 的单条 `filterDisplayName` 拆成三条按维度独立的只读展示名，
  三个入口抽成纯 JVM 工厂并加互斥单测
- 坚果云产物名 `*-minified-<sha>.apk` 规范为 `*-release-<sha>.apk`（上传源不变）
- `ledger-transactions` 主 spec MODIFIED 整块替换，补一级下钻与串味回归 Scenario
- 版本 0.2.11 → 0.2.12，表结构不变，覆盖安装即可
