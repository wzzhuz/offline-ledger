# Delta for reporting-ui

## ADDED Requirements

### Requirement: 冷启动明细首屏先渲染启动快照

系统 SHALL 在杀进程后的冷启动时，于应用锁解锁之后、真实分页数据到达之前，
优先渲染一份「启动快照」（上次真实首屏最多 60 行的渲染副本），让用户立即看到明细账目；
快照不满足判废条件时 SHALL 回退骨架占位。
系统 MUST NOT 为校验快照而在冷启动时查询数据库，MUST NOT 把快照作为长期第二数据源，
MUST NOT 在解锁前渲染任何账目。

#### Scenario: 快照命中

- **GIVEN** 应用已解锁、当前为无筛选默认月视图、快照月份与当前月一致、`dbVersion` 与编译期常量一致
- **WHEN** 用户杀掉进程后点图标冷启动
- **THEN** 明细列表立即显示快照中的账目行，界面框架不再出现「列表空着」
- **AND** 真实 Paging 与快照态并行取数，UI 不等待数据库

#### Scenario: 快照判废

- **GIVEN** 任一条不满足：存在显式范围 / 分类 / 账户 / 对方 / 描述 / 金额 / 币种 / 类型筛选、非默认周期视图、
  快照月份与当前月不一致、`dbVersion` 或快照格式版本不一致、应用锁未解锁、文件不可读或解析失败
- **WHEN** 冷启动进入明细页
- **THEN** 系统显示骨架占位而非过期账目
- **AND** 不发起任何查库校验

#### Scenario: 应用锁开启

- **GIVEN** 用户启用了应用锁
- **WHEN** 冷启动且尚未通过生物识别 / PIN 校验
- **THEN** 只显示 `AppLockScreen`，明细 / 资产 / 统计 / 设置任何账目均不显示
- **AND** 启动快照 MUST NOT 成为绕过锁的旁路

**理由**：使用者实测「冷启动 1~2s、列表空着」，而切月 / 统计页 / 账户页无感，
说明瓶颈在每次启动必付的串行链而非查询。感知优先不缩短总时长，但消除「空列表」体感；
答案见 `design.md`「为什么不是查询慢」。缓存只活到换源，靠判废与失效而非同步保持一致。

---

### Requirement: 启动快照与真实分页结果原子换源

系统 SHALL 在 `loadState.refresh` 转为 `NotLoading` 且首屏结果确定后，
一次性把明细内容源从快照态切到真实 Paging 态，销毁快照视图并用真数据覆写快照文件。
快照态与真实态 MUST 是两个独立内容分支，MUST NOT 共用一个 `LazyColumn` 数据源；
换源过程 MUST NOT 出现空列表帧。换源后系统 MUST NOT 在本次进程内再次读取该快照。
换源允许发生一次内容跳变（真数据覆盖旧快照），MUST NOT 承诺零跳变。

#### Scenario: 正常换源

- **GIVEN** 快照态已渲染，真实 Paging 正在加载
- **WHEN** `loadState.refresh` 变为 `NotLoading` 且首屏结果已确定
- **THEN** 明细列表原子切换为真实数据，快照视图销毁
- **AND** 全程无 `LazyColumn` 空白帧，不把「列表空着」复现一遍

#### Scenario: 内容不同

- **GIVEN** 冷启动期间有到期周期补记插入了新流水
- **WHEN** 真实首屏到达
- **THEN** 列表显示包含新流水的真实结果
- **AND** 旧快照被真数据覆写，供下次冷启动使用

#### Scenario: 之后的行为

- **GIVEN** 换源已完成
- **WHEN** 用户切月、设置筛选、下拉刷新或从统计页下钻
- **THEN** 全部走既有 `revision + flatMapLatest` 链路，与启动快照无关
- **AND** 既有切月即刷新、单一周期源、下钻带周期等行为零回归

**理由**：Paging 3 的 refresh 会先清空现有 items；若两态共用数据源，一有 `itemCount > 0` 就切会先闪一次空列表，
正好把要修的症状复现。等 `NotLoading` + 双分支原子换源是最小可靠解。

---

### Requirement: 启动快照的失效边界

任一写库成功时，系统 SHALL 立即作废启动快照态并触发重查；
作废动作 SHALL 挂在 `LedgerRepository` 最底层写事务出口，
MUST NOT 只挂 `DataRefreshBus` / `bumpRevision` / `refreshTick` 中的任一上层出口。
启动快照 MUST NOT 参与 `exportSnapshot()`、`SnapshotWriter`、CSV 导出、加密备份、
`BackupPayload`、统计聚合、余额重算或 `records` 写入；Room 始终是唯一权威数据源。
真实首屏失败或超过 3s 时，系统 SHALL 降级为骨架并给出提示，MUST NOT 静默把旧快照冒充最新数据。

