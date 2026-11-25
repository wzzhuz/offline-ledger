## MODIFIED Requirements

### Requirement: 环形图支持一级与二级切换并落地三级下钻

统计页分类占比卡 SHALL 提供「大类 / 小类」切换，一级口径 SHALL 走
`GROUP BY rootCategoryId`，二级口径 SHALL 走 `GROUP BY categoryId`，
两者 SHALL 按金额降序展示占比与具体金额。
**第 70 批改法**：卡片左上角 SHALL 为「蓝色竖条 + **纯文本**『大类』/『小类』
+ **独立的可点切换图标**」三部分；文本 MUST 只写口径名，
MUST NOT 把 ⇄ 拼进文本或 FilterChip 里，切换 MUST 由旁边单独的图标按钮承担
（一羽真机图 1/2/3 的 `▌大类 ⇌` / `▌小类 ⇌`）。
**第 71 批**：那个独立图标 SHALL 是**半箭头**造型（形如 `⇌`）——
上箭头只有上半个尖（一条横线 + 右端一条「左上→右端点」斜线）、
下箭头只有下半个尖（一条横线 + 左端一条「右下→左端点」斜线），
MUST NOT 再用上下两条斜线拼成完整三角箭头（第 70 批那个太"实"）。
图标 SHALL 用 Canvas `drawLine` 自绘，零新增依赖 / 零资源 / 零实验性 API。
**第 72 批**：按真机对比，上一版 20dp 图标 / 2f 线宽 / 饱和蓝 / 32dp 按钮盒
「太大、太丑、很突兀」，与粗体口径文字视觉重量失衡。本批 SHALL 改为
**14dp 图标 / 1.2f 线宽 / `CategoryHeaderBlue` 55% 透明浅蓝灰 / 24dp 按钮盒**，
紧贴「大类 / 小类」文字（无额外起始间距），做成一羽那种不抢标题的细小标记；
半箭头形状 MUST 保持不变。MUST NOT 放大回 20dp、MUST NOT 用饱和蓝、
MUST NOT 加回 32dp 包围盒。
MUST NOT 新增右上「支出 ⇌」类按钮（使用者未要求，且第 69 批已因加戏返工）。
MUST NOT 用两枚 FilterChip，MUST NOT 在「大类」口径下提供一级行「⌄」内联展开二级
（要看小类 MUST 直接点切换图标）。
分类占比卡 SHALL 只承载分类维度，收款方 / 账户 MUST 拆回独立默认折叠卡，
MUST NOT 混进本卡、MUST NOT 用维度 chip 在本卡内切换。
环形图与图例列表 SHALL 在这张卡内同时可见、共享同一份净支出切片数据，
MUST NOT 出现两个不一致的合计；MUST NOT 用互斥切换 chip 让两者二选一。
排行 SHALL 由图例列表承担，每一条 SHALL 由带唯一统计口径的只读 SQL 聚合得出。
点击一级扇区 / 图例行 SHALL 按 `rootCategoryId` 查该大类下全部流水，
点击二级扇区 / 图例行 SHALL 按 `categoryId` 查该分类流水，
两者 SHALL 以**统计页内底部 sheet** 按明细页行样式展示，
MUST NOT 跳明细 Tab、MUST NOT 向 `DetailViewModel` 注入状态。
扇区点击与图例整行点击（触摸区 ≥40dp）SHALL 共用同一个回调；
「分类管理 ›」入口 SHALL 保留。
环中心 SHALL 统一写「净支出 ¥X」。
**第 70 批新增 / 第 72 批订正**：环形图 SHALL 为每个扇区绘制环外指示线
（见本 change ADDED），由**径向短线 + 严格水平折线 + 端点小圆点**组成，
末端标注「分类名 百分比」，颜色 SHALL 与该扇区一致；
折点 MUST 沿各扇区自身中点角径向延伸（MUST NOT 把折点 x 固定在同一竖线上，
否则多扇区会扇形汇聚成交错），同侧标签 MUST 随角度有序、不互相重叠。
既有 6 条收支聚合 SQL、`ChartData.netSlices`、`colorHex` 与
「分类支出占比按净支出计算」MUST NOT 改动。

