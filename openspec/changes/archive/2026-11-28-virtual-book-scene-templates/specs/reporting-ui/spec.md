# reporting-ui Delta — 第 94 批：虚拟账本场景模板

> 本 change 已归档，下列 Requirement 正文已逐块合并进 `openspec/specs/reporting-ui/spec.md`。

## MODIFIED Requirements

### Requirement: 台账页按人核算往来且录入自动带不计入

第 94 批修订：台账页 SHALL 按 `counterparty` 分组显示方向与净额，并提供**由场景
模板决定**的录入入口（礼金模板为「记收礼 / 记还礼」，专项支出模板只给一个
「记支出」）。MUST NOT 在页面里写死礼金专用文案或按钮。
台账聚合 SQL MUST NOT 过滤 `excludeFromStats`，SHALL 分别 SUM `INCOME` 与
`EXPENSE` 方向金额并带 `currencyCode` 条件；净额 = 收 − 还，SHALL NOT 设置
「结清」状态。总览卡 / 分段标签 / 按人卡的措辞 SHALL 全部取自模板。
台账录入 SHALL 自动带入本账本标签、「不计入收支」开关与模板给出的分类 id，
MUST NOT 预填账户 id、MUST NOT 要求用户逐笔手选分类。
设置页 SHALL 提供新建 / 重命名 / 归档 / 排序与「台账记录不计入收支」总开关（默认开）。
台账页 SHALL 另按「台账页提供总览、分段与记录维护」提供分段、总览与记录维护能力。

#### Scenario: 礼金不虚高

- **GIVEN** 用户新建虚拟账本「结婚礼金」
- **WHEN** 点「记收礼」填对方张三与 500 元并保存
- **THEN** 流水带 `台账:结婚礼金` 标签且 `excludeFromStats = true`
- **AND** 主账本月度收入、支出与预算合计均不含这笔
- **AND** 台账页张三一行显示 收 500.00 / 还 0.00 / 待还 500.00（分类已自动带入，无需手选）

#### Scenario: 改名不丢历史

- **GIVEN** 「结婚礼金」下已有若干流水
- **WHEN** 在设置页把视图改名为「婚礼往来」
- **THEN** 绑定标签保持 `台账:结婚礼金` 不变，历史流水仍归入该视图

---

### Requirement: 台账页提供总览、分段与记录维护

第 93 批：台账页 SHALL 由五块组成，MUST NOT 把录入按钮与流水混在同一条滚动列表里：
标题行（返回 / 名称）、总览卡、分段 chip、内容区、底部固定录入区。
总览卡 SHALL 显示本台账的方向合计与净额 / 笔数，措辞由场景模板给出，
由 `partyTotals` 结果在页内汇总得出，MUST NOT 给统计模块新增台账 scope 或
新开聚合分支。分段 SHALL 提供模板命名的汇总段与「流水」两段；
流水段按 `localDate` 分组并给出当日方向小计。底部录入区 SHALL 固定不随列表
滚动，按钮数量与文案 SHALL 由模板决定（专项支出模板只有一个按钮）。

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

---

### Requirement: 台账预填与标签新建 MUST 在同一次状态赋值内完成

第 92 批修订：`RecordEditorViewModel.load()` 内部是带多个 DAO suspend 的协程，
会整体赋值 `EditorState`。虚拟台账预填（类型 / 标签 / 不计入）SHALL 作为 `load()`
参数传入，并在**同一协程内、构造完 `EditorState` 之后**一次性合并；
MUST NOT 由 Composable 在 `load()` 之外同步调用，否则会被后续整体赋值覆盖，
产出的流水不带 `台账:` 标签（台账视图查不到，主账本明细却会显示）。
预填 type 为 INCOME 时，分类 SHALL 取该 type 下启用且未归档的一级，
MUST NOT 沿用默认 EXPENSE 分类（会被「分类与收支类型不一致」拦截）。

第 94 批修订：当调用方给出 `presetCategoryId` 时，分类 SHALL **按该 id 精确
命中**，只校验分类存在且 `type` 与预填后的收支类型一致，**不要求 `enabled`**；
失效时才回退到上一段的「第一个启用一级」。命中二级分类时 `expandedRootId`
SHALL 指向其父一级。保存校验同样只查存在 + type 一致，因此清单中默认停用的
「人情社交 / 礼金」(9001) 与「其他收入 / 红包收入」(34004) 可被自动预填，
MUST NOT 为启用它们而改清单 / 改停用一级断言。

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

### Requirement: 虚拟账本场景模板决定录入文案与预填分类

第 94 批：虚拟账本 SHALL 是**通用容器**，由场景模板决定录入按钮、汇总措辞与
预填分类，MUST NOT 把「记收礼 / 记还礼 / 待还 / 往来汇总」写死成全站行为。
MUST 提供三种模板：`人情往来`（双向、内置分类 34004 / 9001）、
`专项支出`（仅支出、无内置分类）、`自定义`（双向、无内置分类）。

`VirtualBook` 定义 SHALL 新增 `template` 与 `defaultCategoryId` 两个字段，
仍只存 SharedPreferences、零改表、不进备份 DTO。编码扩为 7 段，
旧 5 段数据（第 91～93 批）SHALL 仍能解码且 `template` 回退礼金模板，
历史台账行为零回归；非法模板 id SHALL 由 `normalize()` 回退礼金模板。

设置页新建时 SHALL 可挑模板；已建视图 SHALL 提供「配置」改模板，非礼金模板
SHALL 可设**一次默认分类**，之后每笔录入自动带入，MUST NOT 要求逐笔手选。
切到礼金模板时 SHALL 清掉自定义默认分类，避免两套口径并存。
设置页 MUST NOT 再出现「主账本明细默认不显示」「从左上角切换器进入」等
第 93 批已失效的描述。

模板 MUST NOT 改变以下口径：记账仍落主账本、切换不带录入归属、
台账记录不计入主账本收支、自动记账永远落主账本、零 `bookId`、零统计 scope。

#### Scenario: 新建装修台账不再出现礼金文案

- **GIVEN** 用户在设置页选「专项支出」模板，新建名为「装修」的视图并设默认分类
- **WHEN** 从明细页账本图标进入该视图
- **THEN** 底部只有一枚「记支出」按钮，总览与分段显示「支出 / 净支出 / 汇总」
- **AND** 点「记支出」打开的记一笔已带该默认分类，无需再选分类
- **AND** 流水仍带 `台账:装修` 标签、勾「不计入收支」，主账本明细可见

#### Scenario: 礼金模板自动带对分类

- **GIVEN** 一个礼金模板视图「结婚礼金」
- **WHEN** 点「记收礼」
- **THEN** 记一笔预填 INCOME + 分类「其他收入 > 红包收入」(34004)，虽该分类默认停用
- **AND** 直接保存成功，不出现「分类与收支类型不一致」
- **WHEN** 点「记还礼」
- **THEN** 预填 EXPENSE + 分类「人情社交 > 礼金」(9001)

#### Scenario: 旧视图不丢

- **GIVEN** 用户从 v0.2.72 覆盖安装，本机已有第 91 批建的老视图（5 段编码）
- **WHEN** 打开设置页虚拟账本列表
- **THEN** 老视图仍在，模板为「人情往来」，行为与升级前一致
