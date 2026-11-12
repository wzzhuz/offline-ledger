# 技术设计：implement-room-ledger-core

## 现状

阶段 0 已交付 55 个文件、8 个 spec 模块、CI 全绿。
代码层只有 `Cents` / `LedgerClock` / `LedgerCurrency` / `BackupEnvelope`
与一个 Compose 占位页；`app/build.gradle.kts` 已声明 Room 2.8.4 + KSP、
Hilt 2.57.1、SQLCipher 4.5.6、kotlinx-serialization 1.9.0，
**但都还没被使用**——这大幅降低了阶段 1 的依赖解析风险。

## 1. 表结构（version = 1）

| 表 | 关键字段 | 设计要点 |
|---|---|---|
| `accounts` | `id, name, type, initialBalanceCents, balanceCents, creditLimitCents?, includeInTotal, isArchived, sortOrder, currencyCode, lastReconciledAt?, lastReconciledBalanceCents?` | `balanceCents` 是**可重建缓存**；`initialBalanceCents` 才是真相源之一；对账锚点字段先留给阶段 3 |
| `categories` | `id, name, type, parentId?, level, iconName, colorHex, sortOrder, isArchived` | 最多两级（`level` 0/1）；二级不另存 type，从父级继承 |
| `records` | `id, type, amountCents, categoryId?, rootCategoryId?, fromAccountId?, toAccountId?, timestamp, localDate, yearMonth, note, excludeFromStats` | 三个冗余列避免 SQL 对 `timestamp` 做函数；`rootCategoryId` 让一级/二级统计各一条 `GROUP BY` |
| `balance_adjustments` | `id, accountId, deltaCents, memo, source, createdAt, isRevoked` | 独立表，`deltaCents` 可正可负；**不进 records、不进任何收支统计** |
| `trash` | `id, originalId, <与 records 同构>, deletedAt` | 删除 = 从 `records` 移入 `trash`；恢复 = 反向 + 重算 |

**索引**：
`records(localDate DESC, id)`、`records(yearMonth, type)`、
`records(rootCategoryId, categoryId, yearMonth)`、`records(amountCents)`、
`records(fromAccountId)`、`records(toAccountId)`、`records(categoryId)`、
`categories(parentId)`、`categories(type, level)`、
`balance_adjustments(accountId)`、`trash(deletedAt)`。

外键全部 `onDelete = RESTRICT`；账户/分类只允许 `isArchived` 归档。

### 为什么用 trash 表而不是主表 `isDeleted`

主表永远只含有效流水，于是：

- 聚合 SQL **不需要** `WHERE isDeleted = 0` 这类容易漏写的条件
- 统计口径 `isStatRelevant` 只需 `type IN ('EXPENSE','INCOME') AND excludeFromStats = 0`
- 恢复天然就是「插回 records + 重算」，与 insert 走同一条路径，
  不存在「只翻转删除标记却忘了联动余额」的可能

这同时兑现了 `ledger-transactions` spec 的「回收站恢复重新走插入事务」要求。

## 2. 余额：一份算法，两种执行方式

**唯一真相定义**：

```
balance = initialBalanceCents
        + Σ(流水对账户的净变动)
        + Σ(未撤销的 BalanceAdjustment.deltaCents)
流水净变动：fromAccountId → -amountCents，toAccountId → +amountCents
```

不按 `ASSET` / `LIABILITY` 分支，也不按 `EXPENSE` / `INCOME` 分支：
信用卡消费只有 `fromAccountId`，还款是 TRANSFER（from 储蓄卡、to 信用卡），
同一套 `+delta` 语义自然让负余额归零。

`LedgerEngine`（纯 Kotlin，零 Android 依赖）承担**唯一余额算法**：

```kotlin
fun deltasFrom(records): Map<Long, Long>          // 纯 JVM 参考实现
fun apply(initial, deltas, adjustments): Map<Long, Long>   // 生产与单测共用
fun computeBalances(accounts, records, adjustments) = apply(..., deltasFrom(...), ...)
fun netWorth(accounts, balances): Long            // SUM(includeInTotal)
fun isStatRelevant(record): Boolean
```

生产路径的 `rebuildAllBalances()` **不把 5 万条读进内存**：
由一条 SQL 把流水压成每账户一行的 delta（最多几十行），
再调用 `LedgerEngine.apply` 写回缓存：

```sql
SELECT accountId, SUM(deltaCents) AS deltaCents FROM (
  SELECT fromAccountId AS accountId, -amountCents AS deltaCents
    FROM records WHERE fromAccountId IS NOT NULL
  UNION ALL
  SELECT toAccountId AS accountId, amountCents AS deltaCents
    FROM records WHERE toAccountId IS NOT NULL
) GROUP BY accountId
```

