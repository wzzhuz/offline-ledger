# 技术设计：deliver-secure-backup-and-settings

## 现状（写码前实际读了什么）

| 已有能力 | 位置 | 阶段 |
|---|---|---|
| `BackupExporter.encode(snapshot): String`（明文，纯 JVM） | `backup/` | 1 |
| `BackupImporter.decode(text): BackupFile`（含版本校验） | `backup/` | 1 |
| `LedgerRepository.importBackup(file)` 单事务全量替换 + 重算 | `data/repository` | 1 |
| `exportSnapshot()` 五类 DTO 全量导出 | `data/repository` | 1 |
| `checkAndRepair()` 自检 + 重算 | `data/repository` | 1 |
| `CategoryEntity.isArchived` / `sortOrder` | `data/local/entity` | **1（预留）** |
| `CategoryDao.insert` / `update` | `data/local/dao` | 1 |
| `DatabaseKeyManager` Keystore AES-256/GCM 保护数据库口令 | `data/local` | 1 |
| `Reconciliation.isTailDifference(delta, threshold = 100)` | `engine` | 3 |
| `reporting-ui` 中文语境配色「或提供配色开关」 | spec | 0 |
| 五个静态检查脚本 | `scripts/` | 0–4 |

**结论**：和阶段 3 / 4 一样，本阶段**零表结构变更、零 Migration、
备份信封四项头部与 `schemaVersion = 1` 一字不改**。
阶段 1 预留 `isArchived` / `sortOrder`、预留事务化导入、预留纯 JVM 编解码，
在这个收口阶段一次性兑现了复利。

## 1. 版本查证（本轮实际联网核实，不凭记忆）

| 项 | 结论 | 依据 |
|---|---|---|
| `androidx.biometric:biometric` 最新**稳定版** | **1.1.0** | AndroidX 官方版本页 2026-04-22 标注 Stable 1.1.0；1.4.0-alpha07 仍是 alpha 线，不用于 v1 |
| 兼容性 | BiometricPrompt 在 API 28+ 走系统弹窗，更低版本走 compat dialog；minSdk 26 满足 | 官方 release notes / 开发者文档 |
| `DEVICE_CREDENTIAL` 与 `BIOMETRIC_STRONG` 组合 | 仅 API 30+ 可用；API 26–29 上组合会失败 | 官方 biometric 指南。**本实现只用 `BIOMETRIC_STRONG`**，降级走自建 PIN，不依赖组合 |
| SAF 是否需要存储权限 | **不需要**。`ACTION_CREATE_DOCUMENT` / `OpenDocument` 由用户参与选择、系统授予 URI 临时读写权限 | Android Developers「从共享存储空间访问文档和其他文件」 |
| `USE_BIOMETRIC` 权限 | normal 级，不弹运行时权限框 | Android 生物识别文档 |

**因此本阶段新增 1 个依赖、1 个 normal 权限，零网络权限、零存储权限。**
`biometric:1.1.0` 传递引入 `androidx.fragment`，`MainActivity` 改继承
`FragmentActivity`（它本身继承 `ComponentActivity`，`setContent` 与
`@AndroidEntryPoint` 均不受影响）。

## 2. 4 份 MODIFIED（比对齐结论多一份，如实说明）

对齐结论说「3 份 MODIFIED」。写码前逐条扫 `privacy-security/spec.md` 时
发现**「导出文件默认加密」也含 CSV 条款**：

> JSON 备份 SHALL 默认口令加密。CSV 导出 SHALL 默认关闭，开启时 MUST 二次确认……

只 MODIFIED `backup-restore` 的「CSV 格式约定」会留下这一处矛盾。
**补第 4 份，宁可多一份也不留两处相反约束。**

