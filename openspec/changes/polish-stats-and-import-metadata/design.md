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


## 6. 第 27 批实测回填
代码 `73e8a19818d0` → push CI `44260916347` **success**（17 步全绿，首轮 Kotlin 编译零错误；
动手前先写回退顺序、每处替换断言唯一匹配，这次没走临时诊断通道）。
release `44261811015` **success**：23 步绿 / 5 步按 input 跳过，「上传到坚果云」
completed success。制品 `offline-ledger-release-v0.2.11` 12,320,054 B、
`offline-ledger-mapping-v0.2.11` 3,672,588 B、
`offline-ledger-metrics-v0.2.11-p-m-nsfalse` 272 B。
`nsfalse` 只表示认证 / 建目录 / PUT 均未失败，HEAD 落盘校验只写日志不中断，
不当作已验证 HEAD 200。交付前 `.github/workflows/diag.yml` 与 `diag/unit.log`
已删除（上批遗留），workflow 仅 build / release。

## 7. 第 28 批（v0.2.12）· 下钻契约收口 + 构建产物口径订正

### 7.1 根因（两处，都先读代码再定）

1. **一级下钻被弹窗挡住**：`DonutInsightCard.onSectorClick` 在 `SliceLevel.ROOT` 时调用
   `vm.openRoot(id)`，只打开「二级构成」弹窗；弹窗内 `onOpenCategoryInDetail` 固定传
   `isRoot = false`。而 `LedgerApp.onOpenCategoryInDetail` 早就支持 `isRoot` 参数，
   `pagedFiltered` 第 27 批起也**已经**接了 `rootCategoryId` 短路。所以真实缺口只是
   一个分支调用，不是缺能力，更不需要新增聚合 SQL。
2. **串味**：`RecordFilter.filterDisplayName` 是**单条**字段，三个 `filterByXxx` 都往它写；
   `FilterSheet` 的 `displayName` 又无条件用它初始化并渲染「分类」行。按收款方跳转时
   `filterDisplayName = "盒马"`，于是分类行与对方框同时出现「盒马」。
   注意：三个入口本来就是从全新 `RecordFilter(...)` 构造的，门禁里写的「沿用旧快照」
   不成立，此处按源码更正。

### 7.2 修法

- ROOT 扇区 / 图例 → `onOpenCategoryInDetail(id, true, ...)`；CHILD 保持 `false`。
  弹窗保留并新增 `onOpenRootInDetail` 按钮，避免「只能跳二级」在任何路径重现。
- 单条展示名拆成 `categoryDisplayName` / `counterpartyDisplayName` / `accountDisplayName`；
  三个入口抽成 `RecordFilter.forCategory/forCounterparty/forAccount` 纯 JVM 工厂，
  新增 `FilterValuesMutualExclusionTest` 断言互斥与 `activeCount()`。
- `FilterSheet` 分类行只读 `categoryDisplayName`；对方框仍读 `counterpartyQuery`；
  账户行仍按 `accountId` 反查。三者互不串味。

### 7.3 12M / 24M 订正（重要）

上一批门禁把「坚果云 24M、GitHub artifact 12M」判成 debug 变体 vs release 变体，
**是错的**。读 `release.yml` 可知：`rename` 步骤的 `apk_name` 取
`*-minified-<sha>.apk`，`upload_release_apk` 与坚果云用的**是同一个文件**，
即 `-PminifyRelease=true` 产出的 R8 release 包。GitHub Actions artifact 的
`size_in_bytes` 是二次压缩后的 zip 体积，APK 里的 `.so` 常以 store 方式存放，
二次压缩能显著缩小，于是同一文件显示成 12M / 24M 两个数。`minified` 不是误命名。
本批只做命名规范化（`minified` → `release`），**不改上传源**，并把口径写进活文档 §7.13。

### 7.4 回退顺序（动手前写死）

1. 若 `PullToRefreshBox` / 跨 Tab 传参编译失败 → 只回退对应文件，不动 `RecordFilter`。
2. 若一级下钻命中为空 → 核查 `rootCategoryId` 是否为 null；`pagedFiltered` 已接参数，
   MUST NOT 另开 `@Query`。
3. 若 `release.yml` 改名后产物匹配不到 → 退回 `*-minified-*.apk` 匹配，
   命名改动独立回滚，不影响 APK 内容。