#### Scenario: 文本与半箭头图标分离

- **GIVEN** 分类占比卡停在「大类」
- **WHEN** 查看左上角
- **THEN** 显示蓝色竖条 + 粗体「大类」文本 + 旁边一个独立的半箭头 `⇌` 图标按钮
- **AND** 上箭头只有上半个尖、下箭头只有下半个尖，不是完整三角箭头
- **AND** 文本里没有 ⇄、没有 chip 边框、没有右上「支出 ⇌」按钮

#### Scenario: 图标小细淡不抢标题

- **GIVEN** 分类占比卡停在「小类」
- **WHEN** 对比一羽真机图与我们的标题栏
- **THEN** 切换图标为 14dp、1.2f 细线、浅蓝灰（`CategoryHeaderBlue` 55%）、
  24dp 按钮盒，紧贴「小类」文字右侧
- **AND** 视觉重量与口径文字接近，MUST NOT 是 20dp 饱和蓝粗线大箭头
- **AND** 仍可点且点击行为不变（切到大类）

#### Scenario: 点图标切口径

- **WHEN** 点那个独立图标
- **THEN** 切到「小类」，环与图例列表按 `categoryId` 聚合
- **AND** 合计与一级口径相等，卡片内没有第二枚口径 chip、没有「⌄」

#### Scenario: 点一级看该大类流水

- **GIVEN** 「大类」口径下「餐饮」为最大扇区
- **WHEN** 点「餐饮」扇区或图例行
- **THEN** 统计页内弹出底部 sheet，按 `rootCategoryId` 显示该大类全部流水
- **AND** 不切换 Tab、不写明细筛选

#### Scenario: 指示线标注每个扇区

- **WHEN** 环上有 4 个扇区
- **THEN** 每个扇区都有一条同色指示线，末端写「名称 百分比」
- **AND** 同一侧的标签上下错开，不与环重叠

#### Scenario: 三维度不混卡

- **WHEN** 查看分类占比卡
- **THEN** 卡内只有分类维度，收款方 / 账户在另一张默认折叠卡上

#### Scenario: 排行按当前币种

- **GIVEN** 账本含 CNY 与 HKD 两类支出、当前选中 CNY
- **WHEN** 查看收款方 / 账户折叠卡
- **THEN** 只统计 CNY，MUST NOT 跨币种混算

### Requirement: 分类图标不做字符串映射

分类图标 SHALL 由 `CategoryIconCatalog` 的**编译期 `when(iconKey)` 常量表**
映射到随包本地 VectorDrawable，MUST NOT 在运行时用字符串反射 / 查表 / 模糊
匹配生成 `ImageVector`（活文档 T14：整份字符串→图标映射表编译失败）。
分类管理列表页、分类排序页（灰阶）、记一笔「分类 ›」与明细筛选「分类」
两个全量选择器，以及**统计页分类占比卡图例列表** SHALL 使用本地
`CategoryIconCatalog` 呈现彩色具象图标；记一笔快捷栏、明细列表行、
账户详情 SHALL 继续使用 `CategoryIcon` 的中性圆 + 分类名中文首字，
MUST NOT 受本批影响。
统计页占比图例 SHALL 复用 `CategoryGlyph`（`iconName` 为空时按预置 id 回查
`CategoryIconKeys`，覆盖安装零写库），目录或缺项表查不到的分类
SHALL 回退 `CategoryIcon` 中性圆 + 首字，页面 MUST NOT 有空白格、MUST NOT 崩溃。
图标配色 MUST 走 `CategoryIconTints`，MUST NOT 改写 `colorHex`；
统计扇区色板 MUST NOT 换成图标配色，两套管径 MUST 分离。

