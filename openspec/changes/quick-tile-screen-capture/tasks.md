# 第 49 批任务

## 已落（代码 / 规格）

- [x] 纯 JVM 层：`ScreenTextParser` / `QuickCaptureResolver` / `QuickEntryEngine` / `QuickCaptureModels` / `QuickCaptureBridge` / `QuickCapturePrefs`
- [x] 3 组纯 JVM 单测：解析、降级链、规则自动分类（9 条）
- [x] `ScreenCaptureAccessibilityService`：白名单节点缓存 + `takeScreenshot` 单帧
- [x] `QuickEntryTileService`：API 34+ PendingIntent 分支
- [x] 透明 `QuickCaptureActivity` + 自绘确认页（不弹标准 Dialog）
- [x] ML Kit 中文 bundled OCR
- [x] `QuickCaptureSettingsActivity`：无障碍 / 添加磁贴 / 电池优化 / 白名单
- [x] Manifest / a11y 配置 / 透明主题 / 字符串
- [x] `LedgerRepository.insertScreenRecord` 幂等写路径
- [x] 版本 0.2.31、规格、活文档 §7.31、project.md 显式授权

## 真机复核（仍 [ ]，沙盒验不了）

- [ ] 白名单账单页 → 下拉 QS → 磁贴 → 确认页预填金额 / 对方 / 描述
- [ ] 连点两次磁贴只入账一次（指纹幂等）
- [ ] 非白名单 App 点磁贴不产生任何解析
- [ ] 节点为空时 OCR 是否兜住；`FLAG_SECURE` 页面是否明确降级不静默写
- [ ] 保存后明细 / 统计刷新，撤销逐字段还原
- [ ] 装新版 / 重启后无障碍权限被系统复位，引导页能一键跳设置
- [ ] Android 13+ 「允许受限设置」能放行
- [ ] v0.2.30 全套（单一周期源、切月刷新、详情页、规则、CSV 重建）不回退

## 合回条件

以上真机项由使用者判定通过后，才 merge 到 main 并定版。


### §7.31 实测编号（第 49 批，分支开发）

- 代码 commit `d29b2c7af12da2c359cf98c9d1bafcd1a32321ea`（短 sha `d29b2c7a`；20 新增 / 7 修改 / 0 删除，`defaultVersionName = "0.2.31"`）。
- 分支 push CI `44765082653` **success**，17/17 步全绿（六个静态检查 + 纯 JVM 单测 + assembleDebug / assembleRelease）。
- release `44766740258` **success**，28 步 = 23 success / 5 skipped，**「上传到坚果云」completed success**；无并发 cancelled run。
- 制品：`offline-ledger-release-v0.2.31` 32,143,745 B、`offline-ledger-mapping-v0.2.31` 4,380,358 B、`offline-ledger-metrics-v0.2.31-p-m-nsfalse` 270 B。`nsfalse` 表示上传脚本走完认证 / 建目录 / PUT，未走「跳过」分支。
- 分支 HEAD `d29b2c7af12d` 时校验：**414↔414 blob、diff 0**；workflow 仅 build / release，`diag/` 零残留。
- **main 仍冻结在 v0.2.30**（`6f85c8318bda`），未 merge、未 bump main 版本号。
- 按 release.yml 命名规则，坚果云目标为 `/my-apps/offline-ledger-releases/offline-ledger-v0.2.31-release-d29b2c7a.apk`。WebDAV 目录与 job logs 在当前凭据下不可读（403），**目录以使用者实际所见为准**，不把 step 绿等价于目录可见。
- 体积从 v0.2.30 的约 12M（artifact 压缩口径）升到 32M，是 ML Kit 中文 bundled 模型与 native 库进入 release 包所致，不是另一个构建变体。
