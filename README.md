# 离线账本（offline-ledger）

**纯本地、零网络权限、无广告的个人 Android 记账应用。** 知道自己的资产与支出构成，
数据不托管给任何第三方，也不依赖长期开机的服务器或 NAS。

> 状态：**v1（阶段 0–5）全部完成并归档，8 模块 / 128 条 Requirement / 192 个 Scenario**；
> 出包阶段已归档；**阶段 6a `add-quick-entry-and-templates` 进行中**：模板、周期记账、桌面快捷方式，首次 `Migration(1→2)`。
> 现在能看月 / 年收支概览、一级 / 二级分类占比、下钻明细、每日支出折线与年度收支双折线。
> 记账、明细、组合筛选、回收站恢复、净资产与对账沿用阶段 2 / 3 能力。
> 本仓库遵循 OpenSpec 规范驱动开发，规格是唯一真理源。

---

## 30 秒架构速览

| 项 | 选择 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose（Material 3） |
| 架构 | MVVM + Clean Architecture（UI → ViewModel → Repository → Room DAO） |
| 存储 | Room (SQLite) + **SQLCipher**（阶段 1 已接入） |
| 异步 / DI | Coroutines + StateFlow / Hilt |
| 序列化 | kotlinx.serialization（备份 DTO 与 Entity 分离） |
| 分页 / 图表 | **Paging 3**（阶段 2 已接入）/ 原生 Canvas 自绘（阶段 4） |
| 权限 | **不声明 `INTERNET`**，仅用 SAF 导入导出 |
| 最低版本 | Android 8.0（API 26） |
| targetSdk | 36（跟随当年 Google Play 要求，每年复核） |
| 默认本地化 | `Asia/Hong_Kong`、默认币种 `HKD` |
| 包名 | `com.zwz.offlineledger` |

三条铁律（详见 `openspec/project.md`）：**数据是你的** / **账要算得准** / **记账不能变慢**。

### 锁定的版本组合

| Gradle | AGP | Kotlin | KSP | Compose BOM | Room | Hilt | JDK |
|---|---|---|---|---|---|---|---|
| 8.13 | 8.13.0 | 2.3.21 | 2.3.11 | 2026.06.00 | 2.8.4 | 2.57.1 | 17 |

SQLCipher `4.5.6@aar`、kotlinx-serialization-json `1.9.0`；
阶段 2 新增 `material-icons-core`（BOM 管理）、`navigation-compose:2.9.0`、
`lifecycle-viewmodel-compose:2.9.1`、`paging-common/compose:3.3.6`。
锁定依据、证据链与应急回退顺序见
`openspec/changes/archive/2026-11-12-scaffold-android-project/design.md` 第 1 节。

---

## 阶段 1 数据层

**5 张表**：`accounts` / `categories`（两级）/ `records`（冗余 `localDate` `yearMonth`
`rootCategoryId`）/ `balance_adjustments` / `trash`。

三条核心设计：

1. **余额是可重建缓存** —— `balance = initialBalance + 流水净变动 + 未撤销校准 delta`。
   所有写操作 `withTransaction { 写入 → rebuildAllBalances() }`，事务中途失败整体回滚。
   `LedgerEngine` 是唯一余额算法；生产由一条 SQL 把流水压成每账户一行再交给它。
2. **删除走 trash 表**，主表不含 `isDeleted`。恢复 = 插回 + 重算，与 insert 同路径；
   统计口径只需 `type IN ('EXPENSE','INCOME') AND excludeFromStats = 0`。
3. **校准独立成表**，不生成伪支出流水，因此不进明细、不进分类统计、重算时不会被重复扣。

数据库口令由 Android Keystore 的 AES-256/GCM 密钥加密保管，
`System.loadLibrary("sqlcipher")` 在 Application.onCreate 最早处加载。

**阶段 1 的最小 JSON 导出是明文**，仅供开发期防止表结构变动丢数据；
口令加密、SAF 文件选择器、CSV 属于阶段 5。请勿把导出文件放到公共目录。

**测试边界（如实说明）**：沙盒无 Android SDK / 模拟器，SQLCipher 的 native 库在 JVM 下
无法加载。因此纯 JVM 单测覆盖的是 `LedgerEngine` 这一唯一余额算法、分类合计、
统计口径与备份往返；**真实 SQLite 事务与加密库运行要等阶段 2 起真机验证**。
事务包裹与聚合 SQL 由两个静态检查脚本兜底。首次 CI 编译门禁全绿，
单测曾因测试自身写错净资产常数而失败，已修正（见 change 的 design.md）。详见
`openspec/changes/archive/2026-11-12-implement-room-ledger-core/design.md` 第 8 节。

