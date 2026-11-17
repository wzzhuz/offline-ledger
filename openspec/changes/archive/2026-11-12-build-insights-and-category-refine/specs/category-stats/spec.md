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
