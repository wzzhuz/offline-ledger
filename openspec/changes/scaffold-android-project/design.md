# 技术设计：scaffold-android-project

## 现状

仓库 `wzzhuz/offline-ledger` 有 21 个文档文件、7 个 spec 模块、
1 个已归档初始 change，**没有任何代码或构建配置**。
`openspec/project.md` 的阶段 0 要求「具体版本组合 SHALL 在阶段 0 的 change 中
查证官方文档后锁定」，本文件兑现这一条。

## 1. 版本组合锁定（本轮核心）

### 查证方法与限制（如实记录）

沙盒对 `dl.google.com` / `repo1.maven.org` / `maven.aliyun.com` 的
`maven-metadata.xml` 请求**全部返回 403**（网络策略拦截，见 `环境能力边界.md` 5.1）。
因此无法用元数据接口逐个核对 artifact 是否存在。

采用的替代证据链，按可信度排序：

1. **同账号已跑通项目 `wzzhuz/lifelog` 的实际构建配置**（通过 GitHub Contents API 读取），
   这是同一时期、同一 CI 环境下真实编译通过的组合，可信度最高
2. **官方文档检索**：Android Developers 的 Kotlin/AGP 兼容表、KSP 版本说明、
   Hilt 配置示例、Google Play 目标 API 要求、SQLCipher for Android 集成文档
3. CI runner 位于境外，可正常解析 Google Maven / Maven Central，
   沙盒 403 不影响实际构建

### 锁定表

| 项 | 版本 | 依据 |
|---|---|---|
| Gradle | 8.13 | `lifelog/gradle/wrapper/gradle-wrapper.properties`；CI 用 `setup-gradle@v4` 显式指定 |
| AGP | 8.13.0 | lifelog 实测；Android Developers「Kotlin 2.3 需 AGP 8.2.2–8.13」 |
| Kotlin / KGP | 2.3.21 | lifelog 实测；官方兼容表 KGP 2.3.20–2.3.21 支持 AGP 8.2.2–9.0.0 |
| KSP | 2.3.11 | lifelog 实测的**独立序号**；KSP 官方确认 2.3.x 起不再与 Kotlin 版本绑定，旧写法 `2.3.21-2.0.2` 的 artifact 不存在 |
| Compose BOM | 2026.06.00 | lifelog 实测；2026.08.00 要求 compileSdk 37 + AGP 9.1.1，刻意降一档 |
| compileSdk / targetSdk | 36 | Google Play 官方：自 **2026-08-31** 起新应用与更新须以 Android 16（API 36）或更高为目标；当前日期已过该期限 |
| minSdk | 26 | `project.md` |
| JDK | 17 | KSP 官方要求 JDK 17+；lifelog CI 用 temurin 17 |
| Room | 2.8.4 | lifelog 实测 |
| Hilt | 2.57.1 | Android Developers 官方 Hilt 文档给出的插件与依赖写法，`ksp(hilt-android-compiler)` 而非 kapt |
| SQLCipher | 4.5.6 (`@aar`) | SQLCipher for Android 官方集成文档给出的 Maven Central 坐标 |
| kotlinx-serialization-json | 1.9.0 | 序列化插件版本跟随 Kotlin 2.3.21；此为**本轮唯一未在同账号项目实测过**的版本，列为风险项 |
| activity-compose | 1.11.0 | lifelog 实测 |
| lifecycle-runtime-compose | 2.9.1 | lifelog 实测 |
| core-ktx | 1.16.0 | lifelog 实测 |

**应急回退分支（写进 tasks）**：若 CI 报某 artifact 不存在，
优先按 `KSP → 2.3.10`（官方 KSP 文档标注的 latest）、
`kotlinx-serialization-json → 1.8.1` 的顺序回退，
**每改一个版本单独推一次**，不攒着一起推（`踩坑清单.md` 1.3：否则报错时无法定位是哪次改动引入的）。

### 刻意推迟引入的依赖

`navigation-compose`、`paging-runtime` / `paging-compose`、
`biometric`、`material-icons-extended` 之外的图标库等，
推迟到各自功能阶段再引入。

**理由**：每多一个未经验证的版本号，首次编译失败概率上一个台阶；
阶段 0 的使命是验证「骨架 + 隐私基线 + 基础类型」这条最小链路，
不是把半年后才用得到的依赖一次配齐。

## 2. 工程结构