| # | 模块 | 被替换的 Requirement | 收口内容 |
|---|---|---|---|
| 1 | `reporting-ui` | 四 Tab 骨架一次成型（**第三次**） | Scenario「后续阶段不改导航」的 `GIVEN 阶段 5 增加备份与应用锁设置` → `GIVEN 后续在设置页新增设置项`，彻底不含阶段号 |
| 2 | `backup-restore` | 设置页提供临时明文导出入口 | → SAF 加密导出 / 导入（口令、二次确认、纯 JVM 编解码） |
| 3 | `backup-restore` | CSV 格式约定 | → 明确 **v1 不提供 CSV 入口**，UTF-8 BOM / 金额两位小数作为 **v1.1 预留格式约定** |
| 4 | `privacy-security` | 导出文件默认加密 | → 只保留 JSON 加密；CSV 条款改为「v1 不提供，v1.1 若实现须二次确认」 |

**同时同步 `project.md`**：阶段 5 表格里的「CSV」改为「CSV 归属确认（v1 不做）」，
「已确认不做」清单中「CSV 导出（默认关闭）」**保持不动**（选 B）。
`reporting-ui`「v1 范围收敛」也保持不动——它本来就把 CSV 列在 v1.1。

**为什么选 B（v1 不做 CSV）**：
CSV 是明文导出，与第一条铁律「导出文件默认加密」张力最大；
且「已确认不做」清单就是防功能悄悄回来的闸门，这次破例它以后就失效了。
使用者在对话里确认「按推荐来」。

## 3. 两套密钥必须分开（最容易写错的一处）

| | SQLCipher 数据库口令 | 备份文件口令 |
|---|---|---|
| 谁生成 | `DatabaseKeyManager`，Keystore AES-256/GCM 保管 32 字节 | 用户在导出时输入 |
| 存哪里 | Keystore + `SharedPreferences` 密文/IV | **不持久化** |
| 换设备 | 失效（重装后 DB 打不开，这是对的） | **必须仍可解密** |
| 用户可见 | 否 | 是 |

**MUST NOT 复用、互相推导，或把备份口令交给 Keystore。**
把 Keystore 口令当备份口令是最隐蔽的自毁：本机导出、本机导入都正常，
换台手机才发现备份永久打不开——那时原设备可能已经没了。

### 加密文件格式（纯 JVM，可单测）

```
[0..4]   MAGIC = "OLBK" + 0x01      5 bytes，明文但不含账目
[5..20]  SALT                      16 bytes，每次随机
[21..32] IV                        12 bytes，每次随机
[33..]   AES-256/GCM ciphertext    Gzip(JSON) 加密，含 128-bit tag
```

- `PBKDF2WithHmacSHA256(passphrase, salt, 120_000, 256)` → `SecretKeySpec(..., "AES")`
- `AES/GCM/NoPadding` + `GCMParameterSpec(128, iv)`
- 编码链：`plainJson: String` → `GZIPOutputStream` → `Cipher.doFinal` → 拼头写出
- 解码链：校验 MAGIC → 取 salt/iv → `cipher.doFinal` → `GZIPInputStream` → UTF-8 String
- 错口令 / 密文篡改 → GCM tag 校验失败 → 抛 **「口令错误或备份文件已损坏」**，
  不泄露是哪种（避免口令探测），且**原账本完全不动**
- 相同明文两次导出密文不同（随机 salt + IV），单测断言这一点

`SecureBackupCodec` **只用 `javax.crypto` / `java.util.zip` / `java.security`**，
不依赖 Android SDK，纯 JVM 单测可直接跑（沙盒无模拟器也能验证往返）。

### ⚠️ 关于「流式导出」的边界（如实说明，不伪装）

`backup-restore` 既有 Requirement 写着「MUST NOT 把全部记录拼成一个超大 String
再写出」。本实现链路是：

```
exportSnapshot()  → BackupExporter.encodeToString()  → 1 个 JSON String
   → GZIPOutputStream（流式）→ Cipher.doFinal → OutputStream（SAF URI，流式）
```

