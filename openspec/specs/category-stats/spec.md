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
及若干二级分类，并 SHALL 预置资产账户与一个带额度的信用负债账户。

#### Scenario: 首次启动

- **GIVEN** 全新安装后首次创建数据库
- **WHEN** `SeedCallback.onCreate` 执行
- **THEN** `categories` 与 `accounts` 表中存在预置数据
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
- **WHEN** 查看环形图
- **THEN** 扇区按 `categoryId` 聚合而非 `rootCategoryId`
- **AND** 合计与一级口径相等

**理由**：使用者明确要求「统计时候支持按一级分类统计，也支持按二级分类的进行统计」。
`rootCategoryId` 冗余列让两条 `GROUP BY` 都不需要递归；三级下钻路径
让「看完图还得重新翻账」不再发生。

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

二级下钻的明细列表 SHALL 复用 `pagedFiltered(...)` 的参数化 SQL，
SHALL 带 `LIMIT`，MUST NOT 一次性加载该分类的全部流水。
明细 SHALL 复用唯一统计口径，已移入 `trash` 的流水 MUST NOT 出现。

#### Scenario: 大类下的大量流水

- **GIVEN** 「餐饮」下有 3000 笔流水
- **WHEN** 下钻查看「食材」明细
- **THEN** SQL 只返回 LIMIT 指定的行数
- **AND** 内存不随该分类总记录数增长

#### Scenario: 口径一致

- **GIVEN** 某笔 `excludeFromStats = 1` 的流水
- **WHEN** 下钻查看明细
- **THEN** 该笔不出现在列表中
- **AND** 与统计页合计口径一致

**理由**：下钻是「月度哪类支出多 → 逐笔核对」的连续路径，
不能因为多一层交互就绕开阶段 2 定下的分页与口径约束。

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
