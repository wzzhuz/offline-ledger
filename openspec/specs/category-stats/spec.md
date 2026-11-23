# 多级分类与统计 Specification

## Purpose

定义两级分类模型、统计口径、下钻路径，以及「多级分类不得增加记账摩擦」的约束。

---

## Requirements

### Requirement: 分类最多两级

分类 SHALL 采用 `parentId` 自关联 + `level` 字段，v1 SHALL 限制最多两级（0 / 1）。系统 MUST NOT 允许创建三级分类。

#### Scenario: 尝试建三级

- **GIVEN** 用户在「餐饮 → 食材」下再建子类
- **WHEN** 提交
- **THEN** 系统拒绝并提示最多两级
- **AND** 分类树保持两层

**理由**：三级以上会让 UI 与统计复杂度陡增，对「知道哪类支出比较多」这个目标收益极小。

---

### Requirement: 子级类型继承父级

二级分类 MUST NOT 独立存储 `type`，SHALL 从父级继承。系统 SHALL 校验二级分类的收支类型与父级一致。

#### Scenario: 类型不一致

- **GIVEN** 用户把「食材」设为收入分类
- **WHEN** 提交
- **THEN** 系统拒绝
- **AND** 「食材」仍继承「餐饮」的 EXPENSE

**理由**：避免出现「餐饮是支出、食材却是收入」这类脏数据，减少一处需要人工维护的冗余。

---

### Requirement: 流水冗余一级分类 ID

写入流水时 SHALL 由 `categoryId` 解析并冗余 `rootCategoryId`：一级分类指向自身、二级分类指向父级。流水的 `categoryId` MAY 直接指向一级分类。

#### Scenario: 记二级分类

- **GIVEN** 用户选择「餐饮 → 食材」记一笔
- **WHEN** 保存
- **THEN** `categoryId = 食材 id`
- **AND** `rootCategoryId = 餐饮 id`

#### Scenario: 只选一级

- **GIVEN** 用户直接点「餐饮」记账
- **WHEN** 保存
- **THEN** `categoryId = 餐饮 id`
- **AND** `rootCategoryId = 餐饮 id`

**理由**：冗余一列换来「按一级统计 = GROUP BY rootCategoryId」，无需递归或闭包表，也让保留「只选一级直接记」的快捷路径成为可能。

---

### Requirement: 统计走 SQL 聚合且不递归

按一级统计 SHALL 使用 `GROUP BY rootCategoryId`，按二级统计 SHALL 使用 `GROUP BY categoryId`。系统 SHALL 校验并展示「一级合计 = 二级合计 = 总支出」。环形图 SHALL 支持「按大类 / 按小类」层级切换。

#### Scenario: 合计一致性

- **GIVEN** 账本含多级分类的若干支出
- **WHEN** 生成月报
- **THEN** 一级合计、二级合计、总支出三者相等
- **AND** 不一致时给出提示而非静默显示

**理由**：合计能对得上，是「账要算得准」在统计维度的延伸；也让使用者能信任报表。

---

### Requirement: 三级下钻路径

系统 SHALL 支持「一级扇区 → 该大类下二级构成 → 该分类明细列表」的下钻，明细列表 SHALL 复用组合筛选能力。

#### Scenario: 下钻到明细

- **GIVEN** 用户点击月报中「餐饮」扇区
- **WHEN** 展开
- **THEN** 显示食材 / 正餐 / 外卖等二级构成
- **AND** 再点「食材」进入该分类明细

**理由**：月度「哪类支出比较多」与逐笔核对之间需要一条连续路径，避免看完图还得重新去翻账。

---

### Requirement: 多级分类不得增加记账摩擦

记一笔 SHALL 保留「只选一级直接保存」的快捷路径，并 SHALL 在二级选择中把最近使用 / 常用分类置顶。系统 MUST NOT 强制用户必须选到二级才能记账。

#### Scenario: 两步记完

- **GIVEN** 用户打开记一笔、点常用一级分类、输入金额
- **WHEN** 保存
- **THEN** 流水创建成功且余额联动
- **AND** 全程无需展开二级选择器

**理由**：「时间长了就没动力」是产品生死线。多级分类是统计需求，不该让日常记账多点两下。

---

### Requirement: 分类归档不改历史统计

归档或调整分类树 SHALL NOT 改变已存在流水的 `rootCategoryId` 归属，除非显式执行带迁移策略的批量更新。

#### Scenario: 归档子类

- **GIVEN** 用户归档「零食」分类
- **WHEN** 查看历史月报
- **THEN** 历史「零食」流水仍归入「餐饮」
- **AND** 历史合计不变化

**理由**：历史记录写完即定型；分类树变动让旧账跟着漂移，会让往期报表无法复现。

---

### Requirement: 两级分类落表并校验继承

`categories` SHALL 用 `parentId` + `level` 表达最多两级，
二级分类 SHALL 从父级继承 `type`，系统 MUST NOT 允许 `level >= 2` 的记录。
写入流水时 SHALL 解析并冗余 `rootCategoryId`。