`kotlinx.serialization` 对任意对象图没有内置 `encodeToStream`，
`encodeToString` 仍会一次生成约 10–15 MB 的 JSON String（5 万条）。
但**压缩与加密都是流式的，不产生第二份完整明文副本**，
且写盘走 `contentResolver.openOutputStream` 而非先落本地文件。
真正逐条流式序列化需要自定义 serializer，**留到实测 5 万条出现内存问题时再做**，
本轮不假报「完全流式」。这条边界写进 `tasks.md` 的未完成项。

## 4. SAF 与导入流程

```kotlin
rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"))
rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())
```

- **导出**：用户输入口令（≥6 位）→ 选位置 → `SettingsViewModel.exportTo(uri, passphrase)`
  → `exportSnapshot()` → `encode` → `SecureBackupCodec.encode` → `openOutputStream(uri)` 写
- **导入**：选文件 → **二次确认「将全量替换现有全部数据」** → 输入口令
  → `openInputStream(uri).readBytes()` → `SecureBackupCodec.decode`
  → `BackupImporter.decode` → `LedgerRepository.importBackup(file)`
- 任一步失败：把错误回报给用户，**不调用 `importBackup`**，账本不变
- 导入成功后自动 `rebuildAllBalances()`（已在 `importBackup` 事务内）与 `checkAndRepair()`

**MUST NOT** 用 `File(path)` 写公共目录、`Runtime.exec` 调 openssl、
或申请任何存储权限。

## 5. 应用锁与冷启动门禁

```
MainActivity (FragmentActivity)
├── onCreate: LedgerPrefs → LedgerColors.applyReverseColors() / FLAG_SECURE
└── setContent:
    var unlocked by rememberSaveable { !prefs.appLockEnabled }
    DisposableEffect(LocalLifecycleOwner): ON_PAUSE → unlocked = false
    if (unlocked) LedgerApp() else AppLockScreen(...)
```

- `AppLockScreen` 在解锁前**不渲染任何账目**（不是盖一层模糊遮罩）
- 生物识别：`BiometricManager.canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS`
  时显示按钮；`BiometricPrompt` + `PromptInfo`（标题 / 副标题 / 「改用 PIN」）
- PIN 兜底：`PasswordVisualTransformation` + `KeyboardType.NumberPassword`，
  校验 `LedgerPrefs.verifyPin()`（SHA-256(salt + pin)，salt 存 prefs）
- **API 26–29 不用 `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` 组合**（第 1 节查证），
  生物识别不可用时直接走 PIN
- 启用应用锁时**强制要求先设置 PIN**，否则关掉生物识别就再也进不去
- `FLAG_SECURE` 通过 `window.setFlags` 按偏好设置，防最近任务预览泄露资产页

**PIN hash 的安全边界（如实说明）**：加盐 SHA-256 只防彩虹表与明文存储，
不防 root 设备上的离线暴力破解；这是「防旁边的人瞥一眼 / 防丢手机被随手翻」，
不是对抗有物理接触的攻击者——账本本身已由 SQLCipher + Keystore 保护。
这一层设计意图写进 UI 说明，不让用户误以为是强认证。

## 6. 偏好与配色开关

`core/prefs/LedgerPrefs.kt`（SharedPreferences，零新增依赖）：

| 键 | 默认 | 用途 |
|---|---|---|
| `reconcile_threshold_cents` | 100（1.00 元） | 对账尾差阈值 |
| `reverse_colors` | false | 红 / 绿支出收入配色反转 |
| `flag_secure` | false | 最近任务防泄露 |
| `app_lock_enabled` | false | 冷启动 / 后台恢复校验 |
| `pin_salt` / `pin_hash` | 空 | 加盐 PIN hash |

**为什么配色开关不用 CompositionLocal 重写 80 处引用**：
`LedgerColors.ExpenseRed` / `IncomeGreen` 改成 `mutableStateOf` -backed 属性，
读取发生在 composition / draw phase 时会订阅并重组/重绘，
**所有现有引用点一行不改**。改动只在 `LedgerTheme.kt` 一个文件 +
`MainActivity.onCreate` 调一次 `applyReverseColors()`。
`Neutral` / `Primary` 保持常量不变（语义色才反转）。

