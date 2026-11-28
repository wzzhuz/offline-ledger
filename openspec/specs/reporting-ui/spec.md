# 页面、性能与范围 Specification

## Purpose

定义页面结构、低摩擦交互、万级数据下的性能口径与 v1 范围边界。

---

## Requirements

### Requirement: 四 Tab 加中央记账按钮

底部导航 SHALL 为 4 个 Tab：明细 / 资产 / 统计 / 设置；
中央 SHALL 有一个 `+` 按钮触发「记一笔」。该按钮 SHALL **内嵌在底部导航栏内部**
（不悬浮于内容区之上），MUST NOT 遮挡任何列表内容。
设置 MUST NOT 作为第 5 个 Tab。

#### Scenario: 导航结构

- **GIVEN** 用户打开应用
- **WHEN** 查看底部导航
- **THEN** 恰好 4 个 Tab 与中央 `+`
- **AND** 设置从 Tab 进入，不额外占一格

#### Scenario: 列表末尾不被遮挡

- **GIVEN** 明细页滚动到最早一条流水
- **WHEN** 查看屏幕底部
- **THEN** 最后一条完整可见
- **AND** 中央 `+` 位于底栏区域内，不覆盖内容

**理由**：`FabPosition.Center` 的悬浮 FAB 会压在 `innerPadding` 之外的列表上，
使用者真机反馈「常驻会挡住内容显示」。改为底栏内嵌后仍是 4 Tab + 中央入口，
结构不变、零遮挡。

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

记一笔金额输入 SHALL 为**纯数字键盘**（`0-9`、`.`、`⌫`、`C`），
MUST NOT 提供 `+ - × ÷ ( ) =` 等运算符或括号。
用户输入字符串 SHALL 仅经 `Cents.fromYuan` 用 `BigDecimal` 转成整数分，
MUST NOT 让 `Float` / `Double` 参与任何金额计算或存储。
引擎里的 `Calculator` 求值器 MAY 保留供其它路径使用，
但「记一笔」MUST NOT 再把表达式交给它。

#### Scenario: 只输数字

- **GIVEN** 用户打开记一笔
- **WHEN** 查看金额键盘
- **THEN** 只有数字、小数点、退格与清空键
- **AND** 输入「38.50」保存后落库 3850 分

#### Scenario: 无浮点尾差

- **GIVEN** 用户输入「0.01」
- **WHEN** 系统解析并落库
- **THEN** 金额为 1 分，全程无浮点类型

**理由**：使用者真机反馈「输入金额都不需要加减乘除括号」。最初规格书
「方便多件商品合并算账」的假设被实测推翻；金额铁律仍要在最靠近输入处生效，
只是输入形态收敛为纯数字。

---

### Requirement: 两级分类快选不得增加记账摩擦

分类选择 SHALL 先展示**启用一级**的横向快捷栏。点击一级 SHALL **立即按一级记**
（`categoryId` 指向该一级自身），并可展开该大类的二级 chips 供可选细分；
**再点同一级 SHALL 收起二级栏**。系统 MUST NOT 显示「就按xx记」之类的二次确认按钮，
MUST NOT 要求额外点击才能按一级保存，MUST NOT 强制用户选到二级，
MUST NOT 提供三级及以上层级。选了二级后再点对应一级 SHALL 把 `categoryId` 重置回该一级。

#### Scenario: 只选一级

- **GIVEN** 用户点中央 `+`、点一级「餐饮」、输入金额
- **WHEN** 直接保存
- **THEN** ≤ 3 次交互完成记账，界面不出现任何「就按一级记」确认按钮
- **AND** `categoryId = 1` 且 `rootCategoryId = 1`

#### Scenario: 选错二级后改按一级

- **GIVEN** 用户展开「餐饮」并点了「聚餐」
- **WHEN** 再点一次「餐饮」一级
- **THEN** `categoryId` 回到 1，二级栏收起，保存即按一级记账

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
与年度收支双折线），并 SHALL 支持一级 / 二级分类切换与下钻明细；
设置页 SHALL 收纳备份、安全、偏好、分类管理、健康检查与回收站。
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

- **GIVEN** 后续在设置页新增设置项
- **WHEN** 打开应用
- **THEN** 底部仍是 4 个 Tab
- **AND** 新功能收纳进已有页面，不新增导航格

**理由**：这是同一条 Requirement 的**第三次 MODIFIED**。
阶段 3 修掉资产页占位、阶段 4 修掉统计页占位，
但阶段 4 的替换文本里 Scenario 仍写「**GIVEN** 阶段 5 增加备份与应用锁设置」，
违反了本 change 自己抽象出的规则「正文与 Scenario 都不能绑定阶段号」。
本次把该 GIVEN 改成不含阶段版本号的长期表述，并补齐设置页的长期行为描述。
**这是最后一次为这条 Requirement 做 MODIFIED**——之后再无过程状态可修。

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

统计页 SHALL 提供月度 / 年度 / 多年切换与周期左右切换，SHALL 展示所选周期的
总收入 / 总支出 / 结余（结余为负用红色）。月度视图 SHALL 展示当月每日支出折线，
年度视图 SHALL 展示 1–12 月收入 vs 支出双折线，并 SHALL 展示按当前币种累计推算的
资产余额趋势（MUST NOT 跨币种混算）。
**第 69 批撤回第 68 批的蓝底身份区与「分析 / 概览」双 Tab**：统计页 SHALL 以
原有白底「统计」标题 + 既有月度 / 年度 / 多年周期切换 + 币种切换为顶部，
MUST NOT 新增蓝底块、MUST NOT 新增第二组 Tab、MUST NOT 改变既有周期切换能力。
统计页 SHALL 同时提供**分类占比卡**（环形图 + 一羽式图例列表，只承载分类维度）
与**收款方 / 账户默认折叠只读卡**（两者各自独立），
分类占比卡内 SHALL 同时可见环形图与其下方图例列表，两者 SHALL 共享同一份
净支出切片数据、MUST NOT 出现两个不一致的合计。
统计页 SHALL 以「白底标题 + 周期 / 币种切换 →（警告置顶）小结卡 → 趋势卡 →
分类占比卡 → 收款方 / 账户默认折叠卡 →（年度）资产卡 → 口径说明入口」的层级组织；
每张卡 SHALL 只承载一个结论、SHALL 有清晰边界，MUST NOT 让所有区块同权重平铺、
MUST NOT 让灰色口径说明常驻两行。统计页是浏览型长报表，SHALL 允许纵向滚动。
「一级合计 = 二级合计 = 总净支出」不一致时的警告 MUST 置顶显眼，
MUST NOT 为美观收掉。既有常驻点数值、竖向引导线与横屏放大页 MUST NOT 因本批回退。

#### Scenario: 月度视图

- **GIVEN** 用户选择 2026 年 11 月
- **WHEN** 查看统计页
- **THEN** 显示该月总收入 / 总支出 / 结余、每日支出折线、分类占比卡与默认折叠的收款方 / 账户卡
- **AND** 当日无支出的日子折线值为 0 而非断线
- **AND** 环形图与下方图例列表同屏可见，二者合计一致

#### Scenario: 周期切换不被改动

- **WHEN** 操作月度 / 年度 / 多年与币种切换
- **THEN** 两行控件完整可见可点，统计数字按新周期 / 币种刷新
- **AND** 顶部没有蓝底块、没有「分析 / 概览」Tab

#### Scenario: 年度视图

- **GIVEN** 用户切到 2026 年度
- **WHEN** 查看统计页
- **THEN** 显示 1–12 月收入与支出两条折线、分类占比卡与按当前币种的资产余额趋势
- **AND** 无流水的月份两条线均为 0 而非缺月

#### Scenario: 合计不一致警告

- **GIVEN** 一级 / 二级 / 总净支出三者不一致
- **WHEN** 打开统计页
- **THEN** 置顶显示警告卡与差额

#### Scenario: 不回到互斥切换

- **WHEN** 打开统计页分类占比卡
- **THEN** 环形图与图例列表同时可见
- **AND** 不存在「看环 / 看排行」二选一的切换 chip

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

### Requirement: 设置页收纳全部设置项且不改导航

设置页 SHALL 收纳：加密备份导出与恢复、应用锁与 PIN、最近任务防泄露、
对账尾差阈值、中文语境配色反转、默认币种与默认账户、分类管理、
模板与周期记账、预算管理、账本健康自检、回收站。
设置页 SHALL 以不超过 5 组的分组列表组织这些入口，**顺序 SHALL 为
「账本 / 数据与备份 / 偏好 / 安全 / 关于」**（按使用频率，日常账本操作优先），
点组进入各自二级页，MUST NOT 把十几个 Button 与输入框堆在一屏。
新增设置项 MUST NOT 改变 4 Tab 导航格数、MUST NOT 新增第 5 个 Tab。
配色反转 SHALL 只交换支出 / 收入语义色，MUST NOT 改变中性色与品牌色，
且 SHALL 对既有页面无需逐个改写即生效（通过可观察的语义色状态）。

#### Scenario: 反转配色

- **GIVEN** 用户开启「支出 / 收入配色反转」
- **WHEN** 查看明细与统计页
- **THEN** 支出与收入颜色互换

#### Scenario: 分组顺序

- **GIVEN** 打开设置 Tab
- **WHEN** 自上而下看四张分组卡
- **THEN** 依次为「账本」「数据与备份」「偏好」「安全」，末尾为「关于」说明

**理由**：第 15 批按使用者真机反馈调整顺序；只改代码会与本条旧文本的
「数据与备份 / 安全 / 偏好 / 账本 / 关于」相反，故按标题整块 MODIFIED。

### Requirement: 记一笔提供模板快选且两步内完成

「记一笔」SHALL 通过键盘上方「模板」chip 提供模板预填，
从打开记一笔到**完成表单预填** SHALL 不超过 2 次点击，MUST NOT 弹二次编辑。
模板语义 SHALL 为**预填草稿、不直接生成流水**（与既有 `applyTemplate()` 一致；
主 spec 旧文「点一下直接生成流水并关闭」为历史腐烂，本 change 以代码为准）。
计算器键盘交互与两级分类快选 SHALL 保持不变。

#### Scenario: 两次点击预填午餐

- **GIVEN** 用户建了「午餐」模板
- **WHEN** 点「模板」chip → 点「午餐」
- **THEN** 表单按模板预填，可继续改金额后保存，MUST NOT 自动落库

---

### Requirement: 桌面快捷方式直达记一笔且不绕过应用锁

系统 SHALL 提供静态 App Shortcut，点开 SHALL 直接进入「记一笔」
而非首页。启用应用锁时，快捷方式 SHALL 仍先展示 `AppLockScreen`，
MUST NOT 提供绕过锁的路径。快捷方式 SHALL NOT 申请任何权限。

#### Scenario: 桌面直达

- **GIVEN** 长按应用图标选择「记一笔」
- **WHEN** 启动
- **THEN** 直接显示记一笔 Dialog
- **AND** 底部导航仍是 4 个 Tab

#### Scenario: 已启用应用锁

- **GIVEN** 应用锁已开启，从桌面快捷方式启动
- **WHEN** 未解锁
- **THEN** 只显示解锁界面，不渲染任何账目
- **AND** 解锁后才进入记一笔

**理由**：低摩擦不能以牺牲「数据是你的」为代价；
快捷方式是 framework 静态能力，零依赖零权限。

---

### Requirement: 模板与规则管理收纳进设置页且不改导航

设置页 SHALL 提供「模板与周期记账」入口，点击 SHALL 进入**全屏独立页面**
（Navigation 路由），MUST NOT 是内容受限的 Dialog。
页面 SHALL 提供模板 / 周期规则两个 Tab、顶部固定「+」新增入口、模板列表与编辑视图分离；
编辑视图 SHALL 完整回填既有模板的全部字段，保存后 SHALL 回列表并刷新。
模板列表 SHALL 用惰性列表承载，MUST NOT 因条目增多把新增入口推到屏幕之外。
系统 SHALL 保持恰好 4 个底部 Tab，MUST NOT 新增导航格数，
MUST NOT 把管理界面做成第 5 个 Tab。

#### Scenario: 打开管理

- **GIVEN** 在设置页
- **WHEN** 点「模板与周期记账」
- **THEN** 进入全屏独立页面，含模板与周期规则两个 Tab、顶部固定「+」入口
- **AND** 不出现 Dialog，返回后底部仍是 4 个 Tab

#### Scenario: 大量模板下新增入口固定

- **GIVEN** 已有 20 个模板与 5 条周期规则
- **WHEN** 打开模板管理页并滚动列表
- **THEN** 顶部「+」入口始终可见，无需上滑寻找新增
- **AND** 点「+」后进入编辑视图，既有模板可编辑且字段完整回填

#### Scenario: 归档模板

- **GIVEN** 一条不再使用的模板
- **WHEN** 归档
- **THEN** 从模板 chips 消失
- **AND** 已由它生成的历史流水不受影响

**理由**：阶段 6a 原文写「Dialog SHALL 提供」「THEN 弹出 Dialog」，
第 5 批已按使用者反馈升为独立全屏页，spec 停在旧行为形成规格腐烂。
本批按标题整块替换，并把第 12 批「入口固定 / 惰性列表 / 编辑独立」一并写入长期行为。
4 Tab 结构在阶段 2 已一次成型且经三次 MODIFIED 收口，仍不变。

---

### Requirement: 明细页提供报销入口且默认剩余可报金额

明细页 SHALL 对每笔 `EXPENSE` 提供「报销」按钮，
点击后 SHALL 弹出金额输入对话框，默认值 = 原金额 − 累计已报销。
确认后 SHALL 刷新列表与月度概览。

#### Scenario: 默认金额

- **GIVEN** 100 元支出已报销 30 元
- **WHEN** 点「报销」
- **THEN** 输入框默认 70 元
- **AND** 用户可直接确认，不必手算

#### Scenario: 完成后刷新

- **GIVEN** 用户确认报销
- **WHEN** 事务提交
- **THEN** 明细列表、当日合计与月度概览同步更新
- **AND** 不出现「记了但列表没变」

**理由**：成对模型必须能看到原流水；明细页天然有这个上下文，
比在「记一笔」里再塞一个历史流水选择器少两步。

---

### Requirement: 统计页与设置页收纳预算且不改导航

统计页 SHALL 展示「已报销 / 净支出」两行与预算进度区，
设置页 SHALL 提供「预算管理」入口（列表 / 新增 / 归档）。
系统 SHALL 保持恰好 4 个底部 Tab，
MUST NOT 把预算做成第 5 个 Tab 或独立页面。

#### Scenario: 概览双口径

- **GIVEN** 当月有支出与报销
- **WHEN** 打开统计页
- **THEN** 同时看到毛支出、已报销、净支出
- **AND** 页面标注「分类占比与预算按净支出计算」

#### Scenario: 预算管理

- **GIVEN** 在设置页点「预算管理」
- **WHEN** 新增一条「餐饮 1000 元」规则
- **THEN** 统计页当月立即出现该预算进度条
- **AND** 底部仍是 4 个 Tab

**理由**：4 Tab 结构经三次 MODIFIED 收口，
新功能只能收纳进已有页面，导航零返工。

### Requirement: 资产页按币种分组且移除单一净资产总数

资产页 SHALL 按 `currencyCode` 分组展示
「资产合计 / 负债合计 / 小计」，
MUST NOT 显示把所有币种加在一起的单一「净资产」总数。
账户行 SHALL 显示其币种标签。
4 个底部 Tab 结构 MUST NOT 改动。

#### Scenario: 两币种账户

- **GIVEN** 一个 HKD 账户与一个 CNY 账户
- **WHEN** 打开资产页
- **THEN** 出现 HKD / CNY 两组小计
- **AND** 页面不出现跨币种净资产总数

#### Scenario: 导航不变

- **GIVEN** 6c-1 已落地
- **WHEN** 查看底部导航
- **THEN** 仍是明细 / 资产 / 统计 / 设置 4 个 Tab

**理由**：严格不混算的可视化落点；分组小计语义诚实，
不给一个需要汇率才能成立的虚假精确总数。

---

### Requirement: 统计页提供币种切换且不跨币种聚合

统计页 SHALL 提供币种切换，默认 SHALL 选中一个具体币种，
所有图表与合计 MUST 只统计该币种，
页面 SHALL 标注「当前币种：X · 不跨币种汇总」。
记一笔 SHALL 显示所选账户币种，
跨币种转账 SHALL 在保存时给出明确错误提示。

#### Scenario: 切换币种

- **GIVEN** HKD 支出 38.50 与 CNY 支出 20.00
- **WHEN** 统计页切到 HKD
- **THEN** 支出合计 38.50、分类占比与趋势只含 HKD 流水
- **AND** 页面标注当前币种

