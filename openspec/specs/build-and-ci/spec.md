# 构建与持续集成 Specification

## Purpose

定义构建工具链、CI 验证、固定签名与静态检查的行为约定。CI 类坑在踩坑清单里占了近一半，却没有 spec 覆盖，本模块把这些「顺手就会被改掉」的参数写成可测的 SHALL 级要求。

---

## Requirements

### Requirement: 版本组合有单一维护点

系统 SHALL 在顶层 `build.gradle.kts` 声明全部构建插件版本，
并 SHALL 在 `openspec/changes/*/design.md` 记录锁定依据与应急回退顺序。
系统 MUST NOT 在多个文件重复维护同一插件的版本号。

#### Scenario: 升级 Kotlin 版本

- **GIVEN** 需要升级 Kotlin / KSP 版本
- **WHEN** 修改版本声明
- **THEN** 只改顶层 `build.gradle.kts` 一处
- **AND** 按 design 中记录的回退顺序，每次只改一个版本并单独推送验证

**理由**：`踩坑清单.md` 1.2 记录过 KSP 从 2.3.0 起不再与 Kotlin 版本绑定、
AGP 9.0 内置 Kotlin 插件等版本绑定坑。集中声明 + 单次单改，
才能在 CI 报错时定位是哪次改动引入的。

---

### Requirement: targetSdk 跟随当年官方要求

系统 SHALL 以开工当年 Google Play 目标 API 要求为准设置 `targetSdk`，
并 SHALL 在阶段 0 的 `design.md` 记录查证依据与复核约定。
系统 MUST NOT 把某个 API 级别永久写死在规格里。

#### Scenario: 年度复核

- **GIVEN** 新一年 Google Play 目标 API 要求更新
- **WHEN** 复核 `targetSdk`
- **THEN** 按新要求提 change 调整，并在 design 中更新依据
- **AND** 不因「规格里写着 36」而跳过复核

**理由**：v1 规格书曾把 targetSdk 钉死在 API 35，而 Android 16 已发布、
Play 要求逐年抬升。值会过期，复核约定不会。

---

### Requirement: 全分支触发编译

`.github/workflows/build.yml` SHALL 使用 `on.push.branches: ['**']` 覆盖所有分支，
并 SHALL 包含 `workflow_dispatch` 手动触发入口。

#### Scenario: 推送到非 main 分支

- **GIVEN** 推送一个 `fix/xxx` 前缀的分支
- **WHEN** 查看 Actions 列表
- **THEN** 能查到该分支对应的运行记录
- **AND** 编译结果可据此判定，而不是「查不到就当通过」

**理由**：`踩坑清单.md` 2.1：曾因只写 `[main, master, 'feature/**']` 导致
Actions 静默不跑，提交在、分支在、列表里却没有记录，极易被误读成编译通过。

---

### Requirement: 显式声明 workflow 权限

`build.yml` 的 `permissions:` 块 SHALL 同时声明 `contents: write` 与
`actions: write`。系统 MUST NOT 只声明其中一项。

#### Scenario: 上传制品

- **GIVEN** workflow 使用 `upload-artifact@v4`
- **WHEN** 执行到上传步骤
- **THEN** 上传成功
- **AND** 不出现因 `actions` 权限为 `none` 导致的失败

**理由**：`踩坑清单.md` 2.2.1：一旦写了 `permissions:` 块就覆盖全部默认值，
只写 `contents: write` 会让 `actions` 变成 `none`，
而报错信息完全看不出是权限问题。

---

### Requirement: 固定 debug 签名

系统 SHALL 把 `app/debug.keystore` 提交进仓库，
并 SHALL 在 `signingConfigs` 中显式引用它。
系统 MUST NOT 使用 `create("debug")` 覆盖 AGP 内置的 debug SigningConfig。

#### Scenario: 两次独立编译覆盖安装

- **GIVEN** CI 在两个不同的全新环境各编译一次 debug APK
- **WHEN** 把第二个 APK 装到已装第一个的设备上
- **THEN** 直接覆盖安装成功
- **AND** 无需卸载，账本数据不丢失

**理由**：`踩坑清单.md` 2.4：Actions 每次全新环境会重新生成 debug 密钥，
签名不同的 APK 装不上，必须先卸载且数据全丢。
1.2 另记：`create("debug")` 会报 already exists，必须换名。

---

### Requirement: 单元测试独立于 APK 构建执行

