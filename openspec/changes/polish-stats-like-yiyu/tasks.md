# 任务：统计页参考一羽（第 68 批 → 第 69 批修订 / v0.2.49）

- [x] 1.1 核查 `RecordRowCard` 明细行、`DrillRow` 六列投影、
      带 LIMIT 的两条下钻 SQL、`LedgerTheme` 默认灰卡根因、
      `LedgerApp` 三块跨 Tab lambda、23 个含 Card/background 的文件
- [x] 1.2 认账第 68 批三处跑偏（蓝底双 Tab / 三维度混卡 / 跳明细 Tab），
      按 5 条反馈重做对齐；使用者 Q1A Q2A Q3A Q4B Q5B Q6A + 验收按我拟的
- [x] 1.3 推四件套修订 + 实现 `d4db626`（18 文件 / blob 585），
      逐 blob tree + content 双校验 PASS；首轮 CI KSP 缺列 →
      补齐 `insightCategoryDetailRows` 并逐字节恢复 `build.yml`（`c8440eb`）
- [x] 2.1 删 `StatsHeader`、`statsTab`、`TabRow`、蓝底 import；
      回到白底「统计」+ 既有 PeriodSwitcher / CurrencySwitcher
- [x] 2.2 `StatsScreen` 签名删三个跨 Tab 回调，只留 `onOpenCategories`
- [x] 2.3 `LedgerApp` 删三块 `detailVm.filterBy* + navigate("detail")`；
      `DetailViewModel` 方法保留不删
- [x] 3.1 `CategoryInsightCard` → `CategoryShareCard`：只分类，
      左上角单个「按大类 ⇄ / 按小类 ⇄」标签（点一下切换）
- [x] 3.2 删三维度 chip、删一级「⌄」展开、删 Top5 冒充净支出分支；
      环中心统一「净支出 ¥X」
- [x] 3.3 `DonutChart` 图例每行加灰色长条（浅灰轨道 + 低饱和蓝填充，
      宽度 = percent），删 `expandableIds` / `expandedChildren` /
      `onExpandToggle` / `onChildClick` 参数
- [x] 3.4 「分类管理 ›」与合计一致性警告置顶保留
- [x] 4.1 新增 `OtherRankCard`：默认折叠，左上角「收款方 ⇄ 账户」单标签，
      展开列 Top5（中性圆 / 名称带卡号 / ¥金额 / 灰条），只读不跳转
- [x] 5.1 `RecordDao.DrillRow` 扩 10 个带默认值字段
      （timestamp / categoryColorHex / counterparty / description /
       from-to 账户名与卡号 / discount / fee）
- [x] 5.2 两条既有下钻 SQL 扩 LEFT JOIN accounts 并 SELECT 新列，
      唯一口径与 `LIMIT :limit` 一字不动；新增
      `drillDownRowsForRootCurrency`（`rootCategoryId = :rootId`）
- [x] 5.3 `check_stat_rules.py` marker 加新查询
- [x] 5.4 `StatsViewModel.openSliceRows(categoryId, isRoot, displayName)`
      按口径选查询，LIMIT 100，填 drillRows
- [x] 5.5 `DrillDownDialog.kt` 重写为 `StatsDrillSheet`
      （ModalBottomSheet + LazyColumn，行逐项对齐 RecordRowCard：
      方向箭头 / 主标题 / 副行 / accountLabel 带卡号 / 金额 /
      划线原价与优惠标签 / 日期），删两个「去明细」按钮
- [x] 5.6 点扇区 / 图例行 → `openSliceRows` → sheet；
      `drillCategoryId != null` 为唯一显示条件
- [x] 6.1 `LedgerTheme.lightColorScheme` 补齐浅蓝灰底 / 纯白卡 /
      柔和深灰蓝文字 / 清新蓝 primary；`LedgerColors.Primary` 同步
- [x] 6.2 `ExpenseRed` / `IncomeGreen` / `applyReverseColors` /
      `CategoryHeaderBlue` 全部零改动
- [x] 6.3 如实登记全 23 个文件观感变化，§8 新增全 App 观感待验
- [x] 7.1 删第 68 批 ADDED「蓝底身份区与双 Tab」；
      重写 8 条 MODIFIED + 4 条 ADDED，标题命中基线、零冲突
- [x] 7.2 活文档 §7.51 登记第 69 批撤回与方法论订正；
      `project.md` 第 69 批
- [x] 7.3 重写 `check_stats_merge.py` 断言新方案
      （无蓝底 / 无跨 Tab / 折叠卡 / 灰条 / 全局主题 / SQL 口径）
- [x] 7.4 版本号 0.2.49
- [x] 8.1 八个静态检查全 PASS；首轮 CI KSP 缺列已定位修复，
      push CI `45553250628` 全绿
- [x] 8.2 触发与轮询拆独立调用，出包 CI `45553797437` success /
      tag `v0.2.49` / SHA256 `9b72ee069c8e422646b3047d521bdcf9f0e4f785aedb33dfcf25d982f930daf4`
- [x] 8.3 实测回填（活文档 §7.51 与 project.md）；正式归档校验仍保持 `[ ]`
- [ ] 9.1 无蓝底块、无「分析 / 概览」Tab，月度/年度/多年切换完好
- [ ] 9.2 左上角单标签切换大类/小类，无下拉箭头、无三维度 chip
- [ ] 9.3 图例灰条长度与百分比一致、五行金额合计 = 净支出
- [ ] 9.4 收款方/账户卡默认折叠、展开后 Top5 正确、只读
- [ ] 9.5 点分类弹底部 sheet 按明细行样式展示、不切 Tab、最多 100 条
- [ ] 9.6 全 App 白卡浅蓝灰底观感、红绿语义不变、反转开关正常
- [ ] 9.7 覆盖安装（v0.2.48 → v0.2.49）数据不丢、不升表
- [ ] 9.8 §8 第 45–68 批既有真机待验项整体继承