#### Scenario: 跨币种转账提示

- **GIVEN** 用户选了不同币种的两个账户做转账
- **WHEN** 点保存
- **THEN** 显示「转账双方币种必须一致」
- **AND** 不写入流水

**理由**：币种切换是「哪类支出比较多」在多币种下的前提；
默认选中具体币种可避免用户不经意看到跨币种混算结果。

### Requirement: 设置页提供 CSV 导出与账单导入入口

设置页 SHALL 在「数据与备份」区提供三个入口：「导出 CSV（核对 / 重建用）」、
「导入账单（第三方账单）」、「从 CSV 重建（本 App 导出文件）」，
分别使用 SAF `CreateDocument("text/csv")` 与 `OpenDocument`。
MUST NOT 再显示「v1 不提供 CSV 导出（明文文件）」或任何
以明文风险为前提的提示。
「导入账单」SHALL 只吃支付宝 / 微信 / 银行 / Firefly III 等第三方账单；
本 App 自有导出文件 SHALL 走「从 CSV 重建」，
投错入口 SHALL 给出明确引导且 MUST NOT 改动账本。
导入前 SHALL 展示「将写入 N 条 / 跳过 M 条重复」确认；
结果或错误 SHALL 在现有 message / error 区展示，
重建里被格式跳过的行 SHALL 在结果摘要中计数说明。
4 个底部 Tab 结构 MUST NOT 改动。

#### Scenario: 导出入口
- **GIVEN** 使用者在设置页点「导出 CSV（核对 / 重建用）」
- **WHEN** 选择保存位置
- **THEN** 写出 UTF-8 BOM 的 11 列 CSV 文件
- **AND** 页面显示导出行数

#### Scenario: 投错入口
- **GIVEN** 使用者选中本 App 自己导出的 CSV
- **WHEN** 点「导入账单（第三方账单）」
- **THEN** 提示改用「从 CSV 重建」或「从加密备份恢复」，账本不改动

#### Scenario: 旧文案消失
- **GIVEN** 6c-2 已落地
- **WHEN** 查看设置页
- **THEN** 不存在「v1 不提供 CSV 导出」「明文文件，列在 v1.1」等字样

**理由**：原先「导出文件可原样导入重建」的措辞没有区分两套格式，
导致使用者按直觉点「导入账单」被行级校验拒绝。按标题整块替换，
避免 specs 同时存在「可原样导入」与「必须分流」两套相反表述。

---

### Requirement: 明细页提供可选币种筛选且与统计页口径一致

明细页 SHALL 提供币种筛选，默认 SHALL 为「全部币种」。
选中具体币种时列表与按日合计 MUST 只含该币种，
且 SHALL 与统计页同月份同币种的支出合计一致。
`pagedFiltered` 与 `dailyTotals` SHALL 通过
`(:currencyCode IS NULL OR currencyCode = :currencyCode)` 短路实现，
null 时行为 MUST NOT 改变。

#### Scenario: 选中 HKD

- **GIVEN** 同月有 HKD 与 CNY 两类流水
- **WHEN** 明细页筛选选 HKD
- **THEN** 列表与每日合计只含 HKD
- **AND** 该月 HKD 支出合计与统计页 HKD 视图一致

#### Scenario: 默认全部币种

- **GIVEN** 用户未设置币种筛选
- **WHEN** 打开明细页
- **THEN** 行为与 6c-1 一致，显示全部币种

**理由**：6c-1 明细页仍写「全部币种（按币种筛选请到统计页）」，
两页口径不一致；可选参数短路是最小改动，
且沿用既有唯一统计口径，不新开一份 SQL。

### Requirement: 记一笔布局紧凑且首屏可见备注与收款方

「记一笔」支出 / 收入态首屏 SHALL 只呈现：类型 Tab、金额、日期时间无标签副行、
分类快捷行（带 `›` 整行可点全量选择器）、收款方与描述的**无边框内联行**，
以及固定底部区的键盘上方**单行非等宽 chips**、键盘、保存按钮。
**转账态 SHALL 例外**：分类行、收款方行与联想条隐藏，
SHALL 在日期副行之后常驻**转出 / 转入两张账户卡**，其余维持不变。
账户在支出 / 收入态 SHALL 从首屏表单行移到 chips（默认账户预填、低频）；
备注与「不计入收支」、模板、标签、优惠/手续费 MUST NOT 占首屏表单区。
收款方与描述 MUST 仍在首屏可见（描述是明细主标题来源，MUST NOT 藏进面板）。
收款方联想条 SHALL 只在输入框聚焦或有内容时出现，MUST NOT 常驻。

> **第 82 批归档修订**：编辑态旧值原本要求「chips 在 label 上显示可读摘要」，
> 与「固定短标签 + 非等宽」冲突。本批改为「角标提示有值 +
> 点开 sheet 可见并修改完整值」，标题沿用历史名称（归档匹配基准）。

#### Scenario: 旧内容不因收起丢失

- **GIVEN** 编辑一条带备注、优惠、两个标签、且不计入收支的旧流水
- **WHEN** 打开编辑态
- **THEN** 备注 / 优惠 / 标签 chips 右上角显示小圆点
- **AND** 点开对应 sheet 可查看并修改全部字段，MUST NOT 让任何旧值不可修改

#### Scenario: 转账态两张账户主字段常驻

- **GIVEN** 用户切到「转账」Tab
- **WHEN** 不做任何滚动、不点任何 chip
- **THEN** 首屏直接看到「转出账户」与「转入账户」两张卡及其当前账户名
- **AND** MUST NOT 需要先打开底部 sheet 或切换分段才能看到它们

---

### Requirement: 记一笔可用日历选择任意日期

日期控件 SHALL 提供日历选择器，用户 SHALL 能一次跳到任意年月日，
MUST NOT 只能靠前一天 / 后一天按钮逐日移动。「今天」快捷入口 SHALL 保留。
日历与 `timestamp` 的换算 SHALL 经 `LedgerClock.zoneId`，避免 UTC 午夜差一天。

#### Scenario: 补记上个月的一笔

- **GIVEN** 今天是 11 月 13 日，要补记 10 月 2 日
- **WHEN** 点日期打开日历并选 10 月 2 日
- **THEN** 该流水 `localDate = 20261002`、`yearMonth = 202610`
- **AND** 不需要点 42 次「前一天」

#### Scenario: 今天快捷

- **GIVEN** 已选到历史日期
- **WHEN** 点「今天」
- **THEN** 日期回到设备今天

**理由**：逐日移动是阶段 2 的最小实现，真机补记时体验很差。

---

### Requirement: 记一笔提供收款方与常用收款方补全

「记一笔」SHALL 在首屏提供收款方**无边框内联**自由文本输入；
系统 SHALL 从既有流水 `DISTINCT` 出常用收款方，并 SHALL 在**收款方输入框正下方**
提供一条 40dp 内联联想条：输入框为空时 SHALL 显示常用、有输入时 SHALL 改为
模糊联想，两者互斥共用同一条，点一下 SHALL 填入输入框。
联想条 SHALL 仅在输入框聚焦或有内容时出现，MUST NOT 常驻首屏、
MUST NOT 塞进 chips 弹层或「更多」面板（脱离输入上下文）。
明细行 SHALL 展示收款方。收款方 MUST NOT 建成账户、MUST NOT 引入复式分录。

#### Scenario: 输入时联想同屏可见

- **GIVEN** 账本里已有「盒马」「地铁」
- **WHEN** 点收款方输入框并输入「盒」
- **THEN** 输入框正下方立刻出现「盒马」联想条，点一下填入
- **AND** 联想条与输入框同屏，MUST NOT 需要上滑或打开任何面板

#### Scenario: 未聚焦不占空间

- **GIVEN** 刚打开记一笔、收款方输入框未聚焦且为空
- **WHEN** 查看首屏
- **THEN** 收款方行下方无联想条，MUST NOT 常驻一行 40dp 区域

## ADDED Requirements

---

### Requirement: 资产页提供账户编辑且可改期初余额与币种

资产页每个账户 SHALL 提供「编辑」入口，支持修改名称、期初余额、币种、
信用额度与是否计入净资产。改动期初余额后 SHALL 走既有事务写路径
并 SHALL 触发余额重算。预置账户 MUST 同样可编辑。

#### Scenario: 给预置账户设期初余额

- **GIVEN** 全新安装后的预置「微信零钱」余额为 0
- **WHEN** 编辑并输入期初余额 1000.00
- **THEN** 该账户余额为 100000 分
- **AND** 重算后资产页分组小计与该账户一致

#### Scenario: 改币种

- **GIVEN** 预置账户币种为 CNY
- **WHEN** 编辑改为 HKD
- **THEN** 账户标签变 HKD，历史流水币种标签不自动改

**理由**：使用者反馈「默认提供的初始账户怎么设置初始余额」——此前只有
新增账户能填期初余额，且 `updateAccountMeta` 不支持，形成功能缺口。

---

### Requirement: 设置页提供默认币种与批量统一币种

设置页 SHALL 提供「默认币种」下拉，只影响**之后新建**的账户，
MUST NOT 自动改动已有账户与流水。设置页 SHALL 另提供显式按钮
「把现有账户与流水统一改为该币种」，点击后 SHALL 在同一事务内更新
accounts / records / trash 的冗余币种标签并重算。

#### Scenario: 只改默认值不动旧数据

- **GIVEN** 账本已有 3 个 CNY 账户与若干 CNY 流水
- **WHEN** 把默认币种改为 USD
- **THEN** 设置页显示 USD
- **AND** 已有 3 个账户与流水仍是 CNY
- **AND** 之后新建账户为 USD

#### Scenario: 显式批量统一

- **GIVEN** 账本混有 CNY 与 HKD
- **WHEN** 点「统一为当前默认币种」
- **THEN** 所有账户、流水与回收站条目的币种标签一致
- **AND** 金额一分不变，余额重算后净资产不变

**理由**：使用者在中国大陆，默认币种应为人民币；但自动改已有数据会改动
正在核对的账目，必须把两种意图拆成两个显式动作。

---

### Requirement: 模板分类按收支类型过滤并按一级分组

模板与周期规则表单的分类选择器 SHALL 只显示与当前选中收支类型一致、
未归档的分类；`CategorySelectDialog` 的左侧一级导航 SHALL 表达一级分组，
二级 SHALL 经 `CategoryDisplay` 以「一级 / 二级」形式标注归属
（回填筛选 chip 的 `displayLabel` 契约一字不动）。
切换收支类型时 SHALL 清空已选分类；保存前 SHALL 校验分类类型与流水类型一致。
模板列表行 SHALL 显示该模板的分类。
**第 83 批归档修订**：原文「分类下拉」「展开分类下拉」措辞已随
`DropdownSelector` 整体下线作废，改为「整行 › 入口 + 通用选择器」；
账户选择同批改通用选择器（见 ADDED「模板与周期规则复用通用…」）。

#### Scenario: 选支出不出现收入分类

- **GIVEN** 新建模板时选中「支出」
- **WHEN** 点开分类选择器
- **THEN** 只出现餐饮 / 交通 / 购物等支出分类及其二级
- **AND** 不出现工资 / 月薪 / 奖金

#### Scenario: 二级归属清晰

- **GIVEN** 「食材」是「餐饮」的二级
- **WHEN** 查看选择器当前值与列表行
- **THEN** 显示「餐饮 / 食材」而非孤立的「└ 食材」

#### Scenario: 列表可读

- **GIVEN** 已存一个模板选了「餐饮 / 食材」
- **WHEN** 打开模板管理
- **THEN** 列表行显示名称、金额与「餐饮 / 食材」

---

### Requirement: 交易对方按流水类型显示
系统 SHALL 在支出流水显示「收款方」、收入流水显示「付款方 / 来源」，
转账流水 SHALL NOT 显示交易对方字段。交易对方 MUST 是流水级自由文本，
MUST NOT 建成账户、MUST NOT 产生复式分录。

**理由**：使用者反馈「收入那边也显示收款方不正确」「新增转账也显示收款方不合理」。

#### Scenario: 收入标签
- **GIVEN** 用户在「收入」Tab 记一笔
- **WHEN** 打开记一笔界面
- **THEN** 交易对方输入框标签为「付款方 / 来源」

#### Scenario: 转账隐藏
- **GIVEN** 用户切到「转账」Tab
- **WHEN** 打开记一笔界面
- **THEN** 交易对方输入框与联想选项均不渲染

---

### Requirement: 记账时间与明细时间
系统 SHALL 允许修改流水发生时刻（时 / 分），默认当前时刻；改日期 SHALL 保留已选时刻。
明细列表每行 SHALL 显示 `HH:mm`。

**理由**：消费完过一会再记，只有日期时核对困难。

#### Scenario: 改时刻
- **GIVEN** 用户选了昨天并把时间改为 14:30
- **WHEN** 保存流水
- **THEN** `timestamp` 对应昨天 14:30（Asia/Shanghai），`localDate` 仍为昨天

---

### Requirement: 分类候选按收支类型统一过滤
系统 SHALL 保证分类网格、模板下拉、保存校验三处共用同一类型约束：
切换收支类型 SHALL 重置为对应类型的一级分类；所选分类类型与流水类型不一致 SHALL 拒绝保存。
新记一笔的默认一级与切类型重置 SHALL 只取**启用且未归档**项，MUST NOT 落到停用扩展项上。

**理由**：使用者反馈「支出里面类型可以选择收入的分类不合理」；阶段 7 只修了模板下拉，
漏了分类网格与转账 Tab，属同源漏洞。

#### Scenario: 切类型
- **GIVEN** 用户在「收入」Tab 选了「工资」
- **WHEN** 切到「支出」Tab
- **THEN** 分类自动重置为支出一级分类，「工资」不再出现在候选中

---

### Requirement: 模板作为预填草稿
系统 SHALL 把模板定义为「预填草稿」而非固定账单：模板金额 MAY 留空；
选中模板 SHALL 把分类、账户、类型、备注、交易对方与不计入收支自动填入记一笔表单，
日期时间 SHALL 取「现在」，且所有字段 SHALL 仍可自由修改。
未填金额的模板 MUST NOT 直接生成流水。
模板管理 SHALL 提供编辑、删除（二次确认）与类型选中高亮，
记一笔中的模板 chip SHALL 显示选中态。

**理由**：使用者反馈「金额每次会变，其他才大概率不变」。原实现点模板即直接写库，
与「自动填充、空的再填、还能自由调整」的预期相反。

#### Scenario: 填充后仍可改
- **GIVEN** 存在金额留空、分类为「餐饮 / 正餐」、账户为「微信」的模板
- **WHEN** 在记一笔中选中该模板
- **THEN** 表单自动带入上述字段、金额为空、时间为现在，用户输入金额并改时间后保存成功
- **AND** 该模板本身不被修改

#### Scenario: 草稿不产生 0 元流水
- **GIVEN** 金额留空的模板
- **WHEN** 任何写路径尝试由它直接生成流水
- **THEN** 系统拒绝生成，且不写入任何 0 元记录

---

### Requirement: 明细页极简网格与中性分类标识
明细列表 SHALL 使用固定四列网格（时间 / 分类·对方 / 账户 / 金额），
统一行高与细分割线，MUST NOT 用卡片堆叠与阴影；分类标识 SHALL 为中性单色圆加中文首字，
MUST NOT 按分类 `colorHex` 上色。单行的编辑、报销、删除 SHALL 收进点击后的动作菜单，
MUST NOT 作为行内按钮破坏网格对齐。

**理由**：使用者在 6.73 寸屏上反馈「交易记录页面空、第一眼不整齐；图标 6~7 种颜色不清晰」。
「空」与「乱」是同一问题：信息层级没立起来，解法是降噪 + 定网格，不是把空白塞满。

#### Scenario: 四列对齐
- **GIVEN** 明细页有若干条不同分类、不同账户的流水
- **WHEN** 打开明细页
- **THEN** 每行时间、分类、账户、金额分别落在固定列，金额右对齐，分类标识均为同一中性色
- **AND** 行内没有报销或删除按钮

#### Scenario: 行操作仍可达
- **GIVEN** 一条支出流水
- **WHEN** 点击该行
- **THEN** 出现编辑 / 报销 / 删除 / 取消动作菜单，选择后各走原有事务路径

---

### Requirement: 图表点数值常驻显示
年度与多年趋势在竖屏 MUST 用简化值（万 / k）常驻标注关键点（峰值、末点、选中点），
奇偶上下交错避免重叠；MUST NOT 12 个点全量常驻完整金额，MUST NOT 画 12 根虚线；
点按 MUST 出现图下随选更新的文字汇总与**一条**瞬时竖向引导线，MUST NOT 用
AlertDialog 遮挡图表；横屏放大页的完整数值表 MUST 保留；跨币种混算仍 MUST NOT。

