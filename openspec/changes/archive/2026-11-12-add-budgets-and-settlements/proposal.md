# 预算与退款报销成对模型（阶段 6b）

> **本文件是 change 的上下文入口。** 新会话只读这一份，就能还原本次改动全貌。

## 为什么改

**背景（使用者原始诉求与项目现状）**：

- 规格书 v2 里点名过一个真实痛点：垫付记了、报销款收回时还得手工对冲，
  否则**年度收入被虚增、分类支出也虚高**；随手记当年主打「退款功能，
  从此支出收入不再虚高」正是这个
- 另一个缺口是「事后看」没有「事前控」：v1 只有月/年报表看占比，
  没有预算这道闸门
- 阶段 6a 已归档：模板、周期记账、桌面快捷方式，`Migration(1→2)` 落地，
  **8 模块 / 149 条 Requirement / 237 个 Scenario、9 个已归档 change**
- `project.md` 的「v1.1 分批计划」把这两项列为 6b「待决策」，
  `reporting-ui`「v1 范围收敛」规定要做 MUST 先显式更新闸门。
  本 change 第一步就是履行它，不是绕过
- **使用者明确决定：真机实测整体推迟到完成度更高后统一装机体验**。
  因此 `Migration(1→2)` 至今未验证，6b 又要叠一次 `Migration(2→3)`

**问题**：v1 / 6a 解决了「算得准、锁得住、愿意记」，但「哪类支出比较多」
这个原始诉求会被垫付报销污染，且没有预算来提前控制。

## 做什么

1. **履行闸门**：把 6b 两项标为「本批实现」；把「小组件、通知栏磁贴」
   **移到 6c 待决策**并写明原因（通知要 `POST_NOTIFICATIONS`、
   小组件要新依赖，与零新增权限/依赖基线冲突）。清单不删，只按批次放行
2. **数据结构**：新增 `budgets` 表（月度分类预算 + 总预算规则）、
   `records` 加 `linkedRecordId`、新增 `RecordType.SETTLEMENT`
3. **首次 `ALTER TABLE` 迁移**：`Migration(2→3)` 显式建表 + 加列，
   `AppDatabase version = 3`，`ALL` 含两个迁移
4. **备份升级**：`BackupSchema.CURRENT = 3`、`MIN_SUPPORTED = 1`，
   payload 加 budgets、`BackupRecord` 加 linkedRecordId；
   **v1 / v2 加密备份仍可导入**
5. **成对模型**：明细页对每笔支出提供「报销/退款」入口，
   生成一条 SETTLEMENT 流水关联原记录，钱回到原支出账户
6. **净支出口径**：环形图与「一级 = 二级 = 总支出」校验改按
   **净支出**（毛支出 − 报销）计算，既有 6 条聚合 SQL 一行不动
7. **预算**：设置页管理、统计页进度条与应用内超支标记；
   消耗 MUST 走 SQL 聚合，不用已加载的分页数据
8. 纯 JVM 单测覆盖净支出减法、超支判定、累计报销上限、
   v1/v2 备份兼容、`Migration(2→3)` SQL 契约；
   `check_stat_rules.py` / `check_transaction_annotations.py` 扩展

## 不做什么

- ❌ **6c 一律不做**：多币种与混算、CSV 导出、账单导入、小组件、通知栏磁贴
- ❌ **不改 `StatRules.SQL_STAT_RELEVANT`**（`type IN ('EXPENSE','INCOME')
  AND excludeFromStats = 0` 一字不动），SETTLEMENT 天然不进收入/支出概览
- ❌ 不改 4 Tab 导航、模板与周期账、资产与对账、既有 6 条聚合 SQL、
  `BackupEnvelope` 四项头部、`release.yml`、Manifest 权限
- ❌ 不做通知栏超支提醒（要 `POST_NOTIFICATIONS`）、不引入任何新依赖
- ❌ 不用负金额表达冲减（`amountCents` 恒正铁律）
- ❌ 不把报销款记成普通 `INCOME`（分类支出仍会虚高，等于没解决问题）
- ❌ 不用 `fallbackToDestructiveMigration`，预算不塞 SharedPreferences
- ❌ 不改历史快照 `docs/设计规格书_v2.md`、`my-app-workflow`

## 对齐结论

> 阶段 0 在对话里产出，五项齐全才进入写作。使用者确认「按推荐来」：
> 只做两项（A）、SETTLEMENT 成对模型（A′）、月度分类预算含总预算（A）。

1. **要解决什么问题**
   从「事后看」补上「事前控」和「账实相符」：预算防超支；
   报销成对模型让年度收入不虚增、分类支出反映真实花费。

2. **范围边界**
   - 改：闸门表、budgets 表 + DAO、RecordType/RecordEntity/BackupRecord 扩字段、
     `Migration(2→3)`、备份 v3、Repository 4 个新写方法、
     2 条 SETTLEMENT 聚合查询、ChartData 净支出函数、预算引擎、
     统计页预算条、明细页报销入口、设置页预算管理、静态检查、纯 JVM 单测
   - 不改：6c 全部、4 Tab、既有页面主体行为、唯一统计口径常量、
     既有 6 条聚合 SQL、构建链、Manifest 权限、历史快照

