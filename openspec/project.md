# 离线账本 · 项目约定

跨所有 change 生效的通用约束。写 spec 或改代码前先读这里。

## 产品定位

**不是理财平台，不是云账本，是一台只放在自己手机里的账本。**
知道自己的资产情况、每月哪类支出多、年终收入支出与曲线变化；数据不托管给第三方，
不依赖长期开机的服务器或 NAS。

### 原始场景（照录使用者诉求）

- 市面记账软件（钱迹 / 随手记 / 鲨鱼记账等）数据存在厂商服务器，部分功能收费；
  **资产数据是高度敏感数据，不信任第三方**
- Firefly III 没有官方安卓版本，且需要服务器或 NAS 长期开机；定时开机不方便随手记，
  **时间长了就没动力了**
- 在 Firefly III 出现过「与实际余额差 1 分钱，账单太多核算太费事，
  最后直接记一笔虚拟支出让余额一致」的情况
- 希望分类支持多级（如 食品 → 食材 / 正餐 / 水果），统计既能按一级也能按二级
- **6c-1 澄清**：「导出明文是可接受的，数据掌控在自己手里就可以，
  不可接受的是市场上 app 将数据保存在他们服务器上」

### 三条铁律（任何改动不得违反）

1. **数据是你的** —— **账本数据不得默认上传到厂商服务器 / 托管给第三方，
   数据流向 MUST 由使用者控制**；关闭系统自动备份并排除数据库，
   **应用锁与数据随时可带走由使用者自行决定启用**。
   **2026-11-22 使用者显式授权**：本地数据库改为明文 Room（去 SQLCipher）。
   理由：自用 App 装在个人可信手机，威胁模型不含设备丢失 / 取证 / 物理接触；
   实测加密开库链（so 加载 + Keystore + 密钥派生）导致冷启动 1~2s，去掉后基本无感。
   红线仍是数据流向，不是本地文件是否加密；**导出 JSON 备份的口令加密 MUST NOT 倒退**。
   **隐私边界（6c-1 与 2026-11-17 两次使用者澄清）**：红线是「**默认把数据传到厂商服务器、
   且用户无法控制**」——这正是弃用钱迹 / 随手记 / 鲨鱼记账的原因；
   **不是**「Manifest 里不能出现 `INTERNET`」或「不能新增 Gradle 依赖」这类形式禁令。
   自用 App 由使用者自己决定不主动往不可信三方上传，权限（控制权）在手里即可。
   本地导出的文件是明文还是加密由使用者自行决定，明文不构成违规；
   JSON 备份已实现口令加密，属于已交付能力而非禁令，MUST NOT 倒退成明文。
   **当前状态**：App 实际仍不声明 `INTERNET`、无任何网络请求，因为现有功能不需要；
   但这一条不再是「铁律级否决理由」。将来若使用者明确要联网能力，
   SHALL 在本文件写一条显式授权（用途 / 数据流向 / 用户如何控制）后放行，
   MUST NOT 再以「零网络权限 / 零新增依赖」为由否决，也 MUST NOT 未经授权悄悄引入。
   **始终禁止**：广告 / 统计埋点 / 崩溃上报 / 远程配置 / 账号体系等
   「默认回传且用户不可控」的 SDK。
2. **账要算得准** —— 整数分、统一带符号余额、净资产 = SUM(balance)、
   余额是可重建缓存、校准不污染任何报表、全局唯一统计口径
3. **记账不能变慢** —— 10 年 2–5 万条仍要两步记完一笔；
   明细分页 + SQL 聚合，多级分类不得增加记账摩擦

有人提议**在未经使用者显式授权的情况下**加云同步、账号体系、埋点、
或把 v1.1 功能悄悄塞进 v1 时，用这三条立刻拦住。

---

## 技术栈

| 项 | 选择 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material Design 3（禁止 XML 布局） |
| 架构 | MVVM + Clean Architecture（UI → ViewModel → Repository → Room DAO） |
| 本地库 | Room (SQLite)，平台默认 SQLiteOpenHelper（明文，第 50 批去 SQLCipher） |
| 异步 / DI | Coroutines + StateFlow / Hilt |
| 序列化 | kotlinx.serialization（备份 DTO 与 Entity 分离） |
| 分页 / 图表 | Paging 3 / 原生 Canvas 自绘（环形图、折线、分组柱） |
| 最低版本 | Android 8.0（API 26） |
| targetSdk | **不写死**，跟随开工当年官方最新要求，编码前复核 |
| 默认本地化 | `Asia/Shanghai`、默认币种 `CNY`（可在设置页改，只影响新建账户） |
| 账户类型 | `ASSET` 资产 / `LIABILITY` 信用负债 / `PREPAID` 充值储值卡 / `INVESTMENT` 投资理财；净资产只按 `includeInTotal` 汇总，不按类型分支 |
| 默认账户 | 偏好存账户 id（不进 Room、不进备份）；记一笔与模板未指定账户时兜底 |
| 包名 | 待阶段 0 确定（建议 `com.zwz.offlineledger`） |

**版本组合**：不在设计归档阶段猜 AGP / Kotlin / Compose BOM 的具体版本号。
本仓库无本地编译环境，GitHub Actions 是唯一试错场；
具体版本组合 SHALL 在阶段 0 的 change 中查证官方文档后锁定，并写入该 change 的 `design.md`。

**依赖取舍（2026-11-17 修订）**：判断标准是「是否会默认回传数据、用户是否可控」，
不是「依赖数量」。纯 UI / 本地能力库（如第 19 批的 `reorderable`）允许引入，
新增依赖 SHALL 记录坐标、许可证与传递依赖审计。
Vico 等第三方图表库仍不引入（Compose API 跨版本变动大，三种图自绘即可）；
Coil / Glide 仍不引入（本项目无远程图片加载需求）；
**任何广告 / 埋点 / 崩溃上报 / 远程配置 SDK 始终禁止**（默认回传且用户不可控）。

**关于 Hilt**：与 `lifelog` 刻意不用 DI 框架的取舍不同，本应用存在加密 OpenHelper、
Repository、备份序列化等多个构造依赖，引入 Hilt 的收益大于成本。

---

## 已确认不做

以下功能明确砍掉或推迟，提 change 时不要重新引入：

### v1.1 分批计划（闸门保留：未标注「本批实现」的项仍不得悄悄加回）

> v1 已收口。以下每一项要进入实现，MUST 先在本表把状态改成「本批实现」，
> 并在对应 change 的 `proposal.md` 写明范围；否则视为范围蔓延。
> 这正是 `reporting-ui`「v1 范围收敛」Scenario 要求的那一步——
> 清单不删除，只按批次显式放行，闸门机制因此继续有效。

| 项 | 批次 | 状态 |
|---|---|---|
| 模板记账 | 6a | **本批实现**（`add-quick-entry-and-templates`） |
| 周期记账（房租 / 工资 / 话费 / 订阅） | 6a | **本批实现**（冷启动按日补记，不用精确闹钟） |
| 桌面 App Shortcut 直达「记一笔」 | 6a | **本批实现** |
| 预算与超支提醒 | 6b | **本批实现**（`add-budgets-and-settlements`，应用内标记，不做通知） |
| 退款 / 报销成对模型（`linkedRecordId` + `RecordType.SETTLEMENT`） | 6b | **本批实现** |
| **多币种基础**（流水币种继承 / 资产页按币种分组 / 统计页币种筛选 / 同币种转账） | **6c-1** | **本批实现**（`add-multi-currency-basics`，严格不混算、不引入汇率） |
| 跨币种混算与汇率折算 | 6c-2 | **永久不做候选**（见下「口径澄清」，需单独决策；本批明确排除） |
| **CSV 明文导出**（UTF-8 BOM / 元两位小数 / `currencyCode` 列） | **6c-2** | **本批实现**（`add-csv-export-and-import`，通过 SAF 写入用户自选位置） |
| **账单导入**（自定义极简 CSV/TSV 模板 + 事务化批量写入 + 去重） | **6c-2** | **本批实现** |
| **明细页与统计页币种口径一致性** | **6c-2** | **本批实现**（`pagedFiltered` / 日合计加可选币种条件） |
| 自动记账 | 6c-2 | 待决策（不随本批搭车） |
| 小组件、通知栏磁贴 | — | **移入「永久不做」**（通知需 `POST_NOTIFICATIONS`、小组件需 Glance/RemoteViews 依赖，与零新增权限/依赖基线冲突；使用者已推迟真机验证，手感无法验） |
| 三级及以上分类 | — | **移入「永久不做」**（两级 + `rootCategoryId` 冗余已够用） |

**分批**：6a / 6b / 6c-1 已归档 / **6c-2 本轮**。
每次开子阶段只把该批项目改为「本批实现」，其余状态原样保留。

> 6c-2 是 v1.1 分批计划的收口批次。做完 CSV 导出、账单导入与
> 明细/统计币种一致性后，v1.1 清单里只剩「永久不做」与「自动记账待决策」。

### 口径澄清（6c-1，使用者明确，优先级高于此前决策理由）

> 「导出明文是可接受的，数据掌控在自己手里就可以，
> 不可接受的是市场上 app 将数据保存在他们服务器上。」

- **阶段 5「v1 不做 CSV」的结论保留**（本批不做），但其**理由被正式推翻**：
  当时写的是「CSV 是明文导出，与导出文件默认加密张力最大」，
  现在明确隐私边界是「不上传第三方」，不是「文件必须加密」。
- 因此 `backup-restore`「CSV 格式约定」与 `privacy-security`「导出文件默认加密」
  两条 Requirement 在 6c-1 用 **MODIFIED** 改写：删除「默认关闭 / 强制二次确认 /
  明文文件勿放公共目录」等以明文风险为前提的约束，删除阶段版本号绑定，
  保留「JSON 备份已加密」这一已交付能力。
- **6c-2 若要做 CSV，只需按闸门放行 + 实现，不再需要隐私评审**；
  但仍 MUST 走一次显式决策，不能悄悄加回。
- **`reporting-ui`「v1 范围收敛」不改**：它描述 v1 的历史范围，至今成立。

## 稳定版前的版本策略（使用者 6c-2 前明确，条件性暂缓）

> 原话：「暂时不用考虑升级场景，在出一个满意的稳定版本之前不会立即使用，
> 只会使用测试数据，**数据结构完全不需要考虑升级迁移**，
> **除非我说明需要考虑升级迁移**。」

**当前状态 = 稳定版前**。使用者至今未装机、不会立即使用；
即使装也只用测试数据，**不存在需要保留的旧库**，
覆盖升级路径不是当前验收范围。因此：

- 数据结构 SHALL 可按功能需要自由调整：
  允许重置 `AppDatabase version`、不再逐段追加 `Migration`、
  不保证旧备份向后兼容、`BackupSchema.CURRENT` / `MIN_SUPPORTED`
  可随实现调整，无需为「兼容 v1/v2/v3」增加代码
- **已落地的三段 Migration 与备份兼容代码保留但冻结**：
  不删除（删除反而增加改动面），也不再作为后续阶段的验收条件