---

## 阶段 2 交互层

**导航**：恰好 4 个 Tab（明细 / 资产 / 统计 / 设置）+ 中央 `+` FAB。
资产与统计放明确标注所属阶段的占位页，结构一次成型不返工。

**记一笔**：支出 / 收入 / 转账三 Tab、两级分类快选（点一级即可保存，
可选展开二级 chips）、内置 `BigDecimal` 计算器键盘、账户胶囊、按天调日期、
备注与「不计入收支」开关；编辑已有流水复用同一组件。
分类图标用 `colorHex` 彩色圆 + 中文首字，不做 30 个图标名的字符串映射。

**明细**：月度概览（支出 / 收入 / 结余 + 左右切月）、**Paging 3** 按日分组、
组合筛选（时间 + 账户 + 类型 + 一级 / 二级分类 + 金额区间 + 备注全文）、
点击行编辑、删除二次确认。

三条性能与精度约束：

1. `PagingData` 是 ViewModel 里**独立的 Flow**，MUST NOT 包进 `UiState`；
   `flatMapLatest` + `cachedIn(viewModelScope)` + 稳定 `itemKey`。
   写操作成功后 `RecordFilter.revision++` 重建 Pager 刷新列表。
2. **一条参数化 SQL** 覆盖全部组合筛选，`LEFT JOIN` 一次取回分类名与账户名；
   按日合计由 `GROUP BY localDate` 单独返回（每月最多 31 行），
   **不是**已加载分页数据求和，不会随滚动跳变。
3. 计算器全程 `BigDecimal`，禁止浮点；除法有限精度，结果经
   `setScale(2, HALF_UP)` 交 `Cents.fromYuan` 转整数分。

**回收站**：恢复走 `LedgerRepository.restoreRecord`（事务内插回 + 重算），
彻底删除只移除回收站记录。
**设置页**：临时明文 JSON 导出（写 `context.filesDir`，自校验可解析）、
账本健康自检、回收站入口。界面醒目标注「明文，勿外传」，
阶段 5 替换为 SAF 选择 + 口令加密导出。

**测试边界（如实说明）**：新增纯 JVM 单测覆盖计算器表达式优先级、除法舍入、
月份范围与筛选 revision；沙盒无 Android SDK / 模拟器，
**5 万条真机切月耗时、滚动掉帧、SQLCipher 真机打开数据库仍未实测**。
本阶段只验证分页 SQL 结构与依赖可编译，不把「编译通过」说成「性能达标」。

---

## 阶段 3 资产与对账

**零表结构变更**：`AccountEntity` / `BackupAccount` 在阶段 1 已预留
`lastReconciledAt` / `lastReconciledBalanceCents`，所以 `AppDatabase` 仍 `version = 1`、
**无 Room Migration**、备份格式与 `schemaVersion = 1` 一字未改、不新增任何 Gradle 依赖。

**资产页**（替换占位）：净资产大卡、总资产 / 总负债（注明「仅展示，不参与计算」）、
资产账户 / 信用负债账户分组、可用额度 = `creditLimitCents + balanceCents`、
新增账户 / 改名 / 归档 / 计入净资产开关。账户详情含专属流水与
「余额调整记录」折叠区，可撤销校准。

**对账流程**：输入实际余额 → 差额 = 实际 − 账面 →
`|差额| ≤ 1.00 元` 一键「忽略尾差并校准」；`> 1.00 元` 默认只给「先核对」，
「仍要校准」备注必填 + 二次确认。校准生成独立 `BalanceAdjustment`，
与 `rebuildAllBalances()`、锚点推进同处一个事务，不进明细 / 分类 / 年报。

**核对视图**（资产页内 Dialog，不新增 Tab）：锚点后流水切片 +
近 7 / 30 / 90 天放宽、金额反查（等于 / 区间 / 近似 ±1.00 元）、
疑似问题标记（金额 0 / 未分类 / 异常大额 / 24h 内同额重复）、
「只看疑似项」、无匹配时提示「未找到 0.01 的记录」。
反查与范围由参数化 SQL 限定，MUST NOT 全表加载后在 Kotlin 过滤。

