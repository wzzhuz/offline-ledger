# 页面、性能与范围 Specification

## Purpose

定义页面结构、低摩擦交互、万级数据下的性能口径与 v1 范围边界。

---

## Requirements

### Requirement: 四 Tab 加中央记账按钮

底部导航 SHALL 为 4 个 Tab：明细 / 资产 / 统计 / 设置；中央 SHALL 为悬浮大号 `+` 按钮触发「记一笔」。设置 MUST NOT 作为第 5 个 Tab。

#### Scenario: 导航结构

- **GIVEN** 用户打开应用
- **WHEN** 查看底部导航
- **THEN** 恰好 4 个 Tab 与中央 `+`
- **AND** 设置从 Tab 进入，不额外占一格

**理由**：v1 规格书写「4 个核心页面」却列到页面 5，此处收口，避免生成代码时摇摆。

---

### Requirement: 记一笔保持低摩擦

记一笔 SHALL 记忆默认账户、上次分类与上次使用时间。系统 SHALL 提供三栏 Tab（支出 / 收入 / 转账）、分类网格、内置计算器键盘、账户胶囊、日期时间、备注与「不计入收支」开关。

#### Scenario: 日常记一笔

- **GIVEN** 用户点中央 `+`、点常用分类、输入金额
- **WHEN** 保存
- **THEN** 流水创建且余额联动
- **AND** 无需每次重选账户与分类

**理由**：「随手记，时间长了没动力」是原始背景里最真实的痛点，任何新增功能都不得让这条路径变长。

---

### Requirement: 明细分页且禁止全量内存分组

明细 SHALL 使用 `Paging 3` 分页（每页 50–100 条）与按 `localDate` 范围的 SQL 过滤，MUST NOT 一次性 `getAll()` 后在 Kotlin 内按日期分组。列表 SHALL 使用 `LazyColumn` 只渲染可见项。

#### Scenario: 5 万条切月

- **GIVEN** 账本含 5 万条流水
- **WHEN** 左右切换月份
- **THEN** 首屏 < 200ms 返回
- **AND** 内存不随总记录数线性增长

**理由**：行数本身不是瓶颈，几万个对象 inflate 才是。已记录过「分页后统计用已加载数」的同类坑。

---

### Requirement: 统计一律走 SQL 聚合

月度概览、分类占比、年度趋势 SHALL 由 SQL `GROUP BY` 返回聚合结果，数据库 SHALL 只向 UI 返回十几行结果。年度趋势 SHALL 为 12 行 × 收入/支出。

#### Scenario: 打开年报

- **GIVEN** 账本含 5 年流水
- **WHEN** 切换到年度视图
- **THEN** 聚合在 300ms 内完成
- **AND** UI 收到 12 行数据而非数万条记录

**理由**：每次进统计页都全量扫描重算，是万级数据下最明显的卡顿来源。

---

### Requirement: 冗余本地日期列

流水 SHALL 冗余 `localDate`（`YYYYMMDD`）与 `yearMonth`（`YYYYMM`），按设备本地时区计算。系统 MUST NOT 在 SQL 中对 `timestamp` 做函数运算来分组。v1 默认时区 SHALL 为 `Asia/Hong_Kong`、默认币种 `HKD`。

#### Scenario: 系统时间变更

- **GIVEN** 用户更改设备时区或系统时间
- **WHEN** 查看历史月报
- **THEN** 历史流水仍归在原自然月
- **AND** 不出现日期串月

**理由**：对 `timestamp` 做函数会让索引失效；冗余列同时解决索引与时区口径两个问题。

---

### Requirement: 中文语境配色

明细金额 SHALL 用「红 = 支出 / 赤字，绿 = 收入」，或 SHALL 提供配色开关。

#### Scenario: 查看明细

- **GIVEN** 用户打开明细页
- **WHEN** 浏览列表
- **THEN** 支出为红色、收入为绿色
- **AND** 与中文记账习惯一致

**理由**：v1 写成「支出绿色、收入红色」，与中文语境习惯相反。

---

### Requirement: v1 范围收敛

v1 SHALL 只包含：4 Tab + 记一笔 + 资产与对账 + 月/年报表 + 加密 JSON 备份 + 无网络权限。模板/周期账、快捷方式与小组件、预算、退款报销、多币种、CSV、账单导入 SHALL 列入 v1.1 清单，MUST NOT 在 v1 中悄悄加入。

#### Scenario: 范围蔓延

- **GIVEN** 有人提议 v1 顺手做预算提醒
- **WHEN** 提交 change
- **THEN** 评审发现属于 v1.1 清单
- **AND** 需先显式更新 `project.md` 的「已确认不做」

**理由**：范围蔓延是个人项目烂尾的首要原因；「已确认不做」清单就是防它悄悄回来的闸门。

---

### Requirement: 性能验收基线

系统 SHALL 以一次性生成的 5 万条假数据做性能自测，在中低端机上满足：冷启动到明细首屏 < 1s、切月 < 200ms、年报聚合 < 300ms、分页滚动无掉帧。