- `check_transaction_annotations.py` 里「逐段 Migration / 版本递增 /
  DEFAULT 校验」的门禁在稳定版前**不再使 CI 失败**；
  `check_stat_rules.py`、金额铁律、隐私门禁不受影响
- 真机验证清单里「三段 Migration 覆盖升级」一项**移除**
- **任何阶段 MUST NOT 以「会破坏升级 / 要写 Migration / 旧备份读不了」
  为由否决数据结构调整**

**恢复条件**：使用者明确说明「需要考虑升级迁移」时，
MUST 重新启用显式 `Migration`、`MIN_SUPPORTED` 向后兼容、
`fallbackToDestructiveMigration` 禁令，并把「从旧版覆盖升级数据完好」
加回真机验证清单。**在此之前本节优先级高于 specs 里同主题 Requirement。**

**暂缓执行清单**（历史决策保留不删；6c-2 已用 1 份 MODIFIED 收口顶层条目，
其余在本节统一声明暂缓，避免十几份 MODIFIED 与规格腐烂）：

- `ledger-transactions`「显式迁移且 seed 覆盖升级场景」、
  「Migration(1→2) 显式且无破坏性迁移」
- `backup-restore`「备份 DTO 与 Entity 分离并带版本号」（旧版兼容那半句）、
  「备份信封头部稳定且带版本号」、「对账锚点随备份往返且不改信封」、
  「备份 schemaVersion 升至 2 且兼容 v1」、「升至 3 且兼容 v1 与 v2」、
  6c-1 新增的「升至 4 且兼容 v1 至 v3」
- `build-and-ci`「写方法清单与迁移静态检查扩展」、
  「迁移静态检查覆盖两段 Migration 与 ALTER TABLE」、
  「阶段 6a / 6b / 6c-1 纯 JVM 单测覆盖迁移」

> 这不是永久废除，是**条件性暂缓**；措辞 MUST NOT 写成「永远不做升级」。

### 真机体验口径（优先级最高，覆盖历史表述）

> 使用者在中国大陆，已完成第一轮真机体验并提交 7 条反馈。
> 本节列出的现行口径**优先于**历史 spec 中与之相反的表述；
> 历史文本保留用于追溯，但新会话 MUST NOT 再按旧文本实现。

| 历史表述（不再执行） | 现行口径 |
|---|---|
| 默认币种 `HKD` / 时区 `Asia/Hong_Kong` | 默认 `CNY` / `Asia/Shanghai`；设置页可改默认币种 |
| 「内置计算器键盘」、`+ - × ÷ ( ) =` | 纯数字键盘（`0-9 . ⌫ C`） |
| 「中央悬浮大号 `+` FAB」 | 底栏**内嵌** `+`，不遮挡内容；Tab 仍恰好 4 个 |
| 记一笔一屏放不下需上滑 | 备注与收款方首屏可见，分类横向单行，「更多」折叠 |
| 日期只能前一天 / 后一天 | 日历可一次选任意日期 |
| 「CSV 明文有风险」 | 明文本地文件由使用者掌控，可接受；红线是数据不上传厂商服务器 |
| 「无 `INTERNET` / 零新增依赖」是铁律 | 2026-11-17 使用者澄清：红线是「默认回传厂商且用户不可控」，不是权限与依赖的形式禁令 |
| 三段 Migration 叠加是最大风险 | 稳定版前 `version = 1`、不注册迁移；已装机包需卸载重装 |

历史 Scenario 里出现的 `HKD`（如「HKD 占比不含 CNY」「v1 备份默认 HKD」）
是多币种测试示例与备份格式兼容语义，**不代表现行默认值**。

### 阶段计划补充

| 阶段 | 内容 |
|---|---|
| 7 | 真机体验打磨：默认币种 / 时区、账户编辑期初余额、日历、收款方、紧凑布局与纯数字键盘、底栏内嵌 `+`、模板分类修复、`counterparty` 全链路 |
| 8 | 第 6 批真机反馈：记一笔统一表单网格 + 三快捷区、账户类型扩 4 类、默认账户兜底、模板自由度与顺序调整（v0.1.3） |
| 9 | 第 7 批真机反馈：备注收进「更多」、输入框 label→placeholder、日期行去标签与箭头、转账放开备注（v0.1.4） |
| 10 | 第 8 批真机反馈：对方输入框收紧 + 一键清空、账户/模板选中态改底色、账户标签可点开全量、模板说明删除、展开区合并一行（v0.1.5） |
| 11 | 第 9 批真机反馈：修复 placeholder 裁切回默认高度 + 三处字号统一、模板 6 个改两行铺开 + 「模板 ›」整行入口（v0.1.6） |
| 12 | 第 10 批真机反馈：统一各表单行高度量级（输入框 `heightIn(min=40)` / 联想项 40dp 点击区 / 模板 chip 32dp 且行间无留白，v0.1.7） |
| 收口 | 使用者「记一笔页面基本满意」→ 10 批打磨全部合并进 `specs/` 并归档；经验与踩坑沉淀为 `docs/页面优化经验与避坑指南.md`，真机未验清单由该文档 §8 继承 |
| 13 | 第 11 批：筛选面板统一网格 / 日期选择器 / 交易对方联想 / 修金额区间回填（v0.1.8，`compact-filter-sheet` 已归档） |
| 14 | 第 12 批：设置 Tab 四个管理页「TopBar 固定新增 + 惰性列表 + 紧凑行」+ 全仓选中态与输入框写法统一（v0.1.9） |
| 15 | 第 13 批：吸收 BeeCount「一卡一结论」——洞察总结卡、Top5 排行、隐私模式、分类管理分段/笔数/详情下钻（v0.2.0） |
| 16 | 第 18 批：引入 `sh.calvin.reorderable:reorderable:3.1.0` 替换自研拖拽手势层；隐私口径按使用者 2026-11-17 声明改写（依赖兼容先由 CI 验证，再逐页替换实现） |
| 17 | 第 20 批：按已验证坐标逐页接入 reorderable 3.1.0（资产页改 `LazyColumn`、分类一级、模板），删除自研手势层；二级 chips 保留 `WrapRow` + ↑↓（v0.2.5） |
| 收口（二） | 使用者 v0.2.5 装机确认「拖动排序顺畅了，达到预期效果。页面打磨的差不多了」→ 第 13–20 批全部合并进 `specs/` 并归档 `build-insights-and-category-refine`；基线 227 Requirement / 408 Scenario，进行中 change 为无；下一阶段为 **Firefly III 历史数据迁移** |

### 永久不做
- 云同步、账号体系、多账本 / 成员 / 共享账套
- 广告、统计埋点、崩溃上报、远程配置
- **未经使用者显式授权并写明数据流向**的云同步 / 账号体系 / 远程配置
  （当前不放行不是因为「无 INTERNET」是铁律，而是现有功能不需要且尚未授权）
- 物理删除有流水的账户 / 分类（改为 `isArchived` 归档）
- `fallbackToDestructiveMigration`（**稳定版前暂缓**：尚无真实账本；
  一旦开始录入真实数据 MUST 重新启用显式 `Migration`，见上节）
- 用「记一笔虚拟支出」做余额校准（改为独立 `BalanceAdjustment`）
- 「总资产 − 总负债」作为净资产计算式
- 三级及以上分类（两级 + `rootCategoryId` 冗余已覆盖统计与下钻需求）
- 小组件与通知栏磁贴（真机手感无法验；非权限否决理由，2026-11-17 口径修订）
- 跨币种混算与汇率折算（6c-1 明确排除；若将来要做 MUST 单独决策并写明汇率来源）

---

## 编码约定

- 金额一律 `Long` 分；`BigDecimal` 只用于输入 / 展示；禁止 `Float` / `Double` 参与金额
- 所有账户统一 `balance += delta`，不按 `ASSET` / `LIABILITY` 分支
- 流水新增 / 编辑 / 删除 / 恢复 MUST 包裹在 `@Transaction` 内，事务末尾重算余额
- `rebuildAllBalances()` 是余额的唯一权威写入路径；冷启动、导入后必跑自检
- 统计口径只认唯一常量 `isStatRelevant`；DAO 只暴露命名明确的查询出口，
  禁止各页面手写裸 SQL 各自过滤
- 流水冗余 `localDate` / `yearMonth` / `rootCategoryId`，避免在 SQL 中对 `timestamp` 做函数运算
- 明细 MUST 走 Paging 3 + 范围 SQL；面向用户的统计 MUST 走 SQL 聚合，
  MUST NOT 用 `records.size`（那是已加载数，会随加载跳变）
- 列表渲染 MUST NOT 出现「每行一次全表查找」的 O(n²) 写法
- Room 迁移 MUST 写显式 `Migration(from, to)`；`onCreate` 的 seed 只对全新库生效，
  升级场景要在 `migrate()` 里补预置数据
- `debug.keystore` 提交进仓库，保证 CI 每次编译签名一致（否则装不上、数据全丢）
- Release 构建会跑 `lintVital`，debug 不跑；早期就手动跑一次 release，别等发版才炸
- 用 Python 拼接 Kotlin 源码后 MUST 运行 `scripts/check_kotlin_strings.py`
  （`\n` 被提前转义成真实换行，已犯过两次）
- 改动涉及技术选型 / 功能增删时 MUST 运行 `scripts/check_docs_consistency.py`

---

## 构建与发布

- `build.yml`（push 全分支触发、六个静态检查、单测、debug APK + release lintVital）
  在阶段 0 建立；`release.yml`（手动触发、`workflow_dispatch`）在出包阶段建立
- 出包阶段 SHALL 默认开启 R8（`minify` 输入默认 `on`），
  Gradle 侧 `-PminifyRelease` 默认 `false`，日常 `build.yml` 行为不变
- 坚果云 WebDAV 上传发生在 GitHub runner 上，传的是**安装包**不是账本数据；
  App 当前不声明 `INTERNET`（无需），但不再作为铁律级否决理由，见三条铁律 1
- `build.yml` 的 push 触发 MUST 覆盖所有分支（`branches: ['**']`）。
  曾写成 `[main, master, 'feature/**']`，`fix/` 分支推送后 Actions 静默不跑，
  极易被误读成「已编译通过」
- 依赖源配阿里云 Maven 镜像 / 清华 Android SDK 镜像
- `permissions:` 块会覆盖全部默认值，只写 `contents: write` 会让 `actions` 变成 `none`，
  导致 `upload-artifact` 失败；需同时写 `actions: write`
- `upload-artifact@v4` 同名冲突，需显式 `overwrite: true`

---

## 分阶段计划（来自规格书 v2）

