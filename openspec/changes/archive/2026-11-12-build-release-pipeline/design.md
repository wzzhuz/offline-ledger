# 技术设计：build-release-pipeline

## 现状（写码前实际读了什么）

| 已有能力 | 位置 | 阶段 |
|---|---|---|
| `build.yml`：push 全分支触发、五个静态检查、单测、debug APK、release lintVital、签名诊断、Artifacts | `.github/workflows/` | 0 |
| `debug.keystore` 固定签名（`commonDebug`，密码与别名写死在仓库里） | `app/build.gradle.kts` | 0 |
| `app/proguard-rules.pro` **仍是占位注释** | `app/` | 0 |
| `versionCode = 1` / `versionName = "0.1.0"` 写死 | `app/build.gradle.kts` | 0 |
| `isMinifyEnabled = false`（debug 与 release 都是） | `app/build.gradle.kts` | 0 |
| 五个静态检查脚本 | `scripts/` | 0–5 |
| `check_manifest_privacy.py` 的 `ALLOWED_PERMISSIONS` 白名单 | `scripts/` | 5 |

**通过 API 实际读取的 lifelog 参照物**：`release.yml`、`release-preview.yml`、
`build.yml`、`cleanup.yml`、`prepare-signing.sh`、`cleanup-artifacts.sh`、
`app/proguard-rules.pro`、`app/build.gradle.kts`。不是凭印象复刻。

**结论**：本阶段**零 Kotlin 业务代码改动、零表结构变更、零 Migration、
备份格式与 `schemaVersion` 一字不改、4 Tab 导航零改动**。
改动集中在 `.github/`、`app/build.gradle.kts`、`app/proguard-rules.pro`
与文档。

## 1. 版本查证与实际取舍

| 项 | 结论 |
|---|---|
| `workflow_dispatch` 的 choice option `'on'` | **必须加引号**。裸写 `on` 被 YAML 1.1 解析成布尔 `true`，与 `default` 的字符串类型对不上，GitHub 会**静默不注册**该工作流（文件在仓库里、Actions 页面却找不到）——lifelog `release-preview.yml` 的注释里记过这个坑 |
| GitHub Release 资产名 | **纯 ASCII**。实测用 API 上传「生活手记-v9.9.9.apk」，返回的资产名是「-v9.9.9.apk」，中文被静默丢弃且不报错。中文只放 Release 标题与说明 |
| `upload-artifact@v4` | 同名冲突需 `overwrite: true`；`permissions` 块写了就覆盖全部默认值，**必须同时写 `contents: write` 与 `actions: write`** |
| 坚果云 WebDAV | 必须用「**应用密码**」（网页端 → 账户信息 → 安全选项 → 第三方应用管理 → 添加应用密码），**不是登录密码**；2023 年起登录密码直连被拒。免费版每月上传 1GB / 下载 3GB、单文件 500MB |
| `grep -oP` | 依赖 PCRE 编译选项，换镜像就可能不支持；**改 Python 正则**提取版本号 |
| `bash -e` + curl | 网络层失败会让赋值语句直接退出，真实 HTTP 状态码没机会打印；curl 赋值加 `\|\| true`，认证测试单独判 `207/200/201` |
| R8 与 `kotlinx.serialization` | 官方要求保留 `$$serializer`、`Companion`、`serializer(...)`；AGP 的 `proguard-android-optimize.txt` 不含这些，必须补 |
| `mapping.txt` 路径 | `app/build/outputs/mapping/release/mapping.txt`（R8 开启时才产生） |

## 2. 相对 lifelog 的三处改良（不是照抄）

| 项 | lifelog | 本项目 |
|---|---|---|
| 坚果云触发条件 | `if: inputs.create_release && inputs.upload_nutstore`（不打 tag 就不传） | **解耦**：只认 `upload_nutstore`，出个不打 tag 的包也能传；`create_release` 默认仍 true |
| 签名 | 「校验签名信息」命中 `CN=LifeLog Debug` 就 `exit 1` | **缺失 Secrets 只告警不中断**：本项目 keystore 随仓库、历次签名一致可覆盖安装，个人自用不该被 4 个 Secret 卡住发版 |
| R8 开关 | 只在 `release-preview.yml` 支持 `both` 对比 | `release.yml` 直接提供 `none / on / both`，**默认 `on`**，一次就能看到体积差 |

另外两处适配：
- **签名诊断用 `apksigner verify --print-certs`**，不用 `keytool -printcert -jarfile`
  （后者只认 v1 JAR 签名，现代 APK 可能只有 v2/v3 而不输出 Owner）——
  这是本仓库阶段 0 真实踩过的坑，`build.yml` 里已有修正版，本 change 沿用
