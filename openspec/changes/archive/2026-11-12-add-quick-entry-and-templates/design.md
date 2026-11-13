# 技术设计：add-quick-entry-and-templates

## 现状（写码前实际读了什么）

| 已有能力 | 位置 | 阶段 |
|---|---|---|
| `AppDatabase` 5 张表、`version = 1`、`exportSchema = false` | `data/local/AppDatabase.kt` | 1 |
| `Migrations.ALL = emptyArray()`（一路零迁移，字段全靠预留） | `data/local/Migrations.kt` | 1 |
| `SeedCallback.onCreate` 只对全新库生效 | `data/local/` | 1 |
| `BackupSchema.CURRENT = 1` / `MIN_SUPPORTED = 1` | `backup/dto/BackupEnvelope.kt` | 0 |
| `BackupPayload` 5 个列表、`BackupEnvelope` 四项头部 | `backup/dto/` | 1 |
| `LedgerSnapshot` 纯 JVM 快照、`exportSnapshot` / `importBackup` 全量替换 | `data/repository/` | 1 |
| `LedgerRepository` 17 个写方法，统一 `db.withTransaction { ... }` | `data/repository/` | 1–5 |
| `LedgerEntryPoint` + `ledgerFactory` 手动 ViewModel 工厂（无 hilt-navigation-compose） | `ui/LedgerDi.kt` | 2 |
| `LedgerApp` 恰好 4 Tab + 中央 FAB，`showEditor` 控制记一笔 Dialog | `ui/LedgerApp.kt` | 2 |
| `MainActivity` 继承 `FragmentActivity`，应用锁解锁前只渲染 `AppLockScreen` | `MainActivity.kt` | 5 |
| `SettingsScreen` 已有分类管理 / 回收站 / 健康自检等入口 | `ui/settings/` | 5 |
| `check_transaction_annotations.py` 的 `WRITE_METHODS` 17 个 + 破坏性迁移检查 | `scripts/` | 5 |
| `app/proguard-rules.pro` 已覆盖 `data.local.entity.**` / `backup.dto.**` / 枚举 | `app/` | 出包 |

**关键差别**：阶段 1–5 全是「零 Migration、零表结构变更」——字段早预留了。
6a 是**首次真正的数据库结构升级**：`Migration(1→2)` +
`BackupSchema 1→2` + 旧备份兼容。这是全项目风险最高的一类改动。

## 1. 闸门：先改写清单，再动代码

`reporting-ui`「v1 范围收敛」的 Scenario 写着：

> **GIVEN** 有人提议 v1 顺手做预算提醒 → **THEN** 需先显式更新
> `project.md` 的「已确认不做」

本 change 严格履行：把「v1 明确不做（v1.1 清单）」改写为
**「v1.1 分批计划」表格**，逐项标注 `6a 本批实现 / 6b / 6c 待决策 / 永久不做`。
**清单不删**，只按批次放行——阶段 5 归档时刚验证过它真的拦住过 CSV，
删掉等于把闸门废了。`reporting-ui`「v1 范围收敛」这条 Requirement
**不改**：它描述的是 v1 的历史范围，在 v1.1 时代依然成立。

`project.md` 分阶段计划表把原来的「阶段 6 = v1.1 增强」四个字
拆成 6a / 6b / 6c 三行，避免下次又出现「一句话阶段」被随意填内容。

## 2. 两张新表：刻意不加索引与外键

| 表 | 用途 | 关键字段 |
|---|---|---|
| `record_templates` | 可直接记一笔的模板 | `name` / `type` / `amountCents` / `categoryId` / `rootCategoryId` / `fromAccountId` / `toAccountId` / `note` / `excludeFromStats` / `sortOrder` / `isArchived` / `createdAt` |
| `recurring_rules` | 周期规则（**自包含**，不引用模板表） | 上面全部 + `cadence` / `dayOfMonth` / `dayOfWeek` / `anchorLocalDate` / `lastGeneratedLocalDate` / `isEnabled` |

**为什么规则表不外键引用模板表**：模板被归档 / 删除后规则仍应能独立存在；
自包含还能让「新增模板」与「新增规则」复用同一个表单组件，
避免两张表级联删除的复杂度。代价是字段重复，但只有十几条规则，无所谓。

