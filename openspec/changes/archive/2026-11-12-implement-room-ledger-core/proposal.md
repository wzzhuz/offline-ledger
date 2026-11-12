# 实现 Room 账本核心数据层

> **本文件是 change 的上下文入口。**
> 新会话只读这一份文件，就应当能还原本次改动的全貌，无需重新讨论。

## 为什么改

**背景（使用者原话与项目现状）**：

- 阶段 0 已交付可编译的 Android 工程骨架，CI 三次运行最终两次 success，
  55 个文件经远端 sha 校验一致，产出约 21.5 MB 的 debug APK
- 使用者指令：「**开始阶段1**」，并确认「**按推荐来**」——
  即先归档阶段 0、阶段 1 接入 SQLCipher 加密
- `openspec/project.md` 的阶段 1 定义为：Room 全部 Entity / DAO / Callback /
  索引 / Migration、Repository 事务 + 重算 + 自检、两级分类解析、
  **最小 JSON 导出**、单元测试
- 原始诉求里最关键的一句：「**资产数据是高度敏感数据，不信任第三方**」，
  以及 Firefly III 「差 1 分钱、核算太费事，最后记一笔虚拟支出」的真实痛点

**问题**：目前只有 `Cents` / `LedgerClock` / `BackupEnvelope` 三个基础类型和占位页，
「账要算得准」还停在纸面。没有表结构、没有事务、没有余额重算，
后续所有页面拿到的余额都无从校验；数据库也仍是明文（SQLCipher 依赖在阶段 0
已声明但未被使用），第一条铁律尚未真正落地。

## 做什么

1. 落 5 张 Room 表 + 索引：`accounts` / `categories`（两级）/ `records`
   （冗余 `localDate` `yearMonth` `rootCategoryId`）/ `balance_adjustments` / `trash`
2. 落 DAO、TypeConverter、`AppDatabase`（version 1）、预置中文两级分类与默认账户的
   `SeedCallback`、显式 Migration 落点
3. 接 SQLCipher：`SupportOpenHelperFactory` + Android Keystore 派生的数据库口令
4. 落 `LedgerRepository`：所有写操作 `withTransaction`，末尾重算余额；
   篡改后冷启动自检自动修复并写审计日志
5. 落 `LedgerEngine`（纯 JVM 唯一余额算法）、`CategoryTree`（两级解析）、
   `StatRules`（全局唯一统计口径）
6. 落最小 JSON 导出/导入：`BackupFile` 顶层信封 + payload DTO（不改阶段 0 的
   `BackupEnvelope` 头部）、导入事务化 + 导入后强制重算
7. 落纯 JVM 单元测试：9 条账务路径、分类合计一致性、统计口径、备份往返、预置数据
8. 落 `check_transaction_annotations.py`：静态检查所有写方法都在事务内
9. 同步 README、`AGENTS.md` 与阶段状态

## 不做什么

- ❌ 不写任何 UI 页面（记一笔 / 明细 / 资产 / 统计属于阶段 2–4）
- ❌ 不做对账 UI、阈值分流、金额反查（阶段 3）
- ❌ 不做图表与下钻（阶段 4）
- ❌ 不做完整加密导出、CSV、应用锁、SAF 文件选择器 UI、分类管理界面（阶段 5）
- ❌ 不实现 v1.1 清单：模板 / 周期账 / 预算 / 退款报销 / 多币种混算 / 三级分类
- ❌ 不改 `wzzhuz/my-app-workflow`、不改 `docs/设计规格书_v2.md` 历史快照
- ❌ 不在沙盒内尝试编译 APK 或跑 Android instrumentation（无 SDK / 模拟器）
- ❌ 不使用 `fallbackToDestructiveMigration`

## 对齐结论

> 阶段 0 在对话里产出，五项齐全才进入写作。

1. **要解决什么问题**
   把「账要算得准」从规格变成可运行的数据库层：Room 表结构与索引、
   `@Transaction` 联动、`rebuildAllBalances()` 自愈、两级分类解析、最小 JSON 导出。
   这决定后续所有页面拿到的余额是否可信，也是「资产状况可见」的数值地基。