- **`build.yml` 的五个静态检查与单测在 release 工作流里也跑一遍**：
  release 包是最终交付物，不能只靠「能编出来」就发

## 3. R8 keep 规则（本阶段最关键的一处）

`app/proguard-rules.pro` 从占位注释替换为完整规则，逐条对应本项目的
反射 / 生成代码 / JNI 边界：

| 边界 | 规则要点 | 不补会怎样 |
|---|---|---|
| `kotlinx.serialization` | 保留 `$$serializer`、`Companion`、`INSTANCE`、`serializer(...)`、`@Serializable` 注解属性 | **备份 JSON 导出/导入全崩**，且是运行时才炸，CI 编译看不出来 |
| 备份 DTO | `com.zwz.offlineledger.backup.dto.**` 的字段名与成员 | 同上，字段名被改名后旧备份读不出来 |
| Room | 保留 `androidx.room.**` 与生成的 `_Impl`；Room 自带 consumer rules，这里补 Entity / TypeConverter 边界 | DAO 实现被裁掉、迁移失败 |
| Hilt / Dagger | 保留 `dagger.hilt.**`、生成组件、`@AndroidEntryPoint` 相关；Hilt 自带 consumer rules | 冷启动注入失败 |
| SQLCipher JNI | `-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }` + 保留 `net.sqlcipher.**` 的类名 | **native 方法签名对不上，一打开数据库就 UnsatisfiedLinkError** |
| 枚举 | `AccountType` / `RecordType` 的 `values()` / `valueOf(String)` 与常量名 | Room TypeConverter 的 `valueOf` 找不到常量 |
| 行号 | `-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute` | `mapping.txt` 可读性下降 |

**如实说明的边界**：多数 AndroidX 库自带 `META-INF/proguard/*.pro`
consumer rules，AGP 会自动合并；上表是**防御性补全**，不是每条都必需。
R8 full mode 下 `kotlinx.serialization` 的规则尤其不能省。

**CI 构建成功 ≠ release 包真机能打开**。R8 出问题的典型表现是
「装得上、一启动就崩」或「打开数据库时崩」，沙盒没有模拟器，
只能靠 keep 规则 + CI 编译 + `mapping.txt` 制品兜底，
**真机验证必须留给使用者**。这条写进验收说明，不粉饰。

## 4. Gradle 开关与版本号机制

```kotlin
// 唯一手动维护点：发版时改这里
val defaultVersionName = "0.1.0"
fun versionCodeFrom(name: String): Int  // major*10000 + minor*100 + patch
val versionNameValue = (findProperty("versionNameOverride") as String?)
    ?.takeIf { it.isNotBlank() } ?: defaultVersionName
val versionCodeValue = (findProperty("versionCodeOverride") as String?)
    ?.toIntOrNull() ?: versionCodeFrom(versionNameValue)

// R8 默认关闭，只有 -PminifyRelease=true 才开
val minifyRelease = (findProperty("minifyRelease") as String?)?.toBoolean() ?: false
```

- `build.yml` 不带 `-PminifyRelease`，**日常 CI 行为完全不变**
- `release.yml` 的 `minify=on` 传 `-PminifyRelease=true`，
  `none` 不传，`both` 两次编译分别不传 / 传
- `isShrinkResources = minifyRelease` 与 `isMinifyEnabled` 同步
- CI 用 `-PversionNameOverride` / `-PversionCodeOverride` 覆盖版本号
  **并传给 Gradle**（lifelog 注释里记过：只作用于文件名和 Release 标题，
  APK 内仍是旧值，是最隐蔽的一类 bug）
- 版本号覆盖**不回写文件**，CI 打一行 warning 提醒发版后同步
  `defaultVersionName`

## 5. 签名：可选正式密钥，缺失降级

`.github/scripts/prepare-signing.sh` 与 lifelog 同构，但环境变量前缀
改为 `OFFLINELEDGER_*`，解码后的 keystore 落在 `$RUNNER_TEMP`：

| Secret | 用途 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | keystore 的 base64 |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | 别名 |
| `KEY_PASSWORD` | 私钥口令（PKCS12 下必须与 storepass 相同） |

四项**全齐**才启用 `signingConfigs.create("official")`；
缺任一则脚本打印缺失清单、输出 `using_release=false`，
`app/build.gradle.kts` 回退 `commonDebug`，流程继续。
「校验签名信息」步骤用 `apksigner` 打印证书：
- 用了正式密钥 → 打印「✅ 已使用正式密钥签名」
- 回退 debug → 打印 `::warning::`「随仓库公开的密钥，仅供个人自用，勿对外分发」，
  **不 `exit 1`**

