# 任务：统计页参考一羽记账优化（第 68 批 / v0.2.48）

- [x] 1.1 核查远端 HEAD（`56142c4d`）、`StatsScreen` / `StatsViewModel` /
      `DonutChart` / `ChartData` / `InsightData` 与基线 3+3 条 Requirement 原文
- [x] 1.2 对照 5 张截图，区分一羽「单卡同排」与第 13 批「互斥切换 chip」
- [x] 1.3 五项对齐 + 9 条验收 + 分级判定（新建 change）；使用者回「按你拟的」
      = Q1A Q2A Q3A Q4A
- [ ] 1.4 推四件套 + 全部实现，逐 blob 双校验（Q4A 一次做完）
- [ ] 2.1 `DonutChart` 增 `legendRows` 一羽式图例：彩色图标 / 名称 / 百分比 /
      金额，整行可点 ≥40dp，共用 `onSectorClick`
- [ ] 2.2 保留原生 Canvas `drawArc` 扇区与中心文字；中心标签支持按维度切换
- [ ] 2.3 不引 `DrawScope.drawText` / `TextMeasurer` / `FlowRow` /
      `BoxWithConstraints`（基线禁用）
- [ ] 3.1 `StatsScreen` 蓝底 `Box` TopBar（复用 `CategoryHeaderBlue`）+ 月份 +
      周期箭头 + 口径入口
- [ ] 3.2 「分析 / 概览」两个真实 Tab：分析=全部卡片，概览=小结+合体卡
- [ ] 3.3 `PeriodSwitcher` / `CurrencySwitcher` 移到蓝底下紧凑行，零功能改动
- [ ] 3.4 小结卡三行对齐 + 支出红进度条；保留怎么算 / 日均 / 净支出 / 预算
- [ ] 3.5 趋势卡点选汇总改蓝色圆角气泡；补笔数 / 日均 / 单日最高 / 峰值日
      四项摘要（全部由既有 state 派生，零新增 DAO）
- [ ] 4.1 新增 `CategoryInsightCard` 合体卡；移除 `RankInsightCard` 挂载
- [ ] 4.2 维度 chip「分类 / 收款方 / 账户」；分类维度保留按大类 / 按小类
- [ ] 4.3 环：分类用 `rootSlices/childSlices`，其他维度用 Top5 转 `ChartSlice`，
      中心按维度换标题（不写「净支出」）
- [ ] 4.4 图例列表：分类维度 `CategoryGlyph(size=32, categoryId=id)`，
      其他维度中性圆 + 首字；缺项回退中性圆
- [ ] 4.5 下钻四层契约保住：扇区 / 图例整行 / 三维度 / 二级展开子项全接
      `state.drillRange` + 既有回调；「分类管理 ›」仍在标题右侧
- [ ] 4.6 合计一致性警告仍置顶，MUST NOT 为美观收掉
- [ ] 5.1 `colorHex`、`ChartData.chartArgb`、`StatsViewModel`、`RecordDao`、
      `InsightData`、`StatsDrillRange`、`version = 7` 全部零改动
- [ ] 5.2 6 条 MODIFIED 整块替换 + 2 条 ADDED，标题命中基线、零冲突
- [ ] 5.3 活文档 §7.50 撤回「合并=一屏硬塞」外推并登记 Q2A 放行；
      `project.md` 第 68 批
- [ ] 5.4 新增 `scripts/check_stats_merge.py` 与 `StatsLegendTest` 纯 JVM 单测
- [ ] 5.5 版本号 0.2.48
- [ ] 6.1 七个静态检查 + 新增检查全 PASS；push CI 全绿
- [ ] 6.2 触发与轮询拆成独立调用，出 v0.2.48 包
- [ ] 6.3 实测回填；归档校验保持 `[ ]`
- [ ] 7.1 蓝底 TopBar 不遮挡周期 / 币种切换，两个 Tab 真实可切
- [ ] 7.2 合体卡 5 行图例百分比与金额和环一致、整行可点、二级展开可跳明细
- [ ] 7.3 收款方 / 账户维度中心标题不误导为净支出
- [ ] 7.4 彩色图标只在统计占比图例，记一笔 / 明细 / 账户详情零回归
- [ ] 7.5 趋势气泡与四项摘要不遮挡图表、单日最高取数正确
- [ ] 7.6 覆盖安装（v0.2.47 → v0.2.48）数据不丢、不升表
- [ ] 7.7 §8 第 45–67 批既有真机待验项整体继承