2. **范围边界**
   - 改：5 张表 + 索引、DAO/TypeConverter/AppDatabase/SeedCallback/Migrations、
     Hilt DatabaseModule + Keystore 口令、SQLCipher 接入、LedgerRepository 事务与重算、
     LedgerEngine / CategoryTree / StatRules、备份 payload DTO 与最小导出导入、单测、静态检查脚本
   - 不改：任何 UI 页面、对账 UI、图表、阶段 5 功能、v1.1 清单、my-app-workflow、历史快照

3. **关键约束**
   三条铁律不得违反：`Long` 分、统一 `balance += delta`、`净资产 = SUM(balance)`；
   `balance` 是可重建缓存而非真相源；校准用独立 `BalanceAdjustment`、不污染报表；
   全局唯一 `isStatRelevant` 口径；外键 `RESTRICT` + `isArchived` 归档，禁止物理删除；
   显式 `Migration`，禁止 `fallbackToDestructiveMigration`；
   `onCreate` seed 只对全新库生效，后续版本要在 `migrate()` 补预置数据；
   索引必须建（`localDate DESC,id` / `yearMonth,type` / `rootCategoryId,categoryId,yearMonth` /
   `amount` / 账户外键）。
   沙盒无 Android SDK / Gradle / javac，单测与编译只能由 Actions 验证；
   推送用**单次 commit**（逐文件 PUT 实测会触发 28 次 CI）；
   Python 拼 Kotlin 后必跑 `check_kotlin_strings.py`；推完用 API 校验远端 sha。

4. **验收标准**（可测）
   - **GIVEN** CI 跑完 **THEN** `testDebugUnitTest` / `assembleDebug` / `assembleRelease`
     全绿，且 Actions 列表能查到该次运行
   - **GIVEN** 9 条路径（insert / update / softDelete / restore / transfer /
     信用卡消费与还款 / 校准 / 重算 / 导入后重算）**WHEN** 跑单测 **THEN** 全通过
     且净资产前后一致
   - **GIVEN** 手动篡改 `balance` **WHEN** 冷启动自检 **THEN** 自动重算修复并写审计日志
   - **GIVEN** 一级/二级分类各记若干笔 **THEN** 一级合计 = 二级合计 = 总支出
   - **GIVEN** 阶段 1 结束 **THEN** 能导出一份含账户/分类/流水的 JSON 并重新导入，
     导入后余额与流水聚合一致
   - **GIVEN** 推送完成 **WHEN** 用 API 校验 **THEN** 文件 sha 一致、无 token 明文

5. **被否决的方案**
   一句话结论：**所有「跳 change / balance 当唯一真相源 / 校准伪装成支出 /
   主表留 isDeleted / 破坏性迁移 / 阶段 1 顺手做完阶段 5 / 一次性摊开 UI」
   的方案均被否决**，采用「trash 表 + 写后全量重算 + SQLCipher 一次接入 +
   纯 JVM 单测覆盖等价算法」。完整表格见 `design.md`「考虑过的其他方案」。

## 影响范围

**触及的 spec 模块**：`ledger-transactions`（主）、`money-precision`、
`category-stats`、`backup-restore`、`privacy-security`、`build-and-ci`。

**是否需要重新安装包**：否（尚无真实用户数据；数据库文件名固定，
阶段 1 首次装机会建立加密库）

**是否会丢数据**：否（无历史数据；导入采用事务化全量替换）

**行为变化**：项目从「可编译骨架」变为「有真实账本数据层」；
冷启动、每次写操作、每次导入都会自动重算并校验余额。

## 与项目铁律是否冲突

- [x] 不违反三条铁律（本轮兑现「账要算得准」与「数据是你的」的加密部分）
- [x] 不涉及「已确认不做」清单中的功能
- [x] 无冲突

**补充说明**：阶段 0 的 `build-and-ci` delta 已按规范合并进 `openspec/specs/`
后，本 change 才基于干净基线写 delta，避免两个 change 的 delta 叠加。
归档三步（合并 / 移入 archive / 校验）已执行。