理由：本项目从阶段 0 起就把 `debug.keystore` 提交进仓库，
所有历史包与它同签名、可直接覆盖安装且不丢数据。
个人自用不该因为「忘配 4 个 Secret」就发不出包；
真要对外分发时，补上 Secrets 重跑一次即可。

## 6. 坚果云上传（四步 + 诊断）

```
【1/4】PROPFIND https://dav.jianguoyun.com/dav/   → 207/200/201 = 认证通过
【2/4】MKCOL /my-apps/                            → 201 新建 / 405 已存在
【3/4】MKCOL /my-apps/offline-ledger-releases/    → 201 / 405
【4/4】curl -T 上传 .apk                          → 201 新建 / 204 覆盖
       HEAD 校验                                  → 200 = 确实落盘
```

- 全程日志写进 `GITHUB_STEP_SUMMARY`，用 `EXIT trap` +
  `DUMPED` 去重，避免「失败了但 summary 一片空白」
- `if: inputs.upload_nutstore`，**与 `create_release` 解耦**
- 未配 `JGY_USER` / `JGY_APP_PASSWORD` 时打印补齐路径并
  `echo "skipped=true" >> $GITHUB_OUTPUT; exit 0`，job 仍 success
- 401/403 明确提示「必须用应用密码，不是登录密码」；
  `000` 提示「runner 在美国，跨境链路抖动，换个时间重跑」
- URL 用 `python3 -c 'urllib.parse.quote(sys.argv[1])'` 编码，
  **不读 `os.environ`**（`DIR` / `PARENT` 是未 export 的 shell 变量，
  子进程看不到会抛 `KeyError`——lifelog 注释里记过）

## 7. 静态检查新增一项：workflow 语法

`scripts/check_workflow_syntax.py`（纯文本，不依赖 PyYAML）检查
`.github/workflows/release.yml`：

1. 文件存在且 `workflow_dispatch` 已声明
2. choice 的 `on` / `none` / `both` **都带引号**（防 YAML 1.1 布尔化）
3. `permissions:` 同时含 `contents: write` 与 `actions: write`
4. 产物名 ASCII、含 `offline-ledger-v`
5. `upload-artifact@v4` 带 `overwrite: true`
6. 不含硬编码的坚果云账号 / 应用密码 / keystore 口令字面量

已接入 `build.yml` 的「静态检查」步骤，
与现有五个检查一起在每次 push 时跑。
沿用阶段 1 归档换来的规则：**精确匹配、先剥注释**，
别宽泛搜子串造成自误报。

## 8. 测试策略与如实说明的限制

| 层 | 验证方式 | 覆盖 |
|---|---|---|
| workflow 语法 | `check_workflow_syntax.py` + Actions 页能看到工作流 | YAML 布尔化、权限、ASCII 名、overwrite |
| 构建 | 手动触发一次 `release.yml`，`minify=both` | 五个静态检查、单测、两次 `assembleRelease`、lintVital、签名诊断、Artifacts、`mapping.txt` |
| 体积对比 | Job Summary 打印 plain / minified 的字节数与差值 | R8 确实生效 |
| 坚果云 | 未配 Secrets 时跳过且 job success | 降级路径 |
| **真机** | **本阶段无法验证** | **R8 包能否启动、SQLCipher 打开数据库、BiometricPrompt 弹窗、SAF 文件选择器、5 万条切月 / 年报聚合耗时** |

**手动触发时 `create_release=false`**：只验证编译、制品与坚果云路径，
不擅自打 tag / 占版本号；想要正式发布，在 Actions 页再跑一次并勾选
`create_release`。这一条会写进交付说明。

### CI 失败时的回退顺序（每次只改一处、单独推送）

1. R8 报 `Missing class` / 序列化 `$serializer` 找不到 →
   补 `proguard-rules.pro`，必要时临时 `-dontwarn` 并单独记录
2. `isShrinkResources` 与 Compose 资源冲突 →
   先单独关 shrinkResources 验证是 R8 还是资源压缩的问题
3. `prepare-signing.sh` 环境变量对不上 → 核对
   `OFFLINELEDGER_*` 前缀与 `build.gradle.kts` 读取名一致
4. `apksigner` 在 runner 上找不到 → 沿用 `build.yml` 的
   `find $ANDROID_HOME/build-tools ... | sort -V | tail -n 1`
5. `minify=both` 第二次 `assembleRelease` 命中 Gradle UP-TO-DATE →
   两次编译之间 `rm -rf app/build/outputs/apk/release`
