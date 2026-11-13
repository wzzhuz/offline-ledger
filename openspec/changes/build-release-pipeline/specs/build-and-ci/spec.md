# Delta for 构建与持续集成

## ADDED Requirements

### Requirement: release.yml 手动触发产出 Release 包

系统 SHALL 提供一条 `workflow_dispatch` 触发的 `release.yml`，
SHALL 支持版本号覆盖、是否创建 tag 与 GitHub Release、是否编译 AAB、
是否上传坚果云、以及 R8 开关 `none / on / both` 六个输入。
系统 SHALL 在 Actions 页面可见（不是「查不到=通过」），
并 SHALL 在编译前跑完五个静态检查与单元测试。

#### Scenario: Actions 页能看到工作流

- **GIVEN** `release.yml` 已推送
- **WHEN** 打开仓库 Actions 页
- **THEN** 左侧工作流列表出现「编译 Release 包」
- **AND** 点击后能填 6 个输入并手动运行

#### Scenario: 发布前跑质量门禁

- **GIVEN** 手动触发一次 release 构建
- **WHEN** 编译开始前
- **THEN** 五个静态检查、workflow 语法检查与 `testDebugUnitTest` 全部通过
- **AND** 任一失败则 job 失败、不产出 Release 包

**理由**：release 包是最终交付物，不能只靠「能编出来」就发；
`workflow_dispatch` 的 choice option `'on'` 不加引号会被 YAML 1.1
解析成布尔 true 并导致工作流**静默不注册**，必须实际在 Actions 页确认可见。

---

### Requirement: R8 默认开启且 Gradle 侧保持可关闭

`release.yml` 的 `minify` 输入 SHALL 默认 `on`，
SHALL 通过 `-PminifyRelease=true` 打开 Gradle 侧的
`isMinifyEnabled` 与 `isShrinkResources`。
`app/build.gradle.kts` 的 `minifyRelease` 属性 SHALL 默认 `false`，
使 `build.yml` 的日常 push 构建行为保持不变。
`minify=both` SHALL 一次产出 plain 与 minified 两个 APK，
并在 Job Summary 打印两者字节数与差值。

#### Scenario: 默认出包即混淆

- **GIVEN** 使用者在 Actions 页直接点「Run workflow」，不改任何输入
- **WHEN** 编译
- **THEN** Gradle 收到 `-PminifyRelease=true`
- **AND** 产出经 R8 混淆与资源压缩的 release APK

#### Scenario: 一次对比体积

- **GIVEN** `minify=both`
- **WHEN** 两次 `assembleRelease` 完成
- **THEN** 产出 `...-plain.apk` 与 `...-minified.apk`
- **AND** Summary 打印两者大小与差值
- **AND** minified 包不大于 plain 包

#### Scenario: 日常 CI 不受影响

- **GIVEN** 一次普通 push 触发 `build.yml`
- **WHEN** 跑 `assembleDebug` 与 `assembleRelease`
- **THEN** 不带 `-PminifyRelease`
- **AND** 编译时长与阶段 5 基本一致

**理由**：把 R8 开在 debug 或日常 release 门禁上没意义、还拖慢每次 push；
但出包时又必须默认开，所以拆成「Gradle 默认关、workflow 默认开」两层。

---

### Requirement: R8 keep 规则覆盖序列化与 JNI 边界

`app/proguard-rules.pro` SHALL 保留 `kotlinx.serialization` 的
`$$serializer` / `Companion` / `serializer(...)` 与 `@Serializable` 注解属性，
SHALL 保留备份 DTO 的字段名、Room 生成实现与 TypeConverter 边界、
Hilt 生成组件、SQLCipher 的 native 方法与类名、枚举的
`values()` / `valueOf(String)`，
并 SHALL 保留 `SourceFile,LineNumberTable`。

#### Scenario: 混淆后备份仍可往返

- **GIVEN** 用 R8 混淆后的包导出一份加密备份
- **WHEN** 再用同一包导入
- **THEN** 账户 / 分类 / 流水 / 校准 / 锚点全量一致
- **AND** 不出现「找不到 serializer」或字段全为默认值

#### Scenario: 混淆后能打开数据库

- **GIVEN** 用 R8 混淆后的包冷启动
- **WHEN** 首次打开 SQLCipher 数据库
- **THEN** 不出现 `UnsatisfiedLinkError`
- **AND** 预置分类与账户正常加载

**理由**：多数 AndroidX 库自带 consumer rules，但序列化与 JNI 边界
必须显式补；R8 出问题的典型表现是「装得上、一启动就崩」，
CI 编译通过完全看不出来。

---

### Requirement: mapping.txt 作为制品上传

开启 R8 时，系统 SHALL 把
`app/build/outputs/mapping/release/mapping.txt` 作为 Actions 制品上传，
并 SHALL 在 Job Summary 注明用途。未开启 R8 时该步骤 SHALL 跳过且不使 job 失败。

#### Scenario: 混淆包可排查崩溃

- **GIVEN** 一次 `minify=on` 的构建
- **WHEN** 编译完成
- **THEN** 制品中能下载 `mapping.txt`
- **AND** Summary 写明「用于反混淆线上堆栈」

#### Scenario: 不混淆时不误报

- **GIVEN** 一次 `minify=none` 的构建，没有 mapping 文件
- **WHEN** 上传步骤执行
- **THEN** 步骤跳过
- **AND** job 结论仍为 success

**理由**：R8 后的堆栈全是 `a.b.c`，没有 mapping 等于放弃排查能力；
`if-no-files-found: error` 用在非混淆构建上会误伤 job。

---