| 阶段 | 内容 |
|---|---|
| 0 | Gradle 依赖与包结构、Manifest 隐私基线、金额 / 日期 / 币种基础类型、备份 DTO 与 `schemaVersion` |
| 1 | Room 全部 Entity / DAO / Callback / 索引 / Migration、Repository 事务 + 重算 + 自检、两级分类解析、**最小 JSON 导出**、单元测试 |
| 2 | 计算器键盘「记一笔」、两级分类快选、明细首页 + Paging、搜索筛选、回收站恢复 |
| 3 | 净资产看板、账户详情、余额调整记录、对账流程（阈值 / 锚点 / 金额反查 / 疑似标记） |
| 4 | 一级 / 二级环形图 + 下钻、月度趋势、年度双线、合计一致性校验 |
| 5 | 完整备份与设置（加密导入导出、应用锁、分类管理、账本健康、偏好）；**CSV 当时按「明文风险」判 v1 不做，该理由已于 6c-1 被使用者推翻，结论保留但依据改为批次顺序** |
| 6a | 模板 + 周期记账 + 桌面快捷方式（本轮，首次 `Migration(1→2)`） |
| 6b | 预算与超支提醒、退款 / 报销成对模型（**本轮**，`Migration(2→3)`） |
| 6c-1 | 多币种基础（**已归档**，严格不混算） |
| 6c-2 | **CSV 明文导出 / 账单导入 / 明细与统计币种口径一致性（本轮）**；
| | **稳定版前不考虑升级迁移**（使用者明确，条件性暂缓） |

**v1（阶段 0–5）已全部完成并归档**；出包阶段单独提 change，不属于 v1 功能范围。
**阶段 6 按上表分 6a / 6b / 6c-1 / 6c-2 四批**，每批 MUST 先显式放行闸门再动代码。

**每阶段结束必须能编译运行并跑一遍导出备份**，禁止一次性摊开全部页面。

---

## 参考文档分工

| 内容 | 位置 | 是否会腐化 |
|---|---|---|
| 系统当前行为 | `openspec/specs/` | 会，但归档流程强制同步 |
| 某次变更为什么这么做 | `openspec/changes/archive/*/design.md` | **不会**，历史记录写完即定型 |
| 长期稳定约束 | 本文件 `openspec/project.md` | 低频变更 |
| 架构全貌（30 秒速览） | `README.md` | 会，但短，容易发现 |
| 设计规格书 v2 原文 | `docs/设计规格书_v2.md` | **历史快照，不维护**；冲突以 specs 为准 |
| 页面优化经验 / 避坑清单 / 检查清单 | `docs/页面优化经验与避坑指南.md` | **活文档，MUST 持续更新**；改页面前必读，见 `AGENTS.md` |

> 不在 `docs/` 写**技术决策 / 系统当前行为**（那是 `specs/` 的职责）。
> `docs/页面优化经验与避坑指南.md` 是唯一例外：它只放过程经验、症状→根因→解法、> 可复用检查清单与未验清单继承，不放「系统现在是什么样」，因此不构成双轨。
> 早期「spec + docs 双轨」曾出现
> 「文档写着不用 Room 而代码早已迁移」持续数个版本无人发现：
> 同一件事有两个地方可写，就没有任何机制保证同步。

## 阶段计划补录（第 22 批，2026-11-17）

- v0.2.6：新增 Firefly III 6.6.x `transactions.csv` 一次性迁移（CSV 路线，
  API 直连留作可选二期）。零表结构、零新增依赖、零权限，覆盖安装即可。
- 已归档 change 累计 15 个；本批新建 `import-firefly-iii-csv`（进行中）。
- 隐私红线按 2026-11-17 使用者声明：**默认把账本数据回传厂商服务器且用户无法控制**，
  判断标准是数据流向与用户控制方式，不是权限与依赖数量。


## 阶段计划补录（第 23 批，2026-11-18）

- v0.2.7（实测 commit `2e6b227cfd5f` / CI `44221209264` / release `44222024298`）：导入闭环 + 数据库级幂等（`importSource` / `sourceId` 复合唯一索引，
  `AppDatabase.version = 2`、无 Migration）+ 统计图表增强（净结余 / 多年 / 稳定色板 /
  横屏 insets）+ 跨来源疑似匹配预览。
- **本批必须卸载重装**：库内已有 Firefly III 真实迁移数据，覆盖安装会因无 Migration
  开库失败；卸载前 MUST 先导出 CSV 或加密备份，重装后用「导入账单」重建。
- `import-firefly-iii-csv` 仍进行中（第 22 + 23 批），未归档；基线 227 Req / 408 Scenario 不变。
- 隐私红线仍按 2026-11-17 声明：判断标准是「会不会默认回传、用户能否控制」，
  不是 Manifest 里有没有 `INTERNET` 或依赖数量。

| 26 | 第 26 批：归档 Firefly III 导入（第 22–25 批）；统计页分类下钻跳明细带筛选、年度趋势关键点简化值与图下汇总、导入备注原样 + 来源字段（v0.2.10） |


## 阶段计划补录（第 27 批，2026-11-18）

- v0.2.11：统计页分类下钻补齐「条件值 + 只读展示名 + 面板维度 + 三维入口」
  四层契约；环形图图例可点与色相分明的稳定色板；排行一级内联展开二级、
  分类 / 收款方 / 账户三维均可跳明细。零表结构（v2 不变）→ 覆盖安装即可。
- 第 26 批实测回填：`d9e9dc28d903` / CI `44253403111` / release `44254405887`；
  上批遗留 `diag.yml` / `diag/unit.log` 本批交付前清理。
- `polish-stats-and-import-metadata` 仍进行中（第 26+27 批），未归档；
  当前主 spec 基线 233 条 Requirement / 422 个 Scenario（第 22–25 批归档后），本 change 归档时再增 1 条 ADDED。

- 第 27 批实测：代码 `73e8a19818d0` / CI `44260916347` success / release `44261811015` success；
  制品 `release-v0.2.11` 12,320,054 B、`mapping-v0.2.11` 3,672,588 B、`metrics` 272 B。


## 第 30 批：预置数据与数据库版本 3（2026-11-18）

- `AppDatabase.version = 3`：新增 `categories.enabled`、`accounts.cardLast4`、`counterparty_aliases` 表。
  `Migrations.ALL` 仍为空；按使用者重申的测试阶段口径，**必须卸载重装，装前先导出**。
- 预制账户行删除，首启零账户；账户由只读快捷模板创建，月付类归信用负债。
- 分类清单唯一可替换来源是 `app/src/main/assets/default_categories.json`；`SeedData` 为同构 fallback，
  `SeedCatalogTest` 逐行断言一致。停用只在录入侧隐藏，统计与导出仍读全量。
- 商户别名归一只在导入落库前生效，不改 `records` 主表、不引入网络行为或新权限。
- 隐私口径不变：不默认回传、数据流向由使用者控制；本次没有新增网络权限或联网依赖。


## 第 36 批（v0.2.18）：规则 2.0 与分类迁移映射表

**根因**：一期「即时批量规整」只有 AND 条件、单一固定目标值、规则用完即弃，
无法表达「旧分类 A/B/C 或对方关键词散乱记录一次圈出」与「20 组旧分类一次映射到新分类」。
Firefly III 的规则是「条件组 + 动作组 + 规则组有序 + strict/非 strict + 对历史回溯应用」，
本批借其模型，不引触发器自动跑 / 表达式引擎 / 标签体系 / stop processing。

**实现取舍（与门禁描述的一处偏差，如实登记）**：门禁写「规则四表」，
实作收敛为 `rule_groups` + `rule_definitions` **两张表**，条件与动作序列化为 JSON 列、零外键。
理由：四种操作符各建表会把恢复链路的 FK 拓扑序再拉长一层，v0.2.16 刚修完 1811，
不为结构纯度再冒一次风险；能力范围（可保存、有序、AND/OR、取反、多动作、回溯）不缩水。

| 项 | 落地 |
|---|---|
| 纯 JVM 内核 | `RuleModels.kt` / `RuleEngine.kt`：ALL/ANY、negate、七种操作符（含正则、为空、不等于）、多动作、组内 sortOrder 有序、`changeset` 逐字段撤销 |
| 分类迁移 | `CategoryMigration` + `migrationReport`（每组命中数 / 冲突组 / 未命中）+ 首次命中优先，与规则组共用同一 `applyBulkTargets` 写路径 |
| 持久化 | `AppDatabase.version = 4`，两张表零外键；恢复按 groups → definitions 顺序插入，纳入 `defer_foreign_keys` / `foreign_key_check` 同一事务 |
| 备份 | `BackupPayload.ruleGroups` 带默认值，旧备份缺键解析为空列表；导出与自动快照自动带上规则 |
| UI | 设置页按钮文案精简为「从 CSV 重建」并给同排按钮 `maxLines = 1`；「批量规整」页新增「规则与分类迁移」弹层，预览 → 单事务 → 末尾重算 → 一键撤销 |
| 不做 | 导入后自动触发（触发器默认关）、规则组嵌套、表达式引擎、标签/预算联动、stop processing |

**测试**：`RuleEngineTest` 6 条（ANY/ALL/取反/组顺序/映射命中与冲突/撤销还原）、
`RuleBackupRoundTripTest` 2 条（规则随备份往返、旧备份缺键仍可解析）。
门禁里写的「恢复 androidTest」未做：CI 无模拟器不跑 androidTest，
改为纯 JVM 备份往返单测覆盖 DTO 层，恢复链路仍待真机「导出→清数据→重装恢复」复核。

**`version = 4` → 必须卸载重装、装之前先导出**；v0.2.17 覆盖安装会开库失败。

## 第 37 批（v0.2.19）：规则编辑器三段式

UI 从固定槽位表单改为动态行编辑器：字段 ▾ + 操作符 ▾ + 按类型分派的值控件
（分类 / 账户弹窗含停用归档、对方 / 备注走 DAO 限量联想），规则头带标题，
条件可增删并支持 ALL/ANY 与取反，动作可叠多个。独立「分类迁移映射表」入口
降级为编辑器内「批量录入」，一次生成 N 条普通规则，共用 `RuleEngine` 与
`applyBulkTargets` 的预览 → 单事务 → 末尾重算 → 撤销链路；删入口不删已存规则。

参照 Firefly III 截图时的取舍：不照抄「字段+操作符」超长单选与桌面四列表格；
FF3 `description` = 本项目「交易对方」、`notes` = 「备注」，因此零改表；
「设置目标账户」映射为「交易对方改为」。安全护栏：空关键词 / 空 ID / 空区间
直接不命中，保存前校验至少一条有效条件，避免默认空行清空全库分类。

**不做**：规则组嵌套、表达式引擎、标签 / 预算联动、stop processing、导入后自动触发。
**`AppDatabase.version = 4` 未变 → v0.2.18 上覆盖安装即可，不用卸载。**

## 第 43 批（v0.2.25）

- 使用者实测：从 Firefly III 导入的数据丢失 `description`（对方被写成「虚拟-不记录」，备注另有信息，结果不知道这笔干嘛）。
- 按 1-A / 2-A' / 3-A：加独立 `description` 列（`version = 5`），打通 FF3 导入、自有 CSV、加密备份/自动快照、记一笔、明细行主标题、筛选与规则字段；明细主标题动态回退（有描述用描述、空着用交易对方）。
- 先归档 `data-safety-and-bulk-rules` 与 `preset-data-and-counterparty`，再新建 `ff3-description-field`，决策史不混。

## 第 44 批（v0.2.26）