#### Scenario: 彩色图标进统计占比图例

- **GIVEN** 统计页占比卡处于「按小类」口径
- **WHEN** 查看图例列表
- **THEN** 预置分类显示彩色 Tabler 图标且按 id 稳定
- **AND** 环形扇区颜色与图标 tint 互不干扰

#### Scenario: 覆盖安装仍能显示

- **GIVEN** 从 v0.2.35 之前覆盖安装的用户库 `iconName` 为空串
- **WHEN** 打开统计页占比卡
- **THEN** 按预置 id 回查正常显示彩色图标
- **AND** 零写库、零改表

#### Scenario: 缺项回退

- **WHEN** 用户手工新建的分类或目录缺项出现在占比图例
- **THEN** 回退中性圆 + 中文首字，无空白格、无崩溃

#### Scenario: 其余页面零回归

- **WHEN** 打开记一笔快捷栏、明细列表行、账户详情
- **THEN** 仍是中性圆 + 中文首字，MUST NOT 出现彩色图标

### Requirement: 分类管理彩色图标目录与缺项兜底

分类管理列表页、分类排序页（灰阶）、两个全量选择器与
**统计页分类占比卡图例** SHALL 共用同一份 `CategoryIconCatalog`
编译期常量表；目录 SHALL 来自 Tabler Icons v3.34.0（MIT，pin 版本），
本地 VectorDrawable，零新增 Gradle 依赖、零运行时网络。
用户手工新建分类的 id 不在预置表内，SHALL 回退中性圆 + 中文首字。
目录或缺项表查不到的分类 SHALL 回退中性圆 + 首字，页面 MUST NOT 有空白格、
MUST NOT 崩溃。配色 MUST 走 `CategoryIconTints` 并按领域分组、同组同色，
MUST NOT 逐图标随机上色；`colorHex` MUST NOT 被改写，
统计扇区色板 MUST NOT 改为图标配色。
放行彩色图标 MUST 补一份图标数据；MUST NOT 复制一羽的视觉资产。
记一笔快捷栏 / 明细列表行 / 账户详情的分类标识 SHALL 仍是中性圆 + 首字。

#### Scenario: 预置分类按 id 回查

- **GIVEN** 全新安装或覆盖安装（老库 `iconName` 空串）
- **WHEN** 打开统计页占比卡
- **THEN** 预置分类按 id 回查目录正常显示彩色图标

#### Scenario: 自定义分类回退

- **WHEN** 用户手工新建分类出现在占比图例
- **THEN** 该格显示中性圆 + 首字，页面无空白格、无崩溃

#### Scenario: 配色不串味

- **WHEN** 同一分类同时出现在彩色图例与环形扇区
- **THEN** 图标 tint 与扇区色各自独立，`colorHex` 未被改写

#### Scenario: 其余页面零回归

- **WHEN** 打开记一笔快捷栏 / 明细列表行 / 账户详情
- **THEN** 分类标识仍是中性圆 + 首字

### Requirement: 三级下钻路径

系统 SHALL 支持「分类占比卡 → 该分类流水」的连续路径，
MUST NOT 让使用者看完图还得重新去翻账。
**第 69 批改法**：下钻落点 SHALL 为**统计页内底部 sheet**
（按明细页行样式渲染），「按大类」口径走 `rootCategoryId` 查该大类全部流水、
「按小类」口径走 `categoryId` 查该分类流水；
「要看小类」SHALL 通过左上角「按大类 ⇄ / 按小类 ⇄」标签切换，
MUST NOT 用一级行「⌄」内联展开二级构成。
明细 SHALL 走带 `LIMIT :limit` 与 `excludeFromStats = 0` 唯一口径的参数化 SQL，
MUST NOT 全量加载、MUST NOT 新建第二套分页列表。
系统 MUST NOT 跳明细 Tab、MUST NOT 向 `DetailViewModel` 注入筛选状态、
MUST NOT 在 sheet 内提供「去明细」按钮。

