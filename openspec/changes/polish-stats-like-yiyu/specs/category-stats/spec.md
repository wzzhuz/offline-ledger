## MODIFIED Requirements

### Requirement: 环形图支持一级与二级切换并落地三级下钻

统计页 SHALL 提供「按大类 / 按小类」切换，一级口径 SHALL 走
`GROUP BY rootCategoryId`，二级口径 SHALL 走 `GROUP BY categoryId`，
两者 SHALL 按金额降序展示占比与具体金额。
点击一级扇区 SHALL 用 `WHERE rootCategoryId = :rootId GROUP BY categoryId`
展开该大类下二级构成；点击二级项 SHALL 进入该分类明细列表。
系统 MUST NOT 用递归或闭包表实现层级。
**第 68 批改法**：分类 / 交易对方 / 支出账户三个维度 SHALL 收进
**同一张「分类占比」合体卡**的维度 chip，占比环形图与排行图例列表
SHALL 在这张卡内同时可见、共享同一份净支出切片数据，
MUST NOT 出现两个不一致的合计；MUST NOT 再用互斥切换 chip 让两者二选一，
也 MUST NOT 回到第 13 批那种「为一屏塞下而合并折叠」的取巧。
维度切换 SHALL 只影响合体卡内部，MUST NOT 影响小结卡、趋势卡或统计口径。
排行 SHALL 由带唯一统计口径的只读 SQL 聚合得出，
MUST NOT 在 Kotlin 里对已加载分页数据分组。
合体卡 MUST NOT 因合并丢失下钻能力：扇区点击、图例整行点击（触摸区 ≥40dp）、
三个维度、一级项展开二级、二级子项点击，SHALL 全部接既有回调并复用
`StatsDrillRange` 与 `state.currencyCode`；「分类管理 ›」入口 SHALL 保留。
收款方 / 账户维度 SHALL 用既有 Top5 排行数据转成扇区，
其中心标题 SHALL 写明该维度名称，MUST NOT 把 Top5 合计标成「净支出」。
既有 6 条收支聚合 SQL、`ChartData.netSlices`、`colorHex` 与
「分类支出占比按净支出计算」MUST NOT 改动。

#### Scenario: 一级扇区下钻

- **GIVEN** 月报中「餐饮」为最大支出扇区
- **WHEN** 点击「餐饮」扇区或图例行
- **THEN** 跳到明细 Tab 并带该大类筛选（含其下全部分类）
- **AND** 与第 28 批既有下钻行为一致，不回退成弹窗

#### Scenario: 二级进入明细

- **GIVEN** 合体卡中「餐饮」一级行已展开二级
- **WHEN** 点击「食材」
- **THEN** 显示该分类的流水明细列表
- **AND** 明细走参数化 SQL 与 LIMIT，不全量加载

#### Scenario: 切换统计口径

- **GIVEN** 用户切到「按小类」
- **WHEN** 查看合体卡环形图或图例列表
- **THEN** 扇区与图例按 `categoryId` 聚合而非 `rootCategoryId`
- **AND** 合计与一级口径相等

#### Scenario: 三维度切换

- **WHEN** 在合体卡依次点「分类 / 收款方 / 账户」
- **THEN** 环与图例列表同步切换，点击各项分别进入对应下钻路径
- **AND** 收款方 / 账户维度中心标题不是「净支出」

#### Scenario: 排行按当前币种

- **GIVEN** 账本含 CNY 与 HKD 两类支出、当前选中 CNY
- **WHEN** 查看交易对方排行
- **THEN** 只统计 CNY，MUST NOT 跨币种混算

#### Scenario: 合并后仍可展开二级

- **WHEN** 合体卡「分类」维度下一级行右侧「⌄」被点
- **THEN** 该一级下二级构成展开，二级行可直接跳明细

### Requirement: 分类图标不做字符串映射