`build.yml` SHALL 单独执行 `gradle testDebugUnitTest`，
MUST NOT 以 `assembleDebug` 成功代替单元测试通过。

#### Scenario: 测试代码有编译错误

- **GIVEN** `src/test` 下存在一个编译不过的测试类
- **WHEN** CI 运行
- **THEN** 单元测试步骤失败并使整个 job 失败
- **AND** 不出现「APK 编译通过但测试形同虚设」

**理由**：`assembleDebug` 只编译主源码，不碰 `src/test`。
已有项目实测过这一步漏跑导致测试文件白写。

---

### Requirement: 阶段 0 即验证 release Lint

`build.yml` SHALL 包含一次 release 构建或 `lintVitalRelease`，
使 fatal 级 Lint 错误在阶段 0 即暴露。

#### Scenario: 备份规则 XML 写法有误

- **GIVEN** 备份规则里 `<include>` 与 `<exclude>` 组合成非法白名单
- **WHEN** CI 执行 release 构建
- **THEN** `lintVital` 判为 fatal 并使构建失败
- **AND** 问题在阶段 0 被修掉，而不是潜伏到第一次发版

**理由**：`踩坑清单.md` 2.3：release 跑 `lintVital`、debug 不跑，
Lint 问题会一直潜伏到出正式包时才炸。已有真实案例。

---

### Requirement: 制品上传显式允许覆盖

使用 `upload-artifact@v4` 时 SHALL 显式设置 `overwrite: true`，
并 SHALL 使用确切的 APK 文件路径而非不稳定的 glob。

#### Scenario: 同名制品已存在

- **GIVEN** 上一次运行已上传过同名 artifact
- **WHEN** 本次运行再次上传
- **THEN** 上传成功并覆盖
- **AND** 不出现「上传失败」而界面看不出原因的情况

**理由**：`踩坑清单.md` 2.2.4：v4 的 `overwrite` 默认 false，
同名冲突直接失败，Actions 界面只显示「上传失败」。

---

### Requirement: 隐私基线由 CI 脚本强制检查

`build.yml` SHALL 在编译前执行 `scripts/check_manifest_privacy.py`、
`check_kotlin_strings.py` 与 `check_docs_consistency.py`，
任一脚本失败 SHALL 中断构建。

#### Scenario: 有人误加 INTERNET 权限

- **GIVEN** 某次提交在 Manifest 中声明了 `android.permission.INTERNET`
- **WHEN** CI 执行隐私检查步骤
- **THEN** 脚本报错并使 job 失败
- **AND** 不会编译出一个带网络权限的 APK

**理由**：「数据是你的」是第一条铁律，只写在 `project.md` 里没有强制力。
把铁律变成可执行检查，才能防住半年后的一次「顺手加个权限」。

---

### Requirement: CI 诊断步骤不得因返回码误判构建结果

`build.yml` 中的诊断性步骤（打印证书、计算校验值、输出提示）
SHALL 使用 `if ...; then ...; else ...; fi` 或显式 `|| true` 处理
`grep -q` / `test` / `git diff --quiet` 等可能返回非 0 的命令。
系统 MUST NOT 用 `cmd && echo` 作为步骤的最后一条语句。
只有真正的硬断言 MAY 用 `|| exit 1` 中断构建。

#### Scenario: 签名证书输出格式与预期不符

- **GIVEN** 三个编译门禁（单测 / debug APK / release lintVital）均已 success
- **WHEN** 签名诊断步骤里 `grep -q` 未匹配到预期字符串
- **THEN** 该步骤仍为 success
- **AND** 诊断内容写入日志或 Job Summary
- **AND** job 整体结论为 success，不出现「编译全绿但流水线判红」

#### Scenario: 真正的硬断言失败

- **GIVEN** 某个必须成立的条件（如 `app/debug.keystore` 缺失）
- **WHEN** 校验步骤执行
- **THEN** 显式 `exit 1` 使 job 失败
- **AND** 失败原因在日志中可读

**理由**：Actions 的 `run` 默认 `bash -e`，提示性命令的返回码会让整步失败。
首轮 CI 实测：三个编译门禁全 success，却因 `grep -q ... && echo` 判红，
差点被误读成「编译没过」。诊断步骤的职责是给信息，不是当质量门禁。

### Requirement: 阶段 1 单测独立于 APK 构建执行

