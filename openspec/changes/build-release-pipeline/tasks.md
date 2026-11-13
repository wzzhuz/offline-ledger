# 任务清单：build-release-pipeline

## 1. 阶段 0 对齐与前置归档

- [x] 1.1 读取 `AGENTS.md` / `project.md`，实际通过 API 读取 lifelog 的
  `release.yml` / `release-preview.yml` / `build.yml` / `cleanup.yml` /
  `prepare-signing.sh` / `cleanup-artifacts.sh` / `proguard-rules.pro` /
  `app/build.gradle.kts`
- [x] 1.2 强制开场复述、产出五项对齐结论并停下等待确认
- [x] 1.3 判定「需要建 change」并告知，等确认
- [x] 1.4 使用者确认「按推荐来」：先归档 / 正式签名可选不阻塞 /
  R8 默认 `on` 且 Gradle 侧默认 `false`
- [x] 1.5 **归档阶段 5**：12 ADDED 合并 + 4 MODIFIED 按标题替换 +
  change 移入 `archive/2026-11-12-deliver-secure-backup-and-settings/` +
  校验 PASS；8 模块现为 **128 条 Requirement / 192 个 Scenario**
- [x] 1.6 归档校验第 6 次自误报（新 MODIFIED 的「理由」段引用旧原文）
  已修正为「先剥理由段再匹配」，半途中断未重跑合并、未重复追加 ADDED

## 2. 写 change 四件套

- [x] 2.1 `proposal.md` —— 原始场景与完整「对齐结论」节
- [x] 2.2 `design.md` —— 版本查证 / 三处改良 / R8 keep 规则 /
  Gradle 开关 / 签名降级 / 坚果云四步 / 回退顺序 / 否决方案表
- [x] 2.3 `tasks.md` —— 本文件
- [x] 2.4 `specs/build-and-ci/spec.md` —— ADDED 6 条
- [x] 2.5 `specs/privacy-security/spec.md` —— ADDED 1 条（CI 网络 vs App 权限）
- [ ] 2.6 推送 change 文件到远端并用 API 校验

## 3. release.yml 与 .github/scripts

- [ ] 3.1 `.github/workflows/release.yml`：`workflow_dispatch` + 6 个 inputs
  （`version_name` / `create_release` / `minify` / `lint` /
  `build_aab` / `upload_nutstore` / `nutstore_folder`）
- [ ] 3.2 `.github/scripts/prepare-signing.sh`：`OFFLINELEDGER_*` 环境变量，
  四项全齐才启用正式密钥，缺失降级
- [ ] 3.3 `.github/scripts/cleanup-artifacts.sh`：按 glob 保留最近 N 个
- [ ] 3.4 坚果云四步上传 + Summary 诊断 + EXIT trap
- [ ] 3.5 产物重命名纯 ASCII、`mapping.txt` 制品、`overwrite: true`

## 4. Gradle 与 proguard

- [ ] 4.1 `app/build.gradle.kts`：`defaultVersionName` + `versionCodeFrom()` +
  CI 覆盖机制（不再恒为 1）
- [ ] 4.2 `-PminifyRelease` 开关、`isShrinkResources`、正式签名 `official`
  signingConfig
- [ ] 4.3 `app/proguard-rules.pro` 替换占位注释为完整 keep 规则
- [ ] 4.4 `scripts/check_workflow_syntax.py` 并接入 `build.yml` 静态检查步骤

## 5. 文档同步

- [ ] 5.1 `README.md` 状态与出包阶段说明
- [ ] 5.2 `project.md` 构建与发布节同步
- [ ] 5.3 `build.yml` 的静态检查步骤加第 6 个脚本

## 6. 测试与验证

- [ ] 6.1 本地六个检查脚本全部 PASS
- [ ] 6.2 单次 commit 推送后用 GitHub API 查 Actions 列表，确认**确实触发**
- [ ] 6.3 确认 push CI 全绿、workflow 在 Actions 页可见
- [ ] 6.4 **手动触发一次 `release.yml`**（`minify=both`、
  `create_release=false`、`upload_nutstore=true`）
- [ ] 6.5 轮询 run，回报 plain / minified 体积对比与坚果云状态
- [ ] 6.6 失败按 `design.md` 第 8 节回退顺序单次修复后重推

## 7. 收尾

- [ ] 7.1 同步 `design.md` / `tasks.md` 的实测结果
- [ ] 7.2 推完用远端 API 逐文件比对 blob sha
- [ ] 7.3 使用者确认后归档本 change（7 条 ADDED 合并 + change 移入 archive）

## 明确留到后续（不预先打勾）

- **R8 包真机启动验证**、SQLCipher 真机打开数据库、
  BiometricPrompt 真机弹窗、SAF 文件选择器
- 5 万条切月 / 年报聚合耗时、滚动掉帧
- 正式签名 Secrets 的实际配置与对外分发
- `release-preview.yml` / `cleanup.yml` / AAB 构建
- CSV 导出（v1.1，需先显式更新「已确认不做」清单）

> 归档需使用者明确指令，本轮不擅自归档。