#### Scenario: 年度竖屏读数值
- **WHEN** 统计页切到年度、竖屏查看趋势卡
- **THEN** 峰值 / 末点 / 选中点显示简化值且上下错开，互不重叠

#### Scenario: 点按某月
- **WHEN** 使用者点趋势图某个月的数据点
- **THEN** 该点出现一条竖向虚线，图下显示该月收入 / 支出 / 净结余，可清除选中

### Requirement: 分类管理与回收站为独立页面
分类管理与回收站 SHALL 为全屏独立页面（Navigation 路由），MUST NOT 是内容受限的小弹窗；
设置页 SHALL 重排为不超过 5 组的分组列表，点组进入各自二级页；
进入二级页时底栏 SHALL 隐藏，4 Tab 主路由结构 SHALL 不变。

**理由**：使用者反馈「设置页分类管理是一个小弹窗，显示内容有限；回收站也是一样；
设置页挺杂乱」。设置项按组进二级页才是解杂，把设置项搬进汉堡抽屉只是形似。

#### Scenario: 分类管理全屏
- **GIVEN** 16 个一级分类与 98 个二级分类
- **WHEN** 从设置页进入分类管理
- **THEN** 全屏可滚动展示全部一级与二级，搜索、排序、改名、删除、归档恢复均可用

#### Scenario: 二级页隐藏底栏
- **GIVEN** 用户在「偏好」二级页
- **WHEN** 查看屏幕
- **THEN** 底部 4 Tab 不显示，顶部有返回入口；返回后 4 Tab 恢复

---

### Requirement: 记一笔采用统一表单网格与有上限的快捷区

「记一笔」SHALL 保留固定 52dp 标签列与细分割线，同区域 MUST NOT 混用
带框输入框、无边界文本按钮与芯片作为顶层块。**支出 / 收入态**账户与模板
SHALL 不再作为首屏表单按钮或平铺快捷 chips 存在，统一由键盘上方 chip
直达底部 sheet；**转账态**账户 SHALL 以两张首屏卡片常驻，MUST NOT 再进 chips。
分类快捷栏 SHALL 保留（低摩擦优点）。金额 SHALL 单独大字号展示。
账户选择面板 SHALL 按「账户选择面板展示品牌图标与卡种」呈现，
MUST NOT 再是纯文字列表。
「最近收款方 / 常用账户各最多 4 个」「模板每行 3 个两行铺开」的
快捷区上限条款随第 80 批作废。

#### Scenario: 账户 chip 显示默认账户（收支态）

- **GIVEN** 默认账户为「工商银行 (1122)」且为信用卡
- **WHEN** 打开记一笔（支出）
- **THEN** 账户 chip 完整显示该账户名，点开两列弹层同名卡由「尾号 xxxx」区分
- **AND** 首屏表单区 MUST NOT 再出现「账户」行
- **AND**（第 84 批）弹层顶部 MUST NOT 出现搜索框、叉号按钮或并排分段 chip

## ADDED Requirements

---

---

### Requirement: 默认账户是记一笔与模板的账户兜底

偏好 SHALL 允许设置一个默认账户（存账户 id，不进 Room、不进备份，可清除）。
打开记一笔时 SHALL 优先预填该账户；模板未指定账户时，生成的流水 SHALL
用默认账户（未设则取第一个未归档账户）兜底，MUST NOT 产生无账户流水。

#### Scenario: 模板账户留空
- **GIVEN** 偏好里默认账户为「招行储蓄卡」，模板未填转出账户
- **WHEN** 用该模板记一笔并保存
- **THEN** 流水挂在「招行储蓄卡」上，余额按既有事务联动并重算
- **AND** 年度收入 / 分类支出口径不受影响

**理由**：模板是预填草稿，账户不该必填；但流水必须挂账户，否则资产数额失真。
默认账户同时省掉一次点选，与竞品的「默认流水账户」做法一致。

---

### Requirement: 记一笔低频项收起且输入框不撑高边框

**第 80 批归档修订**：低频项不再统一收进「更多」面板，改为各自 chip 直达。
模板、账户、标签、优惠/手续费、备注 +「不计入收支」SHALL 由键盘上方 chips
各自直接打开底部 sheet，MUST NOT 出现「更多 ›」入口或 `MorePanel` 容器，
MUST NOT 出现两个及以上并列折叠入口。每个 chip SHALL 在编辑态有值时显示
可读摘要。**第 84 批口径精确化**：本条约束的「sheet」特指
`BottomPickerSheet`（备注 / 优惠 / 标签 / 模板快选），它们 SHALL 继续用
`Dialog` + 底部对齐 `Surface` 实现，MUST NOT 引入
`ExperimentalMaterial3Api` 的 `ModalBottomSheet`；
**账户选择器不在此禁令范围内**，按第 84 批 ADDED「账户选择器采用原生底部弹层…」
走 `ModalBottomSheet`。T12 是记一笔低频项 sheet 的**局部惯例**，
MUST NOT 被误读成全仓禁令（统计页 `StatsDrillSheet` 自第 69 批起已用
`ModalBottomSheet`，且已写进 `category-stats` 主 spec）。
文本输入 SHALL 用 placeholder 而非浮动 label。
日期与时间 MUST 仍为紧随金额后的无标签可点副行（不算低频项）。

#### Scenario: 去掉中间层

- **GIVEN** 新建一笔流水
- **WHEN** 想添加优惠
- **THEN** 直接点「优惠」chip 即得输入 sheet，MUST NOT 先点「更多 ›」
- **AND** 首屏不再出现「▸ 添加优惠」与「更多（备注 · 不计入收支）」两行

#### Scenario: 编辑态 chip 带旧值

- **GIVEN** 编辑一条备注「同事聚餐 AA」、优惠 5 元、两个标签的旧流水
- **WHEN** 查看键盘上方 chips
- **THEN** 备注 / 优惠 / 标签三枚 chip 各自显示摘要，MUST NOT 显示成空白「备注 / 优惠 / 标签」而让人以为旧值丢失


---

### Requirement: 记一笔输入框紧凑且支持一键清空

交易对方、描述、备注**三个**输入框的 placeholder SHALL 与输入文字、
表单标签统一为 `bodyMedium`，SHALL NOT 使用 placeholder 默认的 `bodyLarge`。
输入框 SHALL NOT 设置固定 `height` 或 `contentPadding`：
SHALL 用 `Modifier.heightIn(min = 40.dp)` 覆盖官方
`OutlinedTextFieldDefaults.MinHeight = 56.dp` 这个无条件下限。
三个输入框 SHALL 共用同一个 `ClearFieldIcon` 出口（36dp 容器 + 18dp Icon），
在**有内容时**显示尾部清空图标，点击 SHALL 立即清空内容；
空内容时 SHALL NOT 显示清空图标；图标 SHALL NOT 把输入框撑到 48dp。
选中态 SHALL 用 chip 的填充底色表达，SHALL NOT 用 `● ` 等文字前缀。

> **第 81 批归档修订**：本条原文后半段描述「『更多』展开区把备注与不计入
> 开关放在同一 `FormRow`」「『更多』入口带 ⌄/⌃ 展开指示器」「账户 › 整行点击」，
> 均为第 77 批之前形态。第 80 批撤掉「更多」层时漏改本条，造成主 spec
> 同时存在两条相反要求；本批整块删除过期段落，并把描述纳入覆盖范围。
>
> **第 84 批归档修订**：账户选择器的搜索框按使用者「账户没那么多」反馈整体下线，
> 故覆盖范围由四个输入框收为三个，删除「账户搜索 SHALL 支持按账户名筛选」
> 与四处 Scenario。同名卡改由 `accountSecondaryLabel` 的「尾号 xxxx」显示区分
> （见「筛选与统计的账户显示名与全站一致」），MUST NOT 因删搜索连带删掉 `cardLast4`。

#### Scenario: 三处处处可一键清空

- **GIVEN** 交易对方、描述、备注三处均已输入内容
- **WHEN** 分别点各自尾部清空图标
- **THEN** 对应内容立即为空且图标消失，无需用输入法逐字删除
- **AND** 三处 placeholder 完整显示、同为 `bodyMedium`、实际高度约 44dp

#### Scenario: 空内容不留白

- **GIVEN** 描述输入框为空
- **WHEN** 查看该行
- **THEN** 不显示清空图标，占位文字完整可见、下半部分不被裁切

## ADDED Requirements

---

---

### Requirement: 模板快捷区两行铺开且不横向滚动

**第 80 批归档修订**：标题沿用历史名称，实际形态已改为 chip 直达。
常用模板 SHALL 收敛为键盘上方「模板」chip + 一羽式底部 sheet，
sheet SHALL 一行一条给出名称、备注与预填金额，MUST NOT 再以每行 3 个、
两行等宽铺开，MUST NOT 经过「更多」层，MUST NOT 用居中 `AlertDialog` 纯文字列表。
点一项 SHALL 预填草稿并关闭 sheet，MUST NOT 自动落库。

#### Scenario: 两个模板两次点击完成预填

- **GIVEN** 用户有「午餐」「早餐」两个模板
- **WHEN** 点「模板」chip，再点「午餐」
- **THEN** 表单按模板预填、sheet 关闭，可继续改金额后保存
- **AND** 全过程 2 次点击，MUST NOT 出现「更多 › → 模板 › → 全部模板」三层

---

### Requirement: 记一笔各表单行高度量级一致

各表单行内容高度 SHALL 落在 **32–44dp** 区间。收款方与描述的
`OutlinedTextField` SHALL 把 `colors` 的 border 与 container 设为
`Color.Transparent`，MUST NOT 保留整圈描边；MUST NOT 改用 `BasicTextField`
或 filled 变体。一键清空图标、placeholder、`heightIn(min = 40.dp)` 与
`bodyMedium` 字号 SHALL 保持不变。一键清空图标 SHALL 用 36dp 容器 +
18dp Icon，MUST NOT 用默认 48dp 的 `IconButton` 把输入行撑高。
键盘上方 chips 与收款方联想条的每一个可点项 SHALL 用
`heightIn(min = 40.dp)` 容器承载，字号 SHALL NOT 低于 `labelMedium`；
**chips 改非等宽 `weight(...)` 后，最窄项 MUST NOT 低于 40dp 点击区，
右上角 6dp 角标 MUST NOT 成为独立点击目标或缩小可点区域。**

#### Scenario: 窄项仍一次点中

- **GIVEN** 记一笔首屏，模板 / 标签两枚窄 chip 约 41dp 宽
- **WHEN** 点其中任一枚
- **THEN** 一次点击即打开对应 sheet，SHALL NOT 误触相邻 chip 或点不中
- **AND** 可点区不低于 40dp、字号为 `labelMedium`

---

### Requirement: 筛选面板统一网格与紧凑输入框

筛选面板 SHALL 用统一的「固定标签列 + 值 + 指示器」行网格组织全部维度，
同区域 MUST NOT 混用 filled 与 outlined 输入框；行内容高度 SHALL 落在同一量级，
可点区域 SHALL ≥ 40dp。选项数量较多的维度（账户 / 一级 / 二级分类）
SHALL 通过整行点击打开带搜索的全量单选弹层，MUST NOT 用横向滚动或多列纵向排列
把面板高度做成随选项数增长。输入框 SHALL 用 `placeholder` 而非浮动 `label`，
placeholder 与输入文字 SHALL 同字号；输入框高度 SHALL 用 `heightIn(min)` 调整，
MUST NOT 用固定 `height` 导致占位文字被裁切。
日期条件 SHALL 通过日期选择器点选，MUST NOT 要求用户手工输入 `YYYYMMDD`；
日期读写 SHALL 用 UTC 锚点往返，MUST NOT 因设备时区串一天。

#### Scenario: 维度一屏可见

- **GIVEN** 6.73 寸竖屏、账户 10 个以上、二级分类 20 个
- **WHEN** 打开筛选面板
- **THEN** 全部维度一屏可见、无需上滑
- **AND** 各维度左边界对齐、行高量级一致

#### Scenario: 输入框聚焦

- **GIVEN** 用户点击金额区间 / 备注 / 交易对方输入框
- **WHEN** 进入编辑态
- **THEN** 边框不被浮动标签撑高，placeholder 完整不裁切
- **AND** 字号与网格内其他文字一致

#### Scenario: 长名称与日期

- **GIVEN** 账户名为「XX银行0101」且用户要选 2026-11-16
- **WHEN** 打开账户单选弹层与日期选择器
- **THEN** 账户名完整显示、日期选中后写入的 `localDate` 与显示日期是同一天

#### Scenario: 筛选值回填

- **GIVEN** 用户已设置金额区间 20–100 元
- **WHEN** 关闭面板后再次打开并点「应用」
- **THEN** 两个输入框仍显示 20.00 / 100.00
- **AND** 金额条件不被静默清空

**理由**：`docs/页面优化经验与避坑指南.md` §2 / §3 / §7 记录了记一笔页面
连续 10 批的同类踩坑：固定 `height` 裁切 placeholder、浮动 label 撑高边框、
`● ` 文字前缀表选中、降噪过头砍掉触摸目标、单行横滚不便选、点标签本身作唯一入口。
筛选面板是阶段 2 起未重构过的老页面，本批把这些已证伪路径一次性收口，
避免同一类问题在第二个页面重犯。

---

### Requirement: 交易对方筛选提供输入联想

筛选面板 SHALL 提供「交易对方」条件，输入时 SHALL 从全部历史交易对方做模糊匹配并列出可选项，
MUST NOT 只列最近若干条。联想列表 SHALL 仅在输入非空时展开、每项可点区 SHALL ≥ 40dp，
空输入时 MUST NOT 占用面板高度。历史交易对方 SHALL 由一条纯只读 `DISTINCT` 查询提供，
MUST NOT 为此新增表、新增字段或把对方建成账户。
清晰筛选（含角标计数）SHALL 把交易对方计入条件数，清空筛选 SHALL 一并清除。

#### Scenario: 输入联想

- **GIVEN** 历史里有「便利蜂」「全家便利店」「好邻居」
- **WHEN** 输入「便利」
- **THEN** 列出「便利蜂」与「全家便利店」可点选
- **AND** 选中后与其他已设条件叠加生效

#### Scenario: 空输入不占高度

- **GIVEN** 用户未输入交易对方
- **WHEN** 打开筛选面板
- **THEN** 不显示历史对方列表
- **AND** 面板仍一屏可见

**理由**：交易对方是核对时最直接的定位维度；只列最近几条会漏掉较早但常用的商户。
联想复用阶段 8 已验证的 `DISTINCT` 思路，纯本地、零网络行为、零表结构变更；
把它与备注全文并列而非替代，保证两条定位路径都在。

### Requirement: 管理类页面入口固定且列表与编辑分离

分类管理、模板与周期记账、预算管理、回收站这四个管理类页面 SHALL 采用
「**顶部固定新增入口 + 惰性列表 + 编辑独立视图**」结构：
新增 / 编辑入口 SHALL 固定在 TopBar 或列表顶部，MUST NOT 因列表条目增多而下移；
列表 SHALL 用 `LazyColumn` 一类惰性容器承载，MUST NOT 全量 compose；
列表行 MUST NOT 用多个 48dp `TextButton` 横排撑高，行操作 SHALL 收进紧凑菜单；
编辑视图 SHALL 与列表状态分离，编辑既有项 SHALL 完整回填。
回收站每行 SHALL 保留时间、交易对方、分类、账户与备注信息，
MUST NOT 为压缩高度删除这些字段；右侧操作 SHALL 横排紧凑排布。
全仓选中态 SHALL 用 `FilterChip` / `selected` 容器底色表达，
MUST NOT 用 `● ` 文字前缀；管理类页面的输入框 SHALL 用 `placeholder` 而非浮动 `label`，
高度 SHALL 用 `heightIn(min)` 调整，MUST NOT 用固定 `height` 裁切占位文字。

**全站列表分割规范（第 15 批）**：明细、分类、资产、模板、预算、回收站这六类列表
SHALL 共用 `ui/components/LedgerUiParts.kt` 的 `ListDivider()` 单一来源，
1dp / `Neutral.copy(alpha = 0.10f)` / 距屏幕 16dp，MUST NOT 各页各画一遍 Box。
行高 SHALL 按信息量分档（明细 60dp / 资产 56dp / 管理页 44dp），
MUST NOT 一刀切；可点区 MUST ≥40dp。资产页账户行 SHALL 与明细页同为纯行，
MUST NOT 用 `Card` 底色 / 阴影或 1dp 间距表达边界（第 16 批修正：第 15 批只
补了分割线、没拆 Card，卡片内边距仍把内容往里收一圈，视觉量级小于明细页）。