`build.yml` SHALL 继续执行 `gradle testDebugUnitTest`，
且阶段 1 新增的纯 JVM 单测 SHALL 全部通过。

#### Scenario: 余额算法单测失败

- **GIVEN** `LedgerEngine` 的净变动算错一个符号
- **WHEN** CI 运行单元测试
- **THEN** `testDebugUnitTest` 失败并使 job 失败
- **AND** 不出现「APK 编译通过但算法错误被放过」

**理由**：账本里最难查的是「编译能过、不崩溃、数字静默全错」，
纯 JVM 单测是唯一能在无模拟器环境里拦住它的手段。

---

### Requirement: 静态检查覆盖事务与统计口径

`build.yml` SHALL 在编译前执行 `check_transaction_annotations.py` 与
`check_stat_rules.py`，任一失败 SHALL 中断构建。

#### Scenario: 有人新增写方法但忘了包事务

- **GIVEN** Repository 新增一个直接调用 DAO 写操作的方法
- **WHEN** CI 执行静态检查
- **THEN** 脚本报错并使 job 失败
- **AND** 不会产出一个「流水已记但余额未变」的版本

**理由**：Room 在 Repository 方法上的 `@Transaction` 不生效，
这类错误编译期发现不了，只能靠静态检查兜底。

### Requirement: 阶段 2 依赖锁定且零网络面

`app/build.gradle.kts` SHALL 显式声明
`androidx.compose.material:material-icons-core`（由 Compose BOM 管理，实际使用的图标均在 frozen core set 内）、
`navigation-compose:2.9.0`、`lifecycle-viewmodel-compose:2.9.1`、
`paging-common:3.3.6` 与 `paging-compose:3.3.6`。
系统 MUST NOT 引入 `room-paging`、`hilt-navigation-compose`、
Vico / Coil / Glide 或任何含网络行为的依赖。

#### Scenario: 图标 artifact 缺失

- **GIVEN** Compose BOM 2026.06 不再通过 `material3` 传递图标 artifact
- **WHEN** `assembleDebug` 编译
- **THEN** 因已显式声明 `material-icons-core` 而通过
- **AND** 依赖树中不含任何网络上报 SDK

**理由**：阶段 2 首次使用图标，漏声明会直接编译失败；
新增五个 artifact 时最容易顺手引入不需要的传递依赖。

---

### Requirement: 计算器单测独立于 UI

CI 的 `testDebugUnitTest` SHALL 覆盖计算器表达式优先级、除法舍入、
非法表达式与月份范围换算，且这些测试 SHALL 为纯 JVM 测试，不依赖模拟器。

#### Scenario: 表达式算错

- **GIVEN** `Calculator.evaluate` 把 `×` 当成 `+` 处理
- **WHEN** CI 运行单元测试
- **THEN** `12.5+3×2` 断言失败并使 job 失败
- **AND** 不产出一个「能编译但金额静默全错」的版本

**理由**：金额错误不崩溃、编译也能过，只能靠纯 JVM 单测在无模拟器环境里拦住。

### Requirement: 事务静态检查清单随写方法同步扩展

`scripts/check_transaction_annotations.py` 的 `WRITE_METHODS` SHALL 覆盖
`reconcile` / `createAccount` / `updateAccountMeta` / `archiveAccount`，
连同原有 9 个共 13 个写方法。任一方法 SHALL 在其函数体内出现 `db.withTransaction`。
系统 MUST NOT 因新增写方法不在硬编码清单而漏检。

#### Scenario: 新写方法漏包事务

- **GIVEN** `reconcile` 被改成直接调 DAO 而不包事务
- **WHEN** CI 运行静态检查
- **THEN** `check_transaction_annotations` 失败并使 job 失败
- **AND** 不产出一个「能编译但对账可能半写」的版本

#### Scenario: 清单不同步

- **GIVEN** 又新增一个写方法但没加进清单
- **WHEN** 评审或检查
- **THEN** 被判定为规格与实现不同步
- **AND** 需同步更新清单与 build-and-ci spec

**理由**：硬编码清单最典型的失效方式就是「加了新方法忘了登记」；
阶段 1 只有 9 个，阶段 3 新增 4 个必须一起扩。

---

### Requirement: 对账引擎单测独立于 UI 与 Android SDK

