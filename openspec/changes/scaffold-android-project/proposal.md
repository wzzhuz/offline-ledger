# 建立可编译的 Android 工程骨架与隐私基线

> **本文件是 change 的上下文入口。**
> 新会话只读这一份文件，就应当能还原本次改动的全貌，无需重新讨论。

## 为什么改

**背景（使用者原话与项目现状）**：

- 上一轮把 v2 设计归档为 `wzzhuz/offline-ledger` 的 OpenSpec 基线，
  仓库里只有 21 个文档文件：**无代码、无 Gradle、无 workflow、无可用产物**
- 使用者指令：「**开始阶段0**」，并对三项确认「按推荐来 / 凭据可以继续用，
  已授权 workflows 相关权限 / 按推荐来」
- `openspec/project.md` 的阶段 0 定义为：Gradle 依赖与包结构、Manifest 隐私基线、
  金额·日期·币种基础类型、备份 DTO 与 `schemaVersion`；
  同时规定「具体版本组合 SHALL 在阶段 0 的 change 中查证官方文档后锁定」
  与「每阶段结束必须能编译运行并跑一遍导出备份」

**问题**：只有规格没有工程，阶段 1 的 Room 数据层没有落脚点；
沙盒无 Android SDK / Gradle / Java 编译器，「可编译」这件事无法在本地验证，
必须先把 GitHub Actions 建成唯一的编译场。
而三条铁律里的「数据是你的」目前只是文字约定，
没有落到 `AndroidManifest.xml` 与备份排除规则上，也无法被自动检查。

## 做什么

1. 锁定版本组合并写进 `design.md`（查证依据 + 应急回退分支）
2. 建立 Gradle 工程骨架：`settings.gradle.kts`、根与 `app/build.gradle.kts`、
   Gradle 8.13 wrapper（含官方 `gradle-wrapper.jar`）、`.gitignore`
3. 落地 Manifest 隐私基线：不声明 `INTERNET`、`allowBackup=false`、
   `backup_rules.xml` / `data_extraction_rules.xml` 排除数据库、仅 SAF
4. 落地基础类型：`Cents`（Long 分 + BigDecimal 输入/展示）、
   `LedgerClock`（Asia/Hong_Kong → localDate / yearMonth）、`LedgerCurrency`（HKD）
5. 落地备份信封：`BackupEnvelope` + `BackupSchema.CURRENT = 1`，DTO 与 Entity 分离
6. 落地固定签名：`app/debug.keystore`（keytool 生成并提交）、
   `signingConfigs` 显式引用，保证各次 CI 编译签名一致
7. 建立 `.github/workflows/build.yml`：push 全分支触发、JDK 17、
   Gradle 8.13、检查脚本、单元测试、debug APK、签名校验、制品上传
8. 落地两个检查脚本 + 一个隐私专项检查，并让 CI 强制执行
9. 新增第 8 个 spec 模块 `build-and-ci`（delta），并给三个既有模块补 ADDED 要求
10. 更新 `README.md` 的状态与本地构建说明

## 不做什么

- ❌ 不写 Room Entity / DAO / `AppDatabase` / Repository（阶段 1）
- ❌ 不写任何业务页面、记一笔、明细、资产、报表（阶段 2–4）
- ❌ 不实现对账、图表、加密 OpenHelper 的实际接入（阶段 1 / 3 / 4 / 5）
- ❌ 不建 `release.yml`、坚果云上传、Secrets 签名逻辑（出包阶段单独建 change）
- ❌ 不引入 v1.1 清单里的任何功能
- ❌ 不改 `wzzhuz/my-app-workflow` 仓库
- ❌ 不在沙盒内尝试编译 APK（无 SDK / Gradle / javac），不假报「编译通过」
- ❌ 不修改 `docs/设计规格书_v2.md`（历史快照，不维护）

## 对齐结论

> 阶段 0 在对话里产出，五项齐全才进入写作。

1. **要解决什么问题**
   把项目从「只有规格」推进到「可编译的 Android 工程骨架 + 可逐条核查的隐私基线」，
   让阶段 1 的 Room 数据层有落脚点；同时把 GitHub Actions 建成沙盒里唯一的编译验证手段。