#### Scenario: 二级分类记账

- **GIVEN** 分类「餐饮」(id=1, level=0) →「食材」(id=101, level=1, parentId=1)
- **WHEN** 用户选择「食材」记一笔支出
- **THEN** `records.categoryId = 101`
- **AND** `records.rootCategoryId = 1`

#### Scenario: 只选一级

- **GIVEN** 用户直接点「餐饮」记账
- **WHEN** 保存
- **THEN** `categoryId = 1` 且 `rootCategoryId = 1`
- **AND** 无需展开二级选择器

**理由**：冗余一列换来两条最简单的 `GROUP BY`，同时保留低摩擦记账路径。

---

### Requirement: 一级与二级合计一致

系统 SHALL 提供按 `rootCategoryId`（一级）与按 `categoryId`（二级）的聚合查询，
并 SHALL 在单测中断言一级合计 = 二级合计 = 总支出。

#### Scenario: 合计校验

- **GIVEN** 若干笔分布在「餐饮 → 食材 / 正餐」与「交通 → 打车」的支出
- **WHEN** 分别按一级与二级聚合
- **THEN** 两种口径的合计相等且等于总支出
- **AND** 不一致时测试失败

**理由**：合计对得上是「账要算得准」在统计维度的延伸，也让使用者能信任报表。

---

### Requirement: 预置两级中文分类

系统 SHALL 在全新库创建时预置至少 7 个一级支出分类、3 个一级收入分类
及若干二级分类；第 30 批起**首启为零账户**，账户由资产页快捷模板创建，
MUST NOT 再预置资产账户或信用负债账户。

#### Scenario: 首次启动

- **GIVEN** 全新安装后首次创建数据库
- **WHEN** `SeedCallback.onCreate` 执行
- **THEN** `categories` 表中存在预置数据，`accounts` 表为空
- **AND** 所有二级分类的 `type` 与父级一致

**理由**：首次打开就能记账，是「记账不能变慢」的一部分；避免用户先做一轮配置才上手。

### Requirement: 记账入口解析并冗余 rootCategoryId

记一笔在保存前 SHALL 由 `CategoryTree.resolveRootId` 解析 `rootCategoryId`，
一级分类 SHALL 指向自身，二级 SHALL 指向父级。
系统 SHALL 允许 `categoryId` 直接指向一级分类。

#### Scenario: 一级直记

- **GIVEN** 用户只选一级「交通」
- **WHEN** 保存
- **THEN** `categoryId = 2` 且 `rootCategoryId = 2`

#### Scenario: 二级记账

- **GIVEN** 用户选「交通 → 打车」
- **WHEN** 保存
- **THEN** `categoryId = 202` 且 `rootCategoryId = 2`

**理由**：冗余一列换来两条最简单的 `GROUP BY`，统计阶段不必递归或闭包表。

---

### Requirement: 分类图标不做字符串映射

分类图标 SHALL 用 `colorHex` 绘制彩色圆 + 分类名中文首字呈现，
MUST NOT 把 `iconName` 字符串反射映射到 `ImageVector`。
`iconName` 字段 SHALL 原样保留供将来扩展。

#### Scenario: 新增分类名不含已知图标名

- **GIVEN** 用户自定义一个分类，图标名是任意字符串
- **WHEN** 打开分类网格
- **THEN** 正常显示彩色圆 + 首字
- **AND** 不因图标名不存在而编译失败或运行时崩溃

**理由**：预置数据有 30 个图标名，任一在当前 Compose BOM 的图标集里不存在
就会让整份映射表编译失败；中文首字辨识度反而更高，且零依赖风险。

### Requirement: 环形图支持一级与二级切换并落地三级下钻

统计页 SHALL 提供「按大类 / 按小类」切换，一级口径 SHALL 走
`GROUP BY rootCategoryId`，二级口径 SHALL 走 `GROUP BY categoryId`，
两者 SHALL 按金额降序展示占比与具体金额。
点击一级扇区 SHALL 用 `WHERE rootCategoryId = :rootId GROUP BY categoryId`
展开该大类下二级构成；点击二级项 SHALL 进入该分类明细列表。
系统 MUST NOT 用递归或闭包表实现层级。
统计页 SHALL 另外提供「Top5 分类 / 交易对方 / 支出账户」排行视图，
且 SHALL 作为与占比环形图各自独立的一张卡常驻：
每行 SHALL 显示名称、金额、百分比与占比条，一级项 SHALL 可展开二级，
点击排行项 SHALL 进入该分类明细；排行 MUST 由带唯一统计口径的只读 SQL 聚合得出，
MUST NOT 在 Kotlin 里对已加载分页数据分组。排行卡与占比卡 MUST NOT 互相切换或
合并到同一容器，两者 MUST 共享同一份净支出切片数据，MUST NOT 出现两个不一致的合计。
三个维度（分类 / 交易对方 / 支出账户）SHALL 只在排行卡内部切换，MUST NOT 影响占比卡。