- 使用者实测：统计页跳转明细不带时间；转账编辑的冷门 / 归档账户显示不对。
- 按 1-A / 2-A / 3-A：新建 `detail-filter-and-transfer-edit`，`StatsDrillRange` 单一出口给三类共 7 个下钻入口带周期；账户快捷候选合并当前选中项、归档可见、已删除拦截。
- 零改表（`AppDatabase.version = 5` 未变）→ v0.2.25 上覆盖安装即可，不用卸载。
- `ff3-description-field` 保持进行中，待 description 真机验收后再归档。


## 第 45 批（v0.2.27）：默认周期视图 / 编辑态置顶 / 交易详情页

- 无筛选按当前月 / 年（顶部可切），有筛选无时间段看全部时间；统计下钻带周期不回退。
- 编辑态账户与交易对方当前值置顶，按 id 精确回填与已删除拦截不回退。
- 点击记录直接进只读详情页，锁切编辑、删除 / 报销一步到位，不新增 NavHost 路由。
- 零改表（`version = 5` 不变）→ 覆盖安装；change `detail-scope-and-record-page` 进行中。

### 第 45 批实测
commit `657ac195d74f` / CI `44661745120` / release `44662596613`（坚果云 success）/
制品 `offline-ledger-release-v0.2.27` 12,405,979 B。零改表 → 覆盖安装。

## 第 46 批（v0.2.28）：切月即刷新 / chip 同行 / 系统返回消费

- 使用者实测：切月后提示「该月记录尚未加载，继续下滑后再点一次」且列表不刷新；月 / 年切换独占一行；详情页不支持全面屏左滑返回，直接退出 App。
- 根因：`locateMonth()` 只写 `_anchorMonth` / `_overview`，漏写 `_filter.yearMonth` → `periodBounds()` 永远按初始月；详情页是 `actionRow` 二级页、没挂 `BackHandler`。
- 按 1-A / 2-A：切月写回 `yearMonth` + `revision` 触发 Pager 重查，锚点在分页数据到达后定位，删 Snackbar 兜底；月 / 年 `FilterChip` 并入标题 Row；`BackHandler` 消费系统返回，`closeDetail` 单一出口。
- 新增 `DetailMonthSwitchTest` 5 条纯 JVM 单测（月 / 年切月周期重算、跨月模式不夹击、显式范围优先、筛选条件不丢）；不新增 SQL 出口、不动统计口径。
- 零改表（`AppDatabase.version = 5` 未变）→ v0.2.27 上覆盖安装即可，不用卸载。
- 挂账未动：`ff3-description-field`、`detail-filter-and-transfer-edit`、`detail-scope-and-record-page` 三个 change 仍进行中；活文档 §1 事故登记仍待补齐全。

### 第 46 批实测
commit `8a10edf368b6` / push CI `44686081288` success 17/17 / release `44687082430` success（坚果云 success）/ 制品 `offline-ledger-release-v0.2.28` 12408292 B。并发 run `44687435727` 已 cancel，保留 `44687082430`。零改表 → 覆盖安装。

## 第 47 批（v0.2.29）：统计下钻后顶部月概览语义收敛

- 使用者实测：明细页停 11 月、统计页看 10 月下钻后列表是 10 月，但顶部支出 / 结余仍是 11 月，也不是筛选后统计。
- 按 1-A / 2-A：`RecordFilter.showMonthOverview()` 单一出口，仅「无筛选 + 月粒度 + 单月窄视图」显示月概览；年粒度 / 全部时间 / 显式范围 / 统计下钻 / 任意筛选条件一律隐藏数字，只留 `timeSummary()` 范围说明 + 筛选入口。
- 跨范围态日头不显示错月 / 0 值合计；不新增按范围聚合查询、不动统计口径唯一出口。
- 新增 `DetailOverviewVisibilityTest` 7 条纯 JVM 单测；spec 新增 Requirement、design 第 47 批、活文档 §7.29 / §8。
- 零改表（`AppDatabase.version = 5` 未变）→ v0.2.28 上覆盖安装即可，不用卸载。
- 挂账未动：`ff3-description-field`、`detail-filter-and-transfer-edit`、`detail-scope-and-record-page` 三个 change 仍进行中；活文档 §1 事故登记仍待补齐全。

### 第 47 批实测
首轮代码 `1f007364f2b4` / CI `44693391892` failure（单测构造顺序写错），修复 `d24c46e2ba8c` / push CI `44694256046` success 17/17 / release `44695050909` success（坚果云 success）/ 制品 `offline-ledger-release-v0.2.29` 12409171 B。并发 run `44695407402` 已 cancel，保留 `44695050909`。零改表 → 覆盖安装。

## 第 48 批（v0.2.30）：明细页单一周期源

- 使用者实测：统计页从 10 月跳转后表头 / 年月切换仍是 11 月；× 掉筛选后表头 11 月、列表 10 月结果。
- 按 1-A′ / 2-A：`RecordFilter` 新增 `displayYearMonth()` / `displayGranularity()` / `displayPeriodLabel()`，表头、年月选择器、左右箭头、`reload()`、`timeSummary()`、`showMonthOverview()` 全读单一派生；`relocateToMonth()` 下钻态切月换范围保留其余筛选，`cleared()` 清空回默认当前月并递增 revision 触发重查。
- 未隐藏年月选择：保留下钻态直接翻月能力；统计下钻月 / 年 / 多年三类周期表头同步。
- 新增 `DetailDisplayPeriodTest` 8 条纯 JVM 单测；spec 新增 Requirement、design 第 48 批、活文档 §7.30 / §8。
- 零改表（`AppDatabase.version = 5` 未变）→ v0.2.29 上覆盖安装即可，不用卸载。
- 挂账未动：`ff3-description-field`、`detail-filter-and-transfer-edit`、`detail-scope-and-record-page` 三个 change 仍进行中；活文档 §1 事故登记仍待补齐全。

### 第 48 批实测
代码 `2142fe15ad8f` / push CI `44701739996` **success** 17/17 / release `44703074178` **success**（坚果云 success）/ 制品 `offline-ledger-release-v0.2.30` 12410678 B。零改表 → 覆盖安装。

## 第 49 批（v0.2.31）：冷启动明细首屏感知优先

- 使用者实测：R8 0.2.30 / 约 2000 条测试数据 / 无应用锁，杀进程冷启动到明细列表出现 1~2s，**界面框架已出、列表空着**；切月份 / 统计页 / 资产页 / 记一笔均几乎无感；5 次杀进程冷启动「基本都是 1~2s」。
- 根因收敛：切页无感排除「SQL 聚合」与「SQLCipher 页级加解密」；5 次稳定排除「ART 自然攒 profile 下次变快」。嫌疑落在每次启动必付的串行链（so 加载 / Keystore / SQLCipher 开库派生 / Hilt 图 / Room open / 首屏 Paging），**加密项与非加密项未打点前不定案**。
- 按 1-A / 2-A / 3-A：新建 change `optimize-cold-start-first-frame`（**仍进行中，未归档**）。采用**感知优先、零破坏**：`noBackupFilesDir` 存一次性启动快照（最多 60 行）→ 冷启动先渲染 → 真实 Paging 并行 → `Loading→NotLoading` 后原子换源 → 换源即销毁并覆写；判废 / 失败 / 超 3s 回退骨架。
- 一致性：不查库校验、不做长期第二数据源；作废挂 `LedgerRepository` 的 Room `InvalidationTracker.Observer`（11 张表，覆盖全部写事务与元数据写），MUST NOT 只挂三个上层刷新出口；快照不进任何导出 / 快照 / CSV / 备份 DTO，不绕应用锁。
- 配套：五点 `StartupTrace`（release 默认关闭，测完再决定是否追加 Baseline Profile）、`applyDueRules` 挪到首屏换源后、去重 `refreshMonthCounts`、备份规则补 `domain="file"` 排除、检查脚本与 proguard 同步、11 条纯 JVM 单测。
- **铁律 1 重读结论**：红线是「数据不默认上传厂商服务器且用户不可控」，本地加密不是红线；但本批**保留 SQLCipher / Keystore 不动**，A（去加密）/ B（跳 KDF）本轮不实施。将来若调整须独立 change 改本文件 + 3 条 spec。
- 零改表（`AppDatabase.version = 5` 未变）→ v0.2.30 上**覆盖安装即可，不用卸载**；不丢数据。
- 挂账未动：`ff3-description-field`、`detail-filter-and-transfer-edit`、`detail-scope-and-record-page` 三个 change 仍进行中；本批新增第 4 个进行中 change。

### 第 49 批实测
代码 `f9fca48ea153` / push CI `44977573597` **success**（六静态检查 + 单测 + debug APK + release lintVital 全绿）/ release `44978260845` **success**（坚果云 success，已建 tag `v0.2.31`）/ 制品 `offline-ledger-release-v0.2.31` 12,421,988 B、`offline-ledger-mapping-v0.2.31` 3,843,271 B、metrics 269 B。
**CI 全绿 ≠ 真机通过**：首帧 < 200ms、换源无空帧、写后缓存退场、应用锁边界、R8 快照反序列化、五点打点分段结论全部列为真机待验（活文档 §8 第 49 批继承与新增待验）。

## 第 50 批（v0.2.33）：去 SQLCipher 明文库合入 main，废弃第 49 批缓存方案

- **实验定案**：分支 `experiment/plaintext-db`（从 v0.2.30 基线 `6f85c8318bda` 拉，只去加密、不带缓存、不带第 49 批任何改动，HEAD `227e1d2abf16`），R8 包 `offline-ledger-release-v0.2.31.1` 2,542,416 B。使用者真机实测「效果非常好，基本无感就加载出来了」，且顶部支出 / 结余 / 每日金额也一起立刻出来。
  → 1~2s 整段落在**加密开库链**（so 加载 + Keystore + SQLCipher 密钥派生），不是 Hilt / JIT / 查询；第 49 批「最可疑但未定案」至此定案。实验包比正式包小约 10 MB，即 SQLCipher 的 .so。