**两条规格债已按标题修复**：阶段 2 归档时识别的「资产页显示占位说明」与
「筛选面板没有对账入口」，归档阶段 3 时已用 `## MODIFIED Requirements`
的完整文本按标题替换进 `specs/`，specs 不再有互相矛盾的要求。

**测试边界（如实说明）**：新增纯 JVM 单测覆盖差额正负、尾差边界 100 / 101 分、
可用额度、展示聚合值不被减法污染、三种反查区间、疑似标记解码与锚点备份往返。
沙盒无 Android SDK / 模拟器，**5 万条真机切月耗时、滚动掉帧、SQLCipher 真机打开
数据库仍未实测**；本阶段只验证范围 SQL 结构与依赖可编译，不把「编译通过」说成「性能达标」。

---

## 阶段 4 统计报表

**零结构变更**：统计所需的聚合 SQL 在阶段 1 / 2 已全部备好
（`totalExpense` / `totalIncome` / `expenseByRoot` / `expenseByCategory` /
`monthlyTrend` / `dailyTotals` / `pagedFiltered`），本阶段只新增 **4 条只读查询**，
`AppDatabase` 仍 `version = 1`、**无 Migration**、备份格式不变、零新增 Gradle 依赖、
`WRITE_METHODS` 保持 13 个（不新增写方法）。

**统计页**：月度 / 年度切换与周期左右切换、总收入 / 总支出 / 结余（负结余用红色）、
一级 / 二级分类占比切换、合计一致性警告条（不一致时显示差额「分」）、
月度每日支出 + 收入折线、年度 1–12 月收入 vs 支出双折线。

**原生 Canvas 自绘**：`DonutChart` / `LineChart`，零 `TextMeasurer`、零 `FlowRow`、
零 `android.graphics`。中心总额、图例、x 轴刻度全用普通 Compose `Text` 叠加；
环形图点击命中由纯 JVM `ChartData.sectorHit` 判定，图例提供等价入口。

**三级下钻**：点一级扇区 → 该大类下二级构成 → 点二级 → 该分类明细
（带唯一统计口径 + `LIMIT 100` 的 SQL，不全量加载）。

**合计一致性**：`ChartData.checkTotals` 比对
「一级合计 = 二级合计 = 总支出」，UI 警告条 + 纯 JVM 单测双保险。
空日期 / 空月份在纯 JVM 数据层补 0，闰年 2 月返回 29 个点、年度固定 12 个月。

**测试边界（如实说明）**：新增纯 JVM 单测覆盖三口径合计、闰年 / 大小月补 0、
空序列、扇区角度与跨 0° 命中、多系列归一化、颜色解析。
沙盒无 Android SDK / 模拟器，**5 万条真机切月 / 年报聚合耗时、滚动掉帧、
SQLCipher 真机打开数据库仍未实测**；本阶段只验证聚合 SQL 结构与依赖可编译。

---

## 阶段 5 加密备份与设置

**零结构变更**：`AppDatabase` 仍 `version = 1`、**无 Migration**、
备份信封四项头部与 `schemaVersion = 1` 一字不改、4 Tab 导航零改动。
`CategoryEntity` 在阶段 1 预留的 `isArchived` / `sortOrder` 本阶段直接启用；
偏好走 SharedPreferences，不进 Room、不进备份 DTO。

**两套密钥严格分开**：SQLCipher 数据库口令由 Android Keystore 保管、
换设备即失效、用户不可见；备份口令由用户自设、PBKDF2 派生、不落盘、
跨设备必须仍可解密。MUST NOT 复用或互相推导。

**加密文件格式**：`MAGIC("OLBK"+0x01)` + 随机 16B SALT + 随机 12B IV +
`AES-256/GCM(Gzip(JSON))`。`PBKDF2WithHmacSHA256` 120k 次迭代；
错口令 / 密文篡改 / 文件截断都归到同一句「口令错误或备份文件已损坏」，
不泄露是哪种，且**不调用 `importBackup()`**、原账本一条不动。
`SecureBackupCodec` 纯 JVM，单测覆盖往返、错口令、篡改、MAGIC、截断、
短口令、密文不含明文、两次导出不同。

**SAF**：`CreateDocument` / `OpenDocument` + `ContentResolver` 流式读写，
官方明确无需任何存储权限；覆盖导入前二次确认「将全量替换现有全部数据」。
设置页**不再有明文 JSON 导出按钮**。