2. **范围边界**
   - 改：Gradle 骨架、Manifest 隐私基线、金额/日期/币种基础类型、备份 DTO 与 schemaVersion、
     锁定版本组合、`.github/workflows/build.yml` + 固定 `debug.keystore`、三个检查脚本、
     包名 `com.zwz.offlineledger`、新增 `build-and-ci` spec 模块
   - 不改：Room / UI / 对账 / 统计 / v1 范围 / `my-app-workflow` / 历史快照

3. **关键约束**
   三条铁律不得违反；Manifest 不声明 `INTERNET`、`allowBackup=false` + 备份规则排除 db、仅 SAF。
   版本组合必须查证后锁定，不凭记忆——`踩坑清单.md` 1.2 记过 KSP 从 2.3.0 起不再绑定
   Kotlin 版本、AGP 9.0 内置 Kotlin 插件这类坑。
   `debug.keystore` 必须提交进仓库固定签名（2.4）；`build.yml` push 触发必须
   `branches: ['**']`（2.1）；`permissions:` 同时写 contents + actions（2.2.1）；
   `upload-artifact@v4` 需 `overwrite: true`（2.2.4）。
   沙盒无 Android SDK / Gradle / Java 编译器，不本地编译、不假报编译通过。
   推送前比对本地与远端 sha 防覆盖（4.5），推完用 API 列远端校验（4.6 / 4.7）；
   Python 拼 Kotlin 后必跑 `check_kotlin_strings.py`（1.1，犯过两次）。

4. **验收标准**（可测）
   - **GIVEN** 推完源码 **WHEN** Actions 跑完 **THEN** debug APK 构建成功，
     且 Actions 列表里能查到该分支运行记录（不是「查不到 = 通过」）
   - **GIVEN** Manifest **WHEN** 检查 **THEN** 无 `INTERNET`、`allowBackup=false`、
     备份规则排除 db
   - **GIVEN** 第二次编译 **WHEN** 装同一台设备 **THEN** 签名一致、无需卸载
   - **GIVEN** change 四件套 **WHEN** 审阅 **THEN** proposal 含五项对齐结论、
     design 含否决方案表、delta 用 SHALL + GIVEN/WHEN/THEN
   - **GIVEN** 推送完成 **WHEN** 用 API 校验 **THEN** 文件与本地 sha 一致、无 token 明文

5. **被否决的方案**
   一句话结论：**所有「跳 change / 沿用过期 targetSdk / 沙盒内编译 / 不固定签名 /
   依赖默认 GITHUB_TOKEN 权限 / 只推源码不建 workflow」的方案均被否决**，
   采用「完整骨架 + 固定签名 + CI 强验证 + 新增 build-and-ci 模块」。
   完整表格见 `design.md`「考虑过的其他方案」。

## 影响范围

**触及的 spec 模块**：
- `specs/build-and-ci/` —— 新增模块（ADDED，8 条）
- `specs/money-precision/` —— ADDED 2 条（Cents 基础类型、本地日期冗余）
- `specs/privacy-security/` —— ADDED 2 条（Manifest 可核查、自动检查脚本）
- `specs/backup-restore/` —— ADDED 1 条（schemaVersion 常量与信封头部）

**是否需要重新安装包**：否（尚无 APK 发布、尚无用户数据）

**是否会丢数据**：否

**行为变化**：项目从「纯文档仓库」变为「可编译的 Android 工程」；
每次 push 到任意分支都会触发真实编译与单元测试。

## 与项目铁律是否冲突

- [x] 不违反三条铁律（本轮把「数据是你的」落成 Manifest 与检查脚本）
- [x] 不涉及「已确认不做」清单中的功能
- [x] 无冲突

**补充说明**：`project.md` 技术栈表里写「targetSdk 不写死，跟随开工当年官方要求」。
本轮查证 Google Play 官方要求后锁定为 **API 36**（Android 16），
并在 `design.md` 记录依据与「每年复核」的约定，不是把值写死不复核。