CI 的 `testDebugUnitTest` SHALL 覆盖差额正负、尾差边界（100 / 101 分）、
可用额度、展示聚合值不被减法污染、三种金额反查区间与疑似标记解码，
且这些测试 SHALL 为纯 JVM 测试，不依赖模拟器或 Room。

#### Scenario: 尾差边界算错

- **GIVEN** `isTailDifference` 把 101 分误判为尾差
- **WHEN** CI 运行单元测试
- **THEN** 边界断言失败并使 job 失败
- **AND** 不产出一个「大额差额被一键抹平」的版本

#### Scenario: 净资产被减法倒推

- **GIVEN** 有人把 `displayTotals` 的净资产改成「总资产 − 总负债」
- **WHEN** 运行含 `includeInTotal = false` 账户的用例
- **THEN** 断言失败
- **AND** 拦住凭空虚高的净资产

**理由**：金额与差额错误不崩溃、编译也能过，只能靠纯 JVM 单测在无模拟器环境里拦住。

### Requirement: 统计口径静态检查覆盖阶段 4 新查询

`scripts/check_stat_rules.py` 的统计查询清单 SHALL 覆盖
`subcategoryTotalsUnderRoot`，并要求其 `@Query` 带 `excludeFromStats = 0`。
`check_transaction_annotations.py` 的 `WRITE_METHODS` SHALL 保持 13 个不变
（阶段 4 不新增写方法）。

#### Scenario: 新查询漏了口径

- **GIVEN** `subcategoryTotalsUnderRoot` 的 `@Query` 未带 `excludeFromStats = 0`
- **WHEN** CI 运行静态检查
- **THEN** `check_stat_rules` 失败并使 job 失败
- **AND** 五个静态检查仍全部在编译前执行

#### Scenario: 写方法清单未被误扩

- **GIVEN** 阶段 4 只新增只读查询
- **WHEN** 查看 `WRITE_METHODS`
- **THEN** 仍为 13 个
- **AND** 不因「新增了 DAO 方法」就顺手把只读方法算进事务清单

**理由**：硬编码清单最典型的失效方式是「加了新方法忘了登记」；
反过来，把只读方法误登记进写清单也是一处规格与实现不同步。

---

### Requirement: 图表数据准备层为纯 JVM 且可单测

CI 的 `testDebugUnitTest` SHALL 覆盖合计一致 / 不一致、
闰年 2 月与大小月补 0、空月补 0、全 0 与单系列归一化、
扇区角度与跨 0° 点击命中、颜色解析合法 / 非法，
且这些测试 SHALL 为纯 JVM 测试，不依赖模拟器、Room 或 `android.graphics`。

#### Scenario: 闰年补零

- **GIVEN** 2028 年 2 月（29 天）只有 2 月 3 日一笔流水
- **WHEN** 生成每日序列
- **THEN** 返回 29 个点
- **AND** 其余 28 天支出均为 0

#### Scenario: 合计不一致被拦住

- **GIVEN** 有人把 `checkTotals` 改成只比两个口径
- **WHEN** 运行单元测试
- **THEN** 三口径断言失败并使 job 失败
- **AND** 不产出一个「静默显示错数」的版本

**理由**：统计口径与补 0 的错误不崩溃、编译也能过，
只能靠纯 JVM 单测在无模拟器环境里拦住。

### Requirement: 阶段 5 新增依赖与权限进入检查清单

`app/build.gradle.kts` SHALL 声明 `androidx.biometric:biometric:1.1.0`
（官方最新稳定版，1.4.0 属 alpha 线不用于 v1），
`check_manifest_privacy.py` SHALL 把 `USE_BIOMETRIC` 列为唯一允许的
`<uses-permission>`，`check_transaction_annotations.py` 的
`WRITE_METHODS` SHALL 为 17 个。
`MainActivity` SHALL 继承 `FragmentActivity` 以支持 `BiometricPrompt`。
系统 MUST NOT 为应用锁引入含网络行为的依赖。

#### Scenario: 误用 alpha 版

- **GIVEN** 有人把 biometric 依赖改成 1.4.0-alpha07
- **WHEN** 评审或 CI 检查
- **THEN** 发现与 `design.md` 锁定的稳定版不符
- **AND** 要求改回 1.1.0 或显式提 change 复核

#### Scenario: 权限白名单漏登记

- **GIVEN** 新增 `USE_BIOMETRIC` 却没扩 `ALLOWED_PERMISSIONS`
- **WHEN** CI 执行隐私检查
- **THEN** 检查失败并使 job 失败
- **AND** 不产出一个「检查器与实际 Manifest 不同步」的版本