### Requirement: 版本号不再恒为 1 且 CI 覆盖传进 Gradle

`app/build.gradle.kts` SHALL 定义 `defaultVersionName` 与
`versionCodeFrom()`（`major*10000 + minor*100 + patch`），
SHALL 支持 `-PversionNameOverride` / `-PversionCodeOverride`。
CI 确定的版本号 MUST 传给 Gradle，**MUST NOT** 只用于文件名与 Release 标题。
版本号 SHALL NOT 回写文件，覆盖时 SHALL 打印警告提醒发版后同步。

#### Scenario: 覆盖生效到 APK 内

- **GIVEN** 手动触发时填 `version_name=0.2.0`
- **WHEN** 编译
- **THEN** APK 的 `versionName=0.2.0`、`versionCode=200`
- **AND** 文件名与 Release 标题也是 `v0.2.0`

#### Scenario: 文件仍是旧值

- **GIVEN** CI 覆盖为 `0.2.0`，文件里 `defaultVersionName` 仍是 `0.1.0`
- **WHEN** 编译开始
- **THEN** 打印 `::warning::` 提醒发版后同步修改
- **AND** 不擅自改仓库文件

**理由**：lifelog 注释里记过这个隐蔽 bug——只改文件名和标题，
APK 内仍是旧版本号，装上去显示不对。

---

### Requirement: 签名缺失降级且诊断不依赖 v1 JAR 签名

系统 SHALL 支持 4 个 Secret 启用正式签名；缺任一 SHALL 回退仓库内
`debug.keystore` 并打印告警，**MUST NOT** 因此中断发版。
签名诊断 SHALL 用 `apksigner verify --print-certs`，
MUST NOT 只依赖 `keytool -printcert -jarfile`（后者只认 v1 JAR 签名）。
回退 debug 时 SHALL 明确提示「随仓库公开的密钥，仅供个人自用，勿对外分发」。

#### Scenario: 没配 Secrets 照样出包

- **GIVEN** 仓库未配置 `RELEASE_KEYSTORE_BASE64` 等 4 个 Secret
- **WHEN** 手动触发 release 构建
- **THEN** 使用 `commonDebug` 签名、流程继续、job success
- **AND** Summary 写明「公开密钥，勿对外分发」

#### Scenario: 配齐 Secrets

- **GIVEN** 4 个 Secret 齐全
- **WHEN** 编译
- **THEN** 使用 `official` signingConfig
- **AND** `apksigner` 打印的证书不含 OfflineLedger Debug

**理由**：本项目从阶段 0 起就把 `debug.keystore` 提交进仓库，
历史包与它同签名可覆盖安装且不丢数据；个人自用不该被
「忘配 4 个 Secret」卡死。阶段 0 已踩过 `keytool -printcert -jarfile`
对 v2/v3 签名不输出 Owner 的坑。

---

### Requirement: 坚果云上传与创建 Release 解耦

`release.yml` 的坚果云上传步骤 SHALL 只依赖 `upload_nutstore` 输入，
MUST NOT 依赖 `create_release`。上传 SHALL 依次执行
PROPFIND 认证、两级 MKCOL、`curl -T` 上传、HEAD 落盘校验四步，
并 SHALL 把诊断日志写进 Job Summary。
未配置 `JGY_USER` / `JGY_APP_PASSWORD` 时 SHALL 跳过且 job 仍 success，
Summary SHALL 写明补齐路径。凭据 SHALL 取自 Secrets，
MUST NOT 写进仓库。

#### Scenario: 不打 tag 也能传

- **GIVEN** `create_release=false`、`upload_nutstore=true`
- **WHEN** 编译完成
- **THEN** 仍执行坚果云四步上传
- **AND** 不创建 tag、不建 GitHub Release

#### Scenario: 未配 Secrets

- **GIVEN** 未配置坚果云应用密码
- **WHEN** 上传步骤执行
- **THEN** 打印「跳过坚果云上传」与补齐路径
- **AND** job 结论仍为 success

#### Scenario: 用错密码

- **GIVEN** 配置了 `JGY_APP_PASSWORD` 但填的是登录密码
- **WHEN** PROPFIND 返回 401 / 403
- **THEN** 提示「必须用应用密码，不是登录密码」
- **AND** 不把 401 归因为代码问题

**理由**：lifelog 的坚果云步骤绑死 `create_release`，
导致「出个临时包也想传到手机」做不到；把两者解耦更贴合自用场景。
2023 年起坚果云拒绝登录密码直连 WebDAV。

---

### Requirement: workflow 语法静态检查接入日常门禁

`scripts/check_workflow_syntax.py` SHALL 检查 `release.yml` 的
`workflow_dispatch` 声明、choice option 引号、`permissions` 同时含
contents/actions、产物名纯 ASCII、`upload-artifact@v4` 的
`overwrite: true`、以及不含硬编码凭据字面量。
该检查 SHALL 接入 `build.yml` 的静态检查步骤。
检查 MUST 精确匹配并先剥除注释，MUST NOT 用宽泛子串匹配。

#### Scenario: 有人把 `on` 的引号删了

- **GIVEN** 有人把 choice option `'on'` 改成裸 `on`
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并使 job 失败
- **AND** 不产出一个 Actions 页静默找不到工作流的文件

#### Scenario: 注释里提到凭据名

- **GIVEN** `release.yml` 注释解释了需要哪些 Secret
- **WHEN** 执行静态检查
- **THEN** 不因注释里的 Secret 名误报
- **AND** 六个静态检查仍全部在编译前执行

**理由**：阶段 5 归档第 6 次自误报的教训——检查器比被检查对象更容易出错，
精确匹配与先剥注释是硬要求。