**应用锁**：`MainActivity` 改继承 `FragmentActivity`，冷启动与
`ON_PAUSE` 后台恢复都重新上锁，解锁前**不渲染任何账目界面**；
`androidx.biometric:biometric:1.1.0`（官方最新稳定版）+ 自建 PIN 兜底；
`FLAG_SECURE` 开关防最近任务预览。PIN 以加盐 SHA-256 存储，
UI 明确说明它防的是瞥视 / 丢机随手翻，不是强认证。

**设置中心**：加密备份导出 / 恢复、应用锁与 PIN、FLAG_SECURE、
对账尾差阈值（UI / ViewModel / Repository 三层读同一个偏好值）、
支出 / 收入配色反转（`LedgerColors` 语义色改为可观察状态，
既有 80+ 处引用一行不改）、两级分类管理、账本健康自检、回收站。

**CSV 归属已拍板**：**v1 不做**，4 份 MODIFIED 把
`backup-restore`「CSV 格式约定」与 `privacy-security`「导出文件默认加密」
统一标注为 v1.1 预留，`project.md` 阶段 5 表格同步；
`reporting-ui`「v1 范围收敛」与「已确认不做」清单保持不动。

**测试边界（如实说明）**：新增纯 JVM 单测覆盖加密往返与失败路径；
压缩 / 加密 / 写盘流式，但 `encodeToString` 对 5 万条仍会一次生成
约 10–15 MB 的 JSON String（不产生第二份明文副本）——逐条流式序列化
留到实测出现内存问题时再做。沙盒无 Android SDK / 模拟器，
**5 万条真机切月 / 年报聚合耗时、滚动掉帧、SQLCipher 真机打开数据库、
BiometricPrompt 真机弹窗、SAF 真机文件选择器仍未实测**。

---

## 出包阶段：release.yml

**触发**：仓库 → Actions → 左侧「编译 Release 包」→ Run workflow。
与 `build.yml`（每次 push 自动跑 debug + release lintVital）分工明确。

**默认开启 R8**：`minify` 输入默认 `on`，可选 `none / on / both`。
Gradle 侧 `-PminifyRelease` 默认 `false`，所以 `build.yml` 的日常构建
行为完全不变；`both` 一次产出 plain 与 minified 两个包，
Job Summary 打印体积与差值。`app/proguard-rules.pro` 补齐了
`kotlinx.serialization` / Room / Hilt / SQLCipher JNI / 枚举 / 备份 DTO
的 keep 规则，R8 开启时 `mapping.txt` 作为制品上传。

**版本号**：`defaultVersionName = "0.1.0"` 是唯一手动维护点，
`versionCode` 由 `major*10000 + minor*100 + patch` 推导（不再恒为 1）。
CI 可用 `-PversionNameOverride` 覆盖并**传进 Gradle**，不回写文件。

**签名**：配齐 `RELEASE_KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` /
`KEY_ALIAS` / `KEY_PASSWORD` 四个 Secret 就用正式密钥；
缺任一**只告警、回退仓库内 `debug.keystore`，流程继续**。
回退的包与历次 debug 包同签名，可直接覆盖安装、不丢数据，
但密钥是公开的，**勿对外分发**。诊断用 `apksigner verify --print-certs`，
不依赖只认 v1 JAR 签名的 `keytool -printcert -jarfile`。

**坚果云 WebDAV**：`upload_nutstore` 默认开启，且**与 `create_release` 解耦**
（不打 tag 也能传）。四步：PROPFIND 认证 → 两级 MKCOL → `curl -T` →
HEAD 落盘校验，诊断日志写进 Job Summary。需要先在本仓库 Secrets 里配
`JGY_USER`（注册邮箱）与 `JGY_APP_PASSWORD`（**坚果云网页端 → 账户信息 →
安全选项 → 第三方应用管理 → 添加应用密码**，不是登录密码）。
未配置时步骤跳过、job 仍 success。免费版每月上传 1GB，自用足够。

**边界**：runner 上传的是**安装包 APK**，账本数据从不出设备。
App 仍不声明 `INTERNET`，`check_manifest_privacy.py` 在出包后继续 PASS。
新增 `scripts/check_workflow_syntax.py` 接入 `build.yml` 静态检查，
防 YAML 1.1 把裸 `on` 布尔化导致工作流静默不注册。

**如实说明的限制**：R8 包能否真机启动、SQLCipher 真机打开数据库、
BiometricPrompt 弹窗、SAF 文件选择器、5 万条切月 / 年报聚合耗时，
沙盒无模拟器**均未实测**。CI 构建成功不等于 release 包真机能用，
keep 规则 + `mapping.txt` 只是兜底，装到手机实测这一步必须由使用者完成。

