# 任务清单：deliver-secure-backup-and-settings

## 1. 阶段 0 对齐与前置归档

- [x] 1.1 读取 `AGENTS.md` / `project.md` 与 `privacy-security` / `backup-restore` /
  `reconciliation` / `category-stats` / `reporting-ui` spec，实际读 `SettingsScreen` /
  `LedgerRepository` / `CategoryEntity` / `BackupImporter` / `Reconciliation` /
  `ReconcileViewModel` / 三个静态检查脚本
- [x] 1.2 强制开场复述、产出五项对齐结论并停下等待确认
- [x] 1.3 判定「需要建 change」并告知，等确认
- [x] 1.4 使用者确认「按推荐来」：CSV 留 v1.1 / BiometricPrompt+PIN / SharedPreferences
- [x] 1.5 实际联网查证 `androidx.biometric` 稳定版、SAF 无需存储权限、
  `DEVICE_CREDENTIAL` 组合的 API 门槛，写进 `design.md` 第 1 节
- [x] 1.6 归档阶段 4：9 ADDED 合并 + 1 MODIFIED 第二次按标题替换 +
  change 移入 `archive/2026-11-12-build-statistics-charts/` + 校验 PASS；
  8 模块现为 116 条 Requirement / 166 个 Scenario
- [x] 1.7 阶段 4 记录的 3 处规格债纳入本 change，并补第 4 份 MODIFIED

## 2. 写 change 四件套

- [x] 2.1 `proposal.md` —— 原始场景与完整「对齐结论」节
- [x] 2.2 `design.md` —— 版本查证 / 4 份 MODIFIED / 两套密钥 /
  加密文件格式 / SAF / 应用锁 / 流式边界 / 回退顺序 / 否决方案表
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 `specs/backup-restore/spec.md` —— ADDED 3 + **MODIFIED 2**
- [x] 2.5 `specs/privacy-security/spec.md` —— ADDED 3 + **MODIFIED 1**
- [x] 2.6 `specs/reporting-ui/spec.md` —— ADDED 1 + **MODIFIED 1**
- [x] 2.7 `specs/category-stats/spec.md` —— ADDED 2
- [x] 2.8 `specs/reconciliation/spec.md` —— ADDED 1
- [x] 2.9 `specs/build-and-ci/spec.md` —— ADDED 2
- [ ] 2.10 推送 change 文件到远端并用 API 校验

## 3. 纯 JVM 加密层与单测

- [ ] 3.1 `backup/crypto/SecureBackupCodec.kt`：MAGIC + salt + IV +
  PBKDF2WithHmacSHA256 + AES-256/GCM + Gzip，纯 JVM
- [ ] 3.2 `SecureBackupCodecTest.kt`：往返、错口令、篡改、MAGIC 不匹配、
  截断、短口令、密文不含明文、两次导出不同

## 4. 依赖、权限与静态检查

- [ ] 4.1 `app/build.gradle.kts` 加 `androidx.biometric:biometric:1.1.0`
- [ ] 4.2 `AndroidManifest.xml` 加 `USE_BIOMETRIC`（normal 级）
- [ ] 4.3 `check_manifest_privacy.py` 加 `ALLOWED_PERMISSIONS` 白名单检查
- [ ] 4.4 `check_transaction_annotations.py` 的 `WRITE_METHODS` 13 → 17
- [ ] 4.5 `check_docs_consistency.py` 复核 `project.md` 阶段 5 与 CSV 归属一致

## 5. 偏好、配色与应用锁

- [ ] 5.1 `core/prefs/LedgerPrefs.kt`：阈值 / 配色 / FLAG_SECURE /
  应用锁 / 加盐 PIN hash
- [ ] 5.2 `ui/theme/LedgerTheme.kt`：`mutableStateOf` 语义色反转
- [ ] 5.3 `MainActivity.kt`：`FragmentActivity` + FLAG_SECURE + 冷启动 /
  后台恢复解锁门禁
- [ ] 5.4 `ui/lock/AppLockScreen.kt`：BiometricPrompt + PIN 兜底
- [ ] 5.5 `Reconciliation` / `LedgerRepository.reconcile` / `ReconcileViewModel`
  三处同步阈值

## 6. SAF 导入导出与设置页

- [ ] 6.1 `ui/settings/SettingsViewModel.kt`：导出 / 导入 / 偏好 / 自检
- [ ] 6.2 `ui/settings/SettingsScreen.kt` 重写为设置中心
  （移除临时明文导出入口）
- [ ] 6.3 `ui/settings/BackupPasswordDialog.kt`：口令输入 +
  「将全量替换现有全部数据」二次确认
- [ ] 6.4 `ui/settings/CategoryManagerDialog.kt`：两级分类增删改排序归档
- [ ] 6.5 `CategoryDao` 3 条新查询 + `LedgerRepository` 4 个事务写方法

## 7. 测试与静态检查

- [ ] 7.1 本地五个检查脚本全部 PASS
- [ ] 7.2 单次 commit 推送后用 GitHub API 查 Actions 列表，确认**确实触发**
- [ ] 7.3 轮询 Actions；失败按 `design.md` 第 9 节回退顺序单次修复后重推
- [ ] 7.4 确认 job 结论为 success、制品产出

## 8. 同步与收尾

- [ ] 8.1 更新 `README.md` 阶段状态、目录结构、阶段 5 说明、测试边界
- [ ] 8.2 同步 `project.md` 阶段 5 表格里的 CSV 字样为「v1 不做」
- [ ] 8.3 推完用远端 API 逐文件比对 blob sha
- [ ] 8.4 使用者确认后归档本 change（4 份 MODIFIED 按标题替换 + ADDED 合并）

## 明确留到后续（不预先打勾）

- 逐条流式序列化（`encodeToString` 仍是 5 万条时约 10–15 MB 的单个 String）
- 5 万条真机切月 / 年报聚合耗时、滚动掉帧
- SQLCipher 真机打开数据库、BiometricPrompt 真机弹窗、SAF 真机文件选择器
- `release.yml`、正式签名 Secrets 与分发方案
- CSV 导出（v1.1，需先显式更新「已确认不做」清单）

> 归档需使用者明确指令，本轮不擅自归档。