**为什么两张表都不声明 `@Index` / `@ForeignKey`**：
Room 在打开数据库时会用 Entity 注解生成的 `TableInfo` 校验
迁移后的实际 schema（列集合、类型 affinity、notNull、主键、索引）。
多一个 `@Index` 就要在 Migration SQL 里精确建同名索引，
多一个 `@ForeignKey` 就要匹配外键定义——二者都是
「编译期完全看不出、真机一升级就 `Migration didn't properly handle`」的高风险点。
模板 / 规则最多几十行，全表扫描毫无性能问题，
**Migration SQL 只需两条 `CREATE TABLE`**，把风险压到最低。
明细 / 统计的万级性能铁律不受影响（那几张表的索引一个没动）。

**`exportSchema` 保持 `false`**：`Migrations.kt` 原注释建议
「version 2 起设 `exportSchema = true` 并配 `ksp schemaLocation`」，
那是给 `MigrationTestHelper`（`androidTest` 仪器测试）用的。
本项目 CI 没有模拟器，跑不了仪器测试，导出 JSON 不产生任何验证价值，
反而多改一处 Gradle 配置。故本轮**不导出 schema**，
并在 `Migrations.kt` 注释里写明这个取舍，避免注释与实现矛盾。
迁移正确性改由三层兜底：Migration SQL 静态检查 + 纯 JVM 契约单测 +
使用者真机覆盖升级实测（**明确留给使用者，不假报**）。

## 3. Migration(1→2)

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(SQL_1_2_CREATE_TEMPLATES)
        db.execSQL(SQL_1_2_CREATE_RULES)
    }
}
val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
```

SQL 严格按 Room 约定手写（列顺序不影响校验，按列名比较）：
- `String` / 经 `Converters` 转字符串的枚举 → `TEXT NOT NULL`
- `Long` / `Int` / `Boolean` → `INTEGER NOT NULL`；可空列 → `INTEGER`（无 NOT NULL）
- `@PrimaryKey(autoGenerate = true)` → `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`
- Kotlin 默认值**不产生 SQL `DEFAULT`**，故 SQL 里也不写

`DatabaseModule` 加 `.addMigrations(*Migrations.ALL)`。
`SeedCallback.onCreate` 仍只对全新库生效；6a **不新增预置分类 / 账户 / 模板**，
所以 `migrate()` 不需要补 seed（对齐里那条约束的空集情况，写在这里备案）。

**`check_transaction_annotations.py` 扩展**：除 `WRITE_METHODS` 加到 26 个，
再加三条精确检查——`AppDatabase version = 2`、
`Migrations.ALL` 非空且含 `Migration(1, 2)`、
两条 CREATE TABLE SQL 常量里含全部必要列名。
沿用「先剥注释再匹配」的一贯规则，别宽泛搜子串（阶段 6 已有 7 次自误报教训）。

## 4. 备份：CURRENT=2 / MIN_SUPPORTED=1

| 改动 | 做法 |
|---|---|
| `BackupSchema` | `CURRENT = 2`、`MIN_SUPPORTED = 1` |
| `BackupEnvelope` | **四项头部字段一字不改** |
| `BackupCounts` | 加 `templates` / `recurringRules`，都带 `= 0` 默认值 |
| `BackupPayload` | 加 `templates` / `recurringRules`，都带 `= emptyList()` 默认值 |
| `LedgerSnapshot` | 加两个列表，带默认值（不破坏已有单测构造） |
| `BackupExporter` | 传两个列表并计入 counts |
| `exportSnapshot` | 从两个新 DAO 取全量 |
| `importBackup` | 先 `clear()` 两张新表，再按 payload 插入，末尾重算 |
| `BackupImporter` | **不改**：`MIN_SUPPORTED = 1` 后 v1 备份自动放行 |

**v1 兼容的关键**：`BackupExporter.json` 已设
`ignoreUnknownKeys = true` + `encodeDefaults = true`。
v1 备份里没有 `templates` / `recurringRules` 两个键，
kotlinx.serialization 用默认值补成空列表，解析直接成功。
**覆盖导入语义一致**：用 v1 备份恢复会把现有模板 / 规则清空
（全量替换本来就是这个含义），这点写进 Scenario，不算数据丢失。

**proguard 无需改**：新增 Entity 在 `data.local.entity.**`、
DTO 在 `backup.dto.**`、`Cadence` 枚举在 `data.model.**`，
出包阶段的 keep 规则已全部覆盖。

## 5. 周期补记引擎（纯 JVM，可单测）

`engine/RecurringSchedule.kt`：

```
dueDates(cadence, dayOfMonth, dayOfWeek, anchorLocalDate,
         lastGeneratedLocalDate, todayLocalDate, maxCatchUpDays = 60): List<Int>
