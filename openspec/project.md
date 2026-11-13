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

1. **数据是你的** —— 不声明 `android.permission.INTERNET`，不引入含网络行为的依赖，
   关闭系统自动备份并排除数据库，本地加密 + 应用锁，数据随时可带走。
   **隐私边界（6c-1 使用者澄清）**：不可接受的是「账本数据上传到厂商服务器 / 托管给第三方」；
   **本地导出的文件是明文还是加密由使用者自行决定，明文不构成违规**——
   文件留在用户自己的设备或自选位置，本就掌控在自己手里。
   JSON 备份已实现口令加密，属于已交付能力而非禁令，MUST NOT 倒退成明文
2. **账要算得准** —— 整数分、统一带符号余额、净资产 = SUM(balance)、
   余额是可重建缓存、校准不污染任何报表、全局唯一统计口径
3. **记账不能变慢** —— 10 年 2–5 万条仍要两步记完一笔；
   明细分页 + SQL 聚合，多级分类不得增加记账摩擦

有人提议加云同步、账号体系、埋点或把 v1.1 功能悄悄塞进 v1 时，用这三条立刻拦住。

---

## 技术栈

| 项 | 选择 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material Design 3（禁止 XML 布局） |
| 架构 | MVVM + Clean Architecture（UI → ViewModel → Repository → Room DAO） |
| 本地库 | Room (SQLite) + SQLCipher（自定义 `SupportOpenHelperFactory`） |
| 异步 / DI | Coroutines + StateFlow / Hilt |
| 序列化 | kotlinx.serialization（备份 DTO 与 Entity 分离） |
| 分页 / 图表 | Paging 3 / 原生 Canvas 自绘（环形图、折线、分组柱） |
| 最低版本 | Android 8.0（API 26） |
| targetSdk | **不写死**，跟随开工当年官方最新要求，编码前复核 |
| 默认本地化 | `Asia/Hong_Kong`、默认币种 `HKD`（v1 单币种，`currencyCode` 字段预留） |
| 包名 | 待阶段 0 确定（建议 `com.zwz.offlineledger`） |

**版本组合**：不在设计归档阶段猜 AGP / Kotlin / Compose BOM 的具体版本号。
本仓库无本地编译环境，GitHub Actions 是唯一试错场；
具体版本组合 SHALL 在阶段 0 的 change 中查证官方文档后锁定，并写入该 change 的 `design.md`。

**刻意不引入**：Vico 等第三方图表库（Compose API 跨版本变动大，三种图自绘即可）、
Coil / Glide（会传递引入 OkHttp，与零网络权限冲突）、任何广告 / 埋点 / 崩溃上报 SDK。

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

### 永久不做
- 云同步、账号体系、多账本 / 成员 / 共享账套
- 广告、统计埋点、崩溃上报、远程配置
- `INTERNET` 权限及任何网络请求
- 物理删除有流水的账户 / 分类（改为 `isArchived` 归档）
- `fallbackToDestructiveMigration`（**稳定版前暂缓**：尚无真实账本；
  一旦开始录入真实数据 MUST 重新启用显式 `Migration`，见上节）
- 用「记一笔虚拟支出」做余额校准（改为独立 `BalanceAdjustment`）
- 「总资产 − 总负债」作为净资产计算式
- 三级及以上分类（两级 + `rootCategoryId` 冗余已覆盖统计与下钻需求）
- 小组件与通知栏磁贴（新权限 / 新依赖，与零新增权限基线冲突）
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
  App 仍 MUST NOT 声明 `INTERNET`
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

> 不在 `docs/` 写技术决策。早期「spec + docs 双轨」曾出现
> 「文档写着不用 Room 而代码早已迁移」持续数个版本无人发现：
> 同一件事有两个地方可写，就没有任何机制保证同步。
