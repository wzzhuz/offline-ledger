# tasks · rethink-record-entry-layout

- [x] 0.1 阶段 0 对齐（Q1A Q2A Q3A Q4B Q5B，验收按拟）
- [x] 0.2 新建 change 四件套 + 静态脚本
- [x] 1.1 `LedgerTheme` 加 dynamicColor 开关 + API 31+ 动态色
- [x] 1.2 `MainActivity` edge-to-edge + 根 safeDrawingPadding
- [x] 1.3 `LedgerApp` Scaffold contentWindowInsets 置 0
- [x] 2.1 `TemplateQuickPickBar` 改单按钮
- [x] 2.2 `AccountStrip` 隐藏 chips、保留按钮与全量弹层
- [x] 2.3 收款方/描述去边框（透明 colors）
- [x] 2.4 新增「更多 ›」入口 + `MorePanel`（Dialog 底部 Surface）
- [x] 2.5 `RecordEditorDialog` 首屏重排 + Dialog insets 避让
- [x] 3.1 静态脚本断言覆盖 12 项（纯 UI 批次，新增纯 JVM 单测无收益，已在 design 登记）
- [x] 3.2 版本号 0.2.56、活文档 §7.59 / project.md 登记
- [x] 3.3 推送 → 双 CI → 出 v0.2.56 包 → 交付说明

## 实测结果（2026-11-26）

- push CI `45746395557` 一次全绿；出包 CI `45747016651` 全绿；单次 dispatch
- tag `v0.2.56 → 7e68e83188c2`；APK 3,512,596 B /
  SHA256 `58f5cf237e806adcaf15b060c0eb2c1f48fcd20e29b58f900873fbace455ec83`
- 零改表 → 覆盖安装；一次成包、无诊断 commit、`build.yml` 无临时改动
- 12 个静态检查全 PASS；出包轮询一次查询层超时，短调用复查确认 success，无重发
- §8 真机待验项全部保持 `[ ]`；第 45–76 批既有待验项整体继承