```

- DAILY：每天；WEEKLY：`dayOfWeek`（ISO 1=Mon..7=Sun）匹配；
  MONTHLY：`dayOfMonth`，当月没有该日则取当月最后一天（1 月 31 → 2 月 28）
- 起始 = `lastGeneratedLocalDate + 1 天`，否则 `anchorLocalDate`；
  只迭代到 `todayLocalDate`
- **catch-up 上限 60 天**：起始再取 `max(start, today - 60)`，
  避免用户三年没打开时一次生成上千条把冷启动卡死
- `toEpochMilli(localDate)` 用 `LocalDate.atTime(12, 0)` +
  `LedgerClock.zoneId`（Asia/Hong_Kong），正午避开 DST / 边界串日
- 用 `java.time.LocalDate`（API 26 原生支持，纯 JVM 单测可用），
  避免自写日期运算出错

**幂等**：`recurring_rules.lastGeneratedLocalDate` 只在**确实生成了流水**时
更新为 `todayLocalDate`。起始日晚于 today（未来 anchor）时不更新，
否则规则会永远不生效。每次冷启动重算几十条规则 × ≤60 天，开销可忽略。

**Repository.applyDueRules(todayLocalDate)**：
`db.withTransaction` 内遍历启用中的规则 → 批量 `recordDao.insert(withResolvedRoot(...))`
→ **末尾一次** `rebuildAllBalancesInternal()` → 返回生成条数。
不新开写余额路径，与 `insertRecord` 完全同构。

**不用 WorkManager / AlarmManager**：
精确闹钟在 Android 12+ 要 `SCHEDULE_EXACT_ALARM`（与零新增权限偏好冲突）；
WorkManager 会引入新依赖与后台行为，沙盒无法验证，
且「冷启动补记」对个人账本已足够（打开 App 时账自然就齐了）。
这条取舍写在这里，不埋在代码注释里。

## 6. UI：三处入口，4 Tab 不变

| 入口 | 落点 | 交互 |
|---|---|---|
| 模板快选 | 「记一笔」Dialog 顶部新增一排 chips | 点一下 → `recordFromTemplate()` → 直接 `onSaved()` 关闭，**不弹二次编辑**。≤2 次点击 |
| 管理 | 设置页新增「模板与周期记账」按钮 → Dialog | 两个 Tab（模板 / 周期规则），列表 + 新增 + 归档 + 启用开关 |
| 桌面 | 静态 `res/xml/shortcuts.xml` + Manifest `meta-data` | 点开直接进「记一笔」 |

- 模板快选是独立 Composable `TemplateQuickPick.kt`，
  自带 `LedgerDi.entryPoint` 取 DAO / Repository，
  **不动 `RecordEditorViewModel` 的构造与既有表单状态**（少一处回归面）
- `RecordEditorDialog` 只加一个可选参数 `templateQuickPick: Boolean = true`
- 管理 Dialog 用与项目一致的 `ledgerFactory` 手动 ViewModel 工厂，
  **不引入 hilt-navigation-compose**
- 分类 / 账户下拉直接读 `categoryDao.all()` / `accountDao.all()`，
  复用两级分类的 `rootCategoryId` 解析，不新增分类层级
- `LedgerEntryPoint` 加 `templateDao()` / `recurringRuleDao()` 两个方法

**快捷方式与应用锁**：
`MainActivity` 读 `intent.action == "com.zwz.offlineledger.ACTION_QUICK_RECORD"`
得到 `startWithRecordEntry`，只在 `unlocked == true` 分支传给 `LedgerApp`。
**启用应用锁时快捷方式仍要先解锁**——这个边界是隐私铁律的一部分，
不能因为「快速记账」就给一条绕过锁的路径。
`LedgerApp` 的 `showEditor` 初值改为 `startWithRecordEntry`。

## 7. 测试策略与如实说明的限制

| 层 | 验证方式 | 覆盖 |
|---|---|---|
| 排期 | `RecurringScheduleTest` 纯 JVM | 月度 / 周度 / 日度、月末 31→28、幂等、catch-up 上限、`toEpochMilli` 往返 |
| v1 备份 | `BackupV1CompatTest` 纯 JVM | v1 JSON 无新字段解析成空列表、v2 往返带模板 / 规则、过高版本拒绝 |
| Migration 契约 | `MigrationContractTest` 纯 JVM | 两条 CREATE TABLE 常量含全部必要列、`ALL` 由静态脚本核对 |
| 静态检查 | 六个脚本 | 26 个写方法在事务内、无破坏性迁移、`version = 2`、SQL 列对齐 |
| 构建 | Actions | 单测 + debug / release + lintVital |
| **真机** | **本阶段无法验证** | **`Migration(1→2)` 从 v1 覆盖升级后数据是否完好、R8 包启动、SQLCipher 打开数据库、BiometricPrompt、SAF、5 万条切月耗时** |

**CI 编译通过 ≠ 从 v1 覆盖升级数据还在**。Room 的 schema 校验发生在
运行时打开数据库那一刻，纯 JVM 单测碰不到。这是本阶段最大的残余风险，
必须在交付说明里直说，不能用「六个检查 PASS」代替。

### CI 失败时的回退顺序（每次只改一处、单独推送）

1. KSP 报 Entity / Migration 相关 → 先核对两张表的
   `@ColumnInfo` 名与 SQL 列名逐字一致
2. `lifecycle-viewmodel-compose` / Compose API 签名 → 对照
   `LedgerApp.kt` 既有写法，不引入新实验性 API
3. `java.time` 在 Android 编译报 desugaring → 改用纯整数 yyyyMMdd 运算
   （`RecurringSchedule` 已隔离在单文件，替换成本小）
4. `shortcuts.xml` 资源引用报错 → 核对 `@string` / `@android:drawable` 名称
5. `DropdownMenu` / `FilterChip` 编译错 → 退化为 `TextButton` + `Row`
6. 静态检查自误报 → 按「精确匹配 + 先剥注释 + 先剥理由段」复核，
   不放宽成子串（第 7 次自误报的教训）

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 绕过「已确认不做」闸门直接开做 | 少改一份文档 | 清单就是防范围蔓延的；破例一次以后就失效，且违反 `reporting-ui` spec |
| 把 7 项 v1.1 全摊开 | 一次做完 | 违反「每阶段可编译运行」；多币种碰报表混算、CSV 碰明文导出，风险不在同一量级 |
| 6a + 6b 一起做 | 少一个阶段 | 四张表 + 新 RecordType 一次大迁移，出问题难定位 |
| 规则表外键引用模板表 | 少重复字段 | 模板归档后规则失效；级联删除复杂度高 |
| 两张表加索引 / 外键 | 看起来更规范 | Migration SQL 必须精确匹配索引与外键定义，真机升级炸了编译期看不出来 |
| `exportSchema = true` + `ksp schemaLocation` | 能用 MigrationTestHelper | CI 无模拟器跑不了仪器测试，导出 JSON 无验证价值，还多改一处 Gradle |
| `AlarmManager` 精确闹钟 | 不用打开 App 也记账 | Android 12+ 需 `SCHEDULE_EXACT_ALARM`，与零新增权限冲突 |
| WorkManager 兜底 | 后台也能补记 | 新依赖 + 后台行为，沙盒无法验证；冷启动补记对个人账本已足够 |
| 自动生成流水绕过 `insertRecord` | 少一次重算 | 破坏「余额唯一写入路径」，会算错钱 |
| 模板 / 规则存 SharedPreferences | 零 Migration | 进不了备份，换设备规则全丢，且违反「设置项不进 Room 之外」的取舍 |
| `fallbackToDestructiveMigration` | 一行搞定 | 真实账本数据全丢，永久不做 |
| 顺手做 CSV / 多币种 / 三级分类 | 功能更全 | 6c 待决策 / 永久不做，闸门刚放行 6a 就搭车等于没放行 |
| 把 `startWithRecordEntry` 在锁屏时也生效 | 更快 | 给应用锁开了一条绕过路径，违反隐私铁律 |

## 取舍

选择 **「分批闸门 + 两张无索引表 + 显式 Migration + 冷启动幂等补记 +
静态快捷方式 + 模板快选」**：

- **牺牲**：首次数据库迁移的真机正确性无法在沙盒证明；
  新增 2 张表 / 2 个 DAO / 9 个写方法 / 一个管理 Dialog；
  「后台自动记账」能力缺失（必须打开 App 才补记）
- **换取**：固定支出从 5 步降到 ≤2 步；桌面一键直达；
  周期账不漏不重；v1 备份仍可导入；4 Tab 与既有交互零改动；
  零新增依赖、零新增权限、零网络行为

## 首轮 CI 实测：编译全过，挂在既有测试的硬编码常数

| run | commit | 结论 | 说明 |
|---|---|---|---|
| `43386388893` | `cf33b6c697` | cancelled | 首推被后一次 push 的 concurrency 取消 |
| `43386488446` | `a79c3011fb` | failure | Kotlin 测试源码里 v1 JSON 的双重转义写成非法字面量 |
| `43387143071` | `068fd4c89e` | failure | 编译已全过；只剩 `BackupEnvelopeTest` 一个断言失败 |

**两处真实问题，都不是架构问题**：

1. **Python 拼 Kotlin 的双重转义**：`BackupV1CompatTest` 的 v1 JSON
   先经修正脚本写入生成脚本、再执行生成脚本落盘，`\"` 在两层 Python
   字符串里被吃掉了反斜杠，产出 `""envelope":...` 这种非法 Kotlin。
   `check_kotlin_strings.py` 数引号是偶数所以没抓到——它防的是
   「真实换行」，不是「转义丢失」。改成**单行 Kotlin 三引号原始字符串**
   后编译通过。这是阶段 0 起记过的 `\n` 转义坑的一个新变体，
   建议上提踩坑清单时补一句「生成脚本里嵌 JSON 一律用原始字符串」。