#### Scenario: 性能自测

- **GIVEN** 灌入 5 万条测试数据
- **WHEN** 逐项测量
- **THEN** 四项指标全部达标
- **AND** 未达标项回到数据层优化而非降低口径

**理由**：10 年个人账本约 2–5 万条，不提前定基线，性能问题只会在用了几年后才暴露。

---

### Requirement: 记一笔计算器全程整数精度

记一笔的内置计算器 SHALL 用 `BigDecimal` 求值表达式（支持 `+ - × ÷` 与小数），
MUST NOT 让 `Float` / `Double` 参与任何金额计算；除法 SHALL 用有限精度
`divide(..., 8, HALF_UP)` 防止无限小数；结果 SHALL 经 `setScale(2, HALF_UP)`
后交由 `Cents.fromYuan` 转整数分。

#### Scenario: 多件商品合并算账

- **GIVEN** 用户在计算器输入 `12.5+3×2`
- **WHEN** 保存该笔支出
- **THEN** 金额落库为 1850 分
- **AND** 表达式求值全程未出现浮点类型

#### Scenario: 除不尽

- **GIVEN** 输入 `10÷3`
- **WHEN** 求值
- **THEN** 得到 3.33 元（333 分）
- **AND** 不抛 `ArithmeticException` 无限小数错误

**理由**：`0.1+0.2` 类误差一旦写进账本就再也抹不掉，金额铁律必须在
最靠近用户输入的地方就生效。

---

### Requirement: 两级分类快选不得增加记账摩擦

分类选择 SHALL 先展示一级网格，点击一级 SHALL 展开该大类的二级 chips，
并 SHALL 提供「就按一级记」的直接保存路径。系统 MUST NOT 强制用户选到二级，
MUST NOT 提供三级及以上层级。

#### Scenario: 只选一级

- **GIVEN** 用户点中央 `+`、点一级「餐饮」、输入金额
- **WHEN** 直接保存
- **THEN** ≤ 3 次交互完成记账
- **AND** `categoryId = 1` 且 `rootCategoryId = 1`

#### Scenario: 选到二级

- **GIVEN** 用户点「餐饮」后展开二级并选「食材」
- **WHEN** 保存
- **THEN** `categoryId = 101` 且 `rootCategoryId = 1`
- **AND** 二级 chips 中最近使用项排在前面

**理由**：两级分类是使用者明确要求的能力，但「时间长了没动力」同样是原始背景，
多级分类不能把点两下变成点四下。

---

### Requirement: PagingData 独立于 UiState

明细 ViewModel SHALL 把 `PagingData` 作为独立的 `Flow` 属性，
MUST NOT 包装进 `UiState` data class。Pager 的 `Flow` SHALL 一次性创建于
ViewModel（非 Composable 重组内），SHALL 调用 `cachedIn(viewModelScope)`，
列表 SHALL 提供基于 `id` 的稳定 `itemKey`。

#### Scenario: 筛选面板开关不跳回顶部

- **GIVEN** 用户滚动到明细列表中部
- **WHEN** 打开或关闭筛选面板（只改变 UiState）
- **THEN** 列表滚动位置保持不变
- **AND** 不重新加载首屏

#### Scenario: 写操作后刷新

- **GIVEN** 用户保存一笔新流水
- **WHEN** `RecordFilter.revision` 递增
- **THEN** `flatMapLatest` 重建 Pager 并刷新列表
- **AND** 新流水出现在正确的日期分组中

**理由**：把 `PagingData` 塞进 UiState 是 Paging 3 已知反模式，
任何状态变化都会让列表弹回顶部；`revision` 比手工持有 PagingSource
引用去 `invalidate()` 少一条容易漏的路径。

---

### Requirement: 按日合计由 SQL 单独聚合

日期头显示的当日支出 / 收入 SHALL 由一条 `GROUP BY localDate` 的 SQL 返回
（每月最多 31 行），MUST NOT 用已加载的分页数据求和。

#### Scenario: 滚动不改动日期头

- **GIVEN** 某月有 300 笔流水，首屏只加载 60 条
- **WHEN** 用户向下滚动继续加载
- **THEN** 顶部各日期头的当日合计保持不变
- **AND** 数值等于该日全部流水的聚合结果

**理由**：用已加载数求和会随滚动跳变，正是 `project.md` 明确禁止的
`records.size` 类错误。

---

### Requirement: 四 Tab 骨架一次成型

系统 SHALL 保持恰好 4 个底部 Tab（明细 / 资产 / 统计 / 设置）与中央 `+` FAB。
资产页 SHALL 展示真实净资产看板、总资产 / 总负债、账户分组与对账入口；
统计页 SHALL 展示月 / 年报表（收支对比概览、分类占比环形图、月度趋势折线
与年度收支双折线），并 SHALL 支持一级 / 二级分类切换与下钻明细。
系统 MUST NOT 因某阶段内容为空而隐藏 Tab、临时把设置移到第 5 格，
也 MUST NOT 把核对视图做成第 5 个 Tab（核对视图 SHALL 为资产页内 Dialog）。
系统 SHALL 保持 4 Tab 结构稳定，后续阶段新增内容 MUST NOT 改变导航格数。

