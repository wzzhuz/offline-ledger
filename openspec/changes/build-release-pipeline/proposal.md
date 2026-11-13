# 构建 Release 包流水线

> **本文件是 change 的上下文入口。**
> 新会话只读这一份文件，就应当能还原本次改动的全貌，无需重新讨论。

## 为什么改

**背景（使用者原话与项目现状）**：

- 使用者最初动机是「资产数据高度敏感、不信任第三方」，
  最终诉求是「知道自己的资产情况和支出情况，每个月看下哪类支出比较多，
  年终报表看下收入情况、支出情况、曲线变化」
- 阶段 0–5 **全部完成并归档**：8 个模块 / 128 条 Requirement / 192 个 Scenario、
  7 个已归档 change、178 个文件、CI 全绿（`b3c52bc629`），
  `build.yml` 每次 push 产出约 23.5 MB debug APK 且跑 release 的 lintVital
- **但「能编译」不等于「能发到自己手上」**：目前没有手动出正式包的流水线，
  `app/build.gradle.kts` 里 `versionCode = 1` 恒为 1、`versionName = "0.1.0"` 写死，
  `isMinifyEnabled = false`，`app/proguard-rules.pro` 还是阶段 0 的占位注释。
  包体积 23.5 MB 对自用偏大，R8 没开
- 使用者明确要求：**「参考 lifelog，生成构建的 release.yml 的 actions，
  上传坚果云，默认开启 r8 混淆减少包体积」**

**问题**：阶段 5 归档后 specs 基线干净，正好把「交付闭环」单独做成一个 change：
一条手动触发的 release 流水线，默认 R8 混淆，产物既能挂 GitHub Release /
Artifacts，也能自动传到坚果云 WebDAV，手机直接取来装。

## 做什么

1. **新增 `.github/workflows/release.yml`**：`workflow_dispatch` 手动触发，
   参照同账号 `wzzhuz/lifelog` 的 `release.yml` 并按本项目适配
2. **默认开启 R8**：`release.yml` 的 `minify` 输入默认 `on`，
   可选 `none / on / both` 一次出两个包对比体积；
   `app/build.gradle.kts` 加 `-PminifyRelease` 开关与 `isShrinkResources`
3. **补 R8 keep 规则**：`app/proguard-rules.pro` 覆盖
   `kotlinx.serialization` / Room / Hilt / SQLCipher JNI / 枚举 / 备份 DTO
4. **版本号机制**：`defaultVersionName` + `versionCodeFrom()` +
   CI `-PversionNameOverride` / `-PversionCodeOverride` 覆盖，不再恒为 1
5. **签名**：`.github/scripts/prepare-signing.sh` 支持 4 个 Secret
   切正式密钥；未配置只告警、回退仓库内 `debug.keystore`，流程继续
6. **坚果云 WebDAV 上传**：PROPFIND 认证 → 两级 MKCOL → curl -T → HEAD 200，
   未配 `JGY_USER` / `JGY_APP_PASSWORD` 时跳过且不使 job 失败
7. **`mapping.txt` 作为制品上传**（R8 后排查崩溃必需）
8. **workflow 语法静态检查**接入 `build.yml` 的质量门禁
9. 同步 `README.md`、`project.md`、`build-and-ci` 与 `privacy-security` specs

## 不做什么

- ❌ **不改任何业务代码、表结构、备份格式、4 Tab 导航、Manifest 权限**
- ❌ **不声明 `INTERNET`**：坚果云上传发生在 GitHub runner 上，
  传的是**安装包**不是账本数据；App 仍零网络权限，
  `check_manifest_privacy.py` 必须继续 PASS
- ❌ 不建 `release-preview.yml` / `cleanup.yml` / AAB 构建（本轮未点名）
- ❌ 不把坚果云账号 / 应用密码 / 正式签名密钥写进仓库（一律 Secrets）
- ❌ 不改 `build.yml` 的触发规则（`branches: ['**']` 是硬要求）
- ❌ 不改 `wzzhuz/my-app-workflow`、不改 `docs/设计规格书_v2.md` 历史快照
- ❌ 不在沙盒内编译 APK（无 Android SDK / Gradle），编译由 Actions 验证

## 对齐结论

> 阶段 0 在对话里产出，五项齐全才进入写作。

1. **要解决什么问题**
   把「能编译」变成「能发到自己手上」：一条手动触发的 release 流水线，
   **默认开启 R8 让包变小**，产物既能挂 GitHub Release / Artifacts，
   也能自动传到坚果云 WebDAV，手机直接取来装。
   这是 v1 六个阶段之后的交付闭环。

2. **范围边界**
   - 改：`release.yml`、`prepare-signing.sh`、`cleanup-artifacts.sh`、
     `app/build.gradle.kts`（minify 开关 + 版本号机制 + 正式签名）、
     `app/proguard-rules.pro`、workflow 语法检查脚本、README / project.md / 2 份 delta
   - 不改：业务代码、表结构、备份格式、导航、Manifest、build.yml 触发规则、
     v1.1 清单、历史快照