**理由**：阶段 5 是首个同时新增依赖、权限、Activity 基类的阶段，
版本与白名单必须在 change 里锁定，不能靠记忆。

---

### Requirement: 加密层单测为纯 JVM 且覆盖失败路径

CI 的 `testDebugUnitTest` SHALL 覆盖加密往返、错口令、密文篡改、
MAGIC 不匹配、文件截断、短口令拒绝、密文不含明文分类名与金额、
同明文两次导出密文不同，且这些测试 SHALL 为纯 JVM 测试，
不依赖模拟器、Room、Android SDK 或 `android.graphics`。

#### Scenario: 错口令被拦住

- **GIVEN** 有人把解密异常吞掉并返回空字符串
- **WHEN** 运行单元测试
- **THEN** 错口令断言失败并使 job 失败
- **AND** 不产出一个「静默导入空账本」的版本

**理由**：加密代码写错不会崩溃，只会「看起来能用但换设备打不开」；
纯 JVM 单测是沙盒无模拟器环境里唯一能验证的环节。

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

### Requirement: 写方法清单与迁移静态检查扩展

`check_transaction_annotations.py` 的 `WRITE_METHODS` SHALL 扩到 26 个，
覆盖阶段 6a 新增的 9 个写方法；并 SHALL 精确检查
`AppDatabase version = 2`、`Migrations.ALL` 含 `Migration(1, 2)`、
两条 CREATE TABLE SQL 常量含全部必要列名。
检查 MUST 先剥注释再匹配，MUST NOT 用宽泛子串。
六个静态检查 SHALL 在每次 push 时全部执行。

#### Scenario: 漏登记写方法

- **GIVEN** 有人新增一个不包在事务内的写方法
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并使 job 失败
- **AND** 不产出一个「看着能编译」的包

#### Scenario: 注释里提到破坏性迁移

- **GIVEN** `Migrations.kt` 注释解释「禁止破坏性迁移」
- **WHEN** 执行静态检查
- **THEN** 不因注释自误报
- **AND** 六个静态检查仍全部执行

**理由**：阶段 6a 首次引入 Migration，静态检查是沙盒里唯一能
拦住「忘登记 ALL / 忘加 addMigrations / SQL 列写漏」的手段；
本仓库已有 7 次「检查器自误报」教训，精确匹配是硬要求。

---

### Requirement: 阶段 6a 纯 JVM 单测覆盖迁移与排期

系统 SHALL 提供 `RecurringScheduleTest`、`BackupV1CompatTest`、
`MigrationContractTest` 三个纯 JVM 单测类，
分别覆盖月度 / 周度 / 日度排期与幂等与 catch-up 上限、
v1 备份解析成空列表与 v2 往返与过高版本拒绝、
以及两条 CREATE TABLE 常量列齐全。
`testDebugUnitTest` MUST 在 CI 中单独执行。

#### Scenario: 月末规则

- **GIVEN** 每月 31 日的规则
- **WHEN** 计算 2 月到期日
- **THEN** 返回 2 月最后一天（平年 28 / 闰年 29）
- **AND** 不抛 `DateTimeException`

#### Scenario: v1 备份兼容

- **GIVEN** 一段不含 `templates` / `recurringRules` 键的 v1 JSON
- **WHEN** 用 `BackupImporter.decode` 解析
- **THEN** 成功且两个列表为空
- **AND** 不要求调用方传额外参数

**理由**：CI 没有模拟器，仪器测试跑不了；纯 JVM 层是唯一能自动验证
「排期算对、旧备份能读、SQL 列齐全」的地方。

### Requirement: 迁移静态检查覆盖两段 Migration 与 ALTER TABLE

`check_transaction_annotations.py` 的 `WRITE_METHODS` SHALL 扩到 30 个，
并 SHALL 精确检查 `AppDatabase version = 3`、
`Migrations.ALL` 同时含 `MIGRATION_1_2` 与 `MIGRATION_2_3`、
budgets 的 CREATE TABLE 常量列齐全、
ALTER TABLE 语句含 `linkedRecordId`。
`check_stat_rules.py` SHALL 把 3 条 SETTLEMENT 聚合查询纳入 marker 清单，
并要求其 `@Query` 带 `excludeFromStats = 0`。
检查 MUST 先剥注释再匹配，MUST NOT 用宽泛子串。