```
offline-ledger/
├── settings.gradle.kts          pluginManagement 带 content filter 的 google()
├── build.gradle.kts             顶层只声明插件版本（apply false）
├── gradle/wrapper/              gradle-wrapper.properties + 官方 jar
├── app/
│   ├── build.gradle.kts         签名 / 版本 / 依赖 / Kotlin compilerOptions
│   ├── debug.keystore           提交进仓库，固定签名
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml            无 INTERNET / allowBackup=false
│       ├── res/xml/backup_rules.xml       API ≤31 排除数据库
│       ├── res/xml/data_extraction_rules.xml  API ≥31 排除数据库（含 device-transfer）
│       ├── res/values/{strings,themes}.xml
│       └── java/com/zwz/offlineledger/
│           ├── OfflineLedgerApp.kt        @HiltAndroidApp
│           ├── MainActivity.kt            最小 Compose 占位页
│           ├── core/money/Cents.kt
│           ├── core/time/LedgerClock.kt
│           ├── core/currency/LedgerCurrency.kt
│           └── backup/dto/BackupEnvelope.kt
├── app/src/test/java/.../       Cents / LedgerClock / BackupEnvelope 单测
├── scripts/                     check_kotlin_strings / check_docs_consistency /
│                                check_manifest_privacy
└── .github/workflows/build.yml
```

**包名** `com.zwz.offlineledger`（沿用 `lifelog` 的 `com.zwz.*` 命名）。

### 关于 gradle-wrapper.jar

沙盒无 javac 但有 `keytool` / `java`，无法自行编译 wrapper jar。
从 `gradle/gradle` 官方仓库 `v8.13.0` 取得原始
`gradle/wrapper/gradle-wrapper.jar`（43705 字节，已校验为 zip/JAR）并提交。
CI 仍用 `setup-gradle@v4` 显式指定 8.13（wrapper 损坏也不影响），
jar 主要是为了让使用者能用 Android Studio 直接打开工程。

## 3. 签名方案

沿用 lifelog 已验证的写法，**不覆盖内置的 `debug` SigningConfig**
（`踩坑清单.md` 1.2：AGP 已内置名为 `debug` 的 SigningConfig，
`create("debug")` 会报 already exists）：

```kotlin
signingConfigs {
    create("commonDebug") {
        storeFile = file("debug.keystore")
        storePassword = "offlineledger2026"
        keyAlias = "offlineledgerdebug"
        keyPassword = "offlineledger2026"
    }
}
buildTypes {
    debug   { signingConfig = signingConfigs.getByName("commonDebug") }
    release { signingConfig = signingConfigs.getByName("commonDebug") }
}
```

keystore 由 `keytool -genkeypair -storetype JKS` 生成（显式指定 JKS，
因为 JDK 17 默认 PKCS12），CN 为 `OfflineLedger Debug`。
`build.yml` 增加一步「校验 debug.keystore 存在」与
「`keytool -printcert -jarfile` 打印证书 Owner」。

**已知限制**：提交在仓库里的 debug 密钥等价于公开密钥，
任何拿到仓库的人都能签出同名同签名的包。个人自用足够，
正式分发需改用 Secrets 注入的 release 密钥（出包阶段 change 处理）。

## 4. 隐私基线的可测化

把三条铁律里的第一条从「文档约定」变成「CI 门禁」：

- `scripts/check_manifest_privacy.py` 检查：
  ① Manifest 不含 `android.permission.INTERNET`
  ② `android:allowBackup="false"`
  ③ 两个备份规则 XML 都排除了 `domain="database"`
  ④ 不含 `READ/WRITE_EXTERNAL_STORAGE`
  ⑤ 未引入已知网络 SDK（OkHttp / Firebase / 广告 / 埋点）
- `scripts/check_kotlin_strings.py`：逐行检测未闭合的字符串字面量
  （对应 `踩坑清单.md` 1.1，已犯过两次），用原始字符串生成 Kotlin 源码后必跑
- `scripts/check_docs_consistency.py`：粗筛 `README.md` / `project.md`
  与 `app/build.gradle.kts` 的技术选型关键词是否矛盾

三个脚本在 `build.yml` 里作为独立步骤、失败即中断。

### 备份规则写法（避开已知 Lint 坑）

`踩坑清单.md` 记过：写了 `<include>` 就是白名单模式，
再 exclude 一个没被 include 的路径，Lint 判 fatal，
且 **debug 构建不跑 `lintVital`，只在 release 暴露**。

因此两个规则文件**只写 `<exclude>`、不写 `<include>`**：

```xml
<full-backup-content>
    <exclude domain="database" path="offline_ledger.db" />
    <exclude domain="sharedpref" path="." />
</full-backup-content>
```