6. 坚果云 `PROPFIND` 返回 000（跨境抖动）→ 与代码无关，换时间重跑，
   不为了它改 workflow
7. workflow 静态检查自误报 → 先按「精确匹配 + 先剥注释」复核，
   别放宽成子串（阶段 5 归档第 6 次自误报的教训）

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 不建 change 直接写 workflow | 少 4 个文档 | 决策失溯源，违反流程 |
| 坚果云账号 / 密码写死进 yml | 少配 Secrets | 凭据进仓库，直接违反隐私铁律 |
| 用坚果云**登录密码**直连 WebDAV | 少一步 | 2023 起被拒，必须用应用密码 |
| 中文 APK 文件名（`离线账本-v0.1.0.apk`） | 更亲切 | GitHub Release 资产接口静默丢弃中文，不报错 |
| 把 R8 开在 debug buildType | 每次 push 都验证 | 没意义、拖慢日常 CI、keep 规则有缺就天天红 |
| Gradle 侧 `minifyRelease` 默认 `true` | 门禁更强 | `build.yml` 每次 push 行为被改变，超出本阶段范围 |
| 强制要求正式签名 Secret 否则 `exit 1` | 更像「正式发布」 | 个人自用会卡死发版；本项目 keystore 本就随仓库 |
| 把 CI 的网络请求当成违反隐私铁律 | 看起来更严格 | runner 上传的是**安装包**，账本数据从不出设备；混淆两者会做出错误设计 |
| 顺手做 `cleanup.yml` / `release-preview.yml` / AAB | 一套齐全 | 范围蔓延；使用者本轮只点名 `release.yml` + 坚果云 + R8 |
| 只交 yml 不补 proguard keep 规则 | 最快 | 等于埋一个「装得上、一启动就崩」的包 |
| 手动触发时 `create_release=true` | 一次跑全流程 | 会擅自打 tag、占版本号；验证阶段不该有副作用 |

## 取舍

选择 **「参照 lifelog 的 release.yml + 默认 R8 + Secrets 缺失降级 +
坚果云上传与打 tag 解耦 + 全套 keep 规则 + workflow 语法检查」**：

- **牺牲**：真机行为无法在沙盒验证；新增 3 个 `.github/scripts/` 与
  1 个检查脚本；release 工作流比 debug 长
- **换取**：包体积显著下降；版本号不再恒为 1；正式签名可选且不阻塞；
  缺 Secrets 照样出包；坚果云不打 tag 也能传；R8 崩溃有 `mapping.txt` 可查；
  日常 `build.yml` 行为完全不变

## 首轮静态检查实测：第 7 次「检查器自误报」

新增的 `check_workflow_syntax.py` 第一次跑就 FAIL：

```
- 产物名不是 ASCII 的 offline-ledger-v*：out/${{ steps.rename.outputs.apk_name }}
```

**不是产物名**：命中值是签名诊断步骤里的**路径变量**
`APK="out/${{ steps.rename.outputs.apk_name }}"`，不是用于命名制品的
`APK="offline-ledger-v${VER}-minified.apk"`。
检查器用正则 `APK="([^"]+)"` 把路径变量当成了产物名。

**双修正**：
① 把该变量改名 `APK_PATH`，从命名上消除歧义；
② 检查器跳过含 `/` 或 `${{` 的值，只校验**纯产物名**——
检查力度未下降（真正的中文产物名仍会被抓到），
沿用「精确匹配、别把元信息当行为」的一贯规则。

这是本仓库第 7 次同类坑，前六次分别是：宽泛搜 `isDeleted = 0`、
宽泛搜 `BALANCE_ADJUST`、脚本半途异常后未确认落盘、
按分支名查 tree 的最终一致性延迟、手写常数 168→166、
新 MODIFIED 的「理由」段引用旧 Scenario 原文。
共同模式始终是**检查器比被检查对象更容易出错**。

## 核验通道实测：job logs / annotations / artifact zip 全部 403

首次 release run 全绿后，想从 API 读取 Job Summary 里的 plain/minified
精确字节数，四条路都走不通：

| 通道 | 结果 |
|---|---|
| `GET /actions/runs/{id}/jobs/{job_id}/logs` | 403 |
| `GET /check-runs/{id}/annotations` | 403 |
| `GET /commits/{sha}/check-runs` | 403 |
| `GET /actions/artifacts/{id}/zip`（含手动处理 302 后裸请求 Location） | 302 → 403 |
| `GET /actions/secrets`（只想知道坚果云 Secrets 配没配） | 403 |

