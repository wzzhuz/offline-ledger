# Delta for 页面、性能与范围

## ADDED Requirements

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

系统 SHALL 提供恰好 4 个底部 Tab（明细 / 资产 / 统计 / 设置）与中央 `+` FAB。
尚未实现的资产与统计页 SHALL 显示明确标注所属阶段的占位内容，
MUST NOT 隐藏 Tab 或临时把设置移到第 5 格。

#### Scenario: 阶段 2 打开资产页

- **GIVEN** 阶段 2 版本的应用
- **WHEN** 点底部「资产」
- **THEN** 显示「资产看板 · 阶段 3」占位说明
- **AND** 底部导航仍是 4 个 Tab

**理由**：导航结构反复重排是分阶段开发里最没必要返工的部分；
spec 的 4 Tab 约定不因某阶段内容为空而临时放宽。

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