**全站排序交互统一（第 20 批整块修订）**：分类一级、资产账户、模板 SHALL 共用
`sh.calvin.reorderable:reorderable:3.1.0`（Apache-2.0、纯 UI 库、无运行时网络行为）
与 `ui/components/ReorderListSupport.kt`；资产页 SHALL 改 `LazyColumn`，
MUST NOT 用 `Column + verticalScroll`。库 SHALL 负责拖动中 `animateItem()` 让位补间、
长按窗口、pointer pass、边缘按距离变速自动滚动；业务层 SHALL 只在 `onMove` 改内存
顺序、松手一次性落库。
**范围限定（归档补注）**：本段约束**列表页就地拖动**；独立排序页
SHALL 按「独立分类排序页与临时顺序」与「全部账户排序独立页」执行
`draftOrder` 临时顺序 + 保存 / 放弃，MUST NOT 在该页松手即落库。

**本批整块修订（替换第 16 / 20 批 `WrapRow` 自适应 chips 表述）**：
分类列表页二级 SHALL 为固定列数等宽图标网格（默认 5 列、窄屏 4 列，
`Row + weight(1f)`、固定格高、均匀 gutter、长名单行 ellipsis，
末格为「添加子类」），MUST NOT 再用按文本宽度收缩的自适应 chips，
MUST NOT 把「（停用）」拼进名称，MUST NOT 让短名行的剩余留白碎在行尾，
MUST NOT 用 `LazyVerticalGrid` 嵌进 `LazyColumn.item {}` 造成同方向嵌套滚动，
MUST NOT 用 `@OptIn(ExperimentalLayoutApi)` 的 `FlowRow`（T12），
MUST NOT 用 `Card` 底色 / 阴影表达格子边界。
分类排序页 SHALL 为两态互斥：一级态二级区折叠不渲染、
二级态白卡内为 5 列等宽图标网格，两级 SHALL 各用一套
`rememberReorderableLazyGridState`（库原生支持 Grid）、互不干涉；
列表页仍 SHALL 为等宽图标网格。排序页一级 / 二级 SHALL **整格直接拖动**，
MUST NOT 再画三道横线小手柄；二级态长按任意一级格 SHALL 自动收起二级区，
MUST NOT 设「收起，排一级」按钮；末行不足 5 列 SHALL 补 `weight(1f)` 空占位保持列对齐；
二级区 SHALL 以明显不同的浅色底标注，MUST NOT 再用小字重复拖动提示。
上移 / 下移 SHALL 在列表页二级操作弹层保留为无障碍兜底
（TalkBack 无法执行拖动手势），MUST NOT 作为主入口。
分类管理新增的排序页与分类选择页 SHALL 仍为全屏独立页（Navigation 路由），
MUST NOT 做成内容受限的小弹窗，MUST NOT 新增第 5 个底部 Tab；
排序页的 `draftOrder` 临时顺序 SHALL 与已落库 `sortOrder` 分离，
放弃 MUST NOT 写库，保存 SHALL 一次性全量写回且 MUST NOT 丢项。
分类列表页一级 SHALL 保持竖排 `LazyColumn`，MUST NOT 把 15 个一级横排塞进一屏；
底部胶囊入口与大 `+` FAB SHALL 在列表滚动时保持可见。

依赖取舍按 2026-11-17 使用者澄清：判断标准是「是否默认回传厂商且用户不可控」，
不是「依赖数量」；仍 MUST 记录坐标、许可证与传递依赖审计，仍 MUST NOT 引入
广告 / 埋点 / 崩溃上报 / 远程配置 SDK。零表结构变更。

#### Scenario: 分类增多不推走入口

- **GIVEN** 系统有 16 个一级分类与 98 个二级分类
- **WHEN** 打开分类管理页并滚动列表
- **THEN** 新增入口与大 `+` FAB 仍固定可见

#### Scenario: 六页分割线一致

- **GIVEN** 依次打开明细、分类、资产、模板、预算、回收站
- **WHEN** 查看相邻两行之间
- **THEN** 均为同一颜色 / 厚度 / 16dp 内缩的细线，无卡片贴片或无线留白

#### Scenario: 等宽网格不留碎白
- **GIVEN** 「居住」下有 11 个二级，含 7 字长名与停用项
- **WHEN** 展开「居住」
- **THEN** 二级呈 5 列等宽格、gutter 均匀、行尾无大片碎留白
- **AND** 长名单行省略、停用为小角标、末格为「添加子类」

#### Scenario: 排序页放弃不落库
- **GIVEN** 在排序页拖动过一级与二级但未保存
- **WHEN** 按返回并选择放弃
- **THEN** 库内 `sortOrder` 零变化，重进列表页顺序不变
- **AND** 底部 4 Tab 结构与进入前一致

**理由**：第 15 批把「统一网格」从「左边界 + 行高量级」补完第三半——
行与行之间的边界也要单一来源；第 12 批只同步了容器骨架，没同步分割写法。
使用者 2026-11-23 用真机对照图纠正了「等宽图标网格必然留白大」的外推：
留白来自 `WrapRow` 按文本收缩 + 行尾余量 + 停用后缀拼进名称，
不是固定列数网格本身。本批按标题整块替换，
MUST NOT 只追加 ADDED 而让两条相反要求并存（活文档 §5）。

---

### Requirement: 洞察总结卡给出可解释的均值与笔数

统计页 SHALL 在总结卡显示所选周期的总计大数字、收入 / 支出 / 结余、流水笔数，
以及日均（月度）或月均（年度）；点击均值 SHALL 显示
「总额 ÷ 周期天数 / 月数 = 均值」的计算过程。
笔数与均值 MUST 由 SQL 聚合或纯 JVM 整数运算得出，MUST NOT 加载流水实体后计数。
所选周期无流水时 SHALL 显示空状态与「切月 / 切视图」引导，
MUST NOT 清空用户已设的币种与视图条件。

#### Scenario: 均值可解释

- **GIVEN** 某月支出总计 3100.00 元、共 12 笔、该月 31 天
- **WHEN** 点总结卡的「日均」
- **THEN** 弹出「3100.00 ÷ 31 = 100.00」的计算过程
- **AND** 分母为自然月天数，不按有流水的天数折算

#### Scenario: 空周期

- **GIVEN** 所选月份没有任何流水
- **WHEN** 打开统计页
- **THEN** 显示空状态与切到收入 / 换个月份的引导
- **AND** 当前选中币种与视图不被重置

**理由**：BeeCount 的 `BeeAnalyticsSummary` / `BeeEmptyState` 把聚合数据翻译成结论，
且让分母可解释；这比增加图表数量更能回答「这个月花得怎么样」。

---

### Requirement: 分类占比卡提供分类管理短跳转

分类占比卡 SHALL 在标题右侧提供「分类管理 ›」入口，点击 SHALL 进入已有的
分类管理二级页（Navigation 路由 `categories`），MUST NOT 新增导航格数或第 5 个 Tab。
第 69 批把收款方 / 账户拆回独立卡后，该入口 SHALL 保留在分类占比卡上，
MUST NOT 因拆分而丢失；MUST NOT 新增第二个「分类管理」入口。

#### Scenario: 从占比卡进分类管理

- **GIVEN** 统计页分类占比卡
- **WHEN** 点「分类管理 ›」
- **THEN** 进入分类管理页，返回后底部仍是 4 个 Tab

#### Scenario: 拆分不丢入口

- **WHEN** 收款方 / 账户拆成独立折叠卡后
- **THEN** 分类占比卡标题右侧仍只有一个「分类管理 ›」入口且可点

### Requirement: 预算进度收进总结卡且不新增控件

预算进度 SHALL 收进总结卡底部（显示「已用 / 限额 / 剩余或超支」与自绘进度条），
MUST NOT 作为独立卡片再占一屏区块；消耗 MUST 继续走 SQL 聚合、单月计算，
MUST NOT 在年度视图把 12 个月累加去比「每月限额」。

#### Scenario: 预算超支

- **GIVEN** 餐饮月预算 1000 元、当月净支出 1200 元
- **WHEN** 查看总结卡
- **THEN** 进度条标红并显示超支 200 元
- **AND** 消耗来自按当前月与当前币种的 SQL 聚合

**理由**：第 13 批把预算从「中段独立卡」收进大卡，保持总结卡自洽，不改变既有口径；
第 14 批统计页改为允许滚动，此约束的理由不再是「省一屏区块」，而是避免同一结论散落两处。

### Requirement: Firefly III 迁移二级页

设置 → 数据与备份 SHALL 提供「导入 Firefly III 导出（CSV）」入口，
点击 SHALL 进入独立二级页（不改变 4 Tab 骨架）。
该页 SHALL 通过 `OpenDocument` 由使用者选择文件，MUST NOT 申请存储或网络权限。
页面 SHALL 在选文件前用中文说明支持的字段、四类交易的落法、
拆分交易与外币处理、以及不会导入的附属对象。
解析后 SHALL 显示映射预览（账户与分类逐项可改）与汇总报告，
SHALL 提供「覆盖已有账户的期初余额」开关且默认关闭，
确认写入前 SHALL 二次确认。
结果 SHALL 报告写入 / 跳过 / 新建账户 / 新建分类 / 期初应用 / 期初跳过六个数，
并附耗时与文件总行数；失败 SHALL 明示「已整体回滚，账本未改动」且同样显示结果视图。
结果状态 MUST 原子更新，MUST NOT 被并行 reload 覆盖回运行中态。

#### Scenario: 选文件前看得懂

- **GIVEN** 使用者第一次进入迁移页
- **WHEN** 尚未选择文件
- **THEN** 能看到中文逐字段说明与「不会导入」清单
- **AND** 页面不显示任何网络或权限请求

#### Scenario: 确认前可改映射

- **GIVEN** 解析出 21 个待映射账户
- **WHEN** 使用者把某账户从「新建」改成映射到已有账户
- **THEN** 该行显示映射目标且确认前账本零变化

---

### Requirement: 统计图表可辨与趋势粒度

分类占比图的扇区与图例 SHALL 使用按分类 id 稳定分配的低饱和同色系色板，
MUST NOT 直接读自动生成分类恒为 `#888888` 的 `colorHex`，
MUST NOT 恢复高饱和 6~7 色或把颜色交回使用者配置。
横屏全屏趋势页 SHALL 处理系统栏 insets，标题行 MUST NOT 与状态栏时间 / 电量重叠。
趋势卡 SHALL 提供「收入 − 支出」净结余线（允许负值、0 线居中、仅当前币种），
SHALL 提供「近 5 年」多年周期；点某年数据点 SHALL 下钻回该年的年度视图。
净结余与多年视图 MUST NOT 跨币种混算，MUST NOT 新增统计口径出口。

#### Scenario: 自动生成分类的占比图

- **GIVEN** Firefly III 导入自动新建了 10 个分类，全部 `colorHex = #888888`
- **WHEN** 打开统计页分类占比卡
- **THEN** 各扇区颜色可区分且每次打开保持一致

#### Scenario: 净结余为负

- **GIVEN** 某月支出大于收入
- **WHEN** 切到「净结余」
- **THEN** 折线落在 0 线下方，竖轴显示负值刻度，点击数据点显示负的具体金额

## 归档合入：polish-stats-and-import-metadata

### Requirement: 环形图图例可点与相邻扇区可辨
分类占比卡的图例每项 SHALL 与对应扇区共用同一个下钻回调，整行可点且触摸区
≥40dp；图表色板 SHALL 按分类 id 稳定分配、低饱和但**色相分明**，相邻扇区在
真机上 MUST 可区分；MUST NOT 用同色系明度递进让相邻扇区糊成一片，
MUST NOT 回到第 4 批否掉的高饱和 6~7 跳色，MUST NOT 把 `colorHex` 开放给用户
可配；分类列表 / chip 的中性单色标识 MUST NOT 因此改变。

#### Scenario: 点图例跳明细
- **WHEN** 使用者在「按小类」视图点图例里某一项的名称或色块
- **THEN** 与点对应扇区行为一致，跳到明细 Tab 并带该分类筛选条件

#### Scenario: 相邻颜色可辨
- **WHEN** 存在 5 个以上分类扇区
- **THEN** 任意相邻两扇区与图例色块目视可区分，且同一分类多次打开颜色不变

### Requirement: 明细页年月一次直达与时间范围语义明示（MODIFIED，第 42 批）

明细页 SHALL 提供可一次点到任意年份与 12 个月份的年月选择器；
记一笔与组合筛选面板的官方 `DatePicker` SHALL 保留日视图并支持跳转年月，
MUST NOT 引入第三方日期库或自造带动画的整套日期选择器。
**时间范围留空时系统 SHALL 默认返回全部记录**，MUST NOT 隐式等价于「只查当前月」、
也不得要求使用者先点「全部」快捷段才能看到跨月流水。摘要行 SHALL 明示
当前是「全部记录 · 当前定位 YYYY-MM · 共 N 条命中」还是「当前月 / 自定义范围」。
月份条与年月选择器 SHALL 退化为纯定位锚点：MUST NOT 注入月份过滤、
MUST NOT 清空已设筛选条件、MUST NOT 重建 Pager 把列表弹回顶部。
跨月列表 SHALL 连续分页一滚到底，跨月处 SHALL 插入月分组头；
分组头 MUST NOT 另跑聚合查询，命中数复用既有按年 GROUP BY 角标。
空月 SHALL 在选择器里灰显不可点，避免使用者逐月白翻。
使用者主动选「当前月」快捷段时 SHALL 切回按月视图。

#### Scenario: 不带时间直接看全部记录

- **GIVEN** 明细页筛选面板时间范围留空，库里 2026-03、2026-08、2026-11 各有流水
- **WHEN** 进入明细页
- **THEN** 一屏连续分页返回三个月的全部命中记录，跨月处显示月分组头
- **AND** 摘要行写出「时间：全部记录 · 当前定位 2026-11 · 共 N 条命中」，
  使用者 MUST NOT 靠「一个月一个月翻」判断其它月份有没有记录

#### Scenario: 月份只定位不过滤

- **GIVEN** 全部记录视图，另设了账户条件
- **WHEN** 点月份标题选 2026-03，或点左右箭头切月
- **THEN** 列表滚动到该月首条，账户条件与其它筛选条件一条不丢
- **AND** 查询结果仍是全部月份中满足条件的流水，月份不进 WHERE

#### Scenario: 空月不再白翻

- **GIVEN** 2026 年 5 月没有任何记录
- **WHEN** 打开年月选择器
- **THEN** 5 月格灰显不可点并显示「—」，角标来自同一条按年 GROUP BY

#### Scenario: 仍可主动切回按月

- **GIVEN** 全部记录视图
- **WHEN** 筛选面板点「当前月」快捷段
- **THEN** 列表仅返回当前定位月，摘要行写明「时间：当前月 YYYY-MM」

#### Scenario: 一步跳到八个月前

- **GIVEN** 明细页当前定位 2026-11
- **WHEN** 点月份标题打开选择器，展开年份选 2026、点 3 月
- **THEN** 列表滚到 2026-03 首条，无需连点 8 次箭头，且筛选条件不丢

#### Scenario: 记一笔月末边界

- **GIVEN** 记一笔当前日期为 1 月 31 日
- **WHEN** 在日期选择器里跳到 2 月
- **THEN** 选中日期收敛到该月最后一天（平年 2/28、闰年 2/29）
- **AND** 日视图仍是官方 DatePicker，不静默丢日期、不崩

## 第 52 批：分类全量选择器与一级直记

### Requirement: 分类全量选择器采用分组 chips 且不用下拉框

记一笔「分类 ›」、明细筛选「分类」、Firefly 导入分类映射、
**以及模板与周期规则表单的「分类」行** SHALL 复用同一份只读
`CategorySelectDialog`，采用**顶部搜索 + 左侧一级分组导航 + 右侧 4 列
等宽彩色图标网格**版式，4 字分类名 SHALL 完整可见。
**模板与周期规则 SHALL 以整行 `›` 入口打开该选择器**，
MUST NOT 再使用 `DropdownMenu` / Spinner / ExposedDropdown / 任何下拉列表；
`allowAll` SHALL 在该处复用为「不指定分类」，入口标题 SHALL 写明「可不指定」，
MUST NOT 把 `allowAll` 的 tile 文案改成本体之外的第二套实现。
选择器 SHALL 显示彩色 `CategoryIconCatalog` 图标，
统计页分类占比卡的图例列表 SHALL 同样使用彩色 `CategoryIconCatalog` 图标
（`CategoryGlyph`，缺项回退中性圆 + 首字）；
记一笔快捷栏、明细列表行、账户详情 SHALL 仍是中性圆 + 中文首字。
四处入口 SHALL 保持「整行 › 入口 + 立即选中 + 无输入框 / 无保存按钮 / 无写库路径」。
彩色图标配色 MUST 走 `CategoryIconTints`，`colorHex` MUST NOT 被改写，
统计扇区色板 MUST NOT 改为图标配色。
`CategorySelectDialog` 本体（`columns = 4` / `allowAll`）SHALL 零改动。

