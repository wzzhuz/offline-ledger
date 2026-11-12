# 任务清单：build-record-entry-ui

## 1. 阶段 0 对齐与前置归档

- [x] 1.1 读取 `AGENTS.md` / `project.md` 与 `reporting-ui` / `reconciliation` /
  `ledger-transactions` spec，复述状态并等确认
- [x] 1.2 产出五项对齐结论并停下等待确认
- [x] 1.3 判定「需要建 change」并告知，等确认
- [x] 1.4 使用者确认「按推荐来」：自定义 PagingSource + 4 Tab 一次成型
- [x] 1.5 归档阶段 1：6 份 delta 合并进 `specs/`（含 MODIFIED 修正旧统计口径）、
  change 移入 `archive/2026-11-12-implement-room-ledger-core/`、逐模块校验通过
- [x] 1.6 查证 Paging / Navigation / 图标 artifact 版本并写入 `design.md`

## 2. 写 change 四件套

- [x] 2.1 `proposal.md` —— 原始场景与完整「对齐结论」节
- [x] 2.2 `design.md` —— 导航 / 依赖锁定 / 分页 / 筛选 SQL / 计算器 / 回退顺序 / 否决方案表
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 `specs/reporting-ui/spec.md` —— Delta
- [x] 2.5 `specs/ledger-transactions/spec.md` —— Delta
- [x] 2.6 `specs/category-stats/spec.md` —— Delta
- [x] 2.7 `specs/reconciliation/spec.md` —— Delta
- [x] 2.8 `specs/backup-restore/spec.md` —— Delta
- [x] 2.9 `specs/build-and-ci/spec.md` —— Delta
- [x] 2.10 推送 change 文件到远端并用 API 校验

## 3. 依赖与导航骨架

- [x] 3.1 `app/build.gradle.kts` 加 5 个 artifact（图标 / navigation / viewmodel-compose /
  paging-common / paging-compose），不改已锁版本
- [x] 3.2 `ui/theme/LedgerTheme.kt` + 红 = 支出 / 绿 = 收入配色
- [x] 3.3 `ui/LedgerDi.kt`：`@EntryPoint` + 通用 `ledgerViewModel` factory
- [x] 3.4 `ui/LedgerApp.kt`：4 Tab NavigationBar + 中央 FAB + NavHost
- [x] 3.5 `MainActivity.kt` 替换为 `LedgerApp()`
- [x] 3.6 资产 / 统计占位页（明确标注阶段 3 / 4）

## 4. DAO 与分页数据层

- [x] 4.1 `RecordRow` / `DailyTotalRow` POJO 与 `pagedFiltered` 单条可选参数 SQL
- [x] 4.2 `dailyTotals(ym)` 按日聚合 SQL
- [x] 4.3 `RecordFilter`（含 `revision`、月份范围助手）
- [x] 4.4 `RecordPagingSource`（offset 键、`rows.size` 推进、稳定 getRefreshKey）
- [x] 4.5 `DetailViewModel`：`PagingData` 独立 Flow + `flatMapLatest` + `cachedIn`

## 5. 记一笔

- [x] 5.1 `Calculator.kt` 纯 JVM `BigDecimal` 表达式求值
- [x] 5.2 `RecordEditorViewModel.kt`：三 Tab / 两级分类 / 默认记忆 / 保存与编辑
- [x] 5.3 `RecordEditorDialog.kt`：分类网格 + 二级 chips + 计算器键盘 +
  账户胶囊 + 日期 + 备注 + 不计入收支开关
- [x] 5.4 分类图标用彩色圆 + 中文首字，不做字符串→ImageVector 映射

## 6. 明细 / 筛选 / 回收站 / 导出

- [x] 6.1 `DetailScreen.kt`：月度概览 + 切月 + Paging 按日分组 + 真实按日合计
- [x] 6.2 行点击编辑 / 删除二次确认
- [x] 6.3 `FilterSheet.kt`：时间 + 账户 + 类型 + 一级 / 二级分类 + 金额区间 + 备注
- [x] 6.4 `TrashScreen.kt` / `TrashViewModel.kt`：恢复与彻底删除
- [x] 6.5 `SettingsScreen.kt`：临时明文 JSON 导出 + 自校验 + 账本健康自检 + 回收站入口

## 7. 测试与静态检查

- [x] 7.1 `CalculatorTest.kt`：优先级、舍入、非法表达式、无浮点
- [x] 7.2 `MonthRangeTest.kt`：跨月 / 大小月 / 闰年
- [x] 7.3 `RecordFilterTest.kt`：revision 变化产生新实例
- [x] 7.4 本地五个检查脚本全部 PASS
- [x] 7.5 单次 commit 推送后用 GitHub API 查 Actions 列表，确认**确实触发**
- [x] 7.6 首轮 4 个编译错误 + 次轮 1 个测试期望值错误，已按回退顺序修复
  （详见 `design.md`「首轮 CI 实测」）
- [ ] 7.7 重跑后确认 job 结论为 success

## 8. 同步与收尾

- [x] 8.1 更新 `README.md` 阶段状态、目录结构、UI 说明与测试边界
- [x] 8.2 每次推送后均用远端 Git Trees API 逐文件比对 blob sha，PASS
- [x] 8.3 清理本地凭据文件
- [ ] 8.4 使用者确认后归档本 change（合并 delta 到 `openspec/specs/`）

> 仅 7.7（最终 CI 结论）与 8.4（归档）未完成，不预先打勾。
> 归档需使用者明确指令，本轮不擅自归档。