- **合入方式**：实验分支基线是 v0.2.30，main 已前进 3 个 commit，**不 merge**；在 main HEAD `b450d6f9c633` 上构造新 tree 单次 commit（parent 保持 main HEAD，历史全留）。内容 = v0.2.30 代码底 + 去加密 − 第 49 批全部改动 + 规格修订。
- **代码**：删 `sqlcipher-android:4.5.6@aar`、`System.loadLibrary`、`SupportOpenHelperFactory`、`DatabaseKeyManager.kt` 与 Keystore 取口令；Room 走平台默认 SQLiteOpenHelper。库名 `offline_ledger.db` 与 `version = 5` **不变**（改库名会让备份排除规则失效）。五点 `StartupTrace` 保留但 `ENABLED = false` 并适配明文链路；导出加密 `SecureBackupCodec`、应用锁、备份排除规则、`FLAG_SECURE` 全部不动。
- **缓存方案废弃**：删除 `data/startup/` 4 文件 + `StartupSnapshotTest.kt`；`DetailScreen` / `DetailViewModel` / `LedgerApp` / `LedgerRepository` / `AppDatabase` / `RecordPagingSource` / 两个备份规则 / `check_manifest_privacy.py` / proguard 全部回退到第 48 批状态；**整个 `openspec/changes/optimize-cold-start-first-frame/` 目录删除**（方案废弃、delta 从未合并，留着会误导「新会话只读这一份即可还原全貌」）。历史仍可由 git 追溯，第 49 批经验留在活文档 §7.31 作为留痕。
- **规格修订（正式采纳 A 方案）**：铁律 1 落地描述按使用者确认措辞改写并留显式授权；技术栈表本地库行改明文；`privacy-security/spec.md` 3 条加密 Requirement（「本地数据库加密」「数据库以 SQLCipher 加密打开」「数据库口令由 Android Keystore 保护」）改写为明文边界，L「FLAG_SECURE 与 PIN hash 边界明确」文案同步；保留「隐私边界是数据不离开设备而非文件必须加密」作为依据。**导出 JSON 备份口令加密 MUST NOT 倒退**。
- **破坏性**：旧 `offline_ledger.db` 是加密的，明文 Room 打不开 → **装前 MUST 先导出加密备份 → 卸载 → 安装 → 导入重建**，覆盖安装会崩。零改表、数据可完整重建。
- **版本号**：跳过 `0.2.32`（feature/quick-tile-screen-capture 分支已有同名制品 `offline-ledger-release-v0.2.32` 32,159,121 B，按活文档 §1「禁止同名覆盖」顺延），用 `0.2.33`。
- 挂账未动：`ff3-description-field`、`detail-filter-and-transfer-edit`、`detail-scope-and-record-page` 三个 change 仍进行中；本批不新建 change（取舍与威胁模型已在前两轮论证并实测验证）。

### 第 50 批实测
代码 `d5cb49cbd77e`（parent `b450d6f9c633`，main HEAD） / push CI `45016703228` **success**（六静态检查 + 单测 + debug APK + release lintVital 全绿）/ release `45016754039` **success**（坚果云 success，已建 tag `v0.2.33`）/ 制品 `offline-ledger-release-v0.2.33` 2,541,509 B、`offline-ledger-mapping-v0.2.33` 3,799,683 B、metrics 270 B。
实验分支 `experiment/plaintext-db` 保留供对照（HEAD `227e1d2abf16`），**未合并、未删除**。
**BREAKING 装包顺序 MUST 实测**：导出加密备份 → 卸载 → 安装 v0.2.33 → 导入重建；沙盒无法验证，CI 全绿不得替代（活文档 §1）。

## 第 51 批（v0.2.34）：预制分类 v2 重构（宽一级 / 细二级 / ID 显式规则）

- **起因**：使用者认为预制分类不合理——一级过细且维度混杂（三餐按内容、养车按对象、人情按用途），「三餐」更接近二级。取主流「一级看结构（6~9 个、少而稳）、二级看原因」原则 ；钱迹官方统计里「三餐」恰是一级，但本轮按其二级思路 + 主流宽一级原则，不照抄。
- **清单 v2**（`default_categories.json` version 4，106 行 = 一级 15 + 二级 91；启用 47 / 停用扩展 59）：支出一级 `餐饮 / 交通 / 养车 / 居住 / 购物 / 医疗 / 学习娱乐 / 宠物 / 人情社交 / 其他`，默认启用 7 个（含**养车**，使用者要求独立看成本）；收入一级 `工资 / 奖金 / 兼职 / 理财收益 / 其他收入`。「三餐」概念降为 `餐饮 / 正餐`，与外卖 / 聚餐 / 买菜做饭并列，不再往下挂层级。扩展二级（零食、水果、话费、美妆、长途交通等）`enabled=false`，在分类管理按需启用；停用一级下二级同步停用。
- **ID 规则显式化**：一级支出 1..10、收入 30..34，预留 11..29 / 35..99；二级 = `parentId × 1000 + 序号(1..999)`（1001..34004）。ID 无语义、树关系只看 `parentId`，统计走 `rootCategoryId`。旧隐式规则「父×100」每父最多 99 个二级、父 1 第 101 个会撞父 2 第 1 个，已修。手工新建 / 导入新建走 AUTOINCREMENT 从 `max+1`，不按段位。
- **两个确认题的结论**：① ID 数值对查询效率零影响（主键 B-tree / HashMap / GROUP BY 均与数值无关），抬高 AUTOINCREMENT 起点无实质开销；② 备份自带完整分类表且 `importBackup` 整表替换，**ID 怎么编都不用重写备份代码**；「ID 不动」只适用于「已有数据的库上原地改清单」，使用者已定全新安装、测试数据舍弃，该约束消失。
- **ID 空洞与误删**（使用者追问后核实）：外部导入分类大多挂流水 → `deleteCategory` 只归档、几乎不产生空洞；AUTOINCREMENT「永不重用」正是引用不破的保证，**明确不回填并写进 spec**。误删预制分类确实不可逆（onCreate 只种一次、无恢复默认入口、restoreCategory 对已删行影响 0 行、弹窗无警告）→ 采用 B 方案：新增纯 JVM `CategoryDeletionPolicy.shouldArchive`，预制一律转归档；弹窗补警告；归档区显示改经 `CategoryDisplay.resolve` 带父级前缀。预制 id 集合取 `SeedData.presetIds`（由一致性单测保证与随包 JSON 同步），零 Context、零 DI、零表结构变更。
- **文档债顺手修**：`ledger-transactions` 的 `version SHALL 为 3` → 5；`category-stats` 的「预置资产账户」→ 零账户、停用 Scenario 示例养车 → 宠物、A 方案「人情」→「人情社交」；CSV 往返测试 `三餐,外卖` → `餐饮,外卖`。
- **登记未修**：归档列表平铺累积、重复导入会造同名重复项（导入查重排除了归档行），列为后续独立议题，本轮不动。
- **装包**：`SeedCallback.onCreate` 只对全新库生效 → **全新安装、测试数据重造、不导 v0.2.33 及更早备份**（导入旧备份会把旧分类表整表覆盖回来，新清单不生效）。零改表、`version=5` 不变、`Migrations.ALL` 仍为空。
- **挂账未动**：`ff3-description-field`、`detail-filter-and-transfer-edit`、`detail-scope-and-record-page` 三个 change 仍进行中。本批按「改变系统行为 + 改 spec」新建 change `restructure-preset-categories`。

### 第 51 批实测
代码 `ca4c80f0db29`（parent `85c7e0ce84a9`，main HEAD）→ push CI `45109206812` **六静态检查 / 单测 / debug APK 编译 / release lintVital 全部 success，仅「上传 Debug APK」失败**（attempt 1 与 rerun attempt 2 均如此）。该失败与本批改动无关：库内累积 209 个 Actions artifacts（单页约 1.26 GB），疑似存储配额耗尽，`build.yml` 该步骤已设 `overwrite: true`、`retention-days: 7`，仍上传失败。
release `45109513641` **success**（坚果云 success，已建 tag `v0.2.34`）/ GitHub Release 附件 `offline-ledger-v0.2.34-release-ca4c80f0.apk` 3,039,702 B；Actions artifacts 因同类上传失败未生成（该步骤 `continue-on-error`，不影响打 tag、Release 附件与坚果云）。
**装包**：全新安装、测试数据重造、**不导 v0.2.33 及更早备份**（导入旧备份会把旧分类表整表覆盖回来，新清单不生效）。沙盒无真机，分类候选 / 停用项启用 / 归档恢复 / 弹窗观感 MUST 真机验（§8），CI 全绿不得替代（§1）。
**后续运维**：给 `cleanup-artifacts.sh` 补 `offline-ledger-debug-apk` 的清理 pattern 或手工清理历史产物，否则 push CI 会持续红在 artifact 上传步骤；本批不擅自删仓库产物、不改 workflow。

## 第 52 批（v0.2.35）：停用语义收窄 + 分类全量选择器 + 一级直记

- **起因**：第 51 批把 59 个扩展项设为 `enabled=false`，但旧语义「停用 = 录入侧不可见」让 `RecordEditorViewModel.load()` 在数据源就 `filter { it.enabled }`，连「选择更多」都选不到。使用者要求：默认快捷栏只展示启用项，停用项可像「选择更多账户」那样点「分类」进入更多，**不要用下拉框**，尽可能方便选择；并指出「就按xx记」是多余确认、点一级本就该按一级记、选错二级再点一级应回到一级。
- **停用语义收窄（改 spec）**：从「录入侧不可见」改为「默认快捷栏不展示，全量选择器 / 筛选面板仍可选」。数据源改回全量 `categoryDao.all()`，过滤下沉到 UI；统计 / 排行 / 历史 / CSV / 加密备份读全量不变。筛选面板按使用者确认**同步修改**：三处分类选项去掉 `enabled` 过滤，停用项加「（停用）」角标，归档项仍不进选择器；因筛选面板第 27 批已有带搜索的全量弹层，不再新增「更多」按钮或 chip。
- **纯 JVM 出口** `core/CategoryQuickCandidates.kt`（7 个单测）：`visibleRoots / visibleChildren` 只列启用且未归档项，但**当前选中项所属一级与该二级 MUST 追加**——否则编辑引用停用分类的历史流水时一排候选没一个亮（`save()` 查全量校验其实保存不会失败，只是视觉误导）。`pickerGroups` 含停用一级 / 二级、不含归档；`searchHits` 跨组平铺，二级 label 带「一级 / 二级」全名。
- **「分类 ›」全量选择器（B 方案）**：`FormRow("分类", onRowClick=…)` 整行可点、label 带 `›`，与「账户 ›」同构，不新增 chip、不占首屏。弹层用 `Dialog + Surface`（不引入 ModalBottomSheet、不新增依赖，免依赖审计）：搜索框 + 按一级分组的 `WrapRow` chips + 460dp 上限滚动。组标题可点 = 按一级直记，chip 可点 = 选二级，**点任一项立即选中并关闭，无二次确认**。搜索跨组平铺；停用项带角标、归档项不出现。否决：Spinner / ExposedDropdown / DropdownMenu（使用者明确不要下拉框）、仿账户竖排 TextButton 长列表（106 行要长滚）、快捷栏尾部「更多」chip（挤占首屏，重蹈活文档 §7 模板翻车）。
- **一级直记**：新增 `selectRoot(rootId)`——点一级即 `categoryId = 一级`、首次展开二级 / 再点同一级收起；删「就按「xx」记」TextButton 与死代码 `setExpandedRoot` / `onKeepRoot`；一级选中态由文字换色升级为 `secondaryContainer` 底色。核查确认**保存逻辑本来就是对的**（点一级已把 categoryId 置为一级，`CategoryTree.resolveRootId` 对 level 0 返回自身），本批改的是误导文案与视觉，不是记账口径。默认一级与 `setType()` 重置补 `enabled && !isArchived`，避免落到停用扩展项。
- **铁律 3**：默认路径仍是「打开 → 点一级 → 金额 → 保存」≤3 次交互，且少一个确认按钮；选停用项是低频路径，弹层点选即关闭，不给默认路径加步。计算器键盘、账户 / 模板 / 对方 / 描述 / 备注交互一律不动。
- **非破坏性**：零改分类表数据、零改表结构、`version=5` 不变、`Migrations.ALL` 仍空 → **v0.2.34 直接覆盖安装生效**，不需要导出 / 卸载 / 导入。版本号 `0.2.35`（已核实未占用）。
- **挂账未动**：`ff3-description-field`、`detail-filter-and-transfer-edit`、`detail-scope-and-record-page` 三个 change 仍进行中；第 51 批的 ID 规则、删除策略、分类清单 v2 全部不动。本批按「改变系统行为 + 改 spec 语义」新建 change `record-category-picker-and-root-select`。

