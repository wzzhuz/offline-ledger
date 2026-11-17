# category-stats · Delta

## MODIFIED Requirements

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

### Requirement: 分类管理全量父级与排序

分类管理 SHALL 提供一级与二级的上移 / 下移排序；
新增二级分类的父级选择 SHALL 以「搜索 + 可滚动全量列表」展示所有未归档一级分类，
MUST NOT 因下拉高度或屏幕宽度截断；SHALL 显示当前选中的父级及其收支类型。
分类管理 SHALL 提供「支出 / 收入」分段切换，默认支出，
切换后 MUST NOT 混排另一类型的分类，列表行 MUST NOT 重复显示「（支出）」类型后缀。
一级分类 SHALL 默认全部折叠，点击一级行 SHALL 展开 / 收起其二级分类；
展开态 SHALL 以紧凑 chips 网格展示二级，MUST NOT 一次把全部二级平铺成独立行。
SHALL 显示每个一级的二级分类数与流水笔数；一级行的笔数 SHALL 含其所有二级分类
（标注「含二级」），笔数 MUST 由一条 `GROUP BY` 只读查询得出，MUST NOT 逐行查库。
点击二级 chip SHALL 可编辑该分类，改名 / 删除 / 上移 / 下移 / 看流水五项 MUST 全部保留；
点击一级行右侧「›」或 ⋯ 菜单里的「看流水」SHALL 打开该分类的近期流水详情弹窗，
MUST 带 `LIMIT`、MUST NOT 全量加载，且 MUST 保留时间、金额、分类与备注。
一级行的折叠展开态 SHALL 用 `rememberSaveable` 保存，配置变更后 MUST NOT 丢失。
编辑视图 SHALL 按「名称 / 类型或父级」分组，输入框 SHALL 用 `placeholder`。

分类管理 MUST 继续采用「顶部固定新增入口 + 惰性列表 + 编辑独立视图」结构
（第 12 批已落地），本批 MUST NOT 为增加信息而把列表改回全量 `Column`、网格卡片堆叠
或全量平铺二级分类；chips 网格只在展开态出现且带补空位对齐。

#### Scenario: 10 个一级全可见

- **GIVEN** 系统有 10 个一级分类
- **WHEN** 打开新增二级分类的父级列表
- **THEN** 10 个一级分类均可滚动查看与搜索，选中项高亮并显示收支类型

#### Scenario: 收支分段与笔数

- **GIVEN** 支出分类 10 个、收入分类 4 个，某支出一级下有 3 个二级共 42 笔流水
- **WHEN** 切到「支出」分段
- **THEN** 只显示 10 个支出一级（默认折叠），该一级行显示「42 笔（含二级）」及二级分类数
- **AND** 行内不再出现「（支出）」后缀

#### Scenario: 一级默认折叠与二级编辑

- **GIVEN** 「餐饮」一级下有 8 个二级分类
- **WHEN** 打开分类管理页
- **THEN** 只显示折叠的一级行，不显示任何二级
- **AND** 点「餐饮」展开为 8 个二级 chips，点其中一个 chip 弹出改名 / 删除 / 上移 / 下移 / 看流水
- **AND** 旋转屏幕后「餐饮」仍保持展开

#### Scenario: 分类详情

- **GIVEN** 某分类有 300 条流水
- **WHEN** 点击该分类行
- **THEN** 弹窗显示最近若干条（带 LIMIT）流水的时间、金额与备注
- **AND** 不把 300 条全部加载进内存

**理由**：第 13 批借 BeeCount `CategoryManagePage` 的分段与笔数、
`CategoryDetailPage` 的分类下钻，但明确不照搬 `_CategoryGridView` 网格卡片
（10 一级 + 20 二级会回到卡片堆叠，违反第 12 批刚修好的紧凑列表）。
第 14 批按使用者真机反馈补上「默认折叠 + 展开态 chips + 点 chip 可编辑」，
只改列表项组织、不动第 12/13 批骨架；按标题整块替换，避免 specs 出现
「点击分类行即看流水」与「点击一级行是展开」两条矛盾表述。

### Requirement: 分类管理全量父级与排序

分类管理 SHALL 提供一级与二级的上移 / 下移排序，**排序作用域 SHALL 为
`(type, level, parentId)` 三元组**：一级只在同收支类型的一级之间排序，
二级只在所属一级内部排序，MUST NOT 把全部未归档分类当成一个全局数组交换相邻项。
新增二级分类的父级选择 SHALL 以「搜索 + 可滚动全量列表」展示所有未归档一级分类，
MUST NOT 因下拉高度或屏幕宽度截断；SHALL 显示当前选中的父级及其收支类型。
一级分类 SHALL 另提供**长按拖手柄**重排：拖拽 MUST 仅在该手柄上触发
（MUST NOT 影响列表滚动），MUST 使用稳定 `key`，SHALL 零新增依赖、
MUST NOT 使用跨应用 `dragAndDropSource/Target` 协议；松手后 SHALL 一次性写入
显式 `sortOrder`，重开页面顺序 SHALL 保持。二级 chips 是网格视图，
MUST NOT 做拖拽换位，仍 SHALL 提供上移 / 下移。
一级列表相邻项之间 SHALL 有 `ListDivider()` 细线；一级名称 SHALL 显著重于二级 chip
（`titleSmall + SemiBold`）；二级 chip 宽度 SHALL 按文本自适应并带 `widthIn(max)`
防超长名占满一行，MUST NOT 用 `weight(1f)` 三列等宽 + 补空 Box 把短名拉成整列宽。
账户列表的上移 / 下移排序作用域 SHALL 同样按 `AccountType` 分组，
MUST NOT 跨类型交换。

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

#### Scenario: 拖拽落库可持久
- **GIVEN** 长按「餐饮」手柄拖到「水果」之后松手
- **WHEN** 退出分类管理再重新进入
- **THEN** 「餐饮」仍显示在「水果」之后

**理由**：第 15 批修真 bug（原 `moveCategory` 用全局数组，UI 过滤后排序看起来不生效），
并把 BeeCount 分类管理的拖拽手感以零依赖方式落地；
按标题整块 MODIFIED，避免 specs 同时存在「全局数组排序」与「分组作用域」两条相反要求。