#### Scenario: 模板选分类不再下拉

- **GIVEN** 新建支出模板，预算分类有「餐饮 / 食材」
- **WHEN** 点「分类」整行
- **THEN** 打开与记一笔同款的 4 列图标网格，左侧一级导航可见
- **AND** 点「食材」立即回填并关闭，入口值显示「食材」
- **AND** 展开时 MUST NOT 出现任何下拉菜单

#### Scenario: 模板可不指定分类

- **GIVEN** 模板已选「餐饮 / 食材」
- **WHEN** 再点分类行并点「全部分类」
- **THEN** 入口值回到「不指定」，保存后 `categoryId = null`、`rootCategoryId = null`
- **AND** MUST NOT 因无法清空而被迫重建模板

#### Scenario: 统计占比图例彩色

- **GIVEN** 统计页分类占比卡处于「按小类」口径
- **WHEN** 查看图例列表
- **THEN** 预置分类显示彩色 Tabler 图标，自定义 / 缺项分类回退中性圆 + 中文首字
- **AND** 环形扇区颜色仍由 `ChartData.chartArgb` 按 id 稳定分配，与图标 tint 无关

#### Scenario: 其余页面零回归

- **WHEN** 打开记一笔快捷栏、明细列表行或账户详情
- **THEN** 分类标识仍是中性圆 + 中文首字，MUST NOT 出现彩色图标

---

### Requirement: 资产页蓝底身份区与分组头部强化

**第 79 批归档修订**：本条标题沿用历史名称以便 MODIFIED 匹配，实际形态已改为中性卡片。
资产页 SHALL 以中性卡片顶部身份区承载页面标题、账户数与「含已归档」/「+ 新增」入口，
并在身份区内**按 `currencyCode` 逐组**展示资产 / 负债 / 净资产。
MUST NOT 把 `state.groups` 汇总成一个跨币种的单一「净资产」总数，
MUST NOT 因借鉴一羽记账截图而违反基线「资产页按币种分组且移除单一净资产总数」。
账户分组标题 SHALL 用蓝色小竖条 + 分组名 + 账户数表达，
MUST NOT 显示按 `AccountType` 聚合的跨币种金额。
账户行 SHALL 保持纯行 + `ListDivider()`，MUST NOT 用 `Card` 底色 / 阴影 / 1dp 间距
表达行边界（基线「管理类页面入口固定且列表与编辑分离」）。

#### Scenario: 两币种账户不出现虚假总数

- **GIVEN** 一个 HKD 账户与一个 CNY 账户
- **WHEN** 打开资产页查看顶部身份区
- **THEN** 出现 HKD / CNY 两组各自的资产 / 负债 / 净资产
- **AND** 页面不出现把两币种加在一起的单一净资产数额

#### Scenario: 分组标题不混算

- **GIVEN** 「资产账户」分组下有 CNY 与 HKD 两个账户
- **WHEN** 查看分组标题
- **THEN** 显示蓝色小竖条、分组名与「2 个账户」，MUST NOT 显示跨币种分组金额

---

---

> **归档注记**：本条「小计」标签按第 60 批 Q1A 统一为「净资产」，见「资产页逐币种净资产标签与负债红字负数」。

### Requirement: 资产账户行紧凑化且操作收进菜单

资产账户行 SHALL 以「图标圆 + 名称 / 卡号后四位 / 币种 + 金额右对齐 + 副文案」
组织，图标 SHALL 复用 `CategoryIcon` 的中性圆 + 中文首字，
MUST NOT 引入彩色图标、银行 logo 资源或在线图标下载（联网能力 SHALL 先经 `project.md` 显式授权，本批不申请）。
信用负债账户 SHALL 在余额为零时显示「无欠款」、有额度时沿用
`Reconciliation.availableCredit` 显示「可用 X」；
「不计入净资产」与「已归档」标注 SHALL 保留。
行操作 SHALL 收进单个「⋮」紧凑菜单，菜单 SHALL 含
详情 / 对账 / 编辑 / 归档或恢复 / 删除 / 上移 / 下移七项，
MUST NOT 再用多个文字按钮横排撑高行。
`longPressDraggableHandle` SHALL 挂在整格行根上，
MUST NOT 再显示三道横线「≡」小手柄；`rememberKeyedReorder`、
`commitAccountOrderAll` 与上移 / 下移 TalkBack 兜底 SHALL 零回归。

#### Scenario: 行紧凑且七项可达

- **GIVEN** 一个含卡号后四位的储蓄卡账户与一个信用负债账户
- **WHEN** 查看账户行并点「⋮」
- **THEN** 行显示图标圆、名称带 `(last4)`、金额右对齐；信用负债显示「无欠款」或「可用 X」
- **AND** 菜单七项逐项可达，长按整格仍可拖动排序
- **AND** 页面不出现「≡」手柄与横排文字操作按钮

---

---

### Requirement: 新建账户页采用近全屏紧凑表单

新增账户 SHALL 由 `AlertDialog` 小弹窗改为近全屏 `Dialog`
（`usePlatformDefaultWidth = false`）：中性卡片 TopBar「← 添加资产账户 / 保存」、
分组小竖条、「图标 + 账户名称」紧凑白底小卡、底部大保存按钮。
表单字段与顺序 SHALL 保持不变（账户名称 / 卡号后四位 / 账户类型 /
期初余额 / 信用额度），`onConfirm` 签名与 `AssetsViewModel.addAccount` SHALL 零改动；
输入框 SHALL 用 `placeholder` 与 `heightIn(min)`，MUST NOT 用浮动 `label` 或固定 `height`。
MUST NOT 新增备注、隐藏账户、默认使用或账户图标等业务字段，
MUST NOT 升 `AppDatabase` 版本；`EditAccountDialog` 本批 SHALL 零改动。
MUST NOT 新增 Navigation 路由或改变 4 Tab 结构。

#### Scenario: 新建页字段零回归

- **GIVEN** 打开「+ 新增」
- **WHEN** 点「招行储蓄卡」模板、输入卡号后四位 1234 与期初余额 1000 并保存
- **THEN** 创建非 `LIABILITY` 的资产账户，名称带 `(1234)`，期初余额重算生效
- **AND** 页面仍是 4 Tab、无新路由、数据库版本不变

#### Scenario: 键盘弹起仍可保存

- **GIVEN** 中小屏手机在新建页聚焦「期初余额」
- **WHEN** 软键盘弹起
- **THEN** 底部大保存按钮仍可见可点，表单区可滚动，MUST NOT 被键盘完全遮挡

---

### Requirement: 资产页逐币种净资产标签与负债红字负数

资产页中性卡片顶部身份区 SHALL 按 `currencyCode` **逐组**渲染，
每组 SHALL 把原「X 小计」标为「**净资产**」，
SHALL 以常规色显示「资产 +X」、以语义 `LedgerColors.ExpenseRed`（深色下自动为柔和珊瑚红 `0xFFF0A6A0`）显示「负债 -X」
（负号为纯展示文本，`CurrencyGroup.liabilitiesCents` 仍为绝对值、
MUST NOT 参与任何运算）。
账户行负余额 SHALL 继续以红字负数显示，MUST NOT 被本批改掉。
MUST NOT 把各组 `netCents` 汇总成一个跨币种的单一「净资产」总数，
MUST NOT 违反基线「资产页按币种分组且移除单一净资产总数」。

#### Scenario: 两币种各自净资产

- **GIVEN** 一个 HKD 账户与一个 CNY 账户，其中 CNY 有一笔信用负债欠款
- **WHEN** 打开资产页查看顶部身份区
- **THEN** 出现 HKD / CNY 两组各自的「净资产」，CNY 组负债为红字负号
- **AND** 页面不出现把两币种加在一起的单一净资产数额

#### Scenario: 账户行负数语义不变

- **GIVEN** 信用卡账户 `balanceCents = -50000`
- **WHEN** 查看账户行
- **THEN** 金额显示为负数且为红字，MUST NOT 变成绝对值或白字

---

---

### Requirement: 记一笔常用候选按独立优先级排序且带卡种标志

记一笔常用账户候选 SHALL 按 `quickOrder` 排序取前 4，
SHALL 允许**不同 `AccountType` 的账户一起参与排序**（常用置顶），
MUST NOT 再取 `sortOrder` 的全局前 4。
`AccountQuickCandidates.select` 的「常用前 N + 当前选中项必可见」
语义 SHALL 保持不变，只更换传入列表的数据源顺序。
每个候选 chip SHALL 以**10dp** 小角标单字（「储」/「信」）
显示 `cardKind`，MUST NOT 用文字后缀挤占账户名宽度。
**第 61 批**：候选与全账户选择弹层的 label SHALL 经 `accountBaseLabel()`
带上 `cardLast4`（如「工商银行 (1122)」），MUST NOT 只显示纯名；
「当前：」文案 SHALL 同样基于该组合名。
**第 84 批修订**：账户搜索框已下线，原文「弹层搜索键」「输入后四位 MUST 能搜到」
作废；多张同名银行卡 SHALL 改由每格次要行「尾号 1122 / 3344」直接区分，
SHALL NOT 要求使用者记住或输入后四位。
`AccountQuickCandidates.select` / `labelOf` / `promoteById`
的纯 JVM 契约 SHALL 零改动，只更换映射进 `AccountQuickItem.name` 的字符串。
记一笔分类快捷栏图标 SHALL 由 40/34dp 收紧为 **28/24dp**，
分类管理页 5 列网格仍 SHALL 用 40dp（两处密度各按各的场景）。
资产页分组内排序 MUST NOT 改变候选顺序。

#### Scenario: 跨类型常用置顶

- **GIVEN** 使用者在「全部账户排序」页把一张信用卡与一张储蓄卡排到前两位
- **WHEN** 打开记一笔
- **THEN** 常用候选前两项就是这两张卡，且带各自的小标志
- **AND** 资产页分组内顺序未因此改变

#### Scenario: 多张同名银行卡可区分（改为显示区分）

- **GIVEN** 两张账户名同为「工商银行」、卡号后四位分别为 1122 与 3344
- **WHEN** 打开记一笔查看快捷候选 /「当前：」/ 全账户选择弹层
- **THEN** 三处都分别显示「工商银行 (1122)」与「工商银行 (3344)」
- **AND** 弹层每格次要行显示「尾号 1122 · 储蓄卡 · CNY」/「尾号 3344 · 信用卡 · CNY」
- **AND** MUST NOT 依赖搜索框才能分辨，两张卡各带不占名称宽度的 10dp 卡种小标志

#### Scenario: 记一笔图标密度收紧

- **GIVEN** 打开记一笔
- **WHEN** 查看分类快捷栏与账户候选 chip
- **THEN** 一级分类圆图标为 28dp（选中）/ 24dp（未选中），账户小标志为 10dp
- **AND** 分类管理页网格仍为 40dp，MUST NOT 被连带改小


---

### Requirement: 全部账户排序独立页

系统 SHALL 提供独立「全部账户排序」页（NavHost 路由 `account-quick-sort`），
入口 SHALL 在资产页中性卡片顶部身份区固定可见，MUST NOT 新增第 5 个底部 Tab。
该页 SHALL 以单一 `LazyColumn` 展示**全部未归档账户、不分类型**，
SHALL 复用 `sh.calvin.reorderable:reorderable:3.1.0` 与
`rememberKeyedReorder`，MUST NOT 自写 `Modifier.pointerInput`；
SHALL 提供「保存 / 放弃」与一行「这里排的是记一笔里常用账户的先后顺序」提示。
`draftOrder` SHALL 与已落库 `quickOrder` 完全分离：
放弃 MUST 零写库，保存 SHALL 一次性写入并在完成后刷新。
每行 SHALL 显示小标志、账户名与类型标签，
上移 / 下移 SHALL 以 `semantics.customActions` 保留作 TalkBack 兜底。

#### Scenario: 保存与放弃

- **GIVEN** 在独立页拖动若干账户后
- **WHEN** 点「放弃」
- **THEN** 页面关闭、`quickOrder` 零变化、记一笔候选不变
- **WHEN** 重新进入拖动后点「保存」
- **THEN** `quickOrder` 一次性写回、记一笔候选按新顺序、`sortOrder` 零变化

#### Scenario: 导航格数不变

- **GIVEN** 从资产页进入全部账户排序页
- **WHEN** 查看底部导航
- **THEN** 仍是明细 / 资产 / 统计 / 设置 4 个 Tab


---

---

### Requirement: 账户卡种可编辑且导入账户有保守默认值

**第 61 批**：新增账户页与编辑账户页 SHALL 都提供
「不设 / 储蓄卡 / 信用卡」三态 `cardKind` 选择，
MUST NOT 只有从模板建卡时才带 `DEBIT` / `CREDIT`。
编辑页 SHALL 把**当前选中的** `cardKind` 传给 `onConfirm` 落库，
MUST NOT 写死传 `account.cardKind` 原值
（第 60 批正是这样导致「编辑选不了卡种」）。
导入 / CSV 等**非模板路径**建账户时 SHALL 一并写入 `quickOrder`
与 `cardKind`：保守默认 `LIABILITY → "CREDIT"`，
`ASSET` SHALL 不猜（微信零钱 / 支付宝 / 储蓄卡混在一起，猜了会误标），
使用者 SHALL 可在编辑页随时改。
账户图标位与记一笔 chip SHALL 以 `cardKind` 决定是否有「储 / 信」标志，
未设置时 SHALL 不显示标志、MUST NOT 崩。

#### Scenario: 导入账户补设卡种

- **GIVEN** 通过导入建了一个 `ASSET` 账户、一个 `LIABILITY` 账户
- **WHEN** 打开记一笔查看候选
- **THEN** 信用负债账户带「信」标志，资产账户不带标志（不误标为「储」）
- **WHEN** 编辑资产账户把卡种改成「储蓄卡」并保存
- **THEN** `cardKind = "DEBIT"` 落库，列表与记一笔出现「储」标志

#### Scenario: 编辑保存写回选中值

- **GIVEN** 账户 `cardKind = null`
- **WHEN** 编辑页选「信用卡」并保存
- **THEN** `cardKind = "CREDIT"`，MUST NOT 仍为 null

---

---

### Requirement: 筛选与统计的账户显示名与全站一致

**第 62 批**：筛选面板的账户选项 SHALL 经 `accountBaseLabel()` 带上
`cardLast4`，MUST NOT 只显示纯名。
**第 84 批修订**：筛选面板的账户条件自第 83 批起已走通用
`AccountSelectDialog`，本批该选择器的搜索框下线，故原文「picker 的搜索按
label 匹配，输入后四位 SHALL 能定位」作废；同名卡 SHALL 由选项 tile 的
`accountSecondaryLabel` 次要行「尾号 xxxx」直接区分。
`FilterSheet.accountOptions` / `displayLabel` 仍 SHALL 保留作
`PickRow` 的**只读展示名**（第 62 批带卡号口径），MUST NOT 因删搜索框连带删除。
明细页的筛选 chip（含 `accountDisplayName` 为空时的 fallback）SHALL
同样带卡号。
统计页「账户」维度排行 SHALL 经 `accountBaseLabel()` 带卡号；
`InsightData.RankItem.name` 同时是下钻 `filterByAccount()` 的只读展示名，
故从统计跳明细后的筛选 chip SHALL 自动带上卡号，
MUST NOT 在 UI 侧再拼一次。
明细列表副行与流水详情页 SHALL 经 `accountLabel()` 组合 DAO 投影的
`fromAccountName` / `fromAccountLast4`（转入同理），
MUST NOT 直接显示 `row.fromAccountName` 纯名。
资产页删除确认弹窗、账户详情标题、默认账户设置、对账 / 核对标题、
回收站流水行 SHALL 全部带卡号。
**第 63 批修订 CSV 往返语义**：CSV 导出的 `account` 列 SHALL 写
**展示名**（`accountLabel(name, cardLast4)`，如「工商银行 (1122)」），
并 SHALL 新增可选 `cardLast4` 列写机器匹配键；
导入 SHALL 按 `(name, cardLast4)` 复合匹配，
MUST NOT 再用 `firstOrNull { it.name == ... }` 静默命中同名第一张卡。
**加密备份 / `BackupAccount.name` 仍是纯名且 MUST NOT 被改动**（走 `id` 还原）。

#### Scenario: 筛选与统计不再撞名

