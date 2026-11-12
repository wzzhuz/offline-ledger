# Delta for 页面、性能与范围

> 初始归档：本模块全部为新增要求（`ADDED`），无 `MODIFIED` / `REMOVED`。
> 归档时已原样合并至 `openspec/specs/reporting-ui/spec.md`。

## ADDED Requirements

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