**偏好不进 Room、不进备份 DTO**：换设备后重新设置即可，
避免为一个布尔值写 `Migration(1→2)` 并把设置项混进账本备份。

## 7. 阈值可配置（三处同步，不能只改 UI）

| 层 | 改动 |
|---|---|
| `engine/Reconciliation.kt` | 注释「阶段 5 再做可配置 UI」→ 已落地；常量保留作默认值 |
| `data/repository/LedgerRepository.reconcile(...)` | 加 `thresholdCents: Long = 100L`，`require` 用它判尾差 |
| `ui/reconcile/ReconcileViewModel` | 构造注入 `LedgerPrefs`，`setActual` 与 `reconcile` 都传偏好阈值 |

只改 UI 不改 Repository 会出现「UI 说 ≤5 元可一键抹平、Repository 却按 1 元拒绝」，
这正是本项目一直强调的「同一件事不能有两个地方算」。

## 8. 分类管理（复用预留字段，零 Migration）

`CategoryDao` 新增 3 条只读/写查询：`rename(id,name)`、`archive(id)`、
`updateSortOrder(id,order)`（`insert` / `update` 阶段 1 已有）。

`LedgerRepository` 新增 **4 个写方法**，全部 `db.withTransaction`：

| 方法 | 约束 |
|---|---|
| `createCategory(c)` | 校验 `level ∈ {0,1}`、`parentId` 非空仅当 level=1、父级 level=0 且 `type` 一致 |
| `renameCategory(id,name)` | 名称非空 |
| `archiveCategory(id)` | 只置 `isArchived = 1`，**不物理删除**，不改任何流水 `rootCategoryId` |
| `reorderCategories(ids)` | 批量更新 `sortOrder`，事务末尾重算兜底 |

`check_transaction_annotations.py` 的 `WRITE_METHODS` **13 → 17**，
否则新写方法漏检。分类管理 UI 放设置页内 Dialog，分一级 / 二级两组展示，
支持新增、改名、上移下移、归档（已归档项灰显）。

## 9. 测试策略与如实说明的限制

| 层 | 验证方式 | 覆盖 |
|---|---|---|
| `SecureBackupCodec`（纯 JVM） | JUnit | 往返、错口令、密文篡改、MAGIC 不匹配、文件截断、短口令拒绝、密文不含明文、两次导出不同 |
| 备份格式兼容 | 既有 `BackupCodecTest` | 信封头部 / 锚点往返保持 `schemaVersion = 1` |
| Manifest / 权限 | `check_manifest_privacy.py` 扩展 | 新增 `ALLOWED_PERMISSIONS` 白名单：除 `USE_BIOMETRIC` 外任何 `<uses-permission>` 都判失败；仍禁 `INTERNET` / 存储权限 |
| 事务 | `check_transaction_annotations.py` | 17 个写方法全在 `withTransaction` 内 |
| Compose / Room / Biometric API | CI `assembleDebug` + `assembleRelease`（lintVital） | FragmentActivity、BiometricPrompt 签名、SAF contract |
| 真机 | **本阶段仍无法验证** | 5 万条切月 / 年报聚合耗时、滚动掉帧、SQLCipher 真机打开、BiometricPrompt 真机弹窗、SAF 真机文件选择器 |

**应用锁的失败路径比成功路径重要**：解锁前不能渲染账目、
后台恢复要重新锁、PIN 未设置不允许开生物识别锁。
沙盒无法跑 UI 自动化测试，靠代码结构 + 静态检查 + 单测覆盖计算层，
UI 行为留到有设备时实测，不假报。

### CI 失败时的回退顺序（每次只改一处、单独推送）

1. `biometric:1.1.0` 与 Compose BOM / fragment 版本冲突 →
   显式加 `androidx.fragment:fragment-ktx:1.8.x` 对齐，或退到 1.0.1