#### Scenario: 打开资产页

- **GIVEN** 应用已记入若干流水与账户
- **WHEN** 点底部「资产」
- **THEN** 显示净资产、总资产、总负债与账户分组
- **AND** 每个账户有「详情」与「对账」入口
- **AND** 底部导航仍是 4 个 Tab

#### Scenario: 打开统计页

- **GIVEN** 应用已记入若干流水与分类
- **WHEN** 点底部「统计」
- **THEN** 显示月 / 年切换、收支概览、分类占比环形图与趋势曲线
- **AND** 可切换一级 / 二级口径并下钻到明细
- **AND** 底部导航仍是 4 个 Tab

#### Scenario: 后续阶段不改导航

- **GIVEN** 阶段 5 增加备份与应用锁设置
- **WHEN** 打开应用
- **THEN** 底部仍是 4 个 Tab
- **AND** 新功能收纳进已有页面，不新增导航格

**理由**：这是同一条 Requirement 的**第二次 MODIFIED**。
阶段 3 修掉了「资产页显示阶段 3 占位」，却在同一条里换上
「统计页在阶段 4 落地前 SHALL 显示阶段 4 占位」，
Scenario 还写「GIVEN 阶段 3 版本」——同一个坑第二次复发；
阶段 3 delta 的「理由」自称「不再绑定具体阶段版本号」，正文其实仍绑定，
自述与实现也不一致。本次把正文与 Scenario 全部改成**不含阶段版本号、
不含「占位」字样**的长期行为，并补一条「后续阶段不改导航」，
彻底终结「每阶段都要改占位表述」的循环。
抽象规则：Requirement 只描述系统最终 SHALL 做什么，
「某页当前是占位」属于过程状态，应写在 `tasks.md` / `proposal.md`。

---

### Requirement: 中文语境配色落地

明细列表 SHALL 用红色显示支出、绿色显示收入，转账 SHALL 用中性色；
月度概览的结余为负时 SHALL 同样用红色。

#### Scenario: 浏览明细

- **GIVEN** 明细含一笔支出、一笔收入、一笔转账
- **WHEN** 查看金额文本
- **THEN** 支出红、收入绿、转账中性
- **AND** 与中文记账习惯一致

**理由**：v1 原稿写成「支出绿色、收入红色」与中文语境相反，
`reporting-ui` spec 已收口，本阶段落地。

### Requirement: 统计页落地月年切换与三种图表

统计页 SHALL 提供月度 / 年度切换与周期左右切换，
SHALL 展示所选周期的总收入 / 总支出 / 结余（结余为负用红色）。
月度视图 SHALL 展示当月每日支出折线，年度视图 SHALL 展示
1–12 月收入 vs 支出双折线。两种视图 SHALL 同时展示分类占比环形图。

#### Scenario: 月度视图

- **GIVEN** 用户选择 2026 年 11 月
- **WHEN** 查看统计页
- **THEN** 显示该月总收入 / 总支出 / 结余、分类环形图与每日支出折线
- **AND** 当日无支出的日子折线值为 0 而非断线

#### Scenario: 年度视图

- **GIVEN** 用户切到 2026 年度
- **WHEN** 查看统计页
- **THEN** 显示 1–12 月收入与支出两条折线
- **AND** 无流水的月份两条线均为 0 而非缺月

#### Scenario: 结余为负

- **GIVEN** 某月支出大于收入
- **WHEN** 查看概览
- **THEN** 结余用红色显示
- **AND** 与明细页中文语境配色一致

**理由**：「每个月看下哪类支出比较多，年终报表看下收入情况、支出情况、
曲线变化」是使用者三个原始诉求里最后一块；曲线必须用双折线才看得出走势。

---

### Requirement: 统计聚合空值补零且只返回聚合行

统计页 SHALL 由 SQL `GROUP BY` 返回聚合结果，
月度 SHALL 最多 31 行、年度 SHALL 为 12 行 × 收入 / 支出，
MUST NOT 把记录实体加载到内存后分组，MUST NOT 用 `records.size` 求和。
缺失的日期 / 月份 SHALL 在纯 JVM 数据层补 0，MUST NOT 断线或缺月。
切月 / 切年 SHALL 只重跑对应范围的聚合查询。

#### Scenario: 5 万条账本切年

- **GIVEN** 账本含 5 万条流水
- **WHEN** 切到年度视图
- **THEN** UI 收到 12 行聚合结果而非数万条记录
- **AND** 内存不随总记录数线性增长

#### Scenario: 空月补零

- **GIVEN** 2026 年 2 月没有任何流水
- **WHEN** 查看年度双折线
- **THEN** 2 月两条线均为 0
- **AND** 1 月与 3 月的连线不跨越一个缺失点

**理由**：每次进统计页全量扫描重算是万级数据下最明显的卡顿来源；
空月断线会让曲线误读，补 0 是展示正确性而非美化。