### 第 52 批实测
代码 `014f9da96bad`（parent `0639814b9613`，main HEAD）→ push CI `45125148563` **六静态检查 / 单测 / debug APK 编译 / release lintVital / 校验值全部 success，仅「上传 Debug APK」失败**（与第 51 批同因：库内累积 200+ 个 Actions artifacts，配额耗尽；`overwrite: true` + `retention-days: 7` 仍无法上传，与本批代码无关）。
release `45125186214` **success**（R8 编译 / 整理产物 / 上传 Release APK / 建 tag `v0.2.35` / 坚果云全部 success）→ GitHub Release 附件 `offline-ledger-v0.2.35-release-014f9da9.apk` **3,039,702 B**；Actions artifacts 因同类配额问题为 0（该步骤 `continue-on-error`），**包以 Release 附件与坚果云为准**。
**装包**：本批零改分类表数据、零改表结构、`version=5` 不变 → **v0.2.34 直接覆盖安装生效**，MUST NOT 再要求导出 / 卸载 / 导入（那是第 50/51 批的破坏性顺序）。
**CI 全绿不替代真机**：默认快捷栏 7 个启用一级、「分类 ›」分组 chips 与搜索、停用项角标、460dp 弹层高度、一级选中底色、再点一级收起、编辑停用分类历史流水、筛选面板停用项可选，十项列在 tasks §7 待验（§1）。
**后续运维**：`offline-ledger-debug-apk` 产物清理 pattern 仍缺失，push CI 会持续红在 artifact 上传；本批不擅自删仓库产物、不改 workflow，留待单独一轮处理。

## 第 53 批（运维）：清理 Actions 制品配额并解除清理死锁

- **起因**：第 51/52 批 push CI 连续两次「六静态检查 / 单测 / debug APK / lintVital 全绿，仅上传 Debug APK 失败」。使用者要求清理 debug 产物，避免下次再报错。
- **远端核查（2026-11-23）**：共 **208 个 artifacts / 2,208.6 MB**；其中 `offline-ledger-debug-apk` **81 个 / 1,931.4 MB（87%）**，第二大是 `offline-ledger-mapping-v*` 约 40 个 / 约 140 MB，其余为各版本 release / metrics。`overwrite: true` 在配额耗尽时同样被服务端拒绝，所以同名 debug 包一路累积到 81 个。
- **根因不是代码，是清理死锁**：`.github/scripts/cleanup-artifacts.sh` 本身是通用 `--pattern` 实现，**不需要改脚本**；缺的是调用方。
  - `build.yml` 的「上传 Debug APK」**从来没有任何清理步骤**，`retention-days: 7` 也没兜住（累积期每天多个 push）；
  - `release.yml` 虽有清理，但只清 `offline-ledger-release-v*`，且挂 `if: steps.upload_release_apk.outcome == 'success'` —— 配额满时上传失败 → 清理被跳过 → 越满越不清理。前两批文档写「给 cleanup-artifacts.sh 补 debug pattern」措辞不准，本批更正为「补调用方」。
  - 脚本注释早已写明「配额打满时 upload-artifact 会被服务端直接拒绝，表现为上传步骤 1 秒失败而前面编译全绿，错误信息不提配额」，与现象完全吻合。
- **立即清理**：按 `--keep 1` 语义保留最新 1 个（`14752329305`，2026-11-22T11:37:21Z，14.7 MB），**删除其余 80 个，释放 1,917.3 MB**，80/80 成功、0 失败。清理后总量 **128 个 / 334.2 MB**、debug 仅 1 个。
- **防复发（改 workflow，不改脚本）**：
  - `build.yml` 在上传**之前**新增 `- name: 清理历史 Debug 制品`，`if: always()` + `continue-on-error: true`，调 `--pattern 'offline-ledger-debug-apk' --keep 1`。「无条件 + 排在上传之前」是关键：配额满时上传失败不影响清理，且上传前已释放空间，本次就能成功；稳态恒定 1 个。
  - `release.yml` 清理步骤的 `if:` 从 `outcome == 'success'` 改为 `always()` + `continue-on-error: true`，并把 `offline-ledger-mapping-v*` 一并纳入 `--keep 5`。只清 debug 不够：mapping `retention-days: 90`，保留期内仍会堆到数百 MB 再次打满。
- **不改**：App 源码、分类逻辑、spec、`defaultVersionName`（仍 0.2.35）、`.github/scripts/cleanup-artifacts.sh`、签名与坚果云链路。**本批不出新包**，因为 App 代码零改动。
- **不建 change**：纯 CI 运维，不改变系统行为、不涉及 spec，按流程只记运维批次。
- **验证方式**：本批 push 自身会触发 build CI，直接观察「清理历史 Debug 制品」与「上传 Debug APK」两步是否都 success，比静态检查更有说服力。

### 第 53 批实测（含根因更正）

**重要更正**：本批起初判断「debug 制品堆积导致配额满」，清理后上传仍失败，深挖发现**配额是账号级、不是仓库级**。远端实测（2026-11-23）：

| 仓库 | artifacts | 占用 |
|---|---|---|
| `wzzhuz/dayscounter` | 132 个（71 debug + 61 release） | **1289.2 MB** |
| `wzzhuz/offline-ledger` | 127 个 | 277.2 MB |
| `wzzhuz/rice-cooker-schedule-android` | 32 个 | 90.7 MB |
| `lifelog` / `rice-cooker-schedule` | 6 个 | 8.4 MB |
| **账号合计** | **297 个** | **1.63 GB** |

GitHub Actions 存储与 Packages 共用账号级额度；`dayscounter` 一个仓库占 1.29 GB，本仓库从 2.21 GB 清到 277 MB 仍不足以腾出上传空间。验证链：删 80 个 debug（释放 1917 MB）→ push CI `45127506505` 清理步骤 success、上传仍 failure → 删最后 1 个同名 debug（debug 数归 0）→ rerun attempt 2 仍 failure → 全新 workflow_dispatch run `45129359205` 仍 failure。**排除同名 artifact 冲突、排除本仓库配额、排除 rerun 特有因素**。

因此「让上传真正成功」需要**账号侧**处理（清理 `dayscounter` 等其它仓库制品，或升级套餐），超出「清理 offline-ledger debug 产物」的授权范围，本批**不擅自删其它仓库**，交回使用者拍板。

仓库内可交付的兜底：`build.yml` 上传步骤加 `id: upload_debug_apk` + `continue-on-error: true`，并新增「Debug 制品上传失败告警」写 Summary。Debug APK 只是编译校验产物、不是交付物（正式包以 Release 附件 + 坚果云为准），上传失败 MUST NOT 把整条 CI 染红，也不静默。这与 release.yml 三个上传步骤的既有做法一致。

代码 `6c0816d998b7`（parent `747b50c2ae`）：清理 80 个 + 补无条件清理调用 + 解除 release 死锁；本批兜底改动待第二次 commit。清理后本仓库 **127 个 / 277.2 MB、debug 0 个**。

**兜底实测（如实区分 conclusion 与 outcome）**：兜底 commit `f3bd56bbec18`（parent `6c0816d998b7`）→ push CI `45130602117` **completed success**，六静态检查 / 单测 / debug APK / lintVital / 校验值 / 清理 / 上传 / 告警 / 摘要全部 success，**整条 CI 不再报红**。
但**上传并未真正成功**：该 run 的 artifacts 数为 **0**，账号内 `offline-ledger-debug-apk` 仍为 0 个、本仓库仍 127 个 / 277.2 MB。step 13 的 `conclusion: success` 是 `continue-on-error: true` 的染绿效果，真实 `outcome` 仍是 `failure` —— 旁证是 `if: steps.upload_debug_apk.outcome == 'failure'` 的告警步骤**确实执行了**（若 outcome 为 success 该步应 skipped）。这正是 §7.33 记过的 `continue-on-error` 坑，本批是**有意使用且配了 Summary 告警**，不静默、不假装产物存在。
**账号级根因未消除**：`dayscounter` 仍是 132 个 / 1289.2 MB，未动。要真正恢复上传需账号侧处理，超出本批授权范围，交回使用者拍板。
**结论**：用户诉求「避免下次还报错」已达成（CI 不红 + 明确告警）；「Debug APK 真的传到 Actions 制品」未达成，且它不是交付物（正式包以 v0.2.35 Release 附件与坚果云为准）。两者 MUST NOT 混为一谈。

## 第 54 批（运维）：账号级统一制品清理（debug 1 / release 3）

- **授权**：使用者 2026-11-23 明确授权「清理其他仓库，同步更新所有仓库的清理机制，debug 产物只保留最近一个，release 只保留最近 3 个」。
- **根因闭环**：第 53 批只清了 `offline-ledger` 本仓库，但 Actions 存储配额是**账号级**、与 Packages 共用。拉全 14 个仓库才发现 `wzzhuz/dayscounter` 一个仓库占 132 个 / 1289.2 MB（71 debug + 61 release，且它的 build.yml **从来没有任何清理步骤**），账号合计 297 个 / 1665.6 MB，所以本仓库清到 277 MB 上传仍失败。
- **立即清理（全账号 14 仓）**：按目标稳态删除 **274 个 / 1580.4 MB**，0 失败。

  | 仓库 | 清理前 | 清理后 | 构成 |
  |---|---|---|---|
  | `dayscounter` | 132 / 1289.2 MB | **4 / 35.1 MB** | debug 1 + release 3 |
  | `offline-ledger` | 127 / 277.2 MB | **6 / 20.3 MB** | release 3 + other 3 |
  | `rice-cooker-schedule-android` | 32 / 90.7 MB | **7 / 21.2 MB** | debug 1 + release 3 + other 3 |
  | `lifelog` | 4 / 7.6 MB | 4 / 7.6 MB | 已在稳态 |
  | `rice-cooker-schedule` | 2 / 0.8 MB | 2 / 0.8 MB | 已在稳态 |
  | **账号合计** | **297 / 1665.6 MB** | **23 / 85.1 MB** | |

