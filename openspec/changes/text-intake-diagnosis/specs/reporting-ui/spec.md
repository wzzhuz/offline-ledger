# Delta for reporting-ui

## ADDED Requirements

### Requirement: 自动记账文本入口与原文预览页

系统 SHALL 提供 `TextIntakeActivity`，通过 `ACTION_PROCESS_TEXT` 与
`ACTION_SEND`（`text/plain`）两个 intent-filter 接收使用者主动提交的整页文本，
入口标签 SHALL 为「离线账本 · 自动记账」（第 88 批修订：第 86 批曾用
「离线账本 · 文本诊断」，会让使用者误以为该入口只诊断、不记账，MUST NOT 再用）。
`ACTION_PROCESS_TEXT` 的菜单项标签 SHALL 为「自动识别并记账」。
Activity SHALL 同时读 `EXTRA_PROCESS_TEXT`、`EXTRA_TEXT` 与 `Intent.clipData` 第一项的文本，
MUST NOT 因 ROM 只把分享文本放进 `clipData` 而判定为「未收到文本」；
收到非空文本且非内部试解析时 SHALL 进入自动落库 / 预填草稿判定，
MUST NOT 停留在空诊断页。页面 SHALL 显式显示「本次入口状态」（字符数 / 来源 / 是否会写库）。
页面 SHALL 分为「本次结果 / 规则与试解析 / 记录」三个页签（第 89 批）。
从外部 Intent 进入 SHALL 默认落在「本次结果」页，从设置页内部入口进入 SHALL 默认落在
「规则与试解析」页。
「本次结果」页 SHALL 置顶常驻结果条，第一屏即显示处理结果类型与原因（含
`REJECTED` 的失败文案）、本次收到的字符数与来源；
MUST NOT 把失败原因藏在长页面底部或与规则编辑、留痕、原文混在同一滚动列。
预填草稿表单 SHALL 紧随结果条与本次识别摘要之后，
`DRAFT` / `REJECTED` / `DUPLICATE` 状态下 SHALL 可编辑并手工保存。
「规则与试解析」页 SHALL 承载自动开关、规则增补、复制提示词与粘贴试解析，且 MUST NOT 写库。
「记录」页 SHALL 承载结果摘要与原文只读查看。
页面 SHALL 以只读方式显示来源、字符数与截断标记、**全部**疑似金额串、
主金额、页面特征数、方向 / 时间 / 收款方命中、严格门槛结论、
缺失项与提示；SHALL 提供一键复制、一键清空与关闭，并 SHALL 用
`SelectionContainer` 允许使用者长按选择原文贴回分析。
设置页 SHALL 提供「自动记账 / 文本诊断」内部入口用于粘贴试解析，
该入口 MUST NOT 自动落库。

#### Scenario: 支付宝账单详情全选分享

- **GIVEN** 使用者在 HyperOS 3.0 的支付宝账单详情页用系统「全选」选中整页文字
- **WHEN** 点「分享」→「离线账本 · 自动记账」
- **THEN** 页面显示来源 `send`、原文全文、全部疑似金额串、主金额与四项命中结果
- **AND** 严格条件满足且自动开关开启时写事务落库，否则显示预填草稿

#### Scenario: 多个金额候选

- **GIVEN** 原文里同时出现 `-15.60`、`15.90`、`-0.30` 或商品说明里的 `6.00元`
- **WHEN** 页面渲染
- **THEN** 全部候选逐条列出，主金额由状态锚点选择，不折叠成「唯一候选」
- **AND** 系统不猜测哪个是真实金额

#### Scenario: 粘贴试解析

- **GIVEN** 使用者在设置页打开「自动记账 / 文本诊断」
- **WHEN** 粘贴一段原文并点「试解析」
- **THEN** 页面显示只读预演结果
- **AND** 不触发自动落库事务

**理由**：支付宝账单详情是开放集格式，页面必须把原文与命中情况摊开，
让使用者和 AI 能定位失败样例，而不是让规则错误静默进入账本。

---

### Requirement: 文本规则覆盖层与试解析页

系统 SHALL 提供默认规则集随 APK 打包，并 SHALL 提供 App 内「规则增补」文本框。
覆盖层 SHALL 只解析四种人读指令：`状态词 +=`、`优惠词 +=`、`噪声 +=`、
`分类 源分类 = 目标一级分类`；非法行 SHALL 被忽略并显示数量。
覆盖层 MAY 由 AI 生成后粘贴，使用者 MUST NOT 被要求自己编写正则或 JSON。
覆盖层 SHALL 存 `LedgerPrefs`，系统 SHALL 提供「恢复默认规则」。
覆盖层 MUST NOT 修改金额正则、状态锚点选主金额、优惠算术校验、
页面特征门槛、`ExtraAmountRules` 或自动落库充分条件。
`check_text_intake.py` MUST 断言上述护栏。

#### Scenario: AI 给一行规则后即时修复

- **GIVEN** 使用者贴回一笔因新状态词而降级的原文
- **WHEN** AI 返回 `状态词 += 付款完成`，使用者粘贴到规则增补框并点「试解析」
- **THEN** 该笔状态锚点命中，若其余严格条件也满足则显示可达到自动落库门槛
- **AND** 不需要等新版本

#### Scenario: 坏规则不能放宽门槛

- **GIVEN** 覆盖层文本里试图写正则或改金额规则
- **WHEN** 规则解析运行
- **THEN** 该行作为非法行被忽略
- **AND** 主金额锚点、金额正则、算术校验与落库门槛保持默认