3. **关键约束**
   **CI 有网络 ≠ App 有网络**：坚果云上传发生在 GitHub runner 上，
   传的是**安装包**不是账本数据；App 仍不声明 `INTERNET`。
   **R8 是最大风险**：本项目用了 Hilt（KSP 生成 + 反射）、Room（生成 DAO 实现）、
   `kotlinx.serialization`（`@Serializable` 的 `$serializer` / `Companion`，
   备份 DTO 全靠字段名）、SQLCipher（`System.loadLibrary` + JNI）、
   BiometricPrompt、Compose。多数库自带 consumer rules，但序列化与 native
   边界必须补 keep。**CI 构建成功 ≠ release 包真机能打开**——R8 出问题的典型表现
   是「装得上、一启动就崩」，沙盒无模拟器只能靠 keep 规则 + CI 编译兜底，
   真机验证必须留给使用者。
   **默认开启 R8** 落地为 `release.yml` 的 `minify` 默认 `on`；
   Gradle 侧开关默认 `false`，避免 `build.yml` 每次 push 被 R8 拖慢，
   也不让日常 CI 因混淆规则炸掉。
   照搬 lifelog 的踩坑：文件名**纯 ASCII**（GitHub Release 资产会静默丢弃中文）、
   `grep -oP` 改 Python、`bash -e` 下 curl 加 `|| true`、YAML choice 的 `on` 加引号
   （裸写会被 YAML 1.1 解析成布尔 true，GitHub 会**静默不注册**这个工作流）、
   `permissions` 同时写 contents + actions、`upload-artifact@v4` 显式
   `overwrite: true`、Secret 缺失时 `${{ secrets.X }}` 展开空串要显式判空。
   Secrets 一律不进仓库；推送用单次 commit；Python 拼 shell / Kotlin 后必跑检查脚本。

4. **验收标准**（可测）
   - **GIVEN** 推完 workflow **WHEN** 打开 Actions 页 **THEN** 左侧能看到
     「编译 Release 包」（**不是「查不到=通过」**）
   - **GIVEN** 手动触发一次 **THEN** 五个静态检查、单测、`assembleRelease`、
     lintVital、签名诊断全绿
   - **GIVEN** `minify=both` **THEN** 一次产出 plain / minified 两个包，
     Job Summary 打印两者体积与差值，minified 明确更小
   - **GIVEN** 未配坚果云 Secrets **THEN** 步骤跳过、job 仍 success
     且 Summary 写明原因
   - **GIVEN** 配了 Secrets **THEN** PROPFIND 认证 / 两级 MKCOL /
     curl -T / HEAD 200 四步全绿
   - **GIVEN** 开启 R8 **THEN** `mapping.txt` 作为制品上传
   - **GIVEN** 全流程跑完 **THEN** Manifest 仍无 `INTERNET`、
     远端 sha 校验 PASS、无 token 明文

5. **被否决的方案**
   一句话结论：**所有「跳 change / 把坚果云账号密码写死进 yml /
   用登录密码直连 WebDAV / 中文 APK 文件名 / 把 R8 开在 debug buildType /
   强制要求正式签名 Secrets 否则中断 / 把 CI 的网络请求当成违反隐私铁律 /
   顺手做 cleanup.yml / release-preview.yml / AAB /
   只交 yml 不补 proguard keep 规则」的方案均被否决**，
   采用「参照 lifelog 的 release.yml + 默认 R8 + Secrets 缺失降级 +
   坚果云上传与打 tag 解耦 + 全套 keep 规则」。
   完整表格见 `design.md`「考虑过的其他方案」。

## 影响范围

**触及的 spec 模块**：`build-and-ci`（主，6 条 ADDED）、
`privacy-security`（1 条 ADDED，边界澄清）。

**是否需要重新安装包**：否（签名未变，未配正式密钥时仍用仓库内
`debug.keystore`，与历次 debug 包同签名，可直接覆盖安装、不丢数据）

**是否会丢数据**：否（本 change 只动构建链，不碰数据库与备份）

**行为变化**：多一条手动触发的 release 工作流；
`app/build.gradle.kts` 的 `versionCode` 不再恒为 1；
release 包默认经 R8 混淆，`build.yml` 的日常 debug 构建行为不变。

## 与项目铁律是否冲突

- [x] 不违反三条铁律（本轮不出账本数据，CI 的网络请求发生在 runner 上）
- [x] 不涉及「已确认不做」清单中的功能（v1 不提供 CSV 的约定保持不动）
- [x] 无冲突

**补充说明**：阶段 5 已按规范归档（12 ADDED + 4 MODIFIED 合并，
`archive/2026-11-12-deliver-secure-backup-and-settings/`），
本 change 基于干净的 specs 基线写，delta 不叠加。
使用者确认「按推荐来」：先归档、正式签名可选不阻塞、R8 默认 `on`
且 Gradle 侧默认 `false`。