#### Scenario: 下钻到页内流水

- **GIVEN** 月报中「餐饮」为最大支出扇区
- **WHEN** 点「餐饮」扇区或图例行
- **THEN** 统计页内底部 sheet 显示该大类全部流水，页面不切 Tab
- **AND** 每行按明细页样式显示方向箭头 / 主标题 / 副行 / 账户名 / 金额

#### Scenario: 按小类直接切

- **WHEN** 使用者想看「食材」而非整个「餐饮」大类
- **THEN** 左上角切到「按小类 ⇄」后点「食材」图例即可
- **AND** 全程无一级「⌄」展开、无二级构成弹窗

### Requirement: 下钻明细复用组合筛选且不全量加载

**第 69 批整块替换**：统计页点击分类查看流水时，系统 SHALL 在
**统计页内**以底部 sheet 展示，SHALL 带入 `categoryId`（按小类）或
`rootCategoryId`（按大类）、当前 `StatsDrillRange` 与当前币种，
复用带 `LIMIT :limit` 的既有 `drillDownRowsForCurrency` /
`drillDownRowsForRootCurrency` 只读 SQL，
MUST NOT 跳转明细 Tab、MUST NOT 调用 `detailVm.filterByCategory` /
`filterByCounterparty` / `filterByAccount`、MUST NOT 向明细页注入
只读展示名或「来自统计」筛选 chip、MUST NOT 新建第二套分页列表。
sheet 内每行 SHALL 按明细页 `RecordRowCard` 的样式渲染：
方向箭头、描述 / 收款方 / 分类主标题、「分类 · 收款方 · 备注」副行、
经 `accountLabel` 组合的带卡号账户名、红 / 绿金额、
划线原价与「优惠 / 手续费」标签、日期，
SHALL 由扩展后的 `DrillRow` 投影一次 LEFT JOIN 取回，
MUST NOT 渲染时逐行查库（O(n²)）。
`DrillRow` 新增字段 SHALL 全部带默认值，旧调用点零改动可编译；
新增字段 MUST NOT 改写统计口径或 `SUM(amountCents)` 结果。
`DetailViewModel` 的三个 `filterBy*` 方法 SHALL 保留不删
（两个进行中 change 正在改明细侧），但统计侧 MUST NOT 再触发它们。
分类占比卡的扇区与**图例** SHALL 都能触发同一份页内下钻。

#### Scenario: 点分类只开 sheet

- **WHEN** 使用者在占比卡点某个扇区或对应图例
- **THEN** 统计页内弹出底部 sheet，列表按该分类 + 当前币种 + 当前周期筛选
- **AND** 不切 Tab、明细页筛选 chip 不出现「来自统计」

#### Scenario: 明细行带账户与优惠

- **GIVEN** 该分类下有一笔「工商银行 (1122)」支出、原价 100 实付 99 优惠 1
- **WHEN** 打开 sheet
- **THEN** 该行显示账户名带卡号、划线原价与「优惠 1.00」标签、实付 99.00
- **AND** 与明细页同一行样式

#### Scenario: 不全量加载

- **WHEN** 打开 sheet
- **THEN** 最多显示 100 条、按日期倒序，SQL 带 `LIMIT :limit`
- **AND** 不加载该分类全量流水、不新建分页列表

#### Scenario: 唯一口径

- **WHEN** 新增 `drillDownRowsForRootCurrency` 被 `check_stat_rules.py` 校验
- **THEN** 它带 `type IN ('EXPENSE','INCOME') AND excludeFromStats = 0` 与 `LIMIT :limit`

## ADDED Requirements

### Requirement: 分类占比卡采用一羽式图例列表、灰色长条与环外指示线