- **GIVEN** 两张账户名同为「工商银行」、卡号后四位 1122 / 3344
- **WHEN** 打开筛选面板账户选项、统计页账户排行、从排行跳明细后的筛选 chip
- **THEN** 三处都分别显示「工商银行 (1122)」与「工商银行 (3344)」
- **AND** 筛选面板账户 tile 的次要行同样带「尾号 1122」/「尾号 3344」，
  MUST NOT 依赖搜索框才能分辨

#### Scenario: 导出带卡号且复合匹配回原账户

- **GIVEN** 两张 `name = "工商银行"`、`cardLast4` 为 1122 / 3344 的账户各有流水
- **WHEN** 导出 CSV
- **THEN** `account` 列分别是「工商银行 (1122)」「工商银行 (3344)」，
  `cardLast4` 列分别是 `1122` / `3344`
- **WHEN** 用该文件重建 / 导入
- **THEN** 两笔流水分别回到 id 对应的原账户，MUST NOT 归到同一张卡
- **AND** 加密备份的 `BackupAccount.name` 仍是纯名「工商银行」

#### Scenario: 老 CSV 同名歧义显式报错

- **GIVEN** 库里有两张同名「工商银行」、卡号 1122 / 3344
- **WHEN** 导入一份没有 `cardLast4` 列、账户列写纯名「工商银行」的老 CSV
- **THEN** 导入 MUST 报「账户不唯一，请指定 cardLast4」并整批回滚
- **AND** MUST NOT 静默把流水挂到第一张卡


---

### Requirement: 统计页采用一羽式中性灰底与纯白卡片

系统 SHALL 在 `LedgerTheme` 的 `lightColorScheme` 中提供一羽式浅色体系：
页面背景 SHALL 为**中性浅灰** `0xFFF2F3F7`（第 69 批的 `0xFFF4F6FB`
太浅且偏蓝，与白卡对比不足，第 70 批按真机图三改中性灰），
`surface` 与全部 `surfaceContainer*` SHALL 为纯白 `0xFFFFFFFF`
或近白 `0xFFFAFAFC`，`surfaceVariant` SHALL 为 `0xFFE9EBF1`，
`onSurface` SHALL 为柔和深灰蓝 `0xFF1F2430`，
`outlineVariant` SHALL 为 `0xFFE2E4EB`，
`primary` SHALL 为清新蓝 `0xFF5B7CFA`。
**第 79 批归档修订**：API 31+ 动态浅色 SHALL 由 `dynamicWithNeutralSurface()`
只借用动态强调色，上述中性槽位 MUST 套回本静态板的值，
MUST NOT 让 `scheme.background` 与卡片容器同色。
`CategoryHeaderBlue` SHALL 读 `LedgerColors.Primary` 以保持单一真相源。
统计页每张卡片 SHALL 走统一 `StatsCard` 出口：
显式 `CardDefaults.cardColors(containerColor = LedgerColors.CardSurface)` +
`CardDefaults.cardElevation(defaultElevation = 0.dp)` + `LedgerShapes.Card`（16.dp）。
卡片间距 SHALL 为 `LedgerSpacing.CardGap = 16.dp`，MUST NOT 收到 12dp。
**M3 `Card` 默认 1.dp tonalElevation 会把纯白染成淡蓝灰，
这是「主体不突出、卡片边界糊」的根因，MUST 显式归零。**
SHALL 靠「中性灰底 + 纯白卡 + 卡片间距 + 圆角」形成清晰边界，
MUST NOT 新增阴影、MUST NOT 新增描边、MUST NOT 用卡片堆叠
（基线「明细页极简网格」要求）。
`ExpenseRed`（红 = 支出）/ `IncomeGreen`（绿 = 收入）语义色与
`applyReverseColors` 反转开关 MUST NOT 改动；`colorHex` MUST NOT 被改写。
配色改动 SHALL 只改色值，MUST NOT 改任何布局 / 手势 / 数据 / 聚合 SQL。
第 69 批使用者选 Q5B（全 App 一起变），全 23 个含 `Card` / `.background`
的文件观感都会变化，MUST 如实登记并列入真机待验。

#### Scenario: 灰底白卡边界清晰

- **WHEN** 打开统计页
- **THEN** 页面底为中性浅灰、每张卡片为纯白圆角块，卡片之间间隙与边界清晰可辨
- **AND** 不再出现 M3 默认 tonalElevation 染出的淡蓝灰卡面

#### Scenario: 动态浅色下卡片边界不糊

- **GIVEN** API 31+ 设备启用系统动态色
- **WHEN** 打开统计页
- **THEN** 卡片容器读固定的 `LedgerColors.CardSurface`，与页面中性灰底边界清晰
- **AND** MUST NOT 出现第 78 批「卡片与底同色」的回归

#### Scenario: 红绿语义不变

- **GIVEN** 使用者开启「配色反转」
- **WHEN** 查看任意金额
- **THEN** 红绿语义按既有开关互换，浅灰 / 纯白体系本身不变

#### Scenario: 全 App 观感待验

- **WHEN** 第 70 批出包后装机
- **THEN** 记一笔 / 明细 / 资产 / 分类管理 / 设置各页观感 MUST 由真机逐页确认
- **AND** CI 全绿不替代观感验收

### Requirement: 收款方与账户默认折叠只读卡

统计页 SHALL 提供一张与分类占比卡**独立**的「收款方 / 账户」卡片，
默认 SHALL 为折叠态，MUST NOT 常驻展开；点击标题行 SHALL 展开或收起。
卡片左上角 SHALL 只有一个「收款方 ⇄ 账户」切换标签，点一下在两个维度间来回切，
MUST NOT 摆一排维度 chip、MUST NOT 新增第二个切换控件。
展开后 SHALL 按金额降序列出当前维度 Top5：中性圆 + 中文首字、
名称（账户维度 SHALL 沿用 `accountBaseLabel` 带卡号）、
¥金额与按占比填充的灰色长条。
两个维度 SHALL 复用既有 `state.topCounterparties` / `state.topAccounts`，
MUST NOT 新增 DAO 聚合查询、MUST NOT 跨币种混算。
该卡 SHALL 为**只读展示**，MUST NOT 跳明细 Tab、MUST NOT 向
`DetailViewModel` 注入筛选状态；收款方 / 账户不是分类，
MUST NOT 混进分类占比卡。

#### Scenario: 默认折叠

- **WHEN** 首次打开统计页
- **THEN** 收款方 / 账户卡只显示标题行与「展开」，不显示 Top5 列表

#### Scenario: 切换维度并展开

- **WHEN** 点标题展开，再点左上角「收款方 ⇄ 账户」
- **THEN** 列表切换到另一维度 Top5，仍只有这一个切换控件
- **AND** 账户行显示带卡号的显示名，灰条长度与占比一致

#### Scenario: 只读不跳转

- **WHEN** 点展开列表里的任意一行
- **THEN** 不切换 Tab、不写入明细筛选，页面保持统计页

---

### Requirement: 全局主题支持深浅模式且语义色全程可读

系统 SHALL 在 `LedgerTheme` 提供 `darkTheme` 参数，默认 SHALL 为 `isSystemInDarkTheme()`。
深色 SHALL 走本项目静态 `darkColorScheme`（背景 `0xFF121417`、卡片 `0xFF1E2025`），
MUST NOT 走系统动态深色、MUST NOT 使用纯黑或纯白。
浅色动态色 SHALL 由 `dynamicWithNeutralSurface()` 只借用 primary / secondary /
tertiary 及其容器色，中性槽位（background / surface / surfaceVariant /
surfaceContainer* / outlineVariant）MUST 套回第 70 批静态值，
MUST NOT 让页面底与卡片容器同色（这是「卡片边界糊」的根因）。
`LedgerColors` SHALL 以可观察状态持有 ExpenseRed / IncomeGreen / Neutral / Primary /
OnPrimary / Background / CardSurface / OnSurface / SurfaceVariant / OutlineVariant，
由 `LedgerTheme` 在一次 `SideEffect` 里同步；MUST NOT 用 CompositionLocal 重写
237 处引用（Canvas draw scope 与普通函数在非 Composable 上下文 SHALL 仍能读到）。
深色下 ExpenseRed / IncomeGreen SHALL 换成同语义的柔和珊瑚红 `0xFFF0A6A0` /
柔和绿 `0xFF9CCFA6`，`applyReverseColors` 反转开关在深浅切换后 MUST 仍生效。
MUST NOT 改动红绿语义、MUST NOT 引入新依赖或新权限。

#### Scenario: 系统切到深色

- **GIVEN** 使用者在系统设置切到深色
- **WHEN** 冷启动 App 并逐页查看明细 / 资产 / 统计 / 设置
- **THEN** 页面底为深灰蓝、卡片为略亮深灰蓝、文字为浅色，无残留纯白块
- **AND** 红绿金额仍可辨，配色反转开关状态不丢失

#### Scenario: 动态浅色下卡片边界

- **GIVEN** API 31+ 设备启用系统动态色且壁纸色调明显
- **WHEN** 打开统计页
- **THEN** 页面底仍是中性微灰、卡片仍是纯白，二者边界清晰可辨
- **AND** 强调色随壁纸变化，中性底不随壁纸漂

**理由**：第 78 批把卡片容器改成读 `scheme.surface` 后，与 `Scaffold` 的
`scheme.background` 在动态浅色下同色，真机反馈「分界不明显」。第 79 批拆开
「页面底 / 卡片面 / 次级底」三种语义后恢复，理由写入此处以免再次合并。

---

### Requirement: 金额文本统一等宽数字与四级排版

所有面向使用者的金额文本 SHALL 经 `LedgerType` 的金额样式渲染，
MUST 开启 `fontFeatureSettings = "tnum"` 等宽数字特性：
`AmountDisplay`（32sp Bold）SHALL 用于大额总览（统计页总支出、交易详情主金额），
`AmountRow`（16sp SemiBold）SHALL 用于单笔金额（明细行、收入 / 结余行），
`Title`（15sp Medium）与 `Caption`（12sp Normal）SHALL 分别为主标题与次要备注层级。
金额列表 SHALL 保持右对齐，MUST NOT 因 `+` / `-` / 小数点 / 千分位字形宽度跳动。
MUST NOT 逐个 `TextStyle` 散写字号，MUST NOT 改动既有固定列宽与网格结构。

#### Scenario: 明细列表金额对齐

- **GIVEN** 明细页连续多笔金额位数与正负号不同
- **WHEN** 上下滚动查看金额列
- **THEN** 各金额右对齐、小数点与末位对齐，不随字形宽度左右跳动

#### Scenario: 字体不支持 tnum 时

- **GIVEN** 设备字体未提供 OpenType `tnum` 特性
- **WHEN** 渲染金额文本
- **THEN** 页面不崩溃、金额完整可读，该项 MUST 列入真机待验而非写成「必然生效」

**理由**：金额对齐是记账 App 的高频可读性需求；`tnum` 依赖字体特性，
故只保证样式出口统一，不承诺所有 OEM 字体必然生效。

---

### Requirement: 视觉设计 token 与表面色语义分离

系统 SHALL 提供 `ui/theme/LedgerDesignTokens.kt` 作为视觉常量单一真相源：
`LedgerShapes.Card`（16.dp 圆角）、`LedgerShapes.Icon`（12.dp）、
`LedgerSpacing.CardGap`（16.dp）、`LedgerSpacing.CardPadding`（16.dp）、
`LedgerType`（金额四级排版）。
表面色 SHALL 拆成三个独立语义出口，MUST NOT 再用一个 `Surface` 包打天下：
`Background`（页面 / 全屏底）、`CardSurface`（卡片 / 顶栏 / 弹层，浅色纯白 /
深色 `0xFF1E2025`，刻意固定不随动态色漂）、`OnSurface`（卡片上的文字图标）。
顶栏与资产页身份区 SHALL 用中性卡片承载，MUST NOT 使用整块高饱和蓝底；
主色 SHALL 只作小面积点缀（分组小竖条、进度条填充、FAB、选中态 12% 透明底、
切换图标、选中文字），MUST NOT 因中性化把主色整体删除。
统计页卡片 SHALL 走统一 `StatsCard` 出口，`CardGap` SHALL 为 16.dp，
MUST NOT 为降噪把间距收到 12dp 以下导致边界糊。

#### Scenario: 顶栏风格一致

- **GIVEN** 依次打开资产页、新建账户页、全部账户排序页、分类管理 / 选择 / 排序页
- **WHEN** 查看各页顶部区域
- **THEN** 均为中性卡片 + `OnSurface` 文字，无突兀整块蓝底
- **AND** 主色仍出现在分组小竖条 / 进度条 / FAB 等小面积位置

#### Scenario: 卡片间距回归

- **GIVEN** 统计页有小结卡、趋势卡、分类占比卡等多张卡
- **WHEN** 滚动浏览
- **THEN** 卡片之间 16dp 间隙，边界清晰，MUST NOT 回到 12dp 的糊边界

**理由**：第 78 批把固定 `Color.White` 换成 `scheme.surface`、把间距收到 12dp，
两条真机反馈（分界糊、蓝底突兀）都由「语义合并 + 数值收紧」引入。
拆开语义与回到已验证数值，比继续调色更可靠。

---

### Requirement: 记一笔低频项由键盘上方芯片直达

「记一笔」**支出 / 收入态** SHALL 在数字键盘上方提供**固定单行 5 枚非等宽 chips**：
账户 / 备注 / 优惠 / 标签 / 模板，顺序 SHALL 为
**账户 → 备注 → 优惠 → 标签 → 模板**，权重 SHALL 依次为
**2.2 / 1.7 / 1.3 / 0.9 / 0.9**；**转账态 SHALL 为 4 枚**：
备注 / 手续费 / 标签 / 模板，依次 **2.0 / 1.6 / 0.9 / 0.9**，
MUST NOT 出现「转出/转入」账户 chip（账户已常驻首屏两张卡）。
MUST NOT 再用 `weight(1f)` 等分。

每枚 SHALL 用 `heightIn(min = 40.dp)` 容器、`RoundedCornerShape(12.dp)`
与 `labelMedium` 字号。**模板 chip SHALL 固定显示「模板」，
选中模板后 MUST NOT 跟着改成模板名**；备注固定「备注」、
优惠固定「优惠」/「手续费」；**标签 SHALL 只显示「标签」或有值时的 `#N`**；
账户 SHALL 用最大权重显示完整账户名。
chip 有值（备注 / 不计入收支 / 优惠 / 标签）或应用模板后 SHALL 以
**右上角 6dp 小圆点**提示，MUST NOT 把摘要拼进 label 造成 ellipsis 截断；
完整值 SHALL 在各自的底部 sheet 内可见并修改，MUST NOT 让旧值不可修改。
「不计入收支」开关 SHALL 并入备注 sheet，MUST NOT 为它新增第 6 枚 chip。
每枚点一下 SHALL 直接打开对应底部 sheet，MUST NOT 经过「更多」这一层。
chips 条 SHALL 位于滚动表单区之外、与键盘同属固定底部区，MUST NOT 挤压首屏。
各 sheet 关闭后 SHALL 保留金额、分类、账户、收款方、描述与保存按钮的全部状态。

#### Scenario: 窄屏不再截字

- **GIVEN** 360dp 宽设备、账户名「工商银行」、备注「同事聚餐 AA」且不计入收支、两个标签
- **WHEN** 打开记一笔且不做任何滚动
- **THEN** 账户 chip 显示完整账户名，模板 chip 仍显示「模板」，标签 chip 显示「#2」
- **AND** 备注 / 优惠 / 标签 chip 右上角有小圆点，点开对应 sheet 可见完整旧值

#### Scenario: 选中模板不改名

- **GIVEN** 用户建了「午餐」模板
- **WHEN** 点「模板」chip 并选中「午餐」后回到首屏
- **THEN** chip 文字仍是「模板」，MUST NOT 变成「午餐」
- **AND** 表单已按模板预填

#### Scenario: 转账态四枚

- **GIVEN** 类型切到转账
- **WHEN** 查看键盘上方
- **THEN** 恰好 4 枚 chips（备注 / 手续费 / 标签 / 模板），窄项收在右端
- **AND** 首屏两张账户卡可见，底部仍是明细 / 资产 / 统计 / 设置 4 个 Tab

**理由**：第 80 批把编辑态摘要塞进 chip label，而 5 枚等分每枚约 64dp，
真机只能看到「备注」，账户名也只剩前半。chip 是入口不是展示位；
按信息量分配宽度、窄项收尾，比两行平铺（+40dp 首屏风险）更贴合低摩擦。

---

### Requirement: 收款方联想条与输入框同屏

系统 SHALL 在收款方输入框与其联想条之间保持**零中间层**：
联想条 SHALL 由输入框的聚焦状态与内容驱动，MUST NOT 由 chips 触发、
MUST NOT 由「更多」面板承载、MUST NOT 缓存成常驻列表。
`RecordEditorViewModel` 的 `counterparties` / `counterpartySuggestions`
两条数据链 SHALL 保持不变，MUST NOT 新建查询或引入网络请求。