#### Scenario: 一级扇区下钻

- **GIVEN** 月报中「餐饮」为最大支出扇区
- **WHEN** 点击「餐饮」
- **THEN** 展开食材 / 正餐 / 外卖等二级构成及各自金额
- **AND** 金额的合计等于「餐饮」扇区金额

#### Scenario: 二级进入明细

- **GIVEN** 已展开「餐饮」的二级构成
- **WHEN** 点击「食材」
- **THEN** 显示该分类的流水明细列表
- **AND** 明细走参数化 SQL 与 LIMIT，不全量加载

#### Scenario: 切换统计口径

- **GIVEN** 用户切到「按小类」
- **WHEN** 查看占比卡环形图或排行卡
- **THEN** 扇区与排行按 `categoryId` 聚合而非 `rootCategoryId`
- **AND** 合计与一级口径相等

#### Scenario: 排行按当前币种

- **GIVEN** 账本含 CNY 与 HKD 两类支出、当前选中 CNY
- **WHEN** 查看交易对方排行
- **THEN** 只统计 CNY 流水，MUST NOT 跨币种混算

**理由**：第 13 批吸收 BeeCount `AnalyticsRanking` 的「占比条 + 百分比 + 展开二级 +
点进分类详情」，但保留项目原有的环形图与三级下钻。第 14 批按标题整块替换，
删掉「排行与环形图可互相切换」——第 13 批为挤一屏的合并做法已被使用者否掉，
改为两卡各自独立常驻；同时避免 specs 出现「只有环形图」与「有排行」两种表述。

---

### Requirement: 合计一致性在 UI 与单测双保险

系统 SHALL 校验「一级合计 = 二级合计 = 总支出」，
不一致时 SHALL 在统计页显示警告条与差额（单位分），MUST NOT 静默显示图表。
校验逻辑 SHALL 为纯 JVM 函数并 SHALL 有对应单元测试。

#### Scenario: 合计一致

- **GIVEN** 若干笔分布在「餐饮 → 食材 / 正餐」与「交通 → 打车」的支出
- **WHEN** 生成月报
- **THEN** 三个口径合计相等
- **AND** 不显示警告条

#### Scenario: 合计不一致

- **GIVEN** 一级合计与总支出相差 100 分
- **WHEN** 生成月报
- **THEN** 显示红色警告条与「差额 100 分」
- **AND** 单测中该构造使断言失败

**理由**：合计能对得上是「账要算得准」在统计维度的延伸；
统计口径写错不会崩溃、只会算错数，必须靠 UI 提示 + 单测两条线拦住。

---

### Requirement: 下钻明细复用组合筛选且不全量加载
统计页点击分类查看流水时，系统 SHALL 跳转到明细 Tab 并带入 `categoryId`（或
`rootCategoryId`）、当前 `yearMonth`、当前币种与**只读展示名**，复用既有
`applyFilter` / `PagingData` / 官方下拉刷新，MUST NOT 在弹窗内维护第二套分页列表；
明细页 MUST 显示带具体分类名、可一键清除的筛选 chip，MUST NOT 用维度名兜底；
筛选状态 MUST 保持 Activity 作用域，切 Tab 返回不丢。
组合筛选面板 SHALL 以一行「分类」显示已选的具体名称并提供带搜索的全量单选
弹层（一级 + 「一级 / 二级」），使已设的每一项可见、可改、可清；二级分类下钻
SHALL 同时带入父级 `rootCategoryId`。
分类占比卡的扇区与**图例** SHALL 都能触发同一份下钻；排行卡的三个维度
（分类 / 收款方 / 账户）SHALL 都能跳明细，收款方按包含匹配、账户按 id；
排行卡一级分类 SHALL 可内联展开二级（`rememberSaveable` 保存展开态），点二级
SHALL 直接跳明细，MUST NOT 回到弹窗内长列表。
一级到二级构成的**选择**弹窗可保留，但点二级 MUST 跳明细而非在弹窗里列流水。

#### Scenario: 点二级分类跳明细
- **WHEN** 使用者在占比卡「按小类」视图点某个扇区或对应图例
- **THEN** 切到明细 Tab，列表按该二级分类 + 当前币种筛选，顶部出现显示具体名称的可清除筛选 chip

#### Scenario: 筛选面板还原分类条件
- **WHEN** 下钻后打开组合筛选面板
- **THEN** 「分类」行显示具体名称（一级或「一级 / 二级」），可改可清，已设项计数与 chip 一致

#### Scenario: 排行三维下钻
- **WHEN** 排行卡切到收款方 / 账户并点某一行
- **THEN** 跳到明细 Tab 且分别按包含匹配 / 账户 id 过滤，chip 标明「包含」或显示账户名

#### Scenario: 排行二级展开
- **WHEN** 排行卡点一级行的 ⌄ 展开、再点某个二级
- **THEN** 二级列表内联展示且展开态切 Tab 返回后仍在，点二级跳明细带二级 id