统计页 SHALL 用**单张**「分类占比」卡同时承载环形图与分类排行，
MUST NOT 再挂载独立的「分类排行」卡，也 MUST NOT 把收款方 / 账户混进本卡。
卡片左上角 SHALL 为「蓝色竖条 + 纯文本『大类』/『小类』 + 独立切换图标按钮」，
标题右侧 SHALL 保留「分类管理 ›」；MUST NOT 出现 FilterChip 式口径标签、
维度 chip、一级行「⌄」或右上「支出 ⇌」按钮。
环与图例列表 SHALL 同时可见，允许纵向滚动。
环中心 SHALL 统一写「净支出 ¥X」，由 `state.netExpenseCents` 提供。
**第 70 批新增（撤回第 68/69 批两次「明确未做」）**：SHALL 为每个扇区绘制
**环外指示线**：从扇区外缘按中点角引出斜线，再折成水平线到卡片左 / 右边缘，
端点画小圆点，末端用普通 Compose `Text` 标注「分类名 百分比」，
颜色 SHALL 与扇区一致；同侧标签 SHALL 按 y 排序并下推错开，
MUST NOT 互相重叠、MUST NOT 盖住环。
**第 72 批按一羽真机图重做（撤回第 71 批算法）**：使用者第 71 批后真机仍见
交错，根因是**折点 x 被固定在同一条竖线** `cx ± (outer + lead)` ——
多个扇区外缘点被一起拉到同一竖线，形成扇形汇聚，尤其大扇区占多数、
小扇区挤在窄角度时（真机图三 6 类 / 图四 12 类）必然交叉。
本批 SHALL 改为**径向折点**：
① 折点 `knee = (cx + (outer+lead)*cos(mid), cy + (outer+lead)*sin(mid))`，
即每个扇区沿**自身中点角**从环外缘再往外走 `lead`，x 各不相同、
沿环外缘弧分布；`anchor → knee` SHALL 是严格径向短线，
从圆心发散、数学上 MUST NOT 相交；
② `knee → (endX, knee.y)` SHALL 严格水平，标签 y SHALL 等于 `knee.y`
（文字贴在该水平线上方约 16dp），**取消一切标签 y 下推 / 整体平移**，
折点 y、圆点 y、线 y 三者同源；同侧标签顺序天然随角度有序；
③ 每侧 SHALL 按 `knee.y` 排序后计算**相邻折点 y 的最小间隔**，
仅当该间隔 `< minGap`（18dp）时，才按金额降序（全局 index 升序）
降级到 **Top3**；若 Top3 仍挤，SHALL 继续按金额从大到小保留，
直到相邻间隔达标或只剩 1 条（不交错优先）。
**判据 MUST 用实际折点 y 的最小相邻间隔，MUST NOT 再用
「整个图表高度 / minGap」算容量** —— 小扇区挤在窄角度里时高度判据会
误判成「放得下」，正是第 71 批没兜住真机图四的原因。
正常数据（一羽图一 5 类、图二 12 类，以及本 App 大类 6~7 类）
MUST 全量标注、MUST NOT 提前降级；环与图例列表 MUST 始终全量，
降级只影响指示线，MUST NOT 新增任何提示文字。
实现 SHALL 用 Canvas `drawLine` / `drawCircle` + `Modifier.onSizeChanged`
量尺寸 + 普通 `Text` 的 `absoluteOffset` 叠加，
**MUST NOT** 使用 `DrawScope.drawText` / `TextMeasurer` / `FlowRow` /
实验性 API，MUST NOT 在 `DrawScope` 内回写 state。
环半径 SHALL 留出左右文字空间（本批取 `min(cx,cy) * 0.60f`），
扇区点击命中判定 MUST 与绘制半径同源。
图例列表每行 SHALL 含：分类彩色图标（或中性圆 + 首字兜底）、名称、
百分比、¥金额，以及**灰色长条**——浅灰轨道 `0xFFEDEFF5` +
低饱和 `0xFF5B7CFA` 填充，宽度 SHALL 等于该行占比。
整行 SHALL 可点且触摸区 ≥40dp，点击 SHALL 与对应扇区共用同一个
页内 sheet 下钻回调。图例行 SHALL 按金额降序排列，与环形扇区顺序一致。
百分比 SHALL 复用纯 JVM `StatsLegend`，MUST NOT 在 Compose 里写第二套换算。
**明确不做**：图例「(N条)」笔数（分类维度 `ChartSlice` 无 count，
新增分类笔数聚合会撞「既有聚合 SQL 不改」）。两者 MUST NOT 伪造数据。