#### Scenario: 窗口内记一笔

- **GIVEN** 快照态已渲染、真实首屏尚未到达
- **WHEN** 用户点「记一笔」并保存成功
- **THEN** 快照态立即退场，界面显示写后真数据或骨架
- **AND** 不残留保存前的旧账行

#### Scenario: 覆盖全部写场景

- **GIVEN** 发生编辑 / 删除 / 报销 / 导入 / 恢复 / 批量规整 / 规则回溯 / 周期补记命中任一写库成功
- **WHEN** 写事务提交
- **THEN** 作废动作由底层出口统一触发，不因入口不同遗漏

#### Scenario: 失败兜底

- **GIVEN** 真实 Paging refresh 失败或超过 3s 未返回
- **WHEN** 用户仍在明细页
- **THEN** 显示骨架与提示，不无限期显示过期账目

#### Scenario: 不进数据出口

- **GIVEN** 用户导出加密备份 / CSV 或触发自动快照
- **WHEN** 导出链路读取账本数据
- **THEN** 内容全部来自 Room，启动快照文件不参与、不被复制、不进备份 DTO

**理由**：项目已有三套互不统一的刷新出口，只挂一个必漏（活文档 §7.27「改一处漏一处」）。
把一致性建立在「短命窗口 + 写入即失效 + 失败降级」上，而不是双源同步，可避免文档腐化同源问题。

---

### Requirement: 冷启动诊断打点

系统 SHALL 提供可关闭的冷启动分段打点，覆盖五点：
`Application.onCreate` 入口 → `System.loadLibrary` 返回 → `getOrCreatePassphrase` 返回 →
首次 DAO 查询前（Room open 完成） → 首屏 Paging 返回。
打点 MUST NOT 常驻持久化、MUST NOT 写入账本数据文件、MUST NOT 进入任何导出；
release 包 SHALL 默认关闭或可移除。系统 SHALL 按打点结论决定后续优化路线，MUST NOT 在测量前预设瓶颈位置。

#### Scenario: 定位瓶颈

- **GIVEN** 同一台机、同一 R8 包
- **WHEN** 杀进程冷启动并读取五点耗时
- **THEN** 可判定 1~2s 落在加密链（so / Keystore / 开库派生）、非加密链（Hilt / Room open / 类初始化）还是分散
- **AND** 结论写进 `design.md` 与 `tasks.md`，作为是否追加 Baseline Profile 的依据

**理由**：5 次冷启动稳定 1~2s 已排除「ART 自然攒 profile 下次变快」，
但加密项与非加密项仍叠在同一条串行链上；不测量就砍加密，可能付了卸载重装的代价却没命中主因。

---

## MODIFIED Requirements

### Requirement: 性能验收基线

系统 SHALL 以一次性生成的 5 万条假数据做性能自测，在中低端机上满足：
冷启动到明细首屏 < 1s、切月 < 200ms、年报聚合 < 300ms、分页滚动无掉帧。
冷启动到明细首屏的判定 SHALL 分为两段：
「从点击图标到首个可见明细内容（启动快照或骨架）」SHALL < 200ms，
「从点击图标到真实分页首屏内容稳定」SHALL 沿用 < 1s 总基线；
两段均 MUST 在同一台机、同一 R8 release 包上测量，冷启动定义为杀掉进程后点图标。
换源过程 SHALL 无空列表帧；任一写操作成功后启动快照态 SHALL 立即退场。
系统 MUST NOT 以降低口径或把旧快照冒充真数据的方式宣称达标。

#### Scenario: 性能自测

- **GIVEN** 灌入 5 万条测试数据
- **WHEN** 逐项测量
- **THEN** 四项指标全部达标
- **AND** 未达标项回到数据层优化而非降低口径

#### Scenario: 启动态分段判定

- **GIVEN** 同一台机、同一 R8 release 包
- **WHEN** 杀进程后点图标冷启动
- **THEN** 首个可见明细内容 < 200ms，真实首屏稳定 < 1s，换源无空帧
- **AND** 打点给出五段耗时，不把 CI 全绿当作性能已验

**理由**：原基线只写「冷启动到明细首屏 < 1s」，未定义「首屏」是骨架还是真实数据，
且 `docs/页面优化经验与避坑指南.md` §8 长期把该项列为真机未验。
本次补分段判定，保留原 `<1s` 口径不降低，明确真机与 R8 包前提，防止用启动画面或旧快照冒充达标。