#### Scenario: 清除筛选
- **WHEN** 使用者点明细页的筛选 chip
- **THEN** `clearFilter()` 被调用，列表回到未筛选状态，统计页返回时条件不残留

### Requirement: 分类管理复用预留字段且禁止物理删除

设置页 SHALL 提供两级分类管理，支持新增一级 / 二级、改名、排序与归档。
系统 MUST NOT 物理删除任何分类（尤其已有流水的分类），
归档 SHALL 只置 `isArchived` 且不改任何历史流水的 `rootCategoryId`。
新增分类 SHALL 校验 `level ∈ {0,1}`、二级的 `parentId` 非空且父级为一级，
子级 SHALL 从父级继承 `type`。

#### Scenario: 归档一个已有流水的子类

- **GIVEN** 「零食」下有 200 笔历史流水
- **WHEN** 在分类管理里归档「零食」
- **THEN** `isArchived = 1`，分类仍存在于库中
- **AND** 历史月报「零食」仍归入「餐饮」，合计不变

#### Scenario: 拒绝三级分类

- **GIVEN** 用户在「食材」下再建子类
- **WHEN** 提交
- **THEN** 拒绝并提示最多两级
- **AND** 分类树保持两层

#### Scenario: 类型继承校验

- **GIVEN** 用户在支出大类「餐饮」下建一个收入子类
- **WHEN** 提交
- **THEN** 拒绝并提示类型须与父级一致

**理由**：`CategoryEntity` 在阶段 1 就预留了 `isArchived` / `sortOrder`，
本阶段直接启用，零 Migration；物理删除有流水的分类会产生孤儿流水，
是 `project.md` 永久不做项。

---

### Requirement: 新增分类写方法全部走事务并登记清单

阶段 5 新增的 `createCategory` / `renameCategory` / `archiveCategory` /
`reorderCategories` SHALL 全部包在 `db.withTransaction` 内，
SHALL 登记进 `check_transaction_annotations.py` 的 `WRITE_METHODS`（13 → 17）。
排序变更 SHALL 在事务末尾调用 `rebuildAllBalancesInternal()` 兜底。

#### Scenario: 忘记登记

- **GIVEN** 有人新增一个分类写方法却没扩 `WRITE_METHODS`
- **WHEN** CI 执行静态检查
- **THEN** 评审或检查发现清单与方法数不一致
- **AND** 不产出一个「新方法绕开事务」的版本

**理由**：硬编码清单最典型的失效方式是「加了新方法忘了登记」；
阶段 3 已为此扩过一次清单，本阶段沿用同一规则。

### Requirement: 分类支出占比按净支出计算

系统 SHALL 在环形图、一级 / 二级下钻与合计一致性校验中使用
**净支出 = 毛支出 − SETTLEMENT 合计**，按分类 id 对齐相减。
既有 6 条收支聚合 SQL 与 `StatRules.SQL_STAT_RELEVANT` MUST NOT 改动。
净切片中金额 `<= 0` 的分类 SHALL 不绘制扇区。

#### Scenario: 餐饮垫付后报销

- **GIVEN** 餐饮支出 38.50 元，随后报销收回 38.50 元
- **WHEN** 查看月度支出分类占比
- **THEN** 餐饮净支出为 0 且不出现在环形图
- **AND** 概览仍显示毛支出 38.50 元与「已报销 38.50 元」

#### Scenario: 报销多于支出被钳制

- **GIVEN** 某分类净支出计算结果为负
- **WHEN** 生成切片
- **THEN** 该分类不绘制，其余分类占比不受影响
- **AND** 不出现负金额扇区

**理由**：随手记当年主打「退款功能，从此支出收入不再虚高」；
只有净支出才能回答「哪类支出比较多」这个原始诉求。

---

### Requirement: 合计一致性校验扩为净支出口径

`ChartData.checkTotals` SHALL 接收净支出合计，
并校验「一级净支出合计 = 二级净支出合计 = 总净支出」。
不一致时 UI SHALL 显示警告条，单测 MUST 覆盖。

#### Scenario: 一二级与总额一致

- **GIVEN** 一级 / 二级各记若干笔且含报销
- **WHEN** 打开统计页
- **THEN** 三个口径全部相等、不显示警告

#### Scenario: 不一致

- **GIVEN** 人为构造不一致的聚合结果
- **WHEN** 计算 TotalsCheck
- **THEN** `consistent = false` 且 UI 显示警告条
- **AND** 不静默显示错误占比

**理由**：阶段 4 的合计校验精神是「账要能对得上」；
引入报销后校验口径必须同步升级，否则会变成假的绿灯。

---

### Requirement: 预算进度条使用 SQL 聚合消耗且只在应用内标记超支

系统 SHALL 提供 `TOTAL` / `ROOT` / `CATEGORY` 三种预算范围，
消耗 MUST 来自 SQL 聚合（毛支出减报销），
MUST NOT 用已加载的分页数据求和。
超支 SHALL 以统计页进度条与文字标记呈现，
MUST NOT 发送通知栏提醒或申请 `POST_NOTIFICATIONS`。