- **统一机制（8 个自有仓库各一次 commit）**：`dayscounter` / `lifelog` / `my-app-workflow` / `offline-ledger` / `rice-cooker-schedule` / `rice-cooker-schedule-android` / `secrets-sync` / `wzzhuz.github.io`。
  - 新增 `.github/scripts/cleanup-artifacts.sh`：**自动分类模式**（无参数 = debug 留 1 / release 留 3 / 其它留 3），并保留旧 `--pattern/--keep` 显式模式，兼容既有调用。`category_of` 用大小写不敏感的 `case` glob：`*debug*` / `*release*`|`*-preview-*`|`*v*.apk` / 其它。按 `created_at` 倒序遍历 + 分类计数，等价于「各类别只留最近 N 个」。
  - 新增 `.github/workflows/artifact-cleanup.yml`：`push`（每次 CI 前后收敛）+ `schedule` 每天 + `workflow_dispatch`；显式 `permissions: actions: write`；`concurrency` 防堆积；`if: always()` 精神由「无条件 job」保证。
  - 已有内联清理统一口径：`offline-ledger/release.yml` 的 release / mapping `--keep 5` → **3**；`lifelog/release.yml` 的 `lifelog-v*` `--keep 5` → **3**；`lifelog/build.yml` 清理步骤的 `if: outcome == 'success'` → **`always()` + `continue-on-error: true`**（它原本和第 53 批踩的是同一个死锁）。
  - 版本号、签名、坚果云、应用源码一律未动，**本批不出新包**。
- **6 个 fork 不改代码**（`BeeCount` / `docker_image_pusher` / `HideCamera` / `interview_internal_reference` / `keepass2android` / `legado`）：它们 **0 个 artifacts**，workflow 属上游代码，`legado` 还有 `autoupdatefork.yml` 会自动同步覆盖，写入只会制造冲突。它们的 artifacts 仍纳入本次全账号清理范畴（当前为 0）。若使用者要求连 fork 一起加机制，需单独授权。
- **关键教训**：查配额 MUST 先确认作用域是账号级；`if: outcome == 'success'` 是死锁开关；`continue-on-error` 会把 `conclusion` 染绿但 `outcome` 仍为 failure，判断产物是否真在只能查 artifacts 列表。

### 第 54 批实测

**8 个自有仓库 commit**：`dayscounter c10449a7` / `lifelog 9634c914` / `my-app-workflow 54505feb` / `offline-ledger 51bd6341` / `rice-cooker-schedule 48250f41` / `rice-cooker-schedule-android fcf14c6c` / `secrets-sync 48215452` / `wzzhuz.github.io 975a4670`，逐 blob 校验通过、零删除。

**清理机制全部实测生效**：8 个仓库的 `artifact-cleanup.yml` 均由 push 触发并 **completed success**，一次验证了 YAML 语法、`gh`/`jq` 可用性、`actions: write` 权限、自动分类逻辑四处。

**上传真的恢复了（其它仓库）**：`dayscounter-debug`（09:35）、`生活手记-debug-apk`（09:34）、`rice-cooker-debug-apk`（09:35）均为清理后新建，证明账号级配额腾出后 `upload-artifact` 不再被服务端拒绝。这是第 53 批「清了 1.9 GB 仍失败」的反证闭环。

**一处如实登记**：`offline-ledger` 的 run `45147962536` 虽 completed success，但该 run 的 **artifacts 为 0**，且「Debug 制品上传失败告警」步骤 `conclusion=success`（执行了，而非 skipped）→ `upload_debug_apk.outcome` 仍为 failure，`continue-on-error` 染绿。**本仓库的 Debug APK 仍未真正上传成功**，与其它仓库表现不一致，原因未定（日志在 fine-grained token 下 403 不可读，疑似与同仓两个清理步骤并发 / 同名 artifact 状态有关）。CI 不报红 + 明确告警的兜底仍有效，正式包 v0.2.35 的 Release 附件与坚果云不受影响。此差异列为后续观察项，不包装成「全部修好」。

**账号级占用（验证后）**：清理完毕瞬时 23 个 / 85.1 MB；随后各仓 push CI 重新产出，回升到约 30 个 / 114.3 MB，仍在统一口径的稳态内（每个类别 ≤1 / ≤3）。

## 第 55 批（v0.2.36）：分类管理三页借鉴一羽布局与交互

- **起因**：使用者真机对照「我们的分类管理效果差一羽一大截」，并纠正本会话把「BeeCount 网格卡片留白大」外推成「等宽图标网格不能用」。撤回外推，按一羽组织方式重排三页；change `refine-category-management-pages`，四件套已推 `6e188f2` / 勾选回写 `0b1d00d`。
- **使用者显式放行的两条文档约束**（留痕，防止后续会话再拿旧口径拦）：
  1. **彩色图标**：原「中性单色圆 + 首字 / 彩色图标目录一律不进清单」收窄为**仅限分类管理三页**（列表 / 排序 / 分类选择）；记一笔、明细、账户详情、统计页口径不变。
  2. **二级布局**：原「MUST 用 `WrapRow` 自适应 chips、MUST NOT 等宽列」改为列表页 5 列等宽图标网格、排序页二级临时一维列表。
- **方案 A 已收口（使用者确认「按 Tabler 来」）**：图标采用 **Tabler Icons outline v3.34.0**（pin tag / MIT / `Copyright (c) 2018-2026 Tabler`），从 zipball 只取 90 个 SVG 转成 `res/drawable/ic_cat_*.xml`，覆盖**全部 106 个预置分类**；编译期 `when` 常量表 + `else` 回退中性圆 + 首字。零新增 Gradle 依赖、零 AAR、零运行时网络；MIT 全文与版权声明放 `assets/NOTICE` 随 APK 分发。MUST NOT 复制一羽视觉资产、MUST NOT 引 `material-icons-extended`。
- **图标配色**：纯 JVM `CategoryIconTints` 编译期常量表，**11 种低饱和领域色、按一级分组同组同色**，排序页降为中性灰；`colorHex` 一字未动、仍只管统计扇区。`default_categories.json` 只补 `iconKey` 一个可选字段，106 行数据语义逐字段零改动。
- **覆盖安装兜底（零改表承诺的落地）**：`SeedCatalog` 只在全新安装 `onCreate` 写 `iconName`，老库 106 行仍为空串；`CategoryGlyph` 在 `iconName` 为空时按 `CategoryIconKeys.keyOfPreset(id)` 纯内存回查，v0.2.35 覆盖安装也能立刻显示图标，零写库 / 零 Entity 变更 / 零备份 DTO 变更。
- **落地**：列表页蓝底 TopBar + 收支 Tab + 二级 5 列等宽网格 + 末格「添加子类」+ 停用小角标 + 底部胶囊「已归档 / 停用项管理」+ 大 `+` FAB；新增独立「分类排序」页（横排 5 列长按拖 / 二级点按拖 / 保存与放弃 / `draftOrder` 与 `sortOrder` 分离）；新增「分类选择」页（借一羽添加子类页版式：父级预填 + 分组导航 + 等宽网格 + 搜索 + 继续添加 + 重名校验），同版式用于停用项批量启用。
- **零回归**：不改记一笔 / 明细 / 统计页分类标识；不改 `colorHex`（仍参与 `ChartData` 扇区取色与 3 处 DAO 投影）；不改两级模型、ID 规则、启用/停用语义、备份整表替换、统计口径；上移 / 下移保留作 TalkBack 兜底；一级列表仍竖排 `LazyColumn`（横排只在排序页）。
- **零改表**：`AppDatabase.version = 5` 不变、`Migrations.ALL` 仍空 → **v0.2.35 直接覆盖安装生效**，不需要导出 / 卸载 / 导入。版本号 `0.2.36`（已核实未被 tag / release / artifact 占用）。
- **依赖**：无新增第三方库；`rememberReorderableLazyGridState` 已在 `sh.calvin.reorderable:reorderable:3.1.0` README 查证存在。等宽网格用稳定 `Row + weight(1f)`，MUST NOT 用 `LazyVerticalGrid` 嵌套或 `FlowRow`（T12）。
- **CI 全绿不替代真机**：活文档 §8 第 55 批新增 16 项真机待验（网格观感、长名省略、⋮ 菜单七项、排序手感与放弃零写库、连续添加、图标缺项兜底、记一笔零回归、横屏，以及 90 个 Tabler 图标在 40dp 圆底的辨识度、11 种领域色是否杂乱、浅底对比度、排序页灰阶、收入统一绿语义冲突、覆盖安装按 id 显示图标），§8 既有项整体继承。

## 第 56 批（v0.2.37）：排序页两态互斥 + 共享只读分类选择器

- **起因**：v0.2.36 真机体验「不符合预期」，使用者要求「排序页面直接参考一羽记账」「添加子类页面可以作为记一笔里面还有筛选里面分类选择页面的样式，只是把添加的输入框和按钮去除」；随后追问替代库与「拖一级时二级折叠、两级互不干涉、横排一个变横排 5 个」。
- **第 3 轮技术澄清（已推 `6b87ad0`）**：核查 `sh.calvin.reorderable:3.1.0` README，确认 `LazyVerticalGrid` 与 `rememberReorderableLazyGridState` 原生支持，收回上一轮「库只支持一维、二维 MUST 自写 `pointerInput`」的错误外推；一羽为闭源 App，实现查不到，只按截图与使用者观察推断为两态互斥。四件套按此改完并经使用者确认。
- **排序页**：`CategorySortScreen.kt` 完整重写为两态互斥。一级态单个 5 列 `LazyVerticalGrid` 可拖、二级区折叠不渲染、无 span / header；点一级进二级态，外层一级区改非惰性 `Column + Row(weight)` 静态冻结，白卡插在选中行之后，卡内第二套 `LazyVerticalGrid(Fixed(5))` 拖二级。两套 grid state 各管各段 `draftOrder`，`onMove` 由 ViewModel 同步 `_state.update`；上移 / 下移改为 `semantics.customActions` 的 TalkBack 兜底。
- **共享只读选择器**：新增 `ui/components/CategorySelectSheet.kt` 的 `CategorySelectDialog`（顶部搜索 + 左一级分组导航 + 右 5 列彩色图标格），无名称输入、无「继续添加」/ 保存按钮、无写库路径；记一笔 `AllCategoriesDialog` 与筛选 `PickRow("分类")` 接入同一组件，`CategoryPickerScreen` 零改动。停用项可选带角标、归档项不进、搜索跨组平铺、二级 label 经 `CategoryDisplay`，点选即刻关闭。
- **口径再放宽一次**：彩色 Tabler 图标扩到两个全量选择器；记一笔快捷栏 / 明细列表行 / 账户详情 / 统计页仍中性圆 + 首字，`colorHex` 一字不动。筛选层级区分由 `r:` / `c:` 字符串前缀改为 `CategorySelectChoice.level` 显式字段，spec / design / tasks 三处表述已同步回写，不留矛盾文本。
- **零改表 / 零回归**：`AppDatabase.version = 5`、`default_categories.json`、`colorHex`、三处 DAO 投影、备份整表替换一律未动；零新增 Gradle 依赖；`defaultVersionName` → `0.2.37`，v0.2.36 可直接覆盖安装。
- **CI 全绿不替代真机**：活文档 §8 第 56 批新增 13 项真机待验（两态折叠与互不干涉、拖拽手感与让位、draft 不丢、无闪烁、白卡限高与滚动边界、360dp 窄屏、两处选择器同组件、彩色与中性圆并存观感、停用项兜底与回填、原分类选择页零回归、覆盖安装 / 全新安装、记一笔零回归），§8 既有项整体继承。归档仍需使用者明确说「可以归档」。

### 第 56 批实测（v0.2.37 出包）

