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

### 三条铁律（任何改动不得违反）

1. **数据是你的** —— 不声明 `android.permission.INTERNET`，不引入含网络行为的依赖，
   关闭系统自动备份并排除数据库，本地加密 + 应用锁，导出文件默认加密，数据随时可带走
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

### v1 明确不做（v1.1 清单，不得悄悄加回）
- 模板 / 周期记账
- 桌面快捷方式、小组件、通知栏磁贴
- 预算与超支提醒
- 退款 / 报销成对模型（`linkedRecordId` 字段可预留，功能不实现）
- 多币种与跨币种混算（字段预留，v1 报表不混算）
- CSV 导出（默认关闭）、账单导入 / 自动记账
- 三级及以上分类

### 永久不做
- 云同步、账号体系、多账本 / 成员 / 共享账套
- 广告、统计埋点、崩溃上报、远程配置
- `INTERNET` 权限及任何网络请求
- 物理删除有流水的账户 / 分类（改为 `isArchived` 归档）
- `fallbackToDestructiveMigration`（真实账本必须写显式 `Migration`）
- 用「记一笔虚拟支出」做余额校准（改为独立 `BalanceAdjustment`）
- 「总资产 − 总负债」作为净资产计算式

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

- 本轮（初始设计归档）**不建 `.github/workflows/`**，避免 fine-grained token 推 workflow 的 403
- `build.yml`（push 编译 debug）与 `release.yml`（手动触发编译 release）
  SHALL 在阶段 0 / 出包阶段的 change 中建立
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
| 5 | 完整备份与设置（加密导入导出、应用锁、分类管理、账本健康、偏好）；**CSV 归属确认：v1 不做，留 v1.1** |
| 6 | v1.1 增强 |

**v1（阶段 0–5）已全部完成并归档**；出包阶段单独提 change，不属于 v1 功能范围。

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
