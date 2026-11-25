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

---

## 第 70 批任务（分类图三条观感优化 / v0.2.50）

- [x] 10.1 核对 3 张一羽截图与当前实现；确认「蓝底 / 分析概览 / 支出⇌」不加回
- [x] 10.2 标题改「竖条 + 纯文本『大类/小类』 + 独立自绘 ⇄ 图标按钮」，
      删 FilterChip 口径标签
- [x] 10.3 新增 `StatsCard` 统一出口（纯白 + 零 tonalElevation + 16dp 圆角），
      统计页 7 处 `Card(` 全量替换
- [x] 10.4 `LedgerTheme.background` 改中性浅灰 `F2F3F7`，
      `surfaceContainer*` 收纯白 / 近白，`surfaceVariant` / `outlineVariant` 去蓝味
- [x] 10.5 `DonutChart` 环半径 0.78→0.60、图高 210→250dp，
      Canvas 与点击命中同源
- [x] 10.6 `GuideLine` 数据类 + `onSizeChanged` + 同侧去重叠
- [x] 10.7 Canvas 画斜引线 / 水平折线 / 端点圆点，颜色随扇区
- [x] 10.8 普通 `Text` + `absoluteOffset` 标「名称 百分比」，
      零 `drawText` / `TextMeasurer` / `FlowRow` / 实验性 API
- [x] 10.9 撤回第 68/69 批「环外悬浮百分比明确未做」，
      重写 2 条 ADDED + 1 条 MODIFIED 措辞（12 条 / 45 Scenario）
- [x] 10.10 活文档 §7.52（第三次外推订正 + M3 tonalElevation 根因）、
      `project.md` 第 70 批
- [x] 10.11 `check_stats_merge.py` 断言新三要素与「不加回」项
- [x] 10.12 版本号 0.2.50
- [x] 10.13 八个静态检查全 PASS；首轮 CI 漏 import 已定位修复，
      push CI `45558262022` 全绿（19 步）
- [x] 10.14 触发与轮询拆独立调用，出包 CI `45558932688` success /
      tag `v0.2.50` / SHA256 `4465783f01b712de8e232520276787528bbf869b1273b5cc5e0277c17ad58180`
- [x] 10.15 实测回填（活文档 §7.52 与 project.md）；正式归档校验仍保持 `[ ]`
- [ ] 11.1 左上角文本 / 图标分离、无 chip、无右上支出按钮、无蓝底 Tab
- [ ] 11.2 中性灰底 + 纯白卡边界清晰，全 App 逐页观感
- [ ] 11.3 每个扇区指示线同色、末端标签正确、同侧不重叠、不盖环
- [ ] 11.4 点扇区 / 图例仍弹页内 sheet，周期三档 / 币种 / 合计警告零回归
- [ ] 11.5 覆盖安装（v0.2.49 → v0.2.50）数据不丢、不升表
- [ ] 11.6 §8 第 45–69 批既有真机待验项整体继承

---

## 第 71 批任务（半箭头 + 指示线不交错 / v0.2.51）

- [x] 12.1 定位交错根因：标签 y 下推后折点 y 未同步、水平段变斜线；
      `last` 固定 10dp 无谓下推、无上界 clamp
- [x] 12.2 `SwitchLevelIcon` 改半箭头 `⇌`（上尖只上半、下尖只下半，4 条 drawLine）
- [x] 12.3 新增 `RawGuide`，`GuideLine.y` 改回 `val`、`p1/p2` 改 `anchor/knee`
- [x] 12.4 guides 每侧按 `anchor.y` 保序，`maxOf` 最小间距从
      `NEGATIVE_INFINITY` 起算，整体 `shift` + `coerceIn(pad, h-pad)`