#### Scenario: 零新数据路径

- **GIVEN** 本批落地后
- **WHEN** 核对 `RecordEditorViewModel`
- **THEN** 仍只有 `distinctCounterparties()` 与既有模糊联想，无新增 DAO 方法

**理由**：联想只在输入时有用，必须与输入框同屏；塞进 chip 弹层会多一次点击
且看不到输入上下文。

---

### Requirement: 全局主题支持动态色且可一行回退

`LedgerTheme` SHALL 在 API 31+ 借用系统动态色，API ≤ 30 SHALL 回退静态浅色板，
MUST NOT 在低版本崩溃。`LedgerTheme` SHALL 暴露 `dynamicColor: Boolean = true`
参数，传 `false` SHALL 完整回到第 70 批静态板，MUST NOT 需要改第二处。
**第 78 / 79 批归档补述**：动态浅色 SHALL 由 `dynamicWithNeutralSurface()`
只借 primary / secondary / tertiary 等强调色，中性槽位（background /
surface / surfaceVariant / surfaceContainer* / outlineVariant）MUST 套回静态值，
MUST NOT 让页面底与卡片容器同色；「两套蓝」已由可观察 `LedgerColors` 收敛，
MUST NOT 再登记为「允许出现两套蓝」。`LedgerColors.ExpenseRed / IncomeGreen`
与配色反转开关 MUST NOT 受动态色影响。深浅模式以归档的
「全局主题支持深浅模式且语义色全程可读」为准。

#### Scenario: 低版本回退

- **GIVEN** 设备为 Android 10（API 29）
- **WHEN** 冷启动 App
- **THEN** 使用静态板正常渲染，无动态色调用

#### Scenario: 一行回退

- **GIVEN** 动态色下观感不接受
- **WHEN** MainActivity 把 `LedgerTheme(dynamicColor = false)`
- **THEN** 全 App 回到浅灰底 / 纯白卡 / 清新蓝，红绿语义与反转开关不变

#### Scenario: 中性槽位不随壁纸漂

- **GIVEN** 动态色启用且壁纸色调明显
- **WHEN** 打开统计页
- **THEN** 页面底仍是中性微灰、卡片仍是纯白，边界清晰
- **AND** 强调色随壁纸变化，中性底不变

---

### Requirement: 全 App 采用 edge-to-edge 且可点区避开系统栏

`MainActivity` SHALL 调 `WindowCompat.setDecorFitsSystemWindows(window, false)`
并把系统栏设透明；背景 SHALL 铺满屏幕，根内容 SHALL 用
`Modifier.safeDrawingPadding()` 避开系统栏与手势区。
`LedgerApp` 的 `Scaffold` SHALL 设 `contentWindowInsets = WindowInsets(0,0,0,0)`，
MUST NOT 与外层 safeDrawing 叠加双重 padding。
记一笔的独立 `Dialog` Window SHALL 单独对根 Column 应用
`statusBarsPadding()` 与 `navigationBarsPadding()`，
MUST NOT 让「记一笔」标题或底部保存按钮被系统栏遮挡。
键盘上方 chips 与各底部 sheet SHALL 同样避让手势区。

#### Scenario: 标题与 chips 不被遮挡

- **GIVEN** 全面屏手势导航设备
- **WHEN** 打开记一笔并点开备注 sheet
- **THEN** 标题完整可见、chips 与 sheet 的「完成」按钮在手势条上方完整可点

#### Scenario: 主页面不双重留白

- **GIVEN** 进入明细 / 统计 / 资产任一 Tab
- **WHEN** 查看顶部与底部留白
- **THEN** 内容只避让一次系统栏，MUST NOT 出现两倍 padding 的空白带

---

### Requirement: 转账态转出与转入账户常驻首屏

「记一笔」转账态 SHALL 在日期副行之后、描述行之前提供**两张中性卡片**，
分别承载「转出账户」与「转入账户」，MUST NOT 折叠进 chips 或底部 sheet。
每张卡片 SHALL 显示目标标签、当前账户名（含卡号后四位）、余额与币种，
以及 `›` 指示器；未选择时 SHALL 显示「选择账户」，
原账户已删除时 SHALL 显示「原账户已删除，请重新指定」，MUST NOT 静默留空。
卡片 SHALL 用 `RoundedCornerShape(16.dp)` 与 `LedgerColors.CardSurface`，
MUST NOT 使用阴影、描边或卡片堆叠表达边界（§7.41 借观感不借堆叠）。
左图标容器 SHALL 为 40dp 圆角矩形 + 主色 12% 透明底，
MUST NOT 为此新增 `material-icons-extended` 等 Gradle 依赖。
点某张卡 SHALL 打开 `AccountPickerSheet` 并把 target 锁定为该卡，
sheet 标题 SHALL 相应为「选择转出账户」/「选择转入账户」，
**MUST NOT 在 sheet 内再显示转出 / 转入分段**（避免双入口）。
系统 MUST NOT 提供转出 / 转入 ⇅ 互换手势（使用者明确否决）。
选择后 SHALL 只回填对应一侧账户，另一侧 MUST NOT 被改动；
沿用既有 `setFromAccount` / `setToAccount`，MUST NOT 新建数据路径。

#### Scenario: 一眼看到两张卡

- **GIVEN** 用户切到转账、尚未选择任何账户
- **WHEN** 打开记一笔且不滚动、不点任何 chip
- **THEN** 首屏出现「转出账户」「转入账户」两张卡，均显示「选择账户」
- **AND** 卡片与微灰底边界清晰，MUST NOT 有阴影或描边

#### Scenario: 点卡锁定目标且只改一侧

- **GIVEN** 已选转出为「中国银行」、转入为「支付宝」
- **WHEN** 点「转入账户」卡并在 sheet 里选「花呗」
- **THEN** sheet 标题为「选择转入账户」、无分段，转入回填为「花呗」
- **AND** 转出仍为「中国银行」，金额、日期、描述、保存按钮状态不变

#### Scenario: 不引入互换手势

- **GIVEN** 两卡都已选好账户
- **WHEN** 查看两卡之间区域
- **THEN** 不存在 ⇅ 互换按钮或长按互换手势
- **AND** 改任一侧只能点对应卡片

#### Scenario: 零新依赖与零改表

- **GIVEN** 本批落地后
- **WHEN** 核对 Gradle 依赖与 `AppDatabase.version`
- **THEN** 依赖清单与 v0.2.59 逐项一致、数据库版本仍为 8

**理由**：支出/收入态靠默认账户兜底，账户是低频；转账态转出与转入都是
必填主字段，没有默认值可猜。把主字段折进 chip 是第 80 批的外推错误，
真机反馈后按一羽「两张大白卡」的观感还原，但不借卡片堆叠、不新增互换手势。

---

### Requirement: 账户选择面板展示品牌图标与卡种

账户选择 SHALL 复用 `ui/components/AccountSelectDialog` **唯一通用出口**，
记一笔账户 chip、转账两张账户卡、模板与周期规则表单的转出/转入行、
明细筛选（含统计账户排行下钻）SHALL 全部调用它，
MUST NOT 再存在第二份账户下拉或第二套账户网格。
第 84 批：该出口 SHALL 以 `ModalBottomSheet` 呈现，
顶部 SHALL 只有「标题 + 一枚布局轮换图标 + 当前：xxx」一行，
MUST NOT 再出现叉号关闭按钮、搜索框或并排分段 chip；
关闭由 sheet 自带的点遮罩 / 下滑 / 系统返回三种路径承担。
每格 SHALL 复用项目既有 `AccountIconBox`（预制品牌 / 自定义 / 中性首字回退），
图标尺寸 SHALL 为 **44dp**；卡种角标 SHALL 按图标尺寸等比放大
（`size * 0.4`，约 17dp，字号 `size * 0.5`），MUST NOT 再用
12dp / 7sp 的「储 / 信」小圆。
面板与转账两卡 MUST NOT 显示账户余额；卡号后四位与卡种 SHALL 降为次要小字
（形如 `尾号 1122 · 储蓄卡 · CNY`），由全站唯一组合点
`ui.components.accountSecondaryLabel` 生成。
**第 84 批**：账户搜索框整体下线，同名卡 SHALL 仅由该次要行区分，
`cardLast4` MUST NOT 被连带删除。
`allowNone` 的「不指定账户 / 全部账户」SHALL 与真实账户同款 tile
收进**网格第一格**（灰圆「—」+ 标签），MUST NOT 再单独占一条整行横幅；
分组视图里它 SHALL 排在所有 `AccountType` 组标题之前。
归档项由调用方决定是否过滤，组件不过滤。
MUST NOT 为对齐截图新增 Gradle 依赖或图标资源
（轮换图标用既有 `Icons.Filled.List` + Compose `Canvas` 自绘，零新增 drawable）。

#### Scenario: 一眼分储信

- **GIVEN** 用户有「工商银行」储蓄卡与「招商银行」信用卡
- **WHEN** 打开任一账户选择器
- **THEN** 每格有 44dp 品牌图标与放大角标，次要行显示「尾号 xxxx · 储蓄卡 / 信用卡」
- **AND** 不出现余额数字、搜索框与叉号，MUST NOT 只剩纯文本 + 12dp 小角标

#### Scenario: 不指定收进网格第一格

- **GIVEN** 模板表单或明细筛选（`allowNone = true`）
- **WHEN** 打开账户选择器
- **THEN** 网格第一格是「不指定账户 / 全部账户」灰圆 tile，与真实账户等高同宽
- **AND** MUST NOT 再出现一条独立的整行横幅

#### Scenario: 自定义图标也走同一位置

- **GIVEN** 某账户在编辑页导入了自定义 PNG
- **WHEN** 打开账户选择器 / 资产页 / 排序页 / 模板预览 / 明细筛选
- **THEN** 各处都显示该自定义图标，尺寸 / 角标与预制一致
- **AND** 导入失败或文件缺失时回退中性圆 + 中文首字，MUST NOT 显示空白

#### Scenario: 记一笔转账仍锁定目标

- **GIVEN** 转账态点「转出账户」卡
- **WHEN** 选择一张卡
- **THEN** 只写回 `fromAccountId`，`toAccountId` 不变，弹层关闭
- **AND** 弹层内 MUST NOT 再出现「转出账户 / 转入账户」分段

## ADDED Requirements

---

---

### Requirement: 账户可关联并导入本机图标

`AccountEntity` SHALL 提供 `iconRef: String?`（`accounts` 表 version 9），
取值 SHALL 为 `null` / `auto`（按账户名反查 `AccountTemplates` 自动推断）、
`preset:<iconKey>`（显式关联预制品牌，改名 MUST NOT 丢）、
`custom:<uuid>`（本机导入）。编辑账户 SHALL 随「保存」一次写回该列，
MUST NOT 分两次事务导致图标与元信息不一致。
账户编辑页 SHALL 提供「预置（26 项网格）/ 导入 PNG / 自动」三入口；
预置网格 SHALL 用编译期 `AccountIconCatalog.drawableFor` 常量表，
MUST NOT 用运行时字符串反射（T14）。

导入 SHALL 复用既有 SAF `ActivityResultContracts.OpenDocument()` +
`contentResolver.openInputStream`，只收**透明底 PNG**；
无透明区域的图片 SHALL 被拒绝并给出可读提示，MUST NOT 做自动抠图。
生成物 SHALL 与第 64 批 26 个预置账户 PNG 同构：
128×128、白圆底、图形 56% 内缩、保留 alpha 的统一单色剪影，
写进应用私有 `filesDir/account-icons/`，**不进备份、不随 APK 分发**。
导入图标与预制图标 SHALL 共用同一 `AccountIconBox` 尺寸 / 容器 / 角标。
**分类自定义图标入口本批 MUST NOT 提供**；`CategoryIconCatalog`、
90 个 Tabler drawable 与 `default_categories.json` SHALL 零改动
（使用者第 3 项：后续需要时直接发新版追加 Tabler 预置资源）。

`Migrations.ALL` SHALL 保持空数组（使用者明确测试数据、卸载重装）；
备份 DTO SHALL NOT 扩该字段，导出 / 导入后 SHALL 回退自动图标，
CSV / Firefly 导入新建账户 SHALL 用默认 `null`。

#### Scenario: 改名不丢图标

- **GIVEN** 账户「工商银行」在编辑页显式关联 `preset:icbc`
- **WHEN** 把名称改成「我的工资卡」并保存
- **THEN** 图标仍是中国工商银行徽记，MUST NOT 回退成中文首字

#### Scenario: 导入与预制并排

- **GIVEN** 用户导入一张透明底 PNG 给某账户
- **WHEN** 在账户 sheet 与预置品牌账户并排查看
- **THEN** 两者同为 128×128 白圆底、同尺寸、同角标，观感一致
- **AND** 单色剪影 MUST NOT 被写成「与 Tabler 线性笔画同款」（如实登记差异）

#### Scenario: 非透明图被明确拒绝

- **GIVEN** 用户选了一张无透明区域的普通照片
- **WHEN** 确认导入
- **THEN** 显示「请使用透明底 PNG，App 不做自动抠图」，图标保持原值

#### Scenario: 不进备份

- **GIVEN** 用户导出备份后在另一设备恢复
- **WHEN** 查看该账户图标
- **THEN** 回退按账户名自动推断，MUST NOT 因私有目录文件缺失而崩溃或空白

#### Scenario: 升表卸载重装

- **GIVEN** 设备已装 v0.2.60
- **WHEN** 直接覆盖安装 v0.2.61
- **THEN** 开库失败（version 9 且无 Migration），属已知口径
- **AND** 交付说明 MUST 写明「卸载重装，装之前先导出备份」

---

### Requirement: 账户选择器提供全局与分组两种布局

`AccountSelectDialog` 标题栏右侧 SHALL 提供**且仅提供一枚**图标按钮，
点一下 SHALL 在两种布局间轮换（`AccountPickerLayout.GLOBAL` ↔ `GROUPED`），
MUST NOT 再出现两枚并排的分段 chip、MUST NOT 弹二级菜单、
MUST NOT 引入第三种布局、MUST NOT 改任何落库排序字段。
图标 SHALL 显示**当前模式**且两种模式用不同图形区分：
`GLOBAL` 用既有 `Icons.Filled.List`（三横线），
`GROUPED` 用 `Canvas` 自绘 2×2 圆角方块；
`contentDescription` SHALL 写明「当前 X，点击切换到 Y」供 TalkBack 使用。
默认 SHALL 为 `GLOBAL`。两种布局 SHALL 共用同一份账户数据。

- `GLOBAL` SHALL 按 `quickOrder` 全局排序、不分 `AccountType`、归档沉底，
  与「全部账户排序」独立页及记一笔既有候选顺序完全一致。
- `GROUPED` SHALL 按 `AccountType` 固定组序
  `ASSET → LIABILITY → PREPAID → INVESTMENT` 分组，
  **组内 SHALL 按 `sortOrder` 继承资产页分组排序**，组间标题复用
  `AccountType.displayName()`；MUST NOT 把 `quickOrder` 混进分组视图。
- 切换后同一账户在两种视图中位置不同 SHALL 视为**预期行为**，MUST NOT 被
  「统一排序」修掉（活文档 §1753 两套排序分离的既有铁律）。

布局选择 SHALL 跨启动持久化于 `LedgerPrefs.accountPickerLayout`
（`"global"` / `"grouped"`，未设置或非法值回退 `"global"`），
由 `rememberAccountPickerLayout()` 统一读写；SHALL NOT 进 Room、
SHALL NOT 进备份 DTO、SHALL NOT 触发 Migration，
`AppDatabase.version` 仍 SHALL 为 9、`Migrations.ALL` 仍为空数组。
**第 84 批修订**：原文「MUST NOT 引入 `ModalBottomSheet`（T12）」作废——
该禁令只是记一笔低频项 sheet 的局部惯例，统计页 `StatsDrillSheet` 自第 69 批
起已用 `ModalBottomSheet` 并写进 `category-stats` 主 spec；
本选择器按使用者 1B 拍板改用 `ModalBottomSheet`，
MUST NOT 再自己补遮罩 `clickable`（会与 sheet 自带 scrim 抢事件）。
长按拖拽（§3.1）与新增依赖仍 MUST NOT。

#### Scenario: 两种顺序各自正确

- **GIVEN** 在「全部账户排序」页把信用卡 A 排到第一、资产页把储蓄卡 B 排到资产组第一
- **WHEN** 打开账户选择器看全局视图，再点右上角图标切到分组视图
- **THEN** 全局视图第一项是 A；分组视图「资产账户」组第一项是 B
- **AND** 再点一次图标切回全局仍是 A，`sortOrder` 与 `quickOrder` 两列均零变化