---

## 阶段 6a：模板 / 周期记账 / 桌面快捷方式

**闸门先行**：v1 的「已确认不做」清单改写成 `project.md` 的
**「v1.1 分批计划」表格**，6a 本批放行、6b（预算 / 退款报销）与
6c（多币种 / CSV）仍待决策、三级分类移入永久不做。清单不删除，
只按批次显式放行——这是 `reporting-ui`「v1 范围收敛」Scenario 要求的那一步。

**三处入口**：

| 入口 | 行为 |
|---|---|
| 记一笔顶部模板 chips | 点一下直接生成流水并关闭弹窗，≤2 次点击记完一笔 |
| 设置页「模板与周期记账」 | 两 Tab 管理模板与规则，新增 / 归档 / 启用开关 |
| 桌面长按图标「记一笔」 | 静态 `shortcuts.xml`，直接进记一笔；**启用应用锁时仍先解锁** |

**周期补记**：冷启动按日补记到期规则，幂等（`lastGeneratedLocalDate`）、
单事务批量插入、末尾一次重算、60 天 catch-up 上限。
不用精确闹钟（Android 12+ 要 `SCHEDULE_EXACT_ALARM`），也不引入 WorkManager；
零新增依赖、零新增权限。

**首次数据库迁移**：`AppDatabase version = 2` + `Migration(1→2)` 建
`record_templates` / `recurring_rules` 两张表。两张新表刻意不声明索引与外键，
Migration SQL 只两条 `CREATE TABLE`，把运行时 schema 校验风险压到最低。
`SeedCallback.onCreate` 仍只对全新库生效。

**备份**：`BackupSchema.CURRENT = 2`、`MIN_SUPPORTED = 1`。
`BackupPayload` 新增两个带默认值的列表，**v1 加密备份仍可导入**
（缺键解析成空列表）；`BackupEnvelope` 四项头部字段一字未改。

**如实说明的限制**：**`Migration(1→2)` 从 v1 覆盖升级后数据是否完好，
沙盒无法验证**——Room 的 schema 校验发生在真机打开数据库那一刻，
CI 只能证明编译通过、静态 SQL 列齐全、纯 JVM 契约成立。
装到手机上从旧版覆盖升级实测这一步必须由使用者完成。
R8 包真机启动、SQLCipher 打开数据库、BiometricPrompt 弹窗、
SAF 选择器、5 万条切月耗时也仍未实测。

---

## 本地构建

```bash
./gradlew testDebugUnitTest       # 单元测试
./gradlew assembleDebug           # Debug APK：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease         # Release APK（会跑 lintVital）
```

`gradle/wrapper/gradle-wrapper.jar` 已提交，Android Studio 可直接打开。
`app/debug.keystore` 已提交并固定引用，**各次编译签名一致，可覆盖安装不必卸载**。
该密钥等价于公开密钥，仅供个人自用，不要对外分发用它签名的包。

沙盒环境没有 Android SDK / Gradle / Java 编译器，
真正的编译验证走 GitHub Actions：`.github/workflows/build.yml`。
每次 push 到**任意分支**都会跑：五个静态检查 → 单元测试 → debug APK →
release（`lintVital`）→ 签名诊断 → 上传制品。

| 脚本 | 拦什么 |
|---|---|
| `scripts/check_manifest_privacy.py` | 误加 `INTERNET`、漏关自动备份、引入网络 SDK |
| `scripts/check_kotlin_strings.py` | Python 拼 Kotlin 时 `\n` 被转义成真实换行（犯过两次） |
| `scripts/check_docs_consistency.py` | 版本表 / targetSdk / 「已确认不做」清单与代码脱节 |
| `scripts/check_transaction_annotations.py` | 写方法漏包 `withTransaction`、出现破坏性迁移 |
| `scripts/check_stat_rules.py` | 余额聚合 SQL 或唯一统计口径被改歪 |

---

## 目录结构