**理由**：B 方案的价值是「AI 生成一行词当场生效」，但一段坏文本最多允许造成降级草稿，
绝不能造成「不确定却落库」。

---

### Requirement: 自动落库置信度门槛与严格充分条件

系统 SHALL 仅在**全部**严格充分条件满足且「自动 / 仅草稿」总开关开启时自动落库：
原文未截断；页面特征数 ≥ 2；状态锚点来自受控词表；
主金额为状态锚点前最近独立负金额行且严格两位小数；
支付时间严格 `yyyy-MM-dd HH:mm:ss`；
`订单号` 标签精确等于且其后取得 `^\d{20,}$`；
收款方来源明确；账户 `(纯名, 尾号)` 在本机复合匹配唯一；
若有优惠则 `原价 = 实付 + 优惠` 整数分成立并过 `ExtraAmountRules`。
系统 SHALL 提供「自动 / 仅草稿」总开关，默认自动；关闭时所有入口只形成预填草稿。
任一条件不满足 SHALL 降级为预填草稿并提示缺失项，MUST NOT 猜值补上。
未知页面 SHALL NOT 被外推为支付宝账单详情。

#### Scenario: 六笔实测原文

- **GIVEN** 6 笔支付宝账单详情原文（含优惠、代付、商品说明混入金额、图标占位符、未映射分类）
- **WHEN** 通过 App 文本入口进入识别页
- **THEN** 每笔均能由状态锚点选出主金额，并命中订单号、时间、账户与收款方
- **AND** 两笔优惠通过整数分算术校验
- **AND** 严格充分条件满足时可自动落库

#### Scenario: 未知格式

- **GIVEN** 一段只有微信支付成功文案与 `-8.00` 的原文
- **WHEN** 识别链路运行
- **THEN** 因页面特征不足降级为草稿
- **AND** 数据库流水条数不变、账户余额不变

**理由**：开放集格式无法穷举；严格充分条件把未知格式的代价限制为「少省一步」，
守住铁律 2「账要算得准」。

---

### Requirement: 自动落库、草稿、去重与撤销

严格条件达标时系统 SHALL 调用 `LedgerRepository.commitTextIntake` 写入
`EXPENSE` 流水，`amountCents` SHALL 存实付正整数分，
`discountCents` SHALL 仅存通过算术与 `ExtraAmountRules` 校验的优惠，
分类 SHALL 只预置 `餐饮美食 → 餐饮`，其余未映射 SHALL 留空，
备注 SHALL NOT 自动填商品说明。
去重复用既有 `importSource + sourceId` 口径，
MUST NOT 新开一条写余额分支；同一笔原文分享两次 SHALL 只记一笔。
落库后系统 SHALL 提供 App 内一键撤销，撤销 SHALL 走既有 `deleteRecord`
并触发余额重算，MUST NOT 发通知。
账户匹配不到本机卡片、分类一级不存在或 Repository 写入失败时
SHALL 降级为预填草稿，MUST NOT 静默落库。

#### Scenario: 同一笔全选分享两次

- **GIVEN** 订单号 `2026112623001455461446630585` 已自动落库
- **WHEN** 使用者再次分享同一张账单详情原文
- **THEN** `countByImportSource` 命中
- **AND** 不新增流水、不重复改变账户余额

#### Scenario: 撤销

- **GIVEN** 一笔自动落库显示「已记账」
- **WHEN** 使用者点「撤销这笔」
- **THEN** `deleteRecord` 把流水移入回收站并重算余额
- **AND** 结果摘要追加「撤销」但不包含原文

**理由**：0 次确认最需要保险丝；去重、撤销与既有事务共同把自动化的风险控制住。


---

## ADDED Requirements（第 89 批）

### Requirement: 写事务与 composition 协程隔离

自动落库、手工保存与撤销 SHALL 运行在 Activity 的 `lifecycleScope`，
MUST NOT 运行在 `LaunchedEffect` 或 `rememberCoroutineScope()` 的协程中。
结果状态 SHALL 由 Activity 级 snapshot state 持有，Compose SHALL 只读订阅。
自动落库流程 MUST 只触发一次（普通标记位，MUST NOT 用会触发重组的 state 作 effect key）。
`CancellationException` SHALL 原样抛出，MUST NOT 被 `catch (e: Exception)`
包装成「写入失败」等文案。
`check_text_intake.py` MUST 断言 LaunchedEffect 块内不得出现 `commitTextIntake`。

#### Scenario: 从系统分享进入并自动落库

- **GIVEN** 使用者开启自动开关且未开应用锁
- **WHEN** 从「全选 → 分享 → 离线账本 · 自动记账」进入
- **THEN** `lifecycleScope` 执行 `commitTextIntake` 并写入流水
- **AND** 「本次结果」页顶部显示「处理结果：INSERTED」
- **AND** 不出现 `The coroutine scope left the composition`

#### Scenario: 重组或页面切换期间写入

- **GIVEN** 自动落库事务正在执行
- **WHEN** 使用者切换页签或 Compose 发生重组
- **THEN** 事务不被取消、结果仍回写到「本次结果」页
- **AND** 不会重复触发第二次自动落库

**理由**：第 88 批把写事务放在 `LaunchedEffect` 内，并在 effect 里修改作为 key 的 state，
导致 effect 自我取消；宽泛 catch 又把 `CancellationException` 伪装成业务失败。
这是「没有入库」的直接根因。
