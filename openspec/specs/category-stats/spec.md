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