SQL 是 `deltasFrom` 的声明式等价实现，两者语义一致（from 减、to 加）。
`check_stat_rules.py` 静态检查该 SQL 含四个关键片段，防止日后被「顺手优化」改歪。

### 事务用 `withTransaction`，不在 Repository 上标 `@Transaction`

Room 的 `@Transaction` 只有在 **DAO 方法**上才会生成事务代码；
标在 Repository 的普通方法上不产生任何事务。
因此所有写操作统一用 `androidx.room.withTransaction { }`（room-ktx 已引入）：

```kotlin
suspend fun insertRecord(r: RecordEntity) = db.withTransaction {
    recordDao.insert(r)
    rebuildAllBalancesInternal()      // 事务末尾重算
}
```

`update` / `delete`（移入 trash）/ `restore`（移回）/ `addAdjustment` /
`revokeAdjustment` / `importAll` 全部同构：
**写入 → 事务末尾重算**。不必分别实现「回滚旧影响 + 应用新影响」两套分支。

`check_transaction_annotations.py` 静态检查 Repository 里每个写方法都包在
`withTransaction` 内，且没有任何 `fallbackToDestructiveMigration`。

## 3. SQLCipher 接入（本轮的一次到位）

```kotlin
@Provides @Singleton
fun provideFactory(@ApplicationContext ctx: Context): SupportOpenHelperFactory {
    return SupportOpenHelperFactory(DatabaseKeyManager.getOrCreatePassphrase(ctx))
}
```

- `System.loadLibrary("sqlcipher")` 放 `OfflineLedgerApp.onCreate()`，
  早于任何数据库打开
- `DatabaseKeyManager`：Android Keystore 生成 AES-256/GCM 密钥（alias
  `offline_ledger_db_key`），首次用 `SecureRandom` 生成 32 字节数据库口令，
  密文 + IV 存 `SharedPreferences`；之后每次解密取回
- 密钥材料不出 Keystore，SharedPreferences 里只有密文

**已知风险（首次 CI 可能需一轮修复）**：
`sqlcipher-android:4.5.6@aar` 文档标注依赖 `androidx.sqlite:sqlite:2.2.0`，
而 Room 2.8.4 传递的是更高版本；Gradle 会选最高版本，
SQLCipher 实现的 `SupportSQLiteOpenHelper.Factory` 接口通常向后兼容，
但 `.so` 加载、`SupportOpenHelperFactory` 包名、`sqlite` 版本三者任一不匹配都会编译失败。
回退顺序写在第 8 节。

## 4. 预置数据（SeedCallback）

`RoomDatabase.Callback.onCreate` 里用 `SupportSQLiteDatabase.execSQL(sql, bindArgs)`
插入，数据定义在 `SeedData`（纯 Kotlin 对象），单测可直接验证结构：

- 一级支出：餐饮 / 交通 / 购物 / 居住 / 娱乐 / 医疗 / 其他支出
- 一级收入：工资 / 理财 / 其他收入
- 二级示例：餐饮 → 食材 / 正餐 / 外卖 / 水果 / 饮品；交通 → 公共交通 / 打车 / 加油 / 停车 …
- 默认账户：微信零钱、支付宝、招行储蓄卡（ASSET）；招行信用卡（LIABILITY，带额度）

一级用固定 id（1–10），二级用 101+ 并显式写 `parentId`。
`iconName` 存 Material Icons 名称字符串，`colorHex` 存 `#RRGGBB`。

**升级场景**：`onCreate` 只对全新库生效。后续版本若加预置分类，
必须在 `Migrations.kt` 的 `migrate()` 里补，不能指望 Callback 重跑
（踩坑清单 3.4.2）。阶段 1 先放空的 `ALL_MIGRATIONS` 与明确注释作为落点。

`exportSchema` 阶段 1 设 `false`；启用显式 Migration 时改为 `true`
并配置 `room.schemaLocation`。已在 `Migrations.kt` 注明。

## 5. 两级分类解析

```kotlin
fun resolveRootId(categoryId, categories): Long =
    if (category.level == 0) category.id else (category.parentId ?: category.id)
```

写入 `records` 时由 Repository 解析并冗余 `rootCategoryId`。
一级分类可直接记账（不必选到二级），保留低摩擦路径。

统计：按一级 `GROUP BY rootCategoryId`，按二级 `GROUP BY categoryId`。
单测断言「一级合计 = 二级合计 = 总支出」。

## 6. 统计口径唯一出口

```kotlin
object StatRules {
    const val SQL_STAT_RELEVANT =
        "type IN ('EXPENSE', 'INCOME') AND excludeFromStats = 0"
}
```

`TRANSFER` 与校准天然不在 `records` 的收支统计里（前者 type 不符，后者根本不在该表）。
DAO 只暴露命名明确的统计查询，所有查询复用同一 WHERE 片段。