2. `FragmentActivity` + `@AndroidEntryPoint` 或 `setContent` 不兼容 →
   MainActivity 保持 `ComponentActivity`，改用 `BiometricPrompt(Fragment,...)` 或
   只保留 PIN 兜底（生物识别降级为设置项）
3. `PromptInfo.Builder.setAllowedAuthenticators` 签名差异 →
   去掉该行，用 `canAuthenticate` 前置判断
4. `GCMParameterSpec` / `PBKDF2WithHmacSHA256` 在单测 JVM 不可用 →
   改 `PBKDF2WithHmacSHA1`（仍安全）或把编解码测试标为 androidTest
5. `mutableStateOf` 在 object 中的属性委托编译问题 →
   改显式 `get() = _x.value` / `set(v)`（本实现已直接用显式写法）
6. `CreateDocument("application/octet-stream")` 契约构造签名差异 →
   改用 `Intent.ACTION_CREATE_DOCUMENT` + `rememberLauncherForActivityResult(StartActivityForResult())`
7. `check_manifest_privacy.py` 新白名单误判 → 先按「精确匹配 `<uses-permission>` 标签」
   复核，别放宽成子串（阶段 1 归档自误报两次的教训）
8. Kotlin 字符串转义 / 三引号块 → 跑 `check_kotlin_strings.py` 与逐文件复核

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 不建 change 直接写设置页 | 少 4 个文档 | 4 份 MODIFIED 与 CSV 决策会失溯源；违反流程 |
| 按 `project.md` 阶段 5 把 CSV 做进去 | 贴合 v2 原话「方便 Excel 打开」 | **使用者拍板 B**；CSV 是明文导出，与第一条铁律张力最大；「已确认不做」清单不能破例 |
| 用 Keystore 口令当备份口令 | 少一个口令输入 | 换设备即永久打不开，最隐蔽的自毁 |
| 口令 / PIN 明文存 SharedPreferences | 实现最短 | 违反 `privacy-security`；PIN hash 至少加盐 |
| `Runtime.exec("openssl ...")` | 不用写 JCE 代码 | 系统无 openssl 保证、参数注入风险、不可单测 |
| `File(path)` 写公共目录 | 路径直观 | Scoped Storage 必炸且要存储权限；SAF 官方明确无需权限 |
| 偏好存 Room 新表并纳入备份 | 「数据统一」 | 要写 `Migration(1→2)`、改 `schemaVersion`、把设置项混进账本 |
| 应用锁做成第 5 个 Tab / 只放开关 | 少改 MainActivity | 等于没锁；违反 4 Tab spec |
| 应用锁只在冷启动拦，不拦后台恢复 | 少一个 Lifecycle observer | 切出去再回来账目直接可见，最近任务泄露风险仍在 |
| 配色开关用 CompositionLocal 重写全部引用 | 架构更「正统」 | 要改 80+ 处引用、DrawScope 与非 @Composable 边界复杂；mutableStateOf 一处改动即可 |
| 物理删除分类 | 实现最短 | `project.md` 永久不做；会产生孤儿流水 |
| 只追加 ADDED 不改 3 处规格债 | 归档脚本简单 | specs 会同时存在「明文导出」与「加密导出」等相反要求 |
| 顺手做 v1.1 模板 / 预算 / 多币种 | 进度看起来快 | 范围蔓延是个人项目烂尾首因；v1 收敛更值钱 |
| 引入 OkHttp / 图片库 / 崩溃上报 | 省事 | 传递引入网络栈，直接违反第一条铁律 |

## 取舍

选择 **「SAF + 用户口令 PBKDF2 派生 + AES-GCM + 复用既有导入事务 +
BiometricPrompt/PIN 冷启动门禁 + SharedPreferences 偏好 + 4 份 MODIFIED」**：

