# 设计决策（第 26 批 · v0.2.10）

## 1. 分类下钻：复用明细页，不复制列表
现状 `DrillDownDialog` 在弹窗里放一个 `LazyColumn` 展示 100 条流水，与明细页的
分页 / 下拉 / 筛选 / 编辑全部割裂。第 11 批已证伪「操作型浮层塞长列表」。
本批把「查看该分类流水」改到明细 Tab：`StatsScreen` 新增
`onOpenCategoryInDetail(id, isRoot, yearMonth, currencyCode)`，`LedgerApp` 调
`detailVm.filterByCategory(...)` 后 `navigate("detail")`。筛选状态仍提升到
Activity 作用域（第 5 批），切 Tab 不丢。

**边界（如实记录）**：跨 Tab 只带分类 + 币种 + `yearMonth`，不带 `fromDate/toDate`
跨月条件。原因是 `DetailViewModel.reload()` 的月概览只按 `yearMonth` 聚合，若带
整年 from/to 会让「月概览」与「列表」口径不一致——那是既有设计，不在本批扩大。
年度 / 多年视图下钻落到统计页当前 `yearMonth`，用户可在筛选面板改范围。
一级 → 二级构成的弹窗保留（它是选择器，不是「查看明细」），点二级才跳明细。

## 2. 年度 12 点数值：关键点常驻 + 交错 + 单条引导线
360dp 屏去掉内边距后每点约 27dp，`12,345.67` 放不下；12 根虚线必然交叉。
本批：`LineChart` 新增 `staggerLabels`（奇偶上下错开）与 `guideIndex`（只画选中点
一条竖向虚线），`StatsScreen` 在 YEAR / MULTI_YEAR 传 `compactCents` 简化值，
保留 `ChartData.annotationIndices` 只标峰值 / 末点 / 选中点。点按不再弹
AlertDialog，改图下随选更新的汇总文字 +「查看该年」/「清除选中」。

**未做**：浮层气泡（竖屏 27dp 间距下易遮挡数据点，且触摸区难做宽）；12 点全量
常驻数值。完整 12 月数值表仍在横屏放大页。

## 3. 备注原样 + 来源字段
第 23 批已给 `records` / `trash` 加 `importSource` + `sourceId` 与
`UNIQUE(importSource, sourceId)`，CSV 明文与备份 DTO 也已带字段（**本批查证确认，
上一轮门禁里「要补 DTO」的担心是多余的**）。仍残留的是 `FireflyCsvCodec` 把
description / 分组 / 标签 / 原备注 / 预算 / `ff3:` 拼进 note。本批改为
`note = r.notes` 逐字不改，其余无对应字段的元数据不再塞进备注，在导入结果中明示。
流水详情弹窗显示「来源：firefly-iii · journal xxx」。

**历史数据**：已导入的 1916 行备注带旧前缀，改代码不洗历史；按测试阶段口径
重装重导即可，不写一次性迁移。

## 4. 回退顺序（动手前先写）
1. `LineChart` 新参数默认值不改既有调用点；若 `PathEffect` 解析失败 → 去掉虚线只留交错。
2. `StatsScreen` 回调全部给默认空实现；若跨 Tab 编译报错 → 退回保留 DrillDownDialog。
3. 备注去前缀导致单测红 → 同步改测试断言为「note 不含 ff3: 且 journalId 独立」。


---

# 第 27 批（v0.2.11）· 下钻契约四层补齐

## 1. 根因：第 26 批只改了「跳转动作」
上一批把分类下钻从弹窗改成带筛选跳明细 Tab，但只传了 `categoryId` 与
`isRoot`，漏了四层契约：① 条件值（二级分类没带 `rootCategoryId`，筛选面板
「二级」因此显示「先选一级」，已设的 2 项里找不到分类）；② 展示名
（chip 拿不到名字，`categories` 反查失败时兜底成「分类筛选：分类」）；
③ 面板维度（面板确有「一级 / 二级」两行，但状态还原不了，等于看不见）；
④ 入口覆盖（只有分类维接了跳转，图例与收款方 / 账户排行没接）。
四条使用者反馈同根，不是四个独立 bug。

**修法**：`RecordFilter` / `FilterValues` 加只读 `filterDisplayName`（不进 SQL、
不进 `activeCount()`）；`filterByCategory` 同步反查父级并带展示名；面板把
「一级 / 二级」两行合并为一行「分类」+ 带搜索全量弹层（value 用 `r:` / `c:`
前缀区分层级）；明细页 chip 按三类条件分别显示具体名；新增
`filterByCounterparty` / `filterByAccount`；`LedgerApp` 三个回调共用
`navigateToDetail`。零表结构、零新增依赖、零新增 NavHost 路由。

## 2. 排行二级展开
第 13 批排行卡点一级是 `vm.openRoot` 弹二级构成，第 26 批改跳明细时把这条
能力一起删了。本批用 `StatsUiState.rankChildren`（复用已有 `childSlices` 聚合，
按 `CategoryEntity.parentId` 归组，零新增 SQL）做**内联展开**：一级行加 ⌄，
展开态 `rememberSaveable`，二级行点名直接跳明细。不回弹窗内列表。

## 3. 色板与图例
第 23 批「低饱和同色系明度递进」在真机上相邻扇区分不出来（使用者反馈
「图例颜色太相近」）。改为明度接近、色相分明的 10 色柔和分类色板，仍按 id
取模稳定；图例整行 `clickable` + `heightIn(min=40.dp)` + 12dp 色块与扇区共用
`onSectorClick`。列表 / chip 的中性单色圆不变。

## 4. 回退顺序（动手前先写）
1. 若 `rememberSaveable` / `saveable` 依赖在当前 BOM 解析失败 → 退 `remember`，
   并在 tasks 记录展开态不跨重建保留。
2. 若 `groupBy` 归组导致编译或单测失败 → 退到「只展开 `drillChildren` 已有
   数据」，仍不弹窗。
3. 若跨 Tab 回调参数扩展引发签名冲突 → 拆成独立 `onOpenRankInDetail` 数据类，
   MUST NOT 退回弹窗列表。
4. 若色板仍不可辨 → 只加大色块与图例文字对比度，MUST NOT 开放 `colorHex`。

## 5. 上批（第 26 批）实测回填（补记）
代码 `d9e9dc28d903`，首轮三次红（`44251300803` / `44251658657` /
`44252048207`）由临时诊断回传日志定位：`RecordRow` 缺来源列、
`@Composable` 注解错位；修复后 push CI `44253403111` success。
release `44254405887` success（23 步 / 5 步按 input 跳过），坚果云上传 success；
制品 `release-v0.2.10` 12,319,308 B、`mapping-v0.2.10` 3,663,922 B、
`metrics-v0.2.10-p-m-nsfalse` 271 B。`nsfalse` 只表示认证 / 建目录 / PUT
均未失败，HEAD 落盘校验只写日志不中断。上批遗留的 `diag.yml` /
`diag/unit.log` 在第 27 批交付前清理。