#### Scenario: 一羽式图例五行

- **GIVEN** 净支出 510.00，餐饮 223.00 / 交通 95.00 / 日用品 89.00 /
  购物 58.00 / 外卖 45.00（小类）
- **WHEN** 查看占比卡
- **THEN** 环与五行图例同屏可见，每行显示图标 / 名称 / 百分比 / ¥金额 / 灰色长条
- **AND** 餐饮百分比为 43.73%、灰条最长，五行金额合计等于中心净支出

#### Scenario: 指示线与扇区同色

- **GIVEN** 「餐饮 43.73%」为右侧最大扇区
- **WHEN** 查看环
- **THEN** 右侧有一条与餐饮扇区同色的指示线，末端写「餐饮 43.73%」
- **AND** 左侧其余扇区各有自己的指示线，上下错开不重叠

#### Scenario: 径向折点不汇聚、水平段严格水平不交错

- **GIVEN** 小类口径下左侧有 4 个扇区、右侧 1 个
- **WHEN** 查看环外指示线
- **THEN** 每个折点都在该扇区自身中点角的径向延长线上，x 各不相同
- **AND** `anchor → knee` 是从圆心发散的径向短线，任意两条互不相交
- **AND** 每条水平段都是严格水平线、彼此平行且 y 互不相同
- **AND** 折点 y、圆点 y、水平线 y 三者一致，文字贴在线上方不压线
- **AND** 同侧标签顺序与扇区在环上的顺序一致，MUST NOT 出现扇形汇聚

#### Scenario: 大扇区占多数时也不交错

- **GIVEN** 大类口径「活 83.21%」+ 6 个小类挤在左上窄角度（真机图三）
- **WHEN** 查看环外指示线
- **THEN** 6 条指示线各自从自己的扇区外缘沿径向引出，不汇聚到同一竖线
- **AND** 没有任何两条线交叉，标签随角度从上到下有序

#### Scenario: 标签贴线且不越界

- **GIVEN** 最上方与最下方扇区靠近图表上下边缘
- **WHEN** 查看标签
- **THEN** 每个标签 y 等于自己折点的 y、落在该水平线上方约 16dp
- **AND** 不跑出图表、不盖住下方图例、不盖住环
- **AND** 没有标签 y 下推 / 整体平移，MUST NOT 出现「改了标签 y 没改折点 y」

#### Scenario: 正常数据不降级 / 真挤才 Top3

- **GIVEN** 一羽图一 5 类、图二 12 类，本 App 大类 6~7 类
- **WHEN** 查看指示线
- **THEN** 所有扇区全部有指示线（相邻折点 y 间隔 ≥ minGap），MUST NOT 提前降级
- **AND** 仅当某侧相邻折点 y 的最小间隔 < minGap（真机图四 11 个小类挤在
  77.86% 大扇区之外的窄角度）时，该侧才按金额降序只标 Top3
- **AND** 若 Top3 仍挤则继续减到不交错或只剩 1 条
- **AND** 判据 MUST NOT 用整个图表高度算容量
- **AND** 降级时环与图例列表仍全量，MUST NOT 少画扇区或少列图例

#### Scenario: 不交错优先于标注数量

- **GIVEN** 某侧降级到 Top3 后相邻折点 y 间隔仍 < minGap
- **WHEN** 生成指示线
- **THEN** 继续按金额从大到小保留，直到间隔达标或只剩 1 条
- **AND** 宁可少标也 MUST NOT 画出交错的线

#### Scenario: 不用文本测量 API