2. **`BackupEnvelopeTest` 硬编码 `assertEquals(1, BackupSchema.CURRENT)`**。
   阶段 6a 把 CURRENT 升到 2 后它必然失败。改成不手写 CURRENT，
   只锁住真正需要长期成立的 `MIN_SUPPORTED == 1` 与
   `CURRENT > MIN_SUPPORTED`。这是本仓库**第 8 次「凭印象手写常数」**
   （前七次：147000 净资产、维度数 8、漏传 3 个必填参数、
   168→166、4 字口令、7 次校验自误报中的常数、以及本次）。
   共同模式：约束写在代码里，测试却凭语感固化了某个瞬时值。

**排期引擎自己也写错过两处**，本地用 Python 复算后修掉，
没留给 CI 去发现：
- `monthly_first_day...` 用默认 60 天窗口时被截掉 1 月 1 日，
  断言 4 个实际只有 3 个 → 单测辅助函数默认窗口放宽到 400，
  只有 `catch_up_is_capped` 显式传 60
- `minusDays(60)` 含两端会生成 **61** 条，与「60 天上限」语义不符 →
  引擎 floor 改为 `minusDays(maxCatchUpDays - 1)`，严格 N 条

**job logs / check-runs 仍然 403**，诊断靠临时在 `build.yml` 里
把 Gradle 日志 commit 回 `diag/test.log`（GITHUB_TOKEN 推的 commit
不触发新 workflow，没有循环）。诊断步骤用完已移除，
`diag/` 文件由推送脚本按远端基线删除，不留残留。