## 7. 备份：头部不动，payload 另起

阶段 0 的 `BackupEnvelope` 头部四项（`schemaVersion` / `exportedAtEpochMilli` /
`currencyCode` / `counts`）**一个字不改**，符合「头部稳定」的 spec。
新增顶层：

```kotlin
@Serializable data class BackupFile(
    val envelope: BackupEnvelope,
    val payload: BackupPayload
)
@Serializable data class BackupPayload(
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val records: List<BackupRecord>,
    val balanceAdjustments: List<BackupAdjustment>,
    val trash: List<BackupRecord> = emptyList()
)
```

`BackupExporter.encode(snapshot)` 与 `BackupImporter.decode(json)` 是**纯 JVM** 函数，
因此备份往返可以直接在本地单测里验证，不需要模拟器。
真正落库的 `importAll` 用 `withTransaction` 做全量替换 + 末尾重算。

**阶段 1 的导出是明文**。这是开发期「边写边能导出，防止表结构天天变导致丢数据」
的最低保障，不是阶段 5 的成品。导出函数注释里明确写「明文，勿放公共目录」；
口令加密、SAF 文件选择器、CSV 全部留到阶段 5。

## 8. 测试策略与如实说明的限制

沙盒无 Android SDK / 模拟器，且 SQLCipher 的 native 库在 Robolectric 下无法加载。
因此**不引入 Robolectric**（避免再叠一个未实测版本），采用分层验证：

| 层 | 验证方式 | 覆盖 |
|---|---|---|
| 余额算法 / 分类 / 口径 / 备份往返 / 预置数据 | 纯 JVM JUnit 单测 | 9 条账务路径、一级=二级=总支出、序列化往返、seed 结构 |
| 事务包裹 | `check_transaction_annotations.py` 静态检查 | 每个写方法都在 `withTransaction` 内 |
| Room 注解 / SQL / SQLCipher 接入 | CI `assembleDebug` + `assembleRelease` 编译 | KSP 生成 DAO 实现、@Query 合法性、AAR 兼容 |
| 真实 SQLite 事务与加密库运行 | **留到阶段 2 起真机或 instrumentation** | 本阶段无法验证 |

**这不是「9 条路径跑在真实 Room 上」**，必须如实写在这里：
单测验证的是 `LedgerEngine` 这一唯一余额算法，
Repository 的 SQL 聚合是它的声明式等价实现，由编译与静态检查保证结构正确。
不把「文件已推送」或「单测通过」说成「真机验证通过」。

### CI 失败时的回退顺序（每次只改一处、单独推送）

1. `SupportOpenHelperFactory` 包名不符 → 按编译错误改 import
2. SQLCipher 与 `androidx.sqlite` 版本冲突 → 显式对齐 `androidx.sqlite:sqlite` 版本
3. Room @Query 子查询/UNION 不被接受 → 改为两条独立聚合查询在 Kotlin 里合并
4. Hilt 与 `@Provides` 的 Context 作用域 → 核对 `@ApplicationContext`
5. 实在接不上加密 → **降级为明文 Room 跑通账务逻辑**，
   把 factory 注入保留为空实现，并提 change 说明「加密延后」，不默默绕过铁律

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 不建 change 直接写 Entity | 少 4 个文档 | 表结构与删除语义的决策会失去溯源；违反 AGENTS.md 流程 |
| `balance` 当唯一真相源，只靠 @Transaction | 少一次聚合 | 导入 / 迁移 / 崩溃 / 预置数据都会让缓存漂移，且无自愈；spec 明确要求可重建 |
| 校准伪装成 EXPENSE 流水 | 少一张表 | 污染明细与分类、多出删不掉的伪分类、全量重算时会被当真实流水再扣一次；正是使用者在 Firefly III 的痛点 |
| 主表留 `isDeleted` 软删除 | 少一张表、实现熟悉 | 每个查询都要记得带 `WHERE isDeleted=0`，漏一处就让删掉的账回到年报；trash 表让主表天然干净 |
| `fallbackToDestructiveMigration` | 开发期省事 | 真实账本会丢数据；project.md 永久不做清单明确禁止 |
| 阶段 1 顺手做完整加密导出 / CSV / 应用锁 | 一次到位 | 属阶段 5；范围蔓延是个人项目烂尾首因 |
| 一次性把 UI 也摊开 | 看起来进度快 | 违反「每阶段结束必须能编译运行」，出问题无法定位 |
| 引入 Robolectric 跑真实 Room 事务 | 测试更贴近真实 | 新增未实测版本 + SQLCipher native 库在 JVM 下加载失败，风险叠加；改用纯 JVM 等价算法 + 静态检查 |
| 把 5 万条流水读进 Kotlin 再算余额 | 只有一份代码 | 几万对象 inflate 会卡；SQL 聚合压成几十行才是 reporting-ui spec 要求 |
| 在 Repository 方法上标 `@Transaction` | 写法像 Room 惯例 | Room 只在 DAO 方法上生成事务代码，标在 Repository 上**完全不生效**；改用 `withTransaction` |