#### Scenario: 跨启动记住

- **GIVEN** 点图标切到分组视图并关闭弹层
- **WHEN** 杀掉进程重启 App、再打开任一账户选择器
- **THEN** 仍是分组视图，右上角图标是 2×2 方块
- **AND** 换设备 / 清数据后回退默认全局视图与三横线图标

#### Scenario: 图标轮换与状态自解释

- **GIVEN** 当前为全局视图
- **WHEN** 查看标题栏右侧
- **THEN** 只有一枚三横线图标，无文字分段、无叉号、无搜索框
- **WHEN** 点一下
- **THEN** 立刻变为带 `AccountType` 组标题的分组视图，图标变为 2×2 方块


---

### Requirement: 模板与周期规则复用通用分类与账户选择器

模板与周期规则共用的 `QuickEntryForm` SHALL 以整行 `›` 入口
（标签列 + 值 + `›`）打开通用选择器：分类走 `CategorySelectDialog`，
转出 / 转入账户走 `AccountSelectDialog`。
`DropdownSelector` SHALL 从全仓下线，MUST NOT 再出现任何下拉式分类/账户选择。
账户选择 SHALL 支持「不指定账户」（`allowNone`），
选中后 SHALL 能把已有账户改回 `null`，MUST NOT 迫使使用者重建模板或周期规则；
`QuickEntryForm` SHALL 接收 `List<AccountEntity>` 而非 `List<Pair<Long,String>>`，
以便通用选择器显示图标、卡种与卡号尾号。
保存时的分类类型校验、周期规则账户必填校验、转账两账户不同校验 SHALL 零改动。
统计页无独立账户筛选入口，账户排行下钻 SHALL 继续经
`DetailViewModel.filterByAccount` 落到明细 `FilterSheet`，
该面板的账户条件 SHALL 同样走 `AccountSelectDialog`
（`allowNone = true`，`noneLabel = "全部账户"`，归档项不进选择器）。
**第 84 批修订**：四处入口打开的账户选择器 SHALL 是同一份
`ModalBottomSheet`（单枚图标轮换、无叉号 / 无搜索 / 不指定入网格），
MUST NOT 出现「双布局分段」字样或第二套壳。

#### Scenario: 周期规则改回不指定账户

- **GIVEN** 编辑一条周期规则，转出账户原为「工商银行 (1122)」
- **WHEN** 点转出账户行 → 点网格第一格「不指定账户」→ 保存
- **THEN** 保存被既有必填校验拦下并给出「请选择转出账户」
- **AND** 表单上值确实变成「不指定」，MUST NOT 还卡着旧账户无法清空

#### Scenario: 模板账户可留空

- **GIVEN** 新建一个模板（草稿语义，账户可留空）
- **WHEN** 点网格第一格「不指定账户」并保存
- **THEN** 保存成功，`fromAccountId = null`，生成流水时由默认账户兜底

#### Scenario: 统计下钻与明细同一面板

- **GIVEN** 从统计页账户排行点某账户下钻到明细
- **WHEN** 打开筛选面板的「账户」行
- **THEN** 出现与记一笔同款的通用账户选择器（单枚图标轮换的 `ModalBottomSheet`）
- **AND** 点网格第一格「全部账户」清空该筛选条件

## ADDED Requirements


---

---

### Requirement: 账户选择器采用原生底部弹层与单枚图标轮换

`AccountSelectDialog` SHALL 以
`ModalBottomSheet`（`@OptIn(ExperimentalMaterial3Api::class)`）为壳，
`containerColor = LedgerColors.CardSurface`，保留默认 drag handle。

**第 85 批修订（真机回归，修 bug）**：SHALL 传
`sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`。
默认三态 `Hidden / PartiallyExpanded / Expanded` 会让下滑与系统返回
**只退回半屏而不关闭**，并与内部 `LazyColumn` 的嵌套滚动互相拉扯，
真机表现为「上滑展开后下滑收不起来、一上一下卡住」。
去掉中间档后：打开即全展开，列表到顶后下滑直接 dismiss，
**系统返回手势 SHALL 直接关闭弹层，MUST NOT 回到半屏态**。
MUST NOT 手写 `NestedScrollConnection`（§7.7 手势坑）、
MUST NOT 回退默认三态、MUST NOT 给 `LazyColumn` 设
`userScrollEnabled = false`（会把账户多时的滚动功能改没了）。
第 84 批 design 决策 1「不传自定义 sheetState」的结论据此作废，
归档 delta 保持原样作历史快照，本条为当前生效语义。

关闭 SHALL 由组件自带的三条路径承担，MUST NOT 再自定义叉号按钮或遮罩点击层：

- 点遮罩（scrim）
- 下滑弹层（swipe to dismiss）——使用者第 84 批 1B 明确要这个手势
- 系统返回键 / 侧滑返回手势
- 点任一账户或「不指定」tile 后 SHALL 立即写回并关闭

顶部 SHALL 只有一行：标题（`titleMedium`）+
一枚 24dp 布局轮换图标按钮 + 下一行「当前：xxx」（`labelSmall`，
使用者第 2 项明确保留）。MUST NOT 出现
`Icons.Filled.Close` 叉号、`OutlinedTextField` 搜索框、
`FilterChip` 分段或任何独立整行的「不指定」横幅。

内容 SHALL 用 `LazyColumn` + `chunked(2)` 的 `Row(weight(1f))` 两列网格，
组标题以 `item {}` 插入；MUST NOT 用 `LazyVerticalGrid`（T12 / §7.3）、
MUST NOT 用 `Column + verticalScroll`（与 sheet 下滑手势抢滚动），
MUST NOT 新做长按拖拽（§3.1，排序仍走「全部账户排序」独立页）。
`ModalBottomSheet` 与 `Dialog` 嵌套 SHALL 沿用项目既有先例
（`FilterSheet` 内已嵌 `CategorySelectDialog`），MUST NOT 为此改 4 个调用点的容器。

**零新增依赖 / 零新增权限 / 零新增图标资源**：
`ModalBottomSheet` 来自已在用的 `androidx.compose.material3:material3`
（BOM 2026.06.00），`Icons.Filled.List` 来自已在用的 material-icons-core，
分组图标是 `Canvas` 自绘矩形；MUST NOT 加 `material-icons-extended`、
MUST NOT 加图片加载库、MUST NOT 新增 drawable、MUST NOT 声明 `INTERNET`。
`AppDatabase.version` 仍 SHALL 为 9，本批 SHALL 可直接覆盖 v0.2.62。

#### Scenario: 三种关闭路径都可用

- **GIVEN** 记一笔（或模板 / 周期 / 明细筛选）打开账户选择器
- **WHEN** 分别用系统返回手势、点上方灰色遮罩、向下滑弹层
- **THEN** 三次都能关闭弹层且未选中任何账户
- **WHEN** 再点一张账户
- **THEN** 写回对应 id 并关闭，MUST NOT 出现「关不掉」或双击才关

#### Scenario: 上滑展开后一次下滑即关闭（第 85 批回归）

- **GIVEN** 账户数量多到弹层内容可滚动
- **WHEN** 打开弹层、上滑展开到全屏、再从标题栏或 drag handle 向下滑一次
- **THEN** 弹层直接关闭，MUST NOT 停在半屏态或出现「一上一下」来回弹
- **WHEN** 列表滚到中间时向下滑
- **THEN** 先滚动列表，滚到顶部后手势交给 sheet，继续下滑即关闭

#### Scenario: 系统返回直接关闭而非回半屏（第 85 批回归）

- **GIVEN** 弹层处于全展开状态
- **WHEN** 用系统返回键或侧滑返回手势
- **THEN** 弹层直接关闭并回到调用页，MUST NOT 先退回半屏再要求第二次返回
- **AND** 点遮罩、下滑两条路径行为一致

#### Scenario: 顶部只有一行控件

- **GIVEN** 打开任一账户选择器
- **WHEN** 查看网格上方
- **THEN** 只有标题 + 一枚图标 + 「当前：xxx」灰字
- **AND** MUST NOT 出现「全局排序 / 按类型分组」两枚 chip、搜索框或叉号
- **AND** 「不指定 / 全部账户」在网格第一格而非独立横幅

#### Scenario: 依赖与权限零变化

- **GIVEN** v0.2.62 的依赖清单与 `AndroidManifest.xml`
- **WHEN** 构建 v0.2.63
- **THEN** 两者逐项一致，`app/build.gradle.kts` 无新增 implementation，
  Manifest 无新增 `uses-permission`，`app/src/main/res/drawable/` 数量不变

---

### Requirement: 虚拟账本以标签视图实现且切换不带录入归属

应用 SHALL 提供「虚拟账本」能力：每个虚拟账本对应一条统一前缀标签 `台账:<名称>`
与一个独立只读视图，MUST NOT 引入 `bookId`、账本维度、互斥归属或第二个数据库。
第 93 批修订：切换器 SHALL **只在明细页月份卡标题行占一枚 36dp 图标按钮**，
MUST NOT 在 4 个主 Tab 常驻一整行、MUST NOT 自带状态栏避让容器与说明文字；
资产 / 统计 / 设置三个 Tab 不显示。状态栏避让由 Scaffold 的薄 topBar 统一处理。
切换器 SHALL **只切浏览范围，MUST NOT 携带录入归属**，默认永远回主账本且
MUST NOT 跨启动记忆。记一笔中央 `+` 按钮与自动记账入口 SHALL 永远落主账本，
MUST NOT 感知当前虚拟账本。

#### Scenario: 切换不影响自动记账

- **GIVEN** 用户从切换器进入「结婚礼金」台账页后返回，或停留在任一虚拟账本视图
- **WHEN** 通过系统分享把一笔支付宝消费原文送入自动记账
- **THEN** 该流水仍落在主账本，不带 `台账:` 标签、不勾不计入收支
- **AND** 主账本收支统计如实包含这笔消费

#### Scenario: 零改表可覆盖安装

- **GIVEN** 用户已安装 v0.2.69
- **WHEN** 覆盖安装本批版本
- **THEN** 既有流水、账户、标签与备份格式全部不变
- **AND** Room version 仍为 9，不注册 Migration、不新增 Entity

---

### Requirement: 台账页按人核算往来且录入自动带不计入

台账页 SHALL 按 `counterparty` 分组显示 收 / 还 / 待还 三列与本台账流水列表，
并提供「记收礼」「记还礼」两个入口。台账聚合 SQL MUST NOT 过滤 `excludeFromStats`，
SHALL 分别 SUM `INCOME` 与 `EXPENSE` 方向金额并带 `currencyCode` 条件；
`待还 = 收 − 还`，SHALL NOT 设置「结清」状态。
台账录入 SHALL 自动带入本账本标签与「不计入收支」开关，MUST NOT 预填分类或账户 id。
设置页 SHALL 提供新建 / 重命名 / 归档 / 排序与「台账记录不计入收支」总开关（默认开）。
台账页 SHALL 另按「台账页提供总览、分段与记录维护」提供分段、总览与记录维护能力。

#### Scenario: 礼金不虚高

- **GIVEN** 用户新建虚拟账本「结婚礼金」
- **WHEN** 点「记收礼」填对方张三与 500 元并保存
- **THEN** 流水带 `台账:结婚礼金` 标签且 `excludeFromStats = true`
- **AND** 主账本月度收入、支出与预算合计均不含这笔
- **AND** 台账页张三一行显示 收 500.00 / 还 0.00 / 待还 500.00

#### Scenario: 改名不丢历史

- **GIVEN** 「结婚礼金」下已有若干流水
- **WHEN** 在设置页把视图改名为「婚礼往来」
- **THEN** 绑定标签保持 `台账:结婚礼金` 不变，历史流水仍归入该视图

---

### Requirement: 台账流水在主账本明细可见且带不计入标记

第 93 批修订：撤回第 91 批「默认排除台账记录」。明细分页查询 SHALL 原样返回
带 `台账:%` 标签的流水，MUST NOT 在 SQL 里追加任何排除条件、MUST NOT 改变方法
签名、`RecordFilter` 结构或 Paging 调用链。台账记录与常规流水的区别由
`excludeFromStats` 标记呈现；统计、预算、趋势等报表聚合继续过滤该列，
因此月度收入 SHALL NOT 因台账流水虚高。明细列表 MUST NOT 用
`excludeFromStats` 过滤记录本身。

#### Scenario: 台账流水在主账本可查可改

- **GIVEN** 「结婚礼金」台账下已有一笔收礼流水
- **WHEN** 用户切回主账本明细页（未做任何筛选）
- **THEN** 该流水出现在明细列表中，并显示不计入收支标记
- **AND** 点该行可进入只读详情页并解锁进入编辑，或走台账页行内删除
- **AND** 主账本月度收入合计仍不含这笔

---

### Requirement: 台账预填与标签新建 MUST 在同一次状态赋值内完成

第 92 批修订：`RecordEditorViewModel.load()` 内部是带多个 DAO suspend 的协程，
会整体赋值 `EditorState`。虚拟台账预填（类型 / 标签 / 不计入）SHALL 作为 `load()`
参数传入，并在**同一协程内、构造完 `EditorState` 之后**一次性合并；
MUST NOT 由 Composable 在 `load()` 之外同步调用，否则会被后续整体赋值覆盖，
产出的流水不带 `台账:` 标签（台账视图查不到，主账本明细却会显示）。
预填 type 为 INCOME 时，分类 SHALL 取该 type 下启用且未归档的一级，
MUST NOT 沿用默认 EXPENSE 分类（会被「分类与收支类型不一致」拦截）。

标签选择器新建 SHALL 走 `addPickedTag` 纯函数出口，chip 名单 SHALL 用
`visibleTagNames(已有名, picked)` 合并渲染，使新建项**即时可见且可反选**；
MUST NOT 只渲染数据库已有标签。新建标签 SHALL 通过既有 `bindTags` 按名落库。

#### Scenario: 台账录入产出的流水进入台账视图

- **GIVEN** 用户在「结婚礼金」台账页点「记收礼」
- **WHEN** 填对方张三与 500 元并保存
- **THEN** 流水带 `台账:结婚礼金` 标签且 `excludeFromStats = true`
- **AND** 台账页按人出现张三一行，主账本明细可见这笔且带不计入收支标记
- **AND** 不出现「分类与收支类型不一致」错误

#### Scenario: 新建标签即时可见

- **GIVEN** 记一笔里打开标签选择器，输入一个数据库里没有的名字
- **WHEN** 点新建按钮
- **THEN** 上方 chip 区立刻出现该名字且为选中态，点一下可取消
- **WHEN** 点确定并保存流水
- **THEN** 该标签随流水落库，再次打开选择器时出现在已有标签中

---

### Requirement: 台账页提供总览、分段与记录维护

第 93 批：台账页 SHALL 由五块组成，MUST NOT 把录入按钮与流水混在同一条滚动列表里：
标题行（返回 / 名称）、总览卡、分段 chip、内容区、底部固定录入区。
总览卡 SHALL 显示本台账 总收 / 总还 / 待还 / 笔数，由 `partyTotals` 结果在页内
汇总得出，MUST NOT 给统计模块新增台账 scope 或新开聚合分支。
分段 SHALL 提供「往来汇总」与「流水」两段；流水段按 `localDate` 分组并给出当日
收 / 还小计。底部录入区 SHALL 固定不随列表滚动。

台账流水行 SHALL 可点进全屏 `RecordEditorDialog` 编辑态（不带任何预填），
行尾 SHALL 提供删除入口：二次确认后走既有 `LedgerRepository.deleteRecord`
（移入回收站 + 标签快照 + 余额重算），MUST NOT 新开写余额分支。
删除事务 SHALL 跑在 LifecycleOwner 级 scope，MUST NOT 放进 `LaunchedEffect`
或弹层局部 `rememberCoroutineScope`（第 89 批「coroutine scope left the composition」坑）。

#### Scenario: 记错了能改能删

- **GIVEN** 台账页「流水」段有一笔金额填错的记录
- **WHEN** 点该行
- **THEN** 打开全屏记一笔编辑态，金额 / 对方 / 标签 / 不计入开关均可修改
- **WHEN** 改回台账页点行尾删除并确认
- **THEN** 记录进入回收站、账户余额自动重算、台账总览与按人汇总同步刷新
- **AND** 不出现 `The coroutine scope left the composition`

#### Scenario: 新增与列表分离

- **GIVEN** 台账页已有数十笔流水
- **WHEN** 上下滚动「流水」段
- **THEN**「记收礼 / 记还礼」始终固定在底部，不随列表滚动
- **AND** 切到「往来汇总」只显示按人卡片，不夹杂流水行

