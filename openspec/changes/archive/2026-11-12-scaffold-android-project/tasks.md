# 任务清单：scaffold-android-project

## 1. 阶段 0 对齐与分级

- [x] 1.1 读取 `openspec/AGENTS.md` 与 `project.md`，复述状态并等确认
- [x] 1.2 产出五项对齐结论并停下等待确认
- [x] 1.3 判定「需要建 change」并告知，等确认
- [x] 1.4 回显目标仓库与本地源目录，使用者确认三项决策

## 2. 锁定版本组合

- [x] 2.1 读取 `wzzhuz/lifelog` 已跑通的构建配置作为基线
- [x] 2.2 查证官方文档（Kotlin/AGP 兼容表、KSP、Hilt、Google Play 目标 API、SQLCipher）
- [x] 2.3 记录 Maven metadata 403 的限制与替代证据链
- [x] 2.4 把锁定表与应急回退顺序写入 `design.md`

## 3. 写 change 四件套

- [x] 3.1 `proposal.md` —— 含原始场景与完整的「对齐结论」节
- [x] 3.2 `design.md` —— 含版本锁定表、考虑过的其他方案、风险
- [x] 3.3 `tasks.md` —— 本文件
- [x] 3.4 `specs/build-and-ci/spec.md` —— ADDED 8 条
- [x] 3.5 `specs/money-precision/spec.md` —— ADDED 2 条
- [x] 3.6 `specs/privacy-security/spec.md` —— ADDED 2 条
- [x] 3.7 `specs/backup-restore/spec.md` —— ADDED 1 条
- [x] 3.8 推送 change 文件到远端并用 API 校验

## 4. 工程骨架

- [x] 4.1 `settings.gradle.kts` / 根 `build.gradle.kts` / `gradle.properties`
- [x] 4.2 `gradle/wrapper/gradle-wrapper.properties` + 官方 `gradle-wrapper.jar`
- [x] 4.3 `app/build.gradle.kts` —— 版本 / 签名 / 依赖 / compilerOptions
- [x] 4.4 `.gitignore` / `app/proguard-rules.pro`
- [x] 4.5 用 keytool 生成 `app/debug.keystore`（JKS，固定密码与 alias）

## 5. Manifest 隐私基线

- [x] 5.1 `AndroidManifest.xml` —— 无 INTERNET、`allowBackup=false`、两个规则引用
- [x] 5.2 `res/xml/backup_rules.xml` —— 只写 exclude
- [x] 5.3 `res/xml/data_extraction_rules.xml` —— cloud-backup + device-transfer
- [x] 5.4 `res/values/strings.xml` / `themes.xml`

## 6. 基础类型与备份信封

- [x] 6.1 `core/money/Cents.kt`
- [x] 6.2 `core/time/LedgerClock.kt`
- [x] 6.3 `core/currency/LedgerCurrency.kt`
- [x] 6.4 `backup/dto/BackupEnvelope.kt` + `BackupSchema`
- [x] 6.5 `OfflineLedgerApp.kt` / `MainActivity.kt` 最小闭环
- [x] 6.6 三个单元测试类

## 7. 检查脚本与 CI

- [x] 7.1 `scripts/check_kotlin_strings.py`
- [x] 7.2 `scripts/check_docs_consistency.py`
- [x] 7.3 `scripts/check_manifest_privacy.py`
- [x] 7.4 `.github/workflows/build.yml`
- [x] 7.5 本地运行三个检查脚本并全部通过
- [x] 7.6 查 Actions 列表：28 次 push run，concurrency 取消前 27 次，最终 run 43181928985
- [x] 7.7 首轮结果：单测 / debug APK / release(lintVital) 全 success，但「输出签名信息」因 `grep -q && echo` 在 `set -e` 下返回 1 而判失败；已改用 apksigner + if 结构修复，待重跑
- [x] 7.8 用**单次 commit**（de689582b6）推送修复；重跑 run 43184217199 结论 **success**

## 8. 同步与收尾

- [x] 8.1 更新 `README.md` 的状态、目录结构、本地构建说明
- [x] 8.2 每次推送后均用远端 Git Trees API 逐文件比对 blob sha，结果 PASS
- [x] 8.3 清理本地凭据文件
- [ ] 8.4 使用者确认后归档本 change（合并 delta 到 `openspec/specs/`）

> 仅 8.4（归档）未完成：需使用者明确指令，本轮不擅自归档。
> 归档时须把 delta 合并进 `openspec/specs/`，并把 change 移入 `archive/`。