- **牺牲**：纯 JVM 单测覆盖不到 SAF 与 BiometricPrompt 的 UI 行为；
  导出 JSON 仍有一次 `encodeToString`；新增 1 个依赖与 1 个权限；
  v1 不提供 CSV
- **换取**：零 Migration、备份信封零改动、导航零改动、零网络与存储权限；
  换设备可恢复备份；明文导出入口被彻底替换；三处规格债一次收口；
  加密层可在无模拟器环境里验证往返与错口令

## 实现期补充（如实记录与写作稿的偏离）

**① 配色开关的实现比写作稿更轻**：写作稿说「`mutableStateOf` 承载语义色」，
落地就是把 `LedgerColors.ExpenseRed` / `IncomeGreen` 改成显式
`get() = _x.value` / `private set` 的属性，**其余 80+ 处引用一行未改**。
不用 CompositionLocal、不改 `LedgerTheme` 的调用签名，
`MainActivity.onCreate` 调一次 `applyReverseColors()` 即可。
这是本阶段最省事的一处，也验证了「语义色集中在一个 object」这个阶段 0 的取舍。

**② `SecureBackupCodec` 的错误提示统一**：错口令、GCM tag 校验失败、
Gzip 解压失败三者都归到同一句「口令错误或备份文件已损坏」，
避免让攻击者区分「口令错」与「文件坏」。单测断言这一点。

**③ `SettingsViewModel` 的 Context 不持有**：只在 `exportTo` / `importFrom`
的参数里取 `contentResolver`，ViewModel 不存 Context，无 Activity 泄漏。

**④ 阈值三层同步实际改了 4 个文件**：`Reconciliation.kt`（注释）、
`LedgerRepository.reconcile`（新增 `thresholdCents` 参数）、
`ReconcileViewModel`（注入 `LedgerPrefs`）、`ReconcileDialog`（构造参数）。
写作稿说「三处同步」，实际连常量注释与 Dialog 工厂一共 4 个文件。
`ReconciliationTest` 的既有断言仍用默认 100 分，不需要改。

**⑤ `WRITE_METHODS` 确实扩到 17 个**：4 个分类写方法全部 `db.withTransaction`，
`createCategory` 在事务内校验层级与类型继承；`reorderCategories` 事务末尾
照例 `rebuildAllBalancesInternal()` 兜底（排序不改账，但保持统一写路径）。

**⑥ `check_manifest_privacy.py` 白名单用正则精确匹配标签**：
`re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', m)`，
且 `m` 已先剥掉 XML 注释 —— 沿用阶段 1 归档两次自误报换来的规则，
没有写成宽泛子串搜索。

**⑦ 纯 JVM 单测里 `expectFailure` 用 JUnit `fail()` + `catch IllegalArgumentException`**，
不依赖 `kotlin-test` 或 `assertThrows` 的版本差异。

## 首轮 CI 实测：挂在自己写的测试口令长度，修一次全绿

| commit | run | 结论 |
|---|---|---|
| `2cfa122856`（提案文档） | — | 被后续 concurrency 取消，属正常 |
| `9e775c0e04`（阶段 5 实现） | `43363414260` | **failure**：五个静态检查 / keystore / Gradle 配置全 success，**挂在「运行单元测试」**，debug 与 release 步骤被 skip |
| `7483e9e6f6`（修复） | `43364038705` | **success**：五个静态检查、keystore 校验、单元测试、`assembleDebug`、`assembleRelease`（lintVital）、签名诊断、制品上传全部 success，产出 `offline-ledger-debug-apk` **23,479,144 bytes** |

**根因不是加密实现**：`SecureBackupCodec` 要求口令至少 6 位（按 `CharArray.size`），
而 `chinese text survives the round trip` 用了 `"口令密码"` —— **4 个中文字符**。
`require` 正确拒绝，是测试自己写错了常量。改成 7 字符的 `"中文口令123"` 后全绿。
主源码、Compose / Room / BiometricPrompt / SAF / FragmentActivity **零编译错误**，
`design.md` 第 9 节预留的 8 条回退顺序一条都没用上。