```
offline-ledger/
├── openspec/
│   ├── AGENTS.md             AI 行为指引（新会话强制开场）
│   ├── project.md            项目铁律、技术栈、已确认不做、分阶段计划
│   ├── specs/                真理源：8 个模块
│   │   ├── money-precision/       金额分制、带符号余额、净资产
│   │   ├── ledger-transactions/   事务联动、余额重算、统计口径
│   │   ├── reconciliation/       对账锚点、尾差校准、金额反查
│   │   ├── category-stats/       两级分类、统计与下钻
│   │   ├── privacy-security/     无网络权限、加密、备份排除、应用锁
│   │   ├── backup-restore/       schemaVersion、导入重算
│   │   ├── reporting-ui/         4 Tab、低摩擦、万级数据性能
│   │   └── build-and-ci/         构建工具链、CI 验证、固定签名
│   └── changes/
│       ├── build-release-pipeline/        进行中：出包阶段
│       └── archive/                      已归档（初始设计 / 阶段 0 / 1 / 2 / 3 / 4 / 5）
├── app/src/main/java/com/zwz/offlineledger/
│   ├── core/                 Cents / LedgerClock / LedgerCurrency / LedgerPrefs
│   ├── data/model/           纯 JVM 数据模型
│   ├── data/local/           Entity / DAO / AppDatabase / Seed / Keystore
│   ├── data/di/DatabaseModule.kt
│   ├── data/repository/LedgerRepository.kt
│   ├── data/paging/          RecordFilter / RecordPagingSource
│   ├── engine/               LedgerEngine / CategoryTree / StatRules /
│   │                         Calculator / Reconciliation / ChartData
│   ├── backup/               Envelope / Payload / Exporter / Importer / SecureBackupCodec
│   └── ui/                   LedgerApp / theme / LedgerDi / components
│       ├── detail/            DetailScreen / DetailViewModel / FilterSheet
│       ├── record/            RecordEditorDialog / RecordEditorViewModel
│       ├── assets/            AssetsScreen / AssetsViewModel / AccountDetailDialog
│       ├── reconcile/         ReconcileDialog / ReviewDialog + 两个 ViewModel
│       ├── charts/            DonutChart / LineChart（原生 Canvas）
│       ├── stats/             StatsScreen / StatsViewModel / DrillDownDialog
│       ├── trash/             TrashScreen / TrashViewModel
│       ├── lock/              AppLockScreen（BiometricPrompt + PIN）
│       └── settings/          SettingsScreen / ViewModel / 三个 Dialog
├── app/src/test/             纯 JVM 单测
├── scripts/                  六个 CI 静态检查（含 workflow 语法）
├── .github/workflows/           build.yml（push 校验）/ release.yml（手动出包）
├── .github/scripts/             prepare-signing.sh / cleanup-artifacts.sh
└── docs/设计规格书_v2.md     历史快照（非真理源，冲突时以 specs 为准）
```

`specs/` 写系统 **SHALL** 做什么；`changes/*/design.md` 写某次变更**为什么**这么做；
`project.md` 写长期稳定约束；本文件写架构速览。
**技术决策不写在 `docs/` 里**——历史记录写完即定型，独立技术文档必然腐化。

---

## 当前进行中的改动

**无**。阶段 0 / 1 / 2 / 3 / 4 均已按规范归档，
8 个模块现为 **116 条 Requirement / 166 个 Scenario**。
阶段 4 首次由**同一条 Requirement 的第二次 MODIFIED** 收口统计页占位。
归档 `design.md` 记录了阶段 5 必须处理的 **3 处规格债**：
① 「后续阶段不改导航」Scenario 仍含阶段号；② 明文导出入口将被 SAF 加密导出替换；
③ **CSV 在 `reporting-ui`「v1 范围收敛」被列为 v1.1 禁止项，
却在 `backup-restore` / `privacy-security` / `project.md` 阶段 5 里被视为 v1 功能**——
两处长期约束互相矛盾，阶段 5 必须先拍板。

---

## 下一步

阶段 5（对齐门禁中）：完整备份与设置 —— SAF 加密导入导出、CSV、应用锁、
分类管理、账本健康页、阈值 / 配色偏好，替换阶段 2 的临时明文导出入口。
阶段 5 必须先拍板 CSV 的 v1 / v1.1 归属，并用 MODIFIED 处理上述 3 处规格债。
`release.yml`、正式签名 Secrets 与分发方案留到出包阶段单独提 change。

**每阶段结束必须能编译运行并跑一遍导出备份**，禁止一次性摊开全部页面。

---

## 工作流

本仓库遵循 [`wzzhuz/my-app-workflow`](https://github.com/wzzhuz/my-app-workflow) 的
OpenSpec 规范驱动流程：**需求先对齐，再写码；改完代码，同步更新规格。**
不依赖 OpenSpec CLI 或斜杠命令，任何 AI 工具都能读写同一份规范。