#### Scenario: 漏登记第二段迁移

- **GIVEN** 有人把 `ALL` 改回只含 `MIGRATION_1_2`
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并使 job 失败
- **AND** 不产出「看着能编译」的包

#### Scenario: 新统计查询漏口径

- **GIVEN** `settlementByRootInRange` 的 `@Query` 未带 `excludeFromStats = 0`
- **WHEN** 执行 `check_stat_rules.py`
- **THEN** 检查失败并指出该查询

**理由**：6b 首次 ALTER TABLE，静态检查是沙盒里唯一能拦住
「忘登记 ALL / 列名写漏 / 新查询漏口径」的手段；
本仓库已有 7 次「检查器自误报」教训，精确匹配是硬要求。

---

### Requirement: 阶段 6b 纯 JVM 单测覆盖净支出、预算与备份兼容

系统 SHALL 提供 `SettlementNetTest`、`BudgetEngineTest`、
`BackupV2CompatTest`，并 SHALL 扩展 `MigrationContractTest` 覆盖
`Migration(2→3)`。`testDebugUnitTest` MUST 在 CI 中单独执行。

#### Scenario: 净支出按 id 相减

- **GIVEN** 餐饮毛支出 5000 分、报销 2000 分
- **WHEN** 计算净切片
- **THEN** 餐饮净支出 3000 分
- **AND** 无分类出现负数

#### Scenario: 预算超支判定

- **GIVEN** 限额 100000 分、消耗 120000 分
- **WHEN** 计算 BudgetProgress
- **THEN** `isOver = true`、`overCents = 20000`、剩余为 0

#### Scenario: v2 备份兼容

- **GIVEN** 一段不含 `budgets` / `linkedRecordId` 键的 v2 JSON
- **WHEN** 用 `BackupImporter.decode` 解析
- **THEN** 成功、budgets 为空、linkedRecordId 为 null

**理由**：CI 没有模拟器且使用者已推迟真机验证，
纯 JVM 层是唯一能自动验证「净支出算对、预算判定对、
旧备份能读、ALTER TABLE 列齐全」的地方。

### Requirement: 迁移与统计静态检查覆盖第三段迁移和新查询

`check_transaction_annotations.py` SHALL 精确检查
`AppDatabase version = 4`、`Migrations.ALL` 含三段迁移、
两条 `SQL_3_4_ALTER_*` 常量含 `currencyCode` 与 `DEFAULT 'HKD'`，
检查 MUST 先剥注释再匹配。
`check_stat_rules.py` marker SHALL 覆盖全部 `...ForCurrency` 查询
并要求带唯一统计口径。
`WRITE_METHODS` SHALL 保持 30 个（6c-1 不新增写方法）。

#### Scenario: 漏登记 MIGRATION_3_4

- **GIVEN** `ALL` 只含两段迁移
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并使 job 失败

#### Scenario: ALTER 缺默认值

- **GIVEN** `SQL_3_4_ALTER_RECORDS` 写成 `NOT NULL` 但无 `DEFAULT 'HKD'`
- **WHEN** CI 执行静态检查
- **THEN** 检查失败并指出该常量

**理由**：SQLite 对 `NOT NULL` 的 `ADD COLUMN` 强制要求默认值；
静态检查是沙盒里唯一能拦住「忘登记 ALL / 漏默认值」的手段。

---

### Requirement: 阶段 6c-1 单测覆盖币种继承与旧备份兼容

系统 SHALL 提供 `CurrencyInheritTest` 与 `BackupV3CompatTest`，
并 SHALL 扩展 `MigrationContractTest` 覆盖 `Migration(3,4)`。
`testDebugUnitTest` MUST 在 CI 中单独执行。

#### Scenario: 报销复制原流水币种

- **GIVEN** 一笔 CNY 支出
- **WHEN** 对它发起报销
- **THEN** SETTLEMENT 流水币种也是 CNY

#### Scenario: v1 备份默认 HKD

- **GIVEN** 一段不含 `currencyCode` 键的 v1 JSON
- **WHEN** 用 `BackupImporter.decode` 解析
- **THEN** 成功且所有流水币种为 HKD

**理由**：CI 无模拟器且使用者已推迟真机验证，
纯 JVM 层是唯一能自动验证「币种算对、旧备份能读、ALTER 列齐全」的地方。
