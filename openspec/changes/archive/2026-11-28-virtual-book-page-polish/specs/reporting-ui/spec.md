# reporting-ui Delta — 第 93 批：台账页重做与明细可见性收口

> 本 change 已归档，下列 Requirement 正文已逐块合并进 `openspec/specs/reporting-ui/spec.md`。

## MODIFIED Requirements

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

## ADDED Requirements

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