**这是本仓库第 5 次「凭印象手写常数」类错误**：

| 阶段 | 错误 |
|---|---|
| 1 | 净资产期望值写成 147000（应 247000） |
| 2 | 筛选维度设 7 个却断言 8 |
| 3 | 构造 `LedgerSnapshot` 漏传 3 个必填字段 |
| 4 | 归档文档手写「168 个 Scenario」（实际 166）；**代码首轮全绿** |
| **5** | **中文口令 4 字符 < 最小 6 位** |

共同模式：**约束写在代码里，测试却凭语感构造输入**。
往后凡是「长度 / 个数 / 金额」类测试输入，先回读一次被测函数的 `require` 再写值。
这条已写进本 change 的 `design.md`，建议上提 `my-app-workflow/踩坑清单.md`。

**版本查证被 CI 证实**：`androidx.biometric:biometric:1.1.0` 与
AGP 8.13.0 / Kotlin 2.3.21 / Compose BOM 2026.06.00 / activity-compose 1.11.0
零冲突；`FragmentActivity` + `@AndroidEntryPoint` + `setContent` 组合可用；
`PromptInfo.Builder.setAllowedAuthenticators` 与 `CreateDocument(String)` 契约
签名均与查证一致。制品体积从阶段 4 的 22,712,565 增至 23,479,144 bytes
（+766,579，主要是 biometric 及其传递的 fragment AAR）。

**注意**：这不等于真机验证。5 万条切月 / 年报聚合耗时、滚动掉帧、
SQLCipher 真机打开数据库、BiometricPrompt 真机弹窗、SAF 真机文件选择器
仍未实测，见第 9 节测试边界。

## 风险

**残余风险 1**：`androidx.biometric:1.1.0` 传递的 fragment / activity 版本
与 `activity-compose:1.11.0` 冲突。
**缓解**：第 9 节回退顺序第 1 条；CI 首轮即可暴露。

**残余风险 2**：`FragmentActivity` 改继承后 Hilt `@AndroidEntryPoint`
或 `setContent` 的扩展解析异常。
**缓解**：第 2 条；必要时退回 `ComponentActivity` 并降级生物识别。

**残余风险 3**：PIN hash 只是加盐 SHA-256，不是强认证。
**缓解**：第 5 节已写明设计意图（防瞥视 / 防丢机随手翻），
账本本体由 SQLCipher + Keystore 保护；UI 明确说明，不夸大。

**残余风险 4**：导出 5 万条时 `encodeToString` 峰值内存。
**缓解**：第 3 节已说明边界；压缩 / 加密 / 写盘流式，
不产生第二份明文副本；真机实测后再决定是否自定义流式 serializer。

**残余风险 5**：`check_manifest_privacy.py` 白名单若写成子串匹配会自误报。
**缓解**：第 7 条；精确匹配 `<uses-permission>` 标签并先剥注释。

## 验证

- [x] 阶段 0 五项对齐结论齐全、分级判定已告知并等确认
- [x] 阶段 4 已按规范归档，无进行中 change 冲突
- [x] 使用者确认「按推荐来」：CSV 不做 / BiometricPrompt+PIN / SharedPreferences
- [x] 4 份 MODIFIED 写出完整替换文本，标题与原 Requirement 一致
- [x] Delta 每个 Requirement 至少一个 GIVEN / WHEN / THEN 场景 + 「理由」
- [x] CI 全绿：run 43364038705（commit 7483e9e6f6）**修复后 success**，
  五个静态检查 / keystore 校验 / 单测 / debug APK / release(lintVital) / 签名诊断 / 制品上传 全部 success
- [x] 推送后用远端 Git Trees API 逐文件比对 blob sha，PASS
- [x] 产物 offline-ledger-debug-apk 23,479,144 bytes
- [ ] 推送后用远端 Git Trees API 逐文件比对 blob sha
- [ ] 使用者确认后归档本 change