`data_extraction_rules.xml` 的 `<cloud-backup>` 与 `<device-transfer>`
两个 section 都写同样的 exclude。
`build.yml` 额外跑一次 `assembleRelease`（含 `lintVital`），
把 Lint 问题在阶段 0 就逼出来，而不是等发版。

## 5. CI 关键参数（逐条对应踩坑清单）

| 参数 | 值 | 对应坑 |
|---|---|---|
| `on.push.branches` | `['**']` | 2.1 静默不跑最危险 |
| `on.push.tags` | `['v*']` | — |
| `permissions` | `contents: write` + `actions: write` | 2.2.1 只写 contents 会让 actions 变 none |
| `upload-artifact@v4` | `overwrite: true` | 2.2.4 同名冲突 |
| `gradle testDebugUnitTest` | 单独一步 | lifelog build.yml 注释：assembleDebug 不碰 src/test |
| `gradle assembleRelease` | 单独一步 | 2.3 debug 不跑 lintVital |
| 签名校验 | `keytool -printcert -jarfile` | 2.4 签名漂移 |
| `setup-gradle@v4` | `gradle-version: '8.13'` | wrapper 损坏不阻塞 |

不引入 lifelog 的 `.github/scripts/prepare-signing.sh` /
`cleanup-artifacts.sh` / `release.yml` / 坚果云上传：
阶段 0 不需要 Secrets 与分发，少一层脚本就少一处失败点。

## 6. 基础类型的设计取舍

- `Cents` 用 `@JvmInline value class`，**不加 `require(value >= 0)`**：
  流水 `amount` 恒正，但账户 `balance`、校准 `deltaCents` 可为负，
  同一个类型要能表达两者；非负校验放到写流水的入口，阶段 1 实现
- 输入/展示用 `BigDecimal.setScale(2, HALF_UP)`，
  `longValueExact()` 而非 `toLong()`，溢出时抛异常而不是静默截断
- `LedgerClock` 固定 `ZoneId.of("Asia/Hong_Kong")`，
  由 `timestamp` 派生 `localDate`(YYYYMMDD) 与 `yearMonth`(YYYYMM)，
  显式兑现 `reporting-ui` spec 的「冗余本地日期列」要求
- `BackupEnvelope` 只定义**头部**（schemaVersion / exportedAt / currencyCode / counts），
  实际数据 DTO 留到阶段 1。**头部一旦定下不再变更**，
  这样阶段 1 往里加字段不会破坏已导出的备份
- `BackupSchema.CURRENT = 1`、`MIN_SUPPORTED = 1`

## 7. 新增 build-and-ci spec 模块

CI 类坑在 `踩坑清单.md` 里占了近一半，却没有任何 spec 覆盖。
新增第 8 个模块，把「全分支触发 / 固定签名 / 权限位 / 单测独立跑 /
release Lint 门禁 / 版本组合单一维护点 / 检查脚本」写成 SHALL 级可测行为约定，
防止半年后有人"顺手优化"掉 `branches: ['**']` 或 `overwrite: true`。

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 直接写骨架不建 change | 少 4 个文档 | 版本组合与签名决策会失去溯源；违反 `AGENTS.md` 的流程 |
| 沿用 v1 的 targetSdk 35 | 不用查证 | Google Play 自 2026-08-31 起要求 API 36；且 `project.md` 要求跟随当年官方要求 |
| 沙盒内试编译验证 | 反馈快 | 无 Android SDK / Gradle / javac，注定失败；`环境能力边界.md` 明确禁止硬试 |
| 不提交 keystore，让 Actions 自己生成 | 少一个二进制文件 | 2.4：每次全新环境重新生成，第二次 APK 签名不同，装不上且数据全丢 |
| 用 `GITHUB_TOKEN` 默认权限 | 配置少 | 2.2.1：默认只读，workflow 里 push / 清理制品静默失败 |
| 只推源码不建 workflow | 本轮交付更快 | 「每阶段结束必须能编译运行」无从验收，把风险推给阶段 1 |
| 保持 7 个 spec 模块，构建约定压进 `project.md` | 模块少 | CI 约束需要可测 Scenario 与「理由」才能防止被顺手改掉；使用者已确认新增模块 |
| 引入 Vico / Coil / Glide / 导航 / 分页全套 | 一次配齐 | 多个未验证版本号叠加，首次编译失败概率陡增；且 `project.md` 明确不引入前三者 |
| 引入 libs.versions.toml 版本目录 | 版本集中管理 | lifelog 未使用、未实测；阶段 0 优先复用已跑通的最简写法 |
| 把 `release.yml` + 坚果云 + Secrets 一起做掉 | 一次到位 | 阶段 0 不需要分发；少一层脚本少一处失败点，出包阶段单独建 change |

