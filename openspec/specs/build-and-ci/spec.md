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