**Kotlin 编译一次通过的部分**（`068fd4c89e` 已证明）：
Room KSP 处理 `version = 2` 与两张新 Entity、Converters 的 Cadence、
`Migration(1→2)`、26 个写方法、Compose 的 `AssistChip` / `TabRow` /
`DropdownMenu` / 静态 shortcuts 资源——**全部零编译错误**。
这只能证明编译，不能证明真机 `Migration(1→2)` 升级成功。

## 最终验证（阶段 6a 收口）

| 验证 | run | 结论 |
|---|---|---|
| push CI：六个静态检查 / 单测 / debug APK / release lintVital / 签名诊断 | `43387774994` | **success**，13 步全绿 |
| release.yml 验证 ①（`minify=on`，不打 tag、不传坚果云） | `43388782910` | **success** |
| release.yml 验证 ②（同 commit 复跑） | `43389145224` | **success**，24 步全绿 |

- `offline-ledger-debug-apk` 23,574,835 B；
  最新 release run 三个 artifact：release APK（zip 11,998,947 B）、
  `mapping.txt` 3,114,863 B、metrics 226 B。
  **artifact 大小是压缩后 zip，不是 APK 实际体积**；真实体积以 Job Summary 为准。
- `create_release=false` / `upload_nutstore=false` / `build_aab=false` 是本次 input，
  对应步骤显示 skipped 属于预期，不是失败。GitHub Release 数量为 0。