- **WHEN** 检查 `DonutChart.kt` 实现
- **THEN** 没有 `drawText(` / `TextMeasurer` / `FlowRow` import 或调用
- **AND** 标签是普通 Compose `Text`，尺寸来自 `onSizeChanged`

#### Scenario: 灰条长度与占比一致

- **WHEN** 对比「餐饮 43.73%」与「外卖 8.82%」两行
- **THEN** 前者灰条填充明显长于后者，宽度比等于金额比

#### Scenario: 图例整行可点

- **WHEN** 点图例某行名称或图标
- **THEN** 与点对应扇区行为一致，弹出页内底部 sheet

#### Scenario: 不显示伪造笔数

- **WHEN** 查看图例列表
- **THEN** 不出现「(N条)」字样

#### Scenario: 无支出

- **GIVEN** 本周期无支出切片
- **WHEN** 查看占比卡
- **THEN** 环显示空态、无指示线、图例显示「本期没有支出记录」，无崩溃

### Requirement: 分类流水页内底部 sheet 按明细行渲染

统计页 SHALL 提供 `StatsDrillSheet`（`ModalBottomSheet`，
`@OptIn(ExperimentalMaterial3Api::class)`）作为分类下钻的**唯一**落点，
MUST NOT 使用居中 `AlertDialog` / `Dialog`、MUST NOT 跳明细 Tab。
sheet 标题 SHALL 为「{分类名} · 流水」，副标题 SHALL 写「最多 100 条，按日期倒序」。
`LazyColumn` 每行 SHALL 逐项对齐明细页 `RecordRowCard`：
30dp 方向箭头圆（← / → / ⇄ / ↩）、
描述 → 收款方 → 分类的主标题回退链、
「分类 · 收款方 · 备注」副行、
经 `accountLabel` 组合的带卡号账户名、
红 / 绿金额与 `signedAmount`、划线原价与蓝 / 灰「优惠 / 手续费」标签、
`MonthRange.dateLabel` 日期；
行高 SHALL ≥60dp，MUST NOT 用卡片堆叠与阴影。
数据 SHALL 来自扩展后的 `DrillRow` 投影（一次 LEFT JOIN categories + accounts），
`StatsViewModel.openSliceRows(categoryId, isRoot, displayName)` SHALL
按口径选 `drillDownRowsForCurrency` / `drillDownRowsForRootCurrency`，
两者 SHALL 带 `LIMIT 100` 与 `excludeFromStats = 0` 唯一口径。
`drillCategoryId != null` SHALL 为 sheet 唯一显示条件；
关闭 SHALL 走 `closeDrill()`，MUST NOT 残留「查看该大类全部流水 ›」
或「明细」等跨 Tab 按钮。
`DrillRow` 新增字段 SHALL 全部带默认值，
`version = 7` 与 `Migrations.ALL` MUST NOT 改动。
一羽「分类聚合页」是独立页面，本批按「页内 sheet」等价实现，
MUST NOT 冒充 1:1 复刻。

#### Scenario: 点餐饮弹 sheet

- **GIVEN** 「按大类」口径下餐饮有 4 笔流水
- **WHEN** 点餐饮扇区
- **THEN** 统计页内从底部弹出 sheet，显示 4 行明细页样式流水
- **AND** 页面仍是统计 Tab，明细页筛选状态未被改动

#### Scenario: 账户与优惠同行显示

- **GIVEN** 某笔支出来自「工商银行 (1122)」、原价 100 实付 99 优惠 1
- **WHEN** 打开 sheet
- **THEN** 该行显示带卡号账户名、划线 100.00、蓝标「优惠 1.00」、实付 99.00

#### Scenario: 无跨 Tab 按钮

- **WHEN** 查看 sheet 任意状态
- **THEN** 不出现「查看该大类全部流水 ›」「明细」「去明细」按钮
- **AND** 关闭后统计页周期 / 币种 / 口径状态不变