#### Scenario: 餐饮预算超支

- **GIVEN** 餐饮月预算 1000 元，当月净支出 1200 元
- **WHEN** 打开统计页
- **THEN** 显示「已用 1200 / 限额 1000，超支 200 元」并标红
- **AND** 消耗值来自范围聚合查询，不随列表滚动变化

#### Scenario: 未超支

- **GIVEN** 预算 1000 元、净支出 800 元
- **WHEN** 打开统计页
- **THEN** 显示剩余 200 元与进度条，不标红

**理由**：分页只加载一屏，用它求和会随滚动跳变；
通知权限与零新增权限基线冲突，超支标记放在 App 内已足够。

### Requirement: 按币种统计走独立查询且既有聚合 SQL 不改

系统 SHALL 提供带 `currencyCode = :currencyCode` 的
`...ForCurrency` 聚合查询覆盖收支概览、一级 / 二级占比、
月度趋势、年度趋势、下钻与 SETTLEMENT 合计。
这些查询 SHALL 全部带 `excludeFromStats = 0`，
MUST 纳入 `check_stat_rules.py` marker 清单。
既有 12 条聚合 SQL 与 `StatRules.SQL_STAT_RELEVANT` MUST NOT 改动。

#### Scenario: HKD 占比不含 CNY

- **GIVEN** 餐饮分类下同时有 HKD 与 CNY 支出
- **WHEN** 统计页选 HKD 查看一级占比
- **THEN** 餐饮金额只含 HKD 流水

#### Scenario: 新查询漏口径

- **GIVEN** 某条 `...ForCurrency` 查询未带 `excludeFromStats = 0`
- **WHEN** 执行 `check_stat_rules.py`
- **THEN** 检查失败并指出该查询

**理由**：沿用 6b 验证过的「既有聚合 SQL 零改动 + 新增独立查询」思路；
`aggregateAccountDeltas` 若被误加币种条件会直接算错账户余额，
隔离是最稳的做法。

### Requirement: 明细筛选复用唯一统计口径且不改既有聚合 SQL

明细页的 `pagedFiltered` 与 `dailyTotals` SHALL 继续带
`excludeFromStats = 0` 且 `type IN ('EXPENSE','INCOME')`，
加币种条件后 MUST 保持这两条口径不变。
6c-1 的 13 条 `...ForCurrency` 统计查询与 `StatRules.SQL_STAT_RELEVANT`
MUST NOT 改动。
明细页与统计页在相同月份、相同币种下的支出合计 SHALL 一致。

#### Scenario: 两页合计一致

- **GIVEN** 2026 年 11 月 HKD 支出合计 123456 分
- **WHEN** 明细页与统计页都切到该月 HKD
- **THEN** 两处支出合计均为 123456 分

#### Scenario: 不计入统计的流水

- **GIVEN** 一条 `excludeFromStats = true` 的流水
- **WHEN** 明细页按币种筛选
- **THEN** 该流水不出现在日合计中
- **AND** 统计页也不计入

**理由**：口径一致性靠「复用同一条常量 + 不改既有 SQL」保证，
比在 Kotlin 层做减法或另写一份聚合可靠；
这也是 6b / 6c-1 连续验证过的隔离思路。

### Requirement: 模板分类与流水类型一致

模板与周期规则保存前 SHALL 校验所选分类的 `type` 与流水 `type` 一致，
不一致 SHALL 拒绝保存并给出可见错误。切换收支类型 SHALL 清空已选分类，
MUST NOT 保留一个类型不匹配的选择。

#### Scenario: 拒绝错配

- **GIVEN** 表单当前为「支出」且选了「工资」（收入一级）
- **WHEN** 点保存
- **THEN** 保存被拒绝并提示分类类型不匹配
- **AND** 账本零变化

**理由**：模板分类下拉此前没按类型过滤，会写出类型错配的流水，
污染分类统计与「一级合计 = 二级合计」校验。

---

### Requirement: 分类管理全量父级与排序