这是 fine-grained PAT 的权限边界，不是代码问题。
阶段 1 的诊断靠「临时把日志 commit 回 `diag/`」绕过，但那会往仓库写文件。
**本 change 用更干净的办法**：新增「上传构建指标」步骤，把
`plain_bytes` / `min_bytes` / `skipped` 编码进 artifact **名称**
（`offline-ledger-metrics-v0.1.0-pNNNN-mNNNN-nsXXX`），
artifacts 列表 API 能读到名字，于是无需下载 zip 就能核验体积对比
与坚果云上传状态。`if: always()` + `continue-on-error: true`，
不让这个辅助步骤拖垮主流程。

## 实测：两次 release run 全绿，R8 减少 24.9%

| run | commit | 结论 | 说明 |
|---|---|---|---|
| `43379471531` | `de24fc5ff1` | **success** | 首次触发，`minify=both`、`create_release=false`、`upload_nutstore=true`；24 步全绿 |
| `43381245320` | `abb1655475` | **success** | 补「构建指标制品」后复跑，确认改动无回归 |

- **工作流注册**：`编译 Release 包` id=416950295，state=**active**。
  在 Actions 页能看到，不是「查不到=通过」。
- **push CI**：`43378544043` success，六个静态检查 + 单测 + debug APK +
  release lintVital 全绿。
- **R8 体积对比**（同一次构建唯一变量就是 `minify`，对比干净）：

  | | bytes | MiB |
  |---|---|---|
  | plain | 31,964,607 | 30.48 |
  | minified | 24,017,315 | 22.90 |
  | **减少** | **7,947,292** | **7.58（24.9%）** |

  数值来自指标制品名
  `offline-ledger-metrics-v0.1.0-p31964607-m24017315-nsfalse`，
  无需下载 artifact zip（该通道在 fine-grained PAT 下 403）。
- `mapping.txt` 制品 3,031,065 bytes 已上传。
- **坚果云**：步骤 conclusion=success、`nsfalse` = `skipped=false`，
  说明凭据已配且认证 / 两级 MKCOL / 上传四步均未触发失败分支。
  **如实说明**：HEAD 落盘校验只写日志不中断，因此不把「HEAD 200」
  当作已独立验证的事实。
- 首次 CI 零编译错误；唯一一次失败是本地新增的
  `check_workflow_syntax.py` 自误报（把路径变量当产物名，第 7 次同类坑），
  已双修正后全绿。

**未验证的边界（不在本 change 范围）**：R8 包真机启动、
SQLCipher 真机打开数据库、BiometricPrompt 弹窗、SAF 文件选择器、
5 万条切月 / 年报聚合耗时。CI 编译通过不等于 release 包真机能用。

## 风险

**残余风险 1**：R8 混淆后真机启动崩溃（序列化 / JNI 边界）。
**缓解**：第 3 节全套 keep 规则 + `minify=both` 一次出两个包对比 +
`mapping.txt` 制品；真机验证明确留给使用者，不假报。

**残余风险 2**：坚果云跨境链路抖动返回 `000`。
**缓解**：curl `--retry 2 --retry-delay 15 --max-time 900` +
诊断日志写 Summary；与代码无关，换时间重跑。

**残余风险 3**：`minify=both` 时第二次 Gradle 认为 UP-TO-DATE。
**缓解**：第 8 节回退顺序第 5 条，两次编译之间清输出目录。

**残余风险 4**：正式签名 Secret 用 PKCS12 但 `KEY_PASSWORD` 与
`KEYSTORE_PASSWORD` 不一致，签名阶段报 `Cannot recover key`。
**缓解**：`prepare-signing.sh` 提前警告；密钥是使用者自己生成的，
本 change 不代生成。

**残余风险 5**：`check_workflow_syntax.py` 自误报。
**缓解**：第 7 节精确匹配 + 先剥注释；阶段 5 归档第 6 次自误报的教训。

## 验证

- [x] 阶段 0 五项对齐结论齐全、分级判定已告知并等确认
- [x] 阶段 5 已按规范归档，无进行中 change 冲突
- [x] 使用者确认「按推荐来」：先归档 / 正式签名可选 / R8 默认 on
- [x] 通过 API 实际读取 lifelog 的 8 个参照文件
- [x] 2 份 delta 共 9 条 ADDED（build-and-ci 8 + privacy-security 1），每条至少一个 GIVEN / WHEN / THEN 场景 + 「理由」
- [x] CI 全绿（`43378544043` / `43379471531` / `43381245320` 三次 success）
- [x] 手动触发两次 `release.yml`（`minify=both`），体积对比见上表
- [x] 三次推送后均用远端 Git Trees API 逐文件比对 blob sha（PASS）
- [ ] 使用者确认后归档本 change
