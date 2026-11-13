# 任务清单：build-statistics-charts

## 1. 阶段 0 对齐与前置归档

- [x] 1.1 读取 `AGENTS.md` / `project.md` 与 `category-stats` / `reporting-ui` /
  `reconciliation` spec，实际读 `RecordDao` / `CategoryDao` / `MonthRange` / `LedgerDi`，
  确认统计所需聚合 SQL 已在阶段 1 / 2 备好、**无需 Migration / 无需新增依赖**
- [x] 1.2 强制开场复述、产出五项对齐结论并停下等待确认
- [x] 1.3 判定「需要建 change」并告知，等确认
- [x] 1.4 使用者确认「按推荐来」：三种图一次到位 + 年度双折线
- [x] 1.5 归档阶段 3：14 条 ADDED 合并 + **2 份 MODIFIED 首次按标题替换** +
  change 移入 `archive/2026-11-12-implement-assets-and-reconciliation/` + 校验 PASS；
  8 模块现为 107 条 Requirement / 145 个 Scenario
- [x] 1.6 归档阶段 3 时记录的「统计页占位」规格债纳入本 change 的 MODIFIED

## 2. 写 change 四件套

- [x] 2.1 `proposal.md` —— 原始场景与完整「对齐结论」节
- [x] 2.2 `design.md` —— 既有 DAO 清单 / 纯 JVM 数据层 / Canvas 取舍 /
  1 份 MODIFIED / 回退顺序 / 否决方案表 / 浮点边界说明
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 `specs/category-stats/spec.md` —— ADDED 3 条
- [x] 2.5 `specs/reporting-ui/spec.md` —— **MODIFIED 1 条（四 Tab 骨架一次成型）** + ADDED 2 条
- [x] 2.6 `specs/money-precision/spec.md` —— ADDED 1 条
- [x] 2.7 `specs/ledger-transactions/spec.md` —— ADDED 1 条
- [x] 2.8 `specs/build-and-ci/spec.md` —— ADDED 2 条
- [x] 2.9 推送 change 文件到远端并用 API 校验

## 3. 纯 JVM 数据准备层与单测

- [x] 3.1 `engine/ChartData.kt`：合计校验 / 补 0 日序列 / 补 0 年序列 /
  扇区角度与命中 / 归一化 / 手写 hex 解析
- [x] 3.2 `engine/ChartDataTest.kt`：一致与不一致、闰年 / 大小月、空序列、
  全 0、单扇区、跨 0° 命中、非法颜色

## 4. DAO 与静态检查

- [x] 4.1 `RecordDao.subcategoryTotalsUnderRoot(ym, rootId)`：
  `GROUP BY categoryId` + 唯一统计口径
- [x] 4.2 `scripts/check_stat_rules.py` 扩展覆盖新查询（不新增写方法，
  `check_transaction_annotations.py` 的 13 个清单不变）

## 5. Canvas 图表组件

- [x] 5.1 `ui/charts/DonutChart.kt`：`drawArc` 扇区 + `Box` 叠中心总额 +
  图例 + 点击命中（不依赖 `TextMeasurer`）
- [x] 5.2 `ui/charts/LineChart.kt`：网格 / 折线 / 点 / x 轴刻度用 Compose `Text`

## 6. 统计页与下钻

- [x] 6.1 `ui/stats/StatsViewModel.kt` + `StatsUiState`（只读，无写事务）
- [x] 6.2 `ui/stats/StatsScreen.kt`：月 / 年切换、收支概览、合计警告条、
  一级 / 二级切换、环形图、月度折线、年度双折线
- [x] 6.3 `ui/stats/DrillDownDialog.kt`：一级 → 二级构成 → 分类明细
  （复用 `pagedFiltered` + LIMIT 100）
- [x] 6.4 `ui/LedgerApp.kt` 把 stats 占位换成 `StatsScreen()`，4 Tab 结构不动
- [x] 6.5 删除 `StatsPlaceholder.kt`

## 7. 测试与静态检查

- [x] 7.1 `ChartDataTest` 全部 PASS
- [x] 7.2 本地五个检查脚本全部 PASS（含扩展后的统计口径清单）
- [x] 7.3 单次 commit 推送后用 GitHub API 查 Actions 列表，确认**确实触发**
- [ ] 7.4 轮询 Actions；失败按 `design.md` 第 6 节回退顺序单次修复后重推
- [ ] 7.5 确认 job 结论为 success、制品产出

## 8. 同步与收尾

- [x] 8.1 更新 `README.md` 阶段状态、目录结构、统计报表说明、测试边界
- [x] 8.2 推完用远端 API 逐文件比对 sha
- [x] 8.3 凭据按使用者说明保留至项目完成，仅清理临时诊断产物
- [ ] 8.4 使用者确认后归档本 change（1 份 MODIFIED 按标题第二次替换 + ADDED 合并）

> 7.4 / 7.5（CI 轮询与结论）与 8.4（归档）未完成，不预先打勾。
> 归档需使用者明确指令，本轮不擅自归档。