- Kotlin 编译从 `068fd4c89e` 起一次通过：Room KSP 处理 `version = 2`、
  两张新 Entity、Cadence 转换器、26 个写方法、Compose 的 chips/TabRow/
  DropdownMenu、静态 shortcuts 资源，零编译错误。

### 真机实测由使用者明确推迟（必须记录，不能视为已通过）

使用者在阶段 6a 结束后决定：**先不做真机实测，继续下一阶段，
等完成度更高再统一装机体验**。因此以下各项**全部保持未验证**，
本项目从未用「CI 全绿」代替过它们：

- `Migration(1→2)` 从 v1 覆盖升级后数据是否完好（**本阶段最大残余风险**）
- R8 release 包真机能否正常启动
- SQLCipher 在真机打开加密数据库
- BiometricPrompt 弹窗 / SAF 文件选择器
- 5 万条切月 / 年报聚合耗时、滚动掉帧
- 模板快选、周期补记、桌面快捷方式在真机上的手感

风险随阶段累积，后续每个阶段的对齐结论都要重新列出这份清单，
直到使用者统一装机体验为止。装新包前建议先在旧版导出一份加密备份。

## 风险

**残余风险 1（最大）**：`Migration(1→2)` 在真机覆盖升级时
schema 校验失败或数据丢失。**缓解**：两张表无索引 / 外键，
SQL 只两条 CREATE TABLE；静态检查核对列名；`ALL` 显式登记；
**真机覆盖升级必须由使用者实测**，交付说明不粉饰。

**残余风险 2**：`java.time` 在 minSdk 26 的编译 / R8 行为。
**缓解**：API 26 原生支持；`RecurringSchedule` 隔离在单文件，
回退顺序第 3 条给了纯整数替代方案。

**残余风险 3**：冷启动补记若规则配置错误会一次写入多笔。
**缓解**：60 天 catch-up 上限 + `lastGeneratedLocalDate` 幂等 +
全部在单事务内（失败整体回滚）。

**残余风险 4**：静态检查脚本再次自误报。
**缓解**：精确匹配 + 先剥注释；FAIL 走第二条独立路径复核。

**残余风险 5**：应用锁用户点快捷方式时体验多一步解锁。
**这是刻意的安全边界，不是 bug**，不修。

## 验证

- [x] 阶段 0 五项对齐结论齐全、分级判定已告知并等确认
- [x] 使用者确认「按推荐来」：先归档 / 只做 6a / 闸门改分批计划表
- [x] 出包阶段已归档，specs 基线 137 条 Requirement / 212 个 Scenario
- [x] 闸门已显式改写，清单保留
- [x] 写码前实际读了 AppDatabase / Migrations / DatabaseModule /
  BackupEnvelope / BackupPayload / LedgerRepository / LedgerDi /
  LedgerApp / MainActivity / SettingsScreen / Converters / 两个检查脚本
- [x] 5 份 delta 共 12 条 ADDED，每条至少一个 GIVEN / WHEN / THEN + 「理由」
- [x] CI 全绿（`43387774994` / `43388782910` / `43389145224` 三次 success）
- [x] 每次推送后均用远端 Git Trees API 逐文件比对 blob sha（PASS）
- [x] 归档（使用者「继续下一阶段」按推荐 A 授权，本脚本执行）