分类管理 SHALL 提供一级与二级排序，**排序作用域 SHALL 为
`(type, level, parentId)` 三元组**：一级只在同收支类型的一级之间排序，
二级只在所属一级内部排序，MUST NOT 把全部未归档分类当成一个全局数组交换相邻项。
**第 20 批整块修订（替换第 15-17 批自研拖拽表述）**：分类一级、资产页账户、
模板管理 SHALL 共用 `sh.calvin.reorderable:reorderable:3.1.0`（Apache-2.0、
Maven Central、纯 UI 库、无运行时网络行为）与
`ui/components/ReorderListSupport.kt` 的 `rememberKeyedReorder` 适配层：
MUST 用 `ReorderableItem` + 稳定 `key`，MUST 用 `longPressDraggableHandle` 长按手柄，
库 SHALL 负责拖动中 `animateItem()` 让位补间、pointer pass 顺序、长按窗口、
拖到边缘按距离变速自动滚动，MUST NOT 再自写 `pointerInput` / `scrollable` /
「拖拽期间禁用父滚动」/ 实测行高阈值；资产页容器 SHALL 为 `LazyColumn`
（库的边缘滚动仅支持 Lazy 容器），MUST NOT 用 `Column + verticalScroll`。
拖动中 `onMove` SHALL 只改内存顺序，松手 `onDragStopped` SHALL 一次性写入
显式 `sortOrder`，重开页面顺序 SHALL 保持；MUST NOT 使用跨应用
`dragAndDropSource/Target` 协议。分类二级 chips 是网格、不属于库的一维列表模型，
SHALL 保留自研 `WrapRow` 自适应布局与操作弹层里的上移 / 下移；
上移 / 下移同时 SHALL 保留为无障碍兜底（TalkBack 无法执行拖动手势），
MUST NOT 再作为主入口。资产页账户的展示仍按 `AccountType` 分组，
落库顺序 SHALL 由 `commitAccountOrderAll` 全量写回，MUST NOT 丢项。
新增二级分类的父级选择 SHALL 以「搜索 + 可滚动全量列表」展示所有未归档一级分类，
MUST NOT 因下拉高度或屏幕宽度截断；SHALL 显示当前选中的父级及其收支类型。
分类管理 SHALL 提供「支出 / 收入」分段切换，默认支出，
切换后 MUST NOT 混排另一类型的分类，列表行 MUST NOT 重复显示「（支出）」类型后缀。
一级分类 SHALL 默认全部折叠，点击一级行 SHALL 展开 / 收起其二级分类；
展开态 SHALL 以 `WrapRow` 按实测宽度的紧凑 chips 展示二级，
MUST 自适应填满可用宽度后换行，MUST NOT 用 `chunked(3)` 固定每行个数
造成短名右侧留白，MUST NOT 一次把全部二级平铺成独立行；
`WrapRow` SHALL 默认**左对齐**（显式 `Alignment.Start`、`layout()` 占满父宽、
每行从 0 起排），短名行的剩余留白 MUST 固定留在右侧，MUST NOT 交给父级
对齐策略把 chips 推到中间；chip 内文字 SHALL 显式左对齐，MUST NOT 用 M3
默认居中造成「一行看着居中」的观感；
chip SHALL 带 `widthIn(max)` 防超长名独占一行，MUST NOT 用 `weight(1f)` 等宽列。
SHALL 显示每个一级的二级分类数与流水笔数；一级行的笔数 SHALL 含其所有二级分类
（标注「含二级」），笔数 MUST 由一条 `GROUP BY` 只读查询得出，MUST NOT 逐行查库。
点击二级 chip SHALL 可编辑该分类，改名 / 删除 / 上移 / 下移 / 看流水五项 MUST 全部保留；
点击一级行右侧「›」或 ⋯ 菜单里的「看流水」SHALL 打开该分类的近期流水详情弹窗，
MUST 带 `LIMIT`、MUST NOT 全量加载，且 MUST 保留时间、金额、分类与备注。
一级行的折叠展开态 SHALL 用 `rememberSaveable` 保存，配置变更后 MUST NOT 丢失。
编辑视图 SHALL 按「名称 / 类型或父级」分组，输入框 SHALL 用 `placeholder`。
分类管理 MUST 继续采用「顶部固定新增入口 + 惰性列表 + 编辑独立视图」结构
（第 12 批已落地），本批 MUST NOT 为增加信息而把列表改回全量 `Column`、网格卡片堆叠
或全量平铺二级分类。
一级列表相邻项之间 SHALL 有 `ListDivider()` 细线；一级名称 SHALL 显著重于二级 chip
（`titleSmall + SemiBold`）。

#### Scenario: 10 个一级全可见
- **GIVEN** 系统有 10 个一级分类
- **WHEN** 打开新增二级分类的父级列表
- **THEN** 10 个一级分类均可滚动查看与搜索，选中项高亮并显示收支类型

#### Scenario: 一级下移只换一级
- **GIVEN** 支出一级「餐饮 / 零食 / 水果」与若干二级分类
- **WHEN** 点「餐饮」下移
- **THEN** 一级顺序变为「零食 / 餐饮 / 水果」，二级与收入分类顺序完全不变

#### Scenario: 二级只在父级内排
- **GIVEN** 「餐饮」下有 8 个二级、「零食」下有 5 个二级
- **WHEN** 点「餐饮·早餐」下移
- **THEN** 只与「餐饮」下的二级换位，「零食」下的二级顺序不变

#### Scenario: 拖动中实时让位
- **GIVEN** 一级「餐饮 / 零食 / 水果 / 交通」
- **WHEN** 长按「餐饮」手柄向下拖过「零食」
- **THEN** 拖动过程中「零食」立即让位、列表显示为「零食 / 餐饮 / 水果 / 交通」
- **AND** 松手后重进页面顺序不变（一次性落库，非松手才跳变）