- [x] 12.5 折点 x 固定、`knee.y == y`，水平段严格水平；圆点与文字同 y
- [x] 12.6 动态 `capacity` 超限才按金额降序 `take(3)`；环与图例始终全量
- [x] 12.7 颜色沿用全局 index，与 `drawArc` 同源，降级不重新编号
- [x] 12.8 修订 1 条 MODIFIED + 1 条 ADDED 措辞（12 条 / 48 Scenario）
- [x] 12.9 活文档 §7.53、`project.md` 第 71 批、`check_stats_merge.py` 新断言
- [x] 12.10 版本号 0.2.51
- [x] 12.11 八个静态检查全 PASS；push CI `45562181037` 一次全绿（19 步，
      无诊断 / 修复 commit）
- [x] 12.12 触发与轮询拆独立调用，出包 CI `45562758315` success /
      tag `v0.2.51` / SHA256 `13989c874dccfb4316423950a0871be6ab2f01065a32f6b68cd5720c1c04b482`
- [x] 12.13 实测回填（活文档 §7.53 与 project.md）；正式归档校验仍保持 `[ ]`
- [ ] 13.1 图标为半箭头、上尖只上半、下尖只下半
- [ ] 13.2 水平段严格水平、同侧平行不交错、折点/圆点/文字三者同 y
- [ ] 13.3 标签不越界、不盖图例、顶部标签不被无谓下推
- [ ] 13.4 5 类数据全量标注不提前降级；极端多分类才 Top3
- [ ] 13.5 点扇区仍弹页内 sheet、大类小类切换 / 周期三档 / 合计警告零回归
- [ ] 13.6 覆盖安装（v0.2.50 → v0.2.51）数据不丢、不升表
- [ ] 13.7 §8 第 45–70 批既有真机待验项整体继承

---

## 第 72 批任务（径向折点 + 小细淡图标 / v0.2.52）

- [x] 14.1 对比 4 张真机图，定位第 71 批真因：折点 x 固定同一竖线 → 扇形汇聚；
      高度算 capacity 没兜住窄角度
- [x] 14.2 `RawGuide` 加 `knee`，折点改为沿自身中点角的 `radialR` 径向点
- [x] 14.3 取消标签 y 下推 / 整体平移 / pad clamp；`GuideLine.y = knee.y`
- [x] 14.4 `minAdjacent` 判据（实际最小相邻折点 y 间隔 < 18dp 才降级）
- [x] 14.5 降级按金额 Top3，仍挤则继续 `dropLast(1)` 到不交错或只剩 1 条
- [x] 14.6 水平段严格水平、圆点与文字同 y；文字 y 改 `g.y - 16dp` 贴线之上
- [x] 14.7 颜色沿用全局 index，降级不重新编号
- [x] 14.8 `SwitchLevelIcon` 14dp / 1.2f / 55% 浅蓝灰 / 24dp 按钮盒，半箭头形状不变
- [x] 14.9 修订 1 条 MODIFIED + 1 条 ADDED 措辞（12 条 / 51 Scenario）
- [x] 14.10 活文档 §7.54、`project.md` 第 72 批、`check_stats_merge.py` 新断言
      （含「固定 x / 高度 capacity / y 下推」残留列为 FAIL）
- [x] 14.11 版本号 0.2.52
- [ ] 14.12 八个静态检查全 PASS；push CI 全绿
- [ ] 14.13 触发与轮询拆独立调用，出 v0.2.52 包
- [ ] 14.14 实测回填；归档校验保持 `[ ]`
- [ ] 15.1 折点沿各自径向、x 各不相同、无扇形汇聚
- [ ] 15.2 径向短线互不相交、水平段严格水平且同侧平行
- [ ] 15.3 标签贴线之上、折点 / 圆点 / 线三者同 y
- [ ] 15.4 一羽 5 类 / 12 类、本 App 大类 7 类全量标注不提前降级
- [ ] 15.5 图四窄角度 11 小类才降 Top3，环与图例仍全量
- [ ] 15.6 图标 14dp 细线浅蓝灰紧贴文字且可点，不再突兀
- [ ] 15.7 点扇区仍弹页内 sheet、大类小类切换 / 周期三档 / 合计警告零回归
- [ ] 15.8 覆盖安装（v0.2.51 → v0.2.52）数据不丢、不升表
- [ ] 15.9 §8 第 45–71 批既有真机待验项整体继承