4. 三项任一真机验不过 → 不做无限迭代，按活文档 §1 登记后单独建 change。

## 8. 第 28 批实测回填（真实编号）

| 项 | 实测 |
|---|---|
| 代码 commit | `9d786823d666d747e628546f21cf82cee6514aa5`（13 改 / 1 新增，v0.2.12） |
| push CI | `44271311112` **success**，17 步全绿，**首轮 Kotlin 编译零错误** |
| release | `44272571557` **success**，23 步绿 / 5 步按 input 跳过；**上传到坚果云 success** |
| 制品 | `offline-ledger-release-v0.2.12` 12319640 B；`offline-ledger-mapping-v0.2.12` 3672809 B；`offline-ledger-metrics-v0.2.12-p-m-nsfalse` 271 B |
| 坚果云 | `nsfalse`（认证 / 建目录 / PUT 均未失败）；HEAD 落盘校验只写日志不中断，不当作已验证 HEAD 200 |
| 静态检查 | 六个 `check_*.py` 本地与 CI 均 PASS；新增 `FilterValuesMutualExclusionTest` 由 CI 单测步骤覆盖 |

**两处查证后更正（写进活文档 §7.13）**：
1. 门禁里写「`filterByXxx` 沿用旧快照」不成立——三个入口本来就从全新
   `RecordFilter(...)` 构造，串味的真因是单条 `filterDisplayName` 被分类行共用。
2. 门禁里写「若 `pagedFiltered` 未接 `rootCategoryId` 就补一条」不需要——
   第 27 批起该参数化短路已存在，本批零新增聚合 SQL 出口。
3. 「12M 是 release、24M 是 debug」的归因是错的：两者是同一个 R8 release 包，
   GitHub Actions artifact 显示的是二次压缩后的 zip 大小。本批只改产物名，不改上传源。

## 9. 第 29 批根因与回退顺序（v0.2.13）

**根因**：分类显示名在三个消费方各自拼装——`StatsViewModel.toSlices` 的
`nameOf` 只返回裸二级名（占比图例据此渲染、扇区点击据此传 `displayName`），
`FilterSheet` 第 96/131 行自己拼 `parent + " / " + name`，`DetailScreen` chip
兜底反查也只取裸名。所以「手工选显示 餐饮/其他，统计跳转却只有 其他」，
且不同一级下的同名「其他」在饼图里无法分辨。这不是 SQL 或条件对象的问题，
`rootCategoryId` / `categoryId` 一直传对了。

**修法**：新增纯 JVM `CategoryDisplay.resolve(parentName, leafName)` 作唯一出口；
`StatsViewModel` 增 `nameOfChild` 让 `childSlices` 的 name 带父级（图例与下钻
自动受益），排行内联展开改回裸名避免在父级下重复；`FilterSheet` 两处拼接与
`DetailScreen` 兜底反查全部改调 `resolve`；新增 `CategoryDisplayTest` 锁死
三种入口输出一致。零表结构、零 DAO 改动、零新增 SQL 出口。

**第 3 条前提更正（重要）**：第 28 批实测回填已查明「GitHub Actions Artifacts
页面约 12M 是 artifact zip 二次压缩后尺寸，坚果云约 24M 是同一个 R8 release
APK 的原始字节」，`release.yml` 的 rename 步骤本来就优先选 `*-release-*.apk`
且文件名已带短 sha。第 29 批门禁里退回「24M = debug 未收缩」并设计
「体积断言 11–13M 否则 fail」是错误结论——照做会让 CI 必然失败。
本批**不改上传选择逻辑、不加体积断言**，只在坚果云步骤补形态证据日志
（minify input / 原始字节 / mapping 存在性 / artifact 口径说明），并把口径写进
活文档 §7.14，避免再次误判。

**三级回退顺序**：
1. 若 `childSlices` 带父级名导致排行 / 合计校验文本异常 → 只在
   `DonutInsightCard` 渲染前 map 一份带父级副本，不动 ViewModel 聚合。
2. 若 `CategoryDisplay` 引入编译解析问题 → 退到各文件内私有扩展函数，
   仍保持单一文案常量。
3. 若新单测在 CI 崩溃 → 先移除单测文件，保留实现，单独诊断 JVM 测试源集。

按指南 §4，所有替换断言「每处唯一匹配」，新增文件不覆盖既有路径。