## 取舍

选择 **「trash 表 + 写后全量重算 + SQLCipher 一次接入 + 纯 JVM 单测覆盖等价算法」**：

- **牺牲**：真实 SQLite 事务与加密库运行要等真机验证；首次 CI 可能因 SQLCipher 兼容性多一轮修复
- **换取**：余额只有一份算法定义，任何写操作都不可能漏联动；数据库从第一天起就是加密的；
  不引入任何新依赖（阶段 0 已声明全部库）

## 风险

**首轮 CI 实测（重要，先记录再谈结果）**：

| 项 | 内容 |
|---|---|
| **症状** | 静态检查 5 项全 success、Kotlin 编译全 success（Room KSP / Hilt / SQLCipher 接入均通过），但 `testDebugUnitTest` 判失败，后续 APK 步骤全部 skipped |
| **根因** | **不是算法错误，是测试期望值写错**：`LedgerEngineTest.01` 里净资产写成 147000，实际应为 247000（100000 + 200000 − 50000 − 3000）。JUnit XML 明确给出 `expected:<147000> but was:<247000>` |
| **解法** | 把期望值改为 247000；同步改回 `_stage1_code.py` 源脚本，避免下次重跑覆盖修复 |
| **防范** | 净资产断言以后一律用 `LedgerEngine.netWorth(accounts, computeBalances(accounts, emptyList()))` 作基线，不手写常数；这也正是 `money-precision` spec「绝不用总资产 − 总负债」想防的那类心算错误 |

**诊断手段**：沙盒读 Actions job logs 与 check-runs annotations 都返回 403
（网络策略，见 `踩坑清单.md` 5.2）。按 2.5 的变通方案，在 workflow 里临时加了
「失败时把 `/tmp/unit.log` 与 JUnit XML commit 回 `diag/`」的步骤，
从 Contents API 读到 XML 才定位到具体断言。
**GITHUB_TOKEN 产生的 commit 不会触发新 workflow，不会形成循环**；诊断步骤用完已移除，
`diag/` 由单次 commit 推送删除。

**这条也修正了第 8 节的一处乐观假设**：编译全绿不等于单测全绿。
阶段 1 首次运行恰恰是「最难查的静默数字错误」被纯 JVM 单测拦下来——
虽然这次错的是测试自己写的常数，不是 `LedgerEngine`。

**残余风险 1**：SQLCipher 4.5.6 与 Room 2.8.4 的 `androidx.sqlite` 版本不兼容。
**缓解**：第 8 节回退顺序；每次只改一处单独推送；推完查 Actions 列表确认确实跑了。

**残余风险 2**：`SupportOpenHelperFactory` 包名或 `System.loadLibrary` 时序不对。
**缓解**：按编译错误定位；`loadLibrary` 放在 Application.onCreate 最早处。

**残余风险 3**：`SeedCallback` 的 `execSQL` 列名与 Entity 定义不一致，编译期发现不了。
**缓解**：Entity 全部显式 `@ColumnInfo(name=...)`；seed SQL 由 `SeedData` 集中定义；
单测验证 `SeedData` 结构（两级、类型继承、账户初值）；阶段 2 首次真机启动即会暴露。

**残余风险 4**：纯 JVM 单测不等于真机验证，可能被误读成「Room 事务已测过」。
**缓解**：第 8 节表格明确分层；tasks 与最终回报都如实写明。

**残余风险 5**：明文 JSON 导出文件若被放到公共目录会泄露账目。
**缓解**：导出函数注释醒目提示；阶段 5 加密导出前不提供 SAF UI 入口，
阶段 1 的导出仅供开发期自测。

## 验证

- [x] 阶段 0 归档三步已完成（合并 delta / 移入 archive / 校验 8 模块）
- [x] 每个 Requirement 至少一个 GIVEN/WHEN/THEN 场景 + 「理由」
- [x] 归档后 specs 共 8 个模块，build-and-ci 已落地
- [x] 编译门禁全绿（Room KSP / Hilt / SQLCipher 接入均通过）
- [x] 修正测试期望值后重跑：run 43211076566（commit 534957790f）**job 结论 success**，
  五个静态检查 / keystore 校验 / 单测 / debug APK / release(lintVital) / 签名诊断 / 制品上传 全部 success
- [x] 推送后用远端 Git Trees API 逐文件比对 blob sha，PASS
- [x] 产物 offline-ledger-debug-apk 21,636,979 bytes
- [ ] 推送后用远端 API 逐文件比对 sha