#### Scenario: 短名二级自适应换行
- **GIVEN** 某一级下有 8 个二级，名称多为 2～3 字
- **WHEN** 展开该一级
- **THEN** chips 按实测宽度连续排列填满一行后才换行，MUST NOT 固定每行 3 个
- **AND** 行尾无大片空白，超长名受 `widthIn(max)` 约束不占满整行
- **AND** chips 从左边界起排，不居中

#### Scenario: 资产页拖拽不误触滚动（第 17 批）
- **GIVEN** 资产账户超过一屏，需要滑动查看更多
- **WHEN** 长按某账户手柄上下拖动
- **THEN** 页面不随手指滚动，账户按实测行高跨行换位，松手顺序持久
- **AND** 拖到屏幕上下边缘时列表自动滚动，可继续与屏幕外账户换位

#### Scenario: 分类详情
- **GIVEN** 某分类有 300 条流水
- **WHEN** 点击该分类行
- **THEN** 弹窗显示最近若干条（带 LIMIT）流水的时间、金额与备注
- **AND** 不把 300 条全部加载进内存

**理由**：第 14 批补默认折叠与 chips，第 15 批修排序作用域真 bug 并加一级拖拽
（但松手才跳变、二级仍上移下移、资产/模板未接）。第 16 批按使用者真机反馈
「拖动过程中移动到对应位置就显示松开后的效果」「整改 app 的排序方案，风格尽量统一」，
把四处排序收敛到同一实现并补实时让位；同时合并本 change 内此前**两个同标题
MODIFIED 块**，避免归档时出现重复 Requirement；按标题整块替换，不留矛盾文本。

### Requirement: 预置分类清单可替换且支持启用开关
随包 `assets/default_categories.json` SHALL 是分类清单的唯一可替换来源；更换清单 MUST NOT 需要改 Kotlin 代码。
第 52 批起 `categories.enabled` SHALL 只控制**记一笔默认快捷栏**的可见性：停用 MUST NOT 删除行、
MUST NOT 改写已有流水、**MUST NOT 从「分类 ›」全量选择器或筛选面板隐藏**（仅加「停用」角标）。
统计、排行、历史、CSV 导出与加密备份 MUST 仍读全量。

#### Scenario: 停用后不进默认快捷栏但统计仍计入
- **WHEN** 使用者停用一级「宠物」
- **THEN** 记一笔默认快捷栏不再出现「宠物」及其二级
- **AND** 点「分类 ›」全量选择器或筛选面板仍可搜到并选中「宠物」及其二级（带「停用」角标）
- **AND** 统计占比卡、排行、历史流水与该分类的既有金额保持不变

#### Scenario: 重新启用
- **WHEN** 使用分类管理再次启用「宠物」
- **THEN** 该一级与其二级重新出现在录入侧，历史流水归属不变

### Requirement: 按 A 方案区分租房与代缴父母水电
「居住」一级 SHALL 只包含租房 / 自住产生的成本（房租、水费、电费、燃气、物业、宽带）。代父母缴纳的水电 SHALL 归到「人情社交 / 赡养·代缴水电」，MUST NOT 与租房费用混在同一一级下。

#### Scenario: 水电两种动机分开
- **WHEN** 使用者记一笔老家父母的水电费
- **THEN** 可选「人情社交 / 赡养·代缴水电」
- **AND** 「居住 / 水费」「居住 / 电费」仍只用于自己住处

#### Scenario: 同名二级不再混淆
- **WHEN** 图表或筛选 chip 显示二级分类名
- **THEN** MUST 经 `CategoryDisplay.resolve` 带父级名，且每个一级下 MUST NOT 再设名为「其他」的二级

## 第 51 批：预制分类 v2 / ID 规则 / 删除策略

### Requirement: 预置分类 ID 分配规则且 ID 无语义

随包 `assets/default_categories.json` 的 `id` SHALL 按下列规则分配：

- 支出一级 `1..10`，收入一级 `30..34`；`11..29` / `35..99` 为一级预留段；
- 二级 `id = parentId × 1000 + 序号(1..999)`，即 `1001..99999`；
- 系统 MUST NOT 从 ID 数值解析父子关系或收支类型，树关系 SHALL 唯一由 `parentId` 表达，
  统计 SHALL 走 `records.rootCategoryId` 冗余列；
- 一级与二级 ID 段 MUST NOT 重叠（二级最小 id MUST 大于一级最大 id），
  全部 id MUST 全局唯一；
- 手工新建 / CSV 导入新建的分类 SHALL 由 `@PrimaryKey(autoGenerate = true)`
  从 `sqlite_sequence` 历史最大值 +1 分配，MUST NOT 显式指定 id；
- 预制清单与 `SeedData` fallback、测试资源副本 SHALL 逐行一致（`SeedCatalogTest` 断言）；
- `SeedData.isPresetCategoryId(id)` SHALL 由清单 id 集合生成，供删除策略使用，
  MUST NOT 靠解析数值段位判断。

#### Scenario: 加一级与二级不撞号