3. **关键约束**
   **闸门**：只把 6b 两项标「本批实现」；`reporting-ui`「v1 范围收敛」不改
   （它描述 v1 历史范围，仍成立）；小组件移 6c 并写明原因。
   **金额铁律不破**：SETTLEMENT 金额恒正；余额联动刻意复用现有
   `toAccountId += amount` —— 设 `fromAccountId = null`、
   `toAccountId = 原支出账户`，于是 `aggregateAccountDeltas` 与
   `LedgerEngine` **一行不改**就能算对净资产。
   **统计口径**：`SQL_STAT_RELEVANT` 一字不改；SETTLEMENT 天然不进
   收入/支出概览与趋势双线。净支出用**展示层减法**：新增
   `settlementByRootInRange` / `settlementByCategoryInRange` /
   `settlementTotalInRange` 三条查询，ChartData 里算
   「净支出 = 毛支出 − SETTLEMENT 合计」，既有 6 条聚合 SQL 全不动。
   「一级合计 = 二级合计 = 总支出」校验扩成**净支出**口径。
   **预算**：按 `scope(TOTAL/ROOT/CATEGORY) + categoryId` 存规则，
   长期对每个自然月生效（不必每月重建）；消耗 MUST 走上述 SQL 聚合，
   MUST NOT 用 `pagingItems` 求和；超支**只做应用内标记，不做通知**。
   **迁移**：显式 `Migration(2→3)` = `CREATE TABLE budgets` +
   `ALTER TABLE records ADD COLUMN linkedRecordId INTEGER`；
   budgets 表沿用 6a 教训**不声明索引/外键**；列定义 MUST 与 Entity
   逐字一致；仍禁止破坏性迁移。`SeedCallback.onCreate` 仍只对全新库生效，
   6b 不新增预置分类/账户/budget，故 `migrate()` 不补 seed。
   **备份**：`CURRENT=3`、`MIN_SUPPORTED=1`，v1/v2 备份仍可导入；
   budgets 纳入 payload 且带默认空列表；信封四项头部一字不改。
   **零新增依赖 / 零新增权限 / 零网络行为**；六个静态检查全过；
   推送单次 commit；沙盒无 SDK，编译与单测由 Actions 验证。
   **真机验证继续推迟**：`design.md` 必须重写一遍累积清单，
   并提醒装机前先用旧版导出一份加密备份。

4. **验收标准**（可测）
   - **GIVEN** 垫付 38.50 支出再报销收回 **THEN** 净资产回到垫付前、
     年度收入不虚增、餐饮分类净支出减 3850 分、明细可见成对关系
   - **GIVEN** 餐饮月预算 1000 元、当月净支出 1200 **THEN**
     统计页显示超支 200 且标记，预算消耗来自 SQL 聚合
   - **GIVEN** v1 或 v2 加密备份 **WHEN** 用 6b 包导入 **THEN**
     全量一致、budgets 为空、余额重算后净资产一致
   - **GIVEN** 归档 **WHEN** 校验 specs **THEN** 闸门已放行 6b、
     无「v1.1 悄悄加回」矛盾要求
   - **GIVEN** 一级/二级/总净支出 **THEN** 三者一致，不一致给 UI 警告
   - **GIVEN** 累计报销超过原支出金额 **WHEN** 再次发起 **THEN**
     被拒绝，账本不变
   - **GIVEN** CI 跑完 **THEN** 六个静态检查、单测、debug/release 全绿

5. **被否决的方案**
   一句话结论：**所有「绕过闸门 / 6c 搭车 / 退款记普通 INCOME /
   改 6 条聚合 SQL / 负金额冲减 / 通知栏提醒 / 预算塞 SharedPreferences /
   破坏性迁移」的方案均被否决**，采用「闸门分批放行 + SETTLEMENT 成对 +
   展示层净支出减法 + 展示层预算进度 + 显式 Migration(2→3)」。
   完整表格见 `design.md`「考虑过的其他方案」。

## 影响范围

**触及的 spec 模块**：`category-stats`（3）、`ledger-transactions`（3）、
`reporting-ui`（2）、`backup-restore`（2）、`privacy-security`（1）、
`build-and-ci`（2）；合计 **13 条 ADDED**，归档后基线 162 条 Requirement。

**是否需要重新安装包**：否，签名未变。但**这是第二次数据库结构升级，
且首次包含 `ALTER TABLE`**，从 v1 覆盖安装会依次执行
`Migration(1→2)` 与 `Migration(2→3)`。

**是否会丢数据**：设计上否，但**两次叠加迁移的真机正确性完全未验证**。
CI 只能验证编译与纯 JVM 契约；使用者已决定推迟装机，
装之前建议先用旧版导出加密备份。

## 与项目铁律是否冲突

- [x] 不违反三条铁律（零网络权限保持、金额仍用 Long 分、余额唯一写路径）
- [x] 涉及「已确认不做」清单，但**已按闸门表显式放行 6b 两项**
- [x] 6c 与小组件仍被闸门拦住，无冲突

**前置**：阶段 6a 已按规范归档（12 条 ADDED 合并，基线
149 条 Requirement / 237 个 Scenario），本 change 基于干净基线写。