## 取舍

选择 **「复用 lifelog 实测版本组合 + 完整最小闭环 + CI 强验证」**：

- **牺牲**：阶段 0 交付文件较多（约 25 个）；Hilt 与 kotlinx-serialization
  这两项未在 lifelog 实测过，首次 CI 可能需要一轮回退
- **换取**：工具链问题在阶段 0 一次性暴露，阶段 1 只需专注 Room 数据层；
  隐私基线从文字变成 CI 门禁

## 风险

**残余风险 1**：`kotlinx-serialization-json:1.9.0` 或 `KSP 2.3.11` 在 CI 上解析失败。
**缓解**：design 第 1 节给出明确回退顺序；每次只改一个版本单独推送；
推完查 Actions 列表确认**确实跑了**，而不是查不到就当通过。

**残余风险 2**：Hilt `2.57.1` 与 Kotlin 2.3.21 / KSP 2.3.11 组合未实测，
KSP 生成代码可能失败。
**缓解**：阶段 0 写的是最小闭环（`@HiltAndroidApp` + `@AndroidEntryPoint`），
让 CI 真实验证代码生成，不留到阶段 1；失败时按日志单独处理，
必要时把 Hilt 代码生成推迟到阶段 1（依赖仍保留）。

**残余风险 3**：`assembleRelease` 的 `lintVital` 因备份规则 XML 判 fatal。
**缓解**：规则文件只写 exclude 不写 include；阶段 0 就跑 release 把问题逼出来。

**残余风险 4**：沙盒无法读取 Maven metadata，版本依据是二手证据。
**缓解**：已在第 1 节如实写明证据链与限制；CI runner 可正常拉依赖，
真实验证由 Actions 完成。

**残余风险 5（首轮 CI 实测新增）**：诊断步骤把整条流水线判红。

| 项 | 内容 |
|---|---|
| **症状** | 三个真正的编译门禁（单元测试 / `assembleDebug` / `assembleRelease` + lintVital）全部 success，但 job 结论为 failure，失败步骤是「输出签名信息」 |
| **根因** | 该步骤写成 `grep -q "OfflineLedger Debug" /tmp/cert.txt && echo ...`。Actions 的 `run` 默认 `bash -e`，`grep -q` 不匹配返回 1，`&&` 列表整体返回非 0 → 步骤失败。次因：`keytool -printcert -jarfile` 只认 v1 JAR 签名，现代 APK 可能只有 v2/v3 签名而不输出 Owner |
| **解法** | 改用 `if grep ...; then ...; else ...; fi`；优先用 `apksigner verify --print-certs`（能读 v2/v3），找不到才退回 keytool 并 `|| true`；证书内容写入日志与 Job Summary |
| **防范** | 写进 `specs/build-and-ci/spec.md` 的 ADDED Requirement「CI 诊断步骤不得因返回码误判」；`run` 里凡是用 `grep -q` / `test` / `git diff --quiet` 做**提示性判断**时，一律用 `if` 而非 `&&`；硬断言才允许 `|| exit 1` |

> 这是本项目新踩到的坑，与 `my-app-workflow/踩坑清单.md` 2.2.3
> 「`git commit` 无变化返回非 0，set -e 下明明构建成功却判失败」同源。
> 本轮不改 `my-app-workflow` 仓库，先记在本 change 的 design 里，
> 待下次整理规范时再决定是否上提。

**日志接口限制**：沙盒读 Actions job logs 返回 403（网络策略），
与 `踩坑清单.md` 5.2 一致。因此没有硬猜证书内容，
而是把诊断输出写进步骤日志与 Job Summary，让修复后的运行自己说话。

**残余风险 5**：debug 密钥随仓库公开（private 仓库内）。
**缓解**：`build.yml` 打印证书 Owner 提示；出包阶段改用 Secrets 注入正式密钥。

## 验证

- [x] 每个 Requirement 至少一个 GIVEN/WHEN/THEN 场景 + 「理由」
- [x] 版本组合锁定表含依据与回退分支
- [x] Manifest 与两个备份规则 XML 只写 exclude
- [x] 生成物中无 token 明文
- [x] Actions 确实触发（28 次 push run，concurrency 取消前 27 次）
- [x] 单元测试 / assembleDebug / assembleRelease(lintVital) 三步 success
- [ ] 修复签名诊断步骤后重跑，job 结论为 success
- [ ] 推送后用远端 API 逐个校验文件与 sha