- **GIVEN** 未来新增一个一级分类与某父下新增多个二级分类
- **WHEN** 按规则取预留段与父段内空位
- **THEN** 新 id 与现有 id 不重复、父子段不重叠
- **AND** 无需改表结构、无需 Migration、无需重刷历史 `rootCategoryId`

**理由**：旧方案是隐式约定（一级 1..15 收支混编、二级 父×100+序号），每父最多 99 个二级，
父 1 加到第 101 个会撞上父 2 的第 1 个。第 51 批把规则显式化，ID 彻底无语义，
换来对查询效率零影响（主键 B-tree 比较、HashMap 查找、GROUP BY 均与 ID 数值无关）。

### Requirement: AUTOINCREMENT 空洞不回填且删除策略可恢复

系统 MUST NOT 回填 AUTOINCREMENT 因物理删除留下的 rowid 空洞；回填会让旧备份、
残留引用或归档行里的旧 id 被新分类顶上，造成静默关联错乱。
分类删除 SHALL 由 `CategoryDeletionPolicy.shouldArchive(linkedRecordCount, isPreset)` 判定：
有流水或属于预制清单的分类 MUST NOT 物理删，SHALL 统一转 `isArchived = 1`；
仅「手工新建且无流水」才可物理删。分类管理删除确认弹窗 SHALL 明确提示
「预制分类删除后不会自动补回，恢复标准清单需全新安装且不导入旧备份」。

#### Scenario: 误删预制分类

- **GIVEN** 使用者误点删除一个无流水的预制二级分类
- **WHEN** `deleteCategory` 执行
- **THEN** 该行转归档、id 与父子关系保留
- **AND** 「已归档」区显示「一级 / 二级」全名，点「恢复」即可回到录入侧

#### Scenario: 外部导入产生大量不合理分类

- **GIVEN** 从 CSV / Firefly III 导入的分类大多已挂流水
- **WHEN** 使用者批量删除整理
- **THEN** 这些行按既有规则转归档而非物理删，几乎不产生 ID 空洞
- **AND** 归档列表的累积为已知且可接受的限制，MUST NOT 为「整理干净」改写历史归属

**理由**：`SeedCallback.onCreate` 只在全新库跑一次，`Migrations.ALL` 为空，
没有「恢复默认分类」入口；`restoreCategory` 对已物理删的行影响 0 行且不报错。
AUTOINCREMENT「永不重用」正是「备份里 categoryId 引用不破」的保证。

### Requirement: 分类导入必须整表替换

任何分类导入路径（含 `importBackup`）SHALL 先清空 `categories` 再按父先子后整表插回，
MUST NOT 按 id 增量合并、MUST NOT 按名称重映射、MUST NOT 只恢复流水而丢弃分类表。
备份文件是「分类表 + 表内引用」自洽闭环，整表搬运与具体 id 数值无关。

#### Scenario: 旧备份导入新版

- **GIVEN** 使用者持有第 50 批及更早版本的加密备份
- **WHEN** 在 v0.2.34 全新安装后导入该备份
- **THEN** 关联不错乱（备份内 id 自洽）
- **AND** 但旧分类表整体覆盖新清单，新版预制分类**不会生效**
- **AND** 装包说明 MUST 写明「恢复标准清单 = 全新安装且不导入旧备份」

## 第 52 批：停用语义收窄

### Requirement: 停用分类在全量选择器中仍可选

编辑一条引用了停用分类的历史流水时，`RecordEditorViewModel` SHALL 提供**全量**分类数据源，
MUST NOT 在查询层 `filter { it.enabled }` 把停用项从数据源里删掉。
记一笔默认快捷栏 SHALL 由纯 JVM 出口 `CategoryQuickCandidates.visibleRoots / visibleChildren`
只列启用且未归档项，但**当前选中项所属一级与该二级 MUST 追加**，避免「一排候选没一个亮」。
筛选面板的三处分类选项 SHALL 同样去掉 `enabled` 过滤，仅以「（停用）」角标区分。

#### Scenario: 编辑停用分类的历史流水

- **GIVEN** 一条流水引用了停用的「居住 / 话费」
- **WHEN** 打开编辑流水
- **THEN** 快捷栏出现「居住」一级且「话费」chip 为选中态
- **AND** 保存成功，历史 `categoryId` / `rootCategoryId` 不被改写

#### Scenario: 全量选择器不含归档项

- **GIVEN** 库里存在已归档分类
- **WHEN** 打开「分类 ›」全量选择器
- **THEN** 归档项不出现（归档 = 不再新选）
- **AND** 归档项仍可经快捷栏「当前选中项」兜底显示，统计口径不变

**理由**：第 51 批把 59 个扩展项设为 `enabled=false`，原语义「录入侧不可见」让它们在
`load()` 的数据源里就不存在，「更多分类」也就无从可选。语义收窄后，停用回归「默认不占快捷栏、
按需可选」的本意，且统计 / 历史 / 导出读全量的既有铁律不变。