分类图标 SHALL 由 `CategoryIconCatalog` 的**编译期 `when(iconKey)` 常量表**
映射到随包本地 VectorDrawable，MUST NOT 在运行时用字符串反射 / 查表 / 模糊
匹配生成 `ImageVector`（活文档 T14：整份字符串→图标映射表编译失败）。
**第 68 批彩色范围第三次放宽**：分类管理列表页、分类排序页（灰阶）、
记一笔「分类 ›」与明细筛选「分类」两个全量选择器，
以及**统计页分类占比合体卡的图例列表** SHALL 使用本地
`CategoryIconCatalog` 呈现彩色具象图标；记一笔快捷栏、明细列表行、
账户详情 SHALL 继续使用 `CategoryIcon` 的中性圆 + 分类名中文首字，
MUST NOT 受本批影响。
统计页占比图例 SHALL 复用 `CategoryGlyph`（`iconName` 为空时按预置 id 回查
`CategoryIconKeys`，覆盖安装零写库），目录或缺项表查不到的分类
SHALL 回退 `CategoryIcon` 中性圆 + 首字，页面 MUST NOT 有空白格、MUST NOT 崩溃。
图标配色 MUST 走 `CategoryIconTints`，MUST NOT 改写 `colorHex`；
统计扇区色板 MUST NOT 换成图标配色，两套管径 MUST 分离。

#### Scenario: 彩色图标进统计占比图例

- **GIVEN** 统计页占比合体卡处于「分类 / 按小类」维度
- **WHEN** 查看图例列表
- **THEN** 预置分类显示彩色 Tabler 图标且按 id 稳定
- **AND** 环形扇区颜色与图标 tint 互不干扰

#### Scenario: 覆盖安装仍能显示

- **GIVEN** 从 v0.2.35 之前覆盖安装的用户库 `iconName` 为空串
- **WHEN** 打开统计页占比合体卡
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
**统计页分类占比合体卡图例** SHALL 共用同一份 `CategoryIconCatalog`
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
- **WHEN** 打开统计页占比合体卡
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

## ADDED Requirements

### Requirement: 分类占比与排行合体卡采用一羽式图例列表

统计页 SHALL 用**单张**「分类占比」卡同时承载环形图与分类 / 收款方 /
账户排行，MUST NOT 再挂载独立的「分类排行」卡。
合体卡 SHALL 按一羽式组织：标题行（含「分类管理 ›」）→ 维度 chip →
（分类维度专属）按大类 / 按小类 chip → 环形图（中心写净支出）→
**一羽式图例列表**。环与图例列表 SHALL 同时可见，允许纵向滚动，
MUST NOT 用互斥切换 chip 二选一。
图例列表每行 SHALL 含：分类彩色图标（或中性圆 + 首字兜底）、名称、
百分比、金额，整行 SHALL 可点且触摸区 ≥40dp，点击 SHALL 与对应扇区
共用同一个下钻回调。百分比 SHALL 由整数分 `cents / total` 换算，
结果只进显示，MUST NOT 回写金额或参与统计。
图例行 SHALL 按金额降序排列，与环形扇区顺序一致，
MUST NOT 出现两个不一致的合计。
分类维度下一级行 SHALL 可展开其二级构成（复用 `state.rankChildren`），
二级行 SHALL 可直接跳明细；收款方 / 账户维度 SHALL 带卡号显示名
（沿用第 62 批 `accountBaseLabel` 单一出口）。
**明确不做**：图例「(N条)」笔数（分类维度 `ChartSlice` 无 count，
新增分类笔数聚合会撞「既有聚合 SQL 不改」）；
环外悬浮百分比文字（`DonutChart` 禁用 `DrawScope.drawText` / `TextMeasurer`，
改由图例列表承载百分比）。两者 MUST NOT 伪造数据或假装已实现。

#### Scenario: 一羽式图例五行

- **GIVEN** 净支出 510.00，餐饮 223.00 / 交通 95.00 / 日用品 89.00 /
  购物 58.00 / 外卖 45.00（按小类）
- **WHEN** 查看合体卡
- **THEN** 环与五行图例同屏可见，每行显示图标 / 名称 / 百分比 / 金额
- **AND** 餐饮百分比为 43.73%，五行金额合计等于中心净支出

#### Scenario: 图例整行可点

- **WHEN** 点图例某行名称或图标
- **THEN** 与点对应扇区行为一致，跳到明细 Tab 并带该分类筛选

#### Scenario: 二级展开

- **GIVEN** 分类维度下「餐饮」有二级构成
- **WHEN** 点一级行右侧「⌄」
- **THEN** 展开二级行，点二级行直接进入该分类明细

#### Scenario: 不显示伪造笔数

- **WHEN** 查看图例列表
- **THEN** 不出现「(N条)」字样
- **AND** `RecordDao` 既有聚合 SQL 零改动

#### Scenario: 无支出

- **GIVEN** 本周期无支出切片
- **WHEN** 查看合体卡
- **THEN** 环显示空态、图例显示「本期没有支出记录」，无崩溃