- 代码 `2410a91c2c0d`（parent `6b87ad0bbc76`），11 文件逐 blob 校验 PASS；push CI `45211406554` 全绿。
- 出包 CI `45289759199` completed success；tag `v0.2.37` → `2410a91c`；Release 附件
  `offline-ledger-v0.2.37-release-2410a91c.apk`（3,137,307 B，
  SHA256 `a99320ac93c1bded6bc7109a6b392987191bacb678c10a58a6f7ac65a292db77`），
  API `state=uploaded` + `digest` 与 notes 一致。debug.keystore 签名，v0.2.36 可直接覆盖安装。
- **一处如实登记**：本批 Actions artifacts 仍为 0（三个上传步骤 outcome=failure 被
  `continue-on-error` 染绿，「制品上传失败告警」步骤实际执行可反证），`mapping.txt`
  未随制品留存；Release 附件与 tag 不受影响，按 §7.33/§7.35 只认 Release assets。
  坚果云落盘因 job logs 403 无法确认，不写成已上传。归档仍需使用者明确说「可以归档」。

## 第 57 批（v0.2.38）：排序页整格拖动 + 选择器裸名显示

- **起因**：v0.2.37 装机后使用者提 5 条交互问题，拍板 Q1A / Q2A / Q3A「直接做完出包」。
- **核查补正**：v3.1.0 README「Use with Card」段明确给出 `clickable` 与 `draggableHandle` 共存的正式写法（共享 `MutableInteractionSource`），并确认 `onDragStarted` / `onDragStopped` 存在；上一轮 design 里「共存需额外处理」的半截结论补齐。
- **排序页**：一级 / 二级**整格**挂拖动手柄，删掉所有「≡」小手柄；一级短按展开与长按拖动共享 `MutableInteractionSource`。Q1A：二级态静态一级格 `detectTapGestures(onLongPress)` 长按即自动收起二级区，删「收起，排一级」按钮（左上返回键、再点已选中一级格仍可收起）。末行不足 5 列补 `weight(1f)` 空占位。Q2A：删白卡小字、顶部冻结说明、一级态两行引导、底部拖动提示，只留分组标题与「保存 / 放弃」；白卡底色 `0xFFEAF0FF`、二级格白底。
- **选择器 Q3A**：`CategorySelectChoice` 拆 `tileName`（网格 / 搜索结果显示裸名）与 `displayLabel`（回填筛选 `displayName` 仍经 `CategoryDisplay` 拼父级）；第 29 批四处共用显示名契约零改动，`CategoryDisplay` / `CategoryPickerScreen` 零改动。
- **零改表 / 零回归**：`AppDatabase.version = 5`、`default_categories.json`、`colorHex`、三处 DAO 投影、备份整表替换未动；零新增 Gradle 依赖；`defaultVersionName` → `0.2.38`，v0.2.37 直接覆盖安装。
- **CI 全绿不替代真机**：活文档 §8 第 57 批新增 6.24–6.28 五项真机待验（尤其删光提示后的可发现性、整格短按/长按共存手感），既有项整体继承。归档仍需使用者明确说「可以归档」。

### 第 57 批实测（v0.2.38 出包）

- 代码 + 四件套 `ae8f1b3a013e`（parent `6783e847d148`），11 文件逐 blob 校验 PASS；push CI `45309157969` 全绿。
- 出包 CI `45309689913` completed success；tag `v0.2.38` → `ae8f1b3a`；Release 附件
  `offline-ledger-v0.2.38-release-ae8f1b3a.apk`（3,137,307 B，
  SHA256 `daa22436b525d7662cab8ff94cf2c60f90b96f2f7b9e944f49a709628b98f7d6`），
  API `state=uploaded` + `digest` 与 notes 一致。debug.keystore 签名，v0.2.37 可直接覆盖安装。
- **正向差异**：本批 Actions artifacts **3 个**（release / mapping / metrics），全步骤无染绿，
  `mapping.txt` 随制品留存；v0.2.37 那次为 0。坚果云落盘因 job logs 403 无法确认。
- 归档仍需使用者明确说「可以归档」。

## 第 58 批（v0.2.39）：选择器 5→4 列 + 左右分区收窄

- **起因**：v0.2.38 装机后使用者提两条（4 字名在 5 列下只显示 2 字、左右留白大），拍板 1A 2B「直接做完出包」。
- **1A**：`CategorySelectSheet.kt` 右侧 `columns = 5 → 4`。360dp 下右侧原实得 208dp、5 列每格文字区 37.6dp（4 字需约 48dp）；改 4 列后 48dp。
- **2B**：Dialog 外 padding 12→8、Surface 内 padding 12→8、左导航 `width(96.dp) → 80.dp`、Spacer 8→4；右侧实得 244dp、4 列每格约 57dp，4 字名完整可见。左右水平缝隙本就只有 8dp，根因是累计 152dp 非内容宽度占屏 42%。
- **依据**：第 1 轮 design「风险与缓解」表早已登记「窄屏 5 列图标过小 → 降为 4 列」，代码未落地，本批补齐；原「分类选择」页 `CategoryPickerScreen` 本来就是 `columns = 4`。
- **范围锁定**：只改一份只读组件（5 处数值）。分类管理列表页 `columns = 5`、排序页两套 `GridCells.Fixed(5)`、`CategoryIconGrid` / `CategoryTileCell` 格高与行距（三页共用，2B 明确不含紧凑格高）、`CategoryDisplay`、记一笔快捷栏、明细列表行、统计页、`colorHex`、`default_categories.json`、`AppDatabase.version = 5` 全部零改动；零新增 Gradle 依赖；`defaultVersionName` → `0.2.39`，v0.2.38 直接覆盖安装。
- **边界**：7 字长名（赡养·代缴水电）在 57dp 下仍单行省略，MUST NOT 换行撑高格子；跨组搜索同名二级归属缺失仍沿用 Q3A 口径。
- **CI 全绿不替代真机**：活文档 §8 第 58 批新增 6.29 待验，既有项整体继承。归档仍需使用者明确说「可以归档」。

### 第 58 批实测（v0.2.39 出包）

- 代码 + 四件套 `bd2e3d7cc71c`（parent `dbc7b256bfe9`），9 文件逐 blob 校验 PASS；push CI `45315914873` 全绿。
- 出包 CI `45316364221` completed success；tag `v0.2.39` → `bd2e3d7c`；Release 附件
  `offline-ledger-v0.2.39-release-bd2e3d7c.apk`（3,137,307 B，
  SHA256 `faf49588ddfeaa0416e253722c1d2e28015c93859671c77455f2da5f7ad9abc7`），
  API `state=uploaded` + `digest` 与 notes 一致。debug.keystore 签名，v0.2.38 可直接覆盖安装。
- Actions artifacts 3 个（release / mapping / metrics），全步骤无染绿，`mapping.txt` 随制品留存。
- **操作偏差**：dispatch 因工具层 502 被重放两次，第二个 run `45316726495` 排队 pending 时主动 cancel（202 →
  `completed cancelled`），未产生第二次 tag 重建或 Release 覆盖；资产 digest 单一。
  教训：204 空响应的触发请求超时后 MUST 先查 runs 列表再决定是否补发，MUST NOT 直接重发。
- 归档仍需使用者明确说「可以归档」。

## 第 59 批（v0.2.40）：资产账户列表 + 新建账户页借鉴一羽

- **起因**：三张一羽截图（资产列表 / 添加资产账户 / 选择银行），使用者要求「账户列表和新建账户的 ui 也参考一羽记账优化一下」，拍板 Q1A Q2A Q3A。
- **纠错**：上一轮把参考图误当本 App 现状。核查 `AssetsScreen.kt` 后确认本 App 原为白底按币种小计 Card + `AlertDialog` 新建 + 12 个 `FilterChip` 模板，无账户图标 / 无银行 sheet / 无备注隐藏默认字段。本批以代码事实为准。
- **Q1A 列表**：蓝底顶部身份区（仍逐币种显示资产 / 负债 / 小计，MUST NOT 跨币种单一净资产）、分组标题蓝色小竖条 + 账户数、账户行改「`CategoryIcon` 中性圆 + 名称/卡号后四位 + 金额右对齐 + 信用负债『无欠款 / 可用 X』副文案」；5 个横排 `MiniAction` 删除、7 项操作收进单「⋮」；拖柄改整格长按、去掉「≡」；纯行 + `ListDivider()` 保留。
- **Q2A 新建**：`AddAccountDialog` 改近全屏 `Dialog`（蓝底 TopBar / 分组小竖条 / 图标名称小卡 / 底部大保存），字段与顺序、`onConfirm` 签名、`AssetsViewModel.addAccount` 零改动；不新增备注 / 隐藏 / 默认使用 / 图标字段，不升表、不加路由。
- **Q3A 模板 sheet**：新增只读 `AccountTemplateSheet`（按 `group` 分组 + 2 列图标格 + 「自定义名称」入口 + 纯 `onPick` 回填），取代主表单 chips；基线「快捷账户模板替代预制账户行」整块 MODIFIED，语义全保留。不引入 IconFont 在线 PNG（需 `project.md` 显式授权联网，本批不申请）/ 字母索引 / 银行 logo。
- **硬约束兼容**：`reporting-ui` 按币种分组且移除单一净资产总数、账户行纯行不用 Card、4 Tab 不变、`money-precision` 口径、无 `INTERNET` 权限、零表结构变更，逐条核对见 design.md。
- **零回归**：`AssetsViewModel` / `AccountEntity` / `AccountTemplates` / `LedgerApp` / `EditAccountDialog` / 记一笔 / 明细 / 统计 / 分类 / 对账零改动；零新增依赖；`defaultVersionName` → `0.2.40`，v0.2.39 直接覆盖安装。
- **CI 全绿不替代真机**：§8 第 59 批新增 6.30–6.34 待验，既有项整体继承。归档仍需使用者明确说「可以归档」。

### 第 59 批实测（v0.2.40 出包）

- 代码 + 四件套 `7b4515fba3d5`（parent `9577ad6f4378`），10 文件逐 blob 校验 PASS；push CI `45329111449` 全绿。
- 出包 CI `45329663851` completed success；tag `v0.2.40` → `7b4515fba3d5`；Release 附件
  `offline-ledger-v0.2.40-release-7b4515fb.apk`（3,137,307 B，
  SHA256 `4c1474fbd2625adfd19258713ba1a9d70e69a77aeb9c8e71901df1d41166c9e1`），
  API `state=uploaded` + `digest` 与 notes 一致。debug.keystore 签名，v0.2.39 可直接覆盖安装。
- Actions artifacts 3 个（release / mapping / metrics），全步骤无染绿，`mapping.txt` 随制品留存。
- **操作偏差（第二次同类）**：工具层 502 重放整个触发调用，dispatch 发出两次，
  第二个 run `45329992971` pending 时主动 cancel（202 → `completed cancelled`），
  未产生第二次发布；资产 digest 单一。教训升级：触发与轮询 MUST 拆成独立调用，
  脚本内幂等不足以抵抗整体重放。
- 归档仍需使用者明确说「可以归档」。

